-- Existing MySQL8 database: run once before starting new backend; contact values remain unchanged.
ALTER TABLE `user`
 ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '邮箱已验证',
 ADD COLUMN phone_verified TINYINT NOT NULL DEFAULT 0 COMMENT '手机号已验证',
 ADD COLUMN auth_version INT NOT NULL DEFAULT 0 COMMENT '会话版本';
