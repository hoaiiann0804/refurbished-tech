# Kế hoạch nâng cấp Refurbished Tech

Ngày lập: 2026-09-25.

Bộ tài liệu minh họa: [Workflow, dữ liệu và tư duy nghiệp vụ](docs/planning/README.md).
Đọc bộ tài liệu này để hiểu mô hình; dùng checklist bên dưới để theo dõi triển khai.

## 1. Mục tiêu và phạm vi

Hoàn thiện hệ thống vận hành nội bộ theo luồng: quản trị tài khoản → quản lý model
máy → nhập từng thiết bị → kiểm định → bán hàng → xử lý bảo hành.

Backend Java là hệ thống chính. Frontend React và backend Node cũ là nguồn tham khảo,
chưa được xem là các tính năng đã tích hợp với Java.

Nguyên tắc làm việc:

- Viết/cập nhật kế hoạch trước khi triển khai mỗi bước.
- Comment giải thích **vì sao** chọn cách xử lý, quy tắc nghiệp vụ và tình huống lỗi;
  không chỉ diễn giải lại cú pháp Java.
- Làm theo thứ tự phụ thuộc bên dưới; hoàn thành kiểm thử bước trước rồi mới mở rộng.
- Không commit/push `be/src/`, file môi trường, mật khẩu, token hoặc database backup.
- Giữ thay đổi CORS sang cổng `5174` do người dùng đã thực hiện.
- Không thao tác database production hoặc dùng cấu hình Node cho Java.
- Không tự đánh dấu hoàn thành chỉ vì đã viết code; phải có bằng chứng kiểm thử.

## 2. Điểm xuất phát và thay đổi đang làm

Core Java Phase 0–10 đã có Product, DeviceUnit, inspection, checkout có transaction,
row locking, Warranty, BCrypt/JWT và Google OIDC có điều kiện. Báo cáo trước đó ghi
73 test pass; người dùng đã kiểm tra health API với kết quả `UP`.

Trước yêu cầu bổ sung kế hoạch, đã viết nháp các thay đổi sau trong working tree:

- API đổi mật khẩu và đăng xuất tất cả phiên.
- Phiên bản token trên user, kiểm tra JWT với trạng thái tài khoản.
- Migration V6 và thu hồi OAuth code theo phiên bản token.
- Khôi phục ADMIN local và script `Start-Local.ps1`.
- Giới hạn cờ bỏ qua authorization cho profile test.
- Bổ sung một số unit/integration tests.

**Cập nhật 2026-09-25: bước 1A đã triển khai và kiểm thử local; chưa commit/push.**
Maven verify: 21 unit tests + 64 integration tests = 85 PASS, không lỗi/bỏ qua.
Test-StartLocal.ps1 PASS với Java giả lập. Database test đã lên V6; chưa migrate dev.
Hướng dẫn và giới hạn: [Bước 1A](backend-java/docs/STEP-1A-ACCOUNTS.md).

## 3. Tư duy nghiệp vụ xuyên suốt

### Thiết bị là một tài sản vật lý

Product là model máy; DeviceUnit là một máy cụ thể có serial duy nhất. Không thay
thế quy tắc này bằng bộ đếm quantity. Mọi thao tác bán, kiểm định và bảo hành phải
truy ngược được đến đúng thiết bị.

### Chuyển trạng thái cần có bằng chứng

Máy chỉ được bán khi AVAILABLE. Kiểm định đạt cần đủ dữ liệu về grade, giá, ghi chú
và pin hoặc lý do không đo được. Khi bổ sung sửa chữa/đổi trả, phải thiết kế transition
hợp lệ thay vì cho sửa trạng thái tùy ý.

### Quyền hạn thay đổi theo thời gian

Token hợp lệ về chữ ký không đảm bảo tài khoản còn được phép thao tác. Đổi mật khẩu,
khóa tài khoản và đổi quyền cần chính sách thu hồi phiên. Khôi phục ADMIN không được
biến thành cách tự cấp quyền cho tài khoản bất kỳ.

