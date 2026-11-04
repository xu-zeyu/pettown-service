# PetTown AI 开发规范

本文件适用于整个仓库。AI 修改代码、配置、测试和文档时，均须遵守以下约定。

## 开始工作

- 先阅读 [架构说明](docs/architecture.md)，涉及公共能力时再阅读 [公共模块说明](docs/common-modules.md)，并核对相关源码和 Gradle 配置。
- 修改前查看 `git status` 和相关差异，保留用户已有的修改、暂存内容及删除操作，不擅自还原或覆盖。
- 先确认需求属于哪个客户端、哪个业务包、哪些启动工程，再确定修改范围。复用现有实现，完成当前需求所需的最小完整改动。
- 文档中的功能描述须与当前代码核对；发现过时说明时明确指出，不把文档提及但仓库不存在的文件或能力当作已实现。
- 不为本次任务附带进行全仓格式化、批量改名、依赖升级或架构重构。行为或配置发生变化时，同步维护直接相关的文档。

## 架构边界

- 项目采用 Gradle 多模块的模块化单体，使用传统 Controller / Service / Mapper 分层；不引入微服务、DDD、六边形架构或额外的领域抽象层。
- 必须进行模块化开发，保持低耦合与单一职责。修改前明确模块归属、对外接口及依赖方向，优先复用所属模块的既有能力；业务调用禁止跨模块直接访问实现类、Mapper 或客户端内部 DTO/VO，跨业务协作通过 Service 接口，公共能力通过公开 API 复用。
- 目录职责必须清晰，包名与实际职责一致：`config` 负责配置与组件注册，`interceptor` 负责请求拦截，`annotation` 负责注解定义；Controller、业务编排、持久化和通用基础能力各归其层，不将多种职责堆入配置类、工具类或启动类。
- Java 源码使用 Java 17，Group 和基础包名前缀统一为 `com.pettown`。构建脚本使用 Gradle Kotlin DSL。
- App、小程序、管理后台分别提供接口和启动入口，共享业务集中在 `module-business`。按业务划分 Java 包，不为每个业务新建 Gradle 模块。

| 位置 | Gradle 路径 | 代码归属 |
| --- | --- | --- |
| `pettown-modules/module-business` | `:module-business` | `com.pettown.business.<业务>`；实体、Mapper、Service 接口和实现。现有业务包括 `pet`、`user`、`verification`、`admin`。 |
| `pettown-modules/module-app` | `:module-app` | `com.pettown.app.<业务>`；App 端 Controller、请求 DTO、响应 VO。 |
| `pettown-modules/module-miniapp` | `:module-miniapp` | `com.pettown.miniapp.<业务>`；小程序端 Controller、请求 DTO、响应 VO。 |
| `pettown-modules/module-admin` | `:module-admin` | `com.pettown.admin.<业务>`；管理后台认证、管理员、角色、权限点及菜单接口。 |
| `pettown-common/common-*` | `:common-*` | `com.pettown.common` 下的公共基础能力。 |
| `pettown-boot/app`、`miniapp`、`admin` | `:pettown-boot:app`、`:pettown-boot:miniapp`、`:pettown-boot:admin` | 各客户端的启动类、运行配置、装配配置和接口集成测试。 |
| `pettown-boot/src/main/java` | `:pettown-boot` | 启动工程共用的辅助代码，如 `EnvFileSupport`。 |

允许的主要依赖方向：

```text
Boot 工程 → 对应客户端模块 → module-business → common 模块
Boot 工程、客户端模块 → 按需使用的 common 模块
```

- `common` 模块不得依赖业务模块、客户端模块或 Boot；公共模块之间按实际需要声明单向依赖。
- `module-business` 不得依赖客户端模块或 Boot；客户端模块之间不得相互依赖。
- Boot 负责最终装配，其他层不得依赖 Boot。所有模块禁止循环依赖。
- `pettown-modules` 是业务模块，生产代码只承载业务与客户端接口，不放置 Spring 装配配置类、拦截器注册或运行环境配置。各端的 `@Configuration`、`@Bean` 装配、专属鉴权拦截器及 `application*.yml` / `.env.example` 统一放入对应 Boot 工程；可复用的基础组件配置放入所属 `common` 模块。模块自身的 Gradle 脚本、Mapper 资源及隔离测试配置仍放在所属模块。
- 只在有实际代码或资源时创建目录，不预建空的包、模块或占位实现。

