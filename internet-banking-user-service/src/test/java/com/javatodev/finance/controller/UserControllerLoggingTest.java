package com.javatodev.finance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.service.UserService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

class UserControllerLoggingTest {

    private static final String PASSWORD = "S3cr3t-P@ssw0rd!";
    private static final String IDENTIFICATION = "NIC-987654321V";

    private final Logger logger = (Logger) LoggerFactory.getLogger(UserController.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void createUserDoesNotLogPasswordOrIdentification() {
        UserService userService = mock(UserService.class);
        User request = new User();
        request.setEmail("jane.doe@example.com");
        request.setIdentification(IDENTIFICATION);
        request.setPassword(PASSWORD);
        when(userService.createUser(any(User.class))).thenReturn(request);

        ResponseEntity<User> response = new UserController(userService).createUser(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain(PASSWORD)
                        .doesNotContain(IDENTIFICATION));
    }
}
