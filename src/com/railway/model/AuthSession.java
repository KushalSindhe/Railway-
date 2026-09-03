package com.railway.model;

import java.time.LocalDateTime;

/**
 * Encapsulates an active authentication session identified by a secure token.
 */
public class AuthSession {
    private final String token;
    private final User user;
    private final LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;

    public AuthSession(String token, User user) {
        this.token = token;
        this.user = user;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = this.createdAt;
    }

    public String getToken() {
        return token;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void touch() {
        this.lastAccessedAt = LocalDateTime.now();
    }
}
