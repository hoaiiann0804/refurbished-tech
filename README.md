# Refurbished Device Inventory & Sales Platform

> **Cập nhật 2026-09-28**: Đã hoàn thành các bước 1A, 2, 3 và phát hành nhánh
> `feature/java-backend-enhancements` → merged vào `main`. 85 tests PASS (47 unit + 38 IT).
> Migrations V1–V11 áp dụng đầy đủ. CI chuyển sang Java-only pipeline.
> Xem [kế hoạch](PLAN.md) và [sơ đồ workflow/dữ liệu](docs/planning/README.md).

Java/Spring Boot backend quản lý từng thiết bị refurbished vật lý theo serial number,
quy trình kiểm định, bán hàng an toàn khi có request đồng thời và bảo hành theo thiết
bị. Project không dùng database hoặc secrets production.

## Trạng thái hiện tại

| Phạm vi | Trạng thái |
|---|---|
| Core Java backend, Phase 0–11 | IMPLEMENTED |
| Unit/integration tests trên PostgreSQL local | TESTED — 85 tests pass (47 unit, 38 IT), gồm Newman + Browser acceptance |
| API vận hành bước 2 + Audit + Operations | IMPLEMENTED, TESTED local |
| Packaged JAR startup và health check | TESTED local |
| Concurrent checkout: hai request, đúng một sale | TESTED local |
| Customer management (tạo/cập nhật khách hàng) | IMPLEMENTED |
| Online reservations (đặt hàng trực tuyến) | IMPLEMENTED |
| WarrantyClaim (xử lý khiếu nại bảo hành) | IMPLEMENTED |
| Audit log vận hành | IMPLEMENTED |
| Admin UI (HTML/JS tĩnh nhúng trong JAR) | IMPLEMENTED, TESTED local (Playwright) |
| Postman collection acceptance tests | IMPLEMENTED (Newman) |
| CI pipeline Java-only (GitHub Actions) | ACTIVE — `java-ci.yml` |
| CI pipeline Node.js cũ | DISABLED — `be-ci.yml.disabled` |
| Frontend mới tương thích Java API | PLANNED |
| Production deployment | NOT DEPLOYED |

Frontend mới tương thích Java API đang được lên kế hoạch. Xem [kế hoạch](PLAN.md) để biết thêm chi tiết.

## Nghiệp vụ cốt lõi

### Product và DeviceUnit

`Product` biểu diễn model/cấu hình chung, ví dụ MacBook Air M1 2020. `DeviceUnit`
biểu diễn đúng một máy vật lý:

```text
Product: MacBook Air M1 2020
├─ DeviceUnit MBA001 — grade A — battery 91% — AVAILABLE
└─ DeviceUnit MBA002 — grade B — battery 84% — SOLD
```

Không dùng `Product.quantity`. Serial number của DeviceUnit là unique toàn hệ thống.

### Inspection lifecycle

```text
RECEIVED → INSPECTING → AVAILABLE
                       → REJECTED
```

Thiết bị chỉ được bán sau khi inspection đạt yêu cầu. Backend từ chối transition sai,
ví dụ `RECEIVED → SOLD`. Inspection đạt yêu cầu phải có grade, giá, ghi chú và bằng
chứng pin hoặc lý do không có chỉ số pin.

### Checkout và concurrency

Checkout chạy trong một Spring `@Transactional` transaction:

```text
AVAILABLE → RESERVED → SOLD
```

`OrderItem` tham chiếu DeviceUnit thật và lưu snapshot giá. Repository dùng
`PESSIMISTIC_WRITE` trên row DeviceUnit. Integration test buộc hai HTTP checkout cùng
chờ một PostgreSQL row lock và chứng minh:

- Đúng một request trả 201.
- Đúng một request trả 409.
- Chỉ một Order và OrderItem hợp lệ tồn tại.
- DeviceUnit kết thúc ở `SOLD`.

### Online Reservations

Khách hàng có thể đặt thiết bị trực tuyến trước khi thanh toán. Reservation có thời
hạn và sẽ hết hạn tự động nếu không được xác nhận. Module `online/` quản lý toàn bộ
vòng đời này.

### Customer

Module `customer/` quản lý thông tin khách hàng bao gồm tạo mới, cập nhật. Order có
thể gắn với Customer.

### Warranty và WarrantyClaim

`Warranty` gắn trực tiếp với một DeviceUnit `SOLD`. Mỗi thiết bị có tối đa một
Warranty, thời hạn 1–36 tháng. `WarrantyClaim` quản lý khiếu nại theo Warranty —
mỗi claim có trạng thái riêng và có thể được cập nhật bởi STAFF/ADMIN.

