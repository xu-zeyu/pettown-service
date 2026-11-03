package com.pettown.common.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.pettown.common.commonEnum.UserSourceEnum;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class UserSourceResolverTests {
    @ParameterizedTest
    @CsvSource({
            "Mozilla/5.0 (iPhone) MicroMessenger/8.0 miniProgram, MINI_PROGRAM",
            "Mozilla/5.0 (Android 14) MicroMessenger/8.0 MiniProgram, MINI_PROGRAM",
            "Mozilla/5.0 (OpenHarmony) miniProgram, MINI_PROGRAM",
            "Mozilla/5.0 (Android 12; HarmonyOS 4.0), HARMONY_OS",
            "Mozilla/5.0 (OpenHarmony 5.0), HARMONY_OS",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X), IOS",
            "Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X), IOS",
            "Mozilla/5.0 (iPod touch), IOS",
            "Mozilla/5.0 (Linux; Android 14), ANDROID",
            "ANDROID, ANDROID",
            "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0, WEB",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1, WEB",
            "Mozilla/5.0 (Android 14) MicroMessenger/8.0, ANDROID"
    })
    void detectsPlatformsAndPrioritizesSpecificMarkers(String userAgent, UserSourceEnum expected) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, userAgent);

        assertThat(UserSourceResolver.resolve(request)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "curl/8.0", "MicroMessenger/8.0"})
    void defaultsToUnknownWithoutRecognizableMarkers(String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userAgent != null) {
            request.addHeader(HttpHeaders.USER_AGENT, userAgent);
        }

        assertThat(UserSourceResolver.resolve(request)).isEqualTo(UserSourceEnum.UNKNOWN);
    }
}
