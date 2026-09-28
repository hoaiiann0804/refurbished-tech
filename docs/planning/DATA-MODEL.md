# Sơ đồ dữ liệu và định hướng mở rộng

[Mục lục](README.md) · [Workflow](WORKFLOW.md)

## 1. ERD hiện có — migrations V1–V5

Sơ đồ liệt kê trường chính, không thay thế toàn bộ DDL. Tham chiếu chính xác:
[migrations](../../backend-java/src/main/resources/db/migration/).

```mermaid
erDiagram
    PRODUCTS ||--o{ DEVICE_UNITS : groups
    SALES_ORDERS ||--|{ ORDER_ITEMS : contains
    DEVICE_UNITS ||--o| ORDER_ITEMS : sold_once
    DEVICE_UNITS ||--o| WARRANTIES : covered_by
    APP_USERS ||--o{ OAUTH_LOGIN_CODES : receives

    PRODUCTS {
        UUID id PK
        varchar model_code UK
        varchar name
        varchar brand
        varchar specification_summary
        boolean active
    }
    DEVICE_UNITS {
        UUID id PK
        UUID product_id FK
        varchar serial_number UK
        varchar status
        varchar grade
        integer battery_health
        varchar battery_health_unavailable_reason
        numeric sale_price
        boolean inspection_passed
        varchar inspection_notes
        timestamptz inspected_at
    }
    SALES_ORDERS {
        UUID id PK
        varchar customer_name
        varchar status
        numeric total_amount
        timestamptz created_at
    }
    ORDER_ITEMS {
        UUID id PK
        UUID order_id FK
        UUID device_unit_id FK,UK
        numeric unit_price
    }
    WARRANTIES {
        UUID id PK
        UUID device_unit_id FK,UK
        integer duration_months
        date starts_on
        date ends_on
    }
    APP_USERS {
        UUID id PK
        varchar email UK
        varchar display_name
        varchar password_hash
        varchar role
        boolean enabled
    }
    OAUTH_LOGIN_CODES {
        UUID id PK
        UUID user_id FK
        varchar code_hash UK
        timestamptz expires_at
        timestamptz used_at
    }
```

Quan hệ một đơn có ít nhất một item là yêu cầu của luồng checkout. FK trong SQL chỉ
bảo vệ hướng item → order, không tự đảm bảo một row order luôn có ít nhất một item.

## 2. Ý nghĩa và bất biến

| Dữ liệu | Ý nghĩa nghiệp vụ | Cách bảo vệ hiện có |
|---|---|---|
| Product | Một model/cấu hình chung | model_code unique, validation |
| DeviceUnit | Một máy vật lý | serial unique, FK Product, state machine |
| Order | Một lần chốt bán | transaction, trạng thái hiện chỉ COMPLETED |
| OrderItem | Máy cụ thể và giá đã bán | FK, unique device_unit_id, snapshot giá |
| Warranty | Thời hạn bảo hành của đúng máy | unique device_unit_id, thời hạn được kiểm tra |
| AppUser | Nhân viên được cấp quyền | email unique, BCrypt, ADMIN/STAFF |
| OAuthLoginCode | Vé đổi JWT dùng một lần | chỉ lưu hash, thời hạn và row lock |

Các điều kiện quan hệ như “chỉ cấp bảo hành cho máy SOLD” do service kiểm tra dưới
transaction/lock; không nên hiểu rằng mọi quy tắc đều đã được một SQL CHECK bảo vệ.

Hiện chưa có FK từ Order/DeviceUnit/Warranty đến nhân viên đã thực hiện thao tác.
Tên khách trong Order là chuỗi, chưa có bảng Customer. Inspection hiện nằm ngay trên
DeviceUnit, chưa có bảng lưu nhiều lần kiểm định.

## 3. Thay đổi V6 — ĐÃ KIỂM THỬ LOCAL

| Bảng | Trường thêm | Lý do |
|---|---|---|
| app_users | token_version | Đổi mật khẩu/logout-all làm JWT cũ hết hiệu lực |
| oauth_login_codes | token_version | Không cho code cũ tạo phiên mới sau thu hồi |

