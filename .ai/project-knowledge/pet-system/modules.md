# 功能模块地图（待人工核实）

这里的“已实现”仅表示仓库中能找到对应源码，尚未代表构建、测试或运行已验证。

| 模块 | 源码可见状态 | 主要内容与证据 |
|---|---|---|
| `pettown-boot/app` | 已实现 | App 启动入口、配置与接口测试；`AppApplication.java` |
| `pettown-boot/miniapp` | 已实现 | 小程序启动入口、配置与接口测试；`MiniappApplication.java` |
| `pettown-boot/admin` | 已实现 | 管理端启动入口、配置与接口测试；`AdminApplication.java` |
| `module-app` | 已实现 | `/api/pets` 的创建、详情、列表；`PetController.java` |
| `module-miniapp` | 已实现 | 登录、验证码、当前用户接口；`auth`、`verification`、`user` Controller |
| `module-admin` | 已实现 | 管理员登录、账号、角色、权限、验证码接口；`auth`、`system`、`verification` Controller |
| `module-business` | 已实现 | 宠物、用户、管理员、验证码的 Service、Mapper、Entity；`src/main/java/com/pettown/business` |
| `common-core` | 已实现 | 统一结果、错误码、异常与枚举 |
| `common-web` | 已实现 | 全局异常处理和用户来源识别 |
| `common-mybatis` | 已实现 | MyBatis-Plus 配置、分页与通用 Mapper |
| `common-redis` | 已实现 | Redis 缓存和 Redisson 配置 |
| `common-security` | 已实现 | Sa-Token 配置、拦截器与密码服务 |
| `common-log` | 已实现 | 请求追踪与日志切面 |
| `common-file` | 部分实现 | OSS 文件服务源码存在；本次扫描未在三个启动模块的构建依赖中发现接入 |
| Vue 前端 | 未发现/待确认 | 当前仓库未发现前端构建清单；若在其他仓库，需要单独登记项目 |

模块清单以 `settings.gradle.kts` 为准。上表是源码扫描结果，功能完整度和运行状态仍待核实。
