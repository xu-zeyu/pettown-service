package com.pettown.boot.miniapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.pettown.boot.EnvFileSupport;

@SpringBootApplication(scanBasePackages = {"com.pettown.boot.miniapp", "com.pettown.miniapp", "com.pettown.business", "com.pettown.common"})
public class MiniappApplication {
    public static void main(String[] args) {
        EnvFileSupport.useClientEnvWhenStartedFromRepository("miniapp");
        SpringApplication.run(MiniappApplication.class, args);
    }
}
