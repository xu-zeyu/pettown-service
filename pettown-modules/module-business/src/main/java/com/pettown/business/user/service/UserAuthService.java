package com.pettown.business.user.service;

import com.pettown.business.user.entity.User;
import com.pettown.common.commonEnum.UserSourceEnum;

public interface UserAuthService {
    User getById(String id);

    User loginOrRegister(String phone, String password, String verificationCode, UserSourceEnum source);
}
