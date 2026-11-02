package com.pettown.common.redis.util;

import com.pettown.common.cache.Cache;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** 兼容原有字符串缓存 API，底层统一使用 Redisson。 */
@Component
public class RedisCache {
    private final Cache cache;

    public RedisCache(Cache cache) {
        this.cache = cache;
    }

    public void put(String key, String value, Duration ttl) {
        cache.set(key, value, ttl);
    }

    public String get(String key) {
        return cache.get(key, String.class);
    }

    public Boolean delete(String key) {
        return cache.delete(key);
    }
}
