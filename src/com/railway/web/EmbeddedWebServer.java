package com.railway.web;

import com.railway.dsa.DijkstraResult;
import com.railway.model.*;
import com.railway.service.AuthService;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

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
            server.createContext("/api/auth/my-bookings", new AuthMyBookingsApiHandler());
            server.createContext("/api/auth/google", new AuthGoogleApiHandler());
            server.createContext("/api/admin/add-station", new AddStationApiHandler());
            server.createContext("/api/admin/add-track", new AddTrackApiHandler());
            server.createContext("/api/admin/add-train", new AddTrainApiHandler());

            server.setExecutor(null); // Default single-thread executor
            server.start();
        } catch (IOException e) {
            System.err.println("Note: Web server could not bind to port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
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
                json.append(String.format("{\"id\":\"%s\",\"name\":\"%s\"}", s.getId(), s.getName()));
                if (i < stations.size() - 1) json.append(",");
            }
            json.append("]");
            sendJsonResponse(exchange, json.toString());
        }
    }

    private class TrainsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Train> trains = trainService.getAllTrainsSortedByAvailableSeats();
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
                        "{\"id\":\"%s\",\"name\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"total\":%d,\"available\":%d,\"waitlist\":%d,\"farePerKm\":%.2f,\"stops\":%s}",
                        t.getId(), t.getName(), t.getSource().getId(), t.getDestination().getId(),
                        t.getTotalSeats(), t.getAvailableSeats(), t.getWaitingListCount(), t.getFarePerKm(),
                        stopsJson.toString()
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

            String json = String.format(
                    "{\"found\":true,\"pnr\":\"%s\",\"passenger\":\"%s\",\"train\":\"%s (%s)\",\"from\":\"%s\",\"to\":\"%s\",\"status\":\"%s\",\"seat\":%d,\"wl\":%d,\"fare\":%.2f,\"travelClass\":\"%s\"}",
                    res.getPnr(), res.getPassenger().getName(), res.getTrainName(), res.getTrainId(),
                    res.getSourceStation().getName(), res.getDestinationStation().getName(),
                    res.getStatus().name(), res.getSeatNumber(), res.getWaitingListNumber(), res.getFare(),
                    res.getTravelClass()
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
                Reservation res = reservationService.bookTicket(trainId, name, age, gender, "WEB-" + System.currentTimeMillis() % 10000, src, dst, travelClass, bookedByUsername);
                String json = String.format(
                        "{\"success\":true,\"pnr\":\"%s\",\"status\":\"%s\",\"seat\":%d,\"wl\":%d,\"fare\":%.2f,\"passenger\":\"%s\",\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"travelClass\":\"%s\",\"bookedBy\":\"%s\"}",
                        res.getPnr(), res.getStatus().name(), res.getSeatNumber(), res.getWaitingListNumber(), res.getFare(),
                        escapeJson(res.getPassenger().getName()), res.getTrainId(), escapeJson(res.getTrainName()),
                        res.getSourceStation().getId(), res.getDestinationStation().getId(),
                        escapeJson(res.getTravelClass()),
                        res.getBookedByUsername() != null ? escapeJson(res.getBookedByUsername()) : ""
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
            String json = String.format(
                    "{\"success\":%b,\"message\":\"%s\",\"promotedPnr\":\"%s\"}",
                    result.isSuccess(), escapeJson(result.message()), promotedPnr
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
                json.append(String.format("{\"id\":\"%s\",\"name\":\"%s\"}", s.getId(), s.getName()));
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

    private class AuthGoogleApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"success\":false,\"error\":\"POST required\"}");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormBody(body);

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
                sb.append(String.format(
                        "{\"pnr\":\"%s\",\"passenger\":\"%s\",\"trainId\":\"%s\",\"trainName\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"class\":\"%s\",\"seat\":%d,\"wl\":%d,\"fare\":%.2f,\"status\":\"%s\",\"date\":\"%s\"}",
                        r.getPnr(), escapeJson(r.getPassenger().getName()), r.getTrainId(), escapeJson(r.getTrainName()),
                        r.getSourceStation().getId(), r.getDestinationStation().getId(),
                        escapeJson(r.getTravelClass()), r.getSeatNumber(), r.getWaitingListNumber(),
                        r.getFare(), r.getStatus().name(), r.getFormattedBookingTime()
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
