package com.pettown.admin.verification.service;

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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RedisCaptchaStoreTests {
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private final RedissonClient client = mock(RedissonClient.class);
    private final RScript script = mock(RScript.class);
    private final RBucket<String> bucket = mock(RBucket.class);
    private RedisCaptchaStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("redissonClient", client);
        store = new RedisCaptchaStore(beans.getBeanProvider(RedissonClient.class));
        when(client.<String>getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(bucket);
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveWritesCodeWithFiveMinuteTtl() {
        store.save(ID, "AB34");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(bucket).set(eq("AB34"), eq(300L), eq(TimeUnit.SECONDS));
        verify(client).getBucket(key.capture(), eq(StringCodec.INSTANCE));
        assertThat(key.getValue()).isEqualTo("pettown:admin-captcha:" + ID);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void popAtomicallyReadsAndDeletes() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.VALUE),
                anyList())).thenReturn("AB34");
        assertThat(store.pop(ID)).isEqualTo("AB34");
        ArgumentCaptor<String> lua = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), lua.capture(), eq(RScript.ReturnType.VALUE), keys.capture());
        assertThat(lua.getValue()).contains("GET", "DEL");
        assertThat(keys.getValue()).containsExactly("pettown:admin-captcha:" + ID);
    }

    @Test
    void rejectsWhenRedisUnavailable() {
        RedisCaptchaStore withoutRedis = new RedisCaptchaStore(
                new DefaultListableBeanFactory().getBeanProvider(RedissonClient.class));
        assertThatThrownBy(() -> withoutRedis.save(ID, "AB34"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码服务不可用");
        assertThatThrownBy(() -> withoutRedis.pop(ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码服务不可用");
    }
}