### Audit

Module `audit/` ghi lại toàn bộ hành động vận hành quan trọng (inspection, checkout,
cấp warranty, cập nhật user…). ADMIN có thể tra cứu audit log theo actor, target hoặc
khoảng thời gian.

## Kiến trúc

```text
HTTP request
    ↓
@RestController + DTO + Bean Validation
    ↓
@Service + @Transactional
    ↓
Spring Data JpaRepository
    ↓
Hibernate
    ↓
PostgreSQL + Flyway constraints
```

Project dùng package-by-feature:

```text
backend-java/
├─ src/main/java/com/example/refurbished/
│  ├─ product/       # Product API và persistence
│  ├─ inventory/     # DeviceUnit, inspection, lifecycle
│  ├─ order/         # Order, OrderItem, checkout, idempotency
│  ├─ warranty/      # Warranty và WarrantyClaim
│  ├─ customer/      # Customer management
│  ├─ online/        # Online reservations
│  ├─ audit/         # Audit log vận hành
│  ├─ security/      # User, role, JWT, Google OIDC, rate limiter
│  └─ common/        # config, health, errors, API helpers
├─ src/main/resources/
│  ├─ application.yml
│  ├─ application-dev.yml
│  ├─ application-staging.yml
│  ├─ static/admin/  # Admin UI (HTML/JS/CSS tĩnh, nhúng trong JAR)
│  └─ db/migration/  # Flyway V1–V11
├─ src/test/         # unit + PostgreSQL integration tests
├─ postman/          # Postman collection acceptance tests
├─ scripts/          # PowerShell scripts vận hành local
├─ compose.local.yml # PostgreSQL dev/test cô lập
├─ Dockerfile
├─ mvnw / mvnw.cmd
└─ docs/
```

## Công nghệ

- Java 21 LTS, Eclipse Temurin.
- Spring Boot 3.5.16.
- Spring Web / Spring MVC.
- Spring Data JPA và Hibernate.
- PostgreSQL 17.11 và PostgreSQL JDBC.
- Flyway migrations (V1–V11).
- Bean Validation.
- Spring Security, OAuth2 Resource Server và OAuth2 Client.
- SpringDoc OpenAPI (Swagger UI).
- Maven Wrapper 3.3.4 / Maven 3.9.16.
- JUnit 5 và Spring Boot Test.
- Newman (Postman CLI) cho acceptance tests.
- Playwright (Chromium) cho browser tests của Admin UI.
- Docker Compose cho PostgreSQL development/test.
- GitHub Actions CI (`java-ci.yml`).

Không có Lombok, H2, Redis, Stripe, Kafka hoặc microservices trong Java runtime.

## API hiện có

### Product

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/products` | Tạo product model |
| GET | `/api/products/{id}` | Chi tiết product |
| GET | `/api/products` | Danh sách/phân trang/lọc active |
| PUT | `/api/products/{id}` | Cập nhật thông tin product |

### DeviceUnit và inspection

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/device-units` | Nhận một thiết bị vật lý |
| GET | `/api/device-units/{id}` | Chi tiết thiết bị |
| GET | `/api/device-units` | Lọc theo product/status và phân trang |
| POST | `/api/device-units/{id}/start-inspection` | `RECEIVED → INSPECTING` |
| POST | `/api/device-units/{id}/complete-inspection` | `INSPECTING → AVAILABLE/REJECTED` |

### Order và checkout

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/orders/checkout` | Transactional checkout DeviceUnit AVAILABLE |
| GET | `/api/orders/{id}` | Order và price-snapshot items |
| GET | `/api/orders` | Danh sách orders (ADMIN/STAFF) |

### Online reservations

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/online/reservations` | Đặt thiết bị trực tuyến |
| GET | `/api/online/reservations/{id}` | Chi tiết reservation |
| DELETE | `/api/online/reservations/{id}` | Hủy reservation |

### Customer

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/customers` | Tạo khách hàng |
| GET | `/api/customers/{id}` | Chi tiết khách hàng |
| PUT | `/api/customers/{id}` | Cập nhật khách hàng |
| GET | `/api/customers` | Danh sách khách hàng (ADMIN/STAFF) |

### Warranty và WarrantyClaim

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/warranties` | Cấp Warranty cho DeviceUnit SOLD |
| GET | `/api/warranties/{id}` | Tra cứu Warranty |
| GET | `/api/warranties/device-unit/{deviceUnitId}` | Tra cứu theo thiết bị |
| POST | `/api/warranty-claims` | Tạo khiếu nại bảo hành |
| GET | `/api/warranty-claims/{id}` | Chi tiết claim |
| PUT | `/api/warranty-claims/{id}/status` | Cập nhật trạng thái claim (STAFF/ADMIN) |

