-- v1.0.0 C 端用户状态增量脚本；由用户核对运行库结构后手工执行，本仓库不自动迁移。
-- 已有记录默认 ACTIVE；BANNED 持续到管理员手动解封。不存储临时封禁期限。
-- MySQL 运行库重复执行不会重复加列或权限定义。
SET @pettown_user_status_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE user_account ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE''',
        'SELECT ''user_account.status already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user_account' AND column_name = 'status'
);
PREPARE pettown_user_status_stmt FROM @pettown_user_status_ddl;
EXECUTE pettown_user_status_stmt;
DEALLOCATE PREPARE pettown_user_status_stmt;

INSERT INTO admin_permission (name, code)
SELECT '查看 C 端用户', 'biz:user:read'
WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'biz:user:read');
INSERT INTO admin_permission (name, code)
SELECT '封禁解封 C 端用户', 'biz:user:status'
WHERE NOT EXISTS (SELECT 1 FROM admin_permission WHERE code = 'biz:user:status');
