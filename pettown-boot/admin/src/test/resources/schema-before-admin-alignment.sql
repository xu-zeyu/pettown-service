-- 管理后台基础表；部署前手工执行，不包含默认账号或密码。
CREATE TABLE IF NOT EXISTS admin_account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
    session_version BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL,
    updated_time TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS admin_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_time TIMESTAMP NOT NULL,
    updated_time TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS admin_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(80) NOT NULL,
    CONSTRAINT uk_admin_role_permission UNIQUE (role_id, permission_code),
    CONSTRAINT fk_admin_role_permission_role FOREIGN KEY (role_id) REFERENCES admin_role(id)
);

CREATE TABLE IF NOT EXISTS admin_permission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(80) NOT NULL UNIQUE
);
