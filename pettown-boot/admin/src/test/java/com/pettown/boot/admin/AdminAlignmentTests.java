package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.business.admin.mapper.AdminAccountMapper;
import com.pettown.business.admin.mapper.AdminPermissionMapper;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.cache.Cache;
import com.pettown.common.security.config.PetTownToken;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminAlignmentTests.TokenStorage.class)
@Sql(statements = {
        "DELETE FROM admin_role_permission", "DELETE FROM admin_account",
        "DELETE FROM admin_role", "DELETE FROM admin_permission WHERE code NOT LIKE 'system:%' AND code <> 'SUB_ADMIN'"
})
class AdminAlignmentTests {
    private static final String INITIAL_PASSWORD = "Fixture-Initial-123!";
    private static final String PASSWORD = "Fixture-Changed-123!";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AdminService service;
    @Autowired private AdminAccountMapper accounts;
    @Autowired private AdminPermissionMapper permissions;
    @Autowired private SaTokenDao tokenDao;
    @MockitoBean private Cache cache;
    private final Map<String, Object> codes = new ConcurrentHashMap<>();
    private String rootToken;

    @TestConfiguration(proxyBeanMethods = false)
    static class TokenStorage {
        @Bean SaTokenDao testTokenDao() { return new SaTokenDaoDefaultImpl(); }
        @Bean SecurityProbeController securityProbeController() { return new SecurityProbeController(); }
    }

    @org.springframework.web.bind.annotation.RestController
    static class SecurityProbeController {
        @org.springframework.web.bind.annotation.GetMapping({"/api/admin/public/security-probe", "/security-probe"})
        com.pettown.common.result.Result<String> protectedEndpoint() {
            return com.pettown.common.result.Result.success("ok");
        }

        @org.springframework.web.bind.annotation.GetMapping("/api/admin/future-permission-probe")
        @SaCheckPermission(value = "future:unregistered:permission", type = "admin")
        com.pettown.common.result.Result<String> futurePermission() {
            return com.pettown.common.result.Result.success("ok");
        }
    }

