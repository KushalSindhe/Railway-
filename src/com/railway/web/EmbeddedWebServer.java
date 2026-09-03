package com.railway.web;

import com.railway.dsa.DijkstraResult;
import com.railway.model.*;
import com.railway.service.AuthService;
import com.railway.service.EnvConfig;
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
            int reservationsCount = reservationService.getAllReservations().size();

            String json = String.format(
                    "{\"stations\":%d,\"trains\":%d,\"totalSeats\":%d,\"availableSeats\":%d,\"waitlist\":%d,\"reservations\":%d}",
                    stationsCount, trainsCount, totalSeats, availableSeats, waitlistCount, reservationsCount
            );
            sendJsonResponse(exchange, json);
        }
    }

    private class AddStationApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
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

            List<User> users = authService.getAllUsers();
            int totalUsers = users.size();
            int passengerCount = 0;
            int adminCount = 0;
            int googleCount = 0;
            for (User u : users) {
                if (u.getRole() == UserRole.ADMIN) adminCount++;
                else passengerCount++;
                if ("google".equalsIgnoreCase(u.getAuthProvider())) googleCount++;
            }

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

            String json = String.format(
                    "{\"success\":true,\"totalBookings\":%d,\"cnfBookings\":%d,\"wlBookings\":%d,\"racBookings\":%d,\"cancelledBookings\":%d,\"totalRevenue\":%.2f,\"totalUsers\":%d,\"passengerCount\":%d,\"adminCount\":%d,\"googleCount\":%d,\"stations\":%d,\"trains\":%d,\"totalSeats\":%d,\"availableSeats\":%d,\"waitlist\":%d,\"totalTrackKm\":%.1f}",
                    totalBookings, cnfCount, wlCount, racCount, canCount, totalRevenue,
                    totalUsers, passengerCount, adminCount, googleCount,
                    stationsCount, trainsCount, totalSeats, availableSeats, totalWaitlist, totalTrackKm
            );
            sendJsonResponse(exchange, json);
        }
    }

    private class AdminBookingsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
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
            List<User> users = authService.getAllUsers();
            StringBuilder sb = new StringBuilder("{\"success\":true,\"total\":").append(users.size()).append(",\"users\":[");
            for (int i = 0; i < users.size(); i++) {
                User u = users.get(i);
                if (i > 0) sb.append(",");
                int bookingCount = reservationService.getReservationsByUsername(u.getUsername()).size();
                sb.append(String.format(
                        "{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"role\":\"%s\",\"authProvider\":\"%s\",\"avatarUrl\":\"%s\",\"bookingCount\":%d}",
                        escapeJson(u.getUsername()), escapeJson(u.getFullName()),
                        escapeJson(u.getEmail()), escapeJson(u.getPhone()),
                        u.getRole().name(),
                        escapeJson(u.getAuthProvider()), escapeJson(u.getAvatarUrl()),
                        bookingCount
                ));
            }
            sb.append("]}");
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
            String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?"
                    + "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&scope=" + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)
                    + "&access_type=offline"
                    + "&prompt=select_account";

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

                AuthSession session = authService.loginWithGoogle(userInfo.email, userInfo.name, userInfo.sub, userInfo.picture);
                exchange.getResponseHeaders().set("Location", "/?token=" + session.getToken() + "&google_login=success");
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
                AuthSession session = authService.loginWithGoogle(email, name, googleId, avatarUrl);
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

    private void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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
