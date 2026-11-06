-- v1.0.0 宠物健康记录新表脚本；由用户核对目标库后手工执行，应用不会自动迁移。
-- 执行前确认 pet.id 为 BIGINT，目标库字符集和引擎符合预期，并完成备份。
CREATE TABLE IF NOT EXISTS pet_health_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pet_id BIGINT NOT NULL,
    record_type VARCHAR(16) NOT NULL,
    title VARCHAR(100) NOT NULL,
    record_date DATE NOT NULL,
    notes VARCHAR(2000) NULL,
    next_reminder_date DATE NULL,
    deleted INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    INDEX idx_pet_health_record_pet_date (pet_id, record_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
