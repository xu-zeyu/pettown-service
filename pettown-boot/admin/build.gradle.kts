plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":pettown-boot"))
    implementation(project(":module-admin"))
    implementation(project(":module-business"))
    implementation(project(":common-web"))
    implementation(project(":common-log"))
    implementation(project(":common-mybatis"))
    implementation(project(":common-redis"))
    implementation(project(":common-security"))
    implementation(libs.sa.token.redis.jackson)
    implementation(libs.springdoc.openapi.webmvc.api)
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.springframework.security:spring-security-crypto")
    testRuntimeOnly(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = projectDir
}

tasks.processTestResources {
    from(rootProject.file("docs/sql/admin.sql")) {
        rename { "admin-deployment.sql" }
    }
    from(rootProject.file("docs/sql/admin-single-role.sql")) {
        rename { "admin-single-role.sql" }
    }
    from(rootProject.file("docs/sql/admin-reference-alignment.sql")) {
        rename { "admin-migration.sql" }
    }
    from(rootProject.file("docs/sql/admin-account-soft-delete-v1.0.0-alter.sql")) {
        rename { "admin-account-soft-delete.sql" }
    }
    from(rootProject.file("docs/sql/admin-test-data.sql"))
}
