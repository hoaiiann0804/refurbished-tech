# Vận hành Java

## CI

`.github/workflows/java-ci.yml` chạy JDK 21, PostgreSQL test riêng với application role
không superuser, `mvn verify`, kiểm tra cú pháp UI và lưu reports/JAR. Credentials sinh
riêng mỗi run. Chưa coi CI trên GitHub đạt nếu chưa có workflow run thật.

## Metrics và readiness

- `/actuator/health/liveness`: process có sống; không phụ thuộc database.
- `/actuator/health/readiness`: gồm DB; response public chỉ có status.
- `/actuator/metrics`, `/actuator/prometheus`: chỉ ADMIN với JWT. Không expose env/configdump.
- `X-Request-ID` được giới hạn ký tự/độ dài, trả lại trên response và đặt trong MDC.
  Không dùng ID/email/serial làm nhãn metrics. HTTP status/latency, JVM và Hikari pool
  là metrics sẵn có; chưa có dashboard, cảnh báo, hay SLO đã đo.

Cấu hình dựa trên [Spring Boot 3.5 Actuator](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html).
Theo dõi nghiệp vụ dùng audit; log kỹ thuật không thay thế audit transaction.

## Sao lưu và diễn tập phục hồi

Từ `backend-java`, với Docker Desktop đang chạy:

```powershell
& .\scripts\Backup-Local.ps1 -Source dev
& .\scripts\Restore-Drill.ps1 -BackupPath 'DUONG-DAN-DUMP-VUA-TAO'
```

Backup chỉ đọc DB, dùng pg_dump custom, ghi checksum và metadata trong `.run/backups`.
Restore tạo container PostgreSQL riêng không network/port/volume, kiểm tra checksum,
restore với `--exit-on-error`, kiểm tra schema/count rồi xóa đúng container vừa tạo.
Không có tham số cho phép restore đè database người dùng. File `.restore.json` ghi thời gian
và kết quả thực tế, không phải cam kết RTO. Backup local cùng ổ đĩa không bảo vệ khi mất máy:
cần chuyển sang kho khác có mã hóa, phân quyền và retention trước vận hành thật.
Chưa tự chọn nhà cung cấp lưu trữ, lịch backup, RPO/RTO hoặc backup secrets.

## Staging riêng

Build JAR rồi dùng `compose.staging.yml` và env riêng (không tái sử dụng mật khẩu dev):
`REFURBISHED_STAGING_ADMIN_PASSWORD`, `REFURBISHED_STAGING_PASSWORD`,
`REFURBISHED_STAGING_JWT_SECRET` (ít nhất 32 ký tự).

```powershell
docker compose --env-file .env.staging.local -f compose.staging.yml up -d --build
```

Profile staging chỉ nhận `jdbc:postgresql://db:5432/refurbished_staging` và role cùng tên,
không chấp nhận trộn profile hoặc bật sandbox payment. DB không publish port; API bind
host loopback 18080. Container non-root/read-only có giới hạn memory và bỏ capabilities.
Đây là cấu hình staging local, chưa phải production: cần HTTPS/reverse proxy, quản lý secrets,
bootstrap ADMIN có kiểm soát, pin image digest JRE đã duyệt và thử rollback ở môi trường đích.
Không tự mở public deployment hoặc tạo tài khoản quản trị khi chưa cấu hình môi trường.

Rollback JAR không hoàn tác migration: kiểm tra tương thích schema và có backup đã restore
thử trước khi nâng cấp. Không dùng Flyway clean hoặc tự xóa volume để sửa lỗi migration.
