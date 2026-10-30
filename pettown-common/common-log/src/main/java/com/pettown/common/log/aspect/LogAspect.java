package com.pettown.common.log.aspect;

import com.pettown.common.log.annotation.Log;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
            LOGGER.info("operation={} type={} traceId={} success=true elapsedMs={}", operation.value(),
                    operation.operationType(), MDC.get("traceId"), elapsed(started));
            return result;
        } catch (Throwable error) {
            LOGGER.warn("operation={} type={} traceId={} success=false elapsedMs={} error={}", operation.value(),
                    operation.operationType(), MDC.get("traceId"), elapsed(started), error.toString());
            throw error;
        }
    }

    private long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
