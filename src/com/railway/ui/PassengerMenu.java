package com.railway.ui;

import com.railway.dsa.DijkstraResult;
import com.railway.model.BookingStatus;
import com.railway.model.Passenger;
import com.railway.model.Reservation;
import com.railway.model.Station;
import com.railway.model.Train;
import com.railway.model.User;
import com.railway.model.UserRole;
import com.railway.service.AuthService;
import com.railway.service.RailwayNetworkService;
import com.railway.service.ReservationService;
import com.railway.service.ReservationService.CancellationResult;
import com.railway.service.TrainService;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import static com.railway.ui.ConsoleUI.*;

/**
 * Interactive Console menu for Passenger users.
 */
public class PassengerMenu {
    private final Scanner scanner;
    private final RailwayNetworkService networkService;
    private final TrainService trainService;
    private final ReservationService reservationService;
    private final AuthService authService;
    private User loggedInUser;

    public PassengerMenu(Scanner scanner,
                         RailwayNetworkService networkService,
                         TrainService trainService,
                         ReservationService reservationService,
                         AuthService authService) {
        this.scanner = scanner;
        this.networkService = networkService;
        this.trainService = trainService;
        this.reservationService = reservationService;
        this.authService = authService != null ? authService : new AuthService();
    }

    public PassengerMenu(Scanner scanner,
                         RailwayNetworkService networkService,
                         TrainService trainService,
                         ReservationService reservationService) {
        this(scanner, networkService, trainService, reservationService, new AuthService());
    }

    public void setLoggedInUser(User user) {
        this.loggedInUser = user;
    }

    public User getLoggedInUser() {
        return loggedInUser;
    }

    public void show() {
        boolean inPassenger = true;
        while (inPassenger) {
            System.out.println("\n" + CYAN + BOLD +
                    "┌────────────────────────────────────────────────────────────────┐\n" +
                    "│                     PASSENGER SERVICES PORTAL                  │\n" +
                    "└────────────────────────────────────────────────────────────────┘" + RESET);
            String userTag = loggedInUser != null
                    ? (GREEN + BOLD + loggedInUser.getFullName() + " (@" + loggedInUser.getUsername() + ")" + RESET)
                    : (YELLOW + "Guest (Sign In to save bookings to your account)" + RESET);
            System.out.println("  User Account: " + userTag);
            System.out.println("──────────────────────────────────────────────────────────────────");
            System.out.println("  [1] Search Trains between Stations");
            System.out.println("  [2] Check Seat Availability & Fare for a Train");
            System.out.println("  [3] Book a Train Ticket (CNF / Waiting List)");
            System.out.println("  [4] Cancel a Ticket by PNR (Auto-promotes waitlist)");
            System.out.println("  [5] Check PNR / Reservation Status");
            System.out.println("  [6] View My Booked Tickets History" + (loggedInUser != null ? (" (" + loggedInUser.getUsername() + ")") : ""));
            System.out.println("  [7] Passenger Authentication (Sign In / Register / Switch)");
            System.out.println("  [8] Find Shortest Route & Stations (Graph Dijkstra Algorithm)");
            System.out.println("  [9] View All Railway Stations in Network");
            System.out.println("  [0] Return to Main Menu");
            System.out.println("──────────────────────────────────────────────────────────────────");
            System.out.print("  Enter choice [0-9]: ");

            int choice = readInt();
            switch (choice) {
                case 1:
                    handleSearchTrains();
                    break;
                case 2:
                    handleCheckAvailability();
                    break;
                case 3:
                    handleBookTicket();
                    break;
                case 4:
                    handleCancelTicket();
                    break;
                case 5:
                    handleCheckPnr();
                    break;
                case 6:
                    handleMyBookings();
                    break;
                case 7:
                    handleUserAccount();
                    break;
                case 8:
                    handleFindRoute();
                    break;
                case 9:
                    handleViewStations();
                    break;
                case 0:
                    inPassenger = false;
                    break;
                default:
                    System.out.println("  " + RED + "Invalid choice. Please choose between 0 and 9." + RESET);
                    pause();
            }
        }
    }

