package com.pettown.miniapp.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pettown.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RedisVerificationRequestLimiterTests {
    private final RedissonClient client = mock(RedissonClient.class);
    private final RScript script = mock(RScript.class);
    private RedisVerificationRequestLimiter limiter;

    @BeforeEach
    void setUp() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("redissonClient", client);
        limiter = new RedisVerificationRequestLimiter(beans.getBeanProvider(RedissonClient.class),
                Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void passesPhoneAndSourceLimitsToOneAtomicScript() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), anyLong())).thenReturn(0L);

        limiter.checkAndRecord("13800138000", "192.0.2.10");

        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> lua = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(script).eval(eq(RScript.Mode.READ_WRITE), lua.capture(),
                eq(RScript.ReturnType.INTEGER), keys.capture(), anyLong());
        String day = LocalDate.of(2026, 11, 5).format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(keys.getValue()).containsExactly(
                "pettown:verification-rate:phone:13800138000:cooldown",
                "pettown:verification-rate:phone:13800138000:" + day,
                "pettown:verification-rate:ip:192.0.2.10:" + day);
        assertThat(lua.getValue()).contains("EXISTS", "INCR", "EXPIRE", ">= 10", ">= 50");
    }

    @Test
    void rejectsCooldownPhoneDailyAndSourceDailyLimits() {
        String[] messages = {"", "60 秒", "手机号今日", "来源今日"};
        for (int code = 1; code <= 3; code++) {
            when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                    anyList(), anyLong())).thenReturn((long) code);
            assertThatThrownBy(() -> limiter.checkAndRecord("13800138000", "192.0.2.10"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining(messages[code]);
        }
    }

    @Test
    void refusesRequestsWhenRedisIsDisabled() {
        var beans = new DefaultListableBeanFactory();
        var disabled = new RedisVerificationRequestLimiter(beans.getBeanProvider(RedissonClient.class));
        assertThatThrownBy(() -> disabled.checkAndRecord("13800138000", "192.0.2.10"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Redis 未启用");
    }
}
