# Báo cáo thực tế Phase 5

## Files created

- `src/main/java/com/example/refurbished/common/exception/InvalidInspectionException.java`
- `src/main/java/com/example/refurbished/inventory/dto/CompleteInspectionRequest.java`
- `src/main/resources/db/migration/V2__require_inspection_evidence.sql`
- `src/test/java/com/example/refurbished/inventory/DeviceUnitTest.java`
- `src/test/java/com/example/refurbished/DeviceLifecycleIT.java`
- `docs/PHASE-5.md`
- `docs/PHASE-5-REPORT.md`

## Files modified

- `src/main/java/com/example/refurbished/common/exception/ApiExceptionHandler.java`
- `src/main/java/com/example/refurbished/inventory/DeviceUnit.java`
- `src/main/java/com/example/refurbished/inventory/DeviceUnitRepository.java`
- `src/main/java/com/example/refurbished/inventory/InventoryService.java`
- `src/main/java/com/example/refurbished/inventory/InventoryController.java`
- `src/main/java/com/example/refurbished/inventory/dto/DeviceUnitResponse.java`

## Commands executed

Các lệnh chính đã chạy từ `backend-java`, dùng Maven local repository trong workspace:

```powershell
& .\scripts\Use-LocalEnvironment.ps1
.\mvnw.cmd '-Dmaven.repo.local=...\.m2\repository' --batch-mode --no-transfer-progress test
.\mvnw.cmd '-Dmaven.repo.local=...\.m2\repository' --batch-mode --no-transfer-progress clean verify
java -jar .\target\refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Đã gọi health endpoint, list DeviceUnit và truy vấn metadata/schema của PostgreSQL
development để xác minh startup cùng migration V2.

## Dependencies added

Không thêm dependency. Phase 5 dùng Spring Data JPA locking, Bean Validation, Flyway,
JUnit 5 và Spring Boot Test đã có từ các phase trước.

## What was implemented

- Transition `RECEIVED -> INSPECTING -> AVAILABLE|REJECTED`.
- Từ chối transition sai bằng HTTP 409.
- Quy tắc bằng chứng kiểm định cho nhánh pass/fail.
- Transaction và pessimistic row lock cho từng thao tác transition.
- Migration V2 và PostgreSQL CHECK constraints bổ sung.
- Unit tests cho domain và integration tests HTTP/JPA/PostgreSQL.

## What was not implemented

- Order, OrderItem, checkout và chuyển sang RESERVED/SOLD.
- Integration test hai checkout cạnh tranh; chưa có tuyên bố chống bán trùng.
- Warranty, authentication, Redis, payment, email và production deployment.

## Tests executed

`clean verify` chạy **53 tests**, tất cả đạt:

| Test class | Số test | Kết quả |
|---|---:|---|
| LocalDataSourceConfigurationTest | 10 | PASS |
| DeviceUnitTest | 5 | PASS |
| DeviceLifecycleIT | 9 | PASS |
| FoundationIT | 2 | PASS |
| ProductInventoryIT | 27 | PASS |
| **Tổng** | **53** | **0 failures, 0 errors, 0 skipped** |

Integration tests chỉ dùng `127.0.0.1:55433/refurbished_test`. Test xác minh tên
database trước khi tạo hoặc xóa fixture và chỉ xóa ID do test tạo.

## Build result

- Maven `clean verify`: **BUILD SUCCESS**.
- Packaged JAR khởi động thành công với profile `dev`.
- Health endpoint: HTTP 200, `{"status":"UP"}`.
- Flyway history trên development database: V1 và V2 đều success.
- V2 column có kiểu `varchar(1000)` như thiết kế.
- Java process smoke test đã dừng; hai PostgreSQL container local vẫn chạy.

Log thực thi: `.run/phase5-build.log` và `.run/phase5-startup.log`.

## Manual testing

Xem lệnh PowerShell trong `docs/PHASE-5.md`. Trước khi chạy, kiểm tra ứng dụng kết
nối `127.0.0.1:55432/refurbished_dev` bằng role `refurbished_app`; không dùng
production credentials.

## Trạng thái CV

| Hạng mục | Trạng thái |
|---|---|
| Device lifecycle và inspection invariants | IMPLEMENTED, TESTED |
| PostgreSQL migration/constraints | IMPLEMENTED, TESTED |
| Pessimistic locking cho inspection transition | IMPLEMENTED, TESTED theo request tuần tự |
| Concurrent checkout / duplicate-sale protection | PLANNED, chưa implemented/tested |
| Production deployment | Chưa deployed |

