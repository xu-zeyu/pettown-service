package com.pettown.boot.miniapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.pettown.boot.EnvFileSupport;

@MapperScan({"com.pettown.business.user.mapper", "com.pettown.business.pet.mapper"})
@SpringBootApplication(scanBasePackages = {"com.pettown.boot.miniapp", "com.pettown.miniapp", "com.pettown.business.user", "com.pettown.business.verification", "com.pettown.common"})
public class MiniappApplication {
    public static void main(String[] args) {
        EnvFileSupport.useClientEnvWhenStartedFromRepository("miniapp");
        SpringApplication.run(MiniappApplication.class, args);
    }
}
