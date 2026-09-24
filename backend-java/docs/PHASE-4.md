# Phase 4 — Product và DeviceUnit

## Phạm vi

Product là một mẫu/cấu hình phần cứng; DeviceUnit là một thiết bị có serial riêng.
Không có Product.quantity, không tái sử dụng Sequelize table hoặc dữ liệu Node.
Mỗi thiết bị mới bắt buộc ở RECEIVED. Các giá trị grade, batteryHealth, salePrice
lúc nhập chỉ là thông tin sơ bộ, không chứng minh thiết bị đã qua kiểm định.
Các trường này có thể null lúc nhập và sẽ được xử lý ở Phase 5.

Đã có entity/repository/service/DTO/controller, validation, pagination, error
handling và migration PostgreSQL. Chưa có transition endpoint, checkout, Order,
Warranty hoặc security. Ứng dụng vẫn dành cho local development.

## API

| Method | URL | Hành vi |
|---|---|---|
| POST | /api/products | Tạo model; 201 + Location |
| GET | /api/products/{id} | Chi tiết; 404 nếu không có |
| GET | /api/products?active=true&page=0&size=20 | Lọc active tùy chọn, phân trang |
| PUT | /api/products/{id} | Thay name, brand, specificationSummary, active; giữ modelCode |
| POST | /api/device-units | Nhập thiết bị vào Product active; 201 + Location |
| GET | /api/device-units/{id} | Chi tiết thiết bị |
| GET | /api/device-units?productId=UUID&status=RECEIVED&page=0&size=20 | Lọc riêng hoặc kết hợp |

Trang bắt đầu từ 0; size mặc định 20, tối đa 100. Thứ tự cố định createdAt DESC,
id DESC. Không có DELETE, không có API đổi serial/Product của thiết bị và không
có API ghi đè status. PUT Product yêu cầu name, brand, active; bỏ summary nghĩa
là xóa summary. modelCode không nằm trong request sửa.

Response danh sách có cấu trúc ổn định:

```json
{"items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}
```

## Validation và database

- Model code: tối đa 64 ký tự. Serial: tối đa 100 ký tự.
- Cả hai trim rồi uppercase bằng Locale.ROOT. Bắt đầu bằng chữ/số ASCII;
  các ký tự còn lại cho phép chữ/số ASCII và `.`, `_`, `/`, `-`.
- Name tối đa 200, brand tối đa 100, cả hai không rỗng.
- Summary/inspection notes tối đa 2000.
- Grade: A/B/C. Battery health: số nguyên 0..100 nếu có.
- Giá: BigDecimal, lớn hơn 0 nếu có, tối đa 12 chữ số nguyên + 2 thập phân;
  ví dụ 12500000.25. Không silently round input có quá 2 chữ số thập phân.
- UUID sai, enum sai, JSON sai, field không được hỗ trợ: HTTP 400.
- batteryHealth=91.5 bị từ chối thay vì âm thầm đổi thành 91.
- Client gửi status, inspectionPassed hoặc modelCode trong PUT cũng bị từ chối.
- Duplicate modelCode hoặc serial: HTTP 409. Serial unique toàn hệ thống,
  không chỉ trong một Product. Không dùng exists-check làm bảo đảm uniqueness;
  unique constraint PostgreSQL là nguồn bảo vệ cuối cùng.
- Nhập thiết bị vào Product inactive: HTTP 409; Product không có: HTTP 404.

Migration V1 tạo products và device_units với UUID PK, unique constraints,
FK product_id (ON DELETE RESTRICT), CHECK cho enum/pin/giá/identifier và index
(product_id, status). Không thêm index trùng với index do PK/UNIQUE tạo sẵn.
Các timestamp dùng Instant ↔ TIMESTAMPTZ. Enum lưu tên, không lưu ordinal.

Constraint cho AVAILABLE/RESERVED/SOLD yêu cầu inspectionPassed=true, timestamp,
grade, giá và notes. Đây chỉ là constraint dữ liệu sellable, KHÔNG phải state
machine. Chưa triển khai/kiểm thử các transition RECEIVED → INSPECTING → AVAILABLE.

## Error contract

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "fieldErrors": {"name": "must not be blank"}
}
```

404 dùng NOT_FOUND; xung đột nghiệp vụ dùng BUSINESS_CONFLICT; vi phạm constraint
dùng DATA_CONFLICT. API không trả SQL, tên constraint, stack trace hoặc giá trị
request nhạy cảm. Lỗi ngoài dự kiến trả 500 INTERNAL_ERROR và được log phía server.
Các lỗi HTTP framework vẫn giữ đúng status qua ResponseEntityExceptionHandler.

## Chạy thử bằng PowerShell

Terminal 1 (database đích chính xác: 127.0.0.1:55432/refurbished_dev,
role refurbished_app; startup sẽ áp dụng V1 nếu chưa có):

```powershell
cd D:\PROJECT\Refurbished-Tech\backend-java
& .\scripts\Use-LocalEnvironment.ps1
java -jar .\target\refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Nếu PowerShell chặn script, xem hướng dẫn execution policy theo Process trong
README. Nếu database đang dừng, dùng lệnh Compose local của README trước.

Terminal 2: các lệnh này tạo dữ liệu demo mới trong development database:

```powershell
$baseUrl = 'http://127.0.0.1:8080/api'
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$productBody = @{
    modelCode = "MBA-M1-8-256-$suffix"
    name = 'MacBook Air M1 2020'
    brand = 'Apple'
    specificationSummary = 'RAM 8GB / SSD 256GB'
} | ConvertTo-Json

$product = Invoke-RestMethod "$baseUrl/products" -Method Post -ContentType 'application/json' -Body $productBody
$product

$deviceBody = @{
    productId = $product.id
    serialNumber = "mba001-$suffix"
    grade = 'A'
    batteryHealth = 91
    salePrice = 12500000
} | ConvertTo-Json

$device = Invoke-RestMethod "$baseUrl/device-units" -Method Post -ContentType 'application/json' -Body $deviceBody
$device
# Kết quả: serial chữ hoa, status RECEIVED, inspectionPassed và inspectedAt null.

Invoke-RestMethod "$baseUrl/device-units?productId=$($product.id)&status=RECEIVED&page=0&size=20"

# Gửi lại đúng serial -> HTTP 409. PowerShell sẽ báo lỗi HTTP, đây là kết quả mong đợi.
Invoke-RestMethod "$baseUrl/device-units" -Method Post -ContentType 'application/json' -Body $deviceBody
```

Ví dụ ngừng nhận máy mới cho Product:

```powershell
$updateBody = @{
    name = $product.name
    brand = $product.brand
    specificationSummary = $product.specificationSummary
    active = $false
} | ConvertTo-Json
Invoke-RestMethod "$baseUrl/products/$($product.id)" -Method Put -ContentType 'application/json' -Body $updateBody
```

Thiết bị đã nhập vẫn tồn tại và giữ trạng thái. Intake mới cho Product này bị từ chối.

## Transaction và cách đọc code

Controller nhận DTO, chạy @Valid, gọi service; không gọi repository trực tiếp.
Service có @Transactional(readOnly=true) mặc định cho GET; method tạo/sửa có
@Transactional để commit/rollback. Entity không được serialize trực tiếp.

saveAndFlush/flush gửi SQL và kiểm tra constraint trước khi dựng response;
flush KHÔNG phải commit. Spring commit khi service method hoàn tất qua proxy.
Product sửa trong transaction là managed entity; Hibernate dirty checking ghi
thay đổi, không cần gọi save lần nữa.

Intake và sửa Product cùng lấy row lock trên Product để kiểm tra active trong
transaction. Đây không phải triển khai checkout/concurrency Phase 7 và chưa có
kiểm thử cạnh tranh bán hàng. Không được mô tả chức năng chống bán trùng là hoàn thành.

## Java/Spring — mapping và câu hỏi

| Khái niệm | Là gì / vì sao cần | .NET / Node tương đương | Câu hỏi phỏng vấn |
|---|---|---|---|
| @Entity, @Id | Mapping object vào bảng/PK | EF Core entity / Sequelize model | Vì sao entity cần constructor không tham số? |
| JpaRepository | CRUD/query infrastructure | DbContext queries / Sequelize find/create | Spring tạo implementation repository ở đâu? |
| @ManyToOne(LAZY) | FK từ từng máy về model, tránh tải không cần thiết | EF navigation / Sequelize belongsTo | Lazy loading có rủi ro gì ngoài transaction? |
| DTO record | Hợp đồng API bất biến, không lộ entity internals | C# record/request model | Vì sao không nhận trực tiếp entity từ client? |
| @Valid + Bean Validation | Kiểm tra request tại boundary | DataAnnotations / Joi | Validation có thay thế unique constraint không? |
| @Transactional | Transaction boundary của service | EF BeginTransaction / Sequelize transaction | Flush khác commit thế nào? |
| @RestControllerAdvice | Xử lý lỗi chung và HTTP status | ASP.NET exception middleware / Express errorHandler | Vì sao không trả SQL exception cho client? |
| Flyway | Migration SQL có version/checksum | EF migrations / Sequelize migrations | Vì sao không sửa V1 sau khi áp dụng? |
| EnumType.STRING | Lưu tên enum để không phụ thuộc thứ tự khai báo | C# enum string conversion / Sequelize enum | Đổi tên enum có cần migration không? |
| BigDecimal | Decimal arithmetic/precision cho tiền | C# decimal | Vì sao tránh double cho giá? |

Hibernate suy ra CHAR khi enum string có length=1. Grade được khai báo thêm
@JdbcTypeCode(SqlTypes.VARCHAR) để khớp VARCHAR(1) trong migration. Đây là mapping
của Hibernate đang dùng, không phải thêm một thư viện mới.

## 5 câu hỏi luyện phỏng vấn Phase 4

1. Product khác DeviceUnit như thế nào, và vì sao không có Product.quantity?
2. Vì sao unique check trong Java không thay thế UNIQUE constraint ở PostgreSQL?
3. @Transactional, dirty checking, flush và commit liên quan thế nào?
4. Vì sao dùng DTO và LAZY association khi open-in-view=false?
5. Flyway migration khác Hibernate ddl-auto:validate thế nào?

## Kiểm thử

`mvnw.cmd clean verify` chạy unit tests và HTTP/JPA/PostgreSQL integration tests.
Các test mới nằm trong ProductInventoryIT. Test xác minh current_database trước
khi tạo/xóa fixture; cleanup chỉ xóa Product IDs do chính test tạo, trong database
refurbished_test. Không có truncate hoặc reset development database.

Không có concurrency sale test, không benchmark, không production deployment.
Kết quả chạy thực tế được ghi ở PHASE-4-REPORT.md.
