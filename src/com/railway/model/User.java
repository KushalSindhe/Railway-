package com.railway.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a registered user (Passenger or Railway Administrator).
 */
public class User {
    private final String username;
    private String passwordHash;
    private String salt;
    private String fullName;
    private String email;
    private String phone;
    private final UserRole role;
    private final String authProvider;
    private final String avatarUrl;
    private final String googleId;
    private final LocalDateTime createdAt;

    public User(String username, String passwordHash, String salt,
                String fullName, String email, String phone, UserRole role) {
        this(username, passwordHash, salt, fullName, email, phone, role, "local", "", "");
    }

    public User(String username, String passwordHash, String salt,
                String fullName, String email, String phone, UserRole role,
                String authProvider, String avatarUrl, String googleId) {
        this.username = (username != null) ? username.trim().toLowerCase() : "";
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.fullName = (fullName != null) ? fullName.trim() : "";
        this.email = (email != null) ? email.trim() : "";
        this.phone = (phone != null) ? phone.trim() : "";
        this.role = (role != null) ? role : UserRole.PASSENGER;
        this.authProvider = (authProvider != null && !authProvider.trim().isEmpty()) ? authProvider.trim() : "local";
        this.avatarUrl = (avatarUrl != null) ? avatarUrl.trim() : "";
        this.googleId = (googleId != null) ? googleId.trim() : "";
        this.createdAt = LocalDateTime.now();
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        if (fullName != null && !fullName.trim().isEmpty()) {
            this.fullName = fullName.trim();
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (email != null && !email.trim().isEmpty()) {
            this.email = email.trim();
        }
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        if (phone != null) {
            this.phone = phone.trim();
        }
    }

    public void setPassword(String passwordHash, String salt) {
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getGoogleId() {
        return googleId;
    }

    public boolean isGoogleAuth() {
        return "google".equalsIgnoreCase(authProvider);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return String.format("User[@%s | %s | %s | %s]", username, fullName, role, email);
    }
}
