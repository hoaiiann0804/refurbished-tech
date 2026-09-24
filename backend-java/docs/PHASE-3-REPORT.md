# Phase 3 — Kết quả thực tế

Ngày xác minh: 2026-09-23 (Asia/Bangkok).

## Kết quả

- `clean verify`: **BUILD SUCCESS**, exit code 0.
- Unit tests: **10**, failures 0, errors 0, skipped 0.
- Integration tests: **2**, failures 0, errors 0, skipped 0.
- JAR chạy với profile `dev`; JPA EntityManagerFactory và embedded Tomcat khởi động.
- `GET http://127.0.0.1:8080/api/health`: **HTTP 200**, `{"status":"UP"}`.
- `pg_stat_activity` của PostgreSQL dev xác nhận session dùng database
  `refurbished_dev`, role `refurbished_app`.
- Java process dùng cho smoke test đã được dừng sau kiểm tra.
- Hai container database mới được để chạy để có thể kiểm tra thủ công.
- Không chạy hoặc sửa deployment/Compose của Node, không kết nối database/Redis cũ.

## Phiên bản đã xác minh

| Thành phần | Phiên bản |
|---|---|
| JDK / javac | Temurin 21.0.12.1 |
| Maven Wrapper | 3.3.4, official only-script distribution |
| Maven | 3.9.16 |
| Spring Boot | 3.5.16 |
| Hibernate | 6.6.53.Final |
| PostgreSQL JDBC | 42.7.11 |
| JUnit Jupiter | 5.12.2 |
| PostgreSQL server | 17.11 |

Maven distribution được kiểm tra bằng SHA-512 do Maven Central cung cấp, sau đó
SHA-256 được lưu trong `maven-wrapper.properties`. Wrapper archive được kiểm tra
với SHA-1 phát hành qua HTTPS; endpoint SHA-512 của wrapper archive trả 404.
PostgreSQL image được ghim digest sau khi pull và xác minh phiên bản.

## File được tạo

```text
backend-java/
  .gitignore
  .gitattributes
  .mvn/wrapper/maven-wrapper.properties
  mvnw
  mvnw.cmd
  pom.xml
  README.md
  compose.local.yml
  docker/init-app-role.sh
  scripts/Initialize-Local.ps1
  scripts/Use-LocalEnvironment.ps1
  docs/PHASE-3-REPORT.md
  src/main/java/com/example/refurbished/RefurbishedApplication.java
  src/main/java/com/example/refurbished/common/config/LocalDataSourceConfiguration.java
  src/main/java/com/example/refurbished/common/health/HealthController.java
  src/main/resources/application.yml
  src/main/resources/application-dev.yml
  src/test/java/com/example/refurbished/FoundationIT.java
  src/test/java/com/example/refurbished/common/config/LocalDataSourceConfigurationTest.java
  src/test/resources/application-test.yml
```

File local không commit: `.env.local` (credentials ngẫu nhiên mới), `.downloads/`,
`.maven/`, `.m2/`, `.run/`, `target/`. Tất cả được loại trừ bằng `.gitignore` của
backend-java. Không thay đổi file có sẵn ngoài backend-java, kể cả root .gitignore.

## Lệnh thực thi chính

Từ `backend-java`:

```powershell
java --version
javac --version
$env:MAVEN_USER_HOME = Join-Path $PWD '.maven'
.\mvnw.cmd --version

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Initialize-Local.ps1
docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose --env-file .env.local -f compose.local.yml config --quiet
docker --host npipe:////./pipe/dockerDesktopLinuxEngine compose --env-file .env.local -f compose.local.yml up -d --wait

& .\scripts\Use-LocalEnvironment.ps1
.\mvnw.cmd '-Dmaven.repo.local=D:/PROJECT/Refurbished-Tech/backend-java/.m2/repository' --batch-mode --no-transfer-progress clean verify

java -jar target/refurbished-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --debug=false
Invoke-WebRequest http://127.0.0.1:8080/api/health -UseBasicParsing
```

JAR smoke test được chạy bằng Start-Process với WindowStyle Hidden và redirect log
vào `.run/`; khối finally dừng đúng process được tạo. Ngoài ra có các lệnh chỉ đọc
để kiểm tra Docker resource names/ports, image digest, phiên bản PostgreSQL,
quyền role và session của ứng dụng. Không in password hay full Docker inspect.

Cache Maven được đặt trong workspace cho lần chạy của agent. Người dùng có thể
chạy `mvnw.cmd clean verify` bình thường với Maven cache mặc định; vẫn cùng
distribution và dependency versions.

## Dependency thêm và lý do

| Dependency | Lý do |
|---|---|
| spring-boot-starter-web | HTTP/JSON và embedded server |
| spring-boot-starter-data-jpa | Hibernate, repository infrastructure, JDBC pooling |
| postgresql (runtime) | Driver PostgreSQL |
| spring-boot-starter-validation | Bean Validation cho DTO ở phase tiếp theo |
| spring-boot-starter-test (test) | JUnit 5, Spring context/HTTP integration testing |

Mockito core và Mockito JUnit extension được exclude. Không thêm Lombok, Redis,
Security/JWT, OAuth, payment, email, storage hoặc H2/Testcontainers.
Maven Failsafe là build plugin để `verify` thực sự chạy integration tests.

## Bằng chứng có thể đọc lại

- `target/surefire-reports/com.example.refurbished.common.config.LocalDataSourceConfigurationTest.txt`
- `target/failsafe-reports/com.example.refurbished.FoundationIT.txt`
- `.run/startup.log`
- `.run/health-response.json`

Các file evidence này được sinh khi chạy, bị Git bỏ qua và có thể mất khi clean
hoặc xóa build output. Báo cáo này ghi lại kết quả, không thay thế việc chạy lại.

## Giới hạn và CV integrity

**IMPLEMENTED:** Java foundation, local dev/test isolation, connection guard,
PostgreSQL-backed health endpoint và hướng dẫn chạy.

**TESTED:** 10 cấu hình bị từ chối trước khi tạo kết nối; 2 integration tests trên
PostgreSQL thật; packaged JAR startup + HTTP health + đúng database/user.

**DEPLOYED:** Không. Chạy local không phải production deployment.

**PLANNED:** Product, DeviceUnit, inspection, Order, checkout, concurrency proof,
Warranty và optional integrations. Chưa có business entity/table. Chưa có sales
transaction hay kiểm thử chống bán trùng. Chưa có benchmark hoặc số liệu scale.

Hibernate `validate` hiện khởi động với zero entity; đây không phải bằng chứng
schema nghiệp vụ đã được tạo/validate. Trước Phase 4 cần chọn quản lý migration.
Chưa triển khai Phase 4; chờ người dùng duyệt.
