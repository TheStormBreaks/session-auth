package com.example.sessionauth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    // Keep the form fields together instead of passing three loose strings around.
    @NotBlank
    @Size(max = 50)
    private String username;

    // The actual password is only used for the login check.
    @NotBlank
    @Size(max = 100)
    private String password;

    // This gets checked against the value saved in the session.
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