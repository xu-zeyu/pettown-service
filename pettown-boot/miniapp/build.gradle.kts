plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":pettown-boot"))
    implementation(project(":module-miniapp"))
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
    testImplementation(project(":module-business"))
    testImplementation("org.springframework.security:spring-security-crypto")
    testRuntimeOnly(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = projectDir
}
