package com.pettown.business.user.mapper;

import com.pettown.business.user.entity.User;
import com.pettown.common.mybatis.mapper.BaseMapperX;
import java.util.Optional;

public interface UserMapper extends BaseMapperX<User> {
    default Optional<User> findById(String id) {
        return selectByIdOpt(id);
    }

    default Optional<User> findByPhone(String phone) {
        return Optional.ofNullable(selectOne(User::getPhone, phone));
    }
}
