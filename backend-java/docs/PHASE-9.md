# Phase 9 — Đánh giá optional features

Phase 9 không dịch cơ học các package hoặc feature Node sang Java. Source Node và
frontend đã được kiểm tra để xác định trách nhiệm thật, dependency và mức phù hợp với
Refurbished Device Inventory & Sales Platform.

## Quyết định

| Feature cũ | Usage thực tế | Quyết định | Lý do và điều kiện xem lại |
|---|---|---|---|
| JWT authentication | Middleware đọc Bearer token, User active/verified, refresh/login flows | POSTPONE | Java chưa có User, role hoặc ownership. Chỉ thêm Spring Security sau khi xác định actor/customer/admin và authorization rules. |
| Google/Facebook OAuth | Passport routes và callback frontend | POSTPONE Google; REMOVE Facebook khỏi scope hiện tại | OAuth phụ thuộc identity model. Facebook không có giá trị rõ cho inventory/sales core. |
| Redis | Deduplicate product views trong 24 giờ | REMOVE use case hiện tại | Java domain không có view counter. Không thêm Redis khi PostgreSQL đủ cho core. |
| Reservation expiration | Node cleanup job cho pending Stripe orders | REDESIGN, POSTPONE | Java checkout hiện hoàn tất bán đồng bộ. Chỉ cần khi có `PENDING_PAYMENT`, reservation TTL và webhook idempotency. |
| Stripe payment | Payment intent/webhook, quote và customer ID | REDESIGN, POSTPONE | Phải đổi order lifecycle trước; không gắn Stripe vào checkout `COMPLETED` hiện tại. |
| Email | Verification, password reset và order notifications | POSTPONE | Phụ thuộc User/auth và các business events chưa có. Email phải chạy sau commit/outbox-style khi được làm. |
| Upload/Cloudinary | Product images, multipart upload và Sharp | POSTPONE | Java Product chưa có media model. Cần thiết kế ProductImage/storage contract trước. |
| Gemini chatbot | Tìm generic products, cart và fashion prompts | REMOVE | Prompt và intents không phù hợp refurbished-device domain; không phải core backend. |
| Cart | User/guest cart, merge/sync | REDESIGN, POSTPONE | Mỗi DeviceUnit là duy nhất; cart cần reservation policy để tránh hiển thị thiết bị đã bị người khác mua. |
| Generic quantity inventory/variants | Product/ProductVariant stock counters và ledger | REMOVE | Đã thay bằng DeviceUnit với serial, lifecycle và row locking. |
| Warranty packages | Gói bảo hành gắn Product | REMOVE/REPLACE | Java dùng Warranty gắn đúng DeviceUnit SOLD; không tái dùng package model cũ. |
| Categories/attributes | Generic catalog filtering | POSTPONE | Chỉ thêm taxonomy/specification khi use case tìm kiếm thiết bị yêu cầu. |
| Coupons | Discount và usage rollback | POSTPONE | Không cần cho core inventory/sales; cần price/authorization policy trước. |
| Reviews/wishlist | User-facing engagement | POSTPONE | Phụ thuộc User và frontend mới; không ảnh hưởng invariants thiết bị. |
| Frontend React cũ | Phụ thuộc auth/cart/payment/variants/admin contract Node | REDESIGN | Không tương thích Java API. Giữ làm UI reference; cần frontend mới hoặc adapter riêng. |
| Docker PostgreSQL local | Database dev/test cô lập | KEEP, IMPLEMENTED | `compose.local.yml` đã dùng database/role/port riêng và guard kết nối. |
| Docker image cho Java app | Chưa có | POSTPONE | Container networking cần một profile/guard riêng; không làm yếu local database safety chỉ để container hóa sớm. |
| Nginx/production deployment | Cấu hình Node production cũ | POSTPONE | Production cũ không được chạm. Java chưa deployed. |
| Swagger/OpenAPI | swagger-jsdoc/Swagger UI trong Node | POSTPONE | Có giá trị nhưng không cần thêm dependency trước khi API contract ổn định sau quyết định frontend/auth. |

## Frontend compatibility

Frontend cũ mặc định gọi `http://localhost:8888/api`, tự refresh JWT và dựa vào các
endpoint `/auth`, `/cart`, `/payments`, `/warranty-packages`, product variants,
reviews, coupons và admin APIs. Backend Java chạy `127.0.0.1:8080` và có contract
domain khác. Việc đổi `VITE_API_URL` không đủ để tích hợp.

Một frontend mới nên bắt đầu từ các flow:

1. Danh sách/chi tiết Product.
2. Danh sách DeviceUnit `AVAILABLE` theo Product.
3. Intake và inspection UI cho nhân viên.
4. Checkout chọn đúng DeviceUnit ID.
5. Order detail và Warranty lookup.

Auth và role phải được thiết kế trước khi frontend này được dùng ngoài local demo.

## Kết luận kiến trúc

Không feature tùy chọn nào đủ độc lập và cần thiết để thêm vào runtime trong Phase 9.
Việc không thêm dependency là quyết định kỹ thuật có chủ đích. Core backend tiếp tục
dùng Spring/JPA/PostgreSQL cho những invariants đã kiểm thử. `be/` và `fe/` cũ được
giữ làm reference, chưa được mô tả là đã migrate hoặc tương thích.

## Khi nào có thể archive `be/`

Chỉ archive/xóa bản copy Node sau khi:

- Không cần frontend cũ, hoặc frontend mới đã thay thế contract Node.
- Các quyết định trong bảng này đã được chấp nhận.
- Không còn business rule/file nào cần đối chiếu.
- Đã tạo snapshot/commit/archive riêng.
- Đã xác minh đường dẫn là migration workspace, không phải original repository.

Original Node repository và production deployment vẫn nằm ngoài phạm vi thay đổi.

