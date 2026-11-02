# 小程序账号接口

小程序使用手机号和密码注册、登录。先在小程序使用的 MySQL 数据库执行 [`sql/user_account.sql`](sql/user_account.sql)，并在 `pettown-boot/miniapp/.env` 配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。账号数据保存在 `user_account` 表；密码以 BCrypt 哈希保存。

两个接口均接收 JSON：

```json
{"phone":"13800138000","password":"strongPass123"}
```

| 操作 | 请求 | 成功状态 |
| --- | --- | --- |
| 注册并登录 | `POST /api/miniapp/auth/register` | 201 |
| 登录 | `POST /api/miniapp/auth/login` | 200 |

成功响应沿用统一格式，例如：

```json
{"code":"200","msg":"操作成功","data":{"userId":1,"phone":"13800138000","token":"..."}}
```

客户端后续请求使用 `Authorization: Bearer <token>`。手机号须为 11 位中国大陆手机号；密码须为 8 至 72 位非空格 ASCII 字符。参数错误、手机号已注册或手机号与密码不匹配返回 HTTP 400 和统一错误响应。Token 默认 30 天有效，可通过 `PETTOWN_TOKEN_TIMEOUT`（秒）调整。

当前 Sa-Token 会话存储为进程内存；服务重启后已签发 Token 会失效。多实例部署前需要接入共享会话存储。

小程序服务提供 `http://localhost:8082/v3/api-docs`，可将此 OpenAPI JSON 地址导入 Apifox。
