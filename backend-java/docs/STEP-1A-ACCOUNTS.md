# Bước 1A — Mật khẩu và thu hồi phiên

## Quy tắc và lựa chọn thiết kế

Người dùng đổi mật khẩu phải biết mật khẩu hiện tại. API lấy tài khoản từ JWT,
không nhận ID user tùy ý trong body. Mật khẩu mới khác mật khẩu hiện tại, tối thiểu
12 ký tự, tối đa 72 byte UTF-8 (giới hạn BCrypt), không tự trim.

`AuthService` khóa row user trước khi kiểm tra version/mật khẩu. Nếu hai yêu cầu
cùng đổi mật khẩu, yêu cầu đến sau khóa sẽ thấy phiên bản mới và bị từ chối; không
ghi đè mật khẩu vừa đổi. Hash và `tokenVersion` được cập nhật trong một transaction.

Mỗi request Bearer kiểm tra chữ ký/issuer/hạn dùng và trạng thái tài khoản trong DB.
Phiên bản JWT phải khớp `app_users.token_version`; role phải khớp và user phải enabled.
Đây là sự đánh đổi có chủ đích: thêm đọc DB để thu hồi phiên có hiệu lực ở request sau.
Không tự hủy request đã qua kiểm tra và đang chạy. API đổi mật khẩu/logout-all kiểm
tra lại version dưới row lock. Chưa có refresh token hoặc logout riêng một thiết bị.

OAuth code lưu phiên bản user lúc cấp. Code trước đổi mật khẩu/logout-all không được
dùng để phát JWT mới. Code đã dùng hoặc hết hạn tiếp tục bị từ chối.

## Migration và nâng cấp

V6 thêm token_version trên app_users và oauth_login_codes; không sửa migrations cũ.
JWT phát trước nâng cấp thiếu claim `ver` bị từ chối: đăng nhập lại. Code OAuth cũ
được gán version -1 nên cũng cần thực hiện lại đăng nhập Google.

Không chạy đồng thời JAR cũ và JAR mới trên cùng database dev: JAR cũ không có kiểm
tra thu hồi phiên. Dừng bản cũ rồi khởi động bản mới. Flyway sẽ migrate database dev
khi người vận hành khởi động bản mới; kiểm thử chỉ migrate database test.

## Chạy local

Từ `backend-java`, sau khi Docker db-dev hoạt động và build hoàn tất:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
& .\scripts\Start-Local.ps1
# Nếu 8080 đang bận và muốn chạy cổng khác:
& .\scripts\Start-Local.ps1 -Port 8082
```

Script nạp env trước khi chạy Java, kiểm tra JAR/cổng và không kế thừa các biến
bootstrap/recovery còn sót cho lần chạy thường. Script không tự khởi động Docker.
Không dùng script này để tạo ADMIN đầu tiên: bootstrap vẫn dùng hướng dẫn Phase 10.

## Khôi phục ADMIN quên mật khẩu

Dừng backend cũ bằng Ctrl+C trước khi chạy:

```powershell
& .\scripts\Start-Local.ps1 -RecoverAdmin
```

Nhập email ADMIN đã có, mật khẩu mới và xác nhận bằng prompt ẩn. Recovery chỉ hoạt
động trên profile dev với database được guard. Không có HTTP endpoint recovery.
Không đổi ID/role, không tạo tài khoản, không mở khóa user, không nâng STAFF thành ADMIN.
Thành công thì backend tiếp tục chạy, đăng nhập bằng mật khẩu mới; token/code cũ hết hiệu lực.

Biến recovery được truyền cho tiến trình Java và khôi phục giá trị ban đầu ở terminal
khi script kết thúc. Nếu trước đó bạn tự đặt biến recovery/bootstrap, chúng vẫn được
khôi phục cho caller; cần xóa chúng trước khi tự chạy `java -jar` ngoài script.
Không ghi mật khẩu vào command line hoặc file cấu hình được commit.

## Test Postman

1. `POST /api/auth/login` lấy accessToken như trước.
2. Đặt Authorization → Bearer Token.
3. Gửi `POST /api/auth/change-password` với Body JSON:

```json
{
  "currentPassword": "mat-khau-hien-tai-cua-ban",
  "newPassword": "mat-khau-moi-cua-ban"
}
```

Response 204 không có body. Gọi `/api/auth/me` bằng token cũ sẽ trả 401; đăng nhập
lại bằng mật khẩu mới để lấy token mới. Không dùng chuỗi ví dụ làm mật khẩu thật.

`POST /api/auth/logout-all` với Bearer token, không cần body, trả 204. Nó thu hồi
tất cả token hiện tại của user, không đổi mật khẩu; user vẫn có thể đăng nhập lại.

| Trường hợp | HTTP |
|---|---|
| Thiếu JWT, JWT đã thu hồi hoặc sai mật khẩu hiện tại | 401 |
| Thiếu trường, mật khẩu dưới 12 ký tự | 400 |
| Mật khẩu mới trùng cũ hoặc vượt 72 byte UTF-8 | 409 |
| Thành công | 204 |

## Kiểm chứng

Kết quả ngày 2026-09-25: **21 unit tests + 64 integration tests = 85 PASS**,
0 failures/errors/skipped; Maven verify BUILD SUCCESS. Log local:
`.run/step1a-verify.log`. `Test-StartLocal.ps1` PASS.

`SecurityIT` kiểm tra đổi mật khẩu, rollback khi sai, logout-all, token/code cũ,
tài khoản disabled, thay role, recovery và hai HTTP đổi mật khẩu cạnh tranh row lock.
Recovery tests tạo fixture trong refurbished_test; không chạy recovery với ADMIN thật.
Test context xác nhận runner recovery không được đăng ký trong profile test.

```powershell
& .\scripts\Use-LocalEnvironment.ps1
.\mvnw.cmd --batch-mode --no-transfer-progress verify
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-StartLocal.ps1
```

Test script dùng Java giả lập để kiểm tra lỗi và biến môi trường; không thay thế
thử nhập prompt thủ công trên một máy Windows cụ thể.

## Phần tiếp theo

Bước 1B: quản lý user, bảo vệ ADMIN cuối cùng, giới hạn đăng nhập và tách API Bearer
khỏi session Google. Live Google callback vẫn cần credentials và kiểm thử riêng.
