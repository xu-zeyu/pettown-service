# PetTown 工程约定

- 本仓库是 Gradle 多模块的模块化单体，Group 和基础包名前缀统一为 `com.pettown`。
- `pettown-modules` 包含 App、小程序、管理后台项目模块和供它们复用的 `module-business` 核心业务模块；各模块内部再按业务组织包。
- 客户端模块负责 Controller、请求 DTO 和响应 VO；共享业务的 Service、Mapper、实体放在 `module-business`，Service 接口与实现分别放在 `service`、`service/impl`。
- 基础公共能力放入对应的 `pettown-common` 子模块。客户端模块按需依赖核心业务模块与公共模块；核心业务模块不得依赖客户端模块，禁止循环依赖。
- `pettown-boot` 只放启动类、运行配置和模块装配。
- 插件版本、依赖版本、Java 编译配置由父工程统一管理。
- 只在有实际代码或资源时创建相应子目录；具体职责和依赖方向见 `docs/architecture.md`。
