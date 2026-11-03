package com.pettown.admin.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 菜单只是前端导航，接口仍独立校验权限。 */
public record MenuResponse(
        @Schema(description = "菜单编码") String code,
        @Schema(description = "菜单标题") String title,
        @Schema(description = "前端页面路径；目录节点为 null") String path,
        @Schema(description = "子菜单；叶子节点为空数组") List<MenuResponse> children) {
    public static List<MenuResponse> visibleTo(Set<String> permissions) {
        List<MenuResponse> children = new ArrayList<>();
        if (permissions.contains("system:account:read")) {
            children.add(new MenuResponse("accounts", "管理员", "/system/accounts", List.of()));
        }
        if (permissions.contains("system:role:read")) {
            children.add(new MenuResponse("roles", "角色", "/system/roles", List.of()));
        }
        if (permissions.contains("system:permission:read")) {
            children.add(new MenuResponse("permissions", "权限点", "/system/permissions", List.of()));
        }
        return children.isEmpty() ? List.of() : List.of(new MenuResponse("system", "系统管理", null, children));
    }
}
