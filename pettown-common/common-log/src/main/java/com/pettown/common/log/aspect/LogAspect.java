package com.pettown.common.log.aspect;

import com.pettown.common.log.annotation.Log;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LogAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogAspect.class);

    @Around("@annotation(operation)")
    public Object record(ProceedingJoinPoint point, Log operation) throws Throwable {
        long started = System.nanoTime();
        try {
            Object result = point.proceed();
            LOGGER.info("operation={} type={} success=true elapsedMs={}", operation.value(),
                    operation.operationType(), elapsed(started));
            return result;
        } catch (Throwable error) {
            // 异常由全局处理器统一记录，避免同一次失败在切面和处理器重复告警。
            LOGGER.debug("operation={} type={} success=false elapsedMs={} error={}", operation.value(),
                    operation.operationType(), elapsed(started), error.toString());
            throw error;
        }
    }

    private long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