### Dữ liệu lịch sử phải giải thích được

Giá bán tại thời điểm chốt đơn không thay đổi theo giá hiện tại. Lịch sử thao tác phải
cho biết ai làm gì, với thiết bị nào, khi nào; không ghi bí mật xác thực vào lịch sử.

## 4. Thứ tự triển khai

### Bổ sung theo yêu cầu — Swagger/OpenAPI

Ưu tiên trước 1B để người dùng thử API hiện có qua trình duyệt.
Tư duy: sinh schema từ controller/DTO để tài liệu theo sát code; mô tả quyền và thứ tự
nghiệp vụ, nhưng không dùng annotation tài liệu để thay authorization thật.

- [x] Thêm springdoc cho Spring Boot 3.5, UI và JSON schema.
- [x] JWT Bearer Authorize; login/health/GET Product không yêu cầu token trên tài liệu.
- [x] Mô tả và nhóm API; giấu tham số JWT nội bộ khỏi form.
- [x] Bật dev/test, mặc định tắt; mở tài liệu không mở quyền API nghiệp vụ.
- [x] Integration test UI/schema và giữ test security pass; hướng dẫn sử dụng.

Kết quả: 21 unit tests và 65 integration tests PASS, gồm UI/schema/JWT và phân quyền.
Hướng dẫn: [Swagger](backend-java/docs/SWAGGER.md).

### Bước 1A — Đổi mật khẩu, khôi phục ADMIN và thu hồi phiên

**Trạng thái:** hoàn thành triển khai và kiểm thử local ngày 2026-09-25. Live Google và prompt recovery trên tài khoản thật chưa được chạy.

**Tư duy nghiệp vụ:** người dùng có thể quên mật khẩu nhưng vẫn phải giữ nguyên ID,
quyền và dữ liệu của tài khoản. Khi đổi mật khẩu, các phiên cũ không được tiếp tục dùng.

**Tư duy giải quyết vấn đề:**

- API đổi mật khẩu lấy user từ JWT, không nhận user ID tùy ý trong body.
- Xác minh mật khẩu hiện tại; kiểm tra mật khẩu mới theo giới hạn UTF-8 của BCrypt.
- Khóa row user khi đổi mật khẩu/logout-all để tránh cập nhật ghi đè.
- Tăng `tokenVersion` cùng transaction cập nhật mật khẩu; so phiên bản ở request sau.
- Chấp nhận thêm lần đọc DB mỗi request để hỗ trợ thu hồi phiên; chưa thêm cache vì
  cache có thể làm chậm hiệu lực thu hồi. Đo hiệu năng trước khi tối ưu.
- Kiểm tra cả OAuth code cũ để không dùng nó phát hành JWT mới sau khi thu hồi phiên.
- Khôi phục ADMIN chỉ chạy local/dev, cho tài khoản ADMIN đã có và đang hoạt động;
  không tự tạo, nâng quyền STAFF hoặc mở khóa user.
- Script nhập mật khẩu ẩn, kiểm tra cấu hình/cổng, nạp env và quản lý biến dùng một lần.

**Cần review kỹ:** ranh giới transaction và row lock; dữ liệu JPA đã nạp trong cùng
transaction; request đang xử lý khi phiên bị thu hồi; migration với token/code cũ;
vòng đời biến recovery và thứ tự startup runners; không báo thành công trước commit.

**Tiêu chí nghiệm thu:**

- [x] Đổi mật khẩu đúng trả 204; mật khẩu mới đăng nhập được, mật khẩu cũ bị từ chối.
- [x] Sai mật khẩu hiện tại, mật khẩu quá ngắn, quá số byte hoặc trùng mật khẩu cũ
  không thay dữ liệu hoặc thu hồi nhầm phiên.
