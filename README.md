# Refurbished Device Inventory & Sales Platform

Java/Spring Boot backend quản lý từng thiết bị refurbished vật lý theo serial number,
quy trình kiểm định, bán hàng an toàn khi có request đồng thời và bảo hành theo thiết
bị. Project được xây trong migration workspace từ bài học của backend Node.js cũ;
đây không phải bản dịch từng dòng và không dùng database hoặc secrets production.

## Trạng thái hiện tại

| Phạm vi | Trạng thái |
|---|---|
| Core Java backend, Phase 0–8 | IMPLEMENTED |
| Unit/integration tests trên PostgreSQL local | TESTED — 73 tests pass |
| Packaged JAR startup và health check | TESTED local |
| Concurrent checkout: hai request, đúng một sale | TESTED local |
| Optional feature assessment, Phase 9 | COMPLETED |
| Spring Security, JWT, ADMIN/STAFF, Google OIDC, Phase 10 | IMPLEMENTED, TESTED local |
| Frontend mới tương thích Java API | PLANNED |
| Production deployment | NOT DEPLOYED |

Backend Node và React cũ vẫn nằm trong `be/` và `fe/` để tham khảo. Chúng không phải
runtime của Java backend và frontend cũ chưa tương thích với Java API.

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

### Warranty

Warranty gắn trực tiếp với một DeviceUnit `SOLD`. Mỗi thiết bị có tối đa một Warranty,
thời hạn 1–36 tháng. PostgreSQL unique/FK/check constraints bảo vệ các invariant.

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
│  ├─ order/         # Order, OrderItem, checkout
│  ├─ warranty/      # Warranty theo DeviceUnit SOLD
│  ├─ security/      # User, role, JWT, Google OIDC
│  └─ common/        # config, health, errors, API helpers
├─ src/main/resources/
│  ├─ application.yml
│  ├─ application-dev.yml
│  └─ db/migration/  # Flyway V1–V5
├─ src/test/         # unit + PostgreSQL integration tests
├─ compose.local.yml # PostgreSQL dev/test cô lập
├─ mvnw / mvnw.cmd
└─ docs/
```

## Công nghệ

- Java 21 LTS, Eclipse Temurin.
- Spring Boot 3.5.16.
- Spring Web / Spring MVC.
- Spring Data JPA và Hibernate.
- PostgreSQL 17.11 và PostgreSQL JDBC.
- Flyway migrations.
- Bean Validation.
- Spring Security, OAuth2 Resource Server và OAuth2 Client.
- Maven Wrapper 3.3.4 / Maven 3.9.16.
- JUnit 5 và Spring Boot Test.
- Docker Compose cho PostgreSQL development/test.

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

### Warranty và health

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/warranties` | Cấp Warranty cho DeviceUnit SOLD |
| GET | `/api/warranties/{id}` | Tra cứu Warranty |
| GET | `/api/warranties/device-unit/{deviceUnitId}` | Tra cứu theo thiết bị |
| GET | `/api/health` | Kiểm tra HTTP và database |

### Authentication và users

| Method | Endpoint | Quyền |
|---|---|---|
| POST | `/api/auth/login` | Public; email/password → JWT |
| GET | `/api/auth/me` | Authenticated |
| POST | `/api/auth/oauth/exchange` | Public; one-time Google code → JWT |
| GET | `/oauth2/authorization/google` | Public khi Google credentials được cấu hình |
| POST | `/api/users` | ADMIN |

GET Product là public. ADMIN quản lý Product/User. ADMIN và STAFF thực hiện intake,
inspection, checkout và cấp Warranty. Order, Warranty và inventory detail yêu cầu JWT.

## Database isolation

| Profile | JDBC URL | Role |
|---|---|---|
| dev | `jdbc:postgresql://127.0.0.1:55432/refurbished_dev` | `refurbished_app` |
| test | `jdbc:postgresql://127.0.0.1:55433/refurbished_test` | `refurbished_test` |

`LocalDataSourceConfiguration` từ chối remote host, database/user sai, query parameter
không được phép và profile không hợp lệ trước khi tạo connection pool. Hibernate dùng
`ddl-auto=validate`; Flyway là thành phần duy nhất thay đổi schema.

Migrations:

| Version | Nội dung |
|---|---|
| V1 | Product và DeviceUnit |
| V2 | Inspection/battery evidence constraints |
| V3 | Sales Order và OrderItem |
| V4 | Warranty |
| V5 | App users và one-time OAuth login codes |

## Chạy local trên Windows

Yêu cầu: JDK 21, Docker Desktop Linux Engine và PowerShell.

