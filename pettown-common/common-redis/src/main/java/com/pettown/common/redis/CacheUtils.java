package com.pettown.common.redis;

import com.pettown.common.cache.Cache;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/** 基于 Redisson 的缓存实现，与 ai-commerce-starter-cache 保持相同的核心 API。 */
@Component
public class CacheUtils implements Cache {
    private final ObjectProvider<RedissonClient> redissonClientProvider;

    public CacheUtils(ObjectProvider<RedissonClient> redissonClientProvider) {
        this.redissonClientProvider = redissonClientProvider;
    }

    @Override
    public void setCache(String key, Object value) {
        client().getBucket(key).set(value);
    }

    @Override
    public void set(String key, Object value, Duration expire) {
        client().getBucket(key).set(value, expire.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Optional<Object> getCache(String key) {
        return Optional.ofNullable(client().getBucket(key).get());
    }

    @Override
    public <V> V get(String key, Class<V> clazz) {
        return clazz.cast(client().getBucket(key).get());
    }

    @Override
    public <V> V getOrElse(String key, V defaultValue) {
        RBucket<V> bucket = client().getBucket(key);
        return Optional.ofNullable(bucket.get()).orElse(defaultValue);
    }

    @Override
    public boolean delete(String key) {
        return client().getBucket(key).delete();
    }

    @Override
    public boolean compareAndDelete(String key, Object expectedValue) {
        Assert.notNull(expectedValue, "待校验的缓存值不能为空");
        return client().getBucket(key).compareAndSet(expectedValue, null);
    }

    public <K, V> RMap<K, V> getMap(String key) {
        return client().getMap(key);
    }

    private RedissonClient client() {
        // 保留未启用 Redis 时其他业务正常启动的能力；缓存调用必须使用真实 Redis。
        RedissonClient client = redissonClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Redis 未启用，请设置 pettown.redis.enabled=true");
        }
        return client;
    }
}
