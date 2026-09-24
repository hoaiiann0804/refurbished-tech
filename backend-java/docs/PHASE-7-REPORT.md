# Báo cáo thực tế Phase 7

## Files created

- `src/test/java/com/example/refurbished/CheckoutConcurrencyIT.java`
- `docs/PHASE-7.md`
- `docs/PHASE-7-REPORT.md`

## Files modified

- `backend-java/README.md`
- Root `README.md`

Không cần sửa production code trong Phase 7: transaction, deterministic lock order,
`PESSIMISTIC_WRITE` và unique constraint đã được đặt đúng ở Phase 6. Phase 7 bổ sung
bằng chứng concurrency thực thi trên PostgreSQL thật.

## Commands executed

```powershell
.\mvnw.cmd ... '-Dit.test=CheckoutConcurrencyIT' verify
.\mvnw.cmd ... clean verify
```

## Dependencies added

Không thêm dependency. Test dùng JDK concurrency utilities, JDBC, Spring Boot Test
và PostgreSQL đã có.

## What was implemented and tested

- Hai HTTP checkout cạnh tranh đúng cùng một DeviceUnit row.
- PostgreSQL xác nhận cả hai transaction cùng chờ lock trước khi test thả blocker.
- Một response 201, một response 409.
- Một Order COMPLETED, một OrderItem, DeviceUnit SOLD.
- Fixture cleanup chỉ tác động IDs do test tạo trong `refurbished_test`.

## What was not implemented

- Load/performance benchmark hoặc nhiều node ứng dụng.
- Lock timeout/retry policy và reservation expiration.
- Warranty, authentication, payment hoặc production deployment.

## Tests executed

Targeted concurrency test: **1 PASS**.

Full `clean verify`:

| Nhóm | Tests | Kết quả |
|---|---:|---|
| Unit tests | 16 | PASS |
| Integration tests | 46 | PASS |
| **Tổng** | **62** | **0 failures, 0 errors, 0 skipped** |

## Build result

- Maven `clean verify`: **BUILD SUCCESS**.
- Database: `jdbc:postgresql://127.0.0.1:55433/refurbished_test`.
- PostgreSQL schema version: V3.
- Full build log: `.run/phase7-build.log`.

Không cần startup smoke test mới vì Phase 7 chỉ thêm test, không thay đổi runtime
code, schema hay dependency; packaged application đã được smoke-test ở Phase 6.

## Manual testing

Concurrency timing khó xác minh đáng tin cậy bằng hai lệnh PowerShell gõ tay. Cách
tái lập chính xác là chạy:

```powershell
& .\scripts\Use-LocalEnvironment.ps1
.\mvnw.cmd '-Dit.test=CheckoutConcurrencyIT' verify
```

## CV integrity

| Hạng mục | Trạng thái |
|---|---|
| Transactional checkout | IMPLEMENTED, TESTED |
| Pessimistic row locking | IMPLEMENTED, concurrency-tested |
| Exactly one sale under two concurrent requests | TESTED trên local PostgreSQL |
| Performance/production scale | Chưa benchmark, chưa deployed |
