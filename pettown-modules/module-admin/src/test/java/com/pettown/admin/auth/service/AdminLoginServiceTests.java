package com.pettown.admin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pettown.admin.verification.service.AdminCaptchaService;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminLoginServiceTests {
    private final AdminService adminService = mock(AdminService.class);
    private final AdminCaptchaService captchaService = mock(AdminCaptchaService.class);
    private final AdminLoginFailureGuard guard = mock(AdminLoginFailureGuard.class);
    private AdminLoginService service;

    @BeforeEach
    void setUp() {
        service = new AdminLoginService(adminService, captchaService, guard);
    }

    @Test
    void wrongCaptchaRejectsBeforePasswordCheck() {
        when(captchaService.consume("t1", "WRONG")).thenReturn(false);
        assertThatThrownBy(() -> service.login("admin", "pw", "t1", "WRONG"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception)
                        .getCommonErrorCode().getCode()).isEqualTo("4002"));
        verify(adminService, never()).authenticate(anyString(), anyString());
        verify(guard, never()).recordPasswordFailure(anyString());
    }

    @Test
    void passwordFailureIsRecordedAndRethrown() {
        when(captchaService.consume("t1", "AB34")).thenReturn(true);
        when(adminService.authenticate("admin", "bad")).thenThrow(
                new BusinessException(ErrorCodeEnum.LOGIN_PASSWORD_INVALID, "账号或密码错误"));
        assertThatThrownBy(() -> service.login("admin", "bad", "t1", "AB34"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号或密码错误");
        verify(guard).recordPasswordFailure("admin");
        verify(guard, never()).clear(anyString());
    }

    @Test
    void lockedAccountFailsBeforeCaptchaConsumption() {
        doThrow(new BusinessException(ErrorCodeEnum.LOGIN_LOCKED, "密码连续错误 5 次，账号暂锁 30 分钟"))
                .when(guard).assertNotLocked("admin");
        assertThatThrownBy(() -> service.login("admin", "pw", "t1", "AB34"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂锁");
        verify(captchaService, never()).consume(anyString(), anyString());
    }

    // 登录成功路径涉及 Sa-Token 会话签发（需请求上下文），由 AdminLoginApiTests 集成用例覆盖
    // token 返回、passwordChangeRequired、回写登录时间与会话可用性。
}