### Audit

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/audit` | Tra cứu audit log (ADMIN) |

### Authentication và users

| Method | Endpoint | Quyền |
|---|---|---|
| POST | `/api/auth/login` | Public; email/password → JWT |
| GET | `/api/auth/me` | Authenticated |
| POST | `/api/auth/oauth/exchange` | Public; one-time Google code → JWT |
| POST | `/api/auth/logout-all` | Authenticated; vô hiệu hóa tất cả JWT cũ |
| POST | `/api/auth/change-password` | Authenticated |
| GET | `/oauth2/authorization/google` | Public khi Google credentials được cấu hình |
| POST | `/api/users` | ADMIN |
| PUT | `/api/users/{id}/role` | ADMIN |
| PUT | `/api/users/{id}/status` | ADMIN |
| GET | `/api/health` | Public |

GET Product là public. ADMIN quản lý Product/User. ADMIN và STAFF thực hiện intake,
inspection, checkout và cấp Warranty. Order, Warranty và inventory detail yêu cầu JWT.

## Database isolation

| Profile | JDBC URL | Role |
|---|---|---|
| dev | `jdbc:postgresql://127.0.0.1:55432/refurbished_dev` | `refurbished_app` |
| test | `jdbc:postgresql://127.0.0.1:55433/refurbished_test` | `refurbished_test` |
| staging | `jdbc:postgresql://...` | (cấu hình qua env) |

`LocalDataSourceConfiguration` từ chối remote host, database/user sai, query parameter
không được phép và profile không hợp lệ trước khi tạo connection pool. Hibernate dùng
`ddl-auto=validate`; Flyway là thành phần duy nhất thay đổi schema.

### Flyway migrations

| Version | Nội dung |
|---|---|
| V1 | Product và DeviceUnit |
| V2 | Inspection/battery evidence constraints |
| V3 | Sales Order và OrderItem |
| V4 | Warranty |
| V5 | App users và one-time OAuth login codes |
| V6 | Token version (logout-all invalidation) |
| V7 | Operations audit log và checkout idempotency requests |
| V8 | Online reservations |
| V9 | Inspections và repair state |
| V10 | Customers và order statuses |
| V11 | WarrantyClaim |

## Chạy local trên Windows

Yêu cầu: JDK 21, Docker Desktop Linux Engine và PowerShell.

```powershell
cd D:\PROJECT\Refurbished-Tech\backend-java

# Tạo local credentials mới; không đọc secrets production.
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Initialize-Local.ps1

# Chỉ khởi động hai PostgreSQL database của Java workspace.
docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose `
  --env-file .env.local -f compose.local.yml up -d --wait

Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
& .\scripts\Use-LocalEnvironment.ps1

# Chỉ cần khi tạo ADMIN lần đầu; nhập password mà không ghi vào command history.
$env:REFURBISHED_BOOTSTRAP_ADMIN_EMAIL = 'admin@local.test'
$env:REFURBISHED_BOOTSTRAP_ADMIN_PASSWORD = `
  [System.Net.NetworkCredential]::new('', (Read-Host 'Admin password' -AsSecureString)).Password

# Chạy toàn bộ kiểm thử (unit + integration tests).
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify

# Khởi động server dev.
java -jar .\target\refurbished-backend-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=dev
```

Terminal khác:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

Kết quả:

```json
{"status":"UP"}
```

Không chạy migration, seed, test hoặc schema generation trên production database.

## Kiểm thử

Build hiện tại có:

| Nhóm | Số test | Kết quả |
|---|---:|---|
| Unit tests | 47 | PASS |
| Integration tests (PostgreSQL thật) | 38 | PASS |
| **Tổng** | **85** | **0 failures, 0 errors, 0 skipped** |

Bao gồm:

- Configuration/database safety guard.
- Product và DeviceUnit CRUD/validation/constraints.
- Inspection lifecycle và rollback.
- Order checkout, price snapshot và atomicity.
- Hai concurrent HTTP checkout tranh cùng một row lock.
- Online reservations.
- Operations/Audit API.
- Warranty và WarrantyClaim rules.
- JWT authentication, ADMIN/STAFF authorization, logout-all, rate limiting.
- Newman Postman acceptance (17 requests, 23 assertions).
- Playwright Admin UI browser test (Chromium).

## CI/CD

