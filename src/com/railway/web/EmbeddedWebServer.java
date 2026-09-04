package com.railway.web;

import com.railway.dsa.DijkstraResult;
import com.railway.model.*;
import com.railway.service.AuthService;
import com.railway.service.EnvConfig;
import com.railway.service.IrctcUserDatasetService;
import com.railway.service.RailwayNetworkService;
import com.railway.service.ReservationService;
import com.railway.service.TrainService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Built-in zero-dependency HTTP server and visual web dashboard.
 * Uses standard JDK com.sun.net.httpserver.HttpServer.
 */
public class EmbeddedWebServer {
    private final int port;
    private final RailwayNetworkService networkService;
    private final TrainService trainService;
    private final ReservationService reservationService;
    private final AuthService authService;
    private final IrctcUserDatasetService irctcUserDatasetService;
    private HttpServer server;

    // Dynamic Stochastic Real-Time Train Delay Engine
    public static class TrainDelayInfo {
        public final String trainId;
        public final int delayMinutes;
        public final String reason;
        public final long expiresAt;

        public TrainDelayInfo(String trainId, int delayMinutes, String reason, long expiresAt) {
            this.trainId = trainId;
            this.delayMinutes = delayMinutes;
            this.reason = reason;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, TrainDelayInfo> activeDynamicDelays = new ConcurrentHashMap<>();
    private volatile long lastDynamicDelaySelection = 0;
    private final Random delayRandom = new Random();
    private static final String[] DELAY_REASONS = {
        "Signal Clearance Hold at Outer Cabin",
        "Track Maintenance Caution & Speed Restriction",
        "Precedence Crossing for Superfast Vande Bharat",
        "Locomotive Traction Motor Diagnostic Check",
        "Dense Fog / Restricted Corridor Visibility",
        "Overhead Equipment (OHE) Voltage Inspection",
        "Platform Turnaround & Shunting Clearance Delay"
    };

    public synchronized List<TrainDelayInfo> triggerMultipleTrainDelays(int count) {
        List<Train> all = new ArrayList<>(trainService.getAllTrains());
        if (all.isEmpty()) return Collections.emptyList();

        // Shuffle to randomly select distinct express trains
        Collections.shuffle(all, delayRandom);
        int toDelay = Math.min(Math.max(1, count), all.size());
        List<TrainDelayInfo> delayedList = new ArrayList<>();

        for (int i = 0; i < toDelay; i++) {
            Train target = all.get(i);
            int delayMin = 14 + delayRandom.nextInt(46); // 14 to 59 minutes
            String reason = DELAY_REASONS[delayRandom.nextInt(DELAY_REASONS.length)];
            long expiresAt = System.currentTimeMillis() + (85 * 1000) + delayRandom.nextInt(40 * 1000); // Active 85-125s
            TrainDelayInfo info = new TrainDelayInfo(target.getId(), delayMin, reason, expiresAt);
            activeDynamicDelays.put(target.getId(), info);
            delayedList.add(info);
        }
        return delayedList;
    }

    public synchronized TrainDelayInfo triggerRandomTrainDelay() {
        List<TrainDelayInfo> list = triggerMultipleTrainDelays(1);
        return list.isEmpty() ? null : list.get(0);
    }

    public EmbeddedWebServer(int port,
                             RailwayNetworkService networkService,
                             TrainService trainService,
                             ReservationService reservationService,
                             AuthService authService) {
        this.port = port;
        this.networkService = networkService;
        this.trainService = trainService;
        this.reservationService = reservationService;
        this.authService = authService != null ? authService : new AuthService();
        this.irctcUserDatasetService = new IrctcUserDatasetService(this.authService);
    }

    public EmbeddedWebServer(int port,
                             RailwayNetworkService networkService,
                             TrainService trainService,
                             ReservationService reservationService) {
        this(port, networkService, trainService, reservationService, new AuthService());
    }

    private final ScheduledExecutorService autoSimulator = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Railway-Auto-Simulator");
        t.setDaemon(true);
        return t;
    });

    private void startAutoSimulation() {
        // Run every 4 seconds: automatically fluctuates confirmed seats & waitlists
        autoSimulator.scheduleAtFixedRate(() -> {
            try {
                trainService.fluctuateAllTrainInventories();
            } catch (Exception ignored) {}
        }, 3, 4, TimeUnit.SECONDS);

        // Run every 18 seconds: periodically simulates realistic active express route delays & recoveries
        autoSimulator.scheduleAtFixedRate(() -> {
            try {
                int delayedCount = 2 + delayRandom.nextInt(3); // 2 to 4 trains
                triggerMultipleTrainDelays(delayedCount);
            } catch (Exception ignored) {}
        }, 10, 18, TimeUnit.SECONDS);

        // Run every 45 seconds: periodic dynamic demand cycle refresh across trains
        autoSimulator.scheduleAtFixedRate(() -> {
            try {
                trainService.randomizeAllTrainInventories();
            } catch (Exception ignored) {}
        }, 45, 45, TimeUnit.SECONDS);
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new StaticDashboardHandler());
            server.createContext("/api/stations", new StationsApiHandler());
            server.createContext("/api/trains", new TrainsApiHandler());
            server.createContext("/api/route", new RouteApiHandler());
            server.createContext("/api/pnr", new PnrApiHandler());
            server.createContext("/api/book", new BookApiHandler());
            server.createContext("/api/cancel", new CancelApiHandler());
            server.createContext("/api/network", new NetworkApiHandler());
            server.createContext("/api/stats", new StatsApiHandler());
            server.createContext("/api/auth/register", new AuthRegisterApiHandler());
            server.createContext("/api/auth/login", new AuthLoginApiHandler());
            server.createContext("/api/auth/logout", new AuthLogoutApiHandler());
            server.createContext("/api/auth/me", new AuthMeApiHandler());
            server.createContext("/api/auth/profile/update", new AuthUpdateProfileApiHandler());
            server.createContext("/api/auth/password/change", new AuthChangePasswordApiHandler());
            server.createContext("/api/auth/my-bookings", new AuthMyBookingsApiHandler());
            server.createContext("/api/auth/google/config", new AuthGoogleConfigApiHandler());
            server.createContext("/api/auth/google/login", new AuthGoogleLoginApiHandler());
            server.createContext("/api/auth/google/callback", new AuthGoogleCallbackApiHandler());
            server.createContext("/api/auth/google", new AuthGoogleApiHandler());
            server.createContext("/api/admin/add-station", new AddStationApiHandler());
            server.createContext("/api/admin/add-track", new AddTrackApiHandler());
            server.createContext("/api/admin/add-train", new AddTrainApiHandler());
            server.createContext("/api/admin/overview", new AdminOverviewApiHandler());
            server.createContext("/api/admin/bookings", new AdminBookingsApiHandler());
            server.createContext("/api/admin/users", new AdminUsersApiHandler());
            server.createContext("/api/admin/user-details", new AdminUserDetailsApiHandler());
            server.createContext("/api/admin/analytics", new AdminAnalyticsApiHandler());
            server.createContext("/api/live-tracking", new AdminLiveTrackingApiHandler());
            server.createContext("/api/simulate-delay", new SimulateDelayApiHandler());
            server.createContext("/api/simulate-seat-flux", new SimulateSeatFluxApiHandler());
            server.createContext("/api/randomize-inventory", new SimulateSeatFluxApiHandler());

            // Java 21 Virtual Threads per task: scales to 10,000+ simultaneous requests seamlessly
            server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            server.start();

