-- 单角色模型（admin_account.role_id 直连 admin_role）迁移脚本。
-- 按标记分步导出，供 MySQL 存储过程 / 手工分步执行；测试按标记抽取单条语句执行。
-- 说明：
-- 1. 建议执行顺序：add-role-column → existing-role-check（确认冲突数为 0）→ add-role-index。
-- 2. existing-role-check 在 MySQL 中须在存储过程或会话变量上下文执行（SELECT ... INTO），
--    孤立角色（role_id 指向不存在的角色）只报告、不自动清理。
-- 3. 若已执行 docs/sql/admin-reference-alignment.sql 完成整体对齐，则无需再执行本脚本。

-- add-role-column:start
ALTER TABLE admin_account ADD COLUMN role_id BIGINT NULL
-- add-role-column:end

-- existing-role-check:start
SELECT COUNT(*) INTO migration_conflicts FROM admin_account a LEFT JOIN admin_role r ON a.role_id = r.id WHERE a.role_id IS NOT NULL AND r.id IS NULL
-- existing-role-check:end

-- add-role-index:start
CREATE INDEX idx_admin_account_role_id ON admin_account (role_id)
-- add-role-index:end
