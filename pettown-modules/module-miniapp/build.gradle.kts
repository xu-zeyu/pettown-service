dependencies {
    implementation(project(":module-business"))
    implementation(project(":common-web"))
    implementation(project(":common-security"))
    implementation(project(":common-redis"))
    implementation(project(":common-file"))
    implementation(libs.swagger.annotations.jakarta)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
