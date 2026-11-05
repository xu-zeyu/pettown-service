# 已发现问题与待核实项

扫描日期：2026-11-04。以下条目仅依据本仓库文件检查，未执行构建或测试。

1. **管理端测试资源引用缺失。** `pettown-boot/admin/build.gradle.kts` 的 `processTestResources` 引用 `docs/sql/admin.sql`、`admin-single-role.sql`、`admin-reference-alignment.sql`、`admin-test-data.sql`；本次扫描未在仓库中找到 `docs/sql`。这可能影响管理端测试资源处理，需通过实际构建确认。
2. **前端技术栈登记与仓库不符。** 原 `.ai` 配置引用 `vue_frontend`，但当前仓库是 Java/Gradle 后端且未发现前端工程文件；已从本项目配置中移除该引用。若前端位于独立仓库，应另行登记。
3. **默认分支与产品信息待确认。** 本地存在 `main` 和 `origin/main`，但尚未核实远程默认分支设置；产品目标用户和业务目标也不能仅凭接口代码定稿。
4. **构建与运行未验证。** `.ai` 中的命令来源于 Gradle 文件；当前没有执行 `build` 或 `test`，因此不声明通过。