            // Start automated 24/7 background passenger flow & inventory fluctuation simulation
            startAutoSimulation();
        } catch (IOException e) {
            System.err.println("Note: Web server could not bind to port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (autoSimulator != null) {
            autoSimulator.shutdownNow();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return port;
    }

    public AuthService getAuthService() {
        return authService;
    }

    // --- Handlers ---

    private class StaticDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String rawPath = exchange.getRequestURI().getPath();
            if (rawPath == null || rawPath.equals("/") || rawPath.trim().isEmpty()) {
                rawPath = "/index.html";
            }

            // Path traversal protection
            if (rawPath.contains("..")) {
                sendStaticBytes(exchange, 403, "text/plain", "403 Forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }

            String relPath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
            File[] candidateDirs = new File[] {
                new File("web"),
                new File("src/com/railway/web"),
                new File(".")
            };

            File targetFile = null;
            for (File dir : candidateDirs) {
                File f = new File(dir, relPath);
                if (f.exists() && f.isFile()) {
                    targetFile = f;
                    break;
                }
            }

            if (targetFile != null) {
                String mime = getMimeType(targetFile.getName());
                byte[] fileBytes = Files.readAllBytes(targetFile.toPath());
                sendStaticBytes(exchange, 200, mime, fileBytes);
            } else if (rawPath.equals("/index.html")) {
                // Fallback to embedded HTML
                String html = getDashboardHtml();
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                sendStaticBytes(exchange, 200, "text/html; charset=UTF-8", bytes);
            } else {
                sendStaticBytes(exchange, 404, "text/plain", ("404 Not Found: " + rawPath).getBytes(StandardCharsets.UTF_8));
            }
        }

        private void sendStaticBytes(HttpExchange exchange, int statusCode, String contentType, byte[] data) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            if (contentType.startsWith("image/") || contentType.startsWith("font/")) {
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            } else {
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private String getMimeType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".ico")) return "image/x-icon";
            if (lower.endsWith(".woff2")) return "font/woff2";
            if (lower.endsWith(".woff")) return "font/woff";
            if (lower.endsWith(".ttf")) return "font/ttf";
            return "application/octet-stream";
        }
    }

    private class StationsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Station> stations = networkService.getAllStations();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < stations.size(); i++) {
                Station s = stations.get(i);
                json.append(String.format("{\"id\":\"%s\",\"name\":\"%s\",\"state\":\"%s\",\"district\":\"%s\",\"zone\":\"%s\",\"x\":%.1f,\"y\":%.1f}",
                        s.getId(), escapeJson(s.getName()), escapeJson(s.getState()), escapeJson(s.getDistrict()),
                        escapeJson(s.getZone()), s.getMapX(), s.getMapY()));
                if (i < stations.size() - 1) json.append(",");
            }
            json.append("]");
            sendJsonResponse(exchange, json.toString());
        }
    }

    private class TrainsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String src = query.get("src");
            String dst = query.get("dst");

            List<Train> trains;
            if (src != null && dst != null && !src.trim().isEmpty() && !dst.trim().isEmpty()) {
                trains = trainService.searchTrains(src.trim(), dst.trim());
                if (trains.isEmpty()) {
                    Station sSrc = networkService.getStation(src.trim());
                    Station sDst = networkService.getStation(dst.trim());
                    if (sSrc != null && sDst != null) {
                        Train corridorTrain = trainService.findOrCreateCorridorTrain(sSrc, sDst);
                        if (corridorTrain != null) {
                            trains = Collections.singletonList(corridorTrain);
                        }
                    }
                }
            } else {
                trains = trainService.getAllTrainsSortedByAvailableSeats();
            }

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < trains.size(); i++) {
                Train t = trains.get(i);
                List<Station> stops = t.getRouteStops();
                StringBuilder stopsJson = new StringBuilder("[");
                for (int j = 0; j < stops.size(); j++) {
                    stopsJson.append("\"").append(stops.get(j).getId()).append("\"");
                    if (j < stops.size() - 1) stopsJson.append(",");
                }
                stopsJson.append("]");

                json.append(String.format(
                        "{\"id\":\"%s\",\"name\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"total\":%d,\"available\":%d,\"waitlist\":%d,\"rac\":%d,\"availableRac\":%d,\"emergency\":%d,\"departureTime\":\"%s\",\"arrivalTime\":\"%s\",\"duration\":\"%s\",\"farePerKm\":%.2f,\"stops\":%s,\"seatsByClass\":{\"1A\":%d,\"2A\":%d,\"3A\":%d,\"SL\":%d,\"GN\":%d},\"availableByClass\":{\"1A\":%d,\"2A\":%d,\"3A\":%d,\"SL\":%d,\"GN\":%d}}",
                        t.getId(), escapeJson(t.getName()), t.getSource().getId(), t.getDestination().getId(),
                        t.getTotalSeats(), t.getAvailableSeats(), t.getWaitingListCount(),
                        t.getRacSeats(), t.getAvailableRacSeats(), t.getEmergencySeats(),
                        escapeJson(t.getDepartureTime()), escapeJson(t.getArrivalTime()), escapeJson(t.getTravelDuration()),
                        t.getFarePerKm(),
                        stopsJson.toString(),
                        t.getSeats1A(), t.getSeats2A(), t.getSeats3A(), t.getSeatsSL(), t.getSeatsGN(),
                        t.getAvailableSeats1A(), t.getAvailableSeats2A(), t.getAvailableSeats3A(), t.getAvailableSeatsSL(), t.getAvailableSeatsGN()
                ));
                if (i < trains.size() - 1) json.append(",");
            }
            json.append("]");
            sendJsonResponse(exchange, json.toString());
        }
    }

    private class RouteApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String src = query.get("src");
            String dst = query.get("dst");

            if (src == null || dst == null) {
                sendJsonResponse(exchange, "{\"error\":\"Missing src or dst parameters\"}");
                return;
            }

            DijkstraResult<Station> result = networkService.findShortestRoute(src, dst);
            if (!result.isReachable()) {
                sendJsonResponse(exchange, "{\"reachable\":false}");
                return;
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"reachable\":true,\"distance\":").append(result.getTotalDistance()).append(",\"path\":[");
            List<Station> path = result.getPath();
            for (int i = 0; i < path.size(); i++) {
                Station s = path.get(i);
                json.append(String.format("{\"id\":\"%s\",\"name\":\"%s\"}", s.getId(), s.getName()));
                if (i < path.size() - 1) json.append(",");
            }
            json.append("]}");
            sendJsonResponse(exchange, json.toString());
        }
    }

    private static String formatSeatsJson(Reservation res) {
        if (res.isGeneralClass()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        List<Integer> seats = res.getSeatNumbers();
        for (int i = 0; i < seats.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(seats.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatPassengersJson(Reservation res) {
        StringBuilder sb = new StringBuilder("[");
        List<Passenger> plist = res.getPassengers();
        for (int i = 0; i < plist.size(); i++) {
            Passenger p = plist.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"name\":\"%s\",\"age\":%d,\"gender\":\"%s\"}",
                    escapeJson(p.getName()), p.getAge(), escapeJson(p.getGender())));
        }
        sb.append("]");
        return sb.toString();
    }

    private class PnrApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String pnr = query.get("pnr");
            if (pnr == null) {
                sendJsonResponse(exchange, "{\"error\":\"Missing pnr query parameter\"}");
                return;
            }

            Reservation res = reservationService.getReservation(pnr);
            if (res == null) {
                sendJsonResponse(exchange, "{\"found\":false}");
                return;
            }

            DijkstraResult<Station> dr = networkService.findShortestRoute(res.getSourceStation().getId(), res.getDestinationStation().getId());
            double dist = (dr != null && dr.isReachable()) ? dr.getTotalDistance() : 0.0;
            String txnId = "TXN-RAIL-" + Math.abs((res.getPnr() + res.getTrainId()).hashCode() % 900000 + 100000);

            String json = String.format(
                    "{\"found\":true,\"pnr\":\"%s\",\"passenger\":\"%s\",\"age\":%d,\"gender\":\"%s\",\"seatCount\":%d,\"seats\":%s,\"seatsDisplay\":\"%s\",\"passengers\":%s,\"train\":\"%s (%s)\",\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"fromName\":\"%s\",\"toName\":\"%s\",\"status\":\"%s\",\"seat\":%d,\"wl\":%d,\"rac\":%d,\"quota\":\"%s\",\"departureTime\":\"%s\",\"arrivalTime\":\"%s\",\"fare\":%.2f,\"refundAmount\":%.2f,\"travelClass\":\"%s\",\"bookingTime\":\"%s\",\"travelDate\":\"%s\",\"formattedTravelDate\":\"%s\",\"distance\":%.1f,\"txnId\":\"%s\"}",
                    res.getPnr(), escapeJson(res.getPassenger().getName()), res.getPassenger().getAge(), escapeJson(res.getPassenger().getGender()),
                    res.getSeatCount(), formatSeatsJson(res), escapeJson(res.getSeatNumbersDisplay()), formatPassengersJson(res),
                    escapeJson(res.getTrainName()), res.getTrainId(), res.getTrainId(), escapeJson(res.getTrainName()),
                    res.getSourceStation().getId(), res.getDestinationStation().getId(),
                    escapeJson(res.getSourceStation().getName()), escapeJson(res.getDestinationStation().getName()),
                    res.getStatus().name(), res.getSeatNumber(), res.getWaitingListNumber(), res.getRacNumber(),
                    escapeJson(res.getQuota()), escapeJson(res.getDepartureTime()), escapeJson(res.getArrivalTime()),
                    res.getFare(), res.getRefundAmount(),
                    escapeJson(res.getTravelClass()), res.getFormattedBookingTime(),
                    escapeJson(res.getTravelDate()), escapeJson(res.getFormattedTravelDate()),
                    dist, txnId
            );
            sendJsonResponse(exchange, json);
        }
    }

    private class BookApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);

            String trainId = params.get("trainId");
            String name = params.get("name");
            String ageStr = params.get("age");
            String gender = params.get("gender");
            String src = params.get("src");
            String dst = params.get("dst");
            String travelClass = params.get("travelClass");
            String quota = params.get("quota");

            String seatCountStr = params.get("seatCount");
            int seatCount = 1;
            if (seatCountStr != null && !seatCountStr.trim().isEmpty()) {
                try {
                    seatCount = Math.max(1, Math.min(6, Integer.parseInt(seatCountStr.trim())));
                } catch (NumberFormatException ignored) {}
            }

            // Extract user from token or parameter
            String token = params.get("token");
            if (token == null || token.isEmpty()) {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7).trim();
                }
            }
            String bookedByUsername = null;
            if (token != null && !token.isEmpty()) {
                User u = authService.validateToken(token);
                if (u != null) {
                    bookedByUsername = u.getUsername();
                }
            }
            if (bookedByUsername == null && params.containsKey("username") && !params.get("username").trim().isEmpty()) {
                bookedByUsername = params.get("username").trim();
            }

            try {
                Train t = trainService.getTrainDirect(trainId);
                if (t != null) {
                    if (src == null || src.trim().isEmpty()) src = t.getSource().getId();
                    if (dst == null || dst.trim().isEmpty()) dst = t.getDestination().getId();
                }
                int age = (ageStr != null && !ageStr.isEmpty()) ? Integer.parseInt(ageStr) : 25;
                if (gender == null || gender.trim().isEmpty()) gender = "M";

                // Build multi-passenger manifest
                List<Passenger> passengerList = new ArrayList<>();
                for (int i = 0; i < seatCount; i++) {
                    String pName = params.get("name_" + i);
                    String pAge = params.get("age_" + i);
                    String pGen = params.get("gender_" + i);
                    if (pName != null && !pName.trim().isEmpty()) {
                        int a = age;
                        try { if (pAge != null && !pAge.trim().isEmpty()) a = Integer.parseInt(pAge.trim()); } catch (Exception ignored) {}
                        String g = (pGen != null && !pGen.trim().isEmpty()) ? pGen.trim().toUpperCase() : gender;
                        passengerList.add(new Passenger("WEB-" + (System.currentTimeMillis() + i) % 100000, pName.trim(), a, g));
                    }
                }
                if (passengerList.isEmpty()) {
                    passengerList.add(new Passenger("WEB-" + System.currentTimeMillis() % 100000, name, age, gender));
                    for (int i = 2; i <= seatCount; i++) {
                        passengerList.add(new Passenger("WEB-" + (System.currentTimeMillis() + i) % 100000, name + " (Guest " + i + ")", age, gender));
                    }
                }

                String travelDate = params.get("travelDate");
                if (travelDate == null || travelDate.trim().isEmpty()) {
                    travelDate = params.get("date");
                }

                Reservation res = reservationService.bookTickets(trainId, passengerList, src, dst, travelClass, bookedByUsername, quota, travelDate);
                DijkstraResult<Station> dr = networkService.findShortestRoute(res.getSourceStation().getId(), res.getDestinationStation().getId());
                double dist = (dr != null && dr.isReachable()) ? dr.getTotalDistance() : 0.0;
                String txnId = "TXN-RAIL-" + Math.abs((res.getPnr() + res.getTrainId()).hashCode() % 900000 + 100000);

                String json = String.format(
                        "{\"success\":true,\"pnr\":\"%s\",\"status\":\"%s\",\"seat\":%d,\"seatCount\":%d,\"seats\":%s,\"seatsDisplay\":\"%s\",\"passengers\":%s,\"wl\":%d,\"rac\":%d,\"quota\":\"%s\",\"departureTime\":\"%s\",\"arrivalTime\":\"%s\",\"fare\":%.2f,\"passenger\":\"%s\",\"age\":%d,\"gender\":\"%s\",\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"fromName\":\"%s\",\"toName\":\"%s\",\"travelClass\":\"%s\",\"bookedBy\":\"%s\",\"bookingTime\":\"%s\",\"travelDate\":\"%s\",\"formattedTravelDate\":\"%s\",\"distance\":%.1f,\"txnId\":\"%s\"}",
                        res.getPnr(), res.getStatus().name(), res.getSeatNumber(), res.getSeatCount(), formatSeatsJson(res), escapeJson(res.getSeatNumbersDisplay()), formatPassengersJson(res),
                        res.getWaitingListNumber(), res.getRacNumber(),
                        escapeJson(res.getQuota()), escapeJson(res.getDepartureTime()), escapeJson(res.getArrivalTime()),
                        res.getFare(),
                        escapeJson(res.getPassenger().getName()), res.getPassenger().getAge(), escapeJson(res.getPassenger().getGender()),
                        res.getTrainId(), escapeJson(res.getTrainName()),
                        res.getSourceStation().getId(), res.getDestinationStation().getId(),
                        escapeJson(res.getSourceStation().getName()), escapeJson(res.getDestinationStation().getName()),
                        escapeJson(res.getTravelClass()),
                        res.getBookedByUsername() != null ? escapeJson(res.getBookedByUsername()) : "",
                        res.getFormattedBookingTime(),
                        escapeJson(res.getTravelDate()), escapeJson(res.getFormattedTravelDate()),
                        dist, txnId
                );
                sendJsonResponse(exchange, json);
            } catch (Exception e) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class CancelApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);
            String pnr = params.get("pnr");

            ReservationService.CancellationResult result = reservationService.cancelTicket(pnr);
            String promotedPnr = result.getPromotedReservation() != null ? result.getPromotedReservation().getPnr() : "";
            Reservation res = result.getCancelledReservation();
            String trainName = res != null ? res.getTrainName() : "";
            String trainId = res != null ? res.getTrainId() : "";
            String passengerName = res != null && res.getPassenger() != null ? res.getPassenger().getName() : "";
            double originalFare = res != null ? res.getFare() : (result.getRefundAmount() + result.getCancellationCharge());

            String json = String.format(
                    "{\"success\":%b,\"message\":\"%s\",\"promotedPnr\":\"%s\",\"refundAmount\":%.2f,\"cancellationCharge\":%.2f,\"refundTxnId\":\"%s\",\"originalFare\":%.2f,\"refundRatio\":\"80%%\",\"cancellationChargeRatio\":\"20%%\",\"pnr\":\"%s\",\"trainId\":\"%s\",\"trainName\":\"%s\",\"passengerName\":\"%s\"}",
                    result.isSuccess(), escapeJson(result.message()), promotedPnr,
                    result.getRefundAmount(), result.getCancellationCharge(), escapeJson(result.getRefundTxnId()),
                    originalFare, escapeJson(pnr != null ? pnr : ""), escapeJson(trainId), escapeJson(trainName), escapeJson(passengerName)
            );
            sendJsonResponse(exchange, json);
        }
    }

    private class NetworkApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Station> stations = networkService.getAllStations();
            List<RouteEdge> tracks = networkService.getAllTracks();

            StringBuilder json = new StringBuilder("{\"stations\":[");
            for (int i = 0; i < stations.size(); i++) {
                Station s = stations.get(i);
                json.append(String.format("{\"id\":\"%s\",\"name\":\"%s\",\"state\":\"%s\",\"district\":\"%s\",\"zone\":\"%s\",\"x\":%.1f,\"y\":%.1f}",
                        s.getId(), escapeJson(s.getName()), escapeJson(s.getState()), escapeJson(s.getDistrict()),
                        escapeJson(s.getZone()), s.getMapX(), s.getMapY()));
                if (i < stations.size() - 1) json.append(",");
            }
            json.append("],\"tracks\":[");
            for (int i = 0; i < tracks.size(); i++) {
                RouteEdge e = tracks.get(i);
                json.append(String.format("{\"source\":\"%s\",\"dest\":\"%s\",\"distance\":%.1f}",
                        e.getSource().getId(), e.getDestination().getId(), e.getDistanceKm()));
                if (i < tracks.size() - 1) json.append(",");
            }
            json.append("]}");
            sendJsonResponse(exchange, json.toString());
        }
    }

    private class StatsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int stationsCount = networkService.getAllStations().size();
            List<Train> trains = trainService.getAllTrains();
            int trainsCount = trains.size();
            int totalSeats = 0;
            int availableSeats = 0;
            int waitlistCount = 0;
            for (Train t : trains) {
                totalSeats += t.getTotalSeats();
                availableSeats += t.getAvailableSeats();
                waitlistCount += t.getWaitingListCount();
            }
            int confirmedBookedSeats = Math.max(0, totalSeats - availableSeats);
            int manualReservations = reservationService.getAllReservations().size();
            int totalConfirmed = confirmedBookedSeats + manualReservations;

            String json = String.format(
                    "{\"stations\":%d,\"trains\":%d,\"totalSeats\":%d,\"availableSeats\":%d,\"waitlist\":%d,\"reservations\":%d,\"confirmed\":%d,\"confirmedSeats\":%d,\"manualReservations\":%d}",
                    stationsCount, trainsCount, totalSeats, availableSeats, waitlistCount, totalConfirmed, totalConfirmed, confirmedBookedSeats, manualReservations
            );
            sendJsonResponse(exchange, json);
        }
    }

    public static class RouteStats {
        public String srcId;
        public String dstId;
        public String srcName;
        public String dstName;
        public int bookingCount = 0;
        public int passengerCount = 0;
        public double revenue = 0.0;
        public double distance = 0.0;

        public RouteStats(String srcId, String dstId, String srcName, String dstName, double distance) {
            this.srcId = srcId;
            this.dstId = dstId;
            this.srcName = srcName;
            this.dstName = dstName;
            this.distance = distance;
        }
    }

    public static class StationUsageStats {
        public String id;
        public String name;
        public String state;
        public int departures = 0;
        public int arrivals = 0;

        public StationUsageStats(String id, String name, String state) {
            this.id = id;
            this.name = name;
            this.state = state;
        }

        public int getTotalFootfall() {
            return departures + arrivals;
        }
    }

    public static class UserHistoryAggregator {
        public String key;
        public String fullName;
        public String email = "";
        public String phone = "";
        public String role = "PASSENGER";
        public String authProvider = "local";
        public int totalBookings = 0;
        public int confirmedCount = 0;
        public int cancelledCount = 0;
        public int racCount = 0;
        public int wlCount = 0;
        public double totalSpend = 0.0;
        public double refundsReceived = 0.0;
        public List<Reservation> bookings = new ArrayList<>();

        public UserHistoryAggregator(String key, String fullName) {
            this.key = key;
            this.fullName = fullName;
        }
    }

    public static class CancelledTicketRecord {
        public String pnr;
        public String passenger;
        public String trainId;
        public String trainName;
        public String from;
        public String to;
        public double fare;
        public double refundAmount;
        public double cancellationCharge;
        public String travelClass;
        public String bookingTime;
        public String travelDate;

        public CancelledTicketRecord(String pnr, String passenger, String trainId, String trainName,
                                     String from, String to, double fare, double refundAmount,
                                     double cancellationCharge, String travelClass, String bookingTime, String travelDate) {
            this.pnr = pnr;
            this.passenger = passenger;
            this.trainId = trainId;
            this.trainName = trainName;
            this.from = from;
            this.to = to;
            this.fare = fare;
            this.refundAmount = refundAmount;
            this.cancellationCharge = cancellationCharge;
            this.travelClass = travelClass;
            this.bookingTime = bookingTime;
            this.travelDate = travelDate;
        }
    }

    public static class TrainOccupancyStats {
        public String id;
        public String name;
        public String from;
        public String to;
        public String fromName;
        public String toName;
        public int totalSeats;
        public int availableSeats;
        public int bookedSeats;
        public double occupancyPercent;
        public int racSeats;
        public int availableRac;
        public int waitlist;
        public double revenue;

        public TrainOccupancyStats(String id, String name, String from, String to, String fromName, String toName,
                                   int totalSeats, int availableSeats, int bookedSeats, double occupancyPercent,
                                   int racSeats, int availableRac, int waitlist, double revenue) {
            this.id = id;
            this.name = name;
            this.from = from;
            this.to = to;
            this.fromName = fromName;
            this.toName = toName;
            this.totalSeats = totalSeats;
            this.availableSeats = availableSeats;
            this.bookedSeats = bookedSeats;
            this.occupancyPercent = occupancyPercent;
            this.racSeats = racSeats;
            this.availableRac = availableRac;
            this.waitlist = waitlist;
            this.revenue = revenue;
        }
    }

    private User requireGoogleAdmin(HttpExchange exchange) throws IOException {
        String token = extractToken(exchange);
        if (token == null || token.trim().isEmpty()) {
            sendJsonResponse(exchange, 401, "{\"success\":false,\"error\":\"Unauthorized: Administrator session token is required.\"}");
            return null;
        }
        User user = authService.validateToken(token);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"success\":false,\"error\":\"Unauthorized: Invalid or expired administrator session.\"}");
            return null;
        }
        if (!user.isGoogleAuth()) {
            sendJsonResponse(exchange, 403, "{\"success\":false,\"error\":\"Security Policy: Administrator section requires mandatory Google Authentication.\"}");
            return null;
        }
        if (!user.isAdmin()) {
            user.setRole(UserRole.ADMIN);
        }
        return user;
    }

    private class AddStationApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User admin = requireGoogleAdmin(exchange);
            if (admin == null) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);
            String id = params.get("id");
            String name = params.get("name");
            try {
                Station s = networkService.addStation(id, name);
                sendJsonResponse(exchange, String.format("{\"success\":true,\"id\":\"%s\",\"name\":\"%s\"}", s.getId(), s.getName()));
            } catch (Exception e) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AddTrackApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User admin = requireGoogleAdmin(exchange);
            if (admin == null) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);
            String src = params.get("src");
            String dst = params.get("dst");
            double distance = Double.parseDouble(params.getOrDefault("distance", "100.0"));
            try {
                RouteEdge e = networkService.addTrack(src, dst, distance);
                sendJsonResponse(exchange, "{\"success\":true,\"message\":\"Track connected successfully\"}");
            } catch (Exception e) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AddTrainApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User admin = requireGoogleAdmin(exchange);
            if (admin == null) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);
            String id = params.get("id");
            String name = params.get("name");
            String src = params.get("src");
            String dst = params.get("dst");
            String stopsStr = params.get("stops");
            int totalSeats = Integer.parseInt(params.getOrDefault("seats", "4"));
            double farePerKm = Double.parseDouble(params.getOrDefault("farePerKm", "1.35"));

            List<String> stops = new ArrayList<>();
            if (stopsStr != null && !stopsStr.trim().isEmpty()) {
                for (String s : stopsStr.split(",")) {
                    if (!s.trim().isEmpty()) stops.add(s.trim().toUpperCase());
                }
            }

            try {
                Train t = trainService.addTrain(id, name, src, dst, stops, totalSeats, farePerKm);
                sendJsonResponse(exchange, String.format("{\"success\":true,\"id\":\"%s\",\"name\":\"%s\"}", t.getId(), t.getName()));
            } catch (Exception e) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminOverviewApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User admin = requireGoogleAdmin(exchange);
            if (admin == null) return;

            List<Reservation> allRes = reservationService.getAllReservations();
            int totalBookings = allRes.size();
            int cnfCount = 0;
            int wlCount = 0;
            int racCount = 0;
            int canCount = 0;
            double totalRevenue = 0.0;

            for (Reservation r : allRes) {
                if (r.getStatus() == BookingStatus.CONFIRMED) {
                    cnfCount++;
                    totalRevenue += r.getFare();
                } else if (r.getStatus() == BookingStatus.RAC) {
                    racCount++;
                    totalRevenue += r.getFare();
                } else if (r.getStatus() == BookingStatus.WAITING_LIST) {
                    wlCount++;
                    totalRevenue += r.getFare();
                } else if (r.getStatus() == BookingStatus.CANCELLED) {
                    canCount++;
                    totalRevenue += Math.max(0, r.getFare() - r.getRefundAmount());
                }
            }

            int totalUsers = IrctcUserDatasetService.TOTAL_USERS;
            int passengerCount = (int) (totalUsers * 0.998);
            int adminCount = (int) (totalUsers * 0.002);
            int googleCount = (int) (totalUsers * 0.30);

            int stationsCount = networkService.getAllStations().size();
            List<Train> trains = trainService.getAllTrains();
            int trainsCount = trains.size();
            int totalSeats = 0;
            int availableSeats = 0;
            int totalWaitlist = 0;
            for (Train t : trains) {
                totalSeats += t.getTotalSeats();
                availableSeats += t.getAvailableSeats();
                totalWaitlist += t.getWaitingListCount();
            }

            double totalTrackKm = 0;
            for (RouteEdge e : networkService.getAllTracks()) {
                totalTrackKm += e.getDistanceKm();
            }

            int confirmedBookedSeats = Math.max(0, totalSeats - availableSeats);
            int dynamicCnf = confirmedBookedSeats + cnfCount;
            int dynamicTotalBookings = dynamicCnf + totalWaitlist + racCount + canCount;

            String json = String.format(
                    "{\"success\":true,\"totalBookings\":%d,\"cnfBookings\":%d,\"wlBookings\":%d,\"racBookings\":%d,\"cancelledBookings\":%d,\"totalRevenue\":%.2f,\"totalUsers\":%d,\"passengerCount\":%d,\"adminCount\":%d,\"googleCount\":%d,\"stations\":%d,\"trains\":%d,\"totalSeats\":%d,\"availableSeats\":%d,\"waitlist\":%d,\"totalTrackKm\":%.1f}",
                    dynamicTotalBookings, dynamicCnf, totalWaitlist, racCount, canCount, totalRevenue,
                    totalUsers, passengerCount, adminCount, googleCount,
                    stationsCount, trainsCount, totalSeats, availableSeats, totalWaitlist, totalTrackKm
            );
            sendJsonResponse(exchange, json);
        }
    }

    private class AdminBookingsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User admin = requireGoogleAdmin(exchange);
            if (admin == null) return;

            List<Reservation> allRes = reservationService.getAllReservations();
            StringBuilder sb = new StringBuilder("{\"success\":true,\"total\":").append(allRes.size()).append(",\"bookings\":[");
            for (int i = 0; i < allRes.size(); i++) {
                Reservation r = allRes.get(i);
                if (i > 0) sb.append(",");
                DijkstraResult<Station> dr = networkService.findShortestRoute(r.getSourceStation().getId(), r.getDestinationStation().getId());
                double dist = (dr != null && dr.isReachable()) ? dr.getTotalDistance() : 0.0;
                String txnId = "TXN-RAIL-" + Math.abs((r.getPnr() + r.getTrainId()).hashCode() % 900000 + 100000);
                sb.append(String.format(
                        "{\"pnr\":\"%s\",\"passenger\":\"%s\",\"age\":%d,\"gender\":\"%s\",\"seatCount\":%d,\"seats\":%s,\"seatsDisplay\":\"%s\",\"passengers\":%s,\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"fromName\":\"%s\",\"toName\":\"%s\",\"travelClass\":\"%s\",\"seat\":%d,\"wl\":%d,\"rac\":%d,\"quota\":\"%s\",\"departureTime\":\"%s\",\"arrivalTime\":\"%s\",\"fare\":%.2f,\"refundAmount\":%.2f,\"status\":\"%s\",\"bookingTime\":\"%s\",\"travelDate\":\"%s\",\"formattedTravelDate\":\"%s\",\"bookedBy\":\"%s\",\"distance\":%.1f,\"txnId\":\"%s\"}",
                        r.getPnr(), escapeJson(r.getPassenger().getName()), r.getPassenger().getAge(), escapeJson(r.getPassenger().getGender()),
                        r.getSeatCount(), formatSeatsJson(r), escapeJson(r.getSeatNumbersDisplay()), formatPassengersJson(r),
                        r.getTrainId(), escapeJson(r.getTrainName()),
                        r.getSourceStation().getId(), r.getDestinationStation().getId(),
                        escapeJson(r.getSourceStation().getName()), escapeJson(r.getDestinationStation().getName()),
                        escapeJson(r.getTravelClass()), r.getSeatNumber(), r.getWaitingListNumber(), r.getRacNumber(),
                        escapeJson(r.getQuota()), escapeJson(r.getDepartureTime()), escapeJson(r.getArrivalTime()),
                        r.getFare(), r.getRefundAmount(),
                        r.getStatus().name(), r.getFormattedBookingTime(),
                        escapeJson(r.getTravelDate()), escapeJson(r.getFormattedTravelDate()),
                        r.getBookedByUsername() != null ? escapeJson(r.getBookedByUsername()) : "",
                        dist, txnId
                ));
            }
            sb.append("]}");
            sendJsonResponse(exchange, sb.toString());
        }
    }

    private class AdminUsersApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User admin = requireGoogleAdmin(exchange);
            if (admin == null) return;

            Map<String, String> qParams = parseQueryParams(exchange.getRequestURI().getQuery());
            int page = 1;
            int pageSize = 50;
            try {
                if (qParams.containsKey("page")) page = Math.max(1, Integer.parseInt(qParams.get("page")));
                if (qParams.containsKey("pageSize")) pageSize = Math.max(1, Math.min(100, Integer.parseInt(qParams.get("pageSize"))));
            } catch (NumberFormatException ignored) {}

            String q = qParams.getOrDefault("search", "");
            String role = qParams.getOrDefault("role", "ALL");
            String state = qParams.getOrDefault("state", "ALL");

            IrctcUserDatasetService.UserSearchResult result = irctcUserDatasetService.searchUsers(q, role, state, page, pageSize);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{\"success\":true,\"total\":%d,\"filteredTotal\":%d,\"page\":%d,\"pageSize\":%d,\"totalPages\":%d,\"queryTimeMs\":%d,\"users\":[",
                result.total, result.filteredTotal, result.page, result.pageSize, result.totalPages, result.queryTimeMs
            ));

            for (int i = 0; i < result.users.size(); i++) {
                if (i > 0) sb.append(",");
                IrctcUserDatasetService.UserRecord u = result.users.get(i);
                sb.append(String.format(
                    "{\"id\":%d,\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"state\":\"%s\",\"zone\":\"%s\",\"homeStation\":\"%s\",\"homeStationCode\":\"%s\",\"loyaltyTier\":\"%s\",\"totalBookings\":%d,\"confirmedBookings\":%d,\"cancelledBookings\":%d,\"racBookings\":%d,\"wlBookings\":%d,\"totalSpend\":%.2f,\"refundsReceived\":%.2f,\"netSpend\":%.2f,\"registrationDate\":\"%s\",\"bookingCount\":%d}",
                    u.id, escapeJson(u.username), escapeJson(u.fullName),
                    escapeJson(u.email), escapeJson(u.phone),
                    u.role, escapeJson(u.authProvider),
                    escapeJson(u.state), escapeJson(u.zone),
                    escapeJson(u.homeStation), escapeJson(u.homeStationCode),
                    escapeJson(u.loyaltyTier),
                    u.totalBookings, u.confirmedBookings, u.cancelledBookings,
                    u.racBookings, u.wlBookings,
                    u.totalSpend, u.refundsReceived, u.netSpend,
                    escapeJson(u.registrationDate), u.totalBookings
                ));
            }
            sb.append("]}");
            sendJsonResponse(exchange, sb.toString());
        }
    }

    private class AdminUserDetailsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User admin = requireGoogleAdmin(exchange);
            if (admin == null) return;

            Map<String, String> qParams = parseQueryParams(exchange.getRequestURI().getQuery());
            String username = qParams.get("username");
            if (username == null || username.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"Missing username parameter\"}");
                return;
            }

            IrctcUserDatasetService.UserDetailResponse details = irctcUserDatasetService.getUserDetails(username);
            if (details == null) {
                sendJsonResponse(exchange, 404, "{\"success\":false,\"error\":\"Passenger not found in IRCTC database\"}");
                return;
            }

            IrctcUserDatasetService.UserRecord u = details.profile;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true,");
            sb.append(String.format(
                "\"profile\":{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"state\":\"%s\",\"zone\":\"%s\",\"homeStation\":\"%s\",\"homeStationCode\":\"%s\",\"loyaltyTier\":\"%s\",\"totalBookings\":%d,\"confirmedBookings\":%d,\"cancelledBookings\":%d,\"racBookings\":%d,\"wlBookings\":%d,\"totalSpend\":%.2f,\"refundsReceived\":%.2f,\"netSpend\":%.2f,\"registrationDate\":\"%s\"},",
                escapeJson(u.username), escapeJson(u.fullName), escapeJson(u.email), escapeJson(u.phone),
                u.role, escapeJson(u.authProvider), escapeJson(u.state), escapeJson(u.zone),
                escapeJson(u.homeStation), escapeJson(u.homeStationCode), escapeJson(u.loyaltyTier),
                u.totalBookings, u.confirmedBookings, u.cancelledBookings, u.racBookings, u.wlBookings,
                u.totalSpend, u.refundsReceived, u.netSpend, escapeJson(u.registrationDate)
            ));

            sb.append("\"tickets\":[");
            for (int i = 0; i < details.recentTickets.size(); i++) {
                if (i > 0) sb.append(",");
                IrctcUserDatasetService.TicketHistoryItem t = details.recentTickets.get(i);
                sb.append(String.format(
                    "{\"pnr\":\"%s\",\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"travelClass\":\"%s\",\"status\":\"%s\",\"fare\":%.2f,\"travelDate\":\"%s\",\"seatNumber\":\"%s\"}",
                    t.pnr, t.trainId, escapeJson(t.trainName), t.from, t.to,
                    escapeJson(t.travelClass), t.status, t.fare, escapeJson(t.travelDate), escapeJson(t.seatNumber)
                ));
            }
            sb.append("]}");
            sendJsonResponse(exchange, sb.toString());
        }
    }

    private class AdminAnalyticsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User admin = requireGoogleAdmin(exchange);
            if (admin == null) return;

            List<Reservation> allRes = reservationService.getAllReservations();
            int totalBookings = allRes.size();
            int cnfCount = 0;
            int wlCount = 0;
            int racCount = 0;
            int canCount = 0;
            double totalGrossRevenue = 0.0;
            double totalRefunds = 0.0;
            double totalCancellationCharges = 0.0;

            Map<String, Integer> classCountMap = new LinkedHashMap<>();
            classCountMap.put("1st Class", 0);
            classCountMap.put("2nd Class", 0);
            classCountMap.put("3 Tier AC", 0);
            classCountMap.put("Sleeper", 0);
            classCountMap.put("General", 0);

            Map<String, Double> classRevenueMap = new LinkedHashMap<>();
            classRevenueMap.put("1st Class", 0.0);
            classRevenueMap.put("2nd Class", 0.0);
            classRevenueMap.put("3 Tier AC", 0.0);
            classRevenueMap.put("Sleeper", 0.0);
            classRevenueMap.put("General", 0.0);

            Map<String, RouteStats> routeStatsMap = new LinkedHashMap<>();
            Map<String, StationUsageStats> stationStatsMap = new LinkedHashMap<>();
            Map<String, UserHistoryAggregator> userAggMap = new LinkedHashMap<>();
            Map<String, Double> trainRevenueMap = new LinkedHashMap<>();
            List<CancelledTicketRecord> cancelledList = new ArrayList<>();

            for (Reservation r : allRes) {
                double fare = r.getFare();
                totalGrossRevenue += fare;
                String tc = r.getTravelClass();
                if (tc != null) {
                    classCountMap.put(tc, classCountMap.getOrDefault(tc, 0) + 1);
                    classRevenueMap.put(tc, classRevenueMap.getOrDefault(tc, 0.0) + fare);
                }

                trainRevenueMap.put(r.getTrainId(), trainRevenueMap.getOrDefault(r.getTrainId(), 0.0) + fare);

                String srcId = r.getSourceStation().getId();
                String dstId = r.getDestinationStation().getId();
                String routeKey = srcId + " ➔ " + dstId;
                DijkstraResult<Station> dr = networkService.findShortestRoute(srcId, dstId);
                double dist = (dr != null && dr.isReachable()) ? dr.getTotalDistance() : 0.0;
                RouteStats rs = routeStatsMap.computeIfAbsent(routeKey, k -> new RouteStats(srcId, dstId, r.getSourceStation().getName(), r.getDestinationStation().getName(), dist));
                rs.bookingCount++;
                rs.passengerCount += r.getSeatCount();
                rs.revenue += fare;

                StationUsageStats susSrc = stationStatsMap.computeIfAbsent(srcId, k -> new StationUsageStats(srcId, r.getSourceStation().getName(), r.getSourceStation().getState()));
                susSrc.departures++;
                StationUsageStats susDst = stationStatsMap.computeIfAbsent(dstId, k -> new StationUsageStats(dstId, r.getDestinationStation().getName(), r.getDestinationStation().getState()));
                susDst.arrivals++;

                String uKey = (r.getBookedByUsername() != null && !r.getBookedByUsername().trim().isEmpty())
                        ? r.getBookedByUsername().trim().toLowerCase()
                        : r.getPassenger().getName().trim().toLowerCase();
                UserHistoryAggregator uha = userAggMap.computeIfAbsent(uKey, k -> new UserHistoryAggregator(uKey, r.getPassenger().getName()));
                uha.bookings.add(r);
                uha.totalBookings++;
                uha.totalSpend += fare;

                if (r.getStatus() == BookingStatus.CONFIRMED) {
                    cnfCount++;
                    uha.confirmedCount++;
                } else if (r.getStatus() == BookingStatus.RAC) {
                    racCount++;
                    uha.racCount++;
                } else if (r.getStatus() == BookingStatus.WAITING_LIST) {
                    wlCount++;
                    uha.wlCount++;
                } else if (r.getStatus() == BookingStatus.CANCELLED) {
                    canCount++;
                    uha.cancelledCount++;
                    double ref = r.getRefundAmount();
                    double fee = Math.max(0.0, fare - ref);
                    totalRefunds += ref;
                    totalCancellationCharges += fee;
                    uha.refundsReceived += ref;
                    cancelledList.add(new CancelledTicketRecord(
                            r.getPnr(), r.getPassenger().getName(), r.getTrainId(), r.getTrainName(),
                            srcId, dstId, fare, ref, fee, r.getTravelClass(),
                            r.getFormattedBookingTime(), r.getTravelDate()
                    ));
                }
            }

            for (User u : authService.getAllUsers()) {
                String uKey = u.getUsername().trim().toLowerCase();
                UserHistoryAggregator uha = userAggMap.computeIfAbsent(uKey, k -> new UserHistoryAggregator(uKey, u.getFullName()));
                uha.email = u.getEmail();
                uha.phone = u.getPhone();
                uha.role = u.getRole().name();
                uha.authProvider = u.getAuthProvider();
                if (uha.fullName == null || uha.fullName.isEmpty() || uha.fullName.equalsIgnoreCase(u.getUsername())) {
                    uha.fullName = u.getFullName();
                }
            }

            List<Train> trains = trainService.getAllTrains();
            int fleetTotalSeats = 0;
            int fleetAvailableSeats = 0;
            int fleetBookedSeats = 0;
            int fleetTotalRac = 0;
            int fleetAvailableRac = 0;
            int fleetTotalWaitlist = 0;

            List<TrainOccupancyStats> trainOccupancyList = new ArrayList<>();
            for (Train t : trains) {
                int tot = t.getTotalSeats();
                int avail = t.getAvailableSeats();
                int booked = Math.max(0, tot - avail);
                double occPct = tot > 0 ? ((booked * 100.0) / tot) : 0.0;
                fleetTotalSeats += tot;
                fleetAvailableSeats += avail;
                fleetBookedSeats += booked;
                fleetTotalRac += t.getRacSeats();
                fleetAvailableRac += t.getAvailableRacSeats();
                fleetTotalWaitlist += t.getWaitingListCount();

                // Aggregate corridor route statistics across active fleet
                String srcId = t.getSource().getId();
                String dstId = t.getDestination().getId();
                String routeKey = srcId + " ➔ " + dstId;
                DijkstraResult<Station> dr = networkService.findShortestRoute(srcId, dstId);
                double dist = (dr != null && dr.isReachable()) ? dr.getTotalDistance() : 750.0;
                double avgFare = Math.max(420.0, dist * 1.15);
                double tRev = booked * avgFare;
                totalGrossRevenue += tRev;
                trainRevenueMap.put(t.getId(), trainRevenueMap.getOrDefault(t.getId(), 0.0) + tRev);

                RouteStats rs = routeStatsMap.computeIfAbsent(routeKey, k -> new RouteStats(srcId, dstId, t.getSource().getName(), t.getDestination().getName(), dist));
                rs.bookingCount += booked;
                rs.passengerCount += booked;
                rs.revenue += tRev;

                // Aggregate station traffic & footfall
                StationUsageStats susSrc = stationStatsMap.computeIfAbsent(srcId, k -> new StationUsageStats(srcId, t.getSource().getName(), t.getSource().getState()));
                susSrc.departures += (booked / 2) + 1;
                StationUsageStats susDst = stationStatsMap.computeIfAbsent(dstId, k -> new StationUsageStats(dstId, t.getDestination().getName(), t.getDestination().getState()));
                susDst.arrivals += (booked / 2) + 1;

                // Aggregate fleet travel class breakdown
                int c1 = (int)(booked * 0.08); // 8% 1st Class
                int c2 = (int)(booked * 0.18); // 18% 2nd Class
                int c3 = (int)(booked * 0.32); // 32% 3 Tier AC
                int cSl = (int)(booked * 0.27); // 27% Sleeper
                int cGn = Math.max(0, booked - (c1 + c2 + c3 + cSl)); // 15% General

                classCountMap.put("1st Class", classCountMap.getOrDefault("1st Class", 0) + c1);
                classCountMap.put("2nd Class", classCountMap.getOrDefault("2nd Class", 0) + c2);
                classCountMap.put("3 Tier AC", classCountMap.getOrDefault("3 Tier AC", 0) + c3);
                classCountMap.put("Sleeper", classCountMap.getOrDefault("Sleeper", 0) + cSl);
                classCountMap.put("General", classCountMap.getOrDefault("General", 0) + cGn);

                classRevenueMap.put("1st Class", classRevenueMap.getOrDefault("1st Class", 0.0) + (c1 * avgFare * 2.2));
                classRevenueMap.put("2nd Class", classRevenueMap.getOrDefault("2nd Class", 0.0) + (c2 * avgFare * 1.45));
                classRevenueMap.put("3 Tier AC", classRevenueMap.getOrDefault("3 Tier AC", 0.0) + (c3 * avgFare * 1.0));
                classRevenueMap.put("Sleeper", classRevenueMap.getOrDefault("Sleeper", 0.0) + (cSl * avgFare * 0.45));
                classRevenueMap.put("General", classRevenueMap.getOrDefault("General", 0.0) + (cGn * avgFare * 0.25));

                trainOccupancyList.add(new TrainOccupancyStats(
                        t.getId(), t.getName(), srcId, dstId,
                        t.getSource().getName(), t.getDestination().getName(),
                        tot, avail, booked, occPct,
                        t.getRacSeats(), t.getAvailableRacSeats(), t.getWaitingListCount(),
                        trainRevenueMap.getOrDefault(t.getId(), 0.0)
                ));
            }

            // Seed realistic cancellation ledger if empty
            if (cancelledList.isEmpty()) {
                canCount = 48;
                totalRefunds = 48 * 840.0;
                totalCancellationCharges = 48 * 180.0;
                String[] sampleClasses = {"3 Tier AC", "2nd Class", "Sleeper", "1st Class", "General"};
                String[] sampleNames = {"Aarav Sharma", "Rohan Mehta", "Pooja Verma", "Vikram Malhotra", "Ananya Deshmukh", "Siddharth Rao", "Kavita Nair", "Aditya Joshi"};
                for (int ci = 0; ci < 12; ci++) {
                    Train ct = trains.get(ci % trains.size());
                    String cPnr = "PNR-CAN-89" + (ci * 73 + 104);
                    double cFare = 920.0 + (ci * 140.0);
                    double cRef = cFare * 0.85;
                    double cFee = cFare - cRef;
                    cancelledList.add(new CancelledTicketRecord(
                            cPnr, sampleNames[ci % sampleNames.length], ct.getId(), ct.getName(),
                            ct.getSource().getId(), ct.getDestination().getId(), cFare, cRef, cFee,
                            sampleClasses[ci % sampleClasses.length], "Today, " + (10 + (ci % 12)) + ":15 AM", "2026-09-10"
                    ));
                }
            }

            double netRevenue = totalGrossRevenue - totalRefunds;
            double cancellationRate = (fleetBookedSeats + canCount) > 0 ? ((canCount * 100.0) / (fleetBookedSeats + canCount)) : 0.0;
            double avgRevenuePerBooking = fleetBookedSeats > 0 ? (totalGrossRevenue / fleetBookedSeats) : 1050.0;

            double fleetOccupancyPercent = fleetTotalSeats > 0 ? ((fleetBookedSeats * 100.0) / fleetTotalSeats) : 0.0;

            List<RouteStats> popularRoutes = new ArrayList<>(routeStatsMap.values());
            popularRoutes.sort((a, b) -> Integer.compare(b.bookingCount, a.bookingCount));

            List<StationUsageStats> mostUsedStations = new ArrayList<>(stationStatsMap.values());
            mostUsedStations.sort((a, b) -> Integer.compare(b.getTotalFootfall(), a.getTotalFootfall()));

            List<UserHistoryAggregator> userHistories = new ArrayList<>(userAggMap.values());
            userHistories.sort((a, b) -> {
                int cmp = Integer.compare(b.totalBookings, a.totalBookings);
                return (cmp != 0) ? cmp : Double.compare(b.totalSpend, a.totalSpend);
            });

            // Build JSON chunks for reusable injection
            StringBuilder classBreakdownSb = new StringBuilder("[");
            int cIdx = 0;
            for (String cName : classCountMap.keySet()) {
                if (cIdx++ > 0) classBreakdownSb.append(",");
                int cCount = classCountMap.get(cName);
                double cRev = classRevenueMap.get(cName);
                classBreakdownSb.append(String.format("{\"class\":\"%s\",\"bookingCount\":%d,\"revenue\":%.2f}", escapeJson(cName), cCount, cRev));
            }
            classBreakdownSb.append("]");

            StringBuilder occupancySb = new StringBuilder("[");
            for (int i = 0; i < trainOccupancyList.size(); i++) {
                if (i > 0) occupancySb.append(",");
                TrainOccupancyStats to = trainOccupancyList.get(i);
                occupancySb.append(String.format(
                        "{\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"fromName\":\"%s\",\"toName\":\"%s\",\"totalSeats\":%d,\"availableSeats\":%d,\"bookedSeats\":%d,\"occupancyPercent\":%.2f,\"occupancyRate\":%.2f,\"racSeats\":%d,\"availableRac\":%d,\"waitlist\":%d,\"revenue\":%.2f}",
                        to.id, escapeJson(to.name), to.from, to.to, escapeJson(to.fromName), escapeJson(to.toName),
                        to.totalSeats, to.availableSeats, to.bookedSeats, to.occupancyPercent, to.occupancyPercent,
                        to.racSeats, to.availableRac, to.waitlist, to.revenue
                ));
            }
            occupancySb.append("]");

            StringBuilder routesSb = new StringBuilder("[");
            for (int i = 0; i < popularRoutes.size(); i++) {
                if (i > 0) routesSb.append(",");
                RouteStats rs = popularRoutes.get(i);
                routesSb.append(String.format(
                        "{\"route\":\"%s ➔ %s\",\"from\":\"%s\",\"to\":\"%s\",\"src\":\"%s\",\"dst\":\"%s\",\"fromName\":\"%s\",\"toName\":\"%s\",\"srcName\":\"%s\",\"dstName\":\"%s\",\"bookingCount\":%d,\"passengerCount\":%d,\"totalRevenue\":%.2f,\"revenue\":%.2f,\"distance\":%.1f}",
                        rs.srcId, rs.dstId, rs.srcId, rs.dstId, rs.srcId, rs.dstId,
                        escapeJson(rs.srcName), escapeJson(rs.dstName), escapeJson(rs.srcName), escapeJson(rs.dstName),
                        rs.bookingCount, rs.passengerCount, rs.revenue, rs.revenue, rs.distance
                ));
            }
            routesSb.append("]");

            StringBuilder stationsSb = new StringBuilder("[");
            int totalFootfallSum = 0;
            for (StationUsageStats sus : mostUsedStations) {
                totalFootfallSum += sus.getTotalFootfall();
            }
            if (totalFootfallSum <= 0) totalFootfallSum = 1;
            for (int i = 0; i < mostUsedStations.size(); i++) {
                if (i > 0) stationsSb.append(",");
                StationUsageStats sus = mostUsedStations.get(i);
                double sharePct = (sus.getTotalFootfall() * 100.0) / totalFootfallSum;
                stationsSb.append(String.format(
                        "{\"id\":\"%s\",\"code\":\"%s\",\"name\":\"%s\",\"state\":\"%s\",\"departures\":%d,\"arrivals\":%d,\"totalTraffic\":%d,\"totalFootfall\":%d,\"trafficShare\":%.2f,\"sharePercent\":%.2f}",
                        sus.id, sus.id, escapeJson(sus.name), escapeJson(sus.state),
                        sus.departures, sus.arrivals, sus.getTotalFootfall(), sus.getTotalFootfall(), sharePct, sharePct
                ));
            }
            stationsSb.append("]");

            StringBuilder cancelledSb = new StringBuilder("[");
            for (int i = 0; i < cancelledList.size(); i++) {
                if (i > 0) cancelledSb.append(",");
                CancelledTicketRecord ctr = cancelledList.get(i);
                cancelledSb.append(String.format(
                        "{\"pnr\":\"%s\",\"passenger\":\"%s\",\"passengerName\":\"%s\",\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"fare\":%.2f,\"refundAmount\":%.2f,\"retainedFee\":%.2f,\"cancellationCharge\":%.2f,\"travelClass\":\"%s\",\"bookingTime\":\"%s\",\"date\":\"%s\",\"travelDate\":\"%s\"}",
                        ctr.pnr, escapeJson(ctr.passenger), escapeJson(ctr.passenger), ctr.trainId, escapeJson(ctr.trainName), ctr.from, ctr.to,
                        ctr.fare, ctr.refundAmount, ctr.cancellationCharge, ctr.cancellationCharge, escapeJson(ctr.travelClass),
                        escapeJson(ctr.bookingTime), escapeJson(ctr.bookingTime), escapeJson(ctr.travelDate)
                ));
            }
            cancelledSb.append("]");

            StringBuilder usersSb = new StringBuilder("[");
            for (int i = 0; i < userHistories.size(); i++) {
                if (i > 0) usersSb.append(",");
                UserHistoryAggregator uha = userHistories.get(i);
                double netSpend = Math.max(0.0, uha.totalSpend - uha.refundsReceived);
                usersSb.append(String.format(
                        "{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"totalBookings\":%d,\"confirmedBookings\":%d,\"confirmedCount\":%d,\"cancelledBookings\":%d,\"cancelledCount\":%d,\"racCount\":%d,\"wlCount\":%d,\"totalSpend\":%.2f,\"totalSpent\":%.2f,\"refundsReceived\":%.2f,\"netSpend\":%.2f,\"netSpent\":%.2f,\"history\":[",
                        escapeJson(uha.key), escapeJson(uha.fullName), escapeJson(uha.email), escapeJson(uha.phone),
                        escapeJson(uha.role), escapeJson(uha.authProvider),
                        uha.totalBookings, uha.confirmedCount, uha.confirmedCount, uha.cancelledCount, uha.cancelledCount, uha.racCount, uha.wlCount,
                        uha.totalSpend, uha.totalSpend, uha.refundsReceived, netSpend, netSpend
                ));
                for (int j = 0; j < uha.bookings.size(); j++) {
                    if (j > 0) usersSb.append(",");
                    Reservation r = uha.bookings.get(j);
                    DijkstraResult<Station> dr = networkService.findShortestRoute(r.getSourceStation().getId(), r.getDestinationStation().getId());
                    double dist = (dr != null && dr.isReachable()) ? dr.getTotalDistance() : 0.0;
                    String txnId = "TXN-RAIL-" + Math.abs((r.getPnr() + r.getTrainId()).hashCode() % 900000 + 100000);
                    usersSb.append(String.format(
                            "{\"pnr\":\"%s\",\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"fromName\":\"%s\",\"toName\":\"%s\",\"travelClass\":\"%s\",\"seat\":%d,\"wl\":%d,\"rac\":%d,\"fare\":%.2f,\"refundAmount\":%.2f,\"status\":\"%s\",\"bookingTime\":\"%s\",\"date\":\"%s\",\"travelDate\":\"%s\",\"distance\":%.1f,\"txnId\":\"%s\"}",
                            r.getPnr(), r.getTrainId(), escapeJson(r.getTrainName()),
                            r.getSourceStation().getId(), r.getDestinationStation().getId(),
                            escapeJson(r.getSourceStation().getName()), escapeJson(r.getDestinationStation().getName()),
                            escapeJson(r.getTravelClass()), r.getSeatNumber(), r.getWaitingListNumber(), r.getRacNumber(),
                            r.getFare(), r.getRefundAmount(), r.getStatus().name(),
                            r.getFormattedBookingTime(), r.getFormattedBookingTime(), escapeJson(r.getTravelDate()), dist, txnId
                    ));
                }
                usersSb.append("]}");
            }
            usersSb.append("]");

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true,");

            int dynamicCnf = fleetBookedSeats + cnfCount;
            int dynamicTotalBookings = dynamicCnf + canCount + fleetTotalWaitlist + racCount;

            // Summary
            sb.append(String.format(
                    "\"summary\":{\"totalBookings\":%d,\"confirmedBookings\":%d,\"cnfBookings\":%d,\"racBookings\":%d,\"wlBookings\":%d,\"cancelledTickets\":%d,\"cancelledBookings\":%d,\"cancellationRate\":%.2f,\"cancellationRatePercent\":%.2f,\"grossRevenue\":%.2f,\"totalGrossRevenue\":%.2f,\"totalRefunds\":%.2f,\"netRevenue\":%.2f,\"totalCancellationFees\":%.2f,\"avgRevenuePerBooking\":%.2f,\"fleetTotalCapacity\":%d,\"fleetTotalSeats\":%d,\"fleetAvailableConfirmed\":%d,\"fleetAvailableSeats\":%d,\"fleetBookedSeats\":%d,\"fleetOccupancyRate\":%.2f,\"fleetOccupancyPercent\":%.2f,\"fleetTotalRac\":%d,\"fleetAvailableRac\":%d,\"fleetWaitlist\":%d,\"totalUsers\":%d,\"totalStations\":%d,\"totalTrains\":%d},",
                    dynamicTotalBookings, dynamicCnf, dynamicCnf, racCount, fleetTotalWaitlist, canCount, canCount, cancellationRate, cancellationRate,
                    totalGrossRevenue, totalGrossRevenue, totalRefunds, netRevenue, totalCancellationCharges, avgRevenuePerBooking,
                    fleetTotalSeats, fleetTotalSeats, fleetAvailableSeats, fleetAvailableSeats, fleetBookedSeats, fleetOccupancyPercent, fleetOccupancyPercent,
                    fleetTotalRac, fleetAvailableRac, fleetTotalWaitlist, IrctcUserDatasetService.TOTAL_USERS, mostUsedStations.size(), trains.size()
            ));

            // Flat arrays
            sb.append("\"classBreakdown\":").append(classBreakdownSb).append(",");
            sb.append("\"occupancyByTrain\":").append(occupancySb).append(",");
            sb.append("\"popularRoutes\":").append(routesSb).append(",");
            sb.append("\"mostUsedStations\":").append(stationsSb).append(",");
            sb.append("\"cancelledTickets\":").append(cancelledSb).append(",");
            sb.append("\"passengerHistory\":").append(usersSb).append(",");

            // 8 dimension structured objects
            sb.append(String.format("\"totalBookingsAnalytics\":{\"total\":%d,\"confirmed\":%d,\"rac\":%d,\"waitingList\":%d,\"cancelled\":%d,\"classBreakdown\":%s},", dynamicTotalBookings, dynamicCnf, racCount, fleetTotalWaitlist, canCount, classBreakdownSb));
            sb.append(String.format("\"cancelledTicketsAnalytics\":{\"totalCancelled\":%d,\"cancellationRate\":%.2f,\"totalRefundsIssued\":%.2f,\"retainedCancellationFees\":%.2f,\"cancelledTickets\":%s},", canCount, cancellationRate, totalRefunds, totalCancellationCharges, cancelledSb));
            sb.append(String.format("\"availableSeatsAnalytics\":{\"fleetCapacity\":%d,\"fleetBooked\":%d,\"fleetAvailable\":%d,\"trains\":%s},", fleetTotalSeats, fleetBookedSeats, fleetAvailableSeats, occupancySb));
            sb.append(String.format("\"trainOccupancyAnalytics\":{\"fleetAverageOccupancy\":%.2f,\"occupancyLeaderboard\":%s},", fleetOccupancyPercent, occupancySb));
            sb.append(String.format("\"popularRoutesAnalytics\":{\"topRoutes\":%s},", routesSb));
            sb.append(String.format("\"revenueAnalytics\":{\"grossRevenue\":%.2f,\"totalRefunds\":%.2f,\"netRevenue\":%.2f,\"avgRevenuePerBooking\":%.2f,\"revenueByClass\":%s},", totalGrossRevenue, totalRefunds, netRevenue, avgRevenuePerBooking, classBreakdownSb));
            sb.append(String.format("\"mostUsedStationsAnalytics\":{\"trafficShare\":%.2f,\"stations\":%s},", 100.0, stationsSb));
            sb.append(String.format("\"passengerBookingHistoryAnalytics\":{\"totalSpent\":%.2f,\"users\":%s}", totalGrossRevenue, usersSb));

            sb.append("}");
            sendJsonResponse(exchange, sb.toString());
        }
    }

    private class AdminLiveTrackingApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String filterState = query.get("state");
            if (filterState != null && (filterState.trim().equalsIgnoreCase("all") || filterState.trim().isEmpty())) {
                filterState = null;
            } else if (filterState != null) {
                filterState = filterState.trim().toLowerCase();
            }

            List<Train> allTrains = trainService.getAllTrains();
            long now = System.currentTimeMillis();

            // Auto-rotate stochastic train delays (guarantees multiple delayed trains dynamically across the network)
            activeDynamicDelays.values().removeIf(d -> now > d.expiresAt);
            if (activeDynamicDelays.size() < 3 || (now - lastDynamicDelaySelection > 25000)) {
                triggerMultipleTrainDelays(3 + delayRandom.nextInt(3)); // 3 to 5 delayed trains
                lastDynamicDelaySelection = now;
            }

            StringBuilder sb = new StringBuilder("{\"success\":true,\"timestamp\":").append(now).append(",\"trains\":[");

            boolean first = true;
            for (Train t : allTrains) {
                List<Station> stops = t.getRouteStops();
                if (stops == null || stops.isEmpty()) {
                    stops = Arrays.asList(t.getSource(), t.getDestination());
                }

                Set<String> statesTraversed = new LinkedHashSet<>();
                for (Station s : stops) {
                    if (s.getState() != null && !s.getState().isEmpty()) {
                        statesTraversed.add(s.getState());
                    }
                }

                boolean matchesState = true;
                if (filterState != null) {
                    matchesState = false;
                    for (String st : statesTraversed) {
                        if (st.toLowerCase().contains(filterState)) {
                            matchesState = true;
                            break;
                        }
                    }
                }

                int trainHash = Math.abs(t.getId().hashCode());
                int cycleSeconds = 90 + (trainHash % 60);
                double rawTime = (now / 1000.0) + (trainHash % 1000);
                double totalProgress = (rawTime % cycleSeconds) / (double) cycleSeconds;

                int numSegments = Math.max(1, stops.size() - 1);
                double segmentFraction = 1.0 / numSegments;
                int currentSegIdx = Math.min(numSegments - 1, (int) (totalProgress / segmentFraction));
                double segProgress = (totalProgress - (currentSegIdx * segmentFraction)) / segmentFraction;

                Station curFrom = stops.get(currentSegIdx);
                Station curTo = stops.get(currentSegIdx + 1);

                double curX = curFrom.getMapX() + (curTo.getMapX() - curFrom.getMapX()) * segProgress;
                double curY = curFrom.getMapY() + (curTo.getMapY() - curFrom.getMapY()) * segProgress;

                int baseSpeed = t.getName().contains("Rajdhani") ? 130 : (t.getName().contains("Vande Bharat") ? 140 : (t.getName().contains("Shatabdi") ? 125 : 105));

                TrainDelayInfo delayInfo = activeDynamicDelays.get(t.getId());
                int delayMin = 0;
                String delayReason = "On Time - Track Clear";
                boolean isDelayed = false;
                if (delayInfo != null) {
                    delayMin = delayInfo.delayMinutes;
                    delayReason = delayInfo.reason;
                    isDelayed = true;
                }

                int liveSpeed = isDelayed ? Math.max(45, (int) (baseSpeed * 0.65)) : (baseSpeed + (int) ((Math.sin(rawTime / 5.0) * 10)));
                String delayStatus = !isDelayed ? "On Time" : ("Delayed " + delayMin + "m (" + delayReason + ")");
                int etaMin = Math.max(2, (int) ((1.0 - segProgress) * 45)) + delayMin;

                String activeState = (segProgress < 0.5) ? curFrom.getState() : curTo.getState();

                if (!first) sb.append(",");
                first = false;

                StringBuilder statesJson = new StringBuilder("[");
                int sIdx = 0;
                for (String st : statesTraversed) {
                    if (sIdx > 0) statesJson.append(",");
                    statesJson.append("\"").append(escapeJson(st)).append("\"");
                    sIdx++;
                }
                statesJson.append("]");

                sb.append(String.format(
                        "{\"id\":\"%s\",\"name\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"fromName\":\"%s\",\"toName\":\"%s\",\"currentFrom\":\"%s\",\"currentFromName\":\"%s\",\"currentTo\":\"%s\",\"currentToName\":\"%s\",\"activeState\":\"%s\",\"states\":%s,\"x\":%.2f,\"y\":%.2f,\"progress\":%.3f,\"segProgress\":%.3f,\"speed\":%d,\"delayMinutes\":%d,\"delayReason\":\"%s\",\"isDelayed\":%b,\"delayStatus\":\"%s\",\"nextStop\":\"%s\",\"etaMinutes\":%d,\"totalSeats\":%d,\"availableSeats\":%d,\"bookedSeats\":%d,\"occupancyPercent\":%.1f,\"waitlist\":%d,\"matchesFilter\":%b}",
                        t.getId(), escapeJson(t.getName()),
                        t.getSource().getId(), t.getDestination().getId(),
                        escapeJson(t.getSource().getName()), escapeJson(t.getDestination().getName()),
                        curFrom.getId(), escapeJson(curFrom.getName()),
                        curTo.getId(), escapeJson(curTo.getName()),
                        escapeJson(activeState), statesJson.toString(),
                        curX, curY, totalProgress, segProgress,
                        liveSpeed, delayMin, escapeJson(delayReason), isDelayed, escapeJson(delayStatus),
                        escapeJson(curTo.getName()), etaMin,
                        t.getTotalSeats(), t.getAvailableSeats(),
                        (t.getTotalSeats() - t.getAvailableSeats()),
                        ((t.getTotalSeats() - t.getAvailableSeats()) * 100.0 / Math.max(1, t.getTotalSeats())),
                        t.getWaitingListCount(),
                        matchesState
                ));
            }
            sb.append("]}");
            sendJsonResponse(exchange, sb.toString());
        }
    }

    private class SimulateDelayApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int count = 3 + delayRandom.nextInt(3); // 3 to 5 trains
            Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
            if (q.containsKey("count")) {
                try {
                    count = Math.max(2, Integer.parseInt(q.get("count")));
                } catch (Exception ignored) {}
            }
            List<TrainDelayInfo> list = triggerMultipleTrainDelays(count);
            if (list.isEmpty()) {
                sendJsonResponse(exchange, "{\"success\":false,\"message\":\"No trains available\"}");
                return;
            }

            StringBuilder arrJson = new StringBuilder("[");
            StringBuilder summarySb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                TrainDelayInfo info = list.get(i);
                Train t = trainService.getTrain(info.trainId);
                String name = (t != null) ? t.getName() : info.trainId;
                if (i > 0) {
                    arrJson.append(",");
                    summarySb.append(", ");
                }
                arrJson.append(String.format(
                        "{\"trainId\":\"%s\",\"trainName\":\"%s\",\"delayMinutes\":%d,\"reason\":\"%s\"}",
                        info.trainId, escapeJson(name), info.delayMinutes, escapeJson(info.reason)
                ));
                summarySb.append("#").append(info.trainId).append(" (+").append(info.delayMinutes).append("m)");
            }
            arrJson.append("]");

            TrainDelayInfo first = list.get(0);
            Train t1 = trainService.getTrain(first.trainId);
            String name1 = (t1 != null) ? t1.getName() : first.trainId;

            String msg = String.format("Stochastic algorithm delayed %d express trains: %s", list.size(), summarySb.toString());
            String json = String.format(
                    "{\"success\":true,\"count\":%d,\"trains\":%s,\"trainId\":\"%s\",\"trainName\":\"%s\",\"delayMinutes\":%d,\"reason\":\"%s\",\"message\":\"%s\"}",
                    list.size(), arrJson.toString(),
                    first.trainId, escapeJson(name1), first.delayMinutes, escapeJson(first.reason),
                    escapeJson(msg)
            );
            sendJsonResponse(exchange, json);
        }
    }

    private class SimulateSeatFluxApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
            String mode = q.getOrDefault("mode", "randomize");
            if ("fluctuate".equalsIgnoreCase(mode)) {
                trainService.fluctuateAllTrainInventories();
            } else {
                trainService.randomizeAllTrainInventories();
            }

            List<Train> trains = trainService.getAllTrains();
            StringBuilder arr = new StringBuilder("[");
            int wlCount = 0;
            int totalAvail = 0;
            for (int i = 0; i < trains.size(); i++) {
                Train t = trains.get(i);
                totalAvail += t.getAvailableSeats();
                wlCount += t.getWaitingListCount();
                if (i > 0) arr.append(",");
                arr.append(String.format(
                        "{\"id\":\"%s\",\"name\":\"%s\",\"available\":%d,\"total\":%d,\"waitlist\":%d,\"rac\":%d,\"availableRac\":%d}",
                        t.getId(), escapeJson(t.getName()), t.getAvailableSeats(), t.getTotalSeats(),
                        t.getWaitingListCount(), t.getRacSeats(), t.getAvailableRacSeats()
                ));
            }
            arr.append("]");

            String msg = String.format("Seat inventory dynamically randomized across all %d trains. Total Confirmed Available: %d seats | Active Waiting List: %d passengers.",
                    trains.size(), totalAvail, wlCount);
            String json = String.format("{\"success\":true,\"mode\":\"%s\",\"totalAvailable\":%d,\"totalWaitlist\":%d,\"message\":\"%s\",\"trains\":%s}",
                    escapeJson(mode), totalAvail, wlCount, escapeJson(msg), arr.toString());
            sendJsonResponse(exchange, json);
        }
    }

    private class AuthRegisterApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);

            String username = params.get("username");
            String password = params.get("password");
            String fullName = params.get("fullName");
            String email = params.get("email");
            String phone = params.get("phone");

            try {
                User user = authService.register(username, password, fullName, email, phone, UserRole.PASSENGER);
                AuthSession session = authService.login(username, password);
                String json = String.format(
                        "{\"success\":true,\"message\":\"Registration successful\",\"token\":\"%s\",\"user\":{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"avatarUrl\":\"%s\"}}",
                        session.getToken(), escapeJson(user.getUsername()), escapeJson(user.getFullName()),
                        escapeJson(user.getEmail()), escapeJson(user.getPhone()), user.getRole().name(),
                        escapeJson(user.getAuthProvider()), escapeJson(user.getAvatarUrl())
                );
                sendJsonResponse(exchange, json);
            } catch (Exception e) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AuthLoginApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);

            String username = params.get("username");
            String password = params.get("password");

            if (params.containsKey("forAdmin") && "true".equalsIgnoreCase(params.get("forAdmin"))) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"Security Policy: Administrator login requires mandatory Google Authentication. Password-only login is disabled for the admin section.\"}");
                return;
            }

            try {
                AuthSession session = authService.login(username, password);
                User user = session.getUser();
                String json = String.format(
                        "{\"success\":true,\"token\":\"%s\",\"user\":{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"avatarUrl\":\"%s\"}}",
                        session.getToken(), escapeJson(user.getUsername()), escapeJson(user.getFullName()),
                        escapeJson(user.getEmail()), escapeJson(user.getPhone()), user.getRole().name(),
                        escapeJson(user.getAuthProvider()), escapeJson(user.getAvatarUrl())
                );
                sendJsonResponse(exchange, json);
            } catch (Exception e) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AuthLogoutApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = extractToken(exchange);
            boolean ok = authService.logout(token);
            sendJsonResponse(exchange, "{\"success\":" + ok + ",\"message\":\"Logged out successfully\"}");
        }
    }

    private class AuthMeApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = extractToken(exchange);
            User user = authService.validateToken(token);
            if (user == null) {
                sendJsonResponse(exchange, "{\"authenticated\":false,\"error\":\"Invalid or expired session\"}");
                return;
            }
            int bookingCount = reservationService.getReservationsByUsername(user.getUsername()).size();
            String json = String.format(
                    "{\"authenticated\":true,\"user\":{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"avatarUrl\":\"%s\"},\"bookingCount\":%d}",
                    escapeJson(user.getUsername()), escapeJson(user.getFullName()),
                    escapeJson(user.getEmail()), escapeJson(user.getPhone()), user.getRole().name(),
                    escapeJson(user.getAuthProvider()), escapeJson(user.getAvatarUrl()),
                    bookingCount
            );
            sendJsonResponse(exchange, json);
        }
    }

    private class AuthUpdateProfileApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);
            String token = params.getOrDefault("token", extractToken(exchange));
            User user = authService.validateToken(token);
            if (user == null) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"Invalid or expired session\"}");
                return;
            }
            String fullName = params.get("fullName");
            String email = params.get("email");
            String phone = params.get("phone");

            boolean updated = authService.updateProfile(token, fullName, email, phone);
            if (updated) {
                int bookingCount = reservationService.getReservationsByUsername(user.getUsername()).size();
                String json = String.format(
                        "{\"success\":true,\"message\":\"Profile updated successfully\",\"user\":{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"avatarUrl\":\"%s\"},\"bookingCount\":%d}",
                        escapeJson(user.getUsername()), escapeJson(user.getFullName()),
                        escapeJson(user.getEmail()), escapeJson(user.getPhone()), user.getRole().name(),
                        escapeJson(user.getAuthProvider()), escapeJson(user.getAvatarUrl()),
                        bookingCount
                );
                sendJsonResponse(exchange, json);
            } else {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"Failed to update profile\"}");
            }
        }
    }

    private class AuthChangePasswordApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);
            String token = params.getOrDefault("token", extractToken(exchange));
            String currentPassword = params.get("currentPassword");
            String newPassword = params.get("newPassword");

            try {
                boolean changed = authService.changePassword(token, currentPassword, newPassword);
                if (changed) {
                    sendJsonResponse(exchange, "{\"success\":true,\"message\":\"Password updated successfully. Please use your new password next time you log in.\"}");
                } else {
                    sendJsonResponse(exchange, "{\"success\":false,\"error\":\"Invalid authentication session.\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, String.format("{\"success\":false,\"error\":\"%s\"}", escapeJson(e.getMessage())));
            } catch (Exception e) {
                sendJsonResponse(exchange, String.format("{\"success\":false,\"error\":\"Password update error: %s\"}", escapeJson(e.getMessage())));
            }
        }
    }

    public static class GoogleUserInfo {
        public String email = "";
        public String name = "";
        public String sub = "";
        public String picture = "";
        public boolean emailVerified = false;
    }

    private GoogleUserInfo verifyGoogleIdToken(String idToken) {
        if (idToken == null || idToken.trim().isEmpty()) {
            return null;
        }
        idToken = idToken.trim();

        // 1. Primary: Verify with Google's official tokeninfo endpoint
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                GoogleUserInfo info = new GoogleUserInfo();
                info.email = extractJsonStringField(json, "email");
                info.name = extractJsonStringField(json, "name");
                info.sub = extractJsonStringField(json, "sub");
                info.picture = extractJsonStringField(json, "picture");
                String ev = extractJsonStringField(json, "email_verified");
                info.emailVerified = "true".equalsIgnoreCase(ev) || json.contains("\"email_verified\":true") || json.contains("\"email_verified\": true");

                if (info.email != null && !info.email.isEmpty()) {
                    return info;
                }
            }
        } catch (Exception e) {
            System.err.println("Note: Online Google tokeninfo lookup failed (" + e.getMessage() + "), using fallback decode.");
        }

        // 2. Fallback: Parse claims payload directly from JWT (header.payload.signature)
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                String payloadJson = new String(decoded, StandardCharsets.UTF_8);
                GoogleUserInfo info = new GoogleUserInfo();
                info.email = extractJsonStringField(payloadJson, "email");
                info.name = extractJsonStringField(payloadJson, "name");
                info.sub = extractJsonStringField(payloadJson, "sub");
                info.picture = extractJsonStringField(payloadJson, "picture");
                if (info.email != null && !info.email.isEmpty()) {
                    return info;
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not decode JWT payload: " + e.getMessage());
        }

        return null;
    }

    private GoogleUserInfo fetchGoogleUserInfoWithAccessToken(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) return null;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                GoogleUserInfo info = new GoogleUserInfo();
                info.email = extractJsonStringField(json, "email");
                info.name = extractJsonStringField(json, "name");
                info.sub = extractJsonStringField(json, "sub");
                info.picture = extractJsonStringField(json, "picture");
                return info;
            }
        } catch (Exception e) {
            System.err.println("Error fetching Google userinfo with access token: " + e.getMessage());
        }
        return null;
    }

    private static String extractJsonStringField(String json, String key) {
        if (json == null || key == null) return "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private class AuthGoogleConfigApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            boolean configured = EnvConfig.isGoogleConfigured();
            String clientId = EnvConfig.getGoogleClientId();
            String redirectUri = EnvConfig.getGoogleRedirectUri();
            String json = String.format(
                    "{\"success\":true,\"configured\":%b,\"clientId\":\"%s\",\"redirectUri\":\"%s\"}",
                    configured, escapeJson(clientId), escapeJson(redirectUri)
            );
            sendJsonResponse(exchange, json);
        }
    }

    private class AuthGoogleLoginApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!EnvConfig.isGoogleConfigured()) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"Google OAuth is not configured in .env file yet. Please set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.\"}");
                return;
            }
            String clientId = EnvConfig.getGoogleClientId();
            String redirectUri = EnvConfig.getGoogleRedirectUri();
            Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
            boolean isAdmin = "true".equalsIgnoreCase(q.get("admin")) || "true".equalsIgnoreCase(q.get("isAdmin"));
            String stateParam = isAdmin ? "&state=admin" : "";

            String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?"
                    + "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&scope=" + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)
                    + "&access_type=offline"
                    + "&prompt=select_account"
                    + stateParam;

            exchange.getResponseHeaders().set("Location", authUrl);
            exchange.sendResponseHeaders(302, -1);
        }
    }

    private class AuthGoogleCallbackApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String query = uri.getQuery();
            Map<String, String> queryParams = query != null ? parseFormBody(query) : new HashMap<>();
            String code = queryParams.get("code");
            String error = queryParams.get("error");
            String state = queryParams.get("state");
            boolean isAdmin = "admin".equalsIgnoreCase(state) || "true".equalsIgnoreCase(queryParams.get("admin"));

            if (error != null && !error.isEmpty()) {
                exchange.getResponseHeaders().set("Location", "/?error=" + URLEncoder.encode("Google auth denied: " + error, StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if (code == null || code.isEmpty()) {
                exchange.getResponseHeaders().set("Location", "/?error=" + URLEncoder.encode("Missing authorization code from Google", StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            try {
                String clientId = EnvConfig.getGoogleClientId();
                String clientSecret = EnvConfig.getGoogleClientSecret();
                String redirectUri = EnvConfig.getGoogleRedirectUri();

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                String tokenForm = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                        + "&grant_type=authorization_code";

                HttpRequest tokenReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://oauth2.googleapis.com/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(tokenForm))
                        .build();

                HttpResponse<String> tokenResp = client.send(tokenReq, HttpResponse.BodyHandlers.ofString());
                if (tokenResp.statusCode() != 200) {
                    exchange.getResponseHeaders().set("Location", "/?error=" + URLEncoder.encode("Token exchange failed: " + tokenResp.body(), StandardCharsets.UTF_8));
                    exchange.sendResponseHeaders(302, -1);
                    return;
                }

                String tokenJson = tokenResp.body();
                String idToken = extractJsonStringField(tokenJson, "id_token");
                String accessToken = extractJsonStringField(tokenJson, "access_token");

                GoogleUserInfo userInfo = null;
                if (idToken != null && !idToken.isEmpty()) {
                    userInfo = verifyGoogleIdToken(idToken);
                }
                if (userInfo == null && accessToken != null && !accessToken.isEmpty()) {
                    userInfo = fetchGoogleUserInfoWithAccessToken(accessToken);
                }

                if (userInfo == null || userInfo.email == null || userInfo.email.isEmpty()) {
                    exchange.getResponseHeaders().set("Location", "/?error=" + URLEncoder.encode("Could not retrieve Google profile", StandardCharsets.UTF_8));
                    exchange.sendResponseHeaders(302, -1);
                    return;
                }

                AuthSession session = isAdmin
                        ? authService.loginAdminWithGoogle(userInfo.email, userInfo.name, userInfo.sub, userInfo.picture)
                        : authService.loginWithGoogle(userInfo.email, userInfo.name, userInfo.sub, userInfo.picture);
                String adminFlag = isAdmin ? "&admin=true" : "";
                exchange.getResponseHeaders().set("Location", "/?token=" + session.getToken() + "&google_login=success" + adminFlag);
                exchange.sendResponseHeaders(302, -1);
            } catch (Exception e) {
                exchange.getResponseHeaders().set("Location", "/?error=" + URLEncoder.encode("Google OAuth error: " + e.getMessage(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(302, -1);
            }
        }
    }

    private class AuthGoogleApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);

            String credential = params.get("credential");
            if (credential == null || credential.isEmpty()) {
                credential = params.get("id_token");
            }
            if (credential == null || credential.isEmpty()) {
                credential = extractJsonStringField(body, "credential");
            }

            String email = params.get("email");
            String name = params.get("name");
            String googleId = params.get("googleId");
            if (googleId == null || googleId.isEmpty()) {
                googleId = params.get("sub");
            }
            String avatarUrl = params.get("avatarUrl");
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                avatarUrl = params.get("picture");
            }

            String isAdminParam = params.get("admin");
            if (isAdminParam == null) isAdminParam = params.get("isAdmin");
            if (isAdminParam == null) isAdminParam = params.get("isAdminLogin");
            if (isAdminParam == null) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
                isAdminParam = q.get("admin");
                if (isAdminParam == null) isAdminParam = q.get("isAdmin");
            }
            boolean isAdmin = "true".equalsIgnoreCase(isAdminParam);

            // Verify Google ID Token (JWT)
            if (credential != null && !credential.isEmpty()) {
                GoogleUserInfo verifiedInfo = verifyGoogleIdToken(credential);
                if (verifiedInfo != null && verifiedInfo.email != null && !verifiedInfo.email.isEmpty()) {
                    email = verifiedInfo.email;
                    if (verifiedInfo.name != null && !verifiedInfo.name.isEmpty()) {
                        name = verifiedInfo.name;
                    }
                    if (verifiedInfo.sub != null && !verifiedInfo.sub.isEmpty()) {
                        googleId = verifiedInfo.sub;
                    }
                    if (verifiedInfo.picture != null && !verifiedInfo.picture.isEmpty()) {
                        avatarUrl = verifiedInfo.picture;
                    }
                } else if (email == null || email.isEmpty()) {
                    sendJsonResponse(exchange, "{\"success\":false,\"error\":\"Invalid Google ID token credential\"}");
                    return;
                }
            }

            if (email == null || email.trim().isEmpty()) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"Google authentication failed: Email is missing.\"}");
                return;
            }

            try {
                AuthSession session = isAdmin
                        ? authService.loginAdminWithGoogle(email, name, googleId, avatarUrl)
                        : authService.loginWithGoogle(email, name, googleId, avatarUrl);
                User user = session.getUser();
                String json = String.format(
                        "{\"success\":true,\"token\":\"%s\",\"user\":{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"avatarUrl\":\"%s\"}}",
                        session.getToken(), escapeJson(user.getUsername()), escapeJson(user.getFullName()),
                        escapeJson(user.getEmail()), escapeJson(user.getPhone()), user.getRole().name(),
                        escapeJson(user.getAuthProvider()), escapeJson(user.getAvatarUrl())
                );
                sendJsonResponse(exchange, json);
            } catch (Exception e) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AuthMyBookingsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = extractToken(exchange);
            User user = authService.validateToken(token);
            if (user == null) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"Unauthorized. Please log in.\"}");
                return;
            }

            List<Reservation> userBookings = reservationService.getReservationsByUsername(user.getUsername());
            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true,\"username\":\"").append(escapeJson(user.getUsername())).append("\",\"bookings\":[");
            for (int i = 0; i < userBookings.size(); i++) {
                Reservation r = userBookings.get(i);
                if (i > 0) sb.append(",");
                DijkstraResult<Station> dr = networkService.findShortestRoute(r.getSourceStation().getId(), r.getDestinationStation().getId());
                double dist = (dr != null && dr.isReachable()) ? dr.getTotalDistance() : 0.0;
                String txnId = "TXN-RAIL-" + Math.abs((r.getPnr() + r.getTrainId()).hashCode() % 900000 + 100000);
                sb.append(String.format(
                        "{\"pnr\":\"%s\",\"passenger\":\"%s\",\"age\":%d,\"gender\":\"%s\",\"seatCount\":%d,\"seats\":%s,\"seatsDisplay\":\"%s\",\"passengers\":%s,\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"fromName\":\"%s\",\"toName\":\"%s\",\"class\":\"%s\",\"seat\":%d,\"wl\":%d,\"rac\":%d,\"quota\":\"%s\",\"departureTime\":\"%s\",\"arrivalTime\":\"%s\",\"fare\":%.2f,\"refundAmount\":%.2f,\"status\":\"%s\",\"date\":\"%s\",\"travelDate\":\"%s\",\"formattedTravelDate\":\"%s\",\"distance\":%.1f,\"txnId\":\"%s\"}",
                        r.getPnr(), escapeJson(r.getPassenger().getName()), r.getPassenger().getAge(), escapeJson(r.getPassenger().getGender()),
                        r.getSeatCount(), formatSeatsJson(r), escapeJson(r.getSeatNumbersDisplay()), formatPassengersJson(r),
                        r.getTrainId(), escapeJson(r.getTrainName()),
                        r.getSourceStation().getId(), r.getDestinationStation().getId(),
                        escapeJson(r.getSourceStation().getName()), escapeJson(r.getDestinationStation().getName()),
                        escapeJson(r.getTravelClass()), r.getSeatNumber(), r.getWaitingListNumber(), r.getRacNumber(),
                        escapeJson(r.getQuota()), escapeJson(r.getDepartureTime()), escapeJson(r.getArrivalTime()),
                        r.getFare(), r.getRefundAmount(),
                        r.getStatus().name(), r.getFormattedBookingTime(),
                        escapeJson(r.getTravelDate()), escapeJson(r.getFormattedTravelDate()),
                        dist, txnId
                ));
            }
            sb.append("]}");
            sendJsonResponse(exchange, sb.toString());
        }
    }

    private String extractToken(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        if (queryParams.containsKey("token") && !queryParams.get("token").isEmpty()) {
            return queryParams.get("token");
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readRequestBody(exchange);
            Map<String, String> form = parseFormBody(body);
            if (form.containsKey("token") && !form.get("token").isEmpty()) {
                return form.get("token");
            }
        }
        return null;
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        sendJsonResponse(exchange, 200, json);
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                map.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static Map<String, String> parseFormBody(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return map;
        String trimmed = body.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            // Self-contained lightweight JSON string property parser
            String inner = trimmed.substring(1, trimmed.length() - 1);
            String[] pairs = inner.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length >= 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim().replace("\"", "");
                    map.put(k, v);
                }
            }
            return map;
        }
        // Supports URL encoded body
        for (String pair : trimmed.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length >= 2) {
                map.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8).trim(),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8).trim());
            }
        }
        return map;
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private String getDashboardHtml() {
        File[] candidateFiles = new File[] {
            new File("web/index.html"),
            new File("src/com/railway/web/index.html"),
            new File("../web/index.html")
        };
        for (File f : candidateFiles) {
            if (f.exists() && f.isFile()) {
                try {
                    return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
            }
        }

        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\" />\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n" +
                "  <title>Indian Railways - Route & Reservation Dashboard</title>\n" +
                "  <style>\n" +
                "    :root { --primary: #0284c7; --accent: #0f172a; --bg: #f8fafc; --card: #ffffff; --success: #16a34a; --warning: #d97706; --border: #e2e8f0; }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }\n" +
                "    body { background: var(--bg); color: var(--accent); padding-bottom: 50px; }\n" +
                "    header { background: linear-gradient(135deg, #0369a1, #0c4a6e); color: white; padding: 24px; text-align: center; box-shadow: 0 4px 10px rgba(0,0,0,0.1); }\n" +
                "    header h1 { font-size: 1.8rem; margin-bottom: 6px; }\n" +
                "    header p { opacity: 0.9; font-size: 0.95rem; }\n" +
                "    .container { max-width: 1200px; margin: 30px auto; padding: 0 20px; display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }\n" +
                "    .card { background: var(--card); border-radius: 12px; padding: 22px; border: 1px solid var(--border); box-shadow: 0 2px 6px rgba(0,0,0,0.04); }\n" +
                "    .card-full { grid-column: 1 / -1; }\n" +
                "    .card h2 { font-size: 1.25rem; color: #0284c7; margin-bottom: 14px; border-bottom: 2px solid #e0f2fe; padding-bottom: 8px; display: flex; justify-content: space-between; align-items: center; }\n" +
                "    .badge { font-size: 0.75rem; background: #e0f2fe; color: #0369a1; padding: 4px 10px; border-radius: 12px; font-weight: bold; }\n" +
                "    table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 0.9rem; }\n" +
                "    th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--border); }\n" +
                "    th { background: #f1f5f9; color: #475569; font-weight: 600; }\n" +
                "    .btn { background: var(--primary); color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 600; transition: 0.2s; }\n" +
                "    .btn:hover { background: #0369a1; }\n" +
                "    .btn-red { background: #ef4444; }\n" +
                "    .btn-red:hover { background: #dc2626; }\n" +
                "    input, select { width: 100%; padding: 9px 12px; border: 1px solid #cbd5e1; border-radius: 6px; margin: 6px 0 14px 0; font-size: 0.95rem; }\n" +
                "    .tag { display: inline-block; padding: 3px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: 600; }\n" +
                "    .tag-cnf { background: #dcfce7; color: #15803d; }\n" +
                "    .tag-wl { background: #fef3c7; color: #b45309; }\n" +
                "    .route-box { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 14px; margin-top: 12px; font-weight: 600; color: #166534; }\n" +
                "    .pnr-box { background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 14px; margin-top: 12px; }\n" +
                "    @media(max-width: 850px) { .container { grid-template-columns: 1fr; } }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <header>\n" +
                "    <h1>🚆 Railway Reservation & Route Management System</h1>\n" +
                "    <p>DSA Visualizer: Graph Dijkstra Shortest Paths • FIFO Waitlist Queue • O(N log N) Sorting</p>\n" +
                "  </header>\n" +
                "  <div class=\"container\">\n" +
                "    <!-- Shortest Route Finder -->\n" +
                "    <div class=\"card\">\n" +
                "      <h2><span>📍 Graph Route Finder (Dijkstra)</span> <span class=\"badge\">O((V+E) log V)</span></h2>\n" +
                "      <label>Origin Station:</label>\n" +
                "      <select id=\"routeSrc\"></select>\n" +
                "      <label>Destination Station:</label>\n" +
                "      <select id=\"routeDst\"></select>\n" +
                "      <button class=\"btn\" onclick=\"calculateRoute()\">Compute Optimal Route</button>\n" +
                "      <div id=\"routeOutput\" style=\"display:none;\" class=\"route-box\"></div>\n" +
                "    </div>\n" +
                "    <!-- Quick PNR Inquiry -->\n" +
                "    <div class=\"card\">\n" +
                "      <h2><span>🔍 PNR Status & Cancellation</span> <span class=\"badge\">Instant</span></h2>\n" +
                "      <label>Enter PNR (e.g. PNR-100101):</label>\n" +
                "      <input type=\"text\" id=\"pnrInput\" placeholder=\"PNR-100101\" />\n" +
                "      <div style=\"display:flex; gap:10px;\">\n" +
                "        <button class=\"btn\" onclick=\"checkPnr()\">Check Status</button>\n" +
                "        <button class=\"btn btn-red\" onclick=\"cancelPnr()\">Cancel Ticket</button>\n" +
                "      </div>\n" +
                "      <div id=\"pnrOutput\" style=\"display:none;\" class=\"pnr-box\"></div>\n" +
                "    </div>\n" +
                "    <!-- Book a Ticket -->\n" +
                "    <div class=\"card\">\n" +
                "      <h2><span>🎟️ Book Ticket Form</span> <span class=\"badge\">Auto-WL Queue</span></h2>\n" +
                "      <label>Select Train:</label>\n" +
                "      <select id=\"bookTrain\"></select>\n" +
                "      <label>Passenger Full Name:</label>\n" +
                "      <input type=\"text\" id=\"passName\" placeholder=\"e.g. Priya Iyer\" />\n" +
                "      <div style=\"display:grid; grid-template-columns: 1fr 1fr; gap:10px;\">\n" +
                "        <div><label>Age:</label><input type=\"number\" id=\"passAge\" value=\"28\" /></div>\n" +
                "        <div><label>Gender:</label><select id=\"passGender\"><option>F</option><option>M</option><option>O</option></select></div>\n" +
                "      </div>\n" +
                "      <button class=\"btn\" onclick=\"bookTicket()\">Book Now</button>\n" +
                "      <div id=\"bookResult\" style=\"display:none; margin-top:10px;\"></div>\n" +
                "    </div>\n" +
                "    <!-- Station Directory -->\n" +
                "    <div class=\"card\">\n" +
                "      <h2><span>🏛️ Railway Network Stations</span> <span class=\"badge\">Graph Nodes</span></h2>\n" +
                "      <div style=\"max-height: 250px; overflow-y: auto;\">\n" +
                "        <table id=\"stationsTable\">\n" +
                "          <thead><tr><th>Code</th><th>Station Name</th></tr></thead>\n" +
                "          <tbody></tbody>\n" +
                "        </table>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "    <!-- Fleet Table -->\n" +
                "    <div class=\"card card-full\">\n" +
                "      <h2><span>🚂 Active Train Fleet (Sorted by Available Seats via MergeSort)</span> <span class=\"badge\">Live Inventory</span></h2>\n" +
                "      <table id=\"trainsTable\">\n" +
                "        <thead>\n" +
                "          <tr><th>Train#</th><th>Train Name</th><th>From</th><th>To</th><th>Seats Avail</th><th>Waiting List</th><th>Rate/km</th></tr>\n" +
                "        </thead>\n" +
                "        <tbody></tbody>\n" +
                "      </table>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "  <script>\n" +
                "    let stations = [];\n" +
                "    let trains = [];\n" +
                "    async function loadData() {\n" +
                "      const resSt = await fetch('/api/stations');\n" +
                "      stations = await resSt.json();\n" +
                "      const resTr = await fetch('/api/trains');\n" +
                "      trains = await resTr.json();\n" +
                "      renderStations();\n" +
                "      renderTrains();\n" +
                "    }\n" +
                "    function renderStations() {\n" +
                "      const srcSel = document.getElementById('routeSrc');\n" +
                "      const dstSel = document.getElementById('routeDst');\n" +
                "      const tbody = document.querySelector('#stationsTable tbody');\n" +
                "      srcSel.innerHTML = ''; dstSel.innerHTML = ''; tbody.innerHTML = '';\n" +
                "      stations.forEach((s, idx) => {\n" +
                "        srcSel.innerHTML += `<option value='${s.id}'>${s.name} [${s.id}]</option>`;\n" +
                "        dstSel.innerHTML += `<option value='${s.id}' ${idx === 2 ? 'selected' : ''}>${s.name} [${s.id}]</option>`;\n" +
                "        tbody.innerHTML += `<tr><td><strong>${s.id}</strong></td><td>${s.name}</td></tr>`;\n" +
                "      });\n" +
                "    }\n" +
                "    function renderTrains() {\n" +
                "      const bTrain = document.getElementById('bookTrain');\n" +
                "      const tbody = document.querySelector('#trainsTable tbody');\n" +
                "      bTrain.innerHTML = ''; tbody.innerHTML = '';\n" +
                "      trains.forEach(t => {\n" +
                "        bTrain.innerHTML += `<option value='${t.id}'>${t.name} (${t.id}) [${t.from} -> ${t.to}]</option>`;\n" +
                "        const availTag = t.available > 0 ? `<span class='tag tag-cnf'>${t.available} / ${t.total} Seats</span>` : `<span class='tag tag-wl'>0 Available</span>`;\n" +
                "        const wlTag = t.waitlist > 0 ? `<span class='tag tag-wl'>WL-${t.waitlist}</span>` : `<span style='color:#94a3b8;'>None</span>`;\n" +
                "        tbody.innerHTML += `<tr><td><strong>${t.id}</strong></td><td>${t.name}</td><td>${t.from}</td><td>${t.to}</td><td>${availTag}</td><td>${wlTag}</td><td>₹${t.farePerKm}</td></tr>`;\n" +
                "      });\n" +
                "    }\n" +
                "    async function calculateRoute() {\n" +
                "      const src = document.getElementById('routeSrc').value;\n" +
                "      const dst = document.getElementById('routeDst').value;\n" +
                "      const out = document.getElementById('routeOutput');\n" +
                "      const res = await fetch(`/api/route?src=${src}&dst=${dst}`);\n" +
                "      const data = await res.json();\n" +
                "      if (!data.reachable) {\n" +
                "        out.innerHTML = `<span style='color:#dc2626;'>No direct connected track found between ${src} and ${dst}.</span>`;\n" +
                "      } else {\n" +
                "        const pathStr = data.path.map(p => `<strong>${p.id}</strong> (${p.name})`).join(' ──▶ ');\n" +
                "        out.innerHTML = `<div>Shortest Path: ${pathStr}</div><div style='margin-top:6px;'>Total Track Distance: <strong>${data.distance.toFixed(1)} km</strong></div>`;\n" +
                "      }\n" +
                "      out.style.display = 'block';\n" +
                "    }\n" +
                "    async function checkPnr() {\n" +
                "      const pnr = document.getElementById('pnrInput').value.trim();\n" +
                "      const out = document.getElementById('pnrOutput');\n" +
                "      if (!pnr) return alert('Please enter PNR');\n" +
                "      const res = await fetch(`/api/pnr?pnr=${pnr}`);\n" +
                "      const data = await res.json();\n" +
                "      if (!data.found) {\n" +
                "        out.innerHTML = `<span style='color:#dc2626;'>PNR ${pnr} not found.</span>`;\n" +
                "      } else {\n" +
                "        const stTag = data.status === 'CONFIRMED' ? `<span class='tag tag-cnf'>CONFIRMED (Seat #${data.seat})</span>` : (data.status === 'WAITING_LIST' ? `<span class='tag tag-wl'>WAITING LIST (WL-${data.wl})</span>` : `<span style='color:#dc2626;'>CANCELLED</span>`);\n" +
                "        out.innerHTML = `<div><strong>PNR:</strong> ${data.pnr} | <strong>Passenger:</strong> ${data.passenger}</div><div style='margin-top:4px;'><strong>Train:</strong> ${data.train} | ${data.from} ➔ ${data.to}</div><div style='margin-top:4px;'><strong>Status:</strong> ${stTag} | <strong>Fare:</strong> ₹${data.fare.toFixed(2)}</div>`;\n" +
                "      }\n" +
                "      out.style.display = 'block';\n" +
                "    }\n" +
                "    async function cancelPnr() {\n" +
                "      const pnr = document.getElementById('pnrInput').value.trim();\n" +
                "      if (!pnr) return alert('Please enter PNR to cancel');\n" +
                "      const res = await fetch('/api/cancel', { method: 'POST', body: 'pnr=' + encodeURIComponent(pnr) });\n" +
                "      const data = await res.json();\n" +
                "      alert(data.message);\n" +
                "      loadData();\n" +
                "      checkPnr();\n" +
                "    }\n" +
                "    async function bookTicket() {\n" +
                "      const trainId = document.getElementById('bookTrain').value;\n" +
                "      const name = document.getElementById('passName').value.trim();\n" +
                "      const age = document.getElementById('passAge').value;\n" +
                "      const gender = document.getElementById('passGender').value;\n" +
                "      const out = document.getElementById('bookResult');\n" +
                "      if (!name) return alert('Enter passenger name');\n" +
                "      const t = trains.find(tr => tr.id === trainId);\n" +
                "      const body = `trainId=${trainId}&name=${encodeURIComponent(name)}&age=${age}&gender=${gender}&src=${t.from}&dst=${t.to}`;\n" +
                "      const res = await fetch('/api/book', { method: 'POST', body: body });\n" +
                "      const data = await res.json();\n" +
                "      if (data.success) {\n" +
                "        const stTag = data.status === 'CONFIRMED' ? `<span class='tag tag-cnf'>CONFIRMED (Seat #${data.seat})</span>` : `<span class='tag tag-wl'>WAITING LIST (WL-${data.wl})</span>`;\n" +
                "        out.innerHTML = `<div class='route-box'>🎉 Booking Successful! PNR: <strong>${data.pnr}</strong> | Status: ${stTag} | Fare: ₹${data.fare.toFixed(2)}</div>`;\n" +
                "        document.getElementById('pnrInput').value = data.pnr;\n" +
                "        loadData();\n" +
                "      } else {\n" +
                "        out.innerHTML = `<div style='color:#dc2626; padding:10px;'>Booking Error: ${data.error}</div>`;\n" +
                "      }\n" +
                "      out.style.display = 'block';\n" +
                "    }\n" +
                "    window.onload = loadData;\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>\n";
    }
}
