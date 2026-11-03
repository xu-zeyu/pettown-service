dependencies {
    api(project(":common-core"))
    api(libs.spring.boot.starter.web)
    api(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
