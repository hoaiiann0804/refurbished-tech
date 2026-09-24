# Báo cáo thực tế Phase 6

## Files created

- `order/Order.java`, `OrderItem.java`, `OrderStatus.java`
- `order/OrderRepository.java`, `OrderService.java`, `OrderController.java`
- `order/dto/CheckoutRequest.java`, `OrderResponse.java`, `OrderItemResponse.java`
- `db/migration/V3__create_orders_and_order_items.sql`
- `src/test/java/com/example/refurbished/OrderCheckoutIT.java`
- `docs/PHASE-6.md`, `docs/PHASE-6-REPORT.md`

## Files modified

- `inventory/DeviceUnit.java`
- `src/test/java/com/example/refurbished/inventory/DeviceUnitTest.java`
- `src/test/java/com/example/refurbished/DeviceLifecycleIT.java`
- `backend-java/README.md`

## Commands executed

```powershell
.\mvnw.cmd ... clean verify
java -jar target/refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
Invoke-WebRequest http://127.0.0.1:8080/api/health
```

Ứng dụng smoke test được chạy ẩn, log vào `.run/phase6-startup*.log` và process được
dừng trong `finally`. Startup log xác nhận JDBC URL development, Flyway V3 và Tomcat.

## Dependencies added

Không thêm dependency.

## What was implemented

- Order aggregate và OrderItem tham chiếu DeviceUnit vật lý.
- Snapshot giá, tính tổng bằng `BigDecimal`.
- Checkout một hoặc nhiều thiết bị trong một transaction.
- Lifecycle `AVAILABLE -> RESERVED -> SOLD`.
- Row lock theo thứ tự UUID ổn định, rollback khi bất kỳ device nào lỗi.
- POST checkout, GET order và migration V3.

## What was not implemented

- Payment, reservation timeout/cancel, shipping, authentication và customer entity.
- Warranty.
- Concurrent integration test hai request; thuộc Phase 7.
- Production deployment.

## Tests and build result

`clean verify`: **BUILD SUCCESS**.

| Nhóm | Tests | Kết quả |
|---|---:|---|
| Unit tests | 16 | PASS |
| Integration tests | 45 | PASS |
| **Tổng** | **61** | **0 failures, 0 errors, 0 skipped** |

Ghi chú: tổng chính xác là 61 (`16 + 45`). Phase 5 có 53 test; Phase 6 thêm 1 unit
test và 7 integration tests.

Integration test dùng `127.0.0.1:55433/refurbished_test`. Flyway áp dụng V3 thành
công. Packaged JAR chạy trên `127.0.0.1:55432/refurbished_dev`, health trả HTTP 200,
và startup log xác nhận schema development lên V3.

## Manual testing

Xem `docs/PHASE-6.md`. Không có dữ liệu checkout demo được ghi vào development
database trong smoke test.

## CV integrity

| Hạng mục | Trạng thái |
|---|---|
| Transactional checkout tuần tự | IMPLEMENTED, TESTED |
| Order/OrderItem và price snapshot | IMPLEMENTED, TESTED |
| Pessimistic locking trong checkout | IMPLEMENTED; chưa concurrency-tested |
| Chống bán trùng dưới request đồng thời | PLANNED cho Phase 7 |
| Production deployment | Chưa deployed |
