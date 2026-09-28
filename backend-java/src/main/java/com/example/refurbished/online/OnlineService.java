package com.example.refurbished.online;

import com.example.refurbished.audit.AuditService;
import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.common.exception.*;
import com.example.refurbished.inventory.*;
import com.example.refurbished.order.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OnlineService {
    private final JdbcTemplate jdbc;
    private final DeviceUnitRepository devices;
    private final OrderRepository orders;
    private final AuditService audit;
    private final boolean sandbox;
    public boolean sandboxEnabled() { return sandbox; }
    public OnlineService(JdbcTemplate jdbc, DeviceUnitRepository devices, OrderRepository orders, AuditService audit,
            @Value("${app.online.sandbox-enabled:false}") boolean sandbox) {
        this.jdbc=jdbc; this.devices=devices; this.orders=orders; this.audit=audit; this.sandbox=sandbox;
    }
    public record Reservation(UUID id, UUID actorId, UUID deviceUnitId, String customerName, String shippingAddress,
            BigDecimal amount, String currency, String status, String paymentStatus, UUID orderId,
            String trackingNumber, String shipmentStatus, Instant expiresAt, Instant createdAt) {}
    private final org.springframework.jdbc.core.RowMapper<Reservation> mapper = (r,n) -> new Reservation(
            r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getObject("device_unit_id",UUID.class),
            r.getString("customer_name"),r.getString("shipping_address"),r.getBigDecimal("amount"),r.getString("currency"),
            r.getString("status"),r.getString("payment_status"),r.getObject("order_id",UUID.class),r.getString("tracking_number"),
            r.getString("shipment_status"),r.getTimestamp("expires_at").toInstant(),r.getTimestamp("created_at").toInstant());

    public Reservation reserve(OnlineController.ReserveRequest request) {
        UUID actor = actor();
        // Khóa requestId trước khóa máy: retry không tạo hai reservation, kể cả hai HTTP đến cùng lúc.
        // Hash collision chỉ làm chờ thêm; danh tính thực vẫn được kiểm tra bằng UUID đầy đủ.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", request.requestId().toString());
        var existing=jdbc.query("SELECT * FROM online_reservations WHERE id=?",mapper,request.requestId());
        if (!existing.isEmpty()) {
            Reservation old=existing.getFirst();
            if (!old.actorId().equals(actor) || !old.deviceUnitId().equals(request.deviceUnitId())
                    || !old.customerName().equals(request.customerName().trim())
                    || !old.shippingAddress().equals(request.shippingAddress().trim())) conflict("Request ID belongs to another reservation.");
            return old;
        }
        DeviceUnit device=lockedDevice(request.deviceUnitId());
        device.reserveForCheckout();
        devices.flush();
        jdbc.update("INSERT INTO online_reservations(id,actor_id,device_unit_id,customer_name,shipping_address,amount,status,expires_at) "
                + "VALUES (?,?,?,?,?,?,'ACTIVE',clock_timestamp()+interval '15 minutes')", request.requestId(),actor,device.getId(),
                request.customerName().trim(),request.shippingAddress().trim(),device.getSalePrice());
        audit.record("RESERVATION_CREATED","DEVICE_UNIT",device.getId(),Map.of("reservationId",request.requestId()));
        return read(request.requestId(),false);
    }
    @Transactional(readOnly=true)
    public PageResponse<Reservation> list(int page,int size) {
        UUID actor=actor(); boolean admin=admin();
        String where=admin?"":" WHERE actor_id=?";
        List<Object> args=new ArrayList<>(); if (!admin) args.add(actor);
        long total=jdbc.queryForObject("SELECT count(*) FROM online_reservations"+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var items=jdbc.query("SELECT * FROM online_reservations"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",mapper,args.toArray());
        return new PageResponse<>(items,page,size,total,(int)((total+size-1)/size));
    }
    @Transactional(readOnly=true)
    public Reservation get(UUID id) { Reservation r=read(id,false); authorize(r); return r; }

    public Reservation cancel(UUID id) {
        Reservation r=read(id,true);authorize(r);
        if (r.status().equals("CANCELLED") || r.status().equals("EXPIRED")) return r;
        if (!r.status().equals("ACTIVE")) conflict("Only an unpaid reservation may be cancelled.");
        release(r,expired(r)?"EXPIRED":"CANCELLED");return read(id,false);
    }
    public Reservation pay(UUID id,OnlineController.PaymentRequest request) {
        requireSandboxAdmin();
        // Mọi luồng expiry/payment khóa reservation rồi máy theo cùng thứ tự, tránh giải phóng máy đã bán.
        Reservation r=read(id,true); validateAmount(r,request);
        if (eventExists(id,request,"PAYMENT")) return r;
        if (!r.paymentStatus().equals("UNPAID")) conflict("Payment was already recorded; reconcile duplicate provider attempts.");
        if (r.status().equals("ACTIVE") && expired(r)) { release(r,"EXPIRED");r=read(id,false); }
        if (!r.status().equals("ACTIVE")) {
            // Tiền đến trễ không chứng minh máy còn thuộc khách này. Ghi ngoại lệ, không chạm kho.
            jdbc.update("UPDATE online_reservations SET payment_status='LATE_PAYMENT' WHERE id=?",id);
            recordEvent(id,request,"PAYMENT");
            audit.record("PAYMENT_RECONCILIATION_REQUIRED","RESERVATION",id,Map.of("sandbox",true));
            return read(id,false);
        }
        DeviceUnit device=lockedDevice(r.deviceUnitId());
        // Thời gian có thể trôi qua trong lúc chờ khóa máy. Kiểm tra lại trước chuyển SOLD.
        if (expired(r)) {
            release(r,"EXPIRED");
            jdbc.update("UPDATE online_reservations SET payment_status='LATE_PAYMENT' WHERE id=?",id);
            recordEvent(id,request,"PAYMENT");
            audit.record("PAYMENT_RECONCILIATION_REQUIRED","RESERVATION",id,Map.of("sandbox",true));
            return read(id,false);
        }
        if (device.getSalePrice().compareTo(r.amount())!=0) conflict("Reserved price changed; reconciliation is required.");
        device.completeSale();
        Order order=new Order(r.customerName());order.addSoldDevice(device);orders.saveAndFlush(order);
        jdbc.update("UPDATE online_reservations SET status='PAID',payment_status='PAID',order_id=? WHERE id=?",order.getId(),id);
        recordEvent(id,request,"PAYMENT");
        audit.record("ONLINE_PAYMENT_CONFIRMED","RESERVATION",id,Map.of("sandbox",true,"orderId",order.getId()));
        audit.record("DEVICE_SOLD","DEVICE_UNIT",device.getId(),Map.of("orderId",order.getId()));
        return read(id,false);
    }
    public Reservation refund(UUID id,OnlineController.PaymentRequest request) {
        requireSandboxAdmin();Reservation r=read(id,true);validateAmount(r,request);
        if (eventExists(id,request,"REFUND")) return r;
        if (!(r.paymentStatus().equals("PAID")||r.paymentStatus().equals("LATE_PAYMENT"))) conflict("No refundable payment.");
        // Hoàn tiền không chứng minh đã nhận lại hàng. Máy SOLD không tự trở về AVAILABLE.
        jdbc.update("UPDATE online_reservations SET payment_status='REFUNDED' WHERE id=?",id);
        recordEvent(id,request,"REFUND");audit.record("SANDBOX_REFUNDED","RESERVATION",id,Map.of("amount",r.amount()));
        return read(id,false);
    }
    public Reservation ship(UUID id,OnlineController.ShipmentRequest request) {
        Reservation r=read(id,true);authorize(r);
        if (!r.paymentStatus().equals("PAID")) conflict("Shipment requires confirmed, non-refunded payment.");
        if (r.shipmentStatus().equals(request.status()) && Objects.equals(r.trackingNumber(),request.trackingNumber().trim())) return r;
        boolean valid=r.shipmentStatus().equals("PENDING") && request.status().equals("SHIPPED")
                || r.shipmentStatus().equals("SHIPPED") && request.status().equals("DELIVERED")
                && r.trackingNumber().equals(request.trackingNumber().trim());
        if (!valid) conflict("Shipment must progress PENDING -> SHIPPED -> DELIVERED with the same tracking number.");
        jdbc.update("UPDATE online_reservations SET tracking_number=?,shipment_status=? WHERE id=?",request.trackingNumber().trim(),request.status(),id);
        audit.record("SHIPMENT_"+request.status(),"RESERVATION",id,Map.of());return read(id,false);
    }
    public int expireBatch() {
        // SKIP LOCKED cho phép nhiều worker; không giành lại reservation đang được payment xử lý.
        var batch=jdbc.query("SELECT * FROM online_reservations WHERE status='ACTIVE' AND expires_at<=clock_timestamp() "
                + "ORDER BY expires_at,id LIMIT 100 FOR UPDATE SKIP LOCKED",mapper);
        // Counter checkout also locks device UUIDs in ascending order.
        for (Reservation r:batch.stream().sorted(Comparator.comparing(Reservation::deviceUnitId)).toList()) release(r,"EXPIRED");
        return batch.size();
    }
    private void release(Reservation r,String status) {
        DeviceUnit device=lockedDevice(r.deviceUnitId());device.releaseReservation();devices.flush();
        jdbc.update("UPDATE online_reservations SET status=? WHERE id=?",status,r.id());
        audit.record("RESERVATION_"+status,"DEVICE_UNIT",device.getId(),Map.of("reservationId",r.id()));
    }
    private boolean expired(Reservation r) { return !r.expiresAt().isAfter(jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant()); }
    private Reservation read(UUID id,boolean lock) {
        return jdbc.query("SELECT * FROM online_reservations WHERE id=?"+(lock?" FOR UPDATE":""),mapper,id).stream().findFirst()
                .orElseThrow(()->new ResourceNotFoundException("Reservation not found."));
    }
    private DeviceUnit lockedDevice(UUID id) { return devices.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Device not found.")); }
    private UUID actor() { UUID id=AuditService.currentActorId();if(id==null)throw new AuthenticationFailedException("Bearer session required.");return id; }
    private boolean admin() { var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN")); }
    private void authorize(Reservation r) { if(!actor().equals(r.actorId())&&!admin())throw new ResourceNotFoundException("Reservation not found."); }
    private void requireSandboxAdmin() {
        if(!sandbox)throw new ResourceNotFoundException("Sandbox payments are disabled.");
        actor();if(!admin())throw new org.springframework.security.access.AccessDeniedException("ADMIN required.");
    }
    private void validateAmount(Reservation r,OnlineController.PaymentRequest p) {
        if(r.amount().compareTo (p.amount())!=0||!r.currency().equals(p.currency())) conflict("Amount/currency must equal the reservation snapshot.");
    }
    private boolean eventExists(UUID id,OnlineController.PaymentRequest p,String kind) {
        // Serialize event IDs too: another reservation cannot reuse a payment/refund event.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,1))",p.eventId().toString());
        var events=jdbc.queryForList("SELECT * FROM sandbox_payment_events WHERE event_id=?",p.eventId());
        if(events.isEmpty())return false;
        var event=events.getFirst();
        if(!id.equals(event.get("reservation_id"))||!kind.equals(event.get("kind"))) conflict("Event ID already belongs to another operation.");
        return true;
    }
    private void recordEvent(UUID id,OnlineController.PaymentRequest p,String kind) {
        jdbc.update("INSERT INTO sandbox_payment_events(event_id,reservation_id,kind,amount,currency) VALUES (?,?,?,?,?)",p.eventId(),id,kind,p.amount(),p.currency());
    }
    private void conflict(String message) { throw new BusinessConflictException(message); }
}
