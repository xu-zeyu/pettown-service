package com.pettown.common.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void expectedClientErrorsOnlyProduceDebugMessagesWithoutStacks() {
        assertThat(handler.handleBusinessException(new BusinessException(ErrorCodeEnum.FAIL, "业务失败"))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(handler.handleMethodArgumentTypeMismatch(new MethodArgumentTypeMismatchException(
                "invalid", Long.class, "id", null, new NumberFormatException()))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(handler.handleConstraintViolationException(new ConstraintViolationException(Set.of()))
                .getStatusCode().value()).isEqualTo(400);

        assertThat(appender.list).hasSize(3).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void unexpectedErrorsKeepOneErrorEventWithFullStack() {
        RuntimeException runtime = new IllegalStateException("database unavailable");
        assertThat(handler.handleRuntimeException(runtime).getStatusCode().value()).isEqualTo(500);
        assertThat(handler.handleException(new Exception("unexpected failure")).getStatusCode().value()).isEqualTo(500);

        assertThat(appender.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        });
    }
}
