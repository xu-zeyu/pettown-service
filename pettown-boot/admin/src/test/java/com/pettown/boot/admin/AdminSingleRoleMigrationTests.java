package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/** 验证四表迁移导出的 DDL 与孤立角色检查；MySQL 存储过程语法需单独原生验证。 */
class AdminSingleRoleMigrationTests {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private String migration;

    @BeforeEach
    void setup() throws Exception {
        migration = new ClassPathResource("admin-single-role.sql").getContentAsString(StandardCharsets.UTF_8);
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("single-role-" + UUID.randomUUID() + ";MODE=MySQL")
                .addScript("schema-before-admin-alignment.sql").build();
        jdbc = new JdbcTemplate(database);
        jdbc.update("INSERT INTO admin_account (id, username, display_name, password_hash, created_time, updated_time) "
                + "VALUES (1, 'fixture_root', '测试账号', 'fixture-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        for (int id = 1; id <= 2; id++) {
            jdbc.update("INSERT INTO admin_role (id, code, name, created_time, updated_time) "
                    + "VALUES (?, ?, '测试角色', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id, "fixture_" + id);
        }
        jdbc.update("INSERT INTO admin_role_permission (role_id, permission_code) VALUES (2, 'fixture:read')");
    }

    @AfterEach
    void close() { database.shutdown(); }

    @Test
    void addsNullableDirectRoleAndIndexWithoutChangingExistingFourTableData() {
        jdbc.execute(statement("add-role-column"));
        jdbc.execute(statement("add-role-index"));
        assertThat(roleConflicts()).isZero();
        assertThat(jdbc.queryForObject("SELECT role_id FROM admin_account WHERE id=1", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account WHERE id=1", String.class)).isEqualTo("fixture-hash");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT permission_code FROM admin_role_permission WHERE role_id=2", String.class))
                .isEqualTo("fixture:read");
        assertThat(jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", String.class))
                .containsExactlyInAnyOrder("ADMIN_ACCOUNT", "ADMIN_ROLE", "ADMIN_PERMISSION", "ADMIN_ROLE_PERMISSION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                + "WHERE TABLE_NAME='ADMIN_ACCOUNT' AND COLUMN_NAME='ROLE_ID'", Integer.class)).isEqualTo(1);
    }

    @Test
    void detectsMissingDirectRoleBeforeIndexCreation() {
        jdbc.execute(statement("add-role-column"));
        jdbc.update("UPDATE admin_account SET role_id=999 WHERE id=1");
        assertThat(roleConflicts()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT role_id FROM admin_account WHERE id=1", Long.class)).isEqualTo(999L);
        // 检查只报告问题，不自动清空或猜测角色。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role_permission", Integer.class)).isEqualTo(1);
    }

    @Test
    void preservesAlreadyAssignedDirectRoleAndAllowsUnassignedAccounts() {
        jdbc.execute(statement("add-role-column"));
        jdbc.update("UPDATE admin_account SET role_id=2 WHERE id=1");
        jdbc.update("INSERT INTO admin_account (username, display_name, password_hash, created_time, updated_time) "
                + "VALUES ('fixture_unassigned', '未分配角色', 'fixture-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        assertThat(roleConflicts()).isZero();
        jdbc.execute(statement("add-role-index"));
        assertThat(jdbc.queryForObject("SELECT role_id FROM admin_account WHERE id=1", Long.class)).isEqualTo(2L);
        jdbc.update("DELETE FROM admin_account WHERE id=1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role_permission", Integer.class)).isEqualTo(1);
    }

    private int roleConflicts() {
        return jdbc.queryForObject(statement("existing-role-check").replace(" INTO migration_conflicts", ""), Integer.class);
    }

    private String statement(String name) {
        String start = "-- " + name + ":start";
        String end = "-- " + name + ":end";
        assertThat(migration).contains(start, end);
        return migration.substring(migration.indexOf(start) + start.length(), migration.indexOf(end)).trim();
    }
}
