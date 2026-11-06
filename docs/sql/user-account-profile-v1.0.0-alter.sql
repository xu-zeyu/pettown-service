-- v1.0.0 C 端用户资料增量脚本（昵称/头像）；由用户核对运行库结构后手工执行，本仓库不自动迁移。
-- 昵称最长 20 字；头像保存本系统上传接口返回的地址，最长 2048。已有记录两列保持 NULL。
-- MySQL 运行库重复执行不会重复加列。
SET @pettown_profile_nickname_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE user_account ADD COLUMN nickname VARCHAR(20) NULL',
        'SELECT ''user_account.nickname already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user_account' AND column_name = 'nickname'
);
PREPARE pettown_profile_nickname_stmt FROM @pettown_profile_nickname_ddl;
EXECUTE pettown_profile_nickname_stmt;
DEALLOCATE PREPARE pettown_profile_nickname_stmt;

SET @pettown_profile_avatar_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE user_account ADD COLUMN avatar VARCHAR(2048) NULL',
        'SELECT ''user_account.avatar already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user_account' AND column_name = 'avatar'
);
PREPARE pettown_profile_avatar_stmt FROM @pettown_profile_avatar_ddl;
EXECUTE pettown_profile_avatar_stmt;
DEALLOCATE PREPARE pettown_profile_avatar_stmt;
