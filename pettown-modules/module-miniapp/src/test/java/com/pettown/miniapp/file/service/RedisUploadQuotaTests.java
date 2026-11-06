package com.pettown.miniapp.file.service;

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
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RedisUploadQuotaTests {
    private static final String USER = "user-77";
    private final RedissonClient client = mock(RedissonClient.class);
    private final RScript script = mock(RScript.class);
    private RedisUploadQuota quota;

    @BeforeEach
    void setUp() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("redissonClient", client);
        quota = new RedisUploadQuota(beans.getBeanProvider(RedissonClient.class),
                Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reserveUsesAtomicDailyKeyWithTtl() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), anyLong())).thenReturn(1L);

        String key = quota.reserve(USER);

        assertThat(key).isEqualTo("pettown:miniapp-image-upload:" + USER + ":20261106");
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> lua = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), lua.capture(),
                eq(RScript.ReturnType.INTEGER), keys.capture(), ttl.capture());
        assertThat(keys.getValue()).containsExactly(key);
        assertThat(lua.getValue()).contains("INCR", "EXPIRE", ">= 50");
        // 固定 2026-11-06 23:00（上海），距次日 0 点 3600 秒。
        assertThat(ttl.getValue()).isEqualTo(3600L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reserveRejectsWhenDailyLimitReached() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), anyLong())).thenReturn(0L);
        assertThatThrownBy(() -> quota.reserve(USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上限");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void releaseDecrementsReservedSlot() {
        quota.release("pettown:miniapp-image-upload:" + USER + ":20261106");
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(),
                eq(RScript.ReturnType.INTEGER), keys.capture());
        assertThat(keys.getValue()).containsExactly("pettown:miniapp-image-upload:" + USER + ":20261106");
    }

    @Test
    void rejectsWhenRedisUnavailable() {
        var beans = new DefaultListableBeanFactory();
        RedisUploadQuota withoutRedis = new RedisUploadQuota(beans.getBeanProvider(RedissonClient.class),
                Clock.systemDefaultZone());
        assertThatThrownBy(() -> withoutRedis.reserve(USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传限额服务不可用");
        assertThatThrownBy(() -> withoutRedis.release("any"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传限额服务不可用");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dayKeyFollowsShanghaiDate() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), anyLong())).thenReturn(1L);
        RedisUploadQuota nearMidnight = new RedisUploadQuota(
                new DefaultListableBeanFactory() {{
                    registerSingleton("redissonClient", client);
                }}.getBeanProvider(RedissonClient.class),
                Clock.fixed(Instant.parse("2026-11-06T15:59:30Z"), ZoneId.of("Asia/Shanghai")));
        String key = nearMidnight.reserve(USER);
        assertThat(key).endsWith(":" + java.time.LocalDate.of(2026, 11, 6).format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