    private void handleSearchTrains() {
        System.out.println("\n" + YELLOW + "─── SEARCH TRAINS BETWEEN STATIONS ───" + RESET);
        System.out.print("  Enter Source Station Code (e.g., NDLS, MAS, CSMT): ");
        String src = scanner.nextLine().trim().toUpperCase();
        System.out.print("  Enter Destination Station Code (e.g., SBC, HYB, HWH): ");
        String dst = scanner.nextLine().trim().toUpperCase();

        if (!networkService.hasStation(src)) {
            System.out.println("  " + RED + "Source station '" + src + "' is invalid or does not exist." + RESET);
            pause();
            return;
        }
        if (!networkService.hasStation(dst)) {
            System.out.println("  " + RED + "Destination station '" + dst + "' is invalid or does not exist." + RESET);
            pause();
            return;
        }

        List<Train> trains = trainService.searchTrains(src, dst);
        if (trains.isEmpty()) {
            System.out.println("  " + YELLOW + "No direct or scheduled trains found connecting " + src + " to " + dst + "." + RESET);
            System.out.println("  " + CYAN + "Tip: Try finding the optimal connecting route in option [6]." + RESET);
        } else {
            System.out.println("\n  " + GREEN + "Found " + trains.size() + " train(s) serving route " + src + " -> " + dst + " (Sorted by Available Seats):" + RESET);
            System.out.println("  ┌────────┬─────────────────────────┬──────────────┬──────────────┬──────────────────┐");
            System.out.printf("  │ %-6s │ %-23s │ %-12s │ %-12s │ %-16s │\n", "Train#", "Name", "From", "To", "Seats Avail / WL");
            System.out.println("  ├────────┼─────────────────────────┼──────────────┼──────────────┼──────────────────┤");
            for (Train t : trains) {
                String seatStatus = t.getAvailableSeats() > 0 ?
                        (GREEN + t.getAvailableSeats() + " Seats" + RESET) :
                        (YELLOW + "WL (" + t.getWaitingListCount() + " in queue)" + RESET);
                System.out.printf("  │ %-6s │ %-23s │ %-12s │ %-12s │ %-25s │\n",
                        t.getId(), t.getName(), t.getSource().getId(), t.getDestination().getId(), seatStatus);
            }
            System.out.println("  └────────┴─────────────────────────┴──────────────┴──────────────┴──────────────────┘");
        }
        pause();
    }

    private void handleCheckAvailability() {
        System.out.println("\n" + YELLOW + "─── CHECK SEAT AVAILABILITY & FARE ───" + RESET);
        System.out.print("  Enter Train ID / Number (e.g., 12627, 12952): ");
        String trainId = scanner.nextLine().trim();

        Train train = trainService.getTrainById(trainId);
        if (train == null) {
            System.out.println("  " + RED + "Train '" + trainId + "' not found." + RESET);
            pause();
            return;
        }

        System.out.println("\n  " + BOLD + "Train Details:" + RESET);
        System.out.println("  Name: " + train.getName() + " [" + train.getId() + "]");
        System.out.println("  Route: " + train.getSource().getName() + " -> " + train.getDestination().getName());
        System.out.print("  Scheduled Stops: ");
        for (int i = 0; i < train.getRouteStops().size(); i++) {
            System.out.print(train.getRouteStops().get(i).getId());
            if (i < train.getRouteStops().size() - 1) System.out.print(" -> ");
        }
        System.out.println();
        System.out.println("  Total Train Capacity: " + train.getTotalSeats() + " seats (1A: 25, 2A: 50, 3A: 70, SL: 150, GN: 200)");
        if (train.getAvailableSeats() > 0) {
            System.out.println("  Status: " + GREEN + BOLD + "AVAILABLE (" + train.getAvailableSeats() + " seats remaining)" + RESET);
        } else {
            System.out.println("  Status: " + YELLOW + BOLD + "WAITING LIST (Current WL Queue Size: " + train.getWaitingListCount() + ")" + RESET);
        }
        System.out.printf("  Base Fare Rate: ₹%.2f / km\n", train.getFarePerKm());
        pause();
    }

