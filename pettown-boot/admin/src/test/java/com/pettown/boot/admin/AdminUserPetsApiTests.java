package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.admin.auth.controller.AdminAuthController;
import com.pettown.common.security.config.PetTownToken;
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
class AdminUserPetsApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private cn.dev33.satoken.stp.StpInterface stpInterface;
    private final ObjectMapper mapper = new ObjectMapper();
    private String rootToken;
    private String readerToken;
    private String statusOnlyToken;

    @BeforeEach
    void setUp() {
        // 其他测试类的上下文会把各自的 StpInterface/SaTokenDao 注册到全局 SaManager，先恢复本上下文的实现，
        // 保证权限解析与本测试事务同连接（否则看不到未提交的夹具数据，权限校验间歇性 403）。
        cn.dev33.satoken.SaManager.setSaTokenDao(new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl());
        cn.dev33.satoken.SaManager.setStpInterface(stpInterface);
        long rootId = insertAccount("pets_root", true);
        long readerId = insertAccount("pets_reader", false);
        long statusOnlyId = insertAccount("pets_status", false);
        long readerRoleId = insertRole("PETS_ROLE_READ");
        long statusRoleId = insertRole("PETS_ROLE_STATUS");
        insertRolePermission(readerRoleId, "biz:user:read");
        insertRolePermission(statusRoleId, "biz:user:status");
        jdbc.update("UPDATE admin_account SET role_id = ? WHERE id = ?", readerRoleId, readerId);
        jdbc.update("UPDATE admin_account SET role_id = ? WHERE id = ?", statusRoleId, statusOnlyId);
        rootToken = session(rootId);
        readerToken = session(readerId);
        statusOnlyToken = session(statusOnlyId);
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source,status,created_at) "
                + "VALUES ('pets_user_a','13800138201','hash-secret-a','MINI_PROGRAM','ACTIVE',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source,status,created_at) "
                + "VALUES ('pets_user_b','13800138202','hash-secret-b','IOS','ACTIVE',CURRENT_TIMESTAMP)");
    }

    @AfterEach
    void tearDown() {
        PetTownToken.ADMIN.logoutByTokenValue(rootToken);
        PetTownToken.ADMIN.logoutByTokenValue(readerToken);
        PetTownToken.ADMIN.logoutByTokenValue(statusOnlyToken);
    }

    @Test
    void returnsPetListAndCountConsistentWithMobile() throws Exception {
        insertPet("pets_user_a", "豆豆", "CAT", 100, false,
                "英短", "FEMALE", "2024-01-05", "4.20", true, "https://cdn.example/x.png");
        insertPet("pets_user_a", "毛毛", "DOG", 101, false,
                null, "MALE", "2023-06-01", null, false, null);
        insertPet("pets_user_b", "别人的猫", "CAT", 102, false,
                null, null, null, null, null, null);

        JsonNode data = pets("pets_user_a", rootToken);
        assertThat(data.path("userId").asText()).isEqualTo("pets_user_a");
        assertThat(data.path("petCount").asLong()).isEqualTo(2);
        JsonNode list = data.path("pets");
        assertThat(list.size()).isEqualTo(2);
        // 与移动端一致：createdTime 倒序、id 倒序
        assertThat(list.get(0).path("name").asText()).isEqualTo("毛毛");
        assertThat(list.get(1).path("name").asText()).isEqualTo("豆豆");
        JsonNode full = list.get(1);
        assertThat(full.path("id").asLong()).isEqualTo(100L);
        assertThat(full.path("ownerId").asText()).isEqualTo("pets_user_a");
        assertThat(full.path("species").asText()).isEqualTo("CAT");
        assertThat(full.path("breed").asText()).isEqualTo("英短");
        assertThat(full.path("gender").asText()).isEqualTo("FEMALE");
        assertThat(full.path("birthDate").asText()).isEqualTo("2024-01-05");
        assertThat(full.path("weightKg").asDouble()).isEqualTo(4.20);
        assertThat(full.path("sterilized").asBoolean()).isTrue();
        assertThat(full.path("avatarUrl").asText()).isEqualTo("https://cdn.example/x.png");
        assertThat(full.path("createdTime").asText()).isNotBlank();
        assertThat(full.path("updatedTime").asText()).isNotBlank();
        // 不含敏感字段与健康记录明细
        String body = data.toString();
        assertThat(body).doesNotContain("passwordHash", "hash-secret-a", "healthRecord", "petHealth");
    }

    @Test
    void excludesSoftDeletedAndOtherUsersPets() throws Exception {
        insertPet("pets_user_a", "健在", "CAT", 110, false,
                null, null, null, null, null, null);
        insertPet("pets_user_a", "已删", "DOG", 111, true,
                null, null, null, null, null, null);
        insertPet("pets_user_b", "他人宠物", "CAT", 112, false,
                null, null, null, null, null, null);

        JsonNode data = pets("pets_user_a", rootToken);
        assertThat(data.path("petCount").asLong()).isEqualTo(1);
        assertThat(data.path("pets").size()).isEqualTo(1);
        assertThat(data.path("pets").get(0).path("name").asText()).isEqualTo("健在");
    }

    @Test
    void emptyPetsReturnEmptyListNotError() throws Exception {
        JsonNode data = pets("pets_user_b", rootToken);
        assertThat(data.path("petCount").asLong()).isZero();
        assertThat(data.path("pets").size()).isZero();
    }

    @Test
    void unknownUserReturns404() throws Exception {
        mvc.perform(get("/api/admin/users/{id}/pets", "missing")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rootToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresUserReadPermission() throws Exception {
        insertPet("pets_user_a", "权限猫", "CAT", 120, false,
                null, null, null, null, null, null);
        // biz:user:read 角色可读
        JsonNode data = pets("pets_user_a", readerToken);
        assertThat(data.path("petCount").asLong()).isEqualTo(1);
        // 仅 biz:user:status 角色被拒
        mvc.perform(get("/api/admin/users/{id}/pets", "pets_user_a")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + statusOnlyToken))
                .andExpect(status().isForbidden());
        // 匿名与 C 端 token 均 401
        mvc.perform(get("/api/admin/users/{id}/pets", "pets_user_a"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/{id}/pets", "pets_user_a")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + PetTownToken.USER.createLoginSession("pets_user_a")))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode pets(String userId, String token) throws Exception {
        String body = mvc.perform(get("/api/admin/users/{id}/pets", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private void insertPet(String ownerId, String name, String species, long id, boolean deleted,
            String breed, String gender, String birthDate, String weightKg, Boolean sterilized, String avatarUrl) {
        // id 越大创建时间越晚（与 createdTime 倒序、id 倒序的移动端口径对齐）
        String createdTime = String.format("2026-11-0%d 10:00:00", id - 98);
        jdbc.update("INSERT INTO pet(id,name,species,owner_id,breed,gender,birth_date,weight_kg,sterilized,"
                        + "avatar_url,deleted,created_time,updated_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, name, species, ownerId, breed, gender, birthDate, weightKg, sterilized,
                avatarUrl, deleted ? 1 : 0, createdTime, createdTime);
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
                username, "宠物查看夹具", "13800000000", "fixture-hash", superAdmin);
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
