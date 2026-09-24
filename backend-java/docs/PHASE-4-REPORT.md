# Phase 4 — Kết quả thực tế

Ngày xác minh: 2026-09-23 (Asia/Bangkok).

## Đã triển khai

- Product: entity, repository, service, DTO tạo/sửa/response, controller.
- DeviceUnit: entity, repository, service, DTO nhập/response, controller.
- DeviceStatus: RECEIVED, INSPECTING, AVAILABLE, RESERVED, SOLD, REJECTED.
- ConditionGrade: A, B, C; enum lưu chuỗi.
- API tạo/xem/sửa Product; nhập/xem/lọc DeviceUnit; pagination có giới hạn.
- Thiết bị mới luôn RECEIVED, thông tin inspection chưa được xác nhận.
- DTO validation, identifier normalization, từ chối unknown fields và pin số lẻ.
- Error responses thống nhất, không trả SQL/stack trace ra client.
- Flyway V1 tạo products/device_units, unique/FK/check constraints và index.
- Migration dùng cùng guarded DataSource; Hibernate tiếp tục validate schema.

Không có API hard-delete hoặc sửa status/serial/Product của DeviceUnit.
Các bước inspection, lifecycle transitions, checkout, Order và Warranty chưa làm.

## File tạo mới

```text
src/main/java/com/example/refurbished/
  common/config/MigrationConfiguration.java
  common/api/PageResponse.java
  common/exception/ApiError.java
  common/exception/ApiExceptionHandler.java
  common/exception/BusinessConflictException.java
  common/exception/ResourceNotFoundException.java
  product/Product.java
  product/ProductRepository.java
  product/ProductService.java
  product/ProductController.java
  product/dto/CreateProductRequest.java
  product/dto/UpdateProductRequest.java
  product/dto/ProductResponse.java
  inventory/DeviceUnit.java
  inventory/DeviceStatus.java
  inventory/ConditionGrade.java
  inventory/DeviceUnitRepository.java
  inventory/InventoryService.java
  inventory/InventoryController.java
  inventory/dto/CreateDeviceUnitRequest.java
  inventory/dto/DeviceUnitResponse.java
src/main/resources/db/migration/V1__create_products_and_device_units.sql
src/test/java/com/example/refurbished/ProductInventoryIT.java
docs/PHASE-4.md
docs/PHASE-4-REPORT.md
```

## File có sẵn được sửa

- pom.xml: thêm Flyway Core + PostgreSQL support.
- src/main/resources/application.yml: từ chối unknown JSON fields và float → int coercion.
- README.md: cập nhật phạm vi Phase 4, migration, test và link hướng dẫn API.

Không sửa source Node/frontend, cấu hình ops/Docker cũ hoặc root .gitignore.

## Dependency thêm

| Dependency | Phiên bản do Spring Boot quản lý | Mục đích |
|---|---|---|
| org.flywaydb:flyway-core | 11.7.2 | Versioned SQL migrations và checksum history |
| org.flywaydb:flyway-database-postgresql | 11.7.2 | PostgreSQL support cho Flyway |

Không thêm Mockito, Lombok, Redis, security, payment hoặc thư viện mapping DTO.
Flyway được giải thích trước khi cài. Migration là nguồn tạo schema duy nhất;
không đổi Hibernate sang update/create và không tự baseline schema có sẵn.

## Commands thực thi chính

```powershell
docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose --env-file .env.local -f compose.local.yml ps

$env:MAVEN_USER_HOME = Join-Path $PWD '.maven'
& .\scripts\Use-LocalEnvironment.ps1
$env:DEBUG = 'false'
.\mvnw.cmd '-Dmaven.repo.local=D:/PROJECT/Refurbished-Tech/backend-java/.m2/repository' --batch-mode --no-transfer-progress clean verify

java -jar target/refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --debug=false
```

Build chạy 2 lần: lần đầu lỗi schema validation vì Hibernate suy ra CHAR(1) cho
grade trong khi V1 dùng VARCHAR(1). Sửa bằng mapping @JdbcTypeCode(SqlTypes.VARCHAR),
giữ nguyên migration đã áp dụng, rồi chạy lại toàn bộ clean verify thành công.

JAR smoke test dùng Start-Process -WindowStyle Hidden, gọi HTTP local, kiểm tra
migration history và pg_stat_activity bằng psql trong CHỈ container dev mới.
Khối finally dừng đúng Java process vừa tạo. Không dừng process khác.

