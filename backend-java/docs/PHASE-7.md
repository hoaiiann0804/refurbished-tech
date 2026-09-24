# Phase 7 — Chứng minh concurrency checkout

## Kết luận đã được kiểm thử

Khi hai HTTP request checkout cùng DeviceUnit `AVAILABLE`:

- Chính xác một request trả `201 Created`.
- Chính xác một request trả `409 BUSINESS_CONFLICT`.
- DeviceUnit kết thúc ở `SOLD`.
- Chỉ một `sales_orders` trạng thái `COMPLETED` tồn tại cho thiết bị.
- Chỉ một `order_items` tham chiếu DeviceUnit đó.

## Race condition nếu không khóa

Nếu chỉ đọc rồi kiểm tra `device.status == AVAILABLE`, request A và B có thể cùng
đọc `AVAILABLE`, cùng tạo Order và cùng bán một thiết bị vật lý. `@Transactional`
một mình không tự biến chuỗi read-check-write thành thao tác độc quyền ở isolation
level mặc định `READ COMMITTED`.

## Transaction và row lock

`OrderService.checkout()` mở transaction. `DeviceUnitRepository.findByIdForUpdate()`
dùng `@Lock(PESSIMISTIC_WRITE)`, khiến Hibernate yêu cầu PostgreSQL row-level write
lock, tương ứng ý nghĩa `SELECT ... FOR UPDATE`.

```text
Request A                         Request B
---------                         ---------
BEGIN                             BEGIN
lock DeviceUnit row               chờ cùng row lock
thấy AVAILABLE
tạo Order + OrderItem
đổi DeviceUnit thành SOLD
COMMIT, thả lock                  nhận lock sau khi A commit
                                  đọc trạng thái mới SOLD
                                  ném BusinessConflictException
                                  ROLLBACK, thả lock
```

Row bị khóa là record trong `device_units` có đúng ID được checkout. Lock được giữ
đến khi transaction commit hoặc rollback. Nếu A rollback, thay đổi của A biến mất,
lock được thả và B có thể đọc lại `AVAILABLE` để tiếp tục thành công.

Unique constraint `order_items.device_unit_id` là tuyến bảo vệ database bổ sung.
Nó không thay thế lock vì lock còn giúp request thua nhận lỗi nghiệp vụ rõ ràng và
giữ toàn bộ checkout nhiều thiết bị nhất quán.

## Test buộc hai request thực sự cạnh tranh

`CheckoutConcurrencyIT` không chỉ đưa hai task vào thread pool rồi hy vọng chúng
chạy cùng lúc. Test thực hiện:

1. Tạo một DeviceUnit đã inspection và đang `AVAILABLE` trong `refurbished_test`.
2. Mở JDBC transaction kiểm soát và giữ row bằng `SELECT ... FOR UPDATE`.
3. Khởi chạy hai HTTP checkout trên hai worker thread.
4. Xác minh cả hai backend transaction đang `wait_event_type='Lock'` trong
   `pg_stat_activity`.
5. Commit connection kiểm soát để thả lock.
6. Chờ cả hai HTTP response và kiểm tra kết quả/data invariant.

Cách này tạo bằng chứng rằng cả hai request đã cùng đến PostgreSQL và cùng cạnh
tranh đúng row, thay vì vô tình chạy tuần tự.

## Java/Spring và mapping

| Khái niệm | Vai trò | .NET / Node tương đương |
|---|---|---|
| `PESSIMISTIC_WRITE` | Giành quyền sửa độc quyền trên row | EF raw `FOR UPDATE`; Sequelize `LOCK.UPDATE` |
| Transaction boundary | Giữ lock xuyên suốt check và write | EF/Sequelize transaction callback |
| `Future` | Nhận kết quả của task chạy thread khác | `Task<T>` / Promise |
| `CountDownLatch` | Đồng bộ điểm bắt đầu của worker | `TaskCompletionSource` / Promise barrier tự xây |
| Database invariant | Chỉ một OrderItem cho một DeviceUnit | Unique constraint giống nhau ở mọi stack |

## 5 câu hỏi phỏng vấn Phase 7

1. Vì sao `@Transactional` mà không có lock vẫn có thể bán trùng?
2. Request B xảy ra chuyện gì khi request A đang giữ pessimistic lock?
3. Khi request A rollback, lock và dữ liệu thay đổi như thế nào?
4. Vì sao unique constraint vẫn cần thiết khi ứng dụng đã dùng row lock?
5. Làm sao chứng minh một concurrency test thật sự tạo overlap thay vì chạy tuần tự?

## Roadmap còn lại

| Phase | Nội dung | Vai trò hoàn thành |
|---|---|---|
| Phase 7 | Concurrency checkout | Hoàn tất và đã test |
| Phase 8 | Warranty tối thiểu gắn với DeviceUnit đã SOLD | Phase cuối của core backend |
| Phase 9 | Auth, Redis, email, frontend, Docker/deployment... | Tùy chọn, đánh giá từng feature |

Sau Phase 8, core backend theo yêu cầu ban đầu được xem là hoàn thành. Phase 9 không
phải một danh sách bắt buộc phải chuyển toàn bộ từ Node sang Java.
