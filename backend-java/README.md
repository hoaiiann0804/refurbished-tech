# Refurbished Device Backend — Phase 10 security

Đã triển khai Product, DeviceUnit, inspection lifecycle, concurrent checkout,
Order/OrderItem và Warranty tối thiểu trong migration workspace. Core backend theo
roadmap Phase 0–8 đã hoàn thành. Phase 10 bổ sung Spring Security, ADMIN/STAFF,
BCrypt/JWT và Google OIDC có điều kiện. Không sử dụng cấu hình và credentials từ `be/`, `ops/`,
database backup hoặc production.

## Công cụ và dependency

- Java 21 LTS.
- Spring Boot 3.5.16: quản lý phiên bản dependency tương thích.
- Maven 3.9.16 qua Apache Maven Wrapper 3.3.4; không cần Maven toàn hệ thống.
- Spring Web: Spring MVC, JSON và embedded Tomcat.
- Spring Data JPA: repository infrastructure, Hibernate và HikariCP.
- PostgreSQL JDBC: driver kết nối PostgreSQL.
- Bean Validation: validation request DTO và tham số phân trang.
- Flyway Core + PostgreSQL module: migration SQL có version, dùng cùng DataSource đã kiểm tra.
- Spring Boot Test/JUnit 5: kiểm thử; loại Mockito khỏi dependency transitively.
- Maven Failsafe: chạy `*IT` trong lifecycle `verify`; không phải runtime dependency.
- PostgreSQL 17.11: image PostgreSQL 17 Alpine được ghim digest trong Compose.

Chưa thêm Redis, payment, email, storage, Lombok, H2 hoặc Testcontainers.
Hướng dẫn security: [PHASE-10.md](docs/PHASE-10.md). Quyết định Phase 9:
[PHASE-9.md](docs/PHASE-9.md). Hướng dẫn core:
[PHASE-8.md](docs/PHASE-8.md).

## Database isolation

| Profile | JDBC URL | Application role | Dữ liệu |
|---|---|---|---|
| dev | `jdbc:postgresql://127.0.0.1:55432/refurbished_dev` | `refurbished_app` | Volume riêng, giữ sau khi dừng |
| test | `jdbc:postgresql://127.0.0.1:55433/refurbished_test` | `refurbished_test` | tmpfs, mất khi container dừng |

Compose project: `refurbished-java-local`.
Volume development: `refurbished-java-local_refurbished_dev_data`.
PostgreSQL chỉ publish port trên `127.0.0.1`.

Mỗi database có tài khoản bootstrap riêng. Application role không có SUPERUSER,
CREATEDB hoặc CREATEROLE; role chỉ sở hữu schema `public` trong database mới.
Không dùng admin role để chạy Java.

`LocalDataSourceConfiguration` kiểm tra profile, JDBC URL và username trước khi
tạo connection pool. Chỉ chấp nhận đúng một profile `dev` hoặc `test` và đúng
URL/role ở bảng trên. URL có query parameter, database cũ hoặc remote host đều
bị từ chối. Không bind các tùy chọn Hikari có thể ghi đè địa chỉ kết nối.
Đây là guard cho workspace local, không phải cơ chế bảo mật production hay bằng
chứng rằng một dịch vụ tùy ý trên localhost là đáng tin cậy.

Spring không tự đọc `.env` của Node. Mật khẩu mới nằm trong `.env.local`, bị
`.gitignore` loại trừ. Không in hoặc commit file này. Script khởi tạo không thay
đổi credentials nếu file đã tồn tại. Không xóa file khi volume dev vẫn đang dùng
mật khẩu cũ: thay đổi environment không tự đổi password trong database hiện có.

