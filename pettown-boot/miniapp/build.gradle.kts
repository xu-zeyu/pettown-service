plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":pettown-boot"))
    implementation(project(":module-miniapp"))
    implementation(project(":common-web"))
    implementation(project(":common-log"))
    implementation(project(":common-mybatis"))
    implementation(project(":common-redis"))
    implementation(libs.sa.token.spring.boot3.starter)
    implementation(libs.springdoc.openapi.webmvc.api)
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":module-business"))
    testImplementation(project(":common-security"))
    testImplementation("org.springframework.security:spring-security-crypto")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = projectDir
}