    private void handleBookTicket() {
        System.out.println("\n" + YELLOW + "─── BOOK A TRAIN TICKET ───" + RESET);
        System.out.print("  Enter Train ID / Number: ");
        String trainId = scanner.nextLine().trim();

        Train train = trainService.getTrainById(trainId);
        if (train == null) {
            System.out.println("  " + RED + "Train with ID '" + trainId + "' does not exist." + RESET);
            pause();
            return;
        }

        System.out.print("  Enter Source Station Code: ");
        String src = scanner.nextLine().trim().toUpperCase();
        System.out.print("  Enter Destination Station Code: ");
        String dst = scanner.nextLine().trim().toUpperCase();

        Station srcStation = networkService.getStation(src);
        Station dstStation = networkService.getStation(dst);

        if (srcStation == null || dstStation == null) {
            System.out.println("  " + RED + "Invalid station codes entered." + RESET);
            pause();
            return;
        }

        if (!train.servesRoute(srcStation, dstStation)) {
            System.out.println("  " + RED + "Train " + train.getName() + " does not operate between " + src + " and " + dst + "." + RESET);
            pause();
            return;
        }

        System.out.print("  Enter Number of Seats / Passengers (1-6) [default 1]: ");
        String seatCountInput = scanner.nextLine().trim();
        int seatCount = 1;
        if (!seatCountInput.isEmpty()) {
            try {
                seatCount = Math.max(1, Math.min(6, Integer.parseInt(seatCountInput)));
            } catch (NumberFormatException ignored) {}
        }

        List<Passenger> passengerList = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            System.out.println(CYAN + "  -- Passenger #" + i + (i == 1 ? " (Lead Passenger)" : "") + " --" + RESET);
            String defName = (i == 1 && loggedInUser != null) ? loggedInUser.getFullName() : "";
            System.out.print("    Full Name" + (!defName.isEmpty() ? " [" + defName + "]" : "") + ": ");
            String pName = scanner.nextLine().trim();
            if (pName.isEmpty()) pName = !defName.isEmpty() ? defName : "Passenger " + i;

            System.out.print("    Age [default 25]: ");
            String aStr = scanner.nextLine().trim();
            int pAge = 25;
            if (!aStr.isEmpty()) {
                try { pAge = Math.max(1, Math.min(120, Integer.parseInt(aStr))); } catch (Exception ignored) {}
            }

            System.out.print("    Gender (M/F/O) [default M]: ");
            String pGen = scanner.nextLine().trim().toUpperCase();
            if (pGen.isEmpty()) pGen = "M";

            passengerList.add(new Passenger("GOV-" + (System.currentTimeMillis() + i) % 100000, pName, pAge, pGen));
        }

        System.out.println("  Select Travel Class:");
        System.out.println("    [1] 1st Class (1A)   - Luxury AC Coupe & Berths (25 Seats, 2.4x)");
        System.out.println("    [2] 2nd Class (2A)   - AC 2-Tier Sleeper (50 Seats, 1.8x)");
        System.out.println("    [3] 3 Tier AC (3A)   - AC 3-Tier Comfort (70 Seats, 1.25x)");
        System.out.println("    [4] Sleeper (SL)     - Standard Non-AC Sleeper (150 Seats, 0.65x)");
        System.out.println("    [5] General (GN)     - Second Sitting Unreserved (200 Seats, 0.35x)");
        System.out.print("  Enter Travel Class [1-5, default 3]: ");
        String classChoice = scanner.nextLine().trim();
        String travelClass = "3 Tier AC";
        if ("1".equals(classChoice)) travelClass = "1st Class";
        else if ("2".equals(classChoice)) travelClass = "2nd Class";
        else if ("3".equals(classChoice)) travelClass = "3 Tier AC";
        else if ("4".equals(classChoice)) travelClass = "Sleeper";
        else if ("5".equals(classChoice)) travelClass = "General";

