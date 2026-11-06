-- v1.0.0 宠物档案新库建表脚本。仅适用于尚无 pet 表的数据库。
-- 本仓库不会自动执行；执行前请核对目标库字符集、既有表及备份。
CREATE TABLE IF NOT EXISTS pet (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    species VARCHAR(30) NOT NULL,
    owner_id VARCHAR(31) NOT NULL,
    breed VARCHAR(100) NULL,
    gender VARCHAR(16) NULL,
    birth_date DATE NULL,
    weight_kg DECIMAL(5,2) NULL,
    sterilized BOOLEAN NULL,
    avatar_url VARCHAR(2048) NULL,
    deleted INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    INDEX idx_pet_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
