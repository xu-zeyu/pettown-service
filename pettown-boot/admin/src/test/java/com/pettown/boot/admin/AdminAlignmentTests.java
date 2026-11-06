package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.secure.SaSecureUtil;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    @Autowired private JdbcTemplate jdbc;
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
        @org.springframework.web.bind.annotation.GetMapping({"/mng/public/security-probe", "/security-probe"})
        com.pettown.common.result.Result<String> protectedEndpoint() {
            return com.pettown.common.result.Result.success("ok");
        }

        @org.springframework.web.bind.annotation.GetMapping("/mng/future-permission-probe")
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
        call("GET", "/auth/me", userToken, null, 401);
        call("GET", "/system/accounts", userToken, null, 401);
        mvc.perform(MockMvcRequestBuilders.get("/security-probe")).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/security-probe").header("Authorization", "Bearer " + rootToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/mng/self").param("Authorization", "Bearer " + rootToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/mng/self")
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
        call("GET", "/auth/me", rootToken, null, 200);
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
    void rejectsLoginForUnknownUsername() throws Exception {
        JsonNode response = call("POST", "/auth/login", null,
                Map.of("username", "fixture_missing", "password", PASSWORD, "smsCode", "000007"), 400);
        assertThat(response.path("msg").asText()).isEqualTo("账号或密码错误");
    }

    @Test
    void smsLoginUsesPasswordAndIsolatedOneTimeCodeWithoutLeakingSecrets() throws Exception {
        var role = service.createNamedRole(1L, "客服", "测试角色");
        var admin = service.createAdmin(1L, "fixture_staff", PASSWORD, "13800000000", "测试管理员", role.getId());
        JsonNode generated = call("POST", "/auth/verification", null,
                Map.of("phone", "13800000000", "type", "ADMIN_LOGIN"), 200);
        assertThat(generated.path("data").isNull()).isTrue();
        String key = "pettown:verification-code:admin_login:13800000000";
        String code = (String) codes.get(key);
        assertThat(code).matches("[0-9]{6}");
        verify(cache).set(eq(key), eq(code), eq(Duration.ofMinutes(5)));
        codes.put("pettown:verification-code:login_register:13800000000", "654321");
        call("POST", "/auth/verification", null, Map.of("phone", "13800000000", "type", "LOGIN_REGISTER"), 400);
        assertThat(codes).containsEntry("pettown:verification-code:login_register:13800000000", "654321");
        call("POST", "/auth/login", null,
                Map.of("username", "fixture_staff", "password", "Wrong-Fixture-Password", "smsCode", code), 400);
        assertThat(codes.get(key)).isEqualTo(code);
        String wrongCode = code.equals("000000") ? "000001" : "000000";
        call("POST", "/auth/login", null, Map.of("username", "fixture_staff", "password", PASSWORD, "smsCode", wrongCode), 400);
        JsonNode loggedIn = call("POST", "/auth/login", null,
                Map.of("username", "fixture_staff", "password", PASSWORD, "smsCode", code), 200);
        String token = loggedIn.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        assertThat(codes).doesNotContainKey(key).containsKey("pettown:verification-code:login_register:13800000000");
        call("POST", "/auth/login", null, Map.of("username", "fixture_staff", "password", PASSWORD, "smsCode", code), 400);
        JsonNode me = call("GET", "/auth/me", token, null, 200).path("data");
        assertThat(me.path("account").path("username").asText()).isEqualTo("fixture_staff");
        assertThat(me.has("permissions")).isTrue();
        assertThat(me.path("account").path("displayName").asText()).isEqualTo("测试管理员");
        assertThat(me.path("roles").get(0).path("name").asText()).isEqualTo("客服");
        assertThat(service.getAccount(admin.getId()).getLastLoginTime()).isNotNull();
        assertThat(me.path("account").has("passwordHash")).isFalse();
        assertThat(me.path("account").has("sessionVersion")).isFalse();
        assertThat(service.getAccount(admin.getId()).getPasswordHash()).isEqualTo(SaSecureUtil.sha256(PASSWORD));
        call("GET", "/permission/list", token, null, 403);
        call("GET", "/auth/me", null, null, 401);
        call("POST", "/auth/logout", token, null, 200);
        call("GET", "/auth/me", token, null, 401);
    }

    @Test
    void changingPasswordRevokesEveryExistingSessionAndRequiresTheNewPassword() throws Exception {
        String otherToken = passwordLogin("fixture_root");
        String replacement = "Fixture-Replaced-123!";

        call("PUT", "/auth/password", rootToken,
                Map.of("oldPassword", PASSWORD, "newPassword", replacement), 200);
        call("GET", "/auth/me", rootToken, null, 401);
        call("GET", "/auth/me", otherToken, null, 401);

        service.generateLoginCode("fixture_root");
        String key = "pettown:verification-code:admin_login:13700000000";
        Object code = codes.get(key);
        call("POST", "/auth/login", null,
                Map.of("username", "fixture_root", "password", PASSWORD, "smsCode", code), 400);
        assertThat(codes).containsEntry(key, code);
        String freshToken = call("POST", "/auth/login", null,
                Map.of("username", "fixture_root", "password", replacement, "smsCode", code), 200)
                .path("data").path("token").asText();
        assertThat(freshToken).isNotBlank();
        call("GET", "/auth/me", freshToken, null, 200);
    }

    @Test
    void initialPasswordOnlyAllowsSelfServiceUntilItIsChanged() throws Exception {
        service.createAccount(1L, "fixture_initial", "初始账号", INITIAL_PASSWORD, "13900000000");
        service.generateLoginCode("fixture_initial");
        String key = "pettown:verification-code:admin_login:13900000000";
        String token = call("POST", "/auth/login", null,
                Map.of("username", "fixture_initial", "password", INITIAL_PASSWORD, "smsCode", codes.get(key)), 200)
                .path("data").path("token").asText();

        assertThat(call("GET", "/auth/me", token, null, 200).path("data").path("account")
                .path("passwordChangeRequired").asBoolean()).isTrue();
        call("GET", "/auth/menus", token, null, 403);
        call("GET", "/system/accounts", token, null, 403);
        call("PUT", "/auth/password", token,
                Map.of("oldPassword", INITIAL_PASSWORD, "newPassword", PASSWORD), 200);
        call("GET", "/auth/me", token, null, 401);

        service.generateLoginCode("fixture_initial");
        String freshToken = call("POST", "/auth/login", null,
                Map.of("username", "fixture_initial", "password", PASSWORD, "smsCode", codes.get(key)), 200)
                .path("data").path("token").asText();
        assertThat(call("GET", "/auth/me", freshToken, null, 200).path("data").path("account")
                .path("passwordChangeRequired").asBoolean()).isFalse();
        call("GET", "/auth/menus", freshToken, null, 200);
    }

    @Test
    void rejectedPasswordChangeDoesNotRevokeTheCurrentSession() throws Exception {
        call("PUT", "/auth/password", rootToken,
                Map.of("oldPassword", "Incorrect-Old-123!", "newPassword", "Fixture-Replaced-123!"), 400);
        call("GET", "/auth/me", rootToken, null, 200);
        call("PUT", "/auth/password", rootToken,
                Map.of("oldPassword", PASSWORD, "newPassword", PASSWORD), 400);
        call("GET", "/auth/me", rootToken, null, 200);
    }

    @Test
    void authenticatesAnAdministratorWithAnExistingBcryptHash() throws Exception {
        var admin = service.createAdmin(1L, "fixture_legacy", PASSWORD, "13800000000", null, null);
        admin.setPasswordHash(new BCryptPasswordEncoder().encode(PASSWORD));
        accounts.updateById(admin);

        String token = passwordLogin("fixture_legacy");
        assertThat(call("GET", "/auth/me", token, null, 200).path("data").path("account").path("username").asText())
                .isEqualTo("fixture_legacy");
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
        call("GET", "/auth/me", token, null, 401);
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
        var role = service.createNamedRole(1L, "动态权限角色", null);
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        var staff = service.createAdmin(1L, "fixture_live_access", PASSWORD, "13800000005", null, role.getId());
        String token = passwordLogin("fixture_live_access");
        call("GET", "/system/accounts", token, null, 200);

        service.updateRole(1L, role.getId(), role.getName(), false);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isEqualTo(role.getId());
        assertThat(authorities(token)).isEmpty();
        assertThat(call("GET", "/auth/me", token, null, 200).path("data").path("roles").size()).isZero();
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
        call("GET", "/auth/me", token, null, 200);
    }

    @Test
    void navigationTracksCurrentReadPermissionsWithoutGrantingServerAccess() throws Exception {
        var role = service.createNamedRole(1L, "导航验收角色", null);
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        service.createAdmin(1L, "fixture_navigation", PASSWORD, "13800000007", null, role.getId());
        String token = passwordLogin("fixture_navigation");

        JsonNode accountMenu = call("GET", "/auth/menus", token, null, 200).path("data");
        assertThat(accountMenu.get(0).path("children").findValuesAsText("code"))
                .containsExactly("accounts");
        call("GET", "/system/accounts", token, null, 200);
        call("GET", "/system/roles", token, null, 403);
        call("GET", "/system/permissions", token, null, 403);

        service.replaceRolePermissions(1L, role.getId(), List.of());
        assertThat(call("GET", "/auth/menus", token, null, 200).path("data")).isEmpty();
        call("GET", "/system/accounts", token, null, 403);

        service.replaceRolePermissions(1L, role.getId(), List.of("system:role:read"));
        JsonNode roleMenu = call("GET", "/auth/menus", token, null, 200).path("data");
        assertThat(roleMenu.get(0).path("children").findValuesAsText("code"))
                .containsExactly("roles");
        call("GET", "/system/roles", token, null, 200);
        call("GET", "/system/accounts", token, null, 403);
    }

    @Test
    void currentAdminApiReturnsLiveRolePermissionsAndMenus() throws Exception {
        JsonNode root = currentAuth("/me", rootToken, 200).path("data");
        assertThat(root.path("account").path("username").asText()).isEqualTo("fixture_root");
        assertThat(strings(root.path("permissions"))).contains("system:account:read", "system:role:read");
        assertThat(currentAuth("/menus", rootToken, 200).path("data").get(0).path("children")
                .findValuesAsText("code")).contains("accounts", "roles", "permissions");

        var accountRole = service.createNamedRole(1L, "新接口账号角色", null);
        var roleRole = service.createNamedRole(1L, "新接口角色角色", null);
        service.replaceRolePermissions(1L, accountRole.getId(), List.of("system:account:read"));
        service.replaceRolePermissions(1L, roleRole.getId(), List.of("system:role:read"));
        service.createAdmin(1L, "fixture_current_account", PASSWORD, "13800000021", null, accountRole.getId());
        service.createAdmin(1L, "fixture_current_role", PASSWORD, "13800000022", null, roleRole.getId());
        String accountToken = passwordLogin("fixture_current_account");
        String roleToken = passwordLogin("fixture_current_role");

        JsonNode account = currentAuth("/me", accountToken, 200).path("data");
        assertThat(account.path("roles").size()).isEqualTo(1);
        assertThat(strings(account.path("permissions"))).containsExactly("system:account:read");
        assertThat(account.path("account").has("passwordHash")).isFalse();
        assertThat(account.path("account").has("sessionVersion")).isFalse();
        assertThat(currentAuth("/menus", accountToken, 200).path("data").get(0).path("children")
                .findValuesAsText("code")).containsExactly("accounts");
        assertThat(strings(currentAuth("/me", roleToken, 200).path("data").path("permissions")))
                .containsExactly("system:role:read");
        assertThat(currentAuth("/menus", roleToken, 200).path("data").get(0).path("children")
                .findValuesAsText("code")).containsExactly("roles");

        service.replaceRolePermissions(1L, accountRole.getId(), List.of());
        assertThat(currentAuth("/me", accountToken, 200).path("data").path("permissions")).isEmpty();
        assertThat(currentAuth("/menus", accountToken, 200).path("data")).isEmpty();
        call("GET", "/auth/me", accountToken, null, 200);
    }

    @Test
    void currentAdminApiRejectsInvalidSessionsAndRestrictsInitialPassword() throws Exception {
        currentAuth("/me", null, 401);
        currentAuth("/menus", null, 401);
        String userToken = PetTownToken.USER.createLoginSession("fixture-current-user");
        currentAuth("/me", userToken, 401);
        currentAuth("/menus", userToken, 401);

        var disabled = service.createAdmin(1L, "fixture_current_disabled", PASSWORD, "13800000023", null, null);
        String disabledToken = passwordLogin("fixture_current_disabled");
        service.setAccountEnabled(1L, disabled.getId(), false);
        currentAuth("/me", disabledToken, 401);
        currentAuth("/menus", disabledToken, 401);
        service.setAccountEnabled(1L, disabled.getId(), true);
        currentAuth("/me", disabledToken, 401);
        currentAuth("/menus", disabledToken, 401);

        service.createAccount(1L, "fixture_current_initial", "初始账号", INITIAL_PASSWORD, "13800000024");
        service.generateLoginCode("fixture_current_initial");
        String key = "pettown:verification-code:admin_login:13800000024";
        String initialToken = call("POST", "/auth/login", null,
                Map.of("username", "fixture_current_initial", "password", INITIAL_PASSWORD, "smsCode", codes.get(key)), 200)
                .path("data").path("token").asText();
        assertThat(currentAuth("/me", initialToken, 200).path("data").path("account")
                .path("passwordChangeRequired").asBoolean()).isTrue();
        currentAuth("/menus", initialToken, 403);
        call("GET", "/auth/me", initialToken, null, 200);
        call("GET", "/auth/menus", initialToken, null, 403);
    }

    @Test
    void currentAccountPageCombinesFiltersAndKeepsStablePageTotals() throws Exception {
        var supportRole = service.createNamedRole(1L, "分页客服", null);
        var managerRole = service.createNamedRole(1L, "分页主管", null);
        var anna = service.createAdmin(1L, "fixture_page_anna", PASSWORD, "13800000031", "北区客服", supportRole.getId());
        var ben = service.createAdmin(1L, "fixture_page_ben", PASSWORD, "13800000032", "南区客服", supportRole.getId());
        var cora = service.createAdmin(1L, "fixture_page_cora", PASSWORD, "13800000033", "北区主管", managerRole.getId());
        service.setAccountEnabled(1L, ben.getId(), false);

        JsonNode first = currentAccounts(rootToken, 200,
                Map.of("username", "fixture_page", "page", "1", "size", "2")).path("data");
        assertThat(first.path("total").asLong()).isEqualTo(3);
        assertThat(first.path("list").size()).isEqualTo(2);
        assertThat(first.path("list").get(0).path("id").asLong()).isEqualTo(anna.getId());
        assertThat(first.path("list").get(1).path("id").asLong()).isEqualTo(ben.getId());
        JsonNode second = currentAccounts(rootToken, 200,
                Map.of("username", "fixture_page", "page", "2", "size", "2")).path("data");
        assertThat(second.path("total").asLong()).isEqualTo(3);
        assertThat(second.path("list").size()).isEqualTo(1);
        assertThat(second.path("list").get(0).path("id").asLong()).isEqualTo(cora.getId());
        assertThat(currentAccounts(rootToken, 200,
                Map.of("username", "fixture_page", "size", "-1")).path("data").path("list").size())
                .isEqualTo(3);

        assertThat(currentAccounts(rootToken, 200, Map.of("displayName", "北区")).path("data").path("list").size())
                .isEqualTo(2);
        JsonNode combined = currentAccounts(rootToken, 200, Map.of("username", "  page_ ",
                "displayName", "北区", "enabled", "true", "roleId", supportRole.getId().toString()))
                .path("data");
        assertThat(combined.path("total").asLong()).isEqualTo(1);
        assertThat(combined.path("list").get(0).path("id").asLong()).isEqualTo(anna.getId());
        assertThat(combined.path("list").get(0).has("passwordHash")).isFalse();
        assertThat(combined.path("list").get(0).has("sessionVersion")).isFalse();
        JsonNode disabled = currentAccounts(rootToken, 200, Map.of("enabled", "false",
                "roleId", supportRole.getId().toString())).path("data");
        assertThat(disabled.path("total").asLong()).isEqualTo(1);
        assertThat(disabled.path("list").get(0).path("id").asLong()).isEqualTo(ben.getId());
        assertThat(currentAccounts(rootToken, 200, Map.of("roleId", "999999")).path("data").path("total").asLong())
                .isZero();
        currentAccounts(rootToken, 400, Map.of("page", "0"));
        currentAccounts(rootToken, 400, Map.of("size", "0"));
        currentAccounts(rootToken, 400, Map.of("roleId", "0"));
        call("GET", "/system/accounts?keyword=fixture_page", rootToken, null, 200);
    }

    @Test
    void currentAccountPageRequiresLiveAccountReadPermission() throws Exception {
        currentAccounts(null, 401, Map.of());
        currentAccounts(PetTownToken.USER.createLoginSession("fixture-page-user"), 401, Map.of());
        var readerRole = service.createNamedRole(1L, "分页只读", null);
        var deniedRole = service.createNamedRole(1L, "分页无权", null);
        service.replaceRolePermissions(1L, readerRole.getId(), List.of("system:account:read"));
        service.replaceRolePermissions(1L, deniedRole.getId(), List.of("system:role:read"));
        var reader = service.createAdmin(1L, "fixture_page_reader", PASSWORD, "13800000034", null, readerRole.getId());
        service.createAdmin(1L, "fixture_page_denied", PASSWORD, "13800000035", null, deniedRole.getId());
        String readerToken = passwordLogin("fixture_page_reader");
        String deniedToken = passwordLogin("fixture_page_denied");
        currentAccounts(readerToken, 200, Map.of());
        currentAccounts(deniedToken, 403, Map.of());
        service.replaceRolePermissions(1L, readerRole.getId(), List.of());
        currentAccounts(readerToken, 403, Map.of());
        service.setAccountEnabled(1L, reader.getId(), false);
        currentAccounts(readerToken, 401, Map.of());
        service.setAccountEnabled(1L, reader.getId(), true);
        currentAccounts(readerToken, 401, Map.of());

        service.createAccount(1L, "fixture_page_initial", "初始账号", INITIAL_PASSWORD, "13800000036");
        service.generateLoginCode("fixture_page_initial");
        String key = "pettown:verification-code:admin_login:13800000036";
        String initialToken = call("POST", "/auth/login", null,
                Map.of("username", "fixture_page_initial", "password", INITIAL_PASSWORD, "smsCode", codes.get(key)), 200)
                .path("data").path("token").asText();
        currentAccounts(initialToken, 403, Map.of());
        call("GET", "/system/accounts", rootToken, null, 200);
    }

    @Test
    void v1AccountCreationAssignsRoleAndRequiresFirstPasswordChange() throws Exception {
        var role = service.createNamedRole(1L, "新建账号只读角色", null);
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        JsonNode created = createV1Account(rootToken, Map.of(
                "username", "NewAdmin85", "displayName", "新管理员", "phone", "13800000041",
                "password", PASSWORD, "roleId", role.getId()), 200).path("data");
        assertThat(created.path("username").asText()).isEqualTo("NewAdmin85");
        assertThat(created.path("displayName").asText()).isEqualTo("新管理员");
        assertThat(created.path("roleId").asLong()).isEqualTo(role.getId());
        assertThat(created.path("passwordChangeRequired").asBoolean()).isTrue();
        assertThat(created.has("passwordHash")).isFalse();
        assertThat(created.has("sessionVersion")).isFalse();

        String firstToken = passwordLogin("NewAdmin85");
        assertThat(currentAuth("/me", firstToken, 200).path("data").path("account")
                .path("passwordChangeRequired").asBoolean()).isTrue();
        currentAuth("/menus", firstToken, 403);
        currentAccounts(firstToken, 403, Map.of());
        call("PUT", "/auth/password", firstToken,
                Map.of("oldPassword", PASSWORD, "newPassword", "Fixture-New-456!"), 200);
        currentAuth("/me", firstToken, 401);

        service.generateLoginCode("NewAdmin85");
        String key = "pettown:verification-code:admin_login:13800000041";
        String freshToken = call("POST", "/auth/login", null,
                Map.of("username", "NewAdmin85", "password", "Fixture-New-456!", "smsCode", codes.get(key)), 200)
                .path("data").path("token").asText();
        assertThat(currentAuth("/me", freshToken, 200).path("data").path("account")
                .path("passwordChangeRequired").asBoolean()).isFalse();
        currentAccounts(freshToken, 200, Map.of());
    }

    @Test
    void v1AccountCreationRejectsDuplicatesInvalidInputsAndPrivilegeEscalation() throws Exception {
        Map<String, Object> request = Map.of("username", "DupAdmin85", "displayName", "重复账号",
                "phone", "13800000042", "password", PASSWORD);
        createV1Account(rootToken, request, 200);
        JsonNode duplicate = createV1Account(rootToken, request, 409);
        assertThat(duplicate.path("code").asText()).isEqualTo("4091");
        assertThat(duplicate.path("msg").asText()).isEqualTo("管理员账号已存在");
        createV1Account(rootToken, Map.of("username", "abc", "displayName", "非法账号", "password", PASSWORD), 400);
        createV1Account(rootToken, Map.of("username", "invalid_name", "displayName", "非法账号", "password", PASSWORD), 400);
        createV1Account(rootToken, Map.of("username", "WeakAdmin85", "displayName", "弱密码", "password", "Onlyletters123"), 400);
        createV1Account(rootToken, Map.of("username", "PhoneAdmin85", "displayName", "坏手机号",
                "password", PASSWORD, "phone", "123"), 400);
        createV1Account(rootToken, Map.of("username", "RoleAdmin85", "displayName", "坏角色",
                "password", PASSWORD, "roleId", 0), 400);
        createV1Account(rootToken, Map.of("username", "RoleAdmin85", "displayName", "不存在角色",
                "password", PASSWORD, "roleId", 999999), 404);
        createV1Account(null, request, 401);
        createV1Account(PetTownToken.USER.createLoginSession("fixture-create-user"), request, 401);

        var writerRole = service.createNamedRole(1L, "只能创建账号", null);
        var superiorRole = service.createNamedRole(1L, "不能授予的角色", null);
        service.replaceRolePermissions(1L, writerRole.getId(), List.of("system:account:create"));
        service.replaceRolePermissions(1L, superiorRole.getId(), List.of("system:role:read"));
        service.createAdmin(1L, "fixture_create_writer", PASSWORD, "13800000043", null, writerRole.getId());
        service.createAdmin(1L, "fixture_create_denied", PASSWORD, "13800000044", null, superiorRole.getId());
        String writerToken = passwordLogin("fixture_create_writer");
        String deniedToken = passwordLogin("fixture_create_denied");
        createV1Account(deniedToken, Map.of("username", "DeniedAdmin85", "displayName", "无权创建",
                "password", PASSWORD), 403);
        createV1Account(writerToken, Map.of("username", "EscalateAdmin85", "displayName", "越权角色",
                "password", PASSWORD, "roleId", superiorRole.getId()), 400);
        assertThat(accounts.selectOne(AdminAccount::getUsername, "EscalateAdmin85")).isNull();
        call("POST", "/system/accounts", rootToken,
                Map.of("username", "fixture_old_create", "displayName", "旧接口", "password", PASSWORD), 200);
    }

    @Test
    void v1AccountStatusRevokesAllSessionsAndBlocksSelfAndSuperAdmin() throws Exception {
        var staff = service.createAdmin(1L, "fixture_status_v1", PASSWORD, "13800000051", null, null);
        String first = passwordLogin("fixture_status_v1");
        String second = passwordLogin("fixture_status_v1");
        long version = staff.getSessionVersion();

        currentAccountMutation("PUT", "/" + staff.getId() + "/status", rootToken,
                Map.of("enabled", false), 200);
        assertThat(service.getAccount(staff.getId()).getEnabled()).isFalse();
        assertThat(service.getAccount(staff.getId()).getSessionVersion()).isGreaterThan(version);
        currentAuth("/me", first, 401);
        currentAuth("/me", second, 401);
        assertThatThrownBy(() -> service.generateLoginCode("fixture_status_v1"))
                .isInstanceOf(com.pettown.common.exception.BusinessException.class);

        currentAccountMutation("PUT", "/" + staff.getId() + "/status", rootToken,
                Map.of("enabled", true), 200);
        currentAuth("/me", first, 401);
        currentAuth("/me", second, 401);
        String fresh = passwordLogin("fixture_status_v1");
        currentAuth("/me", fresh, 200);
        currentAccountMutation("PUT", "/1/status", rootToken, Map.of("enabled", false), 400);
        currentAccountMutation("DELETE", "/1", rootToken, null, 400);
        currentAccountMutation("PUT", "/999999/status", rootToken, Map.of("enabled", false), 404);
        currentAccountMutation("PUT", "/" + staff.getId() + "/status", rootToken, Map.of(), 400);
        call("GET", "/system/accounts/" + staff.getId(), rootToken, null, 200);
    }

    @Test
    void v1AccountDeleteIsSoftAndRequiresSpecificPermissions() throws Exception {
        var readRole = service.createNamedRole(1L, "删除测试无权角色", null);
        service.replaceRolePermissions(1L, readRole.getId(), List.of("system:account:read"));
        var staff = service.createAdmin(1L, "fixture_soft_delete", PASSWORD, "13800000052", null, readRole.getId());
        service.createAdmin(1L, "fixture_status_denied", PASSWORD, "13800000053", null, readRole.getId());
        String targetToken = passwordLogin("fixture_soft_delete");
        String deniedToken = passwordLogin("fixture_status_denied");
        currentAccountMutation("PUT", "/" + staff.getId() + "/status", deniedToken,
                Map.of("enabled", false), 403);
        currentAccountMutation("DELETE", "/" + staff.getId(), deniedToken, null, 403);
        currentAccountMutation("DELETE", "/" + staff.getId(), null, null, 401);
        currentAccountMutation("DELETE", "/" + staff.getId(),
                PetTownToken.USER.createLoginSession("fixture-delete-user"), null, 401);

        var operatorRole = service.createNamedRole(1L, "启停删除操作角色", null);
        service.replaceRolePermissions(1L, operatorRole.getId(),
                List.of("system:account:status", "system:account:delete"));
        var operator = service.createAdmin(1L, "fixture_self_operator", PASSWORD, "13800000054", null,
                operatorRole.getId());
        String operatorToken = passwordLogin("fixture_self_operator");
        currentAccountMutation("PUT", "/" + operator.getId() + "/status", operatorToken,
                Map.of("enabled", false), 400);
        currentAccountMutation("DELETE", "/" + operator.getId(), operatorToken, null, 400);
        currentAccountMutation("PUT", "/1/status", operatorToken, Map.of("enabled", false), 400);
        currentAccountMutation("DELETE", "/1", operatorToken, null, 400);

        currentAccountMutation("DELETE", "/" + staff.getId(), rootToken, null, 200);
        assertThat(jdbc.queryForObject("SELECT deleted FROM admin_account WHERE id=?", Integer.class, staff.getId()))
                .isEqualTo(1);
        assertThat(service.findAccount(staff.getId())).isEmpty();
        assertThat(service.getRole(readRole.getId())).isNotNull();
        currentAuth("/me", targetToken, 401);
        assertThatThrownBy(() -> service.generateLoginCode("fixture_soft_delete"))
                .isInstanceOf(com.pettown.common.exception.BusinessException.class);
        assertThat(currentAccounts(rootToken, 200, Map.of("username", "fixture_soft_delete"))
                .path("data").path("total").asLong()).isZero();
        call("GET", "/system/accounts/" + staff.getId(), rootToken, null, 404);
        currentAccountMutation("DELETE", "/" + staff.getId(), rootToken, null, 404);
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
        assertThat(jdbc.queryForObject("SELECT deleted FROM admin_account WHERE id=?", Integer.class, staff.getId()))
                .isEqualTo(1);
        assertThat(service.getRole(role.getId())).isNotNull();
        assertThat(service.rolePermissions(role.getId())).containsExactly("system:account:read");
        assertThat(service.getAccount(other.getId()).getRoleId()).isEqualTo(role.getId());
        call("GET", "/auth/me", tokenA, null, 401);
        call("GET", "/auth/me", tokenB, null, 401);
        call("DELETE", "/system/accounts/" + staff.getId(), rootToken, null, 404);
    }

    @Test
    void deletedAccountsAreRejectedEvenWhenRemovedOutsideTheHttpEndpoint() throws Exception {
        var staff = service.createAdmin(1L, "fixture_removed", PASSWORD, "13000000000", null, null);
        String token = passwordLogin("fixture_removed");
        accounts.deleteById(staff.getId());
        call("GET", "/auth/me", token, null, 401);
    }

    @Test
    void deletingARoleKeepsAccountsAndClearsOnlyTheirRoleReference() throws Exception {
        var role = service.createNamedRole(1L, "待删除角色", null);
        service.replaceRolePermissions(1L, role.getId(), List.of("system:account:read"));
        var staff = service.createAdmin(1L, "fixture_role_keep", PASSWORD, "13900000001", null, role.getId());
        String token = passwordLogin("fixture_role_keep");
        call("DELETE", "/system/roles/" + role.getId(), rootToken, null, 200);
        assertThat(service.getAccount(staff.getId()).getRoleId()).isNull();
        assertThat(authorities(token)).isEmpty();
        assertThat(call("GET", "/auth/me", token, null, 200).path("data").path("roles").size()).isZero();
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
        call("GET", "/auth/me", token, null, 401);
        service.setAccountEnabled(1L, staff.getId(), true);
        call("GET", "/auth/me", token, null, 401);
        String freshToken = passwordLogin("fixture_disabled");
        service.resetPassword(1L, staff.getId(), INITIAL_PASSWORD);
        assertThat(service.getAccount(staff.getId()).getPasswordHash()).isEqualTo(SaSecureUtil.sha256(INITIAL_PASSWORD));
        call("GET", "/auth/me", freshToken, null, 401);
        service.generateLoginCode("fixture_disabled");
        Object code = codes.get("pettown:verification-code:admin_login:13600000000");
        call("POST", "/auth/login", null,
                Map.of("username", "fixture_disabled", "password", PASSWORD, "smsCode", code), 400);
        JsonNode login = call("POST", "/auth/login", null,
                Map.of("username", "fixture_disabled", "password", INITIAL_PASSWORD, "smsCode", code), 200);
        assertThat(login.path("data").path("token").asText()).isNotBlank();
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
        return json.convertValue(call("GET", "/auth/me", token, null, 200).path("data").path("permissions"),
                json.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private JsonNode call(String method, String path, String token, Object body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/mng" + path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(method + " " + path + ": " + response.getContentAsString()).isEqualTo(expectedStatus);
        return json.readTree(response.getContentAsString());
    }

    private JsonNode currentAuth(String path, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get("/api/admin/auth" + path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("GET /api/admin/auth" + path + ": " + response.getContentAsString())
                .isEqualTo(expectedStatus);
        return json.readTree(response.getContentAsString());
    }

    private List<String> strings(JsonNode node) {
        return java.util.stream.StreamSupport.stream(node.spliterator(), false).map(JsonNode::asText).toList();
    }

    private JsonNode currentAccounts(String token, int expectedStatus, Map<String, String> query) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get("/api/admin/system/accounts");
        if (token != null) request.header("Authorization", "Bearer " + token);
        query.forEach(request::param);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("GET /api/admin/system/accounts: " + response.getContentAsString())
                .isEqualTo(expectedStatus);
        return json.readTree(response.getContentAsString());
    }

    private JsonNode createV1Account(String token, Object body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post("/api/admin/system/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("POST /api/admin/system/accounts: " + response.getContentAsString())
                .isEqualTo(expectedStatus);
        return json.readTree(response.getContentAsString());
    }

    private JsonNode currentAccountMutation(String method, String path, String token, Object body,
            int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/api/admin/system/accounts" + path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(method + " /api/admin/system/accounts" + path + ": "
                + response.getContentAsString()).isEqualTo(expectedStatus);
        return json.readTree(response.getContentAsString());
    }
}
