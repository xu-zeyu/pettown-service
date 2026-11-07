package com.pettown.admin.verification.service;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import java.time.Clock;
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

/** 同一 IP 每分钟最多签发 20 次验证码；按上海时区分钟桶原子计数。 */
@Service
public class RedisCaptchaRateLimiter implements CaptchaRateLimiter {
    static final int LIMIT_PER_MINUTE = 20;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PREFIX = "pettown:admin-captcha-rate:";
    private static final String RESERVE = """
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= 20 then return 0 end
            if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return 1
            """;

    private final ObjectProvider<RedissonClient> redisson;
    private final Clock clock;

    @Autowired
    public RedisCaptchaRateLimiter(ObjectProvider<RedissonClient> redisson) {
        this(redisson, Clock.system(ZONE));
    }

    RedisCaptchaRateLimiter(ObjectProvider<RedissonClient> redisson, Clock clock) {
        this.redisson = redisson;
        this.clock = clock;
    }

    @Override
    public void checkAndRecord(String clientIp) {
        RedissonClient client = client();
        String minute = ZonedDateTime.now(clock).withZoneSameInstant(ZONE)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        Long result = client.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, RESERVE,
                RScript.ReturnType.INTEGER, List.of(PREFIX + clientIp + ":" + minute), 60L);
        if (result == null) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "验证码服务不可用，暂不能签发验证码");
        }
        if (result == 0) {
            throw new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "验证码请求过于频繁，请稍后再试");
        }
    }

    private RedissonClient client() {
        RedissonClient client = redisson.getIfAvailable();
        if (client == null) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "验证码服务不可用，暂不能签发验证码");
        }
        return client;
    }
}
