# Admin 登录与权限接口

Admin 参考 `jinHan-shop-admin-web` 的管理员认证、个人资料、角色、权限目录与批量同步功能，并采用本仓库的 Controller / Service / Mapper 分层。

- `module-admin/com.pettown.admin.auth` 提供登录入口和登录 DTO。`system/controller`、`system/dto`、`system/vo` 提供账号、角色、权限接口及显式响应转换。
- `module-business/com.pettown.business.admin` 提供实体、Mapper、Service 接口与实现。
- `pettown-boot/admin` 负责启动、组件扫描、环境变量、初始管理员及 OpenAPI 装配。`config/SaTokenConfigure` 注册拦截器，`interceptor/AdminSessionInterceptor` 在 Sa-Token 注解校验之前校验管理员会话，`config/StpInterfaceImpl` 通过 Service 提供管理员角色和权限。

原有 `auth` 和 `system` 接口保留，但不提供 `POST /mng/system/roles`（角色创建统一走 `POST /mng/role/create`，不再接受外部角色编码）。**原有 `POST /mng/auth/login` 现在也必须提交 `smsCode`**，不能绕过参考项目要求的密码加验证码认证。原有初始账号改密、账号停用与会话版本控制继续有效；账号改为通过 `admin_account.role_id` 关联单个角色。原有账号创建/资料修改接口支持可选 `phone`，修改手机号使旧会话失效。

## 接口映射

参考项目的业务路径统一置于 `/mng` 前缀下；参考 `/admin` 对应本项目 `/mng`。所有响应使用 `Result<T>`，错误码为字符串。

| 参考路径 | 本项目路径 | 请求 / 响应 |
| --- | --- | --- |
| `POST /admin/create` | `POST /mng/create` | `{username,password,phone,realName,roleId}`；后面三项可选 |
| `POST /admin/role/create` | `POST /mng/role/create` | `{rname,description}` |
| `POST /admin/role/update` | `POST /mng/role/update` | `{id,rname,description}`；非 null 字段局部更新 |
| `POST /admin/role/delete` | `POST /mng/role/delete` | `{id}`；逻辑删除，清理授权与账号绑定 |
| `GET /admin/role/{id}` | `GET /mng/role/{id}` | `{id,rname,description,createdTime,updatedTime,deleted}` |
| `GET /admin/role/page`、`list` | `GET /mng/role/page`、`list` | 分页对象 / 全部角色数组 |
| `GET /admin/role/{id}/permissions` | `GET /mng/role/{id}/permissions` | 权限 `{id,name,code}` 数组 |
| `POST /admin/role/assignPermissions` | `POST /mng/role/assignPermissions` | `{roleId,permissionIds}` |
| `POST /admin/permission/create` | `POST /mng/permission/create` | `{name,code}` |
| `POST /admin/permission/update` | `POST /mng/permission/update` | `{id,name,code}`；非 null 字段局部更新 |
| `DELETE /admin/permission/{id}` | `DELETE /mng/permission/{id}` | 删除自定义权限并清理角色绑定 |
| `GET /admin/permission/{id}` | `GET /mng/permission/{id}` | `{id,name,code}` |
| `GET /admin/permission/page`、`list` | `GET /mng/permission/page`、`list` | 分页对象 / 全部权限数组 |
| `POST /admin/permission/batchSync` | `POST /mng/permission/batchSync` | `{permissions:[{name,code}]}` |

`page`、`size` 使用本项目 `PageParam`，分页响应为 `data.list`、`data.total`；`size=-1` 查询全部，其他合法 size 为 1–1000。不会输出 MyBatis 的 `IPage` 内部结构。

参考项目的 `POST /login/sms`、`POST /public/captcha/login/{name}`、`GET /admin/self`、`GET /admin/info` 有意不在本项目提供：登录统一走 `POST /mng/auth/login`（同样要求 `username+password+smsCode`，返回 Token 与账号信息），验证码按手机号经 `POST /mng/auth/verification` 生成，个人信息统一走 `GET /mng/auth/me`（返回 `account`、`roles`、`permissions`）。

## 认证与授权

