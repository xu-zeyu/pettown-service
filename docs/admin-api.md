# Admin 登录与权限接口

Admin 对齐 `jinHan-shop-admin-web` 的管理员认证、个人资料、角色、权限目录与批量同步功能，代码遵守本仓库 Controller / Service / Mapper 分层：

- `module-admin/com.pettown.admin.auth`：登录入口、登录 DTO、管理员鉴权配置；`system/controller`、`system/dto`、`system/vo`：账号、角色、权限接口及显式响应转换。
- `module-business/com.pettown.business.admin`：实体、Mapper、Service 接口与实现；不依赖客户端 DTO 或 HTTP 类型。
- `pettown-boot/admin`：启动、有限范围扫描、环境变量、初始管理员及 OpenAPI 装配；额外扫描验证码业务，不扫描其他客户端业务。

原有 `auth` 和 `system` 接口保留。**原有 `POST /api/admin/auth/login` 现在也必须提交 `smsCode`**，不能绕过参考项目要求的密码加验证码认证。原有初始账号改密、账号停用与会话版本控制继续有效；账号改为通过 `admin_account.role_id` 关联单个角色。原有账号创建/资料修改接口支持可选 `phone`，修改手机号使旧会话失效。

## 接口映射

参考项目的业务路径统一置于 `/api/admin` 前缀下；参考 `/admin` 对应本项目 `/api/admin`，避免重复的 `/admin/admin`。所有响应使用 `Result<T>`，错误码为字符串。

| 参考路径 | 本项目路径 | 请求 / 响应 |
| --- | --- | --- |
| `POST /login/sms` | `POST /api/admin/login/sms` | `{username,password,smsCode}` → `data` 为 Token 字符串 |
| `POST /public/captcha/login/{name}` | `POST /api/admin/public/captcha/login/{name}` | 按用户名生成验证码，`data=null` |
| `GET /admin/self` | `GET /api/admin/self` | `{avatar,userName,authorities}` |
| `GET /admin/info` | `GET /api/admin/info` | 资料、创建/更新时间、最近登录时间、`role` |
| `POST /admin/create` | `POST /api/admin/create` | `{username,password,phone,realName,roleId}`；后面三项可选 |
| `POST /admin/role/create` | `POST /api/admin/role/create` | `{rname,description}` |
| `POST /admin/role/update` | `POST /api/admin/role/update` | `{id,rname,description}`；非 null 字段局部更新 |
| `POST /admin/role/delete` | `POST /api/admin/role/delete` | `{id}`；逻辑删除，清理授权与账号绑定 |
| `GET /admin/role/{id}` | `GET /api/admin/role/{id}` | `{id,rname,description,createdTime,updatedTime,deleted}` |
| `GET /admin/role/page`、`list` | `GET /api/admin/role/page`、`list` | 分页对象 / 全部角色数组 |
| `GET /admin/role/{id}/permissions` | `GET /api/admin/role/{id}/permissions` | 权限 `{id,name,code}` 数组 |
| `POST /admin/role/assignPermissions` | `POST /api/admin/role/assignPermissions` | `{roleId,permissionIds}` |
| `POST /admin/permission/create` | `POST /api/admin/permission/create` | `{name,code}` |
| `POST /admin/permission/update` | `POST /api/admin/permission/update` | `{id,name,code}`；非 null 字段局部更新 |
| `DELETE /admin/permission/{id}` | `DELETE /api/admin/permission/{id}` | 删除自定义权限并清理角色绑定 |
| `GET /admin/permission/{id}` | `GET /api/admin/permission/{id}` | `{id,name,code}` |
| `GET /admin/permission/page`、`list` | `GET /api/admin/permission/page`、`list` | 分页对象 / 全部权限数组 |
| `POST /admin/permission/batchSync` | `POST /api/admin/permission/batchSync` | `{permissions:[{name,code}]}` |

`page`、`size` 使用本项目 `PageParam`，分页响应为 `data.list`、`data.total`；`size=-1` 查询全部，其他合法 size 为 1–1000。不会输出 MyBatis 的 `IPage` 内部结构。

## 认证与授权

只有两个登录入口及验证码生成入口公开。`self`、`info` 需要管理员登录。新增管理员、角色、权限维护及查询接口均要求 `SUB_ADMIN`；参考代码对部分管理操作只检查登录，本项目保留明确的管理权限检查。原有 `/system` 接口仍按各自 `system:*` 权限检查。

Token 是 Sa-Token 不透明 Token，使用 `PetTownToken.ADMIN`，与用户账号空间隔离，通过 `Authorization: Bearer <token>` 传递。生产会话始终使用 RedisTemplate，默认有效期 7200 秒；不接收 Cookie 或请求体 Token，不记录密码、验证码和 Token。

每次受保护请求重新读取账号状态和权限。禁用、重置密码、本人改密使旧 Token 失效；重新启用不会恢复旧 Token。退出只注销当前 Token。初始账号和旧账号管理接口创建/重置的账号须先通过 `/api/admin/auth/password` 修改密码；此前只能访问个人信息、改密和退出接口。`/api/admin/create` 按参考行为创建可直接使用的账号，直接在 `admin_account.role_id` 保存关联角色；一个账号最多一个角色。`info.role` 返回该账号关联的已启用角色，无角色或角色已禁用/删除时为 null，`self.authorities` 返回该角色的有效权限。

内置权限由 `AdminPermission` 枚举与 SQL 种子维护，包括 `system:*` 与 `SUB_ADMIN`；前端可通过数据库目录补充自定义编码。编码唯一，不支持通配符；内置编码不可改名或删除。批量同步只新增缺失编码，已有名称不覆盖，不删除旧项，同批重复编码只创建一次。角色权限按 ID 整组替换，空数组清空，重复 ID 去重。修改自定义编码时同步迁移关系，删除时清理关系。授权写操作在事务中串行校验，不允许授予超出自身范围的权限或通过角色修改提升自身权限。

