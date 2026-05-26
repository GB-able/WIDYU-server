package com.widyu.admin.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile({"local", "dev"})
@Configuration
public class InMemoryLogAppenderConfig {

    @PostConstruct
    public void registerAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (rootLogger.getAppender("IN_MEMORY") != null) return;

        InMemoryLogAppender appender = new InMemoryLogAppender();
        appender.setName("IN_MEMORY");
        appender.setContext(context);
        appender.start();
        rootLogger.addAppender(appender);
    }
}
