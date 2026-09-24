# Báo cáo thực tế Phase 8

## Files created

- `warranty/Warranty.java`, `WarrantyRepository.java`, `WarrantyService.java`, `WarrantyController.java`
- `warranty/dto/CreateWarrantyRequest.java`, `WarrantyResponse.java`
- `db/migration/V4__create_warranties.sql`
- `src/test/java/com/example/refurbished/warranty/WarrantyTest.java`
- `src/test/java/com/example/refurbished/WarrantyIT.java`
- `docs/PHASE-8.md`, `docs/PHASE-8-REPORT.md`

## Files modified

- `src/test/java/com/example/refurbished/OrderCheckoutIT.java`
- `backend-java/README.md`
- Root `README.md`

## Commands executed

```powershell
.\mvnw.cmd ... clean verify
java -jar target/refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
Invoke-WebRequest http://127.0.0.1:8080/api/health
```

## Dependencies added

Không thêm dependency.

## What was implemented

- Warranty gắn với DeviceUnit vật lý SOLD.
- Một warranty/device bằng service rule, row lock và unique constraint.
- Thời hạn 1–36 tháng; ngày kết thúc được tính và lưu.
- POST issue, GET by ID, GET by DeviceUnit.
- Flyway V4 và PostgreSQL constraints.

## What was not implemented

- Claim/repair workflow, gia hạn, hủy, email hoặc file đính kèm.
- Authentication, payment, frontend integration và production deployment.
- Các feature tùy chọn Phase 9.

## Tests executed

| Nhóm | Tests | Kết quả |
|---|---:|---|
| Unit tests | 18 | PASS |
| Integration tests | 51 | PASS |
| **Tổng** | **69** | **0 failures, 0 errors, 0 skipped** |

## Build and startup result

- `clean verify`: **BUILD SUCCESS**.
- Test database: `127.0.0.1:55433/refurbished_test`, Flyway V4 success.
- Development database: `127.0.0.1:55432/refurbished_dev`, Flyway V3 → V4 success.
- Packaged JAR started; health returned HTTP 200 `{"status":"UP"}`.
- Smoke-test Java process was stopped afterward.
- Logs: `.run/phase8-build.log`, `.run/phase8-startup.log`.

## Manual testing

Xem `docs/PHASE-8.md`. Smoke test không tạo Warranty demo trong development database.

## CV integrity

**IMPLEMENTED:** Product, per-device inventory, inspection lifecycle, Order/OrderItem,
transactional checkout, pessimistic concurrency protection và minimal Warranty.

**TESTED:** 69 automated tests trên build cuối, gồm concurrent HTTP checkout trên
PostgreSQL thật; packaged JAR startup và health trên development database.

**DEPLOYED:** Chưa. Chỉ chạy local.

**PLANNED/OPTIONAL:** Phase 9 integrations sau khi đánh giá giá trị từng feature.
