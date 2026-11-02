plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":pettown-boot"))
    implementation(project(":module-admin"))
    implementation(project(":common-web"))
    implementation(project(":common-log"))
    implementation(project(":common-mybatis"))
    implementation(project(":common-redis"))
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = projectDir
}
