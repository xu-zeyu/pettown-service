-- 本地/联调/测试环境种子数据；禁止在生产环境执行。
-- 依赖 docs/sql/admin.sql 先完成建表；全部插入幂等（按 code / username 判重），可重复执行。
-- 不包含任何真实密码：示例账号 password_hash 为占位值，使用前必须通过重置密码接口更新。

-- 示例只读角色
INSERT INTO admin_role (code, name, description, created_time, updated_time)
SELECT 'fixture_readonly', '测试只读角色', '本地联调只读，测试数据可删除', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM admin_role WHERE code = 'fixture_readonly');

INSERT INTO admin_role_permission (role_id, permission_code)
SELECT r.id, 'system:account:read' FROM admin_role r
WHERE r.code = 'fixture_readonly'
  AND NOT EXISTS (SELECT 1 FROM admin_role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_code = 'system:account:read');

INSERT INTO admin_role_permission (role_id, permission_code)
SELECT r.id, 'system:role:read' FROM admin_role r
WHERE r.code = 'fixture_readonly'
  AND NOT EXISTS (SELECT 1 FROM admin_role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_code = 'system:role:read');

-- 示例账号（占位哈希，使用前必须重置密码）
INSERT INTO admin_account (username, display_name, role_id, password_hash, enabled, super_admin, password_change_required, session_version, created_time, updated_time)
SELECT 'fixture_readonly', '测试只读账号',
       (SELECT id FROM admin_role WHERE code = 'fixture_readonly'),
       'REPLACE-ME-PLACEHOLDER-HASH', TRUE, FALSE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM admin_account WHERE username = 'fixture_readonly');
