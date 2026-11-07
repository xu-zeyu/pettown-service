package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.admin.auth.controller.AdminAuthController;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.common.security.config.PetTownToken;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPermissionCatalogApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private cn.dev33.satoken.stp.StpInterface stpInterface;
    private long superId;
    private long guestId;
    private String superToken;
    private String guestToken;

    @BeforeEach
    void setUp() {
        // 其他测试类的上下文会把各自的 StpInterface/SaTokenDao 注册到全局 SaManager，先恢复本上下文的实现，
        // 保证权限解析与本测试事务同连接（否则看不到未提交的夹具数据，权限校验间歇性 403）。
        cn.dev33.satoken.SaManager.setSaTokenDao(new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl());
        cn.dev33.satoken.SaManager.setStpInterface(stpInterface);
        superId = insertAccount("perm_super", true, null);
        guestId = insertAccount("perm_guest", false, null);
        superToken = session(superId);
        guestToken = session(guestId);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.ADMIN.logoutByTokenValue(superToken);
        PetTownToken.ADMIN.logoutByTokenValue(guestToken);
    }

    @Test
    void catalogContainsEveryBuiltinCodeWithAlignedNames() throws Exception {
        JsonNode list = catalog(superToken);
        Set<String> codes = new HashSet<>();
        for (JsonNode item : list) {
            codes.add(item.path("code").asText());
        }
        assertThat(codes).containsAll(AdminPermissionCodes.builtinCodes());
        for (JsonNode item : list) {
            String code = item.path("code").asText();
            if (AdminPermissionCodes.builtinNames().containsKey(code)) {
                assertThat(item.path("title").asText()).isEqualTo(AdminPermissionCodes.builtinNames().get(code));
            }
        }
    }

    @Test
    void syncRestoresMissingRowsAndIsIdempotent() throws Exception {
        jdbc.update("INSERT INTO admin_permission(name,code) VALUES ('自定义点','custom:demo:read')");
        jdbc.update("DELETE FROM admin_permission WHERE code IN ('system:account:read','biz:user:read')");
        mvc.perform(post("/api/admin/system/permissions/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk());
        Integer afterFirst = count();
        assertThat(afterFirst).isEqualTo(17);
        mvc.perform(post("/api/admin/system/permissions/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk());
        assertThat(count()).isEqualTo(afterFirst);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM admin_permission WHERE code = 'system:account:read'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT name FROM admin_permission WHERE code = 'biz:user:read'", String.class))
                .isEqualTo("查看 C 端用户");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM admin_permission WHERE code = 'custom:demo:read'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void permissionChecksAreEnforced() throws Exception {
        mvc.perform(get("/api/admin/system/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/system/permissions/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnonymous() throws Exception {
        mvc.perform(get("/api/admin/system/permissions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/system/permissions/sync")).andExpect(status().isUnauthorized());
    }

    private JsonNode catalog(String token) throws Exception {
        String body = mvc.perform(get("/api/admin/system/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_permission", Integer.class);
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
                username, "权限夹具", "13800000000", "fixture-hash", superAdmin);
        return jdbc.queryForObject("SELECT id FROM admin_account WHERE username = ?", Long.class, username);
    }
}
