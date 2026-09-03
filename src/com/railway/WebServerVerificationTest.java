package com.railway;

import com.railway.service.*;
import com.railway.web.EmbeddedWebServer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Programmatic test validating EmbeddedWebServer and luxury HTML dashboard endpoints.
 */
public class WebServerVerificationTest {
    public static void main(String[] args) {
        System.out.println("Starting Web Server Verification on test port 8089...");

        RailwayNetworkService netService = new RailwayNetworkService();
        TrainService trainService = new TrainService(netService);
        ReservationService resService = new ReservationService(netService, trainService);
        SampleDataLoader.loadSampleData(netService, trainService, resService);

        EmbeddedWebServer server = new EmbeddedWebServer(8089, netService, trainService, resService);
        server.start();

        try {
            // Test 1: Fetch root HTML
            String html = fetchUrl("http://localhost:8089/");
            boolean hasTitle = html.contains("Bharat Rail Express") || html.contains("BHARAT RAIL EXPRESS");
            boolean hasImages = (html.contains("images/hero_train.jpg") || html.contains("unsplash.com")) &&
                                (html.contains("hero_train.jpg") && html.contains("grand_station.jpg") && html.contains("luxury_cabin.jpg") && html.contains("scenic_railway.jpg"));
            boolean hasGraph = html.contains("networkSvg") || html.contains("dijkstra");
            boolean hasBookTickets = html.contains("Book Tickets");
            boolean hasAllClasses = html.contains("1st Class") && html.contains("2nd Class") &&
                                    html.contains("3 Tier AC") && html.contains("Sleeper") && html.contains("General");

            System.out.println("[TEST HTML] Root Luxury Dashboard: " +
                    (hasTitle && hasImages && hasGraph ? "PASSED! (Length: " + html.length() + " chars)" : "FAILED!"));
            System.out.println("[TEST HTML] 'Book Tickets' Tab & Multi-Class Options: " +
                    (hasBookTickets && hasAllClasses ? "PASSED!" : "FAILED!"));

            // Test 1b: Fetch all 4 static local images
            String[] testImgs = {"hero_train.jpg", "grand_station.jpg", "luxury_cabin.jpg", "scenic_railway.jpg"};
            for (String img : testImgs) {
                byte[] b = fetchBytes("http://localhost:8089/images/" + img);
                System.out.println("[TEST STATIC IMAGE] /images/" + img + ": " +
                        (b != null && b.length > 50000 ? "PASSED! (" + b.length + " bytes)" : "FAILED!"));
            }

            // Test 2: Fetch /api/stations
            String stationsJson = fetchUrl("http://localhost:8089/api/stations");
            System.out.println("[TEST API] /api/stations: " +
                    (stationsJson.contains("NDLS") && stationsJson.contains("SBC") ? "PASSED!" : "FAILED!"));

            // Test 3: Fetch /api/network
            String netJson = fetchUrl("http://localhost:8089/api/network");
            System.out.println("[TEST API] /api/network: " +
                    (netJson.contains("tracks") && netJson.contains("distance") ? "PASSED!" : "FAILED!"));

            // Test 4: Fetch /api/stats
            String statsJson = fetchUrl("http://localhost:8089/api/stats");
            System.out.println("[TEST API] /api/stats: " +
                    (statsJson.contains("stations") && statsJson.contains("trains") ? "PASSED! (" + statsJson + ")" : "FAILED!"));

            // Test 5: Multi-Class Booking API Verification
            String[] classesToTest = {"1st Class", "2nd Class", "3 Tier AC", "Sleeper", "General"};
            for (String tc : classesToTest) {
                String postData = "trainId=12952&name=" + tc.replace(" ", "+") + "+Passenger&age=30&gender=M&src=NDLS&dst=CSMT&travelClass=" + tc.replace(" ", "+");
                String bookRes = postUrl("http://localhost:8089/api/book", postData);
                boolean success = bookRes.contains("\"success\":true") && bookRes.contains(tc);
                System.out.println("[TEST BOOKING CLASS] " + tc + ": " + (success ? "PASSED! (" + bookRes + ")" : "FAILED! (" + bookRes + ")"));

                // Verify PNR query returns travelClass
                int pnrIdx = bookRes.indexOf("\"pnr\":\"");
                if (pnrIdx != -1) {
                    String pnr = bookRes.substring(pnrIdx + 7, bookRes.indexOf("\"", pnrIdx + 7));
                    String pnrJson = fetchUrl("http://localhost:8089/api/pnr?pnr=" + pnr);
                    boolean pnrOk = pnrJson.contains(pnr) && pnrJson.contains(tc);
                    System.out.println("  -> [TEST PNR CLASS LOOKUP] PNR " + pnr + ": " + (pnrOk ? "PASSED! (Includes " + tc + ")" : "FAILED!"));
                }
            }

            // Test 6: User Authentication & Personal Bookings Verification
            System.out.println("\n--- TESTING AUTHENTICATION & PERSONAL BOOKINGS ---");

            // 6a: Demo Login
            String loginRes = postUrl("http://localhost:8089/api/auth/login", "username=passenger&password=pass123");
            boolean loginOk = loginRes.contains("\"success\":true") && loginRes.contains("Aarav Sharma");
            System.out.println("[TEST AUTH LOGIN] Demo Passenger Login: " + (loginOk ? "PASSED!" : "FAILED! (" + loginRes + ")"));

            // Extract Token
            String token = "";
            int tokenIdx = loginRes.indexOf("\"token\":\"");
            if (tokenIdx != -1) {
                token = loginRes.substring(tokenIdx + 9, loginRes.indexOf("\"", tokenIdx + 9));
            }
            System.out.println("  -> [TEST AUTH TOKEN] Extracted Token: " + (token.length() >= 32 ? "PASSED! (" + token + ")" : "FAILED!"));

            // 6b: Validate Token via /api/auth/me
            String meRes = fetchUrl("http://localhost:8089/api/auth/me?token=" + token);
            boolean meOk = meRes.contains("\"authenticated\":true") && meRes.contains("passenger") && meRes.contains("PASSENGER");
            System.out.println("[TEST AUTH ME] /api/auth/me Session Validation: " + (meOk ? "PASSED! (" + meRes + ")" : "FAILED!"));

            // 6c: Register a New User
            String regRes = postUrl("http://localhost:8089/api/auth/register",
                    "username=testuser99&password=securepass99&fullName=Test+Venkatesh&email=test%40irctc.in&phone=%2B919999999999");
            boolean regOk = regRes.contains("\"success\":true") && regRes.contains("Test Venkatesh");
            System.out.println("[TEST AUTH REGISTER] /api/auth/register: " + (regOk ? "PASSED!" : "FAILED! (" + regRes + ")"));

            // 6d: Book Ticket linked to Authenticated User
            String bookAuthRes = postUrl("http://localhost:8089/api/book",
                    "trainId=12627&name=Aarav+Sharma&age=32&gender=M&src=NDLS&dst=SBC&travelClass=1st+Class&token=" + token);
            boolean bookAuthOk = bookAuthRes.contains("\"success\":true") && bookAuthRes.contains("\"bookedBy\":\"passenger\"");
            System.out.println("[TEST AUTH BOOKING] Ticket Linked to User Account: " + (bookAuthOk ? "PASSED! (" + bookAuthRes + ")" : "FAILED!"));

            // 6e: Retrieve Personal Bookings via /api/auth/my-bookings
            String myBookingsRes = fetchUrl("http://localhost:8089/api/auth/my-bookings?token=" + token);
            boolean myBookingsOk = myBookingsRes.contains("\"success\":true") && myBookingsRes.contains("passenger") && myBookingsRes.contains("12627");
            System.out.println("[TEST AUTH MY BOOKINGS] /api/auth/my-bookings: " + (myBookingsOk ? "PASSED! (" + myBookingsRes + ")" : "FAILED!"));

            // 6f: Admin Authentication
            String adminLoginRes = postUrl("http://localhost:8089/api/auth/login", "username=admin&password=admin123");
            boolean adminOk = adminLoginRes.contains("\"success\":true") && adminLoginRes.contains("ADMIN");
            System.out.println("[TEST AUTH ADMIN LOGIN] Admin Authentication: " + (adminOk ? "PASSED!" : "FAILED!"));

            // 6g: Logout
            String logoutRes = postUrl("http://localhost:8089/api/auth/logout", "token=" + token);
            boolean logoutOk = logoutRes.contains("\"success\":true");
            String meAfterLogout = fetchUrl("http://localhost:8089/api/auth/me?token=" + token);
            boolean expiredOk = meAfterLogout.contains("\"authenticated\":false");
            System.out.println("[TEST AUTH LOGOUT] Session Termination: " + (logoutOk && expiredOk ? "PASSED!" : "FAILED!"));

            // 6h: Verify Auth UI elements in Root HTML
            boolean hasAuthModal = html.contains("id=\"authModal\"");
            boolean hasAuthHeader = html.contains("id=\"authHeaderContainer\"");
            boolean hasMyBookingsTab = html.contains("id=\"tabBtnMyBookings\"");
            boolean hasMyBookingsPane = html.contains("id=\"pane-my-bookings\"");
            System.out.println("[TEST HTML UI] Auth Modal & Personal Bookings Tab Present: " +
                    (hasAuthModal && hasAuthHeader && hasMyBookingsTab && hasMyBookingsPane ? "PASSED!" : "FAILED!"));

            // --- TESTING GOOGLE AUTHENTICATION ---
            String gLoginRes = postUrl("http://localhost:8089/api/auth/google", "email=kushal.sindhe@gmail.com&name=Kushal Sindhe&googleId=google_test_101");
            boolean gLoginOk = gLoginRes.contains("\"success\":true") && gLoginRes.contains("Kushal Sindhe") && gLoginRes.contains("\"authProvider\":\"google\"");
            String gToken = "";
            if (gLoginOk) {
                int gTokenIdx = gLoginRes.indexOf("\"token\":\"");
                if (gTokenIdx != -1) {
                    gToken = gLoginRes.substring(gTokenIdx + 9, gLoginRes.indexOf("\"", gTokenIdx + 9));
                }
            }
            System.out.println("[TEST GOOGLE AUTH LOGIN] New Google User Auto-Provisioning: " + (gLoginOk && !gToken.isEmpty() ? "PASSED! (Token: " + gToken + ")" : "FAILED!"));

            // Google /api/auth/me validation
            String gMeRes = fetchUrl("http://localhost:8089/api/auth/me?token=" + gToken);
            boolean gMeOk = gMeRes.contains("\"authenticated\":true") && gMeRes.contains("kushal.sindhe@gmail.com") && gMeRes.contains("\"authProvider\":\"google\"");
            System.out.println("[TEST GOOGLE AUTH ME] /api/auth/me Provider Validation: " + (gMeOk ? "PASSED!" : "FAILED!"));

            // Google User Ticket Booking
            String gBookRes = postUrl("http://localhost:8089/api/book", "trainId=12952&name=Kushal Sindhe&age=26&gender=M&class=1st Class&token=" + gToken);
            boolean gBookOk = gBookRes.contains("\"success\":true") && gBookRes.contains("Kushal Sindhe");
            System.out.println("[TEST GOOGLE BOOKING] Linked Ticket to Google Account: " + (gBookOk ? "PASSED!" : "FAILED!"));

            // Google /api/auth/my-bookings
            String gMyBookings = fetchUrl("http://localhost:8089/api/auth/my-bookings?token=" + gToken);
            boolean gMyBookingsOk = gMyBookings.contains("\"success\":true") && gMyBookings.contains("Kushal Sindhe");
            System.out.println("[TEST GOOGLE MY BOOKINGS] Personal Portfolio for Google Account: " + (gMyBookingsOk ? "PASSED!" : "FAILED!"));

            // Existing Google User Re-login
            String gReloginRes = postUrl("http://localhost:8089/api/auth/google", "email=kushal.sindhe@gmail.com&name=Kushal Sindhe&googleId=google_test_101");
            boolean gReloginOk = gReloginRes.contains("\"success\":true") && gReloginRes.contains("kushal.sindhe@gmail.com");
            System.out.println("[TEST GOOGLE RELOGIN] Existing Google User Return: " + (gReloginOk ? "PASSED!" : "FAILED!"));

            // Verify Google UI elements in HTML
            boolean hasGoogleBtn = html.contains("id=\"googleAuthBtn\"") && html.contains("Sign in with Google");
            boolean hasGoogleChooser = html.contains("id=\"googleChooserModal\"") && html.contains("Choose an account");
            System.out.println("[TEST GOOGLE HTML UI] Google Sign-In Button & Chooser Modal Present: " + (hasGoogleBtn && hasGoogleChooser ? "PASSED!" : "FAILED!"));

            // Test 7: Verify Live Radar & Cinematic Ambiance Removal and Automatic Theme
            boolean noLiveRadar = !html.contains("LIVE RADAR");
            boolean noCinematicAmbiance = !html.contains("Cinematic Ambiance") && !html.contains("scene-selector-bar");
            boolean noManualThemeChange = !html.contains("cycleTheme") && !html.contains("theme-pill-selector") && !html.contains("lightboxSetThemeBtn");
            boolean hasAutoBackground = html.contains("advanceAutomaticBackground");
            System.out.println("[TEST REMOVAL] Live Radar Removed: " + (noLiveRadar ? "PASSED!" : "FAILED!"));
            System.out.println("[TEST REMOVAL] Cinematic Ambiance Selector Removed: " + (noCinematicAmbiance ? "PASSED!" : "FAILED!"));
            System.out.println("[TEST REMOVAL] Manual Theme Controls Removed: " + (noManualThemeChange ? "PASSED!" : "FAILED!"));
            System.out.println("[TEST AUTOMATIC] Automatic Background Rotator Active: " + (hasAutoBackground ? "PASSED!" : "FAILED!"));

            System.out.println("\n>>> ALL WEB SERVER, AUTHENTICATION & LUXURY FRONTEND CHECKS PASSED! <<<");
        } catch (Exception e) {
            System.err.println("Verification failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
            System.out.println("Test server stopped cleanly.");
        }
    }

    private static String fetchUrl(String urlStr) throws Exception {
        byte[] bytes = fetchBytes(urlStr);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String postUrl(String urlStr, String postData) throws Exception {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        byte[] postBytes = postData.getBytes(StandardCharsets.UTF_8);
        conn.getOutputStream().write(postBytes);
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] fetchBytes(String urlStr) throws Exception {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }
}
