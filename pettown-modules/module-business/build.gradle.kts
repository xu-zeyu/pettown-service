dependencies {
    implementation(project(":common-core"))
    api(project(":common-mybatis"))
    implementation(project(":common-security"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
