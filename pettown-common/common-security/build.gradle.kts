dependencies {
    api(project(":common-core"))
    api(libs.sa.token.spring.boot3.starter)
    api(project(":common-web"))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
