package com.pettown.business.user.mapper;

import com.pettown.business.user.entity.User;
import java.util.Optional;

public interface UserMapper {
    Optional<User> findByPhone(String phone);

    User insert(String phone, String passwordHash);
}