    @Test
    void requiresAdminSessionByDefaultAndChecksDeclaredPermissionsThroughSaToken() throws Exception {
        call("GET", "/public/security-probe", null, null, 401);
        call("GET", "/public/security-probe", rootToken, null, 200);
        call("GET", "/future-permission-probe", rootToken, null, 200);
        var role = service.createNamedRole(1L, "普通只读管理员", null);
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        service.createAdmin(1L, "fixture_probe", PASSWORD, "13800000002", null, role.getId());
        String staffToken = passwordLogin("fixture_probe");
        call("GET", "/public/security-probe", staffToken, null, 200);
        call("GET", "/future-permission-probe", staffToken, null, 403);
        call("GET", "/system/accounts", staffToken, null, 200);
        String userToken = PetTownToken.USER.createLoginSession("fixture-user");
        call("GET", "/self", userToken, null, 401);
        call("GET", "/system/accounts", userToken, null, 401);
        mvc.perform(MockMvcRequestBuilders.get("/security-probe")).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/security-probe").header("Authorization", "Bearer " + rootToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/admin/self").param("Authorization", "Bearer " + rootToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/admin/self")
                        .cookie(new jakarta.servlet.http.Cookie("Authorization", "Bearer " + rootToken)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    @Transactional
    void superAdminPermissionsDoNotDependOnRolesOrThePermissionCatalog() throws Exception {
        assertThat(service.accountRoleIds(1L)).isEmpty();
        permissions.delete(new LambdaQueryWrapper<>());
        assertThat(permissions.selectCount(null)).isZero();
        call("GET", "/system/accounts", rootToken, null, 200);
        call("GET", "/future-permission-probe", rootToken, null, 200);
        call("GET", "/public/security-probe", rootToken, null, 200);
    }

    @Test
    void superAdminStillRequiresEnabledAccountCurrentSessionAndInitialPasswordChange() throws Exception {
        AdminAccount root = service.getAccount(1L);
        root.setPasswordChangeRequired(true);
        accounts.updateById(root);
        call("GET", "/future-permission-probe", rootToken, null, 403);
        call("GET", "/self", rootToken, null, 200);
        root.setPasswordChangeRequired(false);
        root.setEnabled(false);
        accounts.updateById(root);
        call("GET", "/future-permission-probe", rootToken, null, 401);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createNamedRole(1L, "禁用超管", null))
                .isInstanceOf(com.pettown.common.exception.BusinessException.class);
        root.setEnabled(true);
        accounts.updateById(root);
        String freshToken = passwordLogin("fixture_root");
        root.setSessionVersion(root.getSessionVersion() + 1);
        accounts.updateById(root);
        call("GET", "/future-permission-probe", freshToken, null, 401);
        String removedToken = passwordLogin("fixture_root");
        accounts.deleteById(1L);
        call("GET", "/future-permission-probe", removedToken, null, 401);
    }

    @Test
    void initializesAndAuthorizesSuperAdminWhenIdOneBelongsToAnOrdinaryAccount() throws Exception {
        AdminAccount ordinary = service.getAccount(1L);
        ordinary.setUsername("fixture_existing");
        ordinary.setSuperAdmin(false);
        accounts.updateById(ordinary);
        service.initializeSuperAdmin("fixture_new_root", INITIAL_PASSWORD, "13800000003");
        AdminAccount root = accounts.selectOne(AdminAccount::getUsername, "fixture_new_root");
        assertThat(root.getId()).isNotEqualTo(1L);
        assertThat(service.getAccount(1L).getUsername()).isEqualTo("fixture_existing");
        service.changePassword(root.getId(), INITIAL_PASSWORD, PASSWORD);
        service.createNamedRole(root.getId(), "非固定 ID 超管创建的角色", null);
        call("GET", "/permission/list", passwordLogin("fixture_new_root"), null, 200);
        service.initializeSuperAdmin("fixture_ignored", INITIAL_PASSWORD, "13800000004");
        assertThat(accounts.selectCount(AdminAccount::getSuperAdmin, true)).isEqualTo(1);
    }

    @BeforeEach
    void setup() throws Exception {
        // Only test storage is in memory; the production Redis DAO remains unchanged.
        SaManager.setSaTokenDao(tokenDao);
        codes.clear();
        doAnswer(call -> { codes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(cache).set(anyString(), any(), any(Duration.class));
        when(cache.compareAndDelete(anyString(), any())).thenAnswer(call ->
                codes.remove(call.getArgument(0), call.getArgument(1)));
        service.initializeSuperAdmin("fixture_root", INITIAL_PASSWORD, "13700000000");
        service.changePassword(1L, INITIAL_PASSWORD, PASSWORD);
        rootToken = passwordLogin("fixture_root");
    }

    @Test
    void smsLoginUsesPasswordAndIsolatedOneTimeCodeWithoutLeakingSecrets() throws Exception {
        var role = service.createNamedRole(1L, "客服", "测试角色");
        var admin = service.createAdmin(1L, "fixture_staff", PASSWORD, "13800000000", "测试管理员", role.getId());
        JsonNode generated = call("POST", "/public/captcha/login/fixture_staff", null, null, 200);
        assertThat(generated.path("data").isNull()).isTrue();
        String key = "pettown:verification-code:admin_login:13800000000";
        String code = (String) codes.get(key);
        assertThat(code).matches("[0-9]{6}");
        verify(cache).set(eq(key), eq(code), eq(Duration.ofMinutes(5)));
        codes.put("pettown:verification-code:login_register:13800000000", "654321");
        call("POST", "/auth/verification", null, Map.of("phone", "13800000000", "type", "LOGIN_REGISTER"), 400);
        assertThat(codes).containsEntry("pettown:verification-code:login_register:13800000000", "654321");
        call("POST", "/login/sms", null,
                Map.of("username", "fixture_staff", "password", "Wrong-Fixture-Password", "smsCode", code), 400);
        assertThat(codes.get(key)).isEqualTo(code);
        String wrongCode = code.equals("000000") ? "000001" : "000000";
        call("POST", "/login/sms", null, Map.of("username", "fixture_staff", "password", PASSWORD, "smsCode", wrongCode), 400);
        JsonNode loggedIn = call("POST", "/login/sms", null,
                Map.of("username", "fixture_staff", "password", PASSWORD, "smsCode", code), 200);
        String token = loggedIn.path("data").asText();
        assertThat(token).isNotBlank();
        assertThat(codes).doesNotContainKey(key).containsKey("pettown:verification-code:login_register:13800000000");
        call("POST", "/login/sms", null, Map.of("username", "fixture_staff", "password", PASSWORD, "smsCode", code), 400);
        JsonNode self = call("GET", "/self", token, null, 200).path("data");
        assertThat(self.path("userName").asText()).isEqualTo("fixture_staff");
        assertThat(self.has("authorities")).isTrue();
        JsonNode info = call("GET", "/info", token, null, 200).path("data");
        assertThat(info.path("realName").asText()).isEqualTo("测试管理员");
        assertThat(info.path("role").path("rname").asText()).isEqualTo("客服");
        assertThat(info.path("lastLoginTime").isNull()).isFalse();
        assertThat(info.has("passwordHash")).isFalse();
        assertThat(info.has("sessionVersion")).isFalse();
        assertThat(service.getAccount(admin.getId()).getPasswordHash()).startsWith("$2");
        call("GET", "/permission/list", token, null, 403);
        call("GET", "/self", null, null, 401);
        call("POST", "/auth/logout", token, null, 200);
        call("GET", "/self", token, null, 401);
    }

    @Test
    void dynamicPermissionsSyncAssignRenameClearAndDeleteAffectExistingSessions() throws Exception {
        call("POST", "/permission/batchSync", rootToken, Map.of("permissions", List.of(
                Map.of("name", "查看订单", "code", "order:read"),
                Map.of("name", "重复项不覆盖", "code", "order:read"))), 200);
        var permission = findPermission("order:read");
        assertThat(permission.path("name").asText()).isEqualTo("查看订单");
        call("POST", "/permission/create", rootToken, Map.of("name", "重复", "code", "order:read"), 400);
        call("POST", "/role/create", rootToken, Map.of("rname", "订单角色", "description", "订单管理"), 200);
        JsonNode role = call("GET", "/role/list", rootToken, null, 200).path("data").get(0);
        long roleId = role.path("id").asLong();
        call("POST", "/create", rootToken, Map.of("username", "fixture_orders", "password", PASSWORD,
                "phone", "13900000000", "realName", "订单专员", "roleId", roleId), 200);
        String staffToken = passwordLogin("fixture_orders");
        call("POST", "/role/assignPermissions", rootToken,
                Map.of("roleId", roleId, "permissionIds", List.of(permission.path("id").asLong(), permission.path("id").asLong())), 200);
        assertThat(authorities(staffToken)).contains("order:read");
        call("POST", "/permission/update", rootToken,
                Map.of("id", permission.path("id").asLong(), "code", "order:view"), 200);
        assertThat(authorities(staffToken)).contains("order:view").doesNotContain("order:read");
        call("POST", "/role/assignPermissions", rootToken,
                Map.of("roleId", roleId, "permissionIds", List.of(999999L)), 400);
        assertThat(authorities(staffToken)).contains("order:view");
        call("POST", "/role/assignPermissions", rootToken,
                Map.of("roleId", roleId, "permissionIds", List.of()), 200);
        assertThat(authorities(staffToken)).isEmpty();
        call("POST", "/role/assignPermissions", rootToken,
                Map.of("roleId", roleId, "permissionIds", List.of(permission.path("id").asLong())), 200);
        call("DELETE", "/permission/" + permission.path("id").asLong(), rootToken, null, 200);
        assertThat(authorities(staffToken)).isEmpty();
        call("POST", "/role/delete", rootToken, Map.of("id", roleId), 200);
        call("GET", "/role/" + roleId, rootToken, null, 404);
        assertThat(service.accountRoleIds(accounts.selectOne(AdminAccount::getUsername, "fixture_orders").getId())).isEmpty();
        assertThat(call("GET", "/role/list", rootToken, null, 200).path("data")).isEmpty();
    }

    @Test
    void rejectsPasswordOnlyLoginAndDelegatedPrivilegeEscalation() throws Exception {
        call("POST", "/auth/login", null, Map.of("username", "fixture_root", "password", PASSWORD), 400);
        var role = service.createNamedRole(1L, "有限管理角色", null);
        var subAdmin = findPermission("SUB_ADMIN");
        service.replaceRolePermissionIds(1L, role.getId(), List.of(subAdmin.path("id").asLong()));
        var staff = service.createAdmin(1L, "fixture_delegate", PASSWORD, "13500000000", null, role.getId());
        String token = passwordLogin("fixture_delegate");
        call("POST", "/role/update", token, Map.of("id", role.getId(), "rname", "改自己的角色"), 400);
        call("POST", "/role/create", token, Map.of("rname", "待授权角色"), 200);
        var otherRole = service.listNamedRoles(new com.pettown.common.mybatis.PageParam().setSize(-1)).getList()
                .stream().filter(r -> !r.getId().equals(role.getId())).findFirst().orElseThrow();
        var unavailablePermission = findPermission("system:account:create");
        call("POST", "/role/assignPermissions", token, Map.of("roleId", otherRole.getId(),
                "permissionIds", List.of(unavailablePermission.path("id").asLong())), 400);
        assertThat(service.rolePermissions(otherRole.getId())).isEmpty();
        call("GET", "/system/accounts", token, null, 403);
        service.updateAccount(1L, staff.getId(), "新的显示名", "13400000000");
        call("GET", "/self", token, null, 401);
    }

    @Test
    @Transactional
    void permissionCatalogChangesCannotExpandADelegatesAuthority() throws Exception {
        service.createPermission(1L, "本人权限", "own:read");
        service.createPermission(1L, "他人权限", "foreign:write");
        long ownPermissionId = findPermission("own:read").path("id").asLong();
        long foreignPermissionId = findPermission("foreign:write").path("id").asLong();
        var role = service.createNamedRole(1L, "权限目录有限管理角色", null);
        service.replaceRolePermissions(1L, role.getId(), List.of("SUB_ADMIN", "own:read"));
        service.createAdmin(1L, "fixture_catalog_delegate", PASSWORD, "13800000006", null, role.getId());
        String token = passwordLogin("fixture_catalog_delegate");

        call("DELETE", "/permission/" + foreignPermissionId, token, null, 400);
        assertThat(service.getPermission(foreignPermissionId).getCode()).isEqualTo("foreign:write");
        call("POST", "/permission/update", token,
                Map.of("id", ownPermissionId, "code", "foreign:new"), 400);
        assertThat(service.getPermission(ownPermissionId).getCode()).isEqualTo("own:read");

        // 内置目录缺失时仍不能把已获授的自定义编码改成新的后端权限。
        permissions.delete(new LambdaQueryWrapper<AdminPermissionRecord>()
                .eq(AdminPermissionRecord::getCode, "system:account:read"));
        call("POST", "/permission/update", token,
                Map.of("id", ownPermissionId, "code", "system:account:read"), 400);
        assertThat(service.getPermission(ownPermissionId).getCode()).isEqualTo("own:read");
        assertThat(service.rolePermissions(role.getId())).containsExactly("SUB_ADMIN", "own:read");
        assertThat(authorities(token)).containsExactly("SUB_ADMIN", "own:read");
        call("GET", "/system/accounts", token, null, 403);
    }

    @Test
    void rollsBackTheEntirePermissionBatchWhenALaterItemIsInvalid() throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.syncPermissions(1L, List.of(
                new AdminService.PermissionDefinition("合法项", "batch:valid"),
                new AdminService.PermissionDefinition("非法项", "*"))))
                .isInstanceOf(com.pettown.common.exception.BusinessException.class);
        var permissions = call("GET", "/permission/list", rootToken, null, 200).path("data");
        assertThat(permissions.findValuesAsText("code")).doesNotContain("batch:valid");
    }

    @Test
    void singleRoleCanBeReplacedAndClearedWithoutAnAccountRoleTable() throws Exception {
        var roleA = service.createNamedRole(1L, "单角色 A", null);
        var roleB = service.createNamedRole(1L, "单角色 B", null);
        service.replaceRolePermissions(1L, roleA.getId(), List.of("system:account:read"));
        service.replaceRolePermissions(1L, roleB.getId(), List.of("system:permission:read"));
        var staff = service.createAdmin(1L, "fixture_role", PASSWORD, "13300000000", null, roleA.getId());
        String token = passwordLogin("fixture_role");
        assertThat(service.getAccount(staff.getId()).getRoleId()).isEqualTo(roleA.getId());
        assertThat(authorities(token)).containsExactly("system:account:read");
        call("PUT", "/system/accounts/" + staff.getId() + "/role", rootToken, Map.of("roleId", roleB.getId()), 200);
        assertThat(authorities(token)).containsExactly("system:permission:read");
        call("PUT", "/system/accounts/" + staff.getId() + "/roles", rootToken,
                Map.of("roleIds", List.of(roleA.getId(), roleB.getId())), 400);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isEqualTo(roleB.getId());
        call("PUT", "/system/accounts/" + staff.getId() + "/role", rootToken, Map.of("roleId", 999999L), 404);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isEqualTo(roleB.getId());
        call("PUT", "/system/accounts/" + staff.getId() + "/role", rootToken, Map.of(), 200);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isNull();
        assertThat(authorities(token)).isEmpty();
        call("PUT", "/system/accounts/" + staff.getId() + "/roles", rootToken, Map.of("roleIds", List.of(roleA.getId())), 200);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isEqualTo(roleA.getId());
        call("PUT", "/system/accounts/" + staff.getId() + "/roles", rootToken, Map.of("roleIds", List.of()), 200);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isNull();
    }

    @Test
    void roleAndPermissionChangesImmediatelyAffectAnExistingToken() throws Exception {
        var role = service.createRole(1L, "fixture_live_role", "动态权限角色");
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        var staff = service.createAdmin(1L, "fixture_live_access", PASSWORD, "13800000005", null, role.getId());
        String token = passwordLogin("fixture_live_access");
        call("GET", "/system/accounts", token, null, 200);

        service.updateRole(1L, role.getId(), role.getName(), false);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isEqualTo(role.getId());
        assertThat(authorities(token)).isEmpty();
        assertThat(call("GET", "/info", token, null, 200).path("data").path("role").isNull()).isTrue();
        call("GET", "/system/accounts", token, null, 403);

        service.updateRole(1L, role.getId(), role.getName(), true);
        call("GET", "/system/accounts", token, null, 200);
        service.replaceRolePermissions(1L, role.getId(), List.of());
        call("GET", "/system/accounts", token, null, 403);
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        call("GET", "/system/accounts", token, null, 200);

        call("PUT", "/system/accounts/" + staff.getId() + "/role", rootToken, Map.of(), 200);
        call("GET", "/system/accounts", token, null, 403);
        service.assignAccountRole(1L, staff.getId(), role.getId());
        call("GET", "/system/accounts", token, null, 200);
        service.deleteRole(1L, role.getId());
        call("GET", "/system/accounts", token, null, 403);
        call("GET", "/self", token, null, 200);
    }

    @Test
    void deletingAnAccountPreservesRoleAndPermissionsAndRevokesAllSessions() throws Exception {
        var role = service.createNamedRole(1L, "可共享角色", null);
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        var staff = service.createAdmin(1L, "fixture_delete", PASSWORD, "13200000000", null, role.getId());
        var other = service.createAdmin(1L, "fixture_keep", PASSWORD, "13100000000", null, role.getId());
        String tokenA = passwordLogin("fixture_delete");
        String tokenB = passwordLogin("fixture_delete");
        call("DELETE", "/system/accounts/" + staff.getId(), tokenA, null, 403);
        call("DELETE", "/system/accounts/1", rootToken, null, 400);
        call("DELETE", "/system/accounts/" + staff.getId(), rootToken, null, 200);
        assertThat(service.findAccount(staff.getId())).isEmpty();
        assertThat(service.getRole(role.getId())).isNotNull();
        assertThat(service.rolePermissions(role.getId())).containsExactly("system:account:read");
        assertThat(service.getAccount(other.getId()).getRoleId()).isEqualTo(role.getId());
        call("GET", "/self", tokenA, null, 401);
        call("GET", "/self", tokenB, null, 401);
        call("DELETE", "/system/accounts/" + staff.getId(), rootToken, null, 404);
    }

    @Test
    void deletedAccountsAreRejectedEvenWhenRemovedOutsideTheHttpEndpoint() throws Exception {
        var staff = service.createAdmin(1L, "fixture_removed", PASSWORD, "13000000000", null, null);
        String token = passwordLogin("fixture_removed");
        accounts.deleteById(staff.getId());
        call("GET", "/self", token, null, 401);
    }

    @Test
    void deletingARoleKeepsAccountsAndClearsOnlyTheirRoleReference() throws Exception {
        var role = service.createRole(1L, "fixture_role_delete", "待删除角色");
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        var staff = service.createAdmin(1L, "fixture_role_keep", PASSWORD, "13900000001", null, role.getId());
        String token = passwordLogin("fixture_role_keep");
        call("DELETE", "/system/roles/" + role.getId(), rootToken, null, 200);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isNull();
        assertThat(authorities(token)).isEmpty();
        assertThat(call("GET", "/info", token, null, 200).path("data").path("role").isNull()).isTrue();
    }

    @Test
    void preservesBuiltinsValidationAndExistingSessionRevocation() throws Exception {
        var permission = findPermission("SUB_ADMIN");
        call("DELETE", "/permission/" + permission.path("id").asLong(), rootToken, null, 400);
        call("POST", "/permission/update", rootToken,
                Map.of("id", permission.path("id").asLong(), "code", "other:admin"), 400);
        call("POST", "/permission/create", rootToken, Map.of("name", "非法", "code", "*"), 400);
        call("GET", "/permission/page?size=0", rootToken, null, 400);
        JsonNode page = call("GET", "/permission/page?size=-1", rootToken, null, 200).path("data");
        assertThat(page.path("list").size()).isEqualTo(page.path("total").asInt());
        var staff = service.createAdmin(1L, "fixture_disabled", PASSWORD, "13600000000", null, null);
        String token = passwordLogin("fixture_disabled");
        service.setAccountEnabled(1L, staff.getId(), false);
        call("GET", "/self", token, null, 401);
        service.setAccountEnabled(1L, staff.getId(), true);
        call("GET", "/self", token, null, 401);
        String freshToken = passwordLogin("fixture_disabled");
        service.resetPassword(1L, staff.getId(), INITIAL_PASSWORD);
        call("GET", "/self", freshToken, null, 401);
    }

    private String passwordLogin(String username) throws Exception {
        service.generateLoginCode(username);
        String key = "pettown:verification-code:admin_login:" + accounts.selectOne(AdminAccount::getUsername, username).getPhone();
        return call("POST", "/auth/login", null, Map.of("username", username, "password", PASSWORD, "smsCode", codes.get(key)), 200)
                .path("data").path("token").asText();
    }

    private JsonNode findPermission(String code) throws Exception {
        for (JsonNode permission : call("GET", "/permission/list", rootToken, null, 200).path("data")) {
            if (permission.path("code").asText().equals(code)) return permission;
        }
        throw new AssertionError("Missing permission " + code);
    }

    private List<String> authorities(String token) throws Exception {
        return json.convertValue(call("GET", "/self", token, null, 200).path("data").path("authorities"),
                json.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private JsonNode call(String method, String path, String token, Object body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/api/admin" + path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(method + " " + path + ": " + response.getContentAsString()).isEqualTo(expectedStatus);
        return json.readTree(response.getContentAsString());
    }
}
