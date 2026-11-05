package com.pettown.miniapp.verification.service;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import java.time.Duration;
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

/** 使用一段 Redis 脚本原子检查三个额度，避免并发请求穿透限额。 */
@Service
public class RedisVerificationRequestLimiter implements VerificationRequestLimiter {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PREFIX = "pettown:verification-rate:";
    private static final String SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then return 1 end
            if tonumber(redis.call('GET', KEYS[2]) or '0') >= 10 then return 2 end
            if tonumber(redis.call('GET', KEYS[3]) or '0') >= 50 then return 3 end
            redis.call('SET', KEYS[1], '1', 'EX', 60)
            if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], ARGV[1]) end
            if redis.call('INCR', KEYS[3]) == 1 then redis.call('EXPIRE', KEYS[3], ARGV[1]) end
            return 0
            """;

    private final ObjectProvider<RedissonClient> redisson;
    private final Clock clock;

    @Autowired
    public RedisVerificationRequestLimiter(ObjectProvider<RedissonClient> redisson) {
        this(redisson, Clock.system(ZONE));
    }

    RedisVerificationRequestLimiter(ObjectProvider<RedissonClient> redisson, Clock clock) {
        this.redisson = redisson;
        this.clock = clock;
    }

    @Override
    public void checkAndRecord(String phone, String sourceIp) {
        RedissonClient client = redisson.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Redis 未启用，不能生成小程序验证码");
        }
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
        String day = now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        long secondsToMidnight = Math.max(1, Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(ZONE)).getSeconds());
        List<Object> keys = List.of(PREFIX + "phone:" + phone + ":cooldown",
                PREFIX + "phone:" + phone + ":" + day,
                PREFIX + "ip:" + sourceIp + ":" + day);
        Long result = client.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, SCRIPT,
                RScript.ReturnType.INTEGER, keys, secondsToMidnight);
        if (result == null) {
            throw new IllegalStateException("Redis 未返回验证码限额结果");
        }
        switch (result.intValue()) {
            case 0 -> { return; }
            case 1 -> throw new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "同一手机号 60 秒内只能获取一次验证码");
            case 2 -> throw new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "同一手机号今日验证码次数已达上限");
            case 3 -> throw new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "当前来源今日验证码次数已达上限");
            default -> throw new IllegalStateException("未知的验证码限额结果");
        }
    }
}
