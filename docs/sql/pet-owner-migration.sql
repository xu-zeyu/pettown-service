-- 宠物归属用户（pet.owner_id）迁移脚本；执行前请备份。
-- 契约：pet.owner_id = user_account.id（VARCHAR(31)），宠物数据按归属用户隔离。
-- 说明：
-- 1. 第一步先加可空列，避免存量数据阻塞 DDL。
-- 2. 第二步回填：必须由业务确认每条存量宠物的归属后执行（占位符替换为真实 user_account.id）；
--    未回填（owner_id 为 NULL）的宠物在 App 接口中对任何用户不可见。
-- 3. 第三步在回填清零后可选收紧为 NOT NULL（需业务确认后执行）。

ALTER TABLE pet ADD COLUMN owner_id VARCHAR(31) NULL;

-- 回填示例（执行前替换占位符；按业务确认的真实归属逐条/批量更新）：
-- UPDATE pet SET owner_id = '<legacy-owner-user-id>' WHERE owner_id IS NULL;

-- 确认无遗留 NULL 后可选收紧（需业务确认后执行）：
-- ALTER TABLE pet MODIFY COLUMN owner_id VARCHAR(31) NOT NULL;

CREATE INDEX idx_pet_owner_id ON pet (owner_id);
