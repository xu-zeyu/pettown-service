package com.pettown.business.user.service;

import com.pettown.business.user.entity.User;

public interface UserAuthService {
    User getById(String id);

    User loginOrRegister(String phone, String password);
}