## 分层与编码

- 客户端按 `controller`、`dto`、`vo` 组织代码；业务模块按 `entity`、`mapper`、`service`、`service/impl` 组织代码。
- Controller 负责请求绑定、参数校验、登录上下文、调用 Service 和响应转换。新增或修改接口时通过 Service 接口访问业务，不直接调用 Mapper，也不在 Controller 中编写持久化或核心业务逻辑。
- Service 接口与实现分离，实现类放在 `service/impl` 并以 `Impl` 结尾。业务规则和数据写入编排放在 Service；需要原子完成的多步数据库写入在 Service 层明确事务边界。
- 共享 Service 不接收客户端专属 DTO，不返回客户端 VO、`ResponseEntity` 等 HTTP 类型；跨客户端复用通过共享 Service 接口完成。
- 请求和响应优先沿用现有 `XxxRequest`、`XxxResponse` 命名及 Java `record` 写法；实体沿用 MyBatis-Plus 注解和现有 Lombok 风格。响应通过显式转换生成，不直接暴露持久化实体。
- 新增依赖注入优先使用构造器；保持改动处的代码风格，不借机整理无关存量代码。
- 使用 `jakarta.validation` 注解及 `@Valid` 校验请求，业务约束在 Service 层保证。接口说明遵守下述 Apifox / OpenAPI 规范。

## 公共能力复用

| 模块 | 应复用的能力 |
| --- | --- |
| `common-core` | `Result<T>`、`BusinessException`、`CommonErrorCode` / `ErrorCodeEnum`、`Cache` 接口。 |
| `common-web` | Web 与参数校验依赖、`advice/GlobalExceptionHandler` 全局异常处理。 |
| `common-mybatis` | `BaseMapperX`、分页与排序参数、`PageResult`、`BaseEntity` 和时间自动填充。 |
| `common-redis` | Redisson 配置、实现 `Cache` 的 `CacheUtils`；`RedisCache` 是已有字符串 API 的兼容入口。 |
| `common-security` | Sa-Token starter、`PetTownToken.USER` / `ADMIN` 账号空间、Servlet 上下文、分发拦截器及 401/403 异常处理。 |
| `common-file` | `FileService` 和阿里云 OSS 实现，按配置启用。 |
| `common-log` | `@Log` 操作日志与 `X-Trace-Id` 请求追踪。 |

- 通用基础能力放入对应 `common` 模块，宠物、用户、验证码等业务规则留在 `module-business`，不因多个客户端使用就移入公共模块。
- HTTP 响应统一使用 `Result<T>`，保持 `code`、`msg`、`data` 字段和字符串错误码约定；需要设置 HTTP 状态或响应头时可使用 `ResponseEntity<Result<T>>`。
- 预期业务失败抛出 `BusinessException`，复用错误码体系，由全局处理器转换响应；不在每个 Controller 中重复捕获并包装异常，不吞掉数据库或基础设施异常。
- 分页复用 `PageParam` / `SortablePageParam` 与 `PageResult`，保留 `size=-1` 查询全部的既有语义，不再创建平行的分页、结果、异常或缓存工具体系。

## 数据、认证与缓存

