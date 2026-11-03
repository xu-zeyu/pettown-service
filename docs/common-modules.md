# 公共模块接入

本次按 PetTown 的 `com.pettown.common` 包名和既有模块边界，复用 `ai-commerce` 的 MyBatis Plus 分页与时间填充、Redis/Redisson 缓存、Sa-Token 账号类型、OSS 文件服务、操作日志与 Trace ID。没有复制原项目的管理员、用户表与日志表 Mapper；这些属于业务数据，需要在对应业务模块落表后实现。

| 模块 | 已提供能力 | 接入条件 |
| --- | --- | --- |
| `common-mybatis` | MyBatis-Plus 分页、排序、Join、`BaseMapperX` CRUD/批量操作、自动填充、基础实体与分页结果 | 三个 Boot 工程均已依赖；App 与小程序须配置数据库连接并先执行建表脚本。 |
| `common-redis` | `CacheUtils` Redisson 缓存、Map 操作，兼容 `RedisCache` 字符串 API | 三个 Boot 工程均已依赖该模块；通过各自 `.env` 配置 Redis。小程序默认启用 Redisson，App/Admin 按需启用。 |
| `common-security` | Sa-Token 的 `user`、`admin` 独立账号空间及 401/403 异常响应 | 公共模块提供核心能力，HTTP Token 解析由小程序和 Admin Boot 引入 starter。Admin 的 RBAC、Redis 会话及初始化见 [Admin 接口说明](admin-api.md)。 |
| `common-file` | `FileService` 的阿里云 OSS 上传下载实现 | 使用方依赖该模块，设置 `pettown.file.enabled=true` 及 `aliyun.oss.endpoint`、`access-key-id`、`access-key-secret`、`bucket-name`、`region`。密钥通过环境变量或部署配置传入。 |
| `common-log` | `@Log` 操作日志、`X-Trace-Id` 请求追踪、滚动文件日志 | 三个 Boot 工程共用日志配置；默认写到 `logs/${spring.application.name}.log`，可通过 `logging.file.name` 改路径；`test` Profile 仅输出控制台。 |

验证所有公共模块编译及启动测试：

```bash
./gradlew build
```

宠物与用户 Mapper 已统一继承 `BaseMapperX`，由 MyBatis-Plus 代理执行数据库操作。宠物数据保存至 `pet`，用户账号保存至 `user_account`。原文档引用的 `sql/persistence.sql` 当前不在仓库中，不能作为现成的初始化脚本使用。已有账号表上线来源字段前，执行一次 [`sql/user-source.sql`](sql/user-source.sql)；脚本不由应用自动执行。OSS 仍需配置真实服务与账号。

## 注册来源

`POST /api/miniapp/auth/login` 首次登录创建账号时，Controller 通过 `common-web` 的 `UserSourceResolver` 从请求 `User-Agent` 获取 `UserSourceEnum`，再传入共享用户 Service；请求体仍只有 `phone`、`password`、`verificationCode`。识别顺序为：`miniProgram` → `MINI_PROGRAM`，`HarmonyOS` / `OpenHarmony` → `HARMONY_OS`，`iPhone` / `iPad` / `iPod` → `IOS`，`Android` → `ANDROID`，其他含 `Mozilla/` 的 UA → `WEB`；大小写不敏感，缺失或无法识别时为 `UNKNOWN`。仅有 `MicroMessenger` 标识不会判为小程序。

来源依赖请求实际携带的 UA 标识；隐藏或未携带平台标识的客户端无法精确识别。来源只在首次注册时保存，后续登录（包括并发注册时读到已有账号）保留最初来源。`user_account.source` 使用 MyBatis 默认枚举映射，存储枚举名（如 `MINI_PROGRAM`，而非 `getValue()` 返回的 `miniProgram`）；历史账号由增量 SQL 填充为 `UNKNOWN`。

## 日志配置

三个 Boot 工程的 `logback-spring.xml` 统一引用 `common-log` 中的配置。默认框架日志为 `WARN`，`com.pettown` 业务日志为 `INFO`；操作成功记录保留，预期内的业务失败、参数错误及操作失败上下文降为 `DEBUG`，系统异常由全局异常处理器输出一次完整 `ERROR` 堆栈。控制台与文件均包含 Trace ID，操作日志正文不再重复记录 Trace ID。小程序的 Sa-Token 操作日志默认关闭，排查时可设置 `SA_TOKEN_IS_LOG=true`。

正常运行时，App、Admin、小程序分别写入 `logs/pettown-app.log`、`logs/pettown-admin.log`、`logs/pettown-miniapp.log`，避免在同一工作目录启动时共用一个文件。日志按天和大小滚动，单文件默认 `10MB`，归档压缩保留 `7` 天，归档总量上限 `100MB`；启动时也清理过期归档。容量限制针对每个服务的归档文件，当前活动文件另计，清理异步执行。`test` Profile 不创建文件日志。

需要调整时，使用 Spring Boot 标准配置，例如：

```yaml
logging:
  level:
    root: WARN
    com.pettown: DEBUG
  file:
    path: /var/log/pettown
    # name: /var/log/pettown/custom.log  # 指定完整文件名时优先于 path
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 7
      total-size-cap: 100MB
      clean-history-on-start: true
```

本次配置不主动删除旧的 `pettown-server.log` 和旧命名格式的归档，确认无需保留后可自行清理。

## common-mybatis 与参考模块对齐

参考 `ai-commerce-start/ai-commerce-starter-mybatis`，保留相同的类名、相对包结构及核心接口，包名前缀改为 `com.pettown.common.mybatis`：

