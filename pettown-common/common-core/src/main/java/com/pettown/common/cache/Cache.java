package com.pettown.common.cache;

import java.time.Duration;
import java.util.Optional;

public interface Cache {
    void setCache(String key, Object value);

    void set(String key, Object value, Duration expire);

    Optional<Object> getCache(String key);

    <V> V get(String key, Class<V> clazz);

    <V> V getOrElse(String key, V defaultValue);

    boolean delete(String key);

    /** 仅在值匹配时原子删除，返回是否成功删除。 */
    boolean compareAndDelete(String key, Object expectedValue);
}
