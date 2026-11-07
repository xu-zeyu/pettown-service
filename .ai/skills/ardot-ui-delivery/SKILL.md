---
name: ardot-ui-delivery
description: 依据 TAPD 需求先做原型思维，再生成 UI 图片，记录排重、通过 Ardot MCP 入项回读并通知企业微信。
---

# Ardot UI 交付

执行 `../../workflows/08-原型与ArdotUI.md`，严格按原型 Agent → Ardot UI Agent → 台账 → Ardot 入项回读 → 企业微信通知的顺序。

## 执行前

- 读取当前项目配置、架构、产品背景、`ui-guidelines.md`，先验证 `.ai` 初始化所需字段。前端规范未形成或项目身份不明时先完成初始化。
- TAPD 需求须实时核实空间和完整 ID。读取范围仅限完成设计所需字段。用户没有指定具体需求时先确认目标，不从配置推断某条需求。
- 本地台账命令：`python3 .ai/skills/ardot-ui-delivery/scripts/ui_ledger.py check --project <项目ID> --workspace <空间ID> --story <完整需求ID> --screen <screen_key>`。退出码 0 为未生成、3 为已生成；后者不重新生成。

## 图片与台账

每个 `screen_key` 使用稳定、简短的页面/状态名称，图片保存在 `.ai/runs/ui/<项目ID>/<需求ID>/`。调用 `generated --project ... --workspace ... --story ... --screen ... --image <绝对图片路径>` 登记文件 SHA-256。`imported --... --ardot-project <真实项目ID> --ardot-item <真实入项ID> --url <回读链接>` 只能在 Ardot 回读后调用。`notified --... --receipt <脱敏成功回执摘要>` 只能在企业微信成功后调用。命令会拒绝跳阶段和重复登记。

## Ardot MCP

Ardot 以 MCP 接入，文档为 `https://docs.ardot.tencent.com/ardot-mcp/introduction.html`。初始化配置记录项目 ID、文档位置和非敏感的连接方式；令牌或登录凭证不得写进 `.ai`。优先用已连接的 Ardot MCP 列出项目；若工具不支持列表，可在用户已登录的浏览器中读取项目页并核对项目名与 ID。执行前核实 MCP 的图片入项、按 ID 查询、项目归属及链接字段。不要猜工具名或请求参数，也不要把调用成功等同于入项完成。当前环境没有已核实的 Ardot MCP 契约时，生成图片与台账后停在 `generated`，报告具体缺口。

## 通知

使用 `../tapd-requirement-intake/scripts/send_wecom.py` 的已配置机器人，仅在全部本次图片已入项并回读后发送一次汇总通知。发送结果不明确时先核查，勿重发；成功后把同一脱敏回执摘要记入各屏幕台账。报告分别列出生成、入项和通知状态，不把待确认事项写成完成。
