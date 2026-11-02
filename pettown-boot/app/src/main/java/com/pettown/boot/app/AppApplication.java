package com.pettown.boot.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.pettown.boot.EnvFileSupport;

@SpringBootApplication(scanBasePackages = {"com.pettown.app", "com.pettown.business.pet", "com.pettown.common"})
public class AppApplication {
    public static void main(String[] args) {
        EnvFileSupport.useClientEnvWhenStartedFromRepository("app");
        SpringApplication.run(AppApplication.class, args);
    }
}