菜单仍通过 `/api/admin/auth/menus` 根据系统读权限生成；参考项目未实现动态菜单树接口。自定义前端菜单和按钮可以使用 `self.authorities`，菜单显示不能代替后端授权。


## 四表结构与账号生命周期

- `admin_account` 保存账号资料、密码哈希及可空的 `role_id`；一个账号可关联一个角色，一个角色可供多个账号使用。账号对角色使用逻辑关联与索引，不引入阻止账号删除的反向外键。
- `admin_role` 保存角色；`admin_permission` 保存权限目录；`admin_role_permission` 保存角色与权限的关系。
- 不再创建或访问 `admin_account_role`，对应实体和 Mapper 已移除。
- `PUT /api/admin/system/accounts/{id}/role` 接收 `{roleId}`；null 或不传解除关联，替换立即影响后续请求。`GET` 同路径查询关联 ID。旧 `/{id}/roles` 数组接口标记废弃，最多接受一项，多个角色明确拒绝；空数组解除关联。
- `DELETE /api/admin/system/accounts/{id}` 需要 `system:account:delete`，删除账号并注销全部会话；保留共享角色、角色权限及使用该角色的其他账号。不能删除自身或超级管理员，不能删除权限超出自身范围的账号。账号即使被直接从数据库移除，其旧 Token 也会返回 401。
- 两套角色删除接口统一在 Service 事务中清空关联账号的 `role_id`，删除角色权限关系并逻辑删除角色；账号、密码及个人资料保留。角色撤权后，账号仍可登录查看个人信息，但不再拥有该角色的业务权限。

旧版五表部署也可以执行同一个单角色迁移脚本：执行前备份并暂停 Admin 写入，脚本在修改结构前检查多角色、孤立关联和新旧 role_id 冲突，发现问题会中止并保留旧表。校验通过后拷贝单角色关联，再移除旧关联表及其中的反向外键；不自动选择最小角色，也不删除账号。MySQL DDL 存在隐式提交，迁移不声称可通过一个事务整体回滚。

## 验证码与短信投递边界

验证码复用 `VerificationCodeService`：六位数字、五分钟 TTL，使用独立用途 `admin_login` 和手机号组成 Redis Key，与小程序登录/重置密码隔离。密码校验通过后使用 `Cache.compareAndDelete` 原子消费，错误验证码不删除正确值，成功后不可重放。账号须启用且有有效手机号。

参考项目把验证码直接返回给客户端，而且没有实际短信发送。本仓库规范禁止返回验证码，因此生成接口只缓存并返回成功，**目前尚未接入短信投递服务，不能据此声称已可向真实手机号发送短信**。上线短信登录前须接入发送服务，在服务端生成后投递；不得通过响应、日志或内存回退提供验证码。无手机号的账号可保留，但无法执行短信登录。

## 部署与迁移

全新数据库执行 [`sql/admin.sql`](sql/admin.sql)，只创建 `admin_account`、`admin_role`、`admin_permission`、`admin_role_permission` 四表。已有这四张表时，执行 [`sql/admin-single-role.sql`](sql/admin-single-role.sql)，自动补充缺失的 `role_id` 字段、查询索引与删除账号权限。若是缺少手机号、头像等字段的最早版本，先执行一次 [`sql/admin-reference-alignment.sql`](sql/admin-reference-alignment.sql)，再执行单角色迁移。迁移不会推断已丢失的角色绑定，若旧关联表已被手动删除且账号没有 role_id，须通过设置角色接口重新关联。脚本不由应用自动执行；测试 schema 与全新部署脚本保持一致。旧账号须通过 `/system/accounts/{id}` 资料修改接口补充正确手机号，超级管理员手机号由运维补充；不会读取参考工程数据库或导入其账号。

复制 `pettown-boot/admin/.env.example` 为同目录 `.env`，配置本服务 MySQL、Redis。管理验证码需要 Redisson，`PETTOWN_REDIS_ENABLED` 默认 `true`；关闭后验证码调用明确失败，不回退到内存。RedisTemplate 会话不受该开关影响。

首次初始化设置 `PETTOWN_ADMIN_BOOTSTRAP_ENABLED=true`，填写 `PETTOWN_ADMIN_BOOTSTRAP_USERNAME`、`PETTOWN_ADMIN_BOOTSTRAP_PASSWORD` 和 `PETTOWN_ADMIN_BOOTSTRAP_PHONE`。初始化只在 ID=1 账号不存在时创建，不覆盖现有账号。初始化后关闭开关并移除初始密码。初始账号首次登录后需改密并重新登录。

```bash
./gradlew :pettown-boot:admin:bootRun
```

## OpenAPI 与验证

通过 `http://localhost:8081/v3/api-docs` 获取 OpenAPI 3，实际端口以配置为准。文档只包含 `/api/admin/**`，Bearer 方案不标记 JWT；验证码和登录接口不标记认证，受保护接口明确标记。

```bash
./gradlew :module-business:test :pettown-boot:admin:test
```

HTTP 集成测试使用 H2 MySQL 模式、Mock 缓存和仅测试使用的 Sa-Token 内存 DAO，覆盖四表结构、单角色替换/清空、独立删除账号/角色、验证码隔离/一次性消费、动态权限、授权替换/清空、删除/改名生效、账号会话撤销及真实 `/v3/api-docs` 输出。未调用真实 MySQL、Redis、短信服务，也未进行 Apifox 客户端导入验证。

单角色迁移的前置检查与数据复制 SQL 使用 H2 验证；MySQL 存储过程、DDL 和真实业务数据库尚未执行验证。
