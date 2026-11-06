package com.pettown.miniapp.user.service;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.miniapp.file.service.MiniappFileService;
import com.pettown.miniapp.user.vo.UserInfoResponse;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 当前用户资料修改：昵称 1-20 字并过敏感词，头像必须为本系统上传接口返回的地址。 */
@Service
public class UserProfileService {
    /** 敏感词初版起始集，待产品提供完整词库后扩充（待确认）。 */
    private static final Set<String> SENSITIVE_WORDS = Set.of("傻逼", "脑残", "狗屎", "fuck", "shit");

    private final UserMapper users;
    private final MiniappFileService fileService;

    @Autowired
    public UserProfileService(UserMapper users, MiniappFileService fileService) {
        this.users = users;
        this.fileService = fileService;
    }

    public UserInfoResponse update(String userId, String nickname, String avatarUrl) {
        if (nickname == null && avatarUrl == null) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "昵称与头像不能同时为空");
        }
        User user = users.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "用户不存在");
        }
        if (nickname != null) {
            user.setNickname(validateNickname(nickname));
        }
        if (avatarUrl != null) {
            fileService.requireOwnImageUrl(avatarUrl);
            user.setAvatarUrl(avatarUrl);
        }
        users.updateById(user);
        return new UserInfoResponse(user.getId(), user.getPhone(), user.getNickname(), user.getAvatarUrl());
    }

    private String validateNickname(String raw) {
        String nickname = raw.trim();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < 1 || length > 20) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "昵称须为 1-20 字");
        }
        String lowered = nickname.toLowerCase(Locale.ROOT);
        for (String word : SENSITIVE_WORDS) {
            if (lowered.contains(word)) {
                throw new BusinessException(ErrorCodeEnum.FAIL, "昵称包含敏感词");
            }
        }
        return nickname;
    }
}
