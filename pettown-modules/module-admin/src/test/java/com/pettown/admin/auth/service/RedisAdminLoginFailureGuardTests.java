package com.pettown.admin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pettown.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RedisAdminLoginFailureGuardTests {
    private static final String USER = "admin_user";
    private final RedissonClient client = mock(RedissonClient.class);
    private final RScript script = mock(RScript.class);
    private RedisAdminLoginFailureGuard guard;

    @BeforeEach
    void setUp() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("redissonClient", client);
        guard = new RedisAdminLoginFailureGuard(beans.getBeanProvider(RedissonClient.class));
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void locksWhenFailureCountReachesFive() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList())).thenReturn(5L);
        assertThatThrownBy(() -> guard.recordPasswordFailure(USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂锁").satisfies(exception ->
                        assertThat(((BusinessException) exception).getCommonErrorCode().getCode()).isEqualTo("423"));
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> lua = ArgumentCaptor.forClass(String.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), lua.capture(), eq(RScript.ReturnType.INTEGER), keys.capture());
        assertThat(keys.getValue()).containsExactly("pettown:admin-login-failure:" + USER);
        assertThat(lua.getValue()).contains("INCR", "EXPIRE", "1800");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void assertNotLockedRejectsWhenCountAtLeastFive() {
        when(script.eval(eq(RScript.Mode.READ_ONLY), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList())).thenReturn(5L);
        assertThatThrownBy(() -> guard.assertNotLocked(USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂锁");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void clearDeletesTheCounter() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList())).thenReturn(1L);
        guard.clear(USER);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), keys.capture());
        assertThat(keys.getValue()).containsExactly("pettown:admin-login-failure:" + USER);
    }

    @Test
    void rejectsWhenRedisUnavailable() {
        RedisAdminLoginFailureGuard withoutRedis = new RedisAdminLoginFailureGuard(
                new DefaultListableBeanFactory().getBeanProvider(RedissonClient.class));
        assertThatThrownBy(() -> withoutRedis.assertNotLocked(USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录保护服务不可用");
    }
}