- [x] JWT và OAuth code trước đổi mật khẩu/logout-all bị từ chối.
- [x] JWT của tài khoản disabled hoặc có quyền đã thay đổi bị từ chối.
- [x] Hai yêu cầu đổi mật khẩu đồng thời không ghi đè kết quả trái chính sách.
- [x] Recovery không đổi ID/role; từ chối STAFF, tài khoản khóa và email không tồn tại.
- [x] Recovery chỉ hoạt động trong dev; kiểm thử không đặt lại ADMIN thật của người dùng.
- [x] Script báo rõ thiếu JAR/env, cổng bận, mật khẩu không khớp và Java thoát lỗi.
- [x] Unit tests và PostgreSQL integration tests pass; ghi lại số test thực tế.

### Bước 1B — Quản lý nhân viên và củng cố bảo mật

**Tư duy nghiệp vụ:** ADMIN quản lý nhân viên nhưng không được vô tình khóa hoặc hạ
quyền ADMIN cuối cùng, khiến hệ thống mất khả năng quản trị.

**Tư duy giải quyết vấn đề:**

- Thêm danh sách/chi tiết user có phân trang, sửa display name, khóa/mở và đổi quyền.
- Kiểm tra quyền phía backend; không dựa vào việc frontend ẩn nút.
- Thiết kế khóa transaction cho quy tắc ADMIN cuối cùng, kiểm thử thay đổi đồng thời.
- Thu hồi phiên khi đổi trạng thái/quyền; response không trả password hash.
- Giới hạn thử đăng nhập, trả thông báo không tiết lộ email tồn tại; xác định rõ giới
  hạn theo tài khoản/IP và cách nhận IP khi có reverse proxy.
- Tách rõ xác thực API bằng Bearer JWT và session của Google OAuth; ngăn session OIDC
  vượt qua chính sách tài khoản đã được cấp quyền; đánh giá CSRF theo cơ chế thực tế.
- Chỉ cho phép bypass authorization khi đúng profile test.
- Refresh token/email reset là phần mở rộng sau khi có chính sách vòng đời token,
  nhà cung cấp email và quy trình chống lạm dụng; không tạo endpoint reset không xác minh.

**Tiêu chí nghiệm thu:**

- [ ] Ma trận public/STAFF/ADMIN được test qua HTTP.
- [ ] Không thể làm mất ADMIN hoạt động cuối cùng, kể cả khi có request đồng thời.
- [ ] Kiểm thử JWT hết hạn/sai issuer/sai chữ ký, user bị khóa và quyền đã đổi.
- [ ] Kiểm thử session Google, code hết hạn/tái sử dụng và xử lý lỗi callback.
- [ ] Giới hạn đăng nhập được kiểm thử và không ghi password/token vào log.

### Bước 2 — API vận hành và lịch sử nghiệp vụ

**Đã triển khai bước 2 trong working tree.** [Kế hoạch chi tiết](docs/planning/PLAN-02-OPERATIONS-API.md).
Hợp đồng và bằng chứng kiểm thử: [API vận hành](backend-java/docs/STEP-2-OPERATIONS.md).
Bước 2 được ưu tiên với ADMIN/STAFF hiện có; không có nghĩa bước 1B đã hoàn tất.
Nghiệm thu 2026-09-25: 21 unit + 71 integration tests PASS (92 tổng), có Newman
17 request / 23 assertion / 0 failure trên database test riêng.

**Tư duy nghiệp vụ:** nhân viên thường biết serial hoặc tên khách, không biết UUID.
Hệ thống cần hỗ trợ tìm lại giao dịch và giải thích trách nhiệm của từng thao tác.

**Tư duy giải quyết vấn đề:**

- Thêm danh sách đơn có phân trang, lọc ngày và tìm kiếm phù hợp dữ liệu hiện có.
- Tra cứu DeviceUnit theo serial đã normalize; tránh cách tải toàn bộ để lọc ở client.
- Ghi audit cho nhập kho, kiểm định, bán, cấp bảo hành và quản lý tài khoản.
- Ghi audit cùng transaction với thay đổi nghiệp vụ để không có lịch sử thành công
  cho thao tác đã rollback; không log mật khẩu hoặc JWT.
- Thống nhất hợp đồng lỗi, pagination và validation; cung cấp Postman collection
  với biến môi trường mẫu, không chứa credentials thật.
