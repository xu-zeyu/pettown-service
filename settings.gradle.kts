rootProject.name = "pettown-server"

listOf("common-core", "common-web", "common-mybatis", "common-redis", "common-security", "common-file", "common-log")
    .forEach { module ->
        include(":$module")
        project(":$module").projectDir = file("pettown-common/$module")
    }

listOf("module-business", "module-app", "module-miniapp", "module-admin")
    .forEach { module ->
        include(":$module")
        project(":$module").projectDir = file("pettown-modules/$module")
    }

include(":pettown-boot")
