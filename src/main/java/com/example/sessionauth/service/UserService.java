package com.example.sessionauth.service;

import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.sessionauth.model.UserAccount;
import com.example.sessionauth.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // Hash the password and make sure the username is free.
    public UserAccount register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Username and password are required");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }
        UserAccount account = new UserAccount(username, passwordEncoder.encode(password));
        return userRepository.save(account);
    }

    // BCrypt does the password comparison for us.
    public Optional<UserAccount> authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(account -> passwordEncoder.matches(password, account.getPassword()));
    }
}