JWT mang claim `ver`; đây là dữ liệu trong token, không phải một bảng JWT trong DB.
Các token cũ thiếu `ver` sẽ cần đăng nhập lại sau khi nâng cấp. Code cũ được migration
đánh dấu không khớp phiên bản. Cần test việc nâng cấp trước khi dùng bản mới.

## 4. Mô hình mở rộng — KẾ HOẠCH, chưa có migration

```mermaid
erDiagram
    APP_USERS ||--o{ AUDIT_EVENTS : performs
    DEVICE_UNITS ||--o{ INSPECTIONS : inspected_multiple_times
    APP_USERS ||--o{ INSPECTIONS : inspects
    WARRANTIES ||--o{ WARRANTY_CLAIMS : receives
    CUSTOMERS o|--o{ SALES_ORDERS : purchases
    PRODUCTS ||--o{ PRODUCT_IMAGES : displays
    APP_USERS ||--o{ CHECKOUT_REQUESTS : submits
    SALES_ORDERS o|--o| CHECKOUT_REQUESTS : records_result
```

Sơ đồ mở rộng giữ các ý tưởng ban đầu; riêng audit_events/checkout_requests đã có V7.
Quan hệ actor là quan hệ logic, không có FK đến app_users. Schema thực tế bổ sung:

```mermaid
erDiagram
    SALES_ORDERS o|--o{ CHECKOUT_REQUESTS : result_fk
    CHECKOUT_REQUESTS {
        UUID actor_id PK
        varchar request_key PK
        varchar request_hash
        UUID order_id FK
        timestamptz created_at
    }
    AUDIT_EVENTS {
        UUID id PK
        UUID actor_user_id
        varchar actor_kind
        varchar action
        varchar target_type
        UUID target_id
        timestamptz occurred_at
        jsonb details
    }
```

order_id được phép null trong transaction đang xử lý; service điền kết quả trước commit.
Audit giữ actor/target như snapshot, không có FK đa hình. Các bảng còn lại vẫn là đề xuất.

| Bảng đề xuất | Nội dung dự kiến | Quyết định cần chốt |
|---|---|---|
| audit_events (đã có V7) | actor, action, target type/id, thời gian, chi tiết allowlist; ADMIN đọc | Thời hạn lưu, kho log chống sửa thuộc vận hành |
| inspections | device, nhân viên, kết quả, bằng chứng, thời điểm | Khi nào cho tái kiểm định, lưu kết quả cũ thế nào |
| warranty_claims | warranty, lỗi, trạng thái xử lý, bàn giao | Điều kiện chấp nhận và quy trình sửa/đổi |
| customers | tên và thông tin liên hệ tối thiểu | Khách vãng lai, trùng số điện thoại, quyền truy cập |
| product_images | product, storage key, thứ tự, mô tả | Nhà cung cấp lưu trữ và giới hạn upload |
| checkout_requests (đã có V7) | PK(actor_id, request_key), request_hash, order_id, created_at | Hiện giữ key không TTL; kết quả thành công commit cùng đơn |

Retention và kho audit độc lập vẫn thuộc kế hoạch vận hành.

## 5. Những thay đổi không được làm cơ học

- Không thêm `Product.quantity` để thay DeviceUnit: mất dấu máy cụ thể.
- Không sửa giá OrderItem khi thay giá niêm yết: sai lịch sử doanh thu.
- Không xóa user/thiết bị tùy ý khi đã có lịch sử tham chiếu.
- Không sửa migration V1–V5 đã áp dụng; thêm migration mới.
- Không bỏ unique `order_items.device_unit_id` chỉ để hỗ trợ bán lại máy đổi trả.
  Cần mô hình lần bán/hoàn trả và điều kiện hợp lệ trước khi nới constraint đó.
- Không thêm cột `payment_status` rồi coi checkout online đã hoàn tất: còn reservation,
  webhook, retry, hủy và hoàn tiền.
