package com.example.sessionauth.controller;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import com.example.sessionauth.model.SignupRequest;
import com.example.sessionauth.repository.UserRepository;

@SpringBootTest
class AuthControllerTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private UserRepository userRepository;

    @Test
    void signupShouldStoreUserAndReturnCreated() {
        // Pretend the user already saw this CAPTCHA on the signup page.
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CAPTCHA_KEY", "ABC123");
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("secret123");
        request.setCaptcha("ABC123");

        ResponseEntity<Map<String, String>> response = authController.signup(
            session, request, new BeanPropertyBindingResult(request, "request"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("message", "Account created successfully. Please sign in.");
        assertThat(userRepository.findByUsername("newuser")).isPresent();
    }

    @Test
    void extendSessionShouldReturnUnauthorizedWhenUserIsNotLoggedIn() {
        // No user in the session should mean no renewal.
        ResponseEntity<Map<String, String>> response = authController.extendSession(
            new MockHttpSession(), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Session expired");
    }

    @Test
    void extendSessionShouldReturnOkForLoggedInUser() {
        // A logged-in session gets the full timeout again.
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "activeuser");
        String originalSessionId = session.getId();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        ResponseEntity<Map<String, String>> response = authController.extendSession(session, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Session renewed");
        assertThat(session.getId()).isNotEqualTo(originalSessionId);
        assertThat(session.getAttribute("user")).isEqualTo("activeuser");
        assertThat(session.getMaxInactiveInterval()).isEqualTo(900);
    }

    @Test
    void signupShouldRejectInvalidRequestBinding() {
        // Spring normally creates this binding result for us during a request.
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CAPTCHA_KEY", "ABC123");
        SignupRequest request = new SignupRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
        bindingResult.rejectValue("password", "Size", "Password is too short");

        ResponseEntity<Map<String, String>> response = authController.signup(session, request, bindingResult);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
