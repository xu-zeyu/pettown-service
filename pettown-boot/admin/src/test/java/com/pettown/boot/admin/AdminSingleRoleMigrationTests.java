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

/** Exercises exported preflight/copy SQL; MySQL routine syntax requires separate native verification. */
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
    }

    @AfterEach
    void close() { database.shutdown(); }

    @Test
    void copiesSingleRoleAndKeepsExistingAccountAndRoleData() {
        jdbc.update("INSERT INTO admin_account_role (account_id, role_id) VALUES (1, 2)");
        assertThat(conflicts("multiple-role-check")).isZero();
        assertThat(conflicts("orphan-check")).isZero();
        jdbc.execute(statement("add-role-column"));
        assertThat(conflicts("existing-role-check")).isZero();
        assertThat(conflicts("role-conflict-check")).isZero();
        jdbc.execute(statement("copy-role"));
        assertThat(jdbc.queryForObject("SELECT role_id FROM admin_account WHERE id=1", Long.class)).isEqualTo(2L);
        jdbc.execute("DROP TABLE admin_account_role");
        jdbc.update("DELETE FROM admin_account WHERE id=1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role", Integer.class)).isEqualTo(2);
    }

    @Test
    void detectsMultipleRolesWithoutChoosingOneOrLosingBindings() {
        jdbc.update("INSERT INTO admin_account_role (account_id, role_id) VALUES (1, 1), (1, 2)");
        assertThat(conflicts("multiple-role-check")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account_role", Integer.class)).isEqualTo(2);
    }

    @Test
    void detectsConflictingAndMissingDirectRoles() {
        jdbc.execute(statement("add-role-column"));
        jdbc.update("INSERT INTO admin_account_role (account_id, role_id) VALUES (1, 1)");
        jdbc.update("UPDATE admin_account SET role_id=2 WHERE id=1");
        assertThat(conflicts("role-conflict-check")).isEqualTo(1);
        jdbc.update("UPDATE admin_account SET role_id=999 WHERE id=1");
        assertThat(conflicts("existing-role-check")).isEqualTo(1);
    }

    @Test
    void detectsOrphanBindingsBeforeAnyDataIsCopied() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbc.update("INSERT INTO admin_account_role (account_id, role_id) VALUES (999, 1)");
        assertThat(conflicts("orphan-check")).isEqualTo(1);
    }

    @Test
    void alreadyMigratedDirectRoleIsPreserved() {
        jdbc.execute(statement("add-role-column"));
        jdbc.update("INSERT INTO admin_account_role (account_id, role_id) VALUES (1, 2)");
        jdbc.update("UPDATE admin_account SET role_id=2 WHERE id=1");
        assertThat(conflicts("role-conflict-check")).isZero();
        jdbc.execute(statement("copy-role"));
        assertThat(jdbc.queryForObject("SELECT role_id FROM admin_account WHERE id=1", Long.class)).isEqualTo(2L);
    }

    private int conflicts(String name) {
        return jdbc.queryForObject(statement(name).replace(" INTO migration_conflicts", ""), Integer.class);
    }

    private String statement(String name) {
        String start = "-- " + name + ":start";
        String end = "-- " + name + ":end";
        return migration.substring(migration.indexOf(start) + start.length(), migration.indexOf(end)).trim();
    }
}
