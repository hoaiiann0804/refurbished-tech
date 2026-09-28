# Test luồng vận hành bằng Postman

## Chạy trên backend local

1. Khởi động Docker và backend theo [hướng dẫn backend](../README.md).
2. Import `operations.postman_collection.json` và `local.postman_environment.json`.
3. Chọn environment **Refurbished Java - Local**. Điền `email`, `password` của ADMIN
   vào giá trị local trong Postman. `baseUrl` mặc định là `http://127.0.0.1:8080`.
4. Chạy collection theo thứ tự 01–17 bằng Collection Runner. Request Health tạo mã
   riêng mỗi lượt; Login lưu token; các request sau tự lưu product/device/order/warranty ID.
5. Xem kết quả assertion của từng request. Có một request cố ý nhận **409** để xác nhận
   cùng Idempotency-Key không được dùng cho nội dung đơn khác.

Collection này **tạo dữ liệu thật trên backend được chọn**: một model, một thiết bị,
một đơn và một bảo hành mỗi lượt. Dùng môi trường local; không có bước xóa dữ liệu tự động
trong collection nhập vào Postman. Khi chạy lại từ đầu, luôn bắt đầu bằng request 01.
Nếu bấm Send từng request, cũng giữ thứ tự này. Không export/chia sẻ environment chứa token/password.

Luồng nghiệp vụ: ADMIN khai báo model → nhập máy RECEIVED → bắt đầu kiểm định → kiểm định
đạt để thành AVAILABLE → chốt bán để thành SOLD → cấp bảo hành cho đúng máy đã bán.
STAFF có thể vận hành máy/đơn nhưng không tạo model hoặc đọc audit nên demo tổng thể dùng ADMIN.

## Vì sao checkout có Idempotency-Key?

Nếu server đã bán thành công nhưng response bị mất, gửi lại cùng key sẽ nhận đơn cũ,
không bị hiểu là một lần bán mới. Request 09 và 10 cùng key và nội dung, phải trả cùng order ID.
Request 11 đổi tên khách với key đó, phải bị từ chối. Key được giữ trong suốt lượt demo,
chỉ đổi khi người dùng thực sự muốn tạo giao dịch khác.

## Thao tác tài khoản riêng

`accounts-manual.postman_collection.json` chứa tạo STAFF, đổi mật khẩu và logout-all.
Chọn riêng request cần dùng và điền các biến tương ứng; đặt `allowAccountChanges=YES`
khi chủ động thực hiện, sau đó xóa giá trị này. Không chạy toàn bộ collection tài khoản như demo.
Sau đổi mật khẩu/logout-all, token cũ hết hiệu lực: đăng nhập lại để tiếp tục.
Environment mẫu không chứa thông tin đăng nhập; collection vận hành không đổi mật khẩu ADMIN.

## Nghiệm thu tự động với database test

`PostmanCollectionIT` mở Spring Boot trên cổng ngẫu nhiên với authorization thật, kiểm tra
database là `refurbished_test`, tạo ADMIN tạm và chạy Newman. Sau đó chỉ dọn fixture thuộc
lượt chạy đó. Không cần dùng tài khoản ADMIN dev của bạn.

Từ `backend-java`, khi Docker database test đã chạy:

```powershell
npm.cmd install --prefix .run/postman-runner --no-save --package-lock=false --ignore-scripts newman@6.2.1
& .\scripts\Use-LocalEnvironment.ps1
$env:REFURBISHED_RUN_POSTMAN = 'true'
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dit.test=PostmanCollectionIT' test-compile failsafe:integration-test failsafe:verify
Remove-Item Env:REFURBISHED_RUN_POSTMAN
```

Nếu đang dùng Maven cache riêng của dự án, thêm `-Dmaven.repo.local=.m2/repository`
và cấu hình `MAVEN_USER_HOME` như README backend. Newman được cài trong `.run` (gitignored),
không thay dependency của ứng dụng. Khi không bật biến trên, test này được skip để
build Java thông thường không bắt buộc cài Node. Credential của fixture chỉ được truyền
qua environment của process con; báo cáo chỉ in số request/assertion và tên kiểm tra thất bại.
