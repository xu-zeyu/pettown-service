package com.pettown.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class CacheUtilsTests {
    private RedissonClient redissonClient;
    private RBucket<Object> bucket;
    private CacheUtils cache;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        bucket = mock(RBucket.class);
        when(redissonClient.getBucket("key")).thenReturn(bucket);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("redissonClient", redissonClient);
        cache = new CacheUtils(beans.getBeanProvider(RedissonClient.class));
    }

    @Test
    void supportsPersistentAndExpiringWrites() {
        cache.setCache("key", "value");
        cache.set("key", "000007", Duration.ofMinutes(5));

        verify(bucket).set("value");
        verify(bucket).set("000007", 300_000, TimeUnit.MILLISECONDS);
    }

    @Test
    void readsValuesWithOptionalTypedAndDefaultApis() {
        when(bucket.get()).thenReturn("000007");
        assertThat(cache.getCache("key")).contains("000007");
        assertThat(cache.get("key", String.class)).isEqualTo("000007");
        assertThat(cache.getOrElse("key", "fallback")).isEqualTo("000007");

        when(bucket.get()).thenReturn(null);
        assertThat(cache.getCache("key")).isEmpty();
        assertThat(cache.get("key", String.class)).isNull();
        assertThat(cache.getOrElse("key", "fallback")).isEqualTo("fallback");
    }

    @Test
    void deletesValuesAndExposesRedisMaps() {
        when(bucket.delete()).thenReturn(true, false);
        assertThat(cache.delete("key")).isTrue();
        assertThat(cache.delete("key")).isFalse();

        RMap<Object, Object> map = mock(RMap.class);
        when(redissonClient.getMap("map")).thenReturn(map);
        assertThat(cache.getMap("map")).isSameAs(map);
    }

    @Test
    void atomicallyDeletesOnlyTheExpectedValue() {
        when(bucket.compareAndSet("000007", null)).thenReturn(true, false);

        assertThat(cache.compareAndDelete("key", "000007")).isTrue();
        assertThat(cache.compareAndDelete("key", "000007")).isFalse();
        verify(bucket, never()).get();
        verify(bucket, never()).delete();
        assertThatThrownBy(() -> cache.compareAndDelete("key", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledRedisDoesNotSilentlyUseMemory() {
        var beans = new DefaultListableBeanFactory();
        CacheUtils disabled = new CacheUtils(beans.getBeanProvider(RedissonClient.class));
        assertThatThrownBy(() -> disabled.set("key", "value", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pettown.redis.enabled=true");
    }

    @Test
    void jsonCodecPreservesLeadingZeros() throws Exception {
        var codec = new JsonJacksonCodec();
        ByteBuf buffer = codec.getValueEncoder().encode("000007");
        try {
            assertThat(buffer.toString(StandardCharsets.UTF_8)).isEqualTo("\"000007\"");
            assertThat(codec.getValueDecoder().decode(buffer, null)).isEqualTo("000007");
        } finally {
            buffer.release();
        }
    }
}
