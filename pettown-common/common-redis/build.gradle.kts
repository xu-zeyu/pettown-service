dependencies {
    api(project(":common-core"))
    api(libs.spring.boot.starter.data.redis)
    api(libs.redisson)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
