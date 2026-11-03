package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            assertThat(entry.getKey()).startsWith("/api/admin/");
            for (JsonNode operation : entry.getValue()) {
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("tags").size()).isPositive();
                operations++;
            }
        }
        assertThat(operations).isEqualTo(45);
        assertThat(paths.path("/api/admin/login/sms").path("post").path("security").size()).isZero();
        assertThat(paths.path("/api/admin/public/captcha/login/{name}").path("post").path("security").size()).isZero();
        assertThat(paths.path("/api/admin/permission/batchSync").path("post").path("description").asText()).contains("SUB_ADMIN", "事务");
        assertThat(paths.path("/api/admin/auth/login").path("post").path("security").size()).isZero();
        assertThat(paths.path("/api/admin/system/accounts").path("get").path("security").get(0).has("bearerAuth")).isTrue();
    }

    @Test
    void documentsAdminFieldsAndPermissionsWithoutLeakingInternalFields() throws Exception {
        JsonNode document = document();
        JsonNode schemas = document.path("components").path("schemas");
        assertThat(schemas.path("CreateAccountRequest").path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        assertThat(schemas.path("CreateAccountRequest").path("properties").path("password").path("format").asText()).isEqualTo("password");
        assertThat(schemas.path("LoginResponse").path("properties").path("expiresIn").path("description").asText()).contains("秒");
        assertThat(schemas.path("AccountResponse").path("properties").has("roleId")).isTrue();
        assertThat(document.path("paths").path("/api/admin/system/accounts/{id}/role").path("put")
                .path("description").asText()).contains("最多一个角色", "null");
        assertThat(document.path("paths").path("/api/admin/system/accounts/{id}").path("delete")
                .path("description").asText()).contains("system:account:delete", "保留角色");
        assertThat(schemas.path("AccountResponse").path("properties").has("passwordHash")).isFalse();
        assertThat(schemas.path("AccountResponse").path("properties").has("sessionVersion")).isFalse();
        assertThat(schemas.path("AdminSmsLoginRequest").path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        assertThat(schemas.path("AdminInfoResponse").path("properties").has("passwordHash")).isFalse();
        for (String path : java.util.List.of("/api/admin/permission/page", "/api/admin/role/page")) {
            var parameters = document.path("paths").path(path).path("get").path("parameters");
            assertThat(parameters.findValuesAsText("name")).contains("page", "size").doesNotContain("sizeValid");
        }
        var list = document.path("paths").path("/api/admin/system/accounts").path("get");
        assertThat(list.path("description").asText()).contains("system:account:read", "size=-1");
        assertThat(list.path("parameters").findValuesAsText("name")).contains("page", "size", "keyword", "enabled").doesNotContain("sizeValid");
    }

    private JsonNode document() throws Exception {
        String content = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(content);
    }
}
