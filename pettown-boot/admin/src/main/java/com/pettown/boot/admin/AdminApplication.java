package com.pettown.boot.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.pettown.boot.EnvFileSupport;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan({"com.pettown.business.admin.mapper", "com.pettown.business.user.mapper", "com.pettown.business.pet.mapper"})
@SpringBootApplication(scanBasePackages = {"com.pettown.boot.admin", "com.pettown.admin", "com.pettown.business.admin", "com.pettown.business.verification", "com.pettown.common"})
public class AdminApplication {
    public static void main(String[] args) {
        EnvFileSupport.useClientEnvWhenStartedFromRepository("admin");
        SpringApplication.run(AdminApplication.class, args);
    }
}
