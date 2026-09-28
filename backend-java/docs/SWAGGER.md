# Thử API bằng Swagger UI

Swagger được bổ sung bằng `springdoc-openapi-starter-webmvc-ui` 2.8.16 cho Spring Boot
3.5. Schema được sinh từ controller/DTO hiện có; không tạo backend hoặc database riêng.
Tương thích: https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot

## Khởi động

Database dev phải đang chạy. Sau khi build JAR mới, dừng backend cũ bằng Ctrl+C rồi:

```powershell
cd D:\PROJECT\Refurbished-Tech\backend-java
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
& .\scripts\Start-Local.ps1
```

- UI: http://127.0.0.1:8080/swagger-ui/index.html
- URL chuyển hướng: http://127.0.0.1:8080/swagger-ui.html
- Schema JSON: http://127.0.0.1:8080/v3/api-docs

Nếu dùng `-Port 8082`, thay 8080 bằng 8082. Không chạy thêm một backend trên cổng đã dùng.
Mặc định docs tắt; profile dev/test bật. Chỉ URL tài liệu được mở public; API nghiệp vụ
vẫn kiểm tra JWT và quyền. Swagger không tự tạo ADMIN hoặc bỏ qua mật khẩu.

## Đăng nhập và Authorize

1. Mở nhóm `auth`, chọn **POST /api/auth/login → Try it out**.
2. Điền email và mật khẩu thật của tài khoản đã tạo, bấm **Execute**.
3. Copy giá trị `accessToken` trong response 200.
4. Bấm **Authorize**, dán riêng token vào `bearerAuth` (không thêm chữ `Bearer`), rồi Close.
5. Thử **GET /api/auth/me → Try it out → Execute** để kiểm tra tài khoản.

Swagger không tự lưu lại token qua reload. Khi đổi mật khẩu/logout-all, token bị thu
hồi; đăng nhập lại rồi thay token trong Authorize. 401 thường do token thiếu/hết hạn/bị
thu hồi; 403 là tài khoản không đủ quyền.

## Luồng thử nghiệp vụ đầy đủ

Ví dụ dưới đây là dữ liệu mẫu, cần đổi modelCode/serial nếu đã tạo trước đó. Copy ID
từ response bước trước thay vì dùng UUID mẫu do Swagger tự sinh.

### 1. Tạo Product (ADMIN)

`POST /api/products`:

```json
{
  "modelCode": "MBA-M1-DEMO-01",
  "name": "MacBook Air M1",
  "brand": "Apple",
  "specificationSummary": "RAM 8GB, SSD 256GB",
  "active": true
}
```

Copy `id` làm `productId`.

### 2. Nhập một máy

`POST /api/device-units` (ADMIN hoặc STAFF):

```json
{
  "productId": "THAY-BANG-ID-PRODUCT",
  "serialNumber": "MBA-DEMO-001"
}
```

Copy `id` làm ID máy; response có status RECEIVED.

### 3. Kiểm định

- `POST /api/device-units/{id}/start-inspection`: điền ID máy, không cần body.
- `POST /api/device-units/{id}/complete-inspection`: cùng ID, body:

```json
{
  "passed": true,
  "grade": "A",
  "batteryHealth": 92,
  "salePrice": 12500000,
  "inspectionNotes": "Da kiem tra man hinh, ban phim va pin."
}
```

Response AVAILABLE. Nếu không đo được pin, bỏ batteryHealth và dùng
`batteryHealthUnavailableReason`; không gửi cả hai giá trị có nội dung.

### 4. Chốt bán

`POST /api/orders/checkout`:

```json
{
  "customerName": "Khach demo",
  "deviceUnitIds": ["THAY-BANG-ID-MAY"]
}
```

Response 201 chứa đơn COMPLETED, thiết bị trở thành SOLD. Dùng ID đơn thử
`GET /api/orders/{id}`. Bán lại cùng máy trả 409 là đúng quy tắc chống bán trùng.

### 5. Cấp bảo hành

`POST /api/warranties`:

```json
{
  "deviceUnitId": "THAY-BANG-ID-MAY",
  "durationMonths": 12
}
```

Tra cứu bằng `GET /api/warranties/device-unit/{deviceUnitId}` hoặc ID bảo hành.

## Lưu ý khi thử

API vận hành mới: `GET /api/orders`, tìm `serialNumber` trong `GET /api/device-units`,
`GET /api/audit-events` (ADMIN), và header `Idempotency-Key` của checkout.
Xem [hợp đồng và tư duy nghiệp vụ](STEP-2-OPERATIONS.md) hoặc chạy
[collection Postman](../postman/README.md) để tự động đi trọn luồng.

- Execute là request thật, ghi vào database dev nếu backend chạy profile dev.
- Schema sinh giá trị minh họa, không đảm bảo các ID đó tồn tại hoặc đúng trạng thái.
- GET danh sách dùng page bắt đầu từ 0, size từ 1 đến 100.
- OAuth exchange cần code thật từ callback Google; đăng nhập Google mở
  `/oauth2/authorization/google` bằng trình duyệt và cần cấu hình credentials.
- ADMIN recovery là công cụ local, không xuất hiện như một HTTP API trên Swagger.
- UI chỉ mô tả quyền; `SecurityFilterChain` mới thực sự bảo vệ API.

## Kiểm thử kỹ thuật

Ngày 2026-09-25: 21 unit tests + 65 integration tests PASS (86 tổng cộng).
Integration log: `.run/swagger-integration.log`; không thực hiện test ghi dữ liệu dev.

`SecurityIT.swaggerIsPublicButBusinessEndpointsRemainProtected` kiểm tra HTML,
JavaScript UI, swagger-config, JSON schema, toàn bộ đường dẫn API, JWT scheme,
public operations, giấu JWT nội bộ và bảo vệ API khi chưa đăng nhập.
