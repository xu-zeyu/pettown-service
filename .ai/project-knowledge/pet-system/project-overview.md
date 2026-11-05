# pet-system 项目概览

扫描日期：2026-11-04。仓库根目录：`/Users/xuzeyu/Documents/company/jinHan/pettown-server`。

## 仓库事实

- Gradle 根项目名为 `pettown-server`，代码主体是 Java 后端；三个 Spring Boot 启动入口分别位于 `pettown-boot/app`、`pettown-boot/miniapp`、`pettown-boot/admin`。
- App 端提供宠物创建、详情和列表接口；小程序端提供登录、验证码和当前用户接口；管理端提供登录以及账号、角色、权限管理接口。
- 构建脚本配置 Java 17 工具链，版本目录声明 Spring Boot 3.5.16、MyBatis-Plus 3.5.15、Sa-Token 1.44.0；Gradle Wrapper 指向 8.12。
- 运行配置引用 MySQL 和 Redis；测试资源使用 H2。仓库中的 `.env.example` 仅为配置示例，真实环境凭证不纳入项目知识。

证据：`settings.gradle.kts`、`build.gradle.kts`、`gradle/libs.versions.toml`、三个 `*Application.java`、对应 Controller、各启动模块的 `application.yml` 和测试资源。

## 待确认

- 完整产品定位、目标用户、业务目标与正式验收范围。
- 远程仓库默认分支；当前本地分支和远程跟踪分支均为 `main`。
- 已配置的 WorkBuddy Skills 和实际部署方式。

本项目画像已于 2026-11-05 经用户人工核实，可长期复用。本文件记录代码可见范围，不代表产品需求、未执行的构建测试或实际部署状态已确认；项目配置状态为 `verified`。