- Mapper 继承 `common-mybatis` 的 `BaseMapperX<T>`，查询优先复用现有方法和 Lambda 条件构造器。业务代码禁止使用 `JdbcTemplate` 或手写 JDBC。
- 实体映射与现有表结构、字段及主键策略保持一致：宠物映射 `pet`，使用自增 `Long` ID；用户映射 `user_account`，使用 Service 生成的字符串 ID 和 `IdType.INPUT`。不擅自统一主键类型或给已有表强加字段。
- 只有表包含对应时间字段时才继承 `BaseEntity`，时间填充复用 `MyMetaObjectHandler`。表结构变更须提供相应 SQL，并同步受影响的 `src/test/resources/schema.sql`。
- 密码使用 BCrypt 哈希保存和校验；密码、密码哈希和验证码不得进入响应或日志。Token 仅通过明确的认证接口响应发放，不记录到日志；认证复用 `PetTownToken`，保持用户与管理员账号空间隔离。
- App、小程序接口默认校验 `PetTownToken.USER`，Admin 默认校验 `PetTownToken.ADMIN`；App 宠物接口同样要求用户登录。三端只从 `Authorization: Bearer <token>` 请求头读取 Token，不接收 Cookie 或请求参数 Token；生产会话使用 RedisTemplate，App 与小程序共享用户会话时须连接相同 Redis。
- 公开业务接口在具体方法上显式添加 `@SaIgnore`，并同步 OpenAPI 的公开语义；不得按方法名猜测是否放行，也不得整体放行 `/public/**` 或 `/auth/**`。框架路径仅按实际需要排除 `/error`、`/v3/api-docs` 及其子路径，新增接口默认受保护。
- 通用 Sa-Token 上下文和 ASYNC/ERROR 内部分发处理放在 `common-security`，各端的路由规则与账号空间由本端配置装配。管理员状态、会话版本及权限规则留在 Admin。首次请求必须鉴权，异步或错误内部分发不重复鉴权。需要具体权限的 Admin 接口使用原生 `@SaCheckPermission`，显式指定 `type = "admin"`。
- 普通缓存需求在业务层注入 `common-core` 的 `Cache` 接口；确需 Redis Map 等专有能力时再使用 `CacheUtils`。运行时缓存使用 Redis，禁用或不可用时不得静默回退到内存实现。
- 验证码保留现有契约：六位数字、五分钟 TTL、按用途和手机号隔离；消费通过 `Cache.compareAndDelete` 原子完成，禁止先读取再删除。生成接口不向客户端返回验证码。

## 构建、配置与装配

- 使用仓库的 `./gradlew`，不依赖系统安装的 Gradle。插件与外部依赖版本集中维护在 `gradle/libs.versions.toml`；Group、Java 编译和通用测试配置由根 `build.gradle.kts` 管理。
- 子模块仅按需声明依赖，不重复指定已由 Version Catalog 或 Spring Boot BOM 管理的版本。区分 `api` 与 `implementation`：只有对外签名需要暴露的依赖才使用 `api`。
- 新增 Gradle 模块时同步 `settings.gradle.kts`；已有公共模块和业务包能够承载需求时直接复用。
- 三个 Boot 工程只装配本端接口、实际使用的业务包及公共组件。新增业务或 Mapper 后，检查对应启动类的组件扫描和 `@MapperScan`，不直接扩大为扫描整个 `com.pettown`。
- 各客户端配置位于自己的 `src/main/resources/application.yml`，本地连接参数由该 Boot 工程目录下的 `.env` 提供，示例为同目录 `.env.example`。保留 `EnvFileSupport`、`PETTOWN_ENV_FILE` 和环境变量覆盖方式。
- 新增配置使用环境变量占位符，并更新对应 `.env.example`；不把真实连接串、密码、AccessKey 等凭据写入代码、文档、测试或示例，不提交 `.env` 和旧的 `config/connection-secrets.yml`。
- 修改 Redis、OSS、用户业务等可选组件时保留现有启用条件，并检查 Bean 依赖；不能靠硬编码凭据、关闭校验或移除组件让应用或测试通过。

## Apifox / OpenAPI 规范

