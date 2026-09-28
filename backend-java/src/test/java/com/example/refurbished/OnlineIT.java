package com.example.refurbished;

import com.example.refurbished.online.OnlineService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import static org.junit.jupiter.api.Assertions.*;

// Re-run the counter-sale regression suite with online sandbox enabled as well.
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "app.security.permit-all-for-tests=false","app.online.sandbox-enabled=true"})
class OnlineIT extends OperationsIT {
    @Autowired OnlineService online;
    @Override @AfterEach void cleanup() {
        jdbc.update("DELETE FROM sandbox_payment_events WHERE reservation_id IN (SELECT id FROM online_reservations WHERE actor_id IN (?,?))",adminId,staffId);
        jdbc.update("DELETE FROM online_reservations WHERE actor_id IN (?,?)",adminId,staffId);
        jdbc.update("DELETE FROM audit_events WHERE target_id IN (SELECT id FROM device_units WHERE product_id=?)",productId);
        super.cleanup();
    }
    @Test void reservationBlocksCounterSaleAndPaymentRetryCreatesOneOrder() {
        UUID device=device(true), id=UUID.randomUUID();
        JsonNode held=reserve(id,device,staffToken,201);
        assertEquals(held,reserve(id,device,staffToken,201));
        checkout(device,"Counter buyer",null,adminToken,409);
        Map<String,Object> event=payment();
        call(HttpMethod.POST,path(id)+"/sandbox-payment",event,staffToken,null,403);
        call(HttpMethod.POST,path(id)+"/sandbox-payment",Map.of("eventId",UUID.randomUUID(),"amount",1,"currency","VND"),adminToken,null,409);
        JsonNode paid=call(HttpMethod.POST,path(id)+"/sandbox-payment",event,adminToken,null,200);
        assertEquals("PAID",paid.get("status").asText());
        assertEquals(paid,call(HttpMethod.POST,path(id)+"/sandbox-payment",event,adminToken,null,200));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM order_items WHERE device_unit_id=?",Integer.class,device));
        assertEquals("SOLD",call(HttpMethod.GET,"/api/device-units/"+device,null,staffToken,null,200).get("status").asText());
    }
    @Test void expiryAndLatePaymentNeverTakeDeviceFromAnotherBuyer() {
        UUID device=device(true), id=UUID.randomUUID();reserve(id,device,staffToken,201);
        jdbc.update("UPDATE online_reservations SET expires_at=clock_timestamp()-interval '1 second' WHERE id=?",id);
        online.expireBatch();
        checkout(device,"Counter buyer",null,staffToken,201);
        JsonNode late=call(HttpMethod.POST,path(id)+"/sandbox-payment",payment(),adminToken,null,200);
        assertEquals("LATE_PAYMENT",late.get("paymentStatus").asText());assertTrue(late.get("orderId").isNull());
        call(HttpMethod.POST,path(id)+"/sandbox-refund",payment(),adminToken,null,200);
        assertEquals("SOLD",call(HttpMethod.GET,"/api/device-units/"+device,null,staffToken,null,200).get("status").asText());
    }
    @Test void shippingAndRefundDoNotReleaseSoldInventory() {
        UUID device=device(true), id=UUID.randomUUID();reserve(id,device,staffToken,201);
        call(HttpMethod.POST,path(id)+"/shipment",Map.of("trackingNumber","TRACK","status","SHIPPED"),staffToken,null,409);
        call(HttpMethod.POST,path(id)+"/sandbox-payment",payment(),adminToken,null,200);
        call(HttpMethod.POST,path(id)+"/shipment",Map.of("trackingNumber","TRACK","status","SHIPPED"),staffToken,null,200);
        call(HttpMethod.POST,path(id)+"/shipment",Map.of("trackingNumber","OTHER","status","DELIVERED"),staffToken,null,409);
        call(HttpMethod.POST,path(id)+"/shipment",Map.of("trackingNumber","TRACK","status","DELIVERED"),staffToken,null,200);
        var event=payment();
        JsonNode refund=call(HttpMethod.POST,path(id)+"/sandbox-refund",event,adminToken,null,200);
        assertEquals(refund,call(HttpMethod.POST,path(id)+"/sandbox-refund",event,adminToken,null,200));
        checkout(device,"Cannot resell refunded device",null,staffToken,409);
    }
    @Test void staffCannotReadOrCancelAnotherActorsReservation() {
        UUID device=device(true), id=UUID.randomUUID();reserve(id,device,adminToken,201);
        call(HttpMethod.GET,path(id),null,staffToken,null,404);
        call(HttpMethod.POST,path(id)+"/cancel",null,staffToken,null,404);
        call(HttpMethod.POST,path(id)+"/cancel",null,adminToken,null,200);
        checkout(device,"Released",null,staffToken,201);
    }
    @Test void twoReservationsCannotOwnSameSerial() throws Exception {
        UUID device=device(true);CountDownLatch start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> reserve=()->{start.await();var headers=new org.springframework.http.HttpHeaders();headers.setBearerAuth(staffToken);
                return http.postForEntity("/api/online/reservations",new org.springframework.http.HttpEntity<>(body(UUID.randomUUID(),device),headers),JsonNode.class).getStatusCode().value();};
            var a=executor.submit(reserve);var b=executor.submit(reserve);start.countDown();
            assertEquals(List.of(201,409),java.util.stream.Stream.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM online_reservations WHERE device_unit_id=? AND status='ACTIVE'",Integer.class,device));
    }
    @Test void paymentRacingExpiryHasOneConsistentOutcome() throws Exception {
        UUID device=device(true),id=UUID.randomUUID();reserve(id,device,staffToken,201);
        jdbc.update("UPDATE online_reservations SET expires_at=clock_timestamp()-interval '1 second' WHERE id=?",id);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var expiry=executor.submit(()->online.expireBatch());
            var paid=executor.submit(()->call(HttpMethod.POST,path(id)+"/sandbox-payment",payment(),adminToken,null,200));
            expiry.get(15,TimeUnit.SECONDS);assertEquals("LATE_PAYMENT",paid.get(15,TimeUnit.SECONDS).get("paymentStatus").asText());
        }
        assertEquals("AVAILABLE",call(HttpMethod.GET,"/api/device-units/"+device,null,staffToken,null,200).get("status").asText());
    }
    private String path(UUID id){return "/api/online/reservations/"+id;}
    private Map<String,Object> payment(){return Map.of("eventId",UUID.randomUUID(),"amount",1500,"currency","VND");}
    private Map<String,Object> body(UUID id,UUID device){return Map.of("requestId",id,"deviceUnitId",device,"customerName","Online buyer","shippingAddress","Test address");}
    private JsonNode reserve(UUID id,UUID device,String token,int status){return call(HttpMethod.POST,"/api/online/reservations",body(id,device),token,null,status);}
}
