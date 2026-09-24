# Báo cáo thực tế Phase 10

## Files created

- `security/`: AppUser, UserRole, repositories, JWT/Auth/OAuth services, controllers,
  SecurityConfiguration, Google configuration/handler và AdminBootstrap.
- `security/dto/`: login, create user, OAuth exchange, token và user responses.
- `common/exception/AuthenticationFailedException.java`.
- `db/migration/V5__create_users_and_oauth_codes.sql`.
- `src/test/java/com/example/refurbished/SecurityIT.java`.
- `docs/PHASE-10.md`, `docs/PHASE-10-REPORT.md`.

## Files modified

- `pom.xml`, `application.yml`, `application-test.yml`.
- `ApiExceptionHandler.java`.
- Local environment initialization/loading scripts.
- Migration-version assertions in existing integration tests.
- Root and backend Java README files.

## Dependencies added

| Dependency | Purpose |
|---|---|
| spring-boot-starter-security | Filter chain, BCrypt, authentication/authorization |
| spring-boot-starter-oauth2-resource-server | Bearer JWT validation |
| spring-boot-starter-oauth2-client | Google Authorization Code/OIDC login |

Versions are managed by Spring Boot 3.5.16. No standalone JWT library was added.

## Implemented

- ADMIN/STAFF users, BCrypt password login and 30-minute JWT.
- Endpoint authorization and JSON 401/403 contract.
- ADMIN-controlled user provisioning and optional local admin bootstrap.
- Conditional Google OIDC registration.
- Verified/existing-email policy and two-minute single-use OAuth exchange codes.
- Flyway V5 constraints and local JWT-secret generation/loading.

## Not implemented/tested

- Refresh tokens, logout/revocation, password reset, account disable API or audit log.
- Live Google callback against real credentials.
- Production key rotation, HTTPS deployment or external secrets manager.

## Tests/build

- Unit tests: 18 PASS.
- Integration tests: 55 PASS, including 4 SecurityIT cases.
- Total: 73, failures/errors/skipped 0.
- Maven `clean verify`: BUILD SUCCESS.
- Log: `.run/phase10-build.log`.

## Startup verification

- Dev database `127.0.0.1:55432/refurbished_dev`, role `refurbished_app`.
- Flyway V4 → V5 success.
- Health HTTP 200.
- `/api/auth/me` without JWT returned 401.
- Java smoke-test process stopped afterward.

