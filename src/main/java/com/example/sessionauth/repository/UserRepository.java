package com.example.sessionauth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.sessionauth.model.UserAccount;

@Repository
public interface UserRepository extends JpaRepository<UserAccount, Long> {
    // Look up a user for login or signup checks.
    Optional<UserAccount> findByUsername(String username);
}