- Apifox 通过 Springdoc 生成的 OpenAPI 3 文档导入或同步，统一入口为 `/v3/api-docs`。默认 App 为 `http://localhost:8080/v3/api-docs`，Admin 为 `http://localhost:8081/v3/api-docs`，小程序为 `http://localhost:8082/v3/api-docs`；实际端口以各端配置为准。不维护与 Controller、DTO、VO 脱节的手工接口定义。
- 每个已接入的 Boot 工程在 `com.pettown.boot.<客户端>.ApifoxConfig` 中提供本端唯一的 `OpenAPI` Bean，命名为 `<客户端>OpenApi`。三端保持同样的配置结构：本端中文标题、版本（当前为 `0.0.1`）、说明及认证方案；不将本端文档配置放入业务模块，不在文档中硬编码部署域名、真实连接信息或凭据。
- Springdoc starter 仅由对应 Boot 工程装配，版本与依赖坐标复用 Version Catalog；客户端模块使用 `libs.swagger.annotations.jakarta` 编写注解。不得为文档接入升级依赖、引入另一套 Swagger 生成器或扩大业务扫描范围。
- 在各端 `application.yml` 中通过 `springdoc.packages-to-scan` 和 `springdoc.paths-to-match` 限定本端接口：App 为 `com.pettown.app`、既有 `/api/pets/**`，Admin 为 `com.pettown.admin`、`/api/admin/**`，miniapp 为 `com.pettown.miniapp`、`/api/miniapp/**`。新增客户端按相同约定配置，不将其他客户端、测试或内部辅助接口纳入文档。
- 新增或修改公开 HTTP 接口时，Controller 使用中文 `@Tag` 分组，每个接口使用 `@Operation(summary = "...")` 描述操作。必要的登录条件、权限编码、状态变更、整组替换/清空语义与会话失效行为写入 `description`；不能仅靠方法名或 `@Log` 充当接口说明。
- 三端统一声明名为 `bearerAuth` 的 HTTP Bearer 认证方案，对应 `Authorization: Bearer <token>`。Sa-Token 是不透明 Token，不标记为 JWT；管理员与用户 Token 仍使用各自账号空间。仅在实际需要登录的 Controller 或方法上添加 `@SecurityRequirement(name = "bearerAuth")`，登录和验证码等公开接口不得误标认证，也不通过全局默认认证误伤公开接口。
- OpenAPI 安全声明只负责文档，不能替代 `PetTownToken` 登录检查、权限注解或拦截器；也不能为了导入文档放宽业务接口鉴权。文档必须反映实际认证和授权行为。
- 请求与响应字段含义不直观、包含枚举、状态、列表替换或时间单位时，用 `@Schema` 说明语义，并与实际校验、枚举及响应字段保持一致。密码请求字段标记 `format = "password"`、`accessMode = WRITE_ONLY`；敏感字段示例只使用明确的占位符，不填入真实密码、验证码或 Token，不暴露实体、密码哈希及会话内部字段。
- 三端配置 `springdoc.default-flat-param-object=true`，将查询参数对象展开为独立 query 参数。分页文档沿用 `page`、`size`、`list`、`total`，明确 `size=-1` 的既有语义。参数文档不应暴露 `sizeValid` 等校验辅助属性，不为调整文档改变真实请求绑定或校验行为。
- 修改文档配置或注解后，在对应 Boot 工程验证实际 `/v3/api-docs` 输出，检查标题、版本、本端路径、操作分组、认证方案及关键字段；仅编译通过不能视为 Apifox 导入契约已验证。测试使用隔离的 H2、Mock 或测试存储，不连接真实业务服务；交付时区分 OpenAPI 输出验证和实际 Apifox 客户端导入验证。

## 验证与交付

- 根据改动范围运行相关测试；共享能力改动同时考虑受影响的客户端。测试放在所属模块的 `src/test`，HTTP 接口与最终装配测试放在对应 Boot 工程。
- 数据库集成测试沿用 H2 的 MySQL 模式与独立 schema；缓存等外部依赖按测试目标使用 Mock 或隔离测试服务，不连接真实业务环境。覆盖实际行为、异常分支及本次修复的回归场景。
- Gradle 任务使用 `settings.gradle.kts` 中的项目路径，不能把磁盘目录直接当成任务路径。常用命令如下，按实际改动选择执行：

```bash
# 业务层测试
./gradlew :module-business:test

# 公共模块测试示例
./gradlew :common-mybatis:test :common-redis:test

# 客户端编译与对应 Boot 测试示例（小程序）
./gradlew :module-miniapp:compileJava :pettown-boot:miniapp:test

# App 接口与装配测试
./gradlew :pettown-boot:app:test

# 完整构建；scripts/build.sh 也是此命令的封装
./gradlew build
```

- 纯文档修改检查内容、引用路径与 `git diff --check` 即可，无需启动服务或运行全量构建。不存在测试源码的模块，`test` 任务不能视为已有测试覆盖。
- 检查最终差异，确保仅包含任务相关文件。交付时说明改动、验证结果及尚未验证的事项；未运行或未通过的检查必须如实说明，不声称成功。
