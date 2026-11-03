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
    implementation(libs.sa.token.spring.boot3.starter)
    implementation(libs.sa.token.redis.jackson)
    implementation(libs.springdoc.openapi.webmvc.api)
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":common-security"))
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
}
