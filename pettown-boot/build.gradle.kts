plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common-web"))
    implementation(project(":common-log"))
    implementation(project(":module-app"))
    implementation(project(":module-miniapp"))
    implementation(project(":module-admin"))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