- Thiết kế idempotency cho checkout: retry cùng khóa và cùng body trả lại kết quả
  cũ; cùng khóa khác body bị từ chối. Không tự retry một thao tác bán chưa rõ kết quả.

**Tiêu chí nghiệm thu:**

- [x] Tra cứu theo serial và lọc/phân trang đơn chính xác, thứ tự kết quả ổn định.
- [x] Audit có actor/action/target/time và không tồn tại nếu nghiệp vụ rollback.
- [x] Test retry checkout, cùng khóa khác body và request đồng thời.
- [x] Postman chạy được luồng đăng nhập → Product → nhập máy → kiểm định → bán → bảo hành.

### Bước 3 — Giao diện vận hành tương thích Java

**Cập nhật 2026-09-26:** UI `/admin/` và browser acceptance đã PASS local. Xem
[nghiệm thu 3–5](docs/planning/IMPLEMENTATION-3-4-5.md). Phần quản lý user đầy đủ
vẫn phụ thuộc bước 1B; không coi UI là thay thế authorization backend.

**Chưa triển khai.** [Kế hoạch chi tiết](docs/planning/PLAN-03-ADMIN-UI.md).

**Tư duy nghiệp vụ:** giao diện phục vụ ADMIN/STAFF trước, bám theo trạng thái từng
thiết bị và quyền thao tác. Chưa đưa giỏ hàng khách hoặc thanh toán online vào luồng này.

**Tư duy giải quyết vấn đề:**

- Xây luồng frontend riêng cho Java, tái sử dụng UI phù hợp nhưng không mang theo
  giả định API Node về token, quantity, cart, payment và warranty package.
- Chốt API contract trước khi viết form; đồng bộ origin/CORS/proxy và OAuth callback.
- Làm lần lượt login → Product → inventory/inspection → checkout/orders → warranty/users.
- Xử lý loading, empty, validation, 401, 403, 409 và lỗi mạng; không báo thành công
  khi chưa nhận kết quả backend, không tự gửi lại checkout khi mạng gián đoạn.
- Không coi ẩn nút là biện pháp phân quyền; backend tiếp tục xác minh mọi request.

**Tiêu chí nghiệm thu:**

- [ ] ADMIN/STAFF thực hiện được các luồng đúng quyền bằng dữ liệu Java thật.
- [ ] Token bị thu hồi đưa người dùng về đăng nhập và xóa trạng thái nhạy cảm.
- [ ] Hai người bán cùng máy: một thành công, người còn lại nhận thông báo dễ hiểu.
- [ ] Build/typecheck và E2E các luồng chính pass; kiểm tra bàn phím và màn hình nhỏ.

### Bước 4 — CI, khả năng quan sát và vận hành

**Cập nhật 2026-09-26:** có workflow CI, metrics/readiness, backup/restore drill và
staging Compose local; chưa có GitHub run, load test hoặc deployment thật.

**Có test/DB local, chưa có bộ vận hành đầy đủ.** [Kế hoạch chi tiết](docs/planning/PLAN-04-JAVA-OPERATIONS.md).

**Tư duy nghiệp vụ:** sửa code không được âm thầm phá checkout; dữ liệu tồn kho/đơn
hàng phải có cách khôi phục và lỗi vận hành phải được phát hiện.

**Tư duy giải quyết vấn đề:**

- Bổ sung Java CI với PostgreSQL test riêng; chạy unit + integration tests và lưu reports.
- Phân biệt workflow legacy và ứng dụng chính, không dùng `|| echo` để che lỗi kiểm tra.
- Thêm request ID, metrics về request/error/latency và chính sách log không lộ bí mật.
- Xác định timeout database/row lock và response khi tranh chấp kéo dài.
- Load test với mục tiêu đo được; đánh giá index/query từ dữ liệu thay vì thêm cache sớm.
- Viết backup/restore runbook và diễn tập restore trên database riêng.
- Thiết kế profile deployment và network guard riêng trước khi container hóa Java;
  không nới guard dev/test để kết nối tùy ý.
