---
name: zentao-bug-intake
description: 用禅道官方 API V2 核实产品、查重、创建并回读测试 Bug；凭证从本 skill 的 .env 读取，适用于 API 测试后的缺陷入库。
---

# 禅道 Bug 入库

用户要求 API 测试缺陷进入禅道时，测试 Agent 先完成用例执行与缺陷复现，再调用本 skill。此流程使用禅道**官方 REST API V2**，不连接禅道 MCP。测试 Agent 与本 Agent 的职责边界见 `../../agents/禅道Agent.md`；企业微信发送统一走 `../wecom-notification/SKILL.md`。

## 配置和核实

1. 在本目录被 Git 忽略的 `.env` 配置 `ZENTAO_BASE_URL`，以及 `ZENTAO_API_TOKEN` 或 `ZENTAO_ACCOUNT` + `ZENTAO_PASSWORD`。Token 优先；账号密码只用于官方 `POST /api.php/v2/users/login` 换取进程内 Token，不写入回执。业务 API 在 `token` 请求头发送凭证。不要把凭证交给 API 测试 skill 的 `.env`，也不要放在命令参数、报告、日志或 Git 文件中。格式见 `.env.example`。
2. `ZENTAO_BASE_URL` 默认只接受有有效 TLS 证书的 HTTPS 或本机回环地址上的受信任隧道。用户明确指定部署好的 HTTP 实例时，仅可在 `.env` 以 `ZENTAO_ALLOW_HTTP_HOST` 精确限定主机和端口，并使用现成 Token；此模式禁止账号密码登录，也不扩展到其他主机。HTTP 传输的 Token 不受 TLS 保护，须在报告中标明实际接入方式。服务地址变更时先核实，不能用其他禅道实例的凭证。
3. 在运行目录执行 `python3 .ai/skills/zentao-bug-intake/scripts/zentao_api.py check --receipt <目标核实回执.json>`。脚本只读分页查询项目与产品，核对配置的项目 ID/名称、唯一产品名及产品详情；回读成功才得到产品 ID。用户给的 `project-index-1.html` 中 `1` 是项目线索，不是产品 ID。若不一致，停止入库。

## 创建与回读

1. 只处理已执行且稳定复现、违反已确认契约的真实缺陷。按根因合并失败，用例与脱敏证据先写入报告。Bug 草稿 JSON 必须有 `title`、`steps`、`failure_signature`、`openedBuild` 字符串数组；主干写 `["trunk"]`。可选 `severity`、`pri`、`type`、`project`。草稿文件保存在当前 `.ai/runs/api-test/.../`，不含令牌、密码、原始个人数据。
2. 调用 `python3 .ai/skills/zentao-bug-intake/scripts/zentao_api.py create-bug --bug-file <草稿.json> --receipt <Bug回执.json>`。脚本先重复核实目标，分页读取该产品 Bug，按稳定失败签名和同名标题查重；同名但签名不同则停止人工核对。每个回执最多 `POST /api.php/v2/bugs` 一次。请求必须含真实 `productID`、标题、`openedBuild` 数组；只对已核实项目填写可选 `project`。
3. 创建返回真实 ID 后，立即 `GET /api.php/v2/bugs/{id}` 核对产品、标题、复现签名；只有 `verified: true` 的回执才算已入库。超时或回读不一致时记为不确定，后续仅按签名只读查重，不自动再次创建。没有真实失败时不制造 Bug。
4. 禅道 Agent 先将已核实 Bug ID、标题、级别、报告路径写入测试报告，再按共享企业微信 skill 的目标、去重与回执规则发送缺陷通知，将业务回执交回测试 Agent。待入库或不确定状态不得写成已入库；本 skill 不自动关闭、解决或指派 Bug。

官方依据：[API V2 登录 Token](https://www.zentao.net/book/api/post-users-login-2142.html)、[产品列表](https://www.zentao.net/book/api/get-products-2153.html)、[创建 Bug](https://www.zentao.net/book/api/post-bugs-2192.html)、[产品 Bug 列表](https://www.zentao.net/book/api/get-products-productid-bugs-2194.html)、[Bug 详情](https://www.zentao.net/book/api/get-bugs-bugid-2197.html)。