| 类 | 能力 |
| --- | --- |
| `PageParam`、`SortablePageParam`、`SortingField` | 默认第 1 页、每页 10 条，最大 1000 条；`size=-1` 查询全部；支持驼峰字段转下划线排序。 |
| `PageResult` | `list`、`total`、空结果工厂；额外保留 `from(IPage)` 转换。 |
| `mapper.BaseMapperX` | 继承 `MPJBaseMapper`，提供分页/Join 分页、可选结果、条件查询/计数/删除及批量插入更新。 |
| `utils.MyBatisUtils` | 构建分页、插入拦截器、解析表名和构造列对象。 |
| `model.BaseEntity`、`config.MyMetaObjectHandler` | 插入填充 `createdTime`、`updatedTime`，更新刷新 `updatedTime`。 |
| `config.MybatisPlusConfig` | MySQL 分页插件，随 Spring 配置装配。 |

MyBatis-Plus 固定为 `3.5.15`，Join 固定为参考项目的 `1.5.5`。本项目使用 Spring Boot 3，因此使用官方对应的 `mybatis-plus-spring-boot3-starter`；版本集中维护于 `gradle/libs.versions.toml`，避免混入 Boot 2 starter。

相对参考实现的修正：`size=-1` 可通过参数校验并返回真实总数、保留排序；排序参数校验字段格式与升降序；Optional 查询只把缺失记录转为空值，数据库异常继续抛出；首条查询在数据库限制为 1 条；更新已有时间的实体时也刷新更新时间。原 `pagination.PageParam/PageResult` 已移至根包，与参考模块一致。

宠物实体继承 `BaseEntity`；用户实体映射账号表的 `id/phone/password_hash/source`，不强制增加时间列，保留字符串 ID 和账号数据兼容性。用户 ID 在 Service 生成，并以 `IdType.INPUT` 写入；宠物 ID 使用数据库自增。

[MyBatis-Plus 官方安装说明](https://baomidou.com/getting-started/install/)与[分页插件说明](https://baomidou.com/plugins/pagination/)分别说明 Boot 3 starter 和独立 `mybatis-plus-jsqlparser` 依赖。

## 各环境连接配置

三个 Boot 工程使用 `${变量名:默认值}` 从各自的 `.env` 读取配置。将目标工程的 `.env.example` 复制为同目录 `.env`，填写端口和需要的连接参数。`.env` 不进入 Git，也不会被打包进 JAR。`bootRun` 固定使用对应工程目录作为工作目录；部署时应从该服务的部署目录启动 JAR。系统环境变量的优先级高于 `.env`，可用于临时覆盖。文件采用 Java properties 兼容的 `KEY=value` 语法。Admin 的完整步骤见 [Admin 接口说明](admin-api.md)；原引用的 `run.md` 当前不存在。

```bash
cp -n pettown-boot/app/.env.example pettown-boot/app/.env
./gradlew :pettown-boot:app:bootRun
```

App 与小程序须在对应 `.env` 填写有效数据库连接，小程序还须配置 Redis 以支持验证码。Admin 现已接入后台账号与 RBAC Mapper，默认启用数据源，须执行 [`sql/admin.sql`](sql/admin.sql) 并配置 MySQL、Redis；管理会话固定使用 RedisTemplate，不受 Redisson 开关影响。小程序的 `PETTOWN_REDIS_ENABLED` 默认 `true`，App/Admin 默认 `false`，需要缓存时设为 `true`。旧的 `pettown-boot/config/connection-secrets.yml` 不再被读取，迁移时请将其中的连接信息填入相应服务的 `.env`。应用只连接各自配置的数据库，不自动访问参考项目数据库。

## Redis 与参考模块对齐

参考 `ai-commerce-start/ai-commerce-starter-cache`，公共缓存接口位于 `common-core` 的 `com.pettown.common.cache.Cache`，Redisson 实现为 `common-redis` 的 `com.pettown.common.redis.CacheUtils`。业务代码可注入 `Cache`，需要 Redis Map 时注入 `CacheUtils`。

| API | 行为 |
| --- | --- |
| `setCache(key, value)` | 写入不设过期时间的对象。 |
| `set(key, value, Duration)` | 写入对象并设置 TTL，以毫秒传递给 Redisson。 |
| `getCache(key)` | 返回 `Optional<Object>`，不存在时为空。 |
| `get(key, Class)` | 读取指定类型，不存在时返回 `null`，类型不匹配时抛出异常。 |
| `getOrElse(key, defaultValue)` | 不存在时返回默认值。 |
| `delete(key)` | 删除 Key，返回是否删除成功。 |
| `compareAndDelete(key, expectedValue)` | 扩展的原子匹配删除，用于验证码一次性校验；值不匹配时保留原值。 |
| `CacheUtils.getMap(key)` | 获取 Redisson `RMap`。 |

`RedissonConfig` 使用与参考模块一致的 `JsonJacksonCodec`，支持对象和字符串缓存。原 `RedisCache.put/get/delete` 保留为兼容入口，统一委托上述实现，不再单独使用 `StringRedisTemplate`。旧的明文字符串缓存与 JSON 编码不同，切换前应让相关旧 Key 失效或重建；新增验证码使用独立 Key 前缀。

验证码消费扩展通过 Redisson `RBucket.compareAndSet(expectedValue, null)` 实现，Redis 在单次脚本中完成比较和删除，避免先读取再删除导致的并发重复使用或误删新验证码。

保留现有 `pettown.redis.enabled` 开关，未启用时其他业务可正常启动；任何实际缓存调用都会明确报错，不回退到内存缓存。未复制参考公共模块中不支持 TTL 的 `DefaultCache`，以保证验证码始终使用 Redis 过期机制。
