-- ============================================================================
-- V10: Seed i18n_messages with auth-service messages (centralized-i18n-management)
-- Source: auth-service V6__seed_i18n_messages.sql (20 records)
--         + auth-service V12__seed_event_error_messages.sql (8 records)
-- Purpose: Consolidate auth i18n messages into system-admin-service DB
-- Total: 28 records (14 en + 14 vi), module = 'auth'
-- ============================================================================

-- English (en) auth messages — from V6
INSERT INTO i18n_messages (code, locale, message, module, is_active, created_at, updated_at) VALUES
('auth.invalid_credentials', 'en', 'Invalid username or password', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.account_locked', 'en', 'Account is locked due to failed login attempts', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.token_expired', 'en', 'JWT token has expired', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.permission_denied', 'en', 'Insufficient permissions', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.captcha_required', 'en', 'CAPTCHA verification required', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.captcha_failed', 'en', 'CAPTCHA verification failed', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.mfa_code_invalid', 'en', 'Invalid MFA verification code', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.mfa_rate_limited', 'en', 'MFA rate limit exceeded', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.rate_limited', 'en', 'Too many login attempts', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.session_limit', 'en', 'Maximum active sessions ({0}) reached', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (code, locale) DO NOTHING;

-- English (en) event sourcing error messages — from V12
INSERT INTO i18n_messages (code, locale, message, module, is_active, created_at, updated_at) VALUES
('auth.event_store_persist_failed', 'en', 'Event store write failure', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.outbox_publish_failed', 'en', 'Event publish failed', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.event_not_found', 'en', 'Event not found', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.outbox_max_retries', 'en', 'Event publish max retries exceeded', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (code, locale) DO NOTHING;

-- Vietnamese (vi) auth messages — from V6
INSERT INTO i18n_messages (code, locale, message, module, is_active, created_at, updated_at) VALUES
('auth.invalid_credentials', 'vi', 'Sai tên đăng nhập hoặc mật khẩu', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.account_locked', 'vi', 'Tài khoản bị khóa do đăng nhập sai quá nhiều lần', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.token_expired', 'vi', 'Token đã hết hạn', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.permission_denied', 'vi', 'Không đủ quyền truy cập', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.captcha_required', 'vi', 'Yêu cầu xác minh CAPTCHA', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.captcha_failed', 'vi', 'Xác minh CAPTCHA thất bại', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.mfa_code_invalid', 'vi', 'Mã xác minh MFA không hợp lệ', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.mfa_rate_limited', 'vi', 'Đã vượt quá giới hạn tốc độ xác minh MFA', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.rate_limited', 'vi', 'Quá nhiều lần đăng nhập', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.session_limit', 'vi', 'Đã đạt tối đa {0} phiên hoạt động', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (code, locale) DO NOTHING;

-- Vietnamese (vi) event sourcing error messages — from V12
INSERT INTO i18n_messages (code, locale, message, module, is_active, created_at, updated_at) VALUES
('auth.event_store_persist_failed', 'vi', 'Lỗi ghi vào event store', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.outbox_publish_failed', 'vi', 'Gửi sự kiện thất bại', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.event_not_found', 'vi', 'Không tìm thấy sự kiện', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.outbox_max_retries', 'vi', 'Vượt quá số lần thử gửi sự kiện', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (code, locale) DO NOTHING;
