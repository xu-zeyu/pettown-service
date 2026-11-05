-- v1.0.0 宠物档案旧库加列脚本。仅适用于已存在基础 pet 表且尚无以下六列的数据库。
-- 本仓库不会自动执行；执行前请核对目标库 pet 表真实 DDL、备份与维护窗口。
-- 不回填旧记录，新增字段均可为 NULL；既有 id/name/species/owner_id/时间列保持原状。
ALTER TABLE pet ADD COLUMN breed VARCHAR(100) NULL;
ALTER TABLE pet ADD COLUMN gender VARCHAR(16) NULL;
ALTER TABLE pet ADD COLUMN birth_date DATE NULL;
ALTER TABLE pet ADD COLUMN weight_kg DECIMAL(5,2) NULL;
ALTER TABLE pet ADD COLUMN sterilized BOOLEAN NULL;
ALTER TABLE pet ADD COLUMN avatar_url VARCHAR(2048) NULL;
