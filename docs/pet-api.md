# 宠物档案 API

当前接口位于 `module-app` 的 `com.pettown.app.pet` 业务包，宠物 Service、实体和 Mapper 位于可供 App 与小程序复用的 `module-business`。

从 `ai-commerce/ai-commerce-common` 的统一响应、错误码、业务异常和全局异常处理提取公共能力，分别落在 `common-core` 和 `common-web`，并调整为 `com.pettown` 包名。未使用源模块的数据库、Redis、认证和配置中心依赖。

运行：`./gradlew :pettown-boot:app:bootRun`（先按 [启动说明](run.md) 配置 App 的 `.env`）。

新增宠物：

```bash
curl -i -X POST http://localhost:8080/api/pets \
  -H 'Content-Type: application/json' \
  -d '{"name":"豆豆","species":"猫"}'
```

返回 HTTP 201、`Location: /api/pets/{id}`，响应体为 `{"code":"200","msg":"操作成功","data":{"id":1,"name":"豆豆","species":"猫"}}`。

查询详情：`GET /api/pets/{id}`。查询列表：`GET /api/pets`。参数校验失败返回 HTTP 400；宠物不存在返回 HTTP 404，均采用统一响应格式。

当前 `PetMapper` 继承 `BaseMapperX<Pet>`，通过 MyBatis-Plus 将数据持久化到 `pet` 表，重启后保留。启动前配置 App 数据库连接并执行 [`sql/persistence.sql`](sql/persistence.sql)。实体使用自增 ID，并自动填充创建及更新时间；列表按 ID 升序返回。