- Chốt hosting, domain, secrets, HTTPS, rollback, mục tiêu RPO/RTO/SLO trước triển khai.

**Tiêu chí nghiệm thu:**

- [ ] CI Java chạy trên repo và fail khi test lỗi; không sử dụng database dev/production.
- [ ] Có số liệu tải, độ trễ và giới hạn thực tế; không tự tuyên bố khả năng chịu tải.
- [ ] Restore đã diễn tập thành công và có bằng chứng, không chỉ có file backup.
- [ ] Deployment có kiểm tra sức khỏe và phương án rollback trước khi phát hành.

### Bước 5 — Mở rộng theo mô hình kinh doanh

**Cập nhật 2026-09-26:** reservation TTL, payment/refund sandbox và shipment thủ công
đã có và được test. Payment provider/webhook/refund thật vẫn chưa triển khai.

Nhánh bán online tùy chọn: [kế hoạch và điều kiện bắt đầu](docs/planning/PLAN-05-ONLINE-SALES.md).

**Trạng thái:** chưa triển khai; quyết định sau khi luồng vận hành nội bộ ổn định.

Các nhánh mở rộng:

- Khách hàng, giá vốn/nguồn nhập, báo cáo lợi nhuận.
- Yêu cầu bảo hành, sửa chữa, tái kiểm định và đổi trả có lịch sử.
- Ảnh và thông số máy, tìm kiếm danh mục nâng cao.
- Nếu bán online: reservation TTL, pending payment, webhook idempotency, refund và shipping.

**Tư duy nghiệp vụ:** thiết bị duy nhất có thể bị nhiều khách cùng chọn; thêm payment
phải thay đổi vòng đời đơn/giữ chỗ trước, không gắn thanh toán vào đơn đã COMPLETED.
Đổi trả không đơn giản là sửa SOLD thành AVAILABLE vì còn lịch sử bán và bảo hành.

**Tiêu chí nghiệm thu:** mỗi nhánh có state machine, invariant, migration và test các
trường hợp thất bại/đồng thời trước khi tích hợp vào giao diện.

## 5. Quy ước comment trong code

Comment tại nơi chứa quyết định nghiệp vụ, transaction, lock, validation và quyền hạn.
Ví dụ:

```java
// Nghiệp vụ: đổi mật khẩu phải thu hồi phiên trên các thiết bị khác.
// Giải pháp: tăng tokenVersion cùng transaction; JWT cũ không còn khớp ở request sau.
user.changePassword(encodedPassword);
```

```java
// Hai nhân viên có thể cùng bán một serial. Khóa row trước khi kiểm tra AVAILABLE
// để người chờ khóa đọc trạng thái mới nhất và không tạo thêm một giao dịch bán.
DeviceUnit device = findLockedDevice(id);
```

Không comment rằng hệ thống đảm bảo tuyệt đối nếu test chỉ chứng minh một trường hợp.
Phân biệt rõ ý định thiết kế, bảo đảm của database và giới hạn đang tồn tại.

## 6. Quy trình hoàn thành từng bước

1. Đọc code hiện tại và ghi nhận thay đổi của người dùng.
2. Cập nhật phạm vi, invariant và tiêu chí nghiệm thu trong kế hoạch này.
3. Viết thay đổi nhỏ theo tầng DTO → domain/service → API → persistence khi phù hợp.
4. Thêm comment giải thích quyết định và test hành vi có ý nghĩa.
5. Chạy kiểm tra phù hợp; ghi lệnh, kết quả và phần chưa xác minh.
6. Review diff, migration, quyền truy cập và trường hợp rollback/concurrency.
7. Cập nhật tài liệu sử dụng và trạng thái; chỉ chuyển sang bước tiếp khi tiêu chí đạt.

Không tự triển khai lên dịch vụ bên ngoài khi chưa có đích và cấu hình cụ thể.
Khi cần quyền truy cập môi trường để kiểm thử, nêu rõ hành động và lý do.
