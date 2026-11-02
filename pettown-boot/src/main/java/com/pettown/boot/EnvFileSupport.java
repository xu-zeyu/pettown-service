package com.pettown.boot;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EnvFileSupport {
    private EnvFileSupport() {
    }

    public static void useClientEnvWhenStartedFromRepository(String client) {
        if (System.getProperty("PETTOWN_ENV_FILE") != null || System.getenv("PETTOWN_ENV_FILE") != null) {
            return;
        }
        Path workingDirectory = Path.of("").toAbsolutePath();
        if (Files.isRegularFile(workingDirectory.resolve("settings.gradle.kts"))) {
            System.setProperty("PETTOWN_ENV_FILE",
                    workingDirectory.resolve("pettown-boot").resolve(client).resolve(".env").toString());
        }
    }
}
