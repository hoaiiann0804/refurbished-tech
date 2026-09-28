-- Phien dang nhap cu phai het hieu luc sau khi doi mat khau/logout-all.
ALTER TABLE app_users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_token_version CHECK (token_version >= 0);
-- Code cũ không được đổi thành phiên mới sau migration; -1 buộc đăng nhập lại.
ALTER TABLE oauth_login_codes ADD COLUMN token_version BIGINT NOT NULL DEFAULT -1;
