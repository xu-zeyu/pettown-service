package com.pettown.miniapp.file.service;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RedisUploadQuota implements UploadQuota {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PREFIX = "pettown:miniapp-image-upload:";
    private static final String RESERVE = """
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= 50 then return 0 end
            if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return 1
            """;
    private static final String RELEASE = """
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count > 0 then redis.call('DECR', KEYS[1]) end
            return 1
            """;

    private final ObjectProvider<RedissonClient> redisson;
    private final Clock clock;

    @Autowired
    public RedisUploadQuota(ObjectProvider<RedissonClient> redisson) {
        this(redisson, Clock.system(ZONE));
    }

    RedisUploadQuota(ObjectProvider<RedissonClient> redisson, Clock clock) {
        this.redisson = redisson;
        this.clock = clock;
    }

    @Override
    public String reserve(String userId) {
        RedissonClient client = client();
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
        String key = PREFIX + userId + ":" + now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        long seconds = Math.max(1, Duration.between(now,
                now.toLocalDate().plusDays(1).atStartOfDay(ZONE)).getSeconds());
        Long result = client.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, RESERVE,
                RScript.ReturnType.INTEGER, List.of(key), seconds);
        if (result == null) {
            throw new IllegalStateException("Redis 未返回图片上传限额结果");
        }
        if (result == 0) {
            throw new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "今日图片上传已达 50 张上限");
        }
        return key;
    }

    @Override
    public void release(String reservationKey) {
        client().getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, RELEASE,
                RScript.ReturnType.INTEGER, List.of(reservationKey));
    }

    private RedissonClient client() {
        RedissonClient client = redisson.getIfAvailable();
        if (client == null) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "上传限额服务不可用，暂不能上传图片");
        }
        return client;
    }
}
