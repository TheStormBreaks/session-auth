package com.example.sessionauth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SignupRequest {

    // These are the fields we need to create an account.
    @NotBlank
    @Size(max = 50)
    private String username;

    // New accounts need a reasonably long password.
    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    // Signup uses the same session-backed CAPTCHA as login.
    @NotBlank
    @Size(max = 10)
    private String captcha;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaptcha() {
        return captcha;
    }

    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }
}