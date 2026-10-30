plugins {
    alias(libs.plugins.spring.boot) apply false
}

val springBootBom = libs.spring.boot.bom.get()

allprojects {
    group = "com.pettown"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    dependencies {
        add("implementation", platform(springBootBom))
        add("testImplementation", platform(springBootBom))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }
}