        try {
            String bookedBy = loggedInUser != null ? loggedInUser.getUsername() : null;
            Reservation res = reservationService.bookTickets(train.getId(), passengerList, src, dst, travelClass, bookedBy, "GENERAL");
            System.out.println("\n" + GREEN + BOLD + "╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║                   TICKET BOOKING SUCCESSFUL!                   ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝" + RESET);
            System.out.println("  PNR Number:       " + BOLD + CYAN + res.getPnr() + RESET);
            if (res.isGeneralClass()) {
                System.out.println("  Booking Category: " + BOLD + CYAN + "UNRESERVED (Open Seating - Coach GS1, No Reserved Seats)" + RESET);
                System.out.println("  Passengers:       " + res.getPassengersDisplay() + " (" + res.getSeatCount() + " Passenger" + (res.getSeatCount() > 1 ? "s" : "") + ")");
            } else {
                System.out.println("  Seats Booked:     " + BOLD + YELLOW + res.getSeatCount() + " Seat" + (res.getSeatCount() > 1 ? "s" : "") + RESET + " (" + res.getSeatNumbersDisplay() + ")");
                System.out.println("  Passengers:       " + res.getPassengersDisplay());
            }
            System.out.println("  Train:            " + res.getTrainName() + " (" + res.getTrainId() + ")");
            System.out.println("  Travel Class:     " + BOLD + YELLOW + res.getTravelClass() + (res.isGeneralClass() ? " (Unreserved)" : "") + RESET);
            if (res.getBookedByUsername() != null) {
                System.out.println("  Linked Account:   " + GREEN + "@" + res.getBookedByUsername() + RESET);
            }
            System.out.println("  Route:            " + res.getSourceStation().getName() + " -> " + res.getDestinationStation().getName());
            if (res.getStatus() == BookingStatus.CONFIRMED) {
                if (res.isGeneralClass()) {
                    System.out.println("  Status:           " + GREEN + BOLD + "CONFIRMED (Unreserved Open Seating - Coach GS1)" + RESET);
                } else {
                    System.out.println("  Status:           " + GREEN + BOLD + "CONFIRMED (" + res.getSeatNumbersDisplay() + ")" + RESET);
                }
            } else if (res.getStatus() == BookingStatus.RAC) {
                System.out.println("  Status:           " + YELLOW + BOLD + "RAC (Position: RAC-" + res.getRacNumber() + ")" + RESET);
            } else {
                System.out.println("  Status:           " + YELLOW + BOLD + "WAITING LIST (Position: WL-" + res.getWaitingListNumber() + ")" + RESET);
                System.out.println("  " + CYAN + "Note: If a confirmed ticket is cancelled, this ticket will be automatically confirmed in FIFO order!" + RESET);
            }
            System.out.printf("  Total Fare Paid:  ₹%.2f (%d Seat%s)\n", res.getFare(), res.getSeatCount(), res.getSeatCount() > 1 ? "s" : "");
            System.out.println("  Booking Time:     " + res.getFormattedBookingTime());
            System.out.println("──────────────────────────────────────────────────────────────────");
        } catch (Exception e) {
            System.out.println("  " + RED + "Booking failed: " + e.getMessage() + RESET);
        }
        pause();
    }

    private void handleMyBookings() {
        if (loggedInUser == null) {
            System.out.println("\n" + YELLOW + "  You are currently in Guest mode. Please sign in to view your personal booking history." + RESET);
            System.out.print("  Would you like to sign in now? (Y/N): ");
            String ans = scanner.nextLine().trim();
            if (ans.equalsIgnoreCase("y")) {
                handleUserAccount();
                if (loggedInUser == null) return;
            } else {
                return;
            }
        }

        System.out.println("\n" + CYAN + BOLD + "─── MY BOOKING HISTORY (@" + loggedInUser.getUsername() + ") ───" + RESET);
        List<Reservation> bookings = reservationService.getReservationsByUsername(loggedInUser.getUsername());
        if (bookings.isEmpty()) {
            System.out.println("  No reservations found under account @" + loggedInUser.getUsername() + ".");
        } else {
            System.out.println("  Found " + bookings.size() + " reservation(s):");
            for (Reservation r : bookings) {
                System.out.println("  ────────────────────────────────────────────────────────────────");
                System.out.println("  PNR: " + BOLD + CYAN + r.getPnr() + RESET + " | Train: " + r.getTrainName() + " (" + r.getTrainId() + ")");
                System.out.println("  Passenger: " + r.getPassenger().getName() + " | Class: " + YELLOW + r.getTravelClass() + RESET);
                System.out.println("  Journey: " + r.getSourceStation().getId() + " ➔ " + r.getDestinationStation().getId() + " | Status: " + r.getStatusDisplay());
                System.out.printf("  Fare: ₹%.2f | Booked on: %s\n", r.getFare(), r.getFormattedBookingTime());
            }
        }
        pause();
    }

    private void handleUserAccount() {
        System.out.println("\n" + BLUE + BOLD + "─── PASSENGER AUTHENTICATION & ACCOUNT ───" + RESET);
        if (loggedInUser != null) {
            System.out.println("  Currently signed in as: " + GREEN + BOLD + loggedInUser.getFullName() + " (@" + loggedInUser.getUsername() + ")" + RESET);
            System.out.println("  Email: " + loggedInUser.getEmail() + " | Phone: " + loggedInUser.getPhone());
            System.out.println("  ────────────────────────────────────────────────────────────");
        }
        System.out.println("  [1] Sign In with Existing Account");
        System.out.println("  [2] Create New Passenger Account");
        System.out.println("  [3] Quick Demo Login: Aarav Sharma (pass123)");
        System.out.println("  [4] Quick Demo Login: Maharani Gayatri Devi (royal123)");
        System.out.println("  [5] Sign In with Google Account (1-Click Google OAuth)");
        if (loggedInUser != null) {
            System.out.println("  [6] Sign Out (Switch to Guest Mode)");
        }
        System.out.println("  [0] Cancel / Return");
        System.out.print("  Enter choice: ");

        int ch = readInt();
        switch (ch) {
            case 1:
                System.out.print("  Enter Username: ");
                String u = scanner.nextLine().trim();
                System.out.print("  Enter Password: ");
                String p = scanner.nextLine().trim();
                try {
                    var session = authService.login(u, p);
                    loggedInUser = session.getUser();
                    System.out.println("  " + GREEN + BOLD + "Welcome back, " + loggedInUser.getFullName() + "!" + RESET);
                } catch (Exception e) {
                    System.out.println("  " + RED + "Login Failed: " + e.getMessage() + RESET);
                }
                break;
            case 2:
                System.out.print("  Choose Username: ");
                String newU = scanner.nextLine().trim();
                System.out.print("  Choose Password (min 4 chars): ");
                String newP = scanner.nextLine().trim();
                System.out.print("  Full Name: ");
                String fn = scanner.nextLine().trim();
                System.out.print("  Email: ");
                String em = scanner.nextLine().trim();
                System.out.print("  Phone: ");
                String ph = scanner.nextLine().trim();
                try {
                    User reg = authService.register(newU, newP, fn, em, ph, UserRole.PASSENGER);
                    var session = authService.login(newU, newP);
                    loggedInUser = session.getUser();
                    System.out.println("  " + GREEN + BOLD + "Account created! Signed in as " + loggedInUser.getFullName() + "." + RESET);
                } catch (Exception e) {
                    System.out.println("  " + RED + "Registration Failed: " + e.getMessage() + RESET);
                }
                break;
            case 3:
                try {
                    var session = authService.login("passenger", "pass123");
                    loggedInUser = session.getUser();
                    System.out.println("  " + GREEN + BOLD + "Logged in as Demo Passenger: Aarav Sharma!" + RESET);
                } catch (Exception e) {
                    System.out.println("  " + RED + e.getMessage() + RESET);
                }
                break;
            case 4:
                try {
                    var session = authService.login("royal", "royal123");
                    loggedInUser = session.getUser();
                    System.out.println("  " + GREEN + BOLD + "Logged in as Royal VIP: Maharani Gayatri Devi!" + RESET);
                } catch (Exception e) {
                    System.out.println("  " + RED + e.getMessage() + RESET);
                }
                break;
            case 5:
                System.out.println("  Choose Google Profile:");
                System.out.println("    [1] Kushal Sindhe (kushal.sindhe@gmail.com)");
                System.out.println("    [2] Aarav Sharma (aarav.sharma@gmail.com)");
                System.out.println("    [3] Priya Patel (priya.patel@gmail.com)");
                System.out.println("    [4] Enter Custom Google Account");
                System.out.print("    Enter choice: ");
                int gChoice = readInt();
                String gEmail = "kushal.sindhe@gmail.com";
                String gName = "Kushal Sindhe";
                if (gChoice == 2) {
                    gEmail = "aarav.sharma@gmail.com";
                    gName = "Aarav Sharma";
                } else if (gChoice == 3) {
                    gEmail = "priya.patel@gmail.com";
                    gName = "Priya Patel";
                } else if (gChoice == 4) {
                    System.out.print("    Enter Google Email: ");
                    gEmail = scanner.nextLine().trim();
                    System.out.print("    Enter Full Name: ");
                    gName = scanner.nextLine().trim();
                }
                try {
                    var session = authService.loginWithGoogle(gEmail, gName, "g-" + UUID.randomUUID(), "");
                    loggedInUser = session.getUser();
                    System.out.println("  " + GREEN + BOLD + "Google Sign-In Successful! Signed in as " + loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")." + RESET);
                } catch (Exception e) {
                    System.out.println("  " + RED + "Google Sign-In Failed: " + e.getMessage() + RESET);
                }
                break;
            case 6:
                loggedInUser = null;
                System.out.println("  " + YELLOW + "Signed out. Now in Guest mode." + RESET);
                break;
            default:
                break;
        }
        pause();
    }

    private void handleCancelTicket() {
        System.out.println("\n" + YELLOW + "─── CANCEL A TICKET ───" + RESET);
        System.out.print("  Enter PNR to Cancel: ");
        String pnr = scanner.nextLine().trim().toUpperCase();

        CancellationResult result = reservationService.cancelTicket(pnr);
        if (!result.isSuccess()) {
            System.out.println("  " + RED + "Cancellation Failed: " + result.message() + RESET);
        } else {
            System.out.println("\n  " + GREEN + BOLD + "Cancellation Completed!" + RESET);
            System.out.println("  " + result.message());

            if (result.getPromotedReservation() != null) {
                Reservation promoted = result.getPromotedReservation();
                System.out.println("\n  " + PURPLE + BOLD +
                        "★ FIFO QUEUE DEMONSTRATION ★\n" +
                        "  Waiting List Passenger Auto-Promoted:\n" +
                        "  PNR: " + promoted.getPnr() + " | Passenger: " + promoted.getPassenger().getName() +
                        "\n  New Status: CONFIRMED | Allocated Seat: #" + promoted.getSeatNumber() + RESET);
            }
        }
        pause();
    }

    private void handleCheckPnr() {
        System.out.println("\n" + YELLOW + "─── CHECK PNR STATUS ───" + RESET);
        System.out.print("  Enter PNR Number (e.g., PNR-100101): ");
        String pnr = scanner.nextLine().trim().toUpperCase();

        Reservation res = reservationService.getReservation(pnr);
        if (res == null) {
            System.out.println("  " + RED + "No reservation found with PNR: " + pnr + RESET);
        } else {
            System.out.println("\n  " + BOLD + "RESERVATION DETAILS:" + RESET);
            System.out.println("  PNR:           " + CYAN + BOLD + res.getPnr() + RESET);
            System.out.println("  Passenger:     " + res.getPassenger());
            System.out.println("  Train:         " + res.getTrainName() + " (" + res.getTrainId() + ")");
            System.out.println("  Journey:       " + res.getSourceStation().getName() + " -> " + res.getDestinationStation().getName());
            System.out.print("  Current Status:");
            if (res.getStatus() == BookingStatus.CONFIRMED) {
                System.out.println(" " + GREEN + BOLD + "CONFIRMED (Seat #" + res.getSeatNumber() + ")" + RESET);
            } else if (res.getStatus() == BookingStatus.WAITING_LIST) {
                System.out.println(" " + YELLOW + BOLD + "WAITING LIST (Position: WL-" + res.getWaitingListNumber() + ")" + RESET);
            } else {
                System.out.println(" " + RED + BOLD + "CANCELLED" + RESET);
            }
            System.out.printf("  Fare Paid:     ₹%.2f\n", res.getFare());
            System.out.println("  Booked On:     " + res.getFormattedBookingTime());
        }
        pause();
    }

    private void handleFindRoute() {
        System.out.println("\n" + PURPLE + BOLD + "─── GRAPH SHORTEST ROUTE FINDER (DIJKSTRA'S ALGORITHM) ───" + RESET);
        System.out.println("  Finds the optimal path and minimum track distance between any two stations in the graph.");
        System.out.print("  Enter Origin Station Code: ");
        String src = scanner.nextLine().trim().toUpperCase();
        System.out.print("  Enter Destination Station Code: ");
        String dst = scanner.nextLine().trim().toUpperCase();

        if (!networkService.hasStation(src) || !networkService.hasStation(dst)) {
            System.out.println("  " + RED + "One or both stations do not exist in the railway network." + RESET);
            pause();
            return;
        }

        DijkstraResult<Station> result = networkService.findShortestRoute(src, dst);
        if (!result.isReachable()) {
            System.out.println("  " + RED + "No connected railway route exists between " + src + " and " + dst + "." + RESET);
        } else {
            System.out.println("\n  " + GREEN + BOLD + "Shortest Railway Route Discovered:" + RESET);
            List<Station> path = result.getPath();
            System.out.print("  Route: ");
            for (int i = 0; i < path.size(); i++) {
                System.out.print(CYAN + BOLD + path.get(i).getId() + RESET + " (" + path.get(i).getName() + ")");
                if (i < path.size() - 1) {
                    System.out.print(" ──▶ ");
                }
            }
            System.out.println();
            System.out.printf("  Total Track Distance: %s%.1f km%s\n", BOLD + GREEN, result.getTotalDistance(), RESET);
            System.out.println("  Number of Junctions: " + path.size());
        }
        pause();
    }

    private void handleViewStations() {
        System.out.println("\n" + YELLOW + "─── RAILWAY STATIONS DIRECTORY ───" + RESET);
        List<Station> stations = networkService.getAllStations();
        System.out.println("  ┌──────┬────────────────────────────────┐");
        System.out.printf("  │ %-4s │ %-30s │\n", "Code", "Station Name");
        System.out.println("  ├──────┼────────────────────────────────┤");
        for (Station s : stations) {
            System.out.printf("  │ %-4s │ %-30s │\n", s.getId(), s.getName());
        }
        System.out.println("  └──────┴────────────────────────────────┘");
        System.out.println("  Total Stations: " + stations.size());
        pause();
    }

    private int readInt() {
        try {
            String s = scanner.nextLine().trim();
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
