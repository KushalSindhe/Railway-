package com.railway.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages environment configurations loaded from .env file and system environment variables.
 * Automatically checks timestamp to reload dynamically if .env is edited at runtime.
 */
public class EnvConfig {
    private static final Map<String, String> envMap = new HashMap<>();
    private static long lastLoadedTime = 0;
    private static File activeEnvFile = null;

    static {
        loadEnv();
    }

    public static synchronized void loadEnv() {
        // Candidate locations for .env
        File[] candidates = new File[] {
                new File(".env"),
                new File("../.env"),
                new File(System.getProperty("user.dir"), ".env")
        };

        File found = null;
        for (File c : candidates) {
            if (c.exists() && c.isFile()) {
                found = c;
                break;
            }
        }

        if (found == null) {
            activeEnvFile = null;
            return;
        }

        activeEnvFile = found;
        lastLoadedTime = found.lastModified();
        envMap.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(found, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    // Strip surrounding quotes if present
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                    }
                    envMap.put(key, val);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not read .env file: " + e.getMessage());
        }
    }

    private static synchronized void checkReload() {
        if (activeEnvFile != null && activeEnvFile.exists()) {
            if (activeEnvFile.lastModified() > lastLoadedTime) {
                loadEnv();
            }
        } else {
            // Check if .env file was newly created
            File f = new File(".env");
            if (f.exists() && f.isFile()) {
                loadEnv();
            }
        }
    }

    public static String get(String key) {
        return get(key, "");
    }

    public static String get(String key, String defaultValue) {
        checkReload();
        // Priority 1: .env file
        String val = envMap.get(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        // Priority 2: System Environment variable
        val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        // Priority 3: System property
        val = System.getProperty(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        return defaultValue;
    }

    public static String getGoogleClientId() {
        return get("GOOGLE_CLIENT_ID", "");
    }

    public static String getGoogleClientSecret() {
        return get("GOOGLE_CLIENT_SECRET", "");
    }

    public static String getGoogleRedirectUri() {
        return get("GOOGLE_REDIRECT_URI", "http://localhost:8080/api/auth/google/callback");
    }

    public static boolean isGoogleConfigured() {
        String clientId = getGoogleClientId();
        if (clientId.isEmpty()) return false;
        if (clientId.contains("your_google_client_id") || clientId.equals("YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com")) {
            return false;
        }
        return clientId.endsWith(".apps.googleusercontent.com") || clientId.length() > 20;
    }
}
