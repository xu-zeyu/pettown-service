package com.pettown.business.user.service.impl;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.business.user.service.UserAuthService;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "pettown.user", name = "enabled", havingValue = "true")
public class UserAuthServiceImpl implements UserAuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserAuthServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User register(String phone, String password) {
        if (userMapper.findByPhone(phone).isPresent()) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "手机号已注册");
        }
        try {
            return userMapper.insert(phone, passwordEncoder.encode(password));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "手机号已注册");
        }
    }

    @Override
    public User authenticate(String phone, String password) {
        User user = userMapper.findByPhone(phone)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FAIL, "手机号或密码错误"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "手机号或密码错误");
        }
        return user;
    }
}
