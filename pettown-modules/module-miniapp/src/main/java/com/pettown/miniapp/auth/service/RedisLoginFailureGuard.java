package com.pettown.miniapp.auth.service;

import com.pettown.business.user.service.LoginFailureGuard;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import java.util.List;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 已注册小程序账号密码失败计数；第 5 次错误起锁定 15 分钟。 */
@Service
public class RedisLoginFailureGuard implements LoginFailureGuard {
    private static final String PREFIX = "pettown:login-failure:";
    private static final String CHECK = "return tonumber(redis.call('GET', KEYS[1]) or '0')";
    private static final String RECORD = """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 or n == 5 then redis.call('EXPIRE', KEYS[1], 900) end
            return n
            """;
    private static final String CLEAR = "return redis.call('DEL', KEYS[1])";

    private final ObjectProvider<RedissonClient> redisson;

    public RedisLoginFailureGuard(ObjectProvider<RedissonClient> redisson) {
        this.redisson = redisson;
    }

    @Override
    public void assertNotLocked(String phone) {
        if (run(RScript.Mode.READ_ONLY, CHECK, phone) >= 5) {
            throw locked();
        }
    }

    @Override
    public void recordPasswordFailure(String phone) {
        if (run(RScript.Mode.READ_WRITE, RECORD, phone) >= 5) {
            throw locked();
        }
    }

    @Override
    public void clear(String phone) {
        run(RScript.Mode.READ_WRITE, CLEAR, phone);
    }

    private long run(RScript.Mode mode, String script, String phone) {
        RedissonClient client = redisson.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Redis 未启用，不能校验小程序账号锁定状态");
        }
        Long result = client.getScript(StringCodec.INSTANCE).eval(mode, script,
                RScript.ReturnType.INTEGER, List.of(PREFIX + phone));
        if (result == null) {
            throw new IllegalStateException("Redis 未返回账号锁定结果");
        }
        return result;
    }

    private BusinessException locked() {
        return new BusinessException(ErrorCodeEnum.LOGIN_LOCKED, "密码连续错误 5 次，账号暂锁 15 分钟");
    }
}
