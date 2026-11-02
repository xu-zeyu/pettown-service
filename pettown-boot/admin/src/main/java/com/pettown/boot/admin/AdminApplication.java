package com.pettown.boot.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.pettown.boot.EnvFileSupport;

@SpringBootApplication(scanBasePackages = {"com.pettown.admin", "com.pettown.common"})
public class AdminApplication {
    public static void main(String[] args) {
        EnvFileSupport.useClientEnvWhenStartedFromRepository("admin");
        SpringApplication.run(AdminApplication.class, args);
    }
}
