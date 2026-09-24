# Phase 6 — Order và transactional checkout

## Thiết kế nghiệp vụ

Phase này coi checkout là giao dịch bán nội bộ hoàn tất ngay. Chưa có payment gateway,
authentication hay giao hàng. Một checkout hợp lệ tạo một `Order` trạng thái
`COMPLETED`; mỗi `OrderItem` tham chiếu đúng `DeviceUnit` vật lý và lưu `unitPrice`
snapshot. Giá lịch sử của đơn không thay đổi nếu giá thiết bị được sửa trong tương lai.

```text
AVAILABLE -> RESERVED -> SOLD
                 cùng một @Transactional checkout
```

`RESERVED` là transition bắt buộc trong domain lifecycle. Vì Phase 6 chưa có luồng
thanh toán bất đồng bộ, hai transition diễn ra trong cùng transaction và trạng thái
được commit ra database là `SOLD`.

## API

| Method | URL | Hành vi |
|---|---|---|
| POST | `/api/orders/checkout` | Bán từ 1 đến 20 DeviceUnit đang AVAILABLE |
| GET | `/api/orders/{id}` | Đọc Order cùng các OrderItem |

Request:

```json
{
  "customerName": "Nguyen Van A",
  "deviceUnitIds": ["00000000-0000-0000-0000-000000000001"]
}
```

Response `201 Created` có `Location: /api/orders/{id}`, tổng tiền và từng item gồm
`deviceUnitId`, serial number, productId, unitPrice.

## Transaction boundary

`OrderService.checkout()` là transaction boundary. Quy trình:

1. Từ chối ID trùng trong request.
2. Sắp UUID để mọi checkout nhiều thiết bị lấy lock theo thứ tự ổn định.
3. Đọc và khóa từng DeviceUnit bằng `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`).
4. Kiểm tra tất cả thiết bị đều `AVAILABLE` trước khi thay đổi entity nào.
5. Chuyển từng thiết bị `AVAILABLE -> RESERVED -> SOLD`.
6. Tạo OrderItem với giá snapshot và tính total.
7. Flush Order, items và các device update; commit khi service method kết thúc.

Nếu một bước ném exception, Spring đánh dấu rollback. Không có Order một phần, item
mồ côi hay thiết bị đầu tiên bị bán khi thiết bị sau không hợp lệ.

Migration V3 tạo `sales_orders` và `order_items`. Tên bảng `sales_orders` tránh từ
khóa SQL `ORDER`. Unique constraint trên `order_items.device_unit_id` là lớp bảo vệ
database để một DeviceUnit không thể xuất hiện trong hai giao dịch bán.

Phase 6 đã sử dụng lock đúng trong implementation, nhưng chưa chạy hai request thật
sự đồng thời. Phase 7 sẽ thực hiện integration test cạnh tranh và chỉ khi test đó đạt
mới có thể tuyên bố đã chứng minh chống bán trùng.

## Kiểm thử thủ công

Khởi động trên đúng database development:

```powershell
cd D:\PROJECT\Refurbished-Tech\backend-java
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
& .\scripts\Use-LocalEnvironment.ps1
java -jar .\target\refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Sau khi tạo Product, nhận DeviceUnit và hoàn tất inspection như Phase 5:

```powershell
$baseUrl = 'http://127.0.0.1:8080/api'
$body = @{
    customerName = 'Nguyen Van A'
    deviceUnitIds = @($availableDeviceId)
} | ConvertTo-Json

$order = Invoke-RestMethod "$baseUrl/orders/checkout" `
    -Method Post -ContentType 'application/json' -Body $body
$order

Invoke-RestMethod "$baseUrl/orders/$($order.id)"
Invoke-RestMethod "$baseUrl/device-units/$availableDeviceId"
# Order: COMPLETED; DeviceUnit: SOLD.

# Gửi lại cùng device phải trả HTTP 409 và không tạo Order thứ hai.
Invoke-RestMethod "$baseUrl/orders/checkout" `
    -Method Post -ContentType 'application/json' -Body $body
```

## Java/Spring và mapping

| Khái niệm | Vai trò | .NET / Node tương đương |
|---|---|---|
| Aggregate `Order` | Quản lý items và total nhất quán | C# domain aggregate / service model |
| Cascade persist | Lưu Order sẽ lưu các OrderItem mới | EF navigation graph / Sequelize include transaction |
| Price snapshot | Bảo toàn giá tại thời điểm giao dịch | Order line price trong EF/Sequelize |
| `@Transactional` | Atomicity cho toàn checkout | EF transaction / Sequelize callback transaction |
| Pessimistic lock | Khóa row DeviceUnit đến commit/rollback | SQL `FOR UPDATE` / Sequelize update lock |
| Dirty checking | Ghi trạng thái DeviceUnit đã đổi | EF Core change tracking |
| `@EntityGraph` | Fetch items cần cho response trong transaction đọc | EF `Include` / Sequelize `include` |
| Unique constraint | Tuyến bảo vệ cuối cho một device/một OrderItem | Unique index trong EF/Sequelize migration |

## 5 câu hỏi phỏng vấn Phase 6

1. Vì sao OrderItem phải lưu unitPrice thay vì luôn đọc DeviceUnit.salePrice?
2. `@Transactional` bảo đảm điều gì khi checkout ba thiết bị nhưng thiết bị thứ ba lỗi?
3. Cascade persist và orphanRemoval khác nhau thế nào?
4. Vì sao nhiều row lock nên được lấy theo một thứ tự ổn định?
5. `@EntityGraph` giúp gì khi `open-in-view=false`?
