-- 管理后台基础表；部署前手工执行，不包含默认账号或密码。
CREATE TABLE IF NOT EXISTS user_account (
    id VARCHAR(31) NOT NULL PRIMARY KEY,
    phone CHAR(11) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    nickname VARCHAR(20),
    avatar VARCHAR(2048),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(50) NOT NULL,
    role_id BIGINT NULL,
    phone VARCHAR(11),
    avatar VARCHAR(500),
    last_login_time TIMESTAMP,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
    session_version BIGINT NOT NULL DEFAULT 0,
    deleted INT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL,
    updated_time TIMESTAMP NOT NULL,
    INDEX idx_admin_account_role_id (role_id)
);
CREATE TABLE IF NOT EXISTS admin_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    deleted INT NOT NULL DEFAULT 0,
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
INSERT INTO admin_permission (name, code) SELECT '查看管理员', 'system:account:read' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:account:read');
INSERT INTO admin_permission (name, code) SELECT '创建管理员', 'system:account:create' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:account:create');
INSERT INTO admin_permission (name, code) SELECT '修改管理员', 'system:account:update' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:account:update');
INSERT INTO admin_permission (name, code) SELECT '启停管理员', 'system:account:status' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:account:status');
INSERT INTO admin_permission (name, code) SELECT '重置管理员密码', 'system:account:reset-password' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:account:reset-password');
INSERT INTO admin_permission (name, code) SELECT '分配管理员角色', 'system:account:assign-role' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:account:assign-role');
INSERT INTO admin_permission (name, code) SELECT '查看角色', 'system:role:read' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:role:read');
INSERT INTO admin_permission (name, code) SELECT '创建角色', 'system:role:create' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:role:create');
INSERT INTO admin_permission (name, code) SELECT '修改角色', 'system:role:update' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:role:update');
INSERT INTO admin_permission (name, code) SELECT '删除角色', 'system:role:delete' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:role:delete');
INSERT INTO admin_permission (name, code) SELECT '分配角色权限', 'system:role:assign-permission' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:role:assign-permission');
INSERT INTO admin_permission (name, code) SELECT '查看权限点', 'system:permission:read' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:permission:read');
INSERT INTO admin_permission (name, code) SELECT '管理后台权限与子管理员', 'SUB_ADMIN' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'SUB_ADMIN');
INSERT INTO admin_permission (name, code) SELECT '删除管理员', 'system:account:delete' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'system:account:delete');
INSERT INTO admin_permission (name, code) SELECT '查看 C 端用户', 'biz:user:read' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'biz:user:read');
INSERT INTO admin_permission (name, code) SELECT '封禁解封 C 端用户', 'biz:user:status' WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'biz:user:status');
