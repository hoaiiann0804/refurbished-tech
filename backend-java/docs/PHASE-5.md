# Phase 5 — Vòng đời kiểm định DeviceUnit

## Phạm vi đã triển khai

Phase 5 triển khai state machine tối thiểu cho từng thiết bị vật lý:

```text
RECEIVED -> INSPECTING -> AVAILABLE
                       -> REJECTED
```

Thiết bị không thể bỏ qua bước kiểm định, kiểm định lại sau khi đã có kết quả, hoặc
chuyển trực tiếp từ `RECEIVED` sang `AVAILABLE`. Các quy tắc nằm trong `DeviceUnit`
để mọi caller (HTTP, service khác hoặc test) đều tuân theo cùng một nghiệp vụ.

## API mới

| Method | URL | Điều kiện | Kết quả |
|---|---|---|---|
| POST | `/api/device-units/{id}/start-inspection` | Chỉ từ `RECEIVED` | `INSPECTING` |
| POST | `/api/device-units/{id}/complete-inspection` | Chỉ từ `INSPECTING` | `AVAILABLE` hoặc `REJECTED` |

Body hoàn tất kiểm định:

```json
{
  "passed": true,
  "grade": "A",
  "batteryHealth": 91,
  "salePrice": 12500000.00,
  "inspectionNotes": "Display, keyboard and ports passed.",
  "batteryHealthUnavailableReason": null
}
```

Nếu `passed=true`, bắt buộc có grade, salePrice, notes và một trong hai loại bằng
chứng về pin: `batteryHealth` hoặc `batteryHealthUnavailableReason`. Trường reason
dành cho thiết bị không có chỉ số pin; không được gửi đồng thời với batteryHealth.
Nếu `passed=false`, notes vẫn bắt buộc để ghi lý do từ chối.

Transition sai trả `409 BUSINESS_CONFLICT`. Dữ liệu kiểm định không hợp lệ về nghiệp
vụ trả `400 INVALID_INSPECTION`. Lỗi Bean Validation trả `400 VALIDATION_ERROR`.
Thiết bị không tồn tại trả `404 NOT_FOUND`.

## Transaction và khóa row

Mỗi transition chạy trong một service method có `@Transactional`. Repository đọc
DeviceUnit bằng `PESSIMISTIC_WRITE`, tương ứng SQL `SELECT ... FOR UPDATE` trên row
thiết bị. Lock được giữ đến commit hoặc rollback. Hai request sửa vòng đời cùng một
thiết bị vì thế không thể cùng đọc rồi ghi từ một trạng thái cũ.

Đây là bảo vệ cho transition kiểm định. Phase 5 chưa triển khai checkout, chưa chứng
minh hai khách hàng cạnh tranh mua cùng một thiết bị; bài test đó thuộc Phase 7.

Hibernate dirty checking phát hiện entity đã đổi trong transaction. `flush()` đẩy SQL
đến PostgreSQL để constraint được kiểm tra trước khi dựng response; flush chưa phải
commit. Nếu exception xảy ra, transaction rollback và transition không được lưu.

Migration V2 thêm `battery_health_unavailable_reason` và CHECK constraints để chặn
dữ liệu giả mạo ngay cả khi SQL không đi qua Java. Hibernate tiếp tục dùng
`ddl-auto=validate`; Flyway mới là thành phần thay đổi schema.

## Chạy thử thủ công bằng PowerShell

Database đích phải hiển thị đúng:

```text
jdbc:postgresql://127.0.0.1:55432/refurbished_dev
user: refurbished_app
```

Terminal 1:

```powershell
cd D:\PROJECT\Refurbished-Tech\backend-java
& .\scripts\Use-LocalEnvironment.ps1
java -jar .\target\refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Terminal 2 (tạo fixture mới trong database development):

```powershell
$baseUrl = 'http://127.0.0.1:8080/api'
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)

$product = Invoke-RestMethod "$baseUrl/products" -Method Post -ContentType 'application/json' -Body (@{
    modelCode = "MBA-M1-$suffix"
    name = 'MacBook Air M1 2020'
    brand = 'Apple'
} | ConvertTo-Json)

$device = Invoke-RestMethod "$baseUrl/device-units" -Method Post -ContentType 'application/json' -Body (@{
    productId = $product.id
    serialNumber = "MBA-$suffix"
} | ConvertTo-Json)

Invoke-RestMethod "$baseUrl/device-units/$($device.id)/start-inspection" -Method Post

$available = Invoke-RestMethod "$baseUrl/device-units/$($device.id)/complete-inspection" `
    -Method Post -ContentType 'application/json' -Body (@{
        passed = $true
        grade = 'A'
        batteryHealth = 91
        salePrice = 12500000
        inspectionNotes = 'Display, keyboard and ports passed.'
    } | ConvertTo-Json)
$available

# Gọi lần hai phải trả HTTP 409 vì AVAILABLE không thể complete inspection lại.
Invoke-RestMethod "$baseUrl/device-units/$($device.id)/complete-inspection" `
    -Method Post -ContentType 'application/json' -Body (@{
        passed = $false
        inspectionNotes = 'Invalid second completion.'
    } | ConvertTo-Json)
```

Để thử nhánh từ chối, tạo DeviceUnit khác, start inspection rồi complete với body
`{"passed":false,"inspectionNotes":"Mainboard diagnostics failed."}`. Kết quả phải
là `REJECTED`, `inspectionPassed=false` và có `inspectedAt`.

## Java/Spring cần hiểu

| Khái niệm | Nó là gì và vì sao cần | .NET / Node tương đương |
|---|---|---|
| Entity chứa hành vi | Method bảo vệ invariant gần dữ liệu | Domain entity C#; model/service method Node |
| State machine | Chỉ cho phép các cạnh transition đã định nghĩa | Enum + domain rules C#/TypeScript |
| `@Transactional` | Transition, lock và update cùng transaction | EF Core / Sequelize transaction |
| `PESSIMISTIC_WRITE` | Khóa row trước khi kiểm tra và sửa | EF raw `FOR UPDATE`; Sequelize update lock |
| Dirty checking | Hibernate tự UPDATE managed entity đã đổi | EF Core change tracking |
| Bean Validation | Kiểm tra hình dạng/range request | DataAnnotations / Joi |
| Domain validation | Quy tắc phụ thuộc nhiều field và trạng thái | Service/entity business rules |
| CHECK constraint | Tuyến phòng thủ cuối tại PostgreSQL | CHECK constraint với EF/Sequelize |
| `Instant` | Thời điểm UTC, map sang `TIMESTAMPTZ` | `DateTimeOffset` / JavaScript Date |

## 5 câu hỏi phỏng vấn Phase 5

1. Vì sao kiểm tra `status == RECEIVED` ngoài transaction không đủ khi có hai request đồng thời?
2. `PESSIMISTIC_WRITE` khóa row nào, giữ đến lúc nào, và rollback ảnh hưởng ra sao?
3. Bean Validation, domain validation và database constraint khác vai trò thế nào?
4. Hibernate dirty checking khác `flush()` và transaction commit thế nào?
5. Vì sao transition nên là method của entity thay vì controller tự gán status?

