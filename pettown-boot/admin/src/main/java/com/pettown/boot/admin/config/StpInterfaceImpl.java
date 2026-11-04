package com.pettown.boot.admin.config;

import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jinHan.shop.core.admin.domain.mapper.AdminMapper;
import com.jinHan.shop.core.admin.domain.model.Admin;
import com.jinHan.shop.core.admin.domain.model.AdminPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 类名: StpInterfaceImpl
 * 描述: 实现 Sa-Token 鉴权接口
 * 作者: xuzeyu
 * 创建时间: 2026/1/1
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private AdminMapper adminMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        LambdaQueryWrapper<Admin> lambdaQuery = new LambdaQueryWrapper<>();
        lambdaQuery.eq(Admin::getId, loginId);
        Admin admin = adminMapper.selectOne(lambdaQuery);
        List<AdminPermission> adminPermissions = adminMapper.selectByRole(admin.getRoleId());
        return adminPermissions.stream()
                .map(AdminPermission::getCode)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> list = new ArrayList<>();
        list.add("admin");
        return list;
    }
}
