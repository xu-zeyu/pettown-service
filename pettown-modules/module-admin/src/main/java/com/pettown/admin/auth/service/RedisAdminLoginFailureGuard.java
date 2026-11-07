package com.pettown.admin.auth.service;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import java.util.List;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 管理员密码失败计数：连续 5 次失败锁定 30 分钟，成功登录清零。 */
@Service
public class RedisAdminLoginFailureGuard implements AdminLoginFailureGuard {
    private static final String PREFIX = "pettown:admin-login-failure:";
    private static final String CHECK = "return tonumber(redis.call('GET', KEYS[1]) or '0')";
    private static final String RECORD = """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 or n == 5 then redis.call('EXPIRE', KEYS[1], 1800) end
            return n
            """;
    private static final String CLEAR = "return redis.call('DEL', KEYS[1])";

    private final ObjectProvider<RedissonClient> redisson;

    @Autowired
    public RedisAdminLoginFailureGuard(ObjectProvider<RedissonClient> redisson) {
        this.redisson = redisson;
    }

    @Override
    public void assertNotLocked(String username) {
        if (run(RScript.Mode.READ_ONLY, CHECK, username) >= 5) {
            throw locked();
        }
    }

    @Override
    public void recordPasswordFailure(String username) {
        if (run(RScript.Mode.READ_WRITE, RECORD, username) >= 5) {
            throw locked();
        }
    }

    @Override
    public void clear(String username) {
        run(RScript.Mode.READ_WRITE, CLEAR, username);
    }

    private long run(RScript.Mode mode, String script, String username) {
        RedissonClient client = redisson.getIfAvailable();
        if (client == null) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "登录保护服务不可用，暂不能登录");
        }
        Long result = client.getScript(StringCodec.INSTANCE).eval(mode, script,
                RScript.ReturnType.INTEGER, List.of(PREFIX + username));
        if (result == null) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "登录保护服务不可用，暂不能登录");
        }
        return result;
    }

    private BusinessException locked() {
        return new BusinessException(ErrorCodeEnum.LOGIN_LOCKED, "密码连续错误 5 次，账号暂锁 30 分钟");
    }
}
