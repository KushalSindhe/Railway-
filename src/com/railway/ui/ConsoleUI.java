package com.railway.ui;

import com.railway.service.AuthService;
import com.railway.service.RailwayNetworkService;
import com.railway.service.ReservationService;
import com.railway.service.TrainService;

import java.util.Scanner;

/**
 * Main interactive console UI coordinator.
 * Handles top-level role navigation, input sanitization, and ANSI formatting.
 */
public class ConsoleUI {
    // ANSI formatting codes
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String CYAN = "\u001B[36m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";

    private final Scanner scanner;
    private final RailwayNetworkService networkService;
    private final TrainService trainService;
    private final ReservationService reservationService;
    private final AuthService authService;
    private final PassengerMenu passengerMenu;
    private final AdminMenu adminMenu;
    private final int webPort;

    public ConsoleUI(RailwayNetworkService networkService,
                     TrainService trainService,
                     ReservationService reservationService,
                     AuthService authService,
                     int webPort) {
        this.scanner = new Scanner(System.in);
        this.networkService = networkService;
        this.trainService = trainService;
        this.reservationService = reservationService;
        this.authService = authService != null ? authService : new AuthService();
        this.webPort = webPort;
        this.passengerMenu = new PassengerMenu(scanner, networkService, trainService, reservationService, this.authService);
        this.adminMenu = new AdminMenu(scanner, networkService, trainService, reservationService);
    }

    public ConsoleUI(RailwayNetworkService networkService,
                     TrainService trainService,
                     ReservationService reservationService,
                     int webPort) {
        this(networkService, trainService, reservationService, new AuthService(), webPort);
    }

    public void start() {
        boolean running = true;
        while (running) {
            printBanner();
            System.out.println(BOLD + "  SELECT USER ROLE / SYSTEM OPTION:" + RESET);
            System.out.println("  " + CYAN + "[1]" + RESET + " Passenger Portal (Search, Book, Cancel, Personal Bookings)");
            System.out.println("  " + CYAN + "[2]" + RESET + " Reservation Clerk / Administrator (Manage Stations, Trains, Charts)");
            System.out.println("  " + CYAN + "[3]" + RESET + " View DSA Concept & Complexity Mapping (Viva Reference)");
            System.out.println("  " + CYAN + "[4]" + RESET + " Web Dashboard Info (" + GREEN + "http://localhost:" + webPort + RESET + ")");
            System.out.println("  " + RED + "[0]" + RESET + " Exit Application");
            System.out.println("──────────────────────────────────────────────────────────────────");
            System.out.print("  Enter choice [0-4]: ");

            int choice = readIntChoice();
            switch (choice) {
                case 1:
                    passengerMenu.show();
                    break;
                case 2:
                    handleAdminLogin();
                    break;
                case 3:
                    showDsaSummary();
                    break;
                case 4:
                    showWebDashboardInfo();
                    break;
                case 0:
                    running = false;
                    System.out.println("\n  " + GREEN + "Thank you for using the Railway Reservation System. Have a safe journey!" + RESET);
                    break;
                default:
                    System.out.println("  " + RED + "Invalid selection. Please enter a valid menu number." + RESET);
                    pause();
            }
        }
    }

    private void handleAdminLogin() {
        System.out.println("\n" + YELLOW + BOLD + "─── ADMIN / RESERVATION CLERK AUTHENTICATION ───" + RESET);
        System.out.print("  Enter Admin Username (Default: admin): ");
        String username = scanner.nextLine().trim();
        if (username.isEmpty()) username = "admin";

        System.out.print("  Enter Admin Password (Default: admin123): ");
        String passcode = scanner.nextLine().trim();
        if (passcode.isEmpty()) passcode = "admin123";

        try {
            var session = authService.login(username, passcode);
            if (session.getUser().isAdmin()) {
                System.out.println("  " + GREEN + BOLD + "Access granted! Welcome, " + session.getUser().getFullName() + "!" + RESET);
                adminMenu.show();
            } else {
                System.out.println("  " + RED + "Access denied. Account @" + username + " does not hold Administrator privileges." + RESET);
                pause();
            }
        } catch (Exception e) {
            System.out.println("  " + RED + "Authentication failed: " + e.getMessage() + RESET);
            pause();
        }
    }

    private void printBanner() {
        System.out.println("\n" + BLUE + BOLD +
                "==================================================================\n" +
                "       INDIAN RAILWAYS RESERVATION & ROUTE MANAGEMENT SYSTEM       \n" +
                "             Built with Core Java 21 & Custom DSA Engine          \n" +
                "==================================================================" + RESET);
    }

    private void showDsaSummary() {
        System.out.println("\n" + PURPLE + BOLD +
                "══════════════════════════════════════════════════════════════════\n" +
                "                  DSA CONCEPTS & COMPLEXITY OVERVIEW               \n" +
                "══════════════════════════════════════════════════════════════════" + RESET);
        System.out.println("  1. " + BOLD + "GRAPH (Adjacency List)" + RESET + ":");
        System.out.println("     - Vertices: Stations | Weighted Edges: Tracks (Distance in km)");
        System.out.println("     - Space: O(V + E) | Optimal for sparse railway topology.");
        System.out.println("\n  2. " + BOLD + "DIJKSTRA'S ALGORITHM (Shortest Path)" + RESET + ":");
        System.out.println("     - Min-Priority Queue for greedy selection of closest unvisited station.");
        System.out.println("     - Time: O((V + E) log V) | Discovers least distance route.");
        System.out.println("\n  3. " + BOLD + "FIFO QUEUE (Linked-Node ADT)" + RESET + ":");
        System.out.println("     - Enqueue: O(1) | Dequeue: O(1)");
        System.out.println("     - Enforces strict first-come-first-served waiting list auto-promotion.");
        System.out.println("\n  4. " + BOLD + "SEARCHING (Binary & Linear)" + RESET + ":");
        System.out.println("     - Binary Search: O(log N) for fast train lookup by Train ID.");
        System.out.println("     - Substring Search: O(N) for user-friendly name matching.");
        System.out.println("\n  5. " + BOLD + "SORTING (MergeSort & QuickSort)" + RESET + ":");
        System.out.println("     - MergeSort: O(N log N) stable sort for available seats and booking charts.");
        System.out.println("     - QuickSort: O(N log N) in-place sort for train fleet by ID.");
        System.out.println("══════════════════════════════════════════════════════════════════");
        pause();
    }

    private void showWebDashboardInfo() {
        System.out.println("\n" + GREEN + BOLD +
                "──────────────────────────────────────────────────────────────────\n" +
                "                     BUILT-IN WEB DASHBOARD                       \n" +
                "──────────────────────────────────────────────────────────────────" + RESET);
        System.out.println("  The embedded zero-dependency HTTP server is actively running at:");
        System.out.println("  " + CYAN + BOLD + "http://localhost:" + webPort + "/" + RESET);
        System.out.println("\n  Features available in Browser:");
        System.out.println("  * Interactive Station & Route Map Visualizer");
        System.out.println("  * Live Train Search & Seat Inventory");
        System.out.println("  * Instant Booking & Waitlist Queue Monitor");
        System.out.println("  * Real-time PNR Status Inquiry");
        System.out.println("──────────────────────────────────────────────────────────────────");
        pause();
    }

    public static void pause() {
        System.out.print("\n  Press [Enter] to continue...");
        try {
            System.in.read();
        } catch (Exception ignored) {}
    }

    private int readIntChoice() {
        try {
            String line = scanner.nextLine().trim();
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
