# TAPD 使用规范

- 需求池评分、TAPD 入库及企业微信通知的组合任务，读取 `.ai/skills/tapd-requirement-intake/SKILL.md` 与 `.ai/workflows/03-需求评审与TAPD入库.md`；用户明确授权的创建和通知无需重复审批。
- 每次版本需求池至少提供 20 条去重、属于该版本、可验收且评分不低于 75 分的合格需求；不足时报告缺口，不靠编造、重复拆分或抬分凑数，也不启动该批次的 TAPD 入库和通知。
- TAPD 需求池的负责人默认取当前项目配置；`pet-system` 默认为徐泽宇，单条需求有明确指定时覆盖。正式入库前在对应 TAPD 项目空间核对成员，回读确认负责人。

- 任务明确关联 TAPD（用户说明 TAPD 中的需求、缺陷、任务、迭代，或提供 TAPD 链接、ID）时，优先通过 `tapd` MCP 查询对应记录，再处理与其相关的代码或答复。普通开发任务无需查询 TAPD。
- 先确认记录所属的项目空间和具体 ID。名称相似或只有模糊描述时，先搜索并核对标题、状态及上下文；不要猜测项目空间或记录。
- 以 TAPD 中最新可读取的记录为准，区分记录事实与自己的推断。关键结论附上记录 ID 或链接；查询失败时说明无法核实，不把本地缓存当作实时状态。
- 读取时只获取完成当前任务所需的字段。不要在日志、代码、提交、评论或答复中泄露 TAPD 令牌、API 密钥及无关的内部信息。
- 用户明确要求创建或修改 TAPD 记录时，先读取现有记录，使用准确的项目空间和 ID，按请求执行并回读核对结果。状态流转、指派、删除、批量更新和通知只在用户授权的范围内执行。
- 代码与 TAPD 记录关联时，在结果中简要说明实现内容、验证结果及对应记录；不要仅凭代码改动自行关闭需求或缺陷。

# 版本研发入口

- “初始化 .ai / 初始化项目”等指令先读取 `.ai/workflows/00-项目初始化.md` 和 01；先扫描目标仓库架构并填全适用配置，前端或客户端项目还要形成 `project-knowledge/<项目ID>/ui-guidelines.md`。不可读或无法证明属于项目的 `.ai` 同级目录直接跳过；只向用户询问目标项目中无法核实的必填信息。
- “根据 TAPD 需求生成原型/UI”等指令依次读取 `.ai/agents/原型Agent.md`、`.ai/agents/ArdotUIAgent.md`、`.ai/workflows/08-原型与ArdotUI.md` 和 `.ai/skills/ardot-ui-delivery/SKILL.md`。先核实 TAPD 记录和 UI 生成台账；原型先于 UI，图片入 Ardot 回读后才发企业微信通知。

- “生成 <版本> 版本需求”等口语指令读取 `.ai/agents/研发Agent.md`、`.ai/skills/tapd-requirement-intake/SKILL.md` 与工作流 02/03，按版本完成需求池、评分评审、TAPD 入库回读和企业微信创建通知。
- “开始 <版本> 代码编写”等口语指令读取 `.ai/agents/研发Agent.md`、`.ai/skills/tapd-version-delivery/SKILL.md` 与工作流 04/05/06。从 TAPD 整理清单，按 P0→P3 及依赖逐项独立分支执行；当前项开始通知、代码审查、自测、合并核对和完成通知结束后才能开始下一项。
- “进行 <模块> 的全量测试”“测试 <模块> 接口”等口语指令先读取 `.ai/agents/测试Agent.md` 和 `.ai/workflows/07-模块自动化测试.md` 做路由；后端 HTTP 接口模块走 `.ai/skills/api-regression-testing/SKILL.md`。必须先将本次涉及的每个项目/服务的 API 用 Apifox 官方格式导入各自目标并导出回读核对，成功后才编写、执行用例。多项目场景须向用户逐一确认“项目 ID → Apifox 项目 ID”，同项目多服务也须确认各服务目标；串联验证全部已确认关联业务链路。可复现缺陷按已核实产品 ID 通过禅道 MCP 创建并回读，随后使用共享 `.ai/skills/wecom-notification/SKILL.md` 通知；产品 ID 不明时不入库。凭证配置于 `.ai/skills/api-regression-testing/.env`；上传受阻时停止本批测试，不以本地执行代替上传。
- 以上口语指令的授权范围按 `.ai/core/人工审批规范.md` 执行；不推断具体 TAPD 版本或记录，不自动关闭 TAPD 事项。