- **Java pipeline**: `.github/workflows/java-ci.yml` — kích hoạt khi có thay đổi trong
  `backend-java/**`. Chạy toàn bộ unit + integration tests trên GitHub Actions.
- **Node pipeline cũ**: `.github/workflows/be-ci.yml.disabled` — đã vô hiệu hoá.

## Luồng làm việc (Git workflow)

```text
main                      ← nhánh ổn định
  └─ feature/<tính năng>  ← phát triển trên đây, tạo PR về main
```

## Phase 9 — optional feature decisions

| Feature | Quyết định hiện tại |
|---|---|
| Authentication/JWT | IMPLEMENTED (Phase 10) |
| Google OAuth | IMPLEMENTED; live Google login cần credentials thật để xác minh |
| Login rate limiting | IMPLEMENTED |
| Token invalidation (logout-all) | IMPLEMENTED qua token version |
| WarrantyClaim | IMPLEMENTED (V11) |
| Customer | IMPLEMENTED (V10) |
| Online reservations | IMPLEMENTED (V8) |
| Audit log | IMPLEMENTED (V7) |
| Admin UI (embedded) | IMPLEMENTED |
| Redis view dedup | REMOVE vì Java domain không có view counter |
| Stripe | REDESIGN/POSTPONE; cần pending-payment lifecycle trước |
| Email | POSTPONE, phụ thuộc auth/business events |
| Upload/Cloudinary | POSTPONE, cần ProductImage model |
| Gemini chatbot | REMOVE vì prompt generic fashion/cart không phù hợp |
| Cart | REDESIGN theo unique DeviceUnit và reservation policy |
| Frontend mới | PLANNED |
| Docker PostgreSQL local | KEEP, IMPLEMENTED |
| Java app container/Nginx/deployment | POSTPONE |

Chi tiết: [Phase 9 decision record](backend-java/docs/PHASE-9.md).

## Spring Boot concepts

| Khái niệm | Trạng thái |
|---|---|
| Java app container/Dockerfile | IMPLEMENTED (Dockerfile có sẵn) |
| Staging config | IMPLEMENTED (application-staging.yml) |

## Mapping Spring Boot concepts

| Khái niệm | Java/Spring |
|---|---|
| REST Controller | `@RestController` |
| DI service | `@Service` + constructor injection |
| ORM entity | JPA `@Entity` |
| Transaction | Spring `@Transactional` |
| Pessimistic lock | JPA `PESSIMISTIC_WRITE` |
| Validation | Bean Validation (`@NotNull`, `@Size`…) |
| DB migrations | Flyway SQL migrations |
| Build tool | Maven `pom.xml` |
| Money | Java `BigDecimal` |
| UTC timestamp | Java `Instant` |
| Date only | Java `LocalDate` |
| Auth | Spring Security + JWT |

## Tài liệu theo phase

- [Phase 4 — Product và DeviceUnit](backend-java/docs/PHASE-4.md)
- [Phase 5 — Inspection lifecycle](backend-java/docs/PHASE-5.md)
- [Phase 6 — Order và checkout](backend-java/docs/PHASE-6.md)
- [Phase 7 — Concurrency proof](backend-java/docs/PHASE-7.md)
- [Phase 8 — Warranty](backend-java/docs/PHASE-8.md)
- [Phase 9 — Optional feature assessment](backend-java/docs/PHASE-9.md)
- [Phase 10 — Security, JWT và Google OAuth](backend-java/docs/PHASE-10.md)

## Tài liệu

- [Kế hoạch tổng thể](PLAN.md)
- [Sơ đồ workflow/dữ liệu](docs/planning/README.md)
- [STEP-1A — Accounts, logout-all, local recovery](backend-java/docs/STEP-1A-ACCOUNTS.md)
- [STEP-2 — Operations API](backend-java/docs/STEP-2-OPERATIONS.md)
- [Postman collection](backend-java/postman/README.md)
- [Swagger UI](backend-java/docs/SWAGGER.md)
- [Hướng dẫn vận hành](backend-java/docs/OPERATIONS.md)

## CV integrity

Có thể mô tả là đã **implemented và tested locally**: Spring Boot REST API,
per-device inventory, inspection state machine, transactional checkout, pessimistic
locking, PostgreSQL constraints/Flyway, Warranty, WarrantyClaim, Customer management,
Online reservations, Audit log, Admin UI, JWT auth, rate limiting, token invalidation,
Newman + Playwright acceptance tests và GitHub Actions CI.

Không mô tả là đã triển khai production, phục vụ traffic thực, tích hợp payment/auth
production, hoặc đạt performance benchmark. Các hạng mục đó chưa được thực hiện.