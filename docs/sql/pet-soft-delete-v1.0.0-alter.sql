-- v1.0.0 宠物和健康记录软删除增量脚本；由用户核对目标库 DDL 后手工执行。
-- 仅适用于两张表均已存在且均尚无 deleted 列的数据库；请先备份，不要重复执行。
ALTER TABLE pet ADD COLUMN deleted INT NOT NULL DEFAULT 0;
ALTER TABLE pet_health_record ADD COLUMN deleted INT NOT NULL DEFAULT 0;
