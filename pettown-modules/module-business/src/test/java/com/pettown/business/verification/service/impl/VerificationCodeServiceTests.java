package com.pettown.business.verification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pettown.common.commonEnum.VerificationCodeTypeEnum;
import com.pettown.common.cache.Cache;
import com.pettown.common.exception.BusinessException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VerificationCodeServiceTests {
    private final Cache cache = mock(Cache.class);
    private final VerificationCodeServiceImpl service = new VerificationCodeServiceImpl(cache);

    @ParameterizedTest
    @CsvSource({"LOGIN_REGISTER,login_register", "RESET_PASSWORD,reset_password"})
    void storesSixDigitsUnderThePurposeValueWithFiveMinuteTtl(VerificationCodeTypeEnum type, String value) {
        String code = service.generate("13800138000", type);

        assertThat(code).matches("[0-9]{6}");
        verify(cache).set("pettown:verification-code:" + value + ":13800138000", code, Duration.ofMinutes(5));
    }

    @Test
    void isolatesPurposesAndPhonesAndReplacesThePreviousCode() {
        Map<String, Object> stored = new HashMap<>();
        doAnswer(invocation -> {
            stored.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cache).set(anyString(), any(), any(Duration.class));

        service.generate("13800138000", VerificationCodeTypeEnum.LOGIN_REGISTER);
        String reset = service.generate("13800138000", VerificationCodeTypeEnum.RESET_PASSWORD);
        String otherPhone = service.generate("13900139000", VerificationCodeTypeEnum.LOGIN_REGISTER);
        String latest = service.generate("13800138000", VerificationCodeTypeEnum.LOGIN_REGISTER);

        assertThat(stored).hasSize(3)
                .containsEntry("pettown:verification-code:login_register:13800138000", latest)
                .containsEntry("pettown:verification-code:reset_password:13800138000", reset)
                .containsEntry("pettown:verification-code:login_register:13900139000", otherPhone);
    }

    @Test
    void rejectsMissingPhoneOrPurposeBeforeWriting() {
        assertThatThrownBy(() -> service.generate(" ", VerificationCodeTypeEnum.LOGIN_REGISTER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generate("13800138000", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cache);
    }

    @Test
    void propagatesCacheFailureWithoutReturningACode() {
        var failure = new IllegalStateException("Redis unavailable");
        doThrow(failure).when(cache).set(anyString(), any(), any(Duration.class));

        assertThatThrownBy(() -> service.generate("13800138000", VerificationCodeTypeEnum.LOGIN_REGISTER))
                .isSameAs(failure);
    }

    @Test
    void consumesAMatchingCodeOnlyOnce() {
        when(cache.compareAndDelete("pettown:verification-code:login_register:13800138000", "000007"))
                .thenReturn(true, false);

        service.verifyAndConsume("13800138000", VerificationCodeTypeEnum.LOGIN_REGISTER, "000007");
        assertThatThrownBy(() -> service.verifyAndConsume(
                "13800138000", VerificationCodeTypeEnum.LOGIN_REGISTER, "000007"))
                .isInstanceOf(BusinessException.class).hasMessage("验证码错误或已过期");
    }

    @Test
    void rejectsWrongMissingOrExpiredCodes() {
        assertThatThrownBy(() -> service.verifyAndConsume(
                "13800138000", VerificationCodeTypeEnum.LOGIN_REGISTER, "123456"))
                .isInstanceOf(BusinessException.class).hasMessage("验证码错误或已过期");
        verify(cache).compareAndDelete("pettown:verification-code:login_register:13800138000", "123456");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "abcdef", " 123456"})
    void rejectsInvalidCodeFormatWithoutAccessingRedis(String code) {
        assertThatThrownBy(() -> service.verifyAndConsume(
                "13800138000", VerificationCodeTypeEnum.LOGIN_REGISTER, code))
                .isInstanceOf(BusinessException.class).hasMessage("验证码须为 6 位数字");
        verifyNoInteractions(cache);
    }

    @Test
    void propagatesCacheFailureDuringVerification() {
        var failure = new IllegalStateException("Redis unavailable");
        when(cache.compareAndDelete(anyString(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.verifyAndConsume(
                "13800138000", VerificationCodeTypeEnum.LOGIN_REGISTER, "000007"))
                .isSameAs(failure);
    }
}