Hibernate dùng `ddl-auto: validate`, SQL initializer bị tắt. Flyway chạy V1 cho
Product/DeviceUnit, V2 cho inspection evidence, V3 cho Order/OrderItem, V4 cho
Warranty và V5 cho User/OAuth codes trước JPA.
MigrationConfiguration dùng trực tiếp DataSource đã kiểm tra, không đọc URL/user
Flyway riêng; `clean` bị tắt và không tự baseline database có schema lạ.
Init script của PostgreSQL chỉ tạo application role và cấp quyền trên hai database mới.
Không sửa migration đã áp dụng; thay đổi schema tiếp theo phải thêm V2, V3...

## Chạy trên Windows / PowerShell

Mở Docker Desktop với Linux Engine. Mở terminal mới sau khi cài JDK 21.
Tất cả lệnh dưới đây chạy từ thư mục `backend-java`:

```powershell
cd D:\PROJECT\Refurbished-Tech\backend-java
java --version
javac --version
.\mvnw.cmd --version

# Tạo credentials ngẫu nhiên mới, không đọc secrets của project cũ.
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Initialize-Local.ps1

# Dùng endpoint Docker Desktop local rõ ràng, không dùng remote Docker context.
docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose --env-file .env.local -f compose.local.yml up -d --wait

# Nạp CHỈ hai application passwords vào terminal hiện tại.
& .\scripts\Use-LocalEnvironment.ps1

# Unit tests + package JAR + integration tests trên refurbished_test.
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify

# Database đích: 127.0.0.1:55432/refurbished_dev, user refurbished_app.
java -jar .\target\refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Nếu Windows chặn script `Use-LocalEnvironment.ps1`, chỉ cho phép trong terminal
hiện tại rồi chạy lại script (không sửa execution policy toàn máy):

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
& .\scripts\Use-LocalEnvironment.ps1
```

Ở terminal khác:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

Kết quả khi HTTP và PostgreSQL cùng hoạt động:

```json
{"status":"UP"}
```

Endpoint chạy `SELECT 1`; không trả thông tin credentials/database ra response.
Đây là kiểm tra kết nối, chưa phải hệ thống observability. Không cần Actuator cho
một endpoint này. Nếu PostgreSQL lỗi, request không trả `UP` thành công.

Nếu port 8080 đang được ứng dụng khác sử dụng, dùng
`--server.port=8082` và kiểm tra URL tương ứng. Không dừng ứng dụng khác.
Không có profile mặc định: quên `--spring.profiles.active=dev` sẽ fail thay vì
âm thầm chọn một database. `application-test.yml` chỉ nằm trong test resources.

Dừng Java bằng Ctrl+C trong terminal đã chạy JAR. Dừng CHỈ hai database mới:

```powershell
docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose --env-file .env.local -f compose.local.yml stop
```

Lần chạy sau dùng lại `up -d --wait`. Không chạy các npm Docker script cũ và
không cần chạy `down -v`.

## Kiểm thử

```powershell
# Không cần database; kiểm tra các cấu hình nguy hiểm bị từ chối trước kết nối.
.\mvnw.cmd test

# Cần db-test healthy và REFURBISHED_TEST_PASSWORD trong terminal.
& .\scripts\Use-LocalEnvironment.ps1
.\mvnw.cmd verify
```

- `LocalDataSourceConfigurationTest`: URL sai, nhầm dev/test, admin role,
  thiếu password, thiếu profile, profile production hoặc trộn profile.
- `FoundationIT`: Spring Boot context/JPA khởi động với PostgreSQL thật;
  xác minh database, current user, quyền role; gọi health qua HTTP thật.
- `ProductInventoryIT`: HTTP Product/DeviceUnit, normalization, duplicate/rollback,
  validation, pagination/filtering, inactive product và database constraints.
- `DeviceLifecycleIT`: transition kiểm định, evidence, rollback và constraints.
- `OrderCheckoutIT`: checkout, price snapshot, nhiều thiết bị và rollback toàn bộ.
- `CheckoutConcurrencyIT`: hai HTTP checkout cùng chờ một PostgreSQL row lock;
  chính xác một request bán thành công.
