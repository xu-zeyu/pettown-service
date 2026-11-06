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
                    || Set.of("/api/admin/auth/me", "/api/admin/auth/menus", "/api/admin/system/accounts")
                            .contains(entry.getKey())).isTrue();
            for (JsonNode operation : entry.getValue()) {
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("tags").size()).isPositive();
                operations++;
            }
        }
        assertThat(operations).isEqualTo(44);
        assertThat(paths.path("/mng/create").path("post").path("description").asText()).contains("SHA-256");
        assertThat(paths.path("/mng/permission/batchSync").path("post").path("description").asText()).contains("SUB_ADMIN", "事务");
        assertThat(paths.path("/mng/auth/login").path("post").path("security").size()).isZero();
        JsonNode verification = paths.path("/mng/auth/verification").path("post");
        assertThat(verification.path("security").size()).isZero();
        assertThat(verification.path("description").asText()).contains("只接受 ADMIN_LOGIN");
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
