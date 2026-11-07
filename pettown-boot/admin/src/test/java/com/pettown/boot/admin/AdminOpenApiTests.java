package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminOpenApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private SaTokenDao tokenDao;

    @Test
    void exportsOnlyThisClientWithDocumentedOperationsAndCorrectAuthentication() throws Exception {
        JsonNode document = document();
        assertThat(document.path("info").path("title").asText()).isEqualTo("PetTown 管理后台接口");
        assertThat(document.path("info").path("version").asText()).isEqualTo("0.0.1");
        JsonNode scheme = document.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.has("bearerFormat")).isFalse();
        assertThat(document.path("security").size()).isZero();
        JsonNode paths = document.path("paths");
        int operations = 0;
        for (var entry : paths.properties()) {
            assertThat(entry.getKey().startsWith("/mng/")
                    || Set.of("/api/admin/auth/login", "/api/admin/auth/me", "/api/admin/auth/menus", "/api/admin/auth/change-password",
                            "/api/admin/system/accounts", "/api/admin/system/accounts/{id}",
                            "/api/admin/system/accounts/{id}/status", "/api/admin/system/accounts/{id}/reset-password",
                            "/api/admin/system/roles", "/api/admin/system/roles/{id}",
                            "/api/admin/users", "/api/admin/users/{id}", "/api/admin/users/{id}/status",
                            "/api/admin/verification/captcha")
                            .contains(entry.getKey())).isTrue();
            for (JsonNode operation : entry.getValue()) {
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("tags").size()).isPositive();
                operations++;
            }
        }
        assertThat(operations).isEqualTo(59);
        assertThat(paths.path("/mng/create").path("post").path("description").asText()).contains("SHA-256");
        assertThat(paths.path("/mng/permission/batchSync").path("post").path("description").asText()).contains("SUB_ADMIN", "事务");
        assertThat(paths.path("/mng/auth/login").path("post").path("security").size()).isZero();
        JsonNode verification = paths.path("/mng/auth/verification").path("post");
        assertThat(verification.path("security").size()).isZero();
        assertThat(verification.path("description").asText()).contains("只接受 ADMIN_LOGIN");
        assertThat(paths.path("/api/admin/verification/captcha").path("get").path("security").size()).isZero();
        assertThat(paths.path("/api/admin/auth/login").path("post").path("security").size()).isZero();
        assertThat(paths.path("/mng/system/accounts").path("get").path("security").get(0).has("bearerAuth")).isTrue();
        for (String path : Set.of("/api/admin/auth/me", "/api/admin/auth/menus")) {
            assertThat(paths.path(path).path("get").path("security").get(0).has("bearerAuth")).isTrue();
            assertThat(paths.path(path).size()).isEqualTo(1);
        }
        JsonNode accountPage = paths.path("/api/admin/system/accounts");
        assertThat(accountPage.size()).isEqualTo(2);
        assertThat(accountPage.path("get").path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(accountPage.path("get").path("description").asText()).contains("system:account:read");
        assertThat(accountPage.path("get").path("parameters").findValuesAsText("name"))
                .contains("page", "size", "username", "displayName", "enabled", "roleId")
                .doesNotContain("sizeValid");
        JsonNode createAccount = accountPage.path("post");
        assertThat(createAccount.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(createAccount.path("description").asText()).contains("system:account:create", "4091");
        assertThat(document.path("components").path("schemas").path("V1CreateAccountRequest")
                .path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        JsonNode statusAccount = paths.path("/api/admin/system/accounts/{id}/status").path("put");
        assertThat(statusAccount.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(statusAccount.path("description").asText()).contains("system:account:status");
        JsonNode deleteAccount = paths.path("/api/admin/system/accounts/{id}").path("delete");
        assertThat(deleteAccount.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(deleteAccount.path("description").asText()).contains("system:account:delete");
        JsonNode resetPassword = paths.path("/api/admin/system/accounts/{id}/reset-password").path("post");
        assertThat(resetPassword.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(resetPassword.path("description").asText()).contains("system:account:reset-password");
        assertThat(paths.path("/api/admin/auth/change-password").path("post")
                .path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(document.path("components").path("schemas").path("V1ResetPasswordRequest")
                .path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        assertThat(document.path("components").path("schemas").path("V1ChangePasswordRequest")
                .path("properties").path("newPassword").path("writeOnly").asBoolean()).isTrue();
        JsonNode rolePage = paths.path("/api/admin/system/roles");
        JsonNode roleItem = paths.path("/api/admin/system/roles/{id}");
        assertThat(rolePage.size()).isEqualTo(2);
        assertThat(roleItem.size()).isEqualTo(3);
        for (String method : Set.of("get", "post")) {
            assertThat(rolePage.path(method).path("security").get(0).has("bearerAuth")).isTrue();
        }
        for (String method : Set.of("get", "put", "delete")) {
            assertThat(roleItem.path(method).path("security").get(0).has("bearerAuth")).isTrue();
        }
        assertThat(rolePage.path("get").path("description").asText()).contains("system:role:read");
        assertThat(rolePage.path("post").path("description").asText()).contains("system:role:create");
        assertThat(roleItem.path("get").path("description").asText()).contains("system:role:read");
        assertThat(roleItem.path("put").path("description").asText()).contains("system:role:update");
        assertThat(roleItem.path("delete").path("description").asText()).contains("system:role:delete");
        for (String path : Set.of("/api/admin/users", "/api/admin/users/{id}")) {
            JsonNode operation = paths.path(path).path("get");
            assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
            assertThat(operation.path("description").asText()).contains("biz:user:read");
        }
        JsonNode userStatus = paths.path("/api/admin/users/{id}/status").path("put");
        assertThat(paths.path("/api/admin/users").path("get").path("parameters").findValuesAsText("name"))
                .contains("page", "size", "phone", "source").doesNotContain("sizeValid");
        assertThat(userStatus.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(userStatus.path("description").asText()).contains("biz:user:status");
        assertThat(document.path("components").path("schemas").path("AdminUserResponse")
                .path("properties").has("passwordHash")).isFalse();
    }

    @Test
    void documentsAdminFieldsAndPermissionsWithoutLeakingInternalFields() throws Exception {
        JsonNode document = document();
        JsonNode schemas = document.path("components").path("schemas");
        assertThat(schemas.path("CreateAccountRequest").path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        assertThat(schemas.path("CreateAccountRequest").path("properties").path("password").path("format").asText()).isEqualTo("password");
        assertThat(schemas.path("LoginResponse").path("properties").path("expiresIn").path("description").asText()).contains("秒");
        assertThat(schemas.path("AccountResponse").path("properties").has("roleId")).isTrue();
        assertThat(document.path("paths").path("/mng/system/accounts/{id}/role").path("put")
                .path("description").asText()).contains("最多一个角色", "null");
        assertThat(document.path("paths").path("/mng/system/accounts/{id}").path("delete")
                .path("description").asText()).contains("system:account:delete", "保留角色");
        assertThat(schemas.path("AccountResponse").path("properties").has("passwordHash")).isFalse();
        assertThat(schemas.path("AccountResponse").path("properties").has("sessionVersion")).isFalse();
        assertThat(schemas.path("LoginRequest").path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        assertThat(schemas.path("VerificationCodeRequest").path("properties").path("type").path("enum"))
                .extracting(JsonNode::asText).containsExactly("ADMIN_LOGIN");
        for (String path : java.util.List.of("/mng/permission/page", "/mng/role/page")) {
            var parameters = document.path("paths").path(path).path("get").path("parameters");
            assertThat(parameters.findValuesAsText("name")).contains("page", "size").doesNotContain("sizeValid");
        }
        var list = document.path("paths").path("/mng/system/accounts").path("get");
        assertThat(list.path("description").asText()).contains("system:account:read", "size=-1");
        assertThat(list.path("parameters").findValuesAsText("name")).contains("page", "size", "keyword", "enabled").doesNotContain("sizeValid");
    }

    private JsonNode document() throws Exception {
        String content = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(content);
    }
}