- `WarrantyIT`: chỉ cấp cho DeviceUnit SOLD, không cấp trùng, lookup và validation.
- Surefire reports: `target/surefire-reports`.
- Failsafe reports: `target/failsafe-reports`.
- `test` không thay thế `verify`: chỉ `verify` chạy integration tests.
- Đã có test nghiệp vụ bán hàng tuần tự và hai checkout đồng thời.

## Cấu trúc

```text
src/main/java/com/example/refurbished/
  RefurbishedApplication.java
  common/config/LocalDataSourceConfiguration.java
  common/config/MigrationConfiguration.java
  common/health/HealthController.java
  common/api/PageResponse.java
  common/exception/
  product/          # Entity, Repository, Service, Controller, dto/
  inventory/        # DeviceUnit, enums, Repository, Service, Controller, dto/
  order/            # Order, OrderItem, Repository, Service, Controller, dto/
  warranty/         # Warranty, Repository, Service, Controller, dto/
  security/         # User, roles, JWT, Google OIDC
src/main/resources/
  application.yml
  application-dev.yml
  db/migration/V1__create_products_and_device_units.sql
  db/migration/V2__require_inspection_evidence.sql
  db/migration/V3__create_orders_and_order_items.sql
  db/migration/V4__create_warranties.sql
  db/migration/V5__create_users_and_oauth_codes.sql
src/test/java/com/example/refurbished/
  FoundationIT.java
  ProductInventoryIT.java
  DeviceLifecycleIT.java
  OrderCheckoutIT.java
  CheckoutConcurrencyIT.java
  WarrantyIT.java
  common/config/LocalDataSourceConfigurationTest.java
src/test/resources/application-test.yml
```

Core package và security Phase 10 đã có; payment/email/upload/deployment vẫn hoãn.

## Mapping để học Java

| Khái niệm | Vai trò trong project | .NET / Node tương đương |
|---|---|---|
| `pom.xml` + Maven | Dependency/build lifecycle; Wrapper ghim Maven | `.csproj` + NuGet / package.json + build tooling |
| `@SpringBootApplication` | Entry point, auto-configuration và component scanning | Program.cs + ASP.NET host / Express bootstrap |
| `@RestController` | HTTP handler trả JSON | ASP.NET controller / Express router-controller |
| Constructor injection | Cấp JdbcTemplate vào controller | ASP.NET DI / truyền service vào constructor |
| `@Configuration` + `@Bean` | Đăng ký DataSource có guard vào IoC container | Đăng ký service trong IServiceCollection |
| `application-*.yml` | Cấu hình theo profile | appsettings.Development.json / environment config |
| DataSource/HikariCP | Pool JDBC connections; không tạo connection mỗi request | ADO.NET connection pooling / Sequelize pool |
| JPA / Hibernate | Đặc tả ORM và implementation cho Product/DeviceUnit | EF Core / Sequelize |
| Java record | DTO bất biến ngắn gọn cho health response | C# record; không phải Lombok |
| `@SpringBootTest` | Khởi động context/server cho integration test | WebApplicationFactory / test Express app |
| JUnit 5 | Test assertions và lifecycle | xUnit/NUnit / Jest |

`open-in-view: false` không giữ JPA session để controller tùy ý lazy-load sau
service transaction. API trả DTO được dựng trong service transaction.
`validate` kiểm tra entity/schema nhưng không tạo schema; Flyway quản lý schema.

## 5 câu hỏi phỏng vấn

1. Maven Wrapper khác Maven cài toàn hệ thống thế nào?
2. `@SpringBootApplication` làm những gì khi ứng dụng khởi động?
3. Vì sao dùng constructor injection thay vì tự `new JdbcTemplate(...)`?
4. `ddl-auto: validate` khác `update` và `create-drop` thế nào?
5. Unit test cấu hình khác integration test HTTP/PostgreSQL ở điểm nào?

## Tài liệu chính thức

- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://maven.apache.org/tools/wrapper/
- https://maven.apache.org/surefire/maven-failsafe-plugin/
- https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html