登录入口与验证码生成入口通过方法上的 `@SaIgnore` 公开。框架路径 `/error`、`/v3/api-docs` 及其子路径由配置排除。其余接口先验证管理员会话和账号状态，再由 Sa-Token 执行 `@SaCheckPermission` 声明的权限检查，注解使用 `type = "admin"`。未声明具体权限的受保护接口按会话规则放行。创建管理员、角色管理和权限管理接口要求 `SUB_ADMIN`，`/system` 接口沿用各自的 `system:` 前缀权限编码。

`super_admin=1` 的启用账号可以通过所有接口权限检查，Sa-Token 在服务端使用 `*` 表示该权限。超级管理员的会话、首次改密和业务操作仍须通过相应校验，响应中的权限列表返回具体编码。

Token 是 Sa-Token 不透明 Token，使用 `PetTownToken.ADMIN`，与用户账号空间隔离，通过 `Authorization: Bearer <token>` 传递。生产会话始终使用 RedisTemplate，默认有效期 7200 秒；不接收 Cookie 或请求体 Token，不记录密码、验证码和 Token。

每次受保护请求重新读取账号状态和会话版本，需要权限时实时读取角色授权。禁用、重置密码、本人改密使旧 Token 失效，重新启用不会恢复旧 Token。退出只注销当前 Token。初始账号和旧账号管理接口创建或重置的账号须先通过 `/mng/auth/password` 修改密码，此前可访问个人信息、改密和退出接口。`/mng/create` 按参考行为创建可直接使用的账号，一个账号最多关联一个角色，关系保存在 `admin_account.role_id`。`GET /mng/auth/me` 的 `roles` 只返回关联的已启用角色（无角色、角色禁用或删除时为空数组），`permissions` 返回有效权限编码。

`AdminPermissionCodes` 声明内置编码，包括 `system:` 前缀编码和 `SUB_ADMIN`。权限目录支持自定义编码，编码唯一且不支持通配符，内置编码不可改名或删除。批量同步只新增缺失编码，已有名称不覆盖，不删除旧项，同批重复编码只创建一次。角色权限按 ID 整组替换，空数组清空，重复 ID 去重。修改自定义编码时同步迁移关系，删除时清理关系。普通管理员只能修改或删除自己持有的权限，改码时还须持有目标编码，避免绕过角色授权限制。授权写操作在事务中串行校验。

菜单仍通过 `/mng/auth/menus` 根据系统读权限生成；参考项目未实现动态菜单树接口。自定义前端菜单和按钮可以使用 `GET /mng/auth/me` 的 `permissions`，菜单显示不能代替后端授权。


## 四表结构与账号生命周期

- `admin_account` 保存账号资料、密码哈希及可空的 `role_id`；一个账号可关联一个角色，一个角色可供多个账号使用。账号对角色使用逻辑关联与索引，不引入阻止账号删除的反向外键。
- `admin_role` 保存角色；`admin_permission` 保存权限目录；`admin_role_permission` 保存角色与权限的关系。
- 不再创建或访问 `admin_account_role`，对应实体和 Mapper 已移除。
- `PUT /mng/system/accounts/{id}/role` 接收 `{roleId}`；null 或不传解除关联，替换立即影响后续请求。`GET` 同路径查询关联 ID。旧 `/{id}/roles` 数组接口标记废弃，最多接受一项，多个角色明确拒绝；空数组解除关联。
- `DELETE /mng/system/accounts/{id}` 需要 `system:account:delete`，删除账号并注销全部会话；保留共享角色、角色权限及使用该角色的其他账号。不能删除自身或超级管理员，不能删除权限超出自身范围的账号。账号即使被直接从数据库移除，其旧 Token 也会返回 401。
- 两套角色删除接口统一在 Service 事务中清空关联账号的 `role_id`，删除角色权限关系并逻辑删除角色；账号、密码及个人资料保留。角色撤权后，账号仍可登录查看个人信息，但不再拥有该角色的业务权限。

## 验证码与短信投递边界

验证码复用 `VerificationCodeService`：六位数字、五分钟 TTL，使用独立用途 `admin_login` 和手机号组成 Redis Key，与小程序登录/重置密码隔离。`POST /mng/auth/verification` 按手机号生成时也只接受 `type=ADMIN_LOGIN`。密码校验通过后使用 `Cache.compareAndDelete` 原子消费，错误验证码不删除正确值，成功后不可重放。按用户名生成及登录时，账号须启用且有有效手机号。

