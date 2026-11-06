-- 存量库对齐迁移：将旧四表结构升级到 docs/sql/admin.sql（参考 schema）的契约。
-- 不包含默认账号或密码；不修改、不丢失既有 admin_account / admin_role / admin_role_permission 数据。
-- 说明：
-- 1. 本脚本为一次性迁移（列级 ALTER 不做幂等），执行前请备份。
-- 2. 本脚本已包含单角色（admin_account.role_id）对齐；整体执行后无需再执行 admin-single-role.sql。
-- 3. 权限目录同步部分可重复执行（WHERE NOT EXISTS 判重）。

ALTER TABLE admin_account ADD COLUMN role_id BIGINT NULL;
ALTER TABLE admin_account ADD COLUMN phone VARCHAR(11);
ALTER TABLE admin_account ADD COLUMN avatar VARCHAR(500);
ALTER TABLE admin_account ADD COLUMN last_login_time TIMESTAMP;
ALTER TABLE admin_account ADD COLUMN deleted INT NOT NULL DEFAULT 0;
ALTER TABLE admin_role ADD COLUMN description VARCHAR(500);
ALTER TABLE admin_role ADD COLUMN deleted INT NOT NULL DEFAULT 0;
CREATE INDEX idx_admin_account_role_id ON admin_account (role_id);

-- 内置权限目录（与 admin.sql 一致，可重复执行）
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

-- 将既有角色绑定中的历史权限编码同步进权限目录（名称截断至 50 字符），不覆盖已有目录项、不改动任何绑定
INSERT INTO admin_permission (name, code)
SELECT DISTINCT SUBSTRING(rp.permission_code, 1, 50), rp.permission_code
FROM admin_role_permission rp
WHERE NOT EXISTS (SELECT 1 FROM admin_permission p WHERE p.code = rp.permission_code);
