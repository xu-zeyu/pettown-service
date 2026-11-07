package com.pettown.admin.verification.service;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RedisCaptchaStore implements CaptchaStore {
    private static final String PREFIX = "pettown:admin-captcha:";
    private static final Duration TTL = Duration.ofSeconds(300);
    private static final String POP = """
            local value = redis.call('GET', KEYS[1])
            if value then redis.call('DEL', KEYS[1]) end
            return value
            """;

    private final ObjectProvider<RedissonClient> redisson;

    @Autowired
    public RedisCaptchaStore(ObjectProvider<RedissonClient> redisson) {
        this.redisson = redisson;
    }

    @Override
    public void save(String captchaId, String code) {
        RBucket<String> bucket = client().getBucket(PREFIX + captchaId, StringCodec.INSTANCE);
        bucket.set(code, TTL.getSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public String pop(String captchaId) {
        return client().getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, POP,
                RScript.ReturnType.VALUE, List.of(PREFIX + captchaId));
    }

    private RedissonClient client() {
        RedissonClient client = redisson.getIfAvailable();
        if (client == null) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "验证码服务不可用，暂不能签发验证码");
        }
        return client;
    }
}
