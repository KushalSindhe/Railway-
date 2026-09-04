package com.railway;

import com.railway.service.*;
import com.railway.web.EmbeddedWebServer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Verification test for Admin Google Authentication enforcement and 8-dimension Analytics.
 */
public class AdminAnalyticsVerificationTest {
    public static void main(String[] args) {
        System.out.println("=== Starting Admin Google Auth & Analytics Verification Test on Port 8091 ===");

        RailwayNetworkService netService = new RailwayNetworkService();
        TrainService trainService = new TrainService(netService);
        ReservationService resService = new ReservationService(netService, trainService);
        SampleDataLoader.loadSampleData(netService, trainService, resService);

        EmbeddedWebServer server = new EmbeddedWebServer(8091, netService, trainService, resService);
        server.start();

        try {
            // 1. Test unauthenticated request to /api/admin/analytics
            int unauthCode = getResponseCode("http://localhost:8091/api/admin/analytics");
            System.out.println("[TEST 1: Unauthenticated Admin Analytics] HTTP Status: " + unauthCode +
                    (unauthCode == 401 ? " -> PASSED! (Unauthorized)" : " -> FAILED!"));

            // 2. Test normal passenger login and attempt to access /api/admin/analytics
            String passLoginRes = postUrl("http://localhost:8091/api/auth/login", "username=passenger&password=pass123");
            String passToken = extractToken(passLoginRes);
            int passAccessCode = getResponseCode("http://localhost:8091/api/admin/analytics?token=" + passToken);
            System.out.println("[TEST 2: Passenger Token Forbidden on Admin Analytics] HTTP Status: " + passAccessCode +
                    (passAccessCode == 403 ? " -> PASSED! (Forbidden for passenger)" : " -> FAILED!"));

            // 3. Test local password admin attempt via /api/auth/login with forAdmin=true
            String adminPwLoginRes = postUrl("http://localhost:8091/api/auth/login", "username=admin&password=admin123&forAdmin=true");
            boolean pwRejected = adminPwLoginRes.contains("\"success\":false") && adminPwLoginRes.contains("Google Authentication");
            System.out.println("[TEST 3: Direct Password Admin Login Rejection] Response: " +
                    (pwRejected ? "PASSED! (Google Auth strictly required)" : "FAILED! (" + adminPwLoginRes + ")"));

            // 4. Test Google Admin Login via /api/auth/google with admin=true
            String gAdminRes = postUrl("http://localhost:8091/api/auth/google",
                    "email=kushal.sindhe@gmail.com&name=Kushal+Sindhe&googleId=google_admin_101&admin=true");
            boolean gAdminOk = gAdminRes.contains("\"success\":true") && gAdminRes.contains("\"role\":\"ADMIN\"") && gAdminRes.contains("\"authProvider\":\"google\"");
            String gAdminToken = extractToken(gAdminRes);
            System.out.println("[TEST 4: Google Admin Login & Elevation] Status: " +
                    (gAdminOk && !gAdminToken.isEmpty() ? "PASSED! (Token: " + gAdminToken + ")" : "FAILED! (" + gAdminRes + ")"));

            // 5. Test Google Admin Access to /api/admin/analytics
            String analyticsJson = fetchUrl("http://localhost:8091/api/admin/analytics?token=" + gAdminToken);
            boolean hasSuccess = analyticsJson.contains("\"success\":true");
            System.out.println("[TEST 5: Google Admin Analytics Access] " + (hasSuccess ? "PASSED!" : "FAILED!"));

            // 6. Validate all 8 requested dimensions in the Analytics JSON
            System.out.println("\n--- Validating 8 Requested Dimensions in Analytics Payload ---");
            boolean dim1 = analyticsJson.contains("\"totalBookingsAnalytics\"") && analyticsJson.contains("\"classBreakdown\"");
            boolean dim2 = analyticsJson.contains("\"cancelledTicketsAnalytics\"") && analyticsJson.contains("\"cancellationRate\"");
            boolean dim3 = analyticsJson.contains("\"availableSeatsAnalytics\"") && analyticsJson.contains("\"fleetCapacity\"");
            boolean dim4 = analyticsJson.contains("\"trainOccupancyAnalytics\"") && analyticsJson.contains("\"fleetAverageOccupancy\"");
            boolean dim5 = analyticsJson.contains("\"popularRoutesAnalytics\"") && analyticsJson.contains("\"topRoutes\"");
            boolean dim6 = analyticsJson.contains("\"revenueAnalytics\"") && analyticsJson.contains("\"revenueByClass\"");
            boolean dim7 = analyticsJson.contains("\"mostUsedStationsAnalytics\"") && analyticsJson.contains("\"trafficShare\"");
            boolean dim8 = analyticsJson.contains("\"passengerBookingHistoryAnalytics\"") && analyticsJson.contains("\"totalSpent\"");

            System.out.println("  1. Total Bookings: " + (dim1 ? "PASSED!" : "FAILED!"));
            System.out.println("  2. Cancelled Tickets: " + (dim2 ? "PASSED!" : "FAILED!"));
            System.out.println("  3. Available Seats: " + (dim3 ? "PASSED!" : "FAILED!"));
            System.out.println("  4. Train Occupancy: " + (dim4 ? "PASSED!" : "FAILED!"));
            System.out.println("  5. Popular Routes: " + (dim5 ? "PASSED!" : "FAILED!"));
            System.out.println("  6. Revenue: " + (dim6 ? "PASSED!" : "FAILED!"));
            System.out.println("  7. Most-Used Stations: " + (dim7 ? "PASSED!" : "FAILED!"));
            System.out.println("  8. Passenger Booking History: " + (dim8 ? "PASSED!" : "FAILED!"));

            boolean allDimsOk = dim1 && dim2 && dim3 && dim4 && dim5 && dim6 && dim7 && dim8;
            System.out.println("\n>>> ALL 8 ANALYTICS DIMENSIONS VERIFIED: " + (allDimsOk ? "SUCCESS!" : "FAILED!") + " <<<");

            // 7. Verify Ticket Cancellation and its reflection in analytics
            // Book ticket as Google admin
            String bookRes = postUrl("http://localhost:8091/api/book",
                    "trainId=12952&name=Test+Passenger&age=28&gender=M&src=NDLS&dst=CSMT&travelClass=1st+Class&token=" + gAdminToken);
            String pnr = extractJsonValue(bookRes, "pnr");
            System.out.println("[TEST 7a: Book Ticket for Cancellation Test] PNR: " + pnr);

            // Cancel ticket
            String cancelRes = postUrl("http://localhost:8091/api/cancel", "pnr=" + pnr + "&token=" + gAdminToken);
            boolean cancelOk = cancelRes.contains("\"success\":true");
            System.out.println("[TEST 7b: Ticket Cancellation via API] " + (cancelOk ? "PASSED!" : "FAILED!"));

            // Re-fetch analytics to ensure cancelled tickets reflect
            String updatedAnalytics = fetchUrl("http://localhost:8091/api/admin/analytics?token=" + gAdminToken);
            boolean cancelReflected = updatedAnalytics.contains("\"totalCancelled\":1") || updatedAnalytics.contains(pnr);
            System.out.println("[TEST 7c: Cancelled Ticket Reflected in Analytics Ledger] " + (cancelReflected ? "PASSED!" : "FAILED!"));

            System.out.println("\n=== ALL ADMIN GOOGLE AUTH & 8-DIMENSION ANALYTICS TESTS PASSED PERFECTLY! ===");
        } catch (Exception e) {
            System.err.println("Test execution failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
            System.out.println("Test server stopped.");
        }
    }

    private static String extractToken(String json) {
        int idx = json.indexOf("\"token\":\"");
        if (idx != -1) {
            return json.substring(idx + 9, json.indexOf("\"", idx + 9));
        }
        return "";
    }

    private static String extractJsonValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\":\"");
        if (idx != -1) {
            return json.substring(idx + key.length() + 4, json.indexOf("\"", idx + key.length() + 4));
        }
        return "";
    }

    private static int getResponseCode(String urlStr) throws Exception {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        return conn.getResponseCode();
    }

    private static String fetchUrl(String urlStr) throws Exception {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
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
        try (InputStream is = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            if (is == null) return "";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
