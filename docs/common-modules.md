# 公共模块接入

本次按 PetTown 的 `com.pettown.common` 包名和既有模块边界，复用 `ai-commerce` 的 MyBatis Plus 分页与时间填充、Redis/Redisson 缓存、Sa-Token 账号类型、OSS 文件服务、操作日志与 Trace ID。没有复制原项目的管理员、用户表与日志表 Mapper；这些属于业务数据，需要在对应业务模块落表后实现。

| 模块 | 已提供能力 | 接入条件 |
| --- | --- | --- |
| `common-mybatis` | MyBatis Plus 分页插件、自动填充、基础实体、分页结果 | 三个 Boot 工程均已依赖该模块；启用 JDBC 自动配置并提供数据库连接后使用。 |
| `common-redis` | `RedisCache` 字符串缓存、Redisson 客户端 | 三个 Boot 工程均已依赖该模块；通过各自 `.env` 配置 Redis，按需启用 Redisson。 |
| `common-security` | Sa-Token 的 `user`、`admin` 独立账号空间及未登录异常响应 | 使用方依赖该模块；业务登录接口负责校验账号后发 Token。当前模块只包含 Sa-Token 核心，尚未装配 HTTP Token 解析。 |
| `common-file` | `FileService` 的阿里云 OSS 上传下载实现 | 使用方依赖该模块，设置 `pettown.file.enabled=true` 及 `aliyun.oss.endpoint`、`access-key-id`、`access-key-secret`、`bucket-name`、`region`。密钥通过环境变量或部署配置传入。 |
| `common-log` | `@Log` 操作日志、`X-Trace-Id` 请求追踪、滚动文件日志 | 已由 Boot 与 `module-app` 引用；日志默认写到 `logs/pettown-server.log`。可通过 `logging.file.name` 改路径。 |

验证所有公共模块编译及启动测试：

```bash
./gradlew --offline :common-mybatis:compileJava :common-redis:compileJava \
  :common-security:compileJava :common-file:compileJava :common-log:compileJava \
  :pettown-boot:app:test :pettown-boot:admin:bootJar :pettown-boot:miniapp:bootJar
```

当前宠物示例仍使用内存 Mapper；MyBatis 模块尚未替换它。OSS 和认证仍需要真实服务、账号配置与业务登录流程。

## 各环境连接配置

三个 Boot 工程使用 `${变量名:默认值}` 从各自的 `.env` 读取配置。将目标工程的 `.env.example` 复制为同目录 `.env`，填写端口和需要的连接参数。`.env` 不进入 Git，也不会被打包进 JAR。`bootRun` 固定使用对应工程目录作为工作目录；部署时应从该服务的部署目录启动 JAR。系统环境变量的优先级高于 `.env`，可用于临时覆盖。文件采用 Java properties 兼容的 `KEY=value` 语法。完整步骤见 [启动说明](run.md)。

```bash
cp -n pettown-boot/app/.env pettown-boot/app/.env
./gradlew :pettown-boot:app:bootRun
```

启用数据库时，在对应 `.env` 中设置 `PETTOWN_AUTOCONFIG_EXCLUDES=` 并填写有效连接；启用 Redisson 时设置 `PETTOWN_REDIS_ENABLED=true`。旧的 `pettown-boot/config/connection-secrets.yml` 不再被读取，迁移时请将其中的连接信息填入相应服务的 `.env`。宠物示例尚未向源项目现有库写入数据。
