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
class AdminRolePermissionAssignApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private cn.dev33.satoken.stp.StpInterface stpInterface;
    private long superId;
    private long guestId;
    private long targetId;
    private long roleId;
    private String superToken;
    private String guestToken;
    private String targetToken;

    @BeforeEach
    void setUp() {
        // 其他测试类的上下文会把各自的 StpInterface/SaTokenDao 注册到全局 SaManager，先恢复本上下文的实现，
        // 保证权限解析与本测试事务同连接（否则看不到未提交的夹具数据，权限校验间歇性 403）。
        cn.dev33.satoken.SaManager.setSaTokenDao(new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl());
        cn.dev33.satoken.SaManager.setStpInterface(stpInterface);
        superId = insertAccount("rpa_super", true, null);
        guestId = insertAccount("rpa_guest", false, null);
        targetId = insertAccount("rpa_target", false, null);
        roleId = insertRole("RPA_ROLE");
        jdbc.update("UPDATE admin_account SET role_id = ? WHERE id = ?", roleId, targetId);
        superToken = session(superId);
        guestToken = session(guestId);
        targetToken = session(targetId);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.ADMIN.logoutByTokenValue(superToken);
        PetTownToken.ADMIN.logoutByTokenValue(guestToken);
        PetTownToken.ADMIN.logoutByTokenValue(targetToken);
    }

    @Test
    void assignmentChangesTargetPermissionsImmediately() throws Exception {
        putPermissions(superToken, "[\"biz:user:read\"]");
        mvc.perform(get("/api/admin/system/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("biz:user:read"));
        mvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("biz:user:read"));
        putPermissions(superToken, "[\"biz:user:status\"]");
        mvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("biz:user:status"))
                .andExpect(jsonPath("$.data.permissions[?(@ == 'biz:user:read')]").doesNotExist());
    }

    @Test
    void emptyListClearsTheGrant() throws Exception {
        putPermissions(superToken, "[\"biz:user:read\",\"biz:user:status\"]");
        putPermissions(superToken, "[]");
        mvc.perform(get("/api/admin/system/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsUnknownPermissionCodeWithoutPartialChange() throws Exception {
        putPermissions(superToken, "[\"biz:user:read\"]");
        mvc.perform(put("/api/admin/system/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"biz:user:read\",\"no:such:code\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/system/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("biz:user:read"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void permissionAndExistenceChecksAreEnforced() throws Exception {
        mvc.perform(put("/api/admin/system/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"biz:user:read\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/system/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/system/roles/{id}/permissions", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnonymous() throws Exception {
        mvc.perform(get("/api/admin/system/roles/{id}/permissions", roleId))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/system/roles/{id}/permissions", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    private void putPermissions(String token, String json) throws Exception {
        mvc.perform(put("/api/admin/system/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":" + json + "}"))
                .andExpect(status().isOk());
    }

    private String session(long accountId) {
        String token = PetTownToken.ADMIN.createLoginSession(accountId);
        PetTownToken.ADMIN.getTokenSessionByToken(token)
                .set(AdminAuthController.SESSION_VERSION, 0L);
        return token;
    }

    private long insertAccount(String username, boolean superAdmin, Long roleId) {
        jdbc.update("INSERT INTO admin_account(username,display_name,phone,password_hash,enabled,super_admin,"
                        + "password_change_required,session_version,created_time,updated_time) VALUES (?,?,?,?,TRUE,?,FALSE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                username, "角色权限夹具", "13800000000", "fixture-hash", superAdmin);
        return jdbc.queryForObject("SELECT id FROM admin_account WHERE username = ?", Long.class, username);
    }

    private long insertRole(String code) {
        jdbc.update("INSERT INTO admin_role(code,name,enabled,created_time,updated_time) VALUES (?,?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                code, code);
        return jdbc.queryForObject("SELECT id FROM admin_role WHERE code = ?", Long.class, code);
    }
}
