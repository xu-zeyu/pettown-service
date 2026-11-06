package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class AdminMigrationTests {
    @Test
    void upgradesExistingSchemaWithoutLosingAccountsRolesOrPermissionBindings() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("admin-migration;MODE=MySQL").addScript("schema-before-admin-alignment.sql").build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.update("INSERT INTO admin_account (id, username, display_name, password_hash, created_time, updated_time) "
                    + "VALUES (1, 'fixture_root', '测试账号', 'fixture-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO admin_role (id, code, name, created_time, updated_time) "
                    + "VALUES (1, 'fixture_role', '测试角色', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO admin_role_permission (role_id, permission_code) VALUES (1, 'legacy:read')");
            String longPermissionCode = "legacy:" + "a".repeat(60);
            jdbc.update("INSERT INTO admin_role_permission (role_id, permission_code) VALUES (1, ?)", longPermissionCode);
            new ResourceDatabasePopulator(new ClassPathResource("admin-migration.sql")).execute(database);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account WHERE id=1", String.class)).isEqualTo("fixture-hash");
            assertThat(jdbc.queryForObject("SELECT deleted FROM admin_role WHERE id=1", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT deleted FROM admin_account WHERE id=1", Integer.class)).isZero();
            assertThat(jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", String.class))
                    .containsExactlyInAnyOrder("ADMIN_ACCOUNT", "ADMIN_ROLE", "ADMIN_PERMISSION", "ADMIN_ROLE_PERMISSION");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role_permission", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT name FROM admin_permission WHERE code=?", String.class, longPermissionCode)).hasSize(50);
            assertThat(jdbc.queryForList("SELECT code FROM admin_permission", String.class)).contains("SUB_ADMIN", "system:account:read", "legacy:read");
            jdbc.update("UPDATE admin_account SET phone='13800000000', avatar='fixture-avatar', last_login_time=CURRENT_TIMESTAMP WHERE id=1");
            jdbc.update("UPDATE admin_role SET description='fixture-description' WHERE id=1");
        } finally {
            database.shutdown();
        }
    }

    @Test
    void freshDeploymentSqlMatchesTheSchemaUsedByHttpTests() throws Exception {
        String deployment = new ClassPathResource("admin-deployment.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String testSchema = new ClassPathResource("schema.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // 管理端 HTTP 测试额外建 C 端用户表；管理员基础建表脚本仍只创建管理员表。
        String adminOnlyTestSchema = testSchema.replaceFirst(
                "(?s)CREATE TABLE IF NOT EXISTS user_account \\(.*?\\);\\n\\n", "");
        assertThat(deployment).isEqualTo(adminOnlyTestSchema);
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("admin-fresh;MODE=MySQL").addScript("admin-deployment.sql").build();
        try {
            assertThat(new JdbcTemplate(database).queryForList(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", String.class))
                    .containsExactlyInAnyOrder("ADMIN_ACCOUNT", "ADMIN_ROLE", "ADMIN_PERMISSION", "ADMIN_ROLE_PERMISSION");
            // CREATE/seed 部分可重复执行；不会创建任何默认账号。
            new ResourceDatabasePopulator(new ClassPathResource("admin-deployment.sql")).execute(database);
            var jdbc = new JdbcTemplate(database);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_NAME='ADMIN_ACCOUNT' AND COLUMN_NAME='DELETED'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_permission", Integer.class)).isEqualTo(16);
        } finally {
            database.shutdown();
        }
    }

    @Test
    void adminTestDataSetupIsIdempotentAndContainsNoRealPasswords() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("admin-seed;MODE=MySQL").addScript("admin-deployment.sql").build();
        try {
            // 种子过程可执行且幂等：重复执行两遍
            var populator = new ResourceDatabasePopulator(new ClassPathResource("admin-test-data.sql"));
            populator.execute(database);
            populator.execute(database);
            var jdbc = new JdbcTemplate(database);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role_permission", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT username FROM admin_account", String.class)).isEqualTo("fixture_readonly");
            // 不含真实密码：占位哈希原样保留，使用前必须重置
            assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account", String.class))
                    .isEqualTo("REPLACE-ME-PLACEHOLDER-HASH");
        } finally {
            database.shutdown();
        }
    }

    @Test
    void softDeleteIncrementalSqlPreservesExistingAccountsAndDefaultsToVisible() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("admin-soft-delete;MODE=MySQL").addScript("schema-before-admin-alignment.sql").build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.update("INSERT INTO admin_account (id, username, display_name, password_hash, created_time, updated_time) "
                    + "VALUES (1, 'fixture_existing', '存量账号', 'fixture-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            new ResourceDatabasePopulator(new ClassPathResource("admin-account-soft-delete.sql"))
                    .execute(database);
            assertThat(jdbc.queryForObject("SELECT deleted FROM admin_account WHERE id=1", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account WHERE id=1", String.class))
                    .isEqualTo("fixture-hash");
            jdbc.update("UPDATE admin_account SET deleted=1 WHERE id=1");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE id=1 AND deleted=1", Integer.class))
                    .isEqualTo(1);
        } finally {
            database.shutdown();
        }
    }
}
