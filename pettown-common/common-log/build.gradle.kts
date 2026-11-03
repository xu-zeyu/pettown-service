dependencies {
    api(project(":common-core"))
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
