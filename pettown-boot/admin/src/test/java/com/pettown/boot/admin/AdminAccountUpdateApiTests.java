package com.pettown.boot.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pettown.admin.auth.controller.AdminAuthController;
import com.pettown.common.security.config.PetTownToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAccountUpdateApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private cn.dev33.satoken.stp.StpInterface stpInterface;
    private long superId;
    private long limitedId;
    private long targetId;
    private long roleAId;
    private long roleBId;
    private long updateRoleId;
    private String superToken;
    private String limitedToken;
    private String targetToken;

    @BeforeEach
    void setUp() {
        // 其他测试类的上下文会把各自的 StpInterface/SaTokenDao 注册到全局 SaManager，先恢复本上下文的实现，
        // 保证权限解析与本测试事务同连接（否则看不到未提交的夹具数据，权限校验间歇性 403）。
        cn.dev33.satoken.SaManager.setSaTokenDao(new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl());
        cn.dev33.satoken.SaManager.setStpInterface(stpInterface);
        superId = insertAccount("edit_super", true);
        limitedId = insertAccount("edit_limited", false);
        targetId = insertAccount("edit_target", false);
        roleAId = insertRole("EDIT_ROLE_A");
        roleBId = insertRole("EDIT_ROLE_B");
        updateRoleId = insertRole("EDIT_ROLE_UPDATE");
        insertRolePermission(roleAId, "biz:user:read");
        insertRolePermission(roleBId, "biz:user:status");
        insertRolePermission(updateRoleId, "system:account:update");
        jdbc.update("UPDATE admin_account SET role_id = ? WHERE id = ?", updateRoleId, limitedId);
        superToken = session(superId);
        limitedToken = session(limitedId);
        targetToken = session(targetId);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.ADMIN.logoutByTokenValue(superToken);
        PetTownToken.ADMIN.logoutByTokenValue(limitedToken);
        PetTownToken.ADMIN.logoutByTokenValue(targetToken);
    }

    @Test
    void editUpdatesDisplayNameAndPhone() throws Exception {
        mvc.perform(put("/api/admin/system/accounts/{id}", targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"新名字\",\"phone\":\"13900000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("新名字"))
                .andExpect(jsonPath("$.data.phone").value("13900000001"));
        jdbc.queryForObject("SELECT phone FROM admin_account WHERE id = ?", String.class, targetId);
    }

    @Test
    void roleChangeImmediatelyUpdatesTargetPermissions() throws Exception {
        mvc.perform(put("/api/admin/system/accounts/{id}", targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + roleAId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(roleAId));
        mvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("biz:user:read"));
        mvc.perform(put("/api/admin/system/accounts/{id}", targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + roleBId + "}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("biz:user:status"))
                .andExpect(jsonPath("$.data.permissions[?(@ == 'biz:user:read')]").doesNotExist());
    }

    @Test
    void rejectsChangingOwnRoleAndOwnProfile() throws Exception {
        mvc.perform(put("/api/admin/system/accounts/{id}", superId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + roleAId + "}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(put("/api/admin/system/accounts/{id}", superId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"自我修改\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void roleChangeRequiresAssignPermissionWhileProfileEditUsesUpdate() throws Exception {
        mvc.perform(put("/api/admin/system/accounts/{id}", targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + limitedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"受限改名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("受限改名"));
        mvc.perform(put("/api/admin/system/accounts/{id}", targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + limitedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + roleAId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownRoleOrTarget() throws Exception {
        mvc.perform(put("/api/admin/system/accounts/{id}", targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/system/accounts/{id}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"不存在\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnonymousAndEmptyUpdate() throws Exception {
        mvc.perform(put("/api/admin/system/accounts/{id}", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/system/accounts/{id}", targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String session(long accountId) {
        String token = PetTownToken.ADMIN.createLoginSession(accountId);
        PetTownToken.ADMIN.getTokenSessionByToken(token)
                .set(AdminAuthController.SESSION_VERSION, 0L);
        return token;
    }

    private long insertAccount(String username, boolean superAdmin) {
        jdbc.update("INSERT INTO admin_account(username,display_name,phone,password_hash,enabled,super_admin,"
                        + "password_change_required,session_version,created_time,updated_time) VALUES (?,?,?,?,TRUE,?,FALSE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                username, "编辑夹具", "13800000000", "fixture-hash", superAdmin);
        return jdbc.queryForObject("SELECT id FROM admin_account WHERE username = ?", Long.class, username);
    }

    private long insertRole(String code) {
        jdbc.update("INSERT INTO admin_role(code,name,enabled,created_time,updated_time) VALUES (?,?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                code, code);
        return jdbc.queryForObject("SELECT id FROM admin_role WHERE code = ?", Long.class, code);
    }

    private void insertRolePermission(long roleId, String code) {
        jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES (?,?)", roleId, code);
    }
}
