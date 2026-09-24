# Phase 8 — Warranty tối thiểu và core backend hoàn thành

## Phạm vi

Warranty gắn trực tiếp với một DeviceUnit vật lý đã `SOLD`. Mỗi DeviceUnit có tối đa
một Warranty. Phase này không xây repair ticket, claim workflow, email nhắc hạn hoặc
gia hạn bảo hành.

Warranty gồm:

- UUID primary key.
- `deviceUnitId` unique và foreign key `ON DELETE RESTRICT`.
- `durationMonths` từ 1 đến 36.
- `startsOn` theo ngày UTC tại lúc cấp.
- `endsOn = startsOn.plusMonths(durationMonths)`.
- `createdAt` là `Instant`.

## API

| Method | URL | Hành vi |
|---|---|---|
| POST | `/api/warranties` | Cấp Warranty cho DeviceUnit SOLD |
| GET | `/api/warranties/{id}` | Tra cứu theo Warranty ID |
| GET | `/api/warranties/device-unit/{deviceUnitId}` | Tra cứu theo thiết bị vật lý |

Request:

```json
{"deviceUnitId":"UUID","durationMonths":12}
```

POST thành công trả `201 Created` và `Location: /api/warranties/{id}`. Thiết bị chưa
bán hoặc đã có Warranty trả `409 BUSINESS_CONFLICT`; không tồn tại trả 404; thời hạn
ngoài 1–36 trả `400 VALIDATION_ERROR`.

## Transaction và invariant

`WarrantyService.issue()` chạy trong `@Transactional` và lấy
`PESSIMISTIC_WRITE` trên đúng DeviceUnit. Trong khi giữ lock, service kiểm tra trạng
thái `SOLD` và kiểm tra Warranty hiện có. Hai request cấp warranty cho cùng thiết bị
vì thế được tuần tự hóa. Unique constraint `warranties.device_unit_id` là bảo vệ cuối
tại database.

JPA `@OneToOne(fetch = LAZY)` biểu diễn quan hệ một warranty/một thiết bị. API trả DTO,
không serialize entity hoặc lazy proxy trực tiếp.

`LocalDate` phù hợp với ngày hiệu lực không có giờ; PostgreSQL lưu bằng `DATE`.
`Instant` phù hợp với thời điểm record được tạo; PostgreSQL lưu bằng `TIMESTAMPTZ`.

## Kiểm thử thủ công

Sau khi có `$soldDeviceId` từ checkout Phase 6:

```powershell
$baseUrl = 'http://127.0.0.1:8080/api'
$body = @{
    deviceUnitId = $soldDeviceId
    durationMonths = 12
} | ConvertTo-Json

$warranty = Invoke-RestMethod "$baseUrl/warranties" `
    -Method Post -ContentType 'application/json' -Body $body
$warranty

Invoke-RestMethod "$baseUrl/warranties/$($warranty.id)"
Invoke-RestMethod "$baseUrl/warranties/device-unit/$soldDeviceId"

# Gửi lại body phải trả HTTP 409.
Invoke-RestMethod "$baseUrl/warranties" `
    -Method Post -ContentType 'application/json' -Body $body
```

Database chạy ứng dụng vẫn phải là `127.0.0.1:55432/refurbished_dev`, role
`refurbished_app`.

## Java/Spring và mapping

| Khái niệm | Vai trò | .NET / Node tương đương |
|---|---|---|
| `@OneToOne` | Một Warranty cho một DeviceUnit | EF one-to-one / Sequelize `hasOne` |
| `LocalDate` | Ngày lịch không có timezone/time | C# `DateOnly` / chuỗi ISO date |
| `Instant` | Thời điểm UTC tuyệt đối | `DateTimeOffset` / JavaScript Date |
| `ON DELETE RESTRICT` | Không cho xóa device còn Warranty | Restrict FK trong EF/Sequelize migration |
| Unique constraint | Bảo đảm một warranty/device | Unique index |
| Pessimistic lock | Tuần tự hóa hai request cấp warranty | SQL `FOR UPDATE` |

## 5 câu hỏi phỏng vấn Phase 8

1. Khi nào dùng `LocalDate`, khi nào dùng `Instant`?
2. Vì sao `@OneToOne` trong Java vẫn cần UNIQUE constraint ở PostgreSQL?
3. Vì sao kiểm tra `existsByDeviceUnitId()` phải chạy khi đang giữ row lock?
4. `ON DELETE RESTRICT` bảo vệ dữ liệu Warranty như thế nào?
5. Bean Validation và constructor invariant khác vai trò ra sao?

## Trạng thái roadmap

Phase 0–8 đã hoàn thành core backend. Phase 9 là bước đánh giá tùy chọn đối với auth,
JWT/OAuth, Redis, reservation expiration, email, upload/storage, frontend integration,
Docker và deployment. Không mặc định chuyển toàn bộ feature Node cũ sang Java.