## Database đích

- Tests/migrations test: 127.0.0.1:55433/refurbished_test, role refurbished_test.
- JAR/migrations dev: 127.0.0.1:55432/refurbished_dev, role refurbished_app.
- Migration V1 applied thành công ở cả hai database.
- Runtime sessions xác minh đúng database/user dev.
- Không sử dụng credentials Node; không kết nối database/Redis production.
- Smoke test dev chỉ GET, không tạo sample business data.
- Test cleanup chỉ xóa fixture theo các Product ID do test tạo, sau khi xác minh
  current_database() là refurbished_test. Không truncate hoặc xóa volume.

## Tests/build đã chạy

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| LocalDataSourceConfigurationTest | 10 | 0 | 0 | 0 |
| FoundationIT | 2 | 0 | 0 | 0 |
| ProductInventoryIT | 27 | 0 | 0 | 0 |
| Tổng | 39 | 0 | 0 | 0 |

Final result: **BUILD SUCCESS**, exit code 0. Packaged JAR khởi động thành công,
JPA EntityManagerFactory initialized sau khi schema được migrate và validate.

ProductInventoryIT kiểm tra:

- Flyway và ứng dụng dùng cùng DataSource; migration history V1 thành công.
- Product code và serial được chuẩn hóa, mỗi thiết bị có UUID độc lập.
- Giá BigDecimal và metadata được lưu/đọc, trạng thái ban đầu RECEIVED.
- Duplicate modelCode và serial (kể cả khác Product/case) trả 409.
- Duplicate transaction rollback; request hợp lệ kế tiếp vẫn ghi được dữ liệu.
- Product inactive không nhận thêm thiết bị; thiết bị cũ không bị thay đổi.
- Filtering/pagination, response không serialize JPA relationship.
- Input bắt buộc/format/giá/pin/enum/unknown fields được kiểm tra qua HTTP.
- Client không thể gửi status=SOLD hoặc inspectionPassed=true khi nhập máy.
- Not found trả 404; malformed UUID, pagination/status query sai trả 400.
- Ghi SQL trực tiếp vẫn bị constraint chặn pin/giá/serial sai, FK sai,
  xóa Product đang có thiết bị, hoặc đặt AVAILABLE khi chưa đủ inspection data.

Các SQLState 23505 xuất hiện trong log là lỗi duplicate được test cố ý tạo để
xác minh HTTP 409, không phải lỗi test cuối cùng.

## Smoke test packaged JAR trên dev

```text
GET /api/health                              -> 200 {"status":"UP"}
GET /api/products                            -> 200, items=[], totalElements=0
GET /api/device-units?status=RECEIVED          -> 200, items=[], totalElements=0
flyway_schema_history: version 1, success=true
pg_stat_activity: refurbished_dev / refurbished_app
```

Java process đã dừng sau xác minh. Hai PostgreSQL local containers vẫn chạy.
Hướng dẫn chạy lại, POST/PUT examples và mapping Java/.NET/Node ở PHASE-4.md.

## Evidence local

- .run/phase4-build.log
- .run/phase4-startup.log
- target/surefire-reports/com.example.refurbished.common.config.LocalDataSourceConfigurationTest.txt
- target/failsafe-reports/com.example.refurbished.FoundationIT.txt
- target/failsafe-reports/com.example.refurbished.ProductInventoryIT.txt

Log và target bị Git ignore, có thể mất khi clean. Báo cáo ghi nhận một lần chạy
thực tế, không thay thế việc chạy lại tests sau thay đổi.

## CV integrity và phase tiếp theo

**IMPLEMENTED:** Product catalog, individual device intake/read APIs, validation,
database constraints, migration và error handling trong Java/Spring Boot.

**TESTED:** 39 tests ở lần build cuối; HTTP/JPA/PostgreSQL integration và packaged
JAR startup đã chạy. Chưa có executed checkout concurrency test.

**DEPLOYED:** Không. Chỉ chạy local development.

**PLANNED:** Phase 5 lifecycle/inspection, Phase 6 orders, Phase 7 concurrency
proof, Phase 8 warranty. Enum và constraint không đồng nghĩa lifecycle đã hoàn tất.

Không có số liệu traffic, user, doanh thu, latency improvement hoặc production scale.
Dừng sau Phase 4, chờ người dùng duyệt Phase 5.
