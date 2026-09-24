# Phase 10 — Spring Security, JWT và Google OAuth

## Mô hình bảo mật

Platform được xem là hệ thống vận hành nội bộ. Không có public registration. ADMIN
provision tài khoản STAFF/ADMIN qua `POST /api/users`. Điều này tránh việc người lạ tự
tạo STAFF rồi thao tác inventory hoặc checkout.

`AppUser` gồm email normalized unique, BCrypt password hash, display name, enabled và
role `ADMIN`/`STAFF`. JWT HS256 có issuer, subject là user UUID, email, role, `iat` và
`exp`; thời hạn mặc định 30 phút.

| API | Access |
|---|---|
| Health, login, OAuth exchange | Public |
| GET Product | Public |
| Create/update Product, create User | ADMIN |
| Intake/inspection/checkout/issue Warranty | ADMIN hoặc STAFF |
| Các API còn lại | Authenticated |

## Password login

```http
POST /api/auth/login
{"email":"admin@local.test","password":"..."}
```

Response chứa `accessToken`, `tokenType=Bearer`, expiry và user. Client gửi:

```http
Authorization: Bearer <JWT>
```

Password dùng BCrypt cost 12. Login sai trả message đồng nhất để không tiết lộ email
có tồn tại. JWT secret tối thiểu 32 ký tự và chỉ đến từ environment.

## Google OAuth/OIDC

Google login dùng Authorization Code Grant/OIDC của Spring Security. Chỉ email Google
đã verified và đã tồn tại trong `app_users` mới đăng nhập được; Google không tự tạo
tài khoản hoặc tự cấp role.

Flow:

```text
Frontend → /oauth2/authorization/google → Google
Google → /login/oauth2/code/google
Backend validates OIDC → creates random one-time code (2 minutes, SHA-256 stored)
Backend → frontend callback?code=...
Frontend POST /api/auth/oauth/exchange → JWT
```

JWT không nằm trực tiếp trong redirect URL. One-time code được row-lock khi exchange,
đánh dấu used và không dùng lại được.

Google Console redirect URI local:

```text
http://127.0.0.1:8080/login/oauth2/code/google
```

Environment variables:

```powershell
$env:REFURBISHED_GOOGLE_CLIENT_ID = '...apps.googleusercontent.com'
$env:REFURBISHED_GOOGLE_CLIENT_SECRET = `
  [System.Net.NetworkCredential]::new('', (Read-Host 'Google client secret' -AsSecureString)).Password
$env:REFURBISHED_OAUTH_FRONTEND_CALLBACK = 'http://127.0.0.1:5173/oauth/callback'
```

Nếu client ID hoặc secret thiếu, Google registration không được tạo nhưng password/JWT
login và ứng dụng vẫn hoạt động.

## Bootstrap ADMIN

Lần đầu, đặt email và password 12–72 ký tự trong process environment rồi start JAR.
ADMIN chỉ được tạo nếu email đó chưa tồn tại; password hiện có không bị startup sửa.

```powershell
$env:REFURBISHED_BOOTSTRAP_ADMIN_EMAIL = 'admin@local.test'
$env:REFURBISHED_BOOTSTRAP_ADMIN_PASSWORD = `
  [System.Net.NetworkCredential]::new('', (Read-Host 'Admin password' -AsSecureString)).Password
```

Sau startup đầu tiên:

```powershell
Remove-Item Env:REFURBISHED_BOOTSTRAP_ADMIN_EMAIL
Remove-Item Env:REFURBISHED_BOOTSTRAP_ADMIN_PASSWORD
```

## Java/.NET/Node mapping

| Spring | .NET / Node |
|---|---|
| SecurityFilterChain | ASP.NET authentication/authorization middleware / Express middleware chain |
| PasswordEncoder BCrypt | ASP.NET PasswordHasher / bcrypt |
| OAuth2 Resource Server | `AddJwtBearer` / JWT verify middleware |
| `hasRole` | `[Authorize(Roles=...)]` / authorization middleware |
| OAuth2 Client/OIDC | ASP.NET Google handler / Passport Google strategy |
| JwtEncoder/JwtDecoder | JWT signing/validation services |

## Google testing limitation

Automated tests verify conditional Google registration, redirect to Google, provisioned
user policy and single-use exchange code. Live callback/token exchange with Google was
not run because no real Google client credentials were supplied. Do not describe live
Google authentication as externally tested until completing that manual flow.

## 5 interview questions

1. Authentication và authorization khác nhau thế nào?
2. Vì sao password phải hash bằng BCrypt thay vì mã hóa hai chiều?
3. JWT signature, expiration và issuer được kiểm tra như thế nào?
4. Vì sao không đặt JWT access token trong OAuth redirect query string?
5. Vì sao Google email không được tự động cấp role STAFF/ADMIN?

