# 当前架构

## 代码组织

`settings.gradle.kts` 将仓库组织为 7 个 `pettown-common` 模块、4 个 `pettown-modules` 模块和 3 个 `pettown-boot` 应用模块。三个应用分别加载对应接口模块和共用模块。`module-app`、`module-miniapp`、`module-admin` 主要放置 HTTP Controller 与请求/响应对象；`module-business` 放置业务 Service、Mapper、Entity。

## 可见调用路径

HTTP 请求进入对应启动应用的 Controller，经业务 Service 调用 MyBatis Mapper。运行配置使用 MySQL JDBC，Redis/Redisson 提供可配置的缓存能力；Sa-Token 用于登录态和访问控制。各启动应用使用 Springdoc/OpenAPI 相关依赖。测试配置使用 H2 内存数据库。

证据：各模块 `build.gradle.kts`、三个 `*Application.java`、`pettown-modules` 下的 Controller/Service/Mapper，以及 `pettown-boot/*/src/main/resources/application.yml`。

## 构建与验证入口

- 构建：`./gradlew build`（`scripts/build.sh` 也调用该命令）。
- 测试：`./gradlew test`；根构建脚本为 Test 任务配置 JUnit Platform。
- 静态检查：未发现专用 lint/静态检查插件配置。

以上命令由构建文件确定，本次扫描未执行；运行所需环境和构建结果尚未验证。
