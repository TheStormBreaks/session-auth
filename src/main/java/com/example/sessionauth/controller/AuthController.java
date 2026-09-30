package com.example.sessionauth.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sessionauth.model.LoginRequest;
import com.example.sessionauth.model.SignupRequest;
import com.example.sessionauth.service.CaptchaService;
import com.example.sessionauth.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final CaptchaService captchaService;
    private final UserService userService;

    public AuthController(CaptchaService captchaService, UserService userService) {
        this.captchaService = captchaService;
        this.userService = userService;
    }

    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] getCaptcha(HttpSession session) throws IOException {
        String captchaText = (String) session.getAttribute("CAPTCHA_KEY");
        if (captchaText == null) {
            captchaText = captchaService.generateCaptchaText();
            session.setAttribute("CAPTCHA_KEY", captchaText);
        }
        try {
            return captchaService.generateCaptchaImage(captchaText);
        } catch (IOException ex) {
            logger.error("Failed to generate CAPTCHA image", ex);
            throw ex;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(HttpSession session,
                        @Valid @RequestBody LoginRequest request,
                        BindingResult bindingResult) {
        String username = request.getUsername();
        String sessionCaptcha = (String) session.getAttribute("CAPTCHA_KEY");
        session.setAttribute("CAPTCHA_KEY", captchaService.generateCaptchaText()); // Use a fresh one after every try.

        if (bindingResult.hasErrors()) {
            logger.warn("Login validation failed for username={}", username);
            return ResponseEntity.badRequest().body(Map.of("error", "Please enter valid login details."));
        }

        if (sessionCaptcha == null || !sessionCaptcha.equalsIgnoreCase(request.getCaptcha().trim())) {
            logger.warn("Login CAPTCHA validation failed for username={}", username);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid CAPTCHA code."));
        }

        if (userService.authenticate(username, request.getPassword()).isEmpty()) {
            logger.warn("Login failed for username={}", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials."));
        }

        session.setAttribute("user", username); // This is all we need to identify the user later.
        session.setMaxInactiveInterval(900); // Fifteen minutes should be enough for this session.
        logger.info("Login succeeded for username={}", username);
        return ResponseEntity.ok(Map.of("username", username));
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(HttpSession session,
                         @Valid @RequestBody SignupRequest request,
                         BindingResult bindingResult) {
        String username = request.getUsername();
        String sessionCaptcha = (String) session.getAttribute("CAPTCHA_KEY");
        session.setAttribute("CAPTCHA_KEY", captchaService.generateCaptchaText()); // Don't let a CAPTCHA be reused.

        if (bindingResult.hasErrors()) {
            logger.warn("Signup validation failed for username={}", username);
            return ResponseEntity.badRequest().body(Map.of("error", "Please enter valid signup details."));
        }

        if (sessionCaptcha == null || !sessionCaptcha.equalsIgnoreCase(request.getCaptcha().trim())) {
            logger.warn("Signup CAPTCHA validation failed for username={}", username);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid CAPTCHA code."));
        }

        try {
            userService.register(username, request.getPassword());
        } catch (IllegalArgumentException ex) {
            logger.warn("Signup failed for username={}: {}", username, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        logger.info("Signup succeeded for username={}", username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Account created successfully. Please sign in."));
    }

    @PostMapping("/extend-session")
    public ResponseEntity<Map<String, String>> extendSession(HttpSession session, HttpServletRequest request) {
        if (currentUser(session) == null) {
            logger.warn("Session renewal failed because the session is expired");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Session expired"));
        }
        request.changeSessionId();
        session.setMaxInactiveInterval(900);
        logger.info("Session renewed for username={}", currentUser(session));
        return ResponseEntity.ok(Map.of("message", "Session renewed"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> currentSession(HttpSession session) {
        String username = currentUser(session);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Session expired"));
        }
        return ResponseEntity.ok(Map.of("username", username));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        String username = currentUser(session);
        session.invalidate();
        logger.info("Logout succeeded for username={}", username);
        return ResponseEntity.noContent().build();
    }

    // Pull the username out of the session, if there is one.
    private String currentUser(HttpSession session) {
        Object user = session.getAttribute("user");
        return user instanceof String username ? username : null;
    }
}