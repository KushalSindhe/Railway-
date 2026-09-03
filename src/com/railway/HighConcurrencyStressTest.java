package com.railway;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class HighConcurrencyStressTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static final int TOTAL_OPERATIONS = 10000;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("==========================================================================");
        System.out.println("     10,000 SIMULTANEOUS USERS HIGH-CONCURRENCY STRESS & INTEGRITY TEST   ");
        System.out.println("       Architecture: Java 21 Virtual Threads & Non-Blocking Web Server     ");
        System.out.println("==========================================================================");
        System.out.println("Target: " + BASE_URL);
        System.out.println("Total Concurrent Operations: " + TOTAL_OPERATIONS);

        // Pre-warm the server
        httpGet(BASE_URL + "/api/stations");
        httpGet(BASE_URL + "/api/trains");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger bookingSuccessCount = new AtomicInteger(0);
        AtomicInteger pnrQuerySuccessCount = new AtomicInteger(0);
        AtomicInteger routeQuerySuccessCount = new AtomicInteger(0);
        AtomicInteger catalogSuccessCount = new AtomicInteger(0);

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(TOTAL_OPERATIONS));
        ConcurrentLinkedQueue<String> createdPnrs = new ConcurrentLinkedQueue<>();

        String[] stationCodes = new String[] {
            "NDLS", "CSMT", "SBC", "MAS", "HWH", "ADI", "JP", "PUNE",
            "BPL", "NGP", "HYB", "BZA", "LKO", "BSB", "PNBE", "GHY",
            "MAO", "ERS", "TVC", "ASR", "CDG", "JAT", "BBS", "ST"
        };
        String[] travelClasses = new String[] { "1st Class", "2nd Class", "3 Tier AC", "Sleeper", "General" };
        String[] quotas = new String[] { "GENERAL", "EMERGENCY" };

        long startTime = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(TOTAL_OPERATIONS);

            for (int i = 0; i < TOTAL_OPERATIONS; i++) {
                final int opId = i;
                futures.add(executor.submit(() -> {
                    long opStart = System.nanoTime();
                    try {
                        int opType = opId % 10;
                        if (opType < 4) {
                            // 40% (4,000): Bookings across any destination
                            String src = stationCodes[opId % stationCodes.length];
                            String dst = stationCodes[(opId * 7 + 1) % stationCodes.length];
                            if (src.equals(dst)) {
                                dst = stationCodes[(opId * 7 + 2) % stationCodes.length];
                            }
                            String travelClass = travelClasses[opId % travelClasses.length];
                            String quota = quotas[opId % quotas.length];
                            String name = "User_" + opId;

                            String body = "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                                    + "&age=" + (20 + (opId % 50))
                                    + "&gender=" + (opId % 2 == 0 ? "M" : "F")
                                    + "&src=" + src
                                    + "&dst=" + dst
                                    + "&travelClass=" + URLEncoder.encode(travelClass, StandardCharsets.UTF_8)
                                    + "&quota=" + quota;

                            String res = httpPost(BASE_URL + "/api/book", body);
                            if (res.contains("\"success\":true")) {
                                successCount.incrementAndGet();
                                bookingSuccessCount.incrementAndGet();
                                // Extract PNR
                                int pnrIdx = res.indexOf("\"pnr\":\"");
                                if (pnrIdx != -1) {
                                    int pnrEnd = res.indexOf("\"", pnrIdx + 7);
                                    if (pnrEnd != -1) {
                                        createdPnrs.add(res.substring(pnrIdx + 7, pnrEnd));
                                    }
                                }
                            } else {
                                failureCount.incrementAndGet();
                                System.err.println("Booking fail for " + src + " -> " + dst + ": " + res);
                            }
                        } else if (opType < 7) {
                            // 30% (3,000): PNR Inquiries
                            String queryPnr = createdPnrs.peek();
                            if (queryPnr == null) queryPnr = "PNR-100101";
                            String res = httpGet(BASE_URL + "/api/pnr?pnr=" + queryPnr);
                            if (res.contains("\"found\":")) {
                                successCount.incrementAndGet();
                                pnrQuerySuccessCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        } else if (opType < 9) {
                            // 20% (2,000): Route Dijkstra calculations across Pan-India tracks
                            String src = stationCodes[opId % stationCodes.length];
                            String dst = stationCodes[(opId + 3) % stationCodes.length];
                            String res = httpGet(BASE_URL + "/api/route?src=" + src + "&dst=" + dst);
                            if (res.contains("\"distance\":") || res.contains("\"reachable\":")) {
                                successCount.incrementAndGet();
                                routeQuerySuccessCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        } else {
                            // 10% (1,000): Station catalog & train searches
                            String res = httpGet(BASE_URL + "/api/stations");
                            if (res.contains("\"id\":\"NDLS\"")) {
                                successCount.incrementAndGet();
                                catalogSuccessCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        }
                    } catch (Exception ex) {
                        int f = failureCount.incrementAndGet();
                        if (f <= 5) {
                            System.err.println("Failure #" + f + ": " + ex.getClass().getName() + " -> " + ex.getMessage());
                        }
                    } finally {
                        latencies.add((System.nanoTime() - opStart) / 1_000_000); // ms
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }
        }

        long totalDurationMs = System.currentTimeMillis() - startTime;
        double throughput = (TOTAL_OPERATIONS * 1000.0) / totalDurationMs;

        Collections.sort(latencies);
        long minLatency = latencies.isEmpty() ? 0 : latencies.get(0);
        long maxLatency = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95Latency = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.95));
        long p99Latency = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));

        System.out.println("--------------------------------------------------------------------------");
        System.out.println("RESULTS:");
        System.out.println("  Total Operations Submitted: " + TOTAL_OPERATIONS);
        System.out.println("  Successful Operations:      " + successCount.get() + " (" + String.format("%.2f", (successCount.get() * 100.0 / TOTAL_OPERATIONS)) + "%)");
        System.out.println("  Failed Operations:          " + failureCount.get());
        System.out.println("  Breakdown:");
        System.out.println("    - Bookings from Any Destination: " + bookingSuccessCount.get() + " successful");
        System.out.println("    - PNR Manifest Inquiries:        " + pnrQuerySuccessCount.get() + " successful");
        System.out.println("    - Real-Time Dijkstra Routes:     " + routeQuerySuccessCount.get() + " successful");
        System.out.println("    - Network Catalogs & Telemetry:  " + catalogSuccessCount.get() + " successful");
        System.out.println("  Performance Metrics:");
        System.out.println("    - Total Wall-Clock Time:  " + totalDurationMs + " ms (" + String.format("%.2f", totalDurationMs / 1000.0) + " s)");
        System.out.println("    - System Throughput:      " + String.format("%.1f", throughput) + " req/sec");
        System.out.println("    - Average Latency:        " + String.format("%.2f", avgLatency) + " ms");
        System.out.println("    - P95 Latency:            " + p95Latency + " ms");
        System.out.println("    - P99 Latency:            " + p99Latency + " ms");
        System.out.println("    - Min Latency:            " + minLatency + " ms");
        System.out.println("    - Max Latency:            " + maxLatency + " ms");
        System.out.println("==========================================================================");

        if (failureCount.get() > 0) {
            System.err.println("TEST FAILED: Experienced " + failureCount.get() + " failures.");
            System.exit(1);
        } else {
            System.out.println(">>> 10,000 SIMULTANEOUS USERS STRESS TEST PASSED WITH 100% SUCCESS! <<<");
        }
    }

    private static String httpGet(String urlStr) throws Exception {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                return response.body();
            } catch (Exception e) {
                if (attempt == maxRetries) throw e;
                Thread.sleep(15 * attempt);
            }
        }
        return "";
    }

    private static String httpPost(String urlStr, String body) throws Exception {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                return response.body();
            } catch (Exception e) {
                if (attempt == maxRetries) throw e;
                Thread.sleep(15 * attempt);
            }
        }
        return "";
    }
}
