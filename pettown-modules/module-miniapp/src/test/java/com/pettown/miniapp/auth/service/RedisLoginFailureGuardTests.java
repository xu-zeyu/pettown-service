package com.pettown.miniapp.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pettown.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RedisLoginFailureGuardTests {
    private final RedissonClient client = mock(RedissonClient.class);
    private final RScript script = mock(RScript.class);
    private RedisLoginFailureGuard guard;

    @BeforeEach
    void setUp() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("redissonClient", client);
        guard = new RedisLoginFailureGuard(beans.getBeanProvider(RedissonClient.class));
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
    }

    @Test
    void locksOnFifthFailureAndRejectsFurtherAttempts() {
        when(script.eval(eq(RScript.Mode.READ_ONLY), eq("return tonumber(redis.call('GET', KEYS[1]) or '0')"),
                eq(RScript.ReturnType.INTEGER), anyList())).thenReturn(0L, 5L);
        when(script.eval(eq(RScript.Mode.READ_WRITE), org.mockito.ArgumentMatchers.contains("INCR"),
                eq(RScript.ReturnType.INTEGER), anyList())).thenReturn(1L, 2L, 3L, 4L, 5L);

        guard.assertNotLocked("13800138000");
        for (int i = 0; i < 4; i++) {
            guard.recordPasswordFailure("13800138000");
        }
        assertThatThrownBy(() -> guard.recordPasswordFailure("13800138000"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("15 分钟");
        assertThatThrownBy(() -> guard.assertNotLocked("13800138000"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("15 分钟");
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        var beans = new DefaultListableBeanFactory();
        var disabled = new RedisLoginFailureGuard(beans.getBeanProvider(RedissonClient.class));
        assertThatThrownBy(() -> disabled.assertNotLocked("13800138000"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Redis 未启用");
    }
}