```powershell
cd D:\PROJECT\Refurbished-Tech\backend-java

# Tạo local credentials mới; không đọc secrets Node/production.
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

.\mvnw.cmd --batch-mode --no-transfer-progress clean verify

# Đích: 127.0.0.1:55432/refurbished_dev, role refurbished_app.
java -jar .\target\refurbished-backend-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=dev
```

Sau khi ADMIN được tạo lần đầu, xóa hai bootstrap environment variables. Lần chạy
sau không cần đặt lại chúng.

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

Build cuối Phase 10 có:

| Nhóm | Số test | Kết quả |
|---|---:|---|
| Unit tests | 18 | PASS |
| Integration tests | 55 | PASS |
| **Tổng** | **73** | **0 failures, 0 errors, 0 skipped** |

Integration tests dùng PostgreSQL thật tại `refurbished_test`, kiểm tra đúng database
trước khi tạo/xóa fixture và không truncate database development.

Các nhóm test chính:

- Configuration/database safety guard.
- Product và DeviceUnit CRUD/validation/constraints.
- Inspection lifecycle và rollback.
- Order checkout, price snapshot và atomicity.
- Hai concurrent HTTP checkout tranh cùng một row lock.
- Warranty rules và persistence.
- JWT authentication, ADMIN/STAFF authorization và one-time OAuth exchange.

## Phase 9 — optional feature decisions

| Feature | Quyết định hiện tại |
|---|---|
| Authentication/JWT | IMPLEMENTED trong Phase 10 |
| Google OAuth | IMPLEMENTED; live Google login cần credentials thật để xác minh |
| Redis view dedup | REMOVE vì Java domain không có view counter |
| Reservation expiration | REDESIGN nếu có async payment |
| Stripe | REDESIGN/POSTPONE; cần pending-payment lifecycle trước |
| Email | POSTPONE, phụ thuộc auth/business events |
| Upload/Cloudinary | POSTPONE, cần ProductImage model |
| Gemini chatbot | REMOVE vì prompt generic fashion/cart không phù hợp |
| Cart | REDESIGN theo unique DeviceUnit và reservation policy |
| Frontend cũ | REDESIGN; API contracts không tương thích |
| Docker PostgreSQL local | KEEP, IMPLEMENTED |
| Java app container/Nginx/deployment | POSTPONE |

Chi tiết và bằng chứng source: [Phase 9 decision record](backend-java/docs/PHASE-9.md).

## Node và frontend cũ

```text
be/  # Node/Express/Sequelize reference
fe/  # React generic e-commerce reference
```

Không xóa hai thư mục này chỉ vì Java core đã hoàn thành. Frontend cũ còn phụ thuộc
Node auth/cart/payment/product contract. Có thể archive bản copy Node sau khi frontend
mới thay thế nó hoặc khi xác nhận không còn dùng frontend cũ. Original Node repository
và production website tuyệt đối không thuộc phạm vi xóa/sửa của workspace này.

## Mapping để học Java

| Node/.NET | Java/Spring |
|---|---|
| Express controller / ASP.NET Controller | `@RestController` |
| Service / ASP.NET DI service | `@Service` + constructor injection |
| Sequelize model / EF Core entity | JPA `@Entity` |
| Sequelize transaction / EF transaction | Spring `@Transactional` |
| Sequelize lock / SQL in EF | JPA `PESSIMISTIC_WRITE` |
| Joi/express-validator / DataAnnotations | Bean Validation |
| Sequelize/EF migrations | Flyway SQL migrations |
| package.json / `.csproj` | Maven `pom.xml` |
| JS number / C# decimal | Java `BigDecimal` cho tiền |
| JS Date / DateTimeOffset | Java `Instant` |
| DateOnly | Java `LocalDate` |

## Tài liệu theo phase

- [Phase 4 — Product và DeviceUnit](backend-java/docs/PHASE-4.md)
- [Phase 5 — Inspection lifecycle](backend-java/docs/PHASE-5.md)
- [Phase 6 — Order và checkout](backend-java/docs/PHASE-6.md)
- [Phase 7 — Concurrency proof](backend-java/docs/PHASE-7.md)
- [Phase 8 — Warranty](backend-java/docs/PHASE-8.md)
- [Phase 9 — Optional feature assessment](backend-java/docs/PHASE-9.md)
- [Phase 10 — Security, JWT và Google OAuth](backend-java/docs/PHASE-10.md)

## CV integrity

Có thể mô tả là đã **implemented và tested locally**: Spring Boot REST API,
per-device inventory, inspection state machine, transactional checkout, pessimistic
locking, PostgreSQL constraints/Flyway và Warranty.

Không mô tả là đã triển khai production, phục vụ traffic thực, tích hợp payment/auth,
hoặc đạt performance benchmark. Các hạng mục đó chưa được thực hiện.
