# 分别启动 App、Admin 和小程序

三个服务分别构建、运行，默认端口为 App `8080`、Admin `8081`、小程序 `8082`。Admin 目前还没有 Controller；小程序账号接口见 [登录注册说明](miniapp-auth.md)。

| 服务 | `.env` 路径 | 本地启动 | 构建 JAR |
| --- | --- | --- | --- |
| App | `pettown-boot/app/.env` | `./gradlew :pettown-boot:app:bootRun` | `./gradlew :pettown-boot:app:bootJar` |
| Admin | `pettown-boot/admin/.env` | `./gradlew :pettown-boot:admin:bootRun` | `./gradlew :pettown-boot:admin:bootJar` |
| 小程序 | `pettown-boot/miniapp/.env` | `./gradlew :pettown-boot:miniapp:bootRun` | `./gradlew :pettown-boot:miniapp:bootJar` |

在仓库根目录执行命令。第一次运行前，分别把目标工程的 `.env.example` 复制为同目录 `.env`，再填写端口、数据库和 Redis 信息。`bootRun` 的工作目录固定在对应工程，因此只读取该工程的 `.env`。`.env` 已加入 `.gitignore`，不会随代码提交。只启动某一个服务时，只需配置该服务。

在 IntelliJ IDEA 中直接运行启动类时，默认工作目录通常是仓库根目录。启动入口会在这种情况下定位到 `pettown-boot/<服务>/.env`；若显式设置 `PETTOWN_ENV_FILE`，则使用指定文件。打包后从独立部署目录运行时，默认读取该目录的 `.env`。

`application.yml` 内提供配置键与默认值，优先读取同目录 `.env` 中的 `SERVER_PORT`、`DB_*`、`REDIS_*` 等值。默认禁用 JDBC 自动配置，默认不创建 Redisson 客户端。接入数据库时，在 `.env` 写入 `PETTOWN_AUTOCONFIG_EXCLUDES=`（空值）并填写有效的 `DB_URL`、用户名和密码；需要 Redisson 时设置 `PETTOWN_REDIS_ENABLED=true` 并填写有效的 Redis 连接信息。

部署时，分别把构建产物 `pettown-boot/<服务>/build/libs/*.jar` 放到该服务的部署目录，并在 **JAR 的启动工作目录** 放置 `.env`：

```sh
cd /srv/pettown/app
java -jar app-0.0.1-SNAPSHOT.jar
```

Admin 和小程序同理，在各自目录启动各自 JAR。这样更新 JAR 时不必修改包内 `application.yml`，只需维护服务器上对应服务的 `.env`。如果通过 systemd 等工具启动，请将 `WorkingDirectory` 设为该服务目录。操作系统环境变量或命令行参数也可以覆盖 `.env` 中的值。