参考项目把验证码直接返回给客户端，而且没有实际短信发送。本仓库规范禁止返回验证码，因此生成接口只缓存并返回成功，**目前尚未接入短信投递服务，不能据此声称已可向真实手机号发送短信**。上线短信登录前须接入发送服务，在服务端生成后投递；不得通过响应、日志或内存回退提供验证码。无手机号的账号可保留，但无法执行短信登录。

## 部署与迁移

下列部署步骤依赖 `docs/sql/` 目录中的 `admin.sql`、`admin-single-role.sql`、`admin-reference-alignment.sql` 和 `admin-test-data.sql`。当前工作区缺少这些脚本。应用只连接已配置的数据库。

全新数据库执行 `docs/sql/admin.sql`，创建四表并写入内置权限目录。已有四表的数据库执行 `docs/sql/admin-single-role.sql`，添加缺失的 `role_id`、索引及内置权限。执行迁移前备份并暂停 Admin 写入，存在无效的角色 ID 时先修正关联再重跑。MySQL DDL 会隐式提交，无法通过一个事务整体回滚。缺少手机号、头像等字段的更早四表版本，先执行一次 `docs/sql/admin-reference-alignment.sql`，再执行单角色迁移。

已有旧版 `admin-test-data.sql` 的测试库，可以在建表或迁移后执行该脚本，再用下列 `CALL` 传入账号、手机号和该脚本要求的 BCrypt 哈希。过程优先以 ID=1 创建启用的超管（ID=1 被占用时使用自增 ID），跳过该超管的首次改密。重复调用保留已有账号资料和角色授权。同名账号存在但不是启用的超级管理员时会中止。应用仍可校验这些 BCrypt 哈希，启动初始化、创建、修改和重置密码通过 `PasswordService` 保存 SHA-256 哈希。

```sql
CALL pettown_seed_admin_test_data('admin', '<测试手机号>', '<60 字符的 BCrypt 哈希>');
-- 完成测试数据初始化后，可移除种子过程。
DROP PROCEDURE pettown_seed_admin_test_data;
```

脚本同时生成只读、普通管理、账号停用、角色停用和未分配角色的 `fixture_admin_*` 样例账号。样例账号不配置手机号，不能直接登录；如需做登录测试，先为启用的样例账号配置独立测试手机号、重置密码，并按首次登录流程改密。脚本由运维或开发人员手工执行，应用不会自动运行这些 SQL。

复制 `pettown-boot/admin/.env.example` 为同目录 `.env`，配置本服务 MySQL、Redis。管理验证码需要 Redisson，`PETTOWN_REDIS_ENABLED` 默认 `true`；关闭后验证码调用明确失败，不回退到内存。RedisTemplate 会话不受该开关影响。

未通过 SQL 创建超管时，可设置 `PETTOWN_ADMIN_BOOTSTRAP_ENABLED=true` 并填写 `PETTOWN_ADMIN_BOOTSTRAP_USERNAME`、`PETTOWN_ADMIN_BOOTSTRAP_PASSWORD` 和 `PETTOWN_ADMIN_BOOTSTRAP_PHONE`。只在没有超级管理员时创建，已有账号不会被覆盖，ID=1 被占用时使用自增 ID。初始化后关闭开关并移除初始密码。由启动配置创建的初始账号首次登录后需改密并重新登录。

```bash
./gradlew :pettown-boot:admin:bootRun
```

## OpenAPI 与验证

通过 `http://localhost:8081/v3/api-docs` 获取 OpenAPI 3，实际端口以配置为准。文档只包含 `/mng/**`，Bearer 方案不标记 JWT；验证码和登录接口不标记认证，受保护接口明确标记。

```bash
./gradlew :module-business:test :pettown-boot:admin:test
```

HTTP 集成测试使用 H2 MySQL 模式、Mock 缓存和仅测试使用的 Sa-Token 内存 DAO，覆盖四表结构、单角色替换/清空、独立删除账号/角色、验证码隔离/一次性消费、动态权限、授权替换/清空、删除/改名生效、账号会话撤销及真实 `/v3/api-docs` 输出。未调用真实 MySQL、Redis、短信服务，也未进行 Apifox 客户端导入验证。

建表、迁移和种子测试依赖本节列出的部署 SQL。脚本缺失时无法验证对应迁移及种子行为。存储过程的 MySQL 原生执行需要另行验证。
