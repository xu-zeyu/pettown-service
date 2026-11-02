# 小程序账号接口

小程序使用手机号和密码登录，首次登录自动注册。新数据库先执行 [`sql/persistence.sql`](sql/persistence.sql)；已有账号表须确认 `id` 为可容纳 31 字符的字符串列后再接入。再在 `pettown-boot/miniapp/.env` 配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。账号数据通过 MyBatis-Plus 保存在 `user_account` 表；密码以 BCrypt 哈希保存。

登录接口接收 JSON：

```json
{"phone":"13800138000","password":"strongPass123"}
```

`POST /api/miniapp/auth/login`：手机号不存在时创建账号并返回 Token；已存在时校验密码并返回 Token。成功时 HTTP 200。

成功响应沿用统一格式，例如：

```json
{"code":"200","msg":"操作成功","data":{"userId":"pettown<24位十六进制哈希>","phone":"13800138000","token":"..."}}
```

客户端后续请求使用 `Authorization: Bearer <token>`。手机号须为 11 位中国大陆手机号；密码须为 8 至 72 位非空格 ASCII 字符。参数错误或已有账号密码不匹配返回 HTTP 400 和统一错误响应。Token 默认 30 天有效，可通过 `PETTOWN_TOKEN_TIMEOUT`（秒）调整。

`GET /api/miniapp/auth/me`：携带上述请求头，返回当前登录用户的 ID 和手机号：

```json
{"code":"200","msg":"操作成功","data":{"userId":"pettown<24位十六进制哈希>","phone":"13800138000"}}
```

缺少、无效或过期的 Token 返回 HTTP 401；Token 对应的用户已被删除时返回 HTTP 404。响应不包含密码哈希。

新账号 ID 为 `pettown` 加 24 位 SHA-256 十六进制哈希（共 31 字符），输入来自安全随机数；数据库主键确保唯一，碰撞时重试。手机号使用 ASCII 定长列和唯一索引。初始化脚本不修改已有表结构或用户 ID；已有数字 ID 表需先完成单独的数据迁移。

当前 Sa-Token 会话存储为进程内存；服务重启后已签发 Token 会失效。多实例部署前需要接入共享会话存储。

小程序服务提供 `http://localhost:8082/v3/api-docs`，可将此 OpenAPI JSON 地址导入 Apifox。
