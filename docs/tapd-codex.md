# Codex 中使用 TAPD MCP

本项目通过 `.codex/config.toml` 配置 `tapd` MCP，使用腾讯云 TAPD MCP Server 文档推荐的 `uvx mcp-server-tapd` stdio 模式。仓库根目录的 `AGENTS.md` 规定了使用方式。此配置仅作用于受信任的本项目。

## 首次启用

1. 安装 `uv`，确认 `/opt/homebrew/bin/uvx` 存在。
2. 在 TAPD 的“我的设置 → 个人访问令牌”创建个人令牌。不要将令牌写入仓库或聊天。
3. 在启动 Codex 的环境中设置 `TAPD_ACCESS_TOKEN`。如果从 macOS 图形界面启动 Codex，可在 zsh 终端交互输入（令牌不会出现在命令历史中），然后完全退出并重新启动 Codex：

   ```zsh
   read -rs 'tapd_token?TAPD token: '
   print
   launchctl setenv TAPD_ACCESS_TOKEN "$tapd_token"
   unset tapd_token
   ```
4. 在 Codex 中输入 `/mcp` 确认 `tapd` 已连接，或在本项目目录运行 `/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex mcp get tapd` 检查配置。再尝试读取一条自己有权限的 TAPD 记录，验证认证和网络访问。

只使用个人令牌一种认证方式。TAPD 文档还支持 API 账号和密钥；如需改用该方式，应将配置的 `env_vars` 改为 `TAPD_API_USER`、`TAPD_API_PASSWORD`，并停止传入个人令牌。

参考：[腾讯云 TAPD MCP Server](https://cloud.tencent.cn/developer/mcp/server/11474) · [OpenAI Docs：Codex MCP 配置](https://developers.openai.com/codex/mcp)
