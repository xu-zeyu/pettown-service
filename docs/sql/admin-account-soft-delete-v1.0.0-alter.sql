-- v1.0.0 管理员账号软删除增量脚本；仅适用于尚无 admin_account.deleted 的现有表。
-- 由用户在运行库备份及核对结构后手工执行；本仓库不自动迁移。
-- 已执行包含 deleted 列的新版 docs/sql/admin.sql 或整体对齐脚本时不要再执行本脚本。
ALTER TABLE admin_account ADD COLUMN deleted INT NOT NULL DEFAULT 0;
