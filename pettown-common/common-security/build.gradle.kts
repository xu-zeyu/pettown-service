dependencies {
    api(project(":common-core"))
    api(libs.sa.token.spring.boot3.starter)
    api(project(":common-web"))
    implementation("org.springframework.security:spring-security-crypto")
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
