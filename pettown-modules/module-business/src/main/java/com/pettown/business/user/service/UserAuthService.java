package com.pettown.business.user.service;

import com.pettown.business.user.entity.User;

public interface UserAuthService {
    User register(String phone, String password);

    User authenticate(String phone, String password);
}
