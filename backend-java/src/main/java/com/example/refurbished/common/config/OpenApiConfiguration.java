package com.example.refurbished.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI refurbishedApi() {
        return new OpenAPI().info(new Info().title("Refurbished Tech API").version("1.0")
                .description("Hệ thống vận hành ADMIN/STAFF. Đăng nhập tại POST /api/auth/login, "
                        + "copy accessToken vào Authorize (không thêm Bearer). "
                        + "Luồng thử: Product → nhập máy → kiểm định → checkout → bảo hành. "
                        + "Try it out thực hiện thao tác thật trên database của backend đang chạy."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer operationGuide() {
        Map<String, String> summaries = Map.ofEntries(
                Map.entry("POST /api/online/reservations", "Giữ một serial cho khách trong 15 phút"),
                Map.entry("GET /api/online/reservations", "Danh sách giữ máy của nhân viên; ADMIN xem tất cả"),
                Map.entry("GET /api/online/reservations/capabilities", "Tính năng online đang được bật"),
                Map.entry("GET /api/online/reservations/{id}", "Chi tiết giữ máy và trạng thái tiền/giao hàng"),
                Map.entry("POST /api/online/reservations/{id}/cancel", "Hủy giữ máy chưa thanh toán"),
                Map.entry("POST /api/online/reservations/{id}/sandbox-payment", "ADMIN mô phỏng thanh toán; không thu tiền thật"),
                Map.entry("POST /api/online/reservations/{id}/sandbox-refund", "ADMIN mô phỏng hoàn tiền; không trả máy về kho"),
                Map.entry("POST /api/online/reservations/{id}/shipment", "Ghi vận đơn và xác nhận giao hàng thủ công"),
                Map.entry("GET /api/orders", "Danh sách đơn, lọc tên khách và khoảng thời gian"),
                Map.entry("GET /api/audit-events", "ADMIN tra cứu lịch sử thao tác"),
                Map.entry("POST /api/auth/login", "Đăng nhập bằng email/mật khẩu"),
                Map.entry("GET /api/auth/me", "Xem tài khoản đang đăng nhập"),
                Map.entry("POST /api/auth/change-password", "Đổi mật khẩu và thu hồi mọi phiên cũ"),
                Map.entry("POST /api/auth/logout-all", "Đăng xuất tất cả thiết bị"),
                Map.entry("POST /api/auth/oauth/exchange", "Đổi mã Google dùng một lần lấy JWT"),
                Map.entry("POST /api/users", "ADMIN cấp tài khoản nhân viên"),
                Map.entry("GET /api/products", "Danh sách model máy và phân trang"),
                Map.entry("GET /api/products/{id}", "Chi tiết model máy"),
                Map.entry("POST /api/products", "ADMIN tạo model máy"),
                Map.entry("PUT /api/products/{id}", "ADMIN cập nhật model máy"),
                Map.entry("GET /api/device-units", "Danh sách thiết bị theo product/trạng thái"),
                Map.entry("GET /api/device-units/{id}", "Chi tiết một máy vật lý"),
                Map.entry("POST /api/device-units", "Nhập máy theo serial duy nhất"),
                Map.entry("POST /api/device-units/{id}/start-inspection", "Bắt đầu kiểm định: RECEIVED → INSPECTING"),
                Map.entry("POST /api/device-units/{id}/complete-inspection", "Kết thúc kiểm định: AVAILABLE hoặc REJECTED"),
                Map.entry("POST /api/orders/checkout", "Chốt bán các máy AVAILABLE trong một transaction"),
                Map.entry("GET /api/orders/{id}", "Chi tiết đơn và giá bán đã lưu"),
                Map.entry("POST /api/warranties", "Cấp bảo hành 1–36 tháng cho máy SOLD"),
                Map.entry("GET /api/warranties/{id}", "Tra cứu bảo hành theo ID"),
                Map.entry("GET /api/warranties/device-unit/{deviceUnitId}", "Tra cứu bảo hành theo ID máy"),
                Map.entry("GET /api/health", "Kiểm tra backend và kết nối database"));
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String key = method.name() + " " + path;
            operation.setSummary(summaries.getOrDefault(key, operation.getSummary()));
            String group = path.split("/")[2];
            operation.setTags(List.of(group));
            boolean publicApi = path.equals("/api/health") || path.equals("/api/auth/login")
                    || path.equals("/api/auth/oauth/exchange")
                    || (method.name().equals("GET") && path.startsWith("/api/products"));
            // security=[] bỏ yêu cầu JWT thừa trên login/public API; không đổi SecurityFilterChain.
            if (publicApi) operation.setSecurity(List.of());
            String description = publicApi ? "Public — không cần JWT."
                    : path.startsWith("/api/users") || path.startsWith("/api/audit-events") || (path.startsWith("/api/products") && !method.name().equals("GET"))
                    ? "Quyền: ADMIN." : "Quyền: ADMIN hoặc STAFF, dùng Bearer JWT.";
            if (path.contains("sandbox-")) description = "Chỉ ADMIN, cần app.online.sandbox-enabled=true ở local. "
                    + "Mặc định tắt; không hoạt động ở staging. Mô phỏng nghiệp vụ, không gọi nhà cung cấp thu/hoàn tiền.";
            if (path.startsWith("/api/online/reservations")) description += " STAFF chỉ truy cập reservation do mình tạo; ADMIN xem tất cả. "
                    + "requestId/eventId phải giữ nguyên khi retry; payment đến trễ ghi LATE_PAYMENT để đối soát.";
            if (path.endsWith("complete-inspection")) description += " Khi passed=true cần grade, salePrice, "
                    + "inspectionNotes và batteryHealth hoặc batteryHealthUnavailableReason. Không gửi đồng thời chỉ số pin và lý do thiếu pin.";
            if (path.endsWith("checkout")) description += " Chọn tối đa 20 ID máy, không trùng. Một máy không AVAILABLE "
                    + "thì cả đơn bị từ chối (409); không bán một phần đơn.";
            if (path.endsWith("checkout")) description += " Header Idempotency-Key tùy chọn, 1–128 ký tự chữ/số/._:-, "
                    + "cần JWT; retry cùng key/body trả đơn cũ, khác body trả 409. Không gửi key mới khi chỉ retry do mất mạng.";
            if (path.equals("/api/orders") || path.equals("/api/audit-events")) description += " from/to dùng ISO-8601 UTC, "
                    + "lọc khoảng [from,to). page bắt đầu 0, size 1–100.";
            if (path.equals("/api/device-units") && method.name().equals("GET")) description += " serialNumber tìm chính xác, "
                    + "trim và uppercase; có thể kết hợp productId/status.";
            if (path.equals("/api/warranties") && method.name().equals("POST")) description += " Mỗi máy tối đa một bảo hành; cấp trùng trả 409.";
            if (path.endsWith("change-password") || path.endsWith("logout-all")) description += " Thành công trả 204, "
                    + "không có body. Token cũ bị thu hồi; đăng nhập và Authorize lại.";
            if (path.endsWith("oauth/exchange")) description += " Code đến từ Google callback thật, hết hạn sau 2 phút. "
                    + "Không dùng accessToken làm code; đăng nhập Google bắt đầu tại /oauth2/authorization/google trên trình duyệt.";
            operation.setDescription(description);
        }));
    }
}
