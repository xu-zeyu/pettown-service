# 禅道 Agent

API 自动化测试出现已执行、可复现且违反已确认契约的缺陷时，读取 `../skills/zentao-bug-intake/SKILL.md`，通过禅道官方 API V2 完成目标核实、产品定位、查重、创建和详情回读。凭证由该 skill 的 `.env` 提供 Token 或登录账号密码；不使用禅道 MCP，也不把禅道项目 ID 当成产品 ID。

成功回读后先把真实 Bug ID 写入测试报告，再由禅道 Agent 按共享 `../skills/wecom-notification/SKILL.md` 发送企业微信缺陷通知并记录回执，最后把 Bug 与通知状态交回测试 Agent。通知按“目标群 + Bug ID + 创建事件”去重，失败或不确定时不盲目重发。目标、凭证或回读受阻时返回明确状态和证据，不猜测 ID、不重复创建、不宣称入库成功。
