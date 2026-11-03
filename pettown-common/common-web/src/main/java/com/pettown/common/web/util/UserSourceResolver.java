package com.pettown.common.web.util;

import com.pettown.common.commonEnum.UserSourceEnum;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.HttpHeaders;

public final class UserSourceResolver {
    private UserSourceResolver() {
    }

    public static UserSourceEnum resolve(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent == null || userAgent.isBlank()) {
            return UserSourceEnum.UNKNOWN;
        }
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        // 小程序和鸿蒙 UA 可能同时包含 iOS 或 Android 标识，优先识别。
        if (normalized.contains("miniprogram")) {
            return UserSourceEnum.MINI_PROGRAM;
        }
        if (normalized.contains("harmonyos") || normalized.contains("openharmony")) {
            return UserSourceEnum.HARMONY_OS;
        }
        if (normalized.contains("iphone") || normalized.contains("ipad") || normalized.contains("ipod")) {
            return UserSourceEnum.IOS;
        }
        if (normalized.contains("android")) {
            return UserSourceEnum.ANDROID;
        }
        if (normalized.contains("mozilla/")) {
            return UserSourceEnum.WEB;
        }
        return UserSourceEnum.UNKNOWN;
    }
}
