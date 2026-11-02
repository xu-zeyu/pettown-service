# PetTown 工程结构

## 架构与 Gradle 父子工程

本项目采用模块化单体、传统 Controller / Service / Mapper 三层架构。根工程 `pettown-server` 统一管理 Gradle 插件版本、依赖版本、Group、Java 17 编译配置与仓库。所有子模块的 Group 均为 `com.pettown`，Java 基础包名前缀均为 `com.pettown`。不采用微服务、DDD 或六边形架构。

| 目录或文件 | 职责 |
| --- | --- |
| `AGENTS.md` | 仓库内的简要工程约定。 |
| `build.gradle.kts` | 父工程的插件、Group、仓库和编译配置。 |
| `settings.gradle.kts` | 注册所有 Gradle 子模块。 |
| `gradle.properties` | Gradle 构建参数。 |
| `gradle/libs.versions.toml` | 集中管理插件与外部依赖版本。 |
| `gradle/wrapper`、`gradlew`、`gradlew.bat` | 固定并运行 Gradle Wrapper。 |
| `pettown-common` | 各客户端项目复用的公共基础能力。 |
| `pettown-modules` | 客户端项目模块与共享核心业务模块。 |
| `pettown-boot` | App、Admin、小程序各自的 Spring Boot 启动、运行配置和项目模块装配。 |
| `docs` | 项目文档。 |
| `scripts` | 构建和部署脚本；目前仅有构建脚本。 |

## 公共模块

以下模块均位于 `pettown-common`，内部包以 `com.pettown.common` 开头。目录随对应实现创建。

| 模块 | 职责与内部目录 |
| --- | --- |
| `common-core` | 公共核心；`cache/Cache` 缓存接口、`constant` 公共常量、`enums` 公共枚举、`exception` 公共异常、`result` 统一结果类型、`util` 通用工具、`validation` 通用校验。 |
| `common-web` | Web 公共组件；`advice` 全局处理、`config` Web 配置、`filter` 过滤器、`interceptor` 拦截器、`response` 响应处理。 |
| `common-mybatis` | MyBatis-Plus 公共组件；根包分页与排序参数、`config` 分页插件与自动填充、`mapper/BaseMapperX` 通用 CRUD 和 Join、`model/BaseEntity` 时间字段、`utils/MyBatisUtils` 查询工具。 |
| `common-redis` | Redis 公共组件；`CacheUtils` 为 Redisson 缓存实现、`config` 配置与 JSON 编解码、`util/RedisCache` 为原字符串缓存 API 的兼容入口。 |
| `common-security` | 安全认证公共组件；`config` 配置、`annotation` 注解、`filter` 安全过滤器、`handler` 安全处理。 |
| `common-file` | 文件管理公共组件；`config` 配置、`service` 文件服务、`properties` 配置属性。 |
| `common-log` | 日志公共组件；`annotation` 操作注解、`aspect` 操作日志、`filter` 请求追踪。 |

## 项目模块与核心业务模块

`pettown-modules` 按客户端项目组织接口，并设置一个共享的核心业务模块。业务包按宠物、用户等业务领域划分，不为每个领域单独创建 Gradle 模块。App 和小程序需要同一套业务逻辑时，分别依赖 `module-business` 的 Service 接口；客户端专属的请求、响应和接口逻辑留在各自模块。

| 模块 | 基础包 | 职责 |
| --- | --- | --- |
| `module-business` | `com.pettown.business` | 共享核心业务；现有宠物实体、Service 和 Mapper 位于 `pet` 业务包。 |
| `module-app` | `com.pettown.app` | App 端 Controller、请求 DTO、响应 VO；现有宠物接口位于 `pet` 业务包。 |
| `module-miniapp` | `com.pettown.miniapp` | 小程序端接口；账号登录即注册依赖共享用户业务，验证码生成和消费依赖共享 `verification` 业务。 |
| `module-admin` | `com.pettown.admin` | 管理后台接口；目前仅保留模块入口。 |

现有宠物业务的实际目录：

| 路径（相对模块根目录） | 职责 |
| --- | --- |
| `module-business/src/main/java/com/pettown/business/pet/entity` | 宠物实体，不包含客户端响应格式。 |
| `module-business/src/main/java/com/pettown/business/pet/service` | 宠物业务接口。 |
| `module-business/src/main/java/com/pettown/business/pet/service/impl` | 业务实现，调用 Mapper。 |
| `module-business/src/main/java/com/pettown/business/pet/mapper` | 继承 `BaseMapperX` 的 MyBatis-Plus 数据访问接口。 |
| `module-app/src/main/java/com/pettown/app/pet/controller` | App 请求入口，调用共享 Service。 |
| `module-app/src/main/java/com/pettown/app/pet/dto` | App 请求参数。 |
| `module-app/src/main/java/com/pettown/app/pet/vo` | App 响应对象，负责将业务实体映射为接口响应。 |

只有实际代码或资源存在时才创建对应目录。新业务若供多个客户端复用，其业务逻辑进入 `module-business` 下的独立业务包；客户端差异保留在各项目模块。`pettown-common` 只承载通用基础能力，不承载宠物等具体业务逻辑。

## Boot 模块

`pettown-boot/app`、`pettown-boot/admin`、`pettown-boot/miniapp` 是三个独立可运行的 Gradle 子工程。每个工程只依赖自己的客户端模块，启动类扫描自己的客户端包、实际使用的共享业务包和公共包；每个工程各有一份 `application.yml`、一个 `.env.example` 和日志配置。App 和小程序工程包含基于独立 H2 数据库的接口装配测试。App 只扫描宠物 Mapper，小程序只扫描用户 Mapper，避免装配其他客户端未使用的业务。运行与部署方式见 [启动说明](run.md)。

## 依赖方向

- 父工程统一配置，不承载业务代码。
- `pettown-common` 只提供基础能力，不依赖业务模块或 Boot 模块。
- `module-business` 按需依赖公共模块，不依赖 App、小程序或管理后台模块。宠物与用户业务统一通过 `common-mybatis` 的 `BaseMapperX` 持久化，禁止在业务代码中使用 `JdbcTemplate` 或手写 JDBC；用户密码使用 BCrypt 保存哈希。
- 客户端项目模块按需依赖 `module-business` 与公共模块。`module-app` 使用共享宠物业务、`common-core`、`common-web` 和 `common-log`；`module-miniapp` 使用共享用户业务、`common-web` 和 `common-security`；`module-admin` 暂无业务实现。客户端模块之间没有 Gradle 依赖。
- 三个 Boot 子工程分别依赖自己的客户端模块及 `common-mybatis`、`common-redis`、`common-log`，负责最终装配；共享业务模块经客户端模块进入运行时。数据库和 Redis 的连接参数由各自 `.env` 提供，启用数据库自动配置与 Redisson 时须有可用服务。其他模块不得依赖 Boot。
- 所有依赖保持单向，禁止循环依赖。
