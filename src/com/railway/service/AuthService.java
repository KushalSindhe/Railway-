package com.railway.service;

import com.railway.model.AuthSession;
import com.railway.model.User;
import com.railway.model.UserRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing user registration, authentication, password security,
 * and active session tokens.
 */
public class AuthService {
    private final Map<String, User> userMap = new ConcurrentHashMap<>();
    private final Map<String, AuthSession> sessionMap = new ConcurrentHashMap<>();

    public AuthService() {
        seedDefaultAccounts();
    }

    private void seedDefaultAccounts() {
        // 1. Pre-seeded Administrator
        registerInternal("admin", "admin123", "Railway Administrator", "admin@irctc.gov.in", "+91 11 2334 0000", UserRole.ADMIN);

        // 2. Pre-seeded Regular Passenger
        registerInternal("passenger", "pass123", "Aarav Sharma", "aarav@gmail.com", "+91 98765 43210", UserRole.PASSENGER);

        // 3. Pre-seeded Royal Executive VIP Passenger
        registerInternal("royal", "royal123", "Maharani Gayatri Devi", "royal@saloon.in", "+91 99999 88888", UserRole.PASSENGER);
    }

    private User registerInternal(String username, String rawPassword, String fullName, String email, String phone, UserRole role) {
        String salt = UUID.randomUUID().toString().substring(0, 8);
        String hash = hashPassword(rawPassword, salt);
        User user = new User(username, hash, salt, fullName, email, phone, role);
        userMap.put(user.getUsername(), user);
        return user;
    }

    public synchronized User register(String username, String rawPassword, String fullName, String email, String phone, UserRole role) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        String cleanUsername = username.trim().toLowerCase();
        if (cleanUsername.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters long.");
        }
        if (userMap.containsKey(cleanUsername)) {
            throw new IllegalArgumentException("Username '" + cleanUsername + "' is already taken.");
        }
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters long.");
        }

        return registerInternal(cleanUsername, rawPassword, fullName, email, phone, role != null ? role : UserRole.PASSENGER);
    }

    public AuthSession login(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            throw new IllegalArgumentException("Username and password are required.");
        }
        String cleanUsername = username.trim().toLowerCase();
        User user = userMap.get(cleanUsername);
        if (user == null) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        String computedHash = hashPassword(rawPassword, user.getSalt());
        if (!computedHash.equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        // Generate session token
        String token = "TOKEN-" + UUID.randomUUID().toString().replace("-", "");
        AuthSession session = new AuthSession(token, user);
        sessionMap.put(token, session);
        return session;
    }

    /**
     * Authenticates or auto-provisions a user via Google Account credentials.
     */
    public synchronized AuthSession loginWithGoogle(String email, String fullName, String googleId, String avatarUrl) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Google email is required.");
        }
        String cleanEmail = email.trim().toLowerCase();
        String cleanFullName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : cleanEmail.split("@")[0];
        String cleanGoogleId = (googleId != null && !googleId.trim().isEmpty()) ? googleId.trim() : UUID.randomUUID().toString();
        String cleanAvatar = (avatarUrl != null) ? avatarUrl.trim() : "";

        // Check if user already exists with this email or googleId
        User existingUser = null;
        for (User u : userMap.values()) {
            if (cleanEmail.equalsIgnoreCase(u.getEmail()) || (cleanGoogleId.equals(u.getGoogleId()) && !cleanGoogleId.isEmpty())) {
                existingUser = u;
                break;
            }
        }

        if (existingUser == null) {
            // Derive a unique username, e.g. from email prefix
            String baseUsername = cleanEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
            if (baseUsername.length() < 3) baseUsername = "google_" + baseUsername;
            String usernameCandidate = baseUsername;
            int counter = 1;
            while (userMap.containsKey(usernameCandidate)) {
                usernameCandidate = baseUsername + "_" + counter++;
            }

            // Generate random password salt/hash for Google-provisioned account
            String salt = UUID.randomUUID().toString().substring(0, 8);
            String rawPassword = UUID.randomUUID().toString();
            String hash = hashPassword(rawPassword, salt);

            existingUser = new User(usernameCandidate, hash, salt, cleanFullName, cleanEmail, "", UserRole.PASSENGER, "google", cleanAvatar, cleanGoogleId);
            userMap.put(existingUser.getUsername(), existingUser);
        }

        // Generate session token
        String token = "TOKEN-" + UUID.randomUUID().toString().replace("-", "");
        AuthSession session = new AuthSession(token, existingUser);
        sessionMap.put(token, session);
        return session;
    }

    public User validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String cleanToken = token.trim();
        if (cleanToken.startsWith("Bearer ")) {
            cleanToken = cleanToken.substring(7).trim();
        }

        AuthSession session = sessionMap.get(cleanToken);
        if (session == null) {
            return null;
        }
        session.touch();
        return session.getUser();
    }

    public boolean logout(String token) {
        if (token == null) return false;
        String cleanToken = token.trim();
        if (cleanToken.startsWith("Bearer ")) {
            cleanToken = cleanToken.substring(7).trim();
        }
        return sessionMap.remove(cleanToken) != null;
    }

    public User getUser(String username) {
        if (username == null) return null;
        return userMap.get(username.trim().toLowerCase());
    }

    public boolean userExists(String username) {
        if (username == null) return false;
        return userMap.containsKey(username.trim().toLowerCase());
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(userMap.values());
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = salt + ":" + password;
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
