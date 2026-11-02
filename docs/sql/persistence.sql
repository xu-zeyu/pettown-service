-- 新库初始化：应用不自动建表，部署前在目标 MySQL 数据库执行。
-- 已有 user_account 表继续使用原数据，id 须为可容纳 31 字符的字符串列。
CREATE TABLE IF NOT EXISTS user_account (
    id VARCHAR(31) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    phone CHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_account_phone (phone)
);

CREATE TABLE IF NOT EXISTS pet (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    species VARCHAR(30) NOT NULL,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL
);
