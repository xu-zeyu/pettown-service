dependencies {
    api(project(":common-core"))
    api(libs.mybatis.plus.boot.starter)
    api(libs.mybatis.plus.jsqlparser)
    api(libs.mybatis.plus.join.boot.starter)
    api("jakarta.validation:jakarta.validation-api")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.validation)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
