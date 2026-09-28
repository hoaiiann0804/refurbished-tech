package com.example.refurbished.security;

import java.nio.charset.StandardCharsets;
import com.example.refurbished.common.exception.BusinessConflictException;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password) {
        // BCrypt giới hạn theo byte UTF-8; 72 ký tự tiếng Việt có thể vượt 72 byte.
        // Không trim mật khẩu: khoảng trắng cũng là một phần bí mật người dùng chọn.
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessConflictException("Password must contain at least 12 characters and at most 72 UTF-8 bytes.");
        }
    }
}
