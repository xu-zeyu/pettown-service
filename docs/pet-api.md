# 宠物档案 API

当前接口位于 `module-app` 的 `com.pettown.app.pet` 业务包，宠物 Service、实体和 Mapper 位于可供 App 与小程序复用的 `module-business`。

从 `ai-commerce/ai-commerce-common` 的统一响应、错误码、业务异常和全局异常处理提取公共能力，分别落在 `common-core` 和 `common-web`，并调整为 `com.pettown` 包名。未使用源模块的数据库、Redis、认证和配置中心依赖。

运行：`./gradlew :pettown-boot:bootRun`

新增宠物：

```bash
curl -i -X POST http://localhost:8080/api/pets \
  -H 'Content-Type: application/json' \
  -d '{"name":"豆豆","species":"猫"}'
```

返回 HTTP 201、`Location: /api/pets/{id}`，响应体为 `{"code":"200","msg":"操作成功","data":{"id":1,"name":"豆豆","species":"猫"}}`。

查询详情：`GET /api/pets/{id}`。查询列表：`GET /api/pets`。参数校验失败返回 HTTP 400；宠物不存在返回 HTTP 404，均采用统一响应格式。

当前 `PetMapper` 的实现使用进程内存存储，适合演示调用链；应用重启后数据会清空。接入正式数据库时可替换 `InMemoryPetMapper`，Controller 和 Service 接口无需改变。
