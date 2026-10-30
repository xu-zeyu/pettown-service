# 公共模块接入

本次按 PetTown 的 `com.pettown.common` 包名和既有模块边界，复用 `ai-commerce` 的 MyBatis Plus 分页与时间填充、Redis/Redisson 缓存、Sa-Token 账号类型、OSS 文件服务、操作日志与 Trace ID。没有复制原项目的管理员、用户表与日志表 Mapper；这些属于业务数据，需要在对应业务模块落表后实现。

| 模块 | 已提供能力 | 接入条件 |
| --- | --- | --- |
| `common-mybatis` | MyBatis Plus 分页插件、自动填充、基础实体、分页结果 | 使用方依赖该模块，并配置数据源；分页插件按 MySQL 方言配置。 |
| `common-redis` | `RedisCache` 字符串缓存、可选 Redisson 客户端 | 使用方依赖该模块并配置 `spring.data.redis.*`；Redisson 需设置 `pettown.redis.enabled=true`。 |
| `common-security` | Sa-Token 的 `user`、`admin` 独立账号空间及未登录异常响应 | 使用方依赖该模块；业务登录接口负责校验账号后发 Token。当前模块只包含 Sa-Token 核心，尚未装配 HTTP Token 解析。 |
| `common-file` | `FileService` 的阿里云 OSS 上传下载实现 | 使用方依赖该模块，设置 `pettown.file.enabled=true` 及 `aliyun.oss.endpoint`、`access-key-id`、`access-key-secret`、`bucket-name`、`region`。密钥通过环境变量或部署配置传入。 |
| `common-log` | `@Log` 操作日志、`X-Trace-Id` 请求追踪、滚动文件日志 | 已由 Boot 与 `module-app` 引用；日志默认写到 `logs/pettown-server.log`。可通过 `logging.file.name` 改路径。 |

验证所有公共模块编译及启动测试：

```bash
./gradlew --offline :common-mybatis:compileJava :common-redis:compileJava \
  :common-security:compileJava :common-file:compileJava :common-log:compileJava :pettown-boot:test
```

当前宠物示例仍使用内存 Mapper；MyBatis 模块尚未替换它。Redis、OSS 和认证需要真实服务、账号配置与业务登录流程，默认启动不会连接这些外部服务。
