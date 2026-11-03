package com.pettown.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;

class LoggingConfigurationTests {
    @TempDir
    Path directory;

    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final LogbackLoggingSystem loggingSystem = new LogbackLoggingSystem(getClass().getClassLoader());

    @AfterEach
    void cleanUp() {
        loggingSystem.cleanUp();
        context.stop();
        context.reset();
    }

    @Test
    void testProfileDoesNotCreateLogFiles() throws Exception {
        MockEnvironment environment = environment("pettown-app");
        environment.setActiveProfiles("test");
        initialize(environment);

        assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE")).isNotNull();
        assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("FILE")).isNull();
        assertThat(directory.resolve("logs")).doesNotExist();
        assertThat(context.getLogger("org.springframework.web").getEffectiveLevel()).isEqualTo(Level.WARN);
        assertThat(context.getLogger("com.pettown.business").getEffectiveLevel()).isEqualTo(Level.INFO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pettown-app", "pettown-admin", "pettown-miniapp"})
    void separatesApplicationFilesAndBoundsArchives(String application) throws Exception {
        initialize(environment(application));

        RollingFileAppender<ILoggingEvent> file = fileAppender();
        assertThat(file.isStarted()).isTrue();
        assertThat(Path.of(file.getFile())).isEqualTo(directory.resolve("logs/" + application + ".log"));
        assertThat(Path.of(file.getFile())).exists();
        SizeAndTimeBasedRollingPolicy<?> policy = (SizeAndTimeBasedRollingPolicy<?>) file.getRollingPolicy();
        assertThat(policy.getMaxHistory()).isEqualTo(7);
        assertThat(policy.isCleanHistoryOnStart()).isTrue();
        assertThat(policy.getFileNamePattern()).endsWith(application + ".log.%d{yyyy-MM-dd}.%i.gz");
        assertThat(policy).extracting("maxFileSize.size", "totalSizeCap.size")
                .containsExactly(10L * 1024 * 1024, 100L * 1024 * 1024);
    }

    @Test
    void supportsCustomFileAndRollingSettings() throws Exception {
        Path customFile = directory.resolve("custom.log");
        initialize(environment("pettown-app")
                .withProperty("logging.file.name", customFile.toString())
                .withProperty("logging.level.root", "ERROR")
                .withProperty("logging.level.com.pettown", "DEBUG")
                .withProperty("logging.logback.rollingpolicy.max-file-size", "2MB")
                .withProperty("logging.logback.rollingpolicy.max-history", "3")
                .withProperty("logging.logback.rollingpolicy.total-size-cap", "20MB")
                .withProperty("logging.logback.rollingpolicy.clean-history-on-start", "false"));

        assertThat(Path.of(fileAppender().getFile())).isEqualTo(customFile);
        SizeAndTimeBasedRollingPolicy<?> policy = (SizeAndTimeBasedRollingPolicy<?>) fileAppender().getRollingPolicy();
        assertThat(policy.getMaxHistory()).isEqualTo(3);
        assertThat(policy.isCleanHistoryOnStart()).isFalse();
        assertThat(policy).extracting("maxFileSize.size", "totalSizeCap.size")
                .containsExactly(2L * 1024 * 1024, 20L * 1024 * 1024);
        assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.ERROR);
        assertThat(context.getLogger("com.pettown.business").getEffectiveLevel()).isEqualTo(Level.DEBUG);
    }

    private MockEnvironment environment(String application) {
        return new MockEnvironment()
                .withProperty("spring.application.name", application)
                .withProperty("logging.file.path", directory.resolve("logs").toString());
    }

    private void initialize(MockEnvironment environment) throws Exception {
        Path configuration = directory.resolve("logback-spring.xml");
        Files.writeString(configuration, """
                <configuration>
                    <include resource="com/pettown/common/log/logback-common.xml"/>
                </configuration>
                """);
        loggingSystem.initialize(new LoggingInitializationContext(environment), configuration.toUri().toString(), null);
    }

    @SuppressWarnings("unchecked")
    private RollingFileAppender<ILoggingEvent> fileAppender() {
        return (RollingFileAppender<ILoggingEvent>) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("FILE");
    }
}
