package com.pettown.admin.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pettown.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RedisCaptchaRateLimiterTests {
    private final RedissonClient client = mock(RedissonClient.class);
    private final RScript script = mock(RScript.class);
    private RedisCaptchaRateLimiter limiter;

    @BeforeEach
    void setUp() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("redissonClient", client);
        limiter = new RedisCaptchaRateLimiter(beans.getBeanProvider(RedissonClient.class),
                Clock.fixed(Instant.parse("2026-11-07T02:30:00Z"), ZoneId.of("Asia/Shanghai")));
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recordsPerIpPerMinuteBucketWithTtl() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), anyLong())).thenReturn(1L);
        limiter.checkAndRecord("198.51.100.7");
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> lua = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), lua.capture(),
                eq(RScript.ReturnType.INTEGER), keys.capture(), ttl.capture());
        // 固定 2026-11-07 10:30（上海）分钟桶。
        assertThat(keys.getValue()).containsExactly("pettown:admin-captcha-rate:198.51.100.7:202611071030");
        assertThat(lua.getValue()).contains("INCR", "EXPIRE", ">= 20");
        assertThat(ttl.getValue()).isEqualTo(60L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsWhenPerMinuteLimitReached() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), anyLong())).thenReturn(0L);
        assertThatThrownBy(() -> limiter.checkAndRecord("198.51.100.7"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("频繁");
    }

    @Test
    void rejectsWhenRedisUnavailable() {
        RedisCaptchaRateLimiter withoutRedis = new RedisCaptchaRateLimiter(
                new DefaultListableBeanFactory().getBeanProvider(RedissonClient.class),
                Clock.systemDefaultZone());
        assertThatThrownBy(() -> withoutRedis.checkAndRecord("198.51.100.7"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码服务不可用");
    }
}
