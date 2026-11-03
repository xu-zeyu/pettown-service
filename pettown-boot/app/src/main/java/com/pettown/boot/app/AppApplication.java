package com.pettown.boot.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.pettown.boot.EnvFileSupport;

@MapperScan("com.pettown.business.pet.mapper")
@SpringBootApplication(scanBasePackages = {"com.pettown.boot.app", "com.pettown.app", "com.pettown.business.pet", "com.pettown.common"})
public class AppApplication {
    public static void main(String[] args) {
        EnvFileSupport.useClientEnvWhenStartedFromRepository("app");
        SpringApplication.run(AppApplication.class, args);
    }
}
