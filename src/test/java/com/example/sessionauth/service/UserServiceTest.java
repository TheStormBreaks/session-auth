package com.example.sessionauth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void shouldRegisterAndAuthenticateUser() {
        // Registering should hash the password, then authentication should do the comparison.
        userService.register("some", "secret123");

        assertThat(userService.authenticate("some", "secret123")).isPresent();
        assertThat(userService.authenticate("some", "wrong-password")).isEmpty();
    }
}
