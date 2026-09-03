package com.railway.ui;

import com.railway.dsa.CustomQueue;
import com.railway.dsa.Graph;
import com.railway.model.Reservation;
import com.railway.model.RouteEdge;
import com.railway.model.Station;
import com.railway.model.Train;
import com.railway.service.RailwayNetworkService;
import com.railway.service.ReservationService;
import com.railway.service.TrainService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import static com.railway.ui.ConsoleUI.*;

/**
 * Interactive Console menu for Reservation Clerk / Railway Administrator.
 */
public class AdminMenu {
    private final Scanner scanner;
    private final RailwayNetworkService networkService;
    private final TrainService trainService;
    private final ReservationService reservationService;

    public AdminMenu(Scanner scanner,
                     RailwayNetworkService networkService,
                     TrainService trainService,
                     ReservationService reservationService) {
        this.scanner = scanner;
        this.networkService = networkService;
        this.trainService = trainService;
        this.reservationService = reservationService;
    }

    public void show() {
        boolean inAdmin = true;
        while (inAdmin) {
            System.out.println("\n" + YELLOW + BOLD +
                    "╔════════════════════════════════════════════════════════════════╗\n" +
                    "║           RAILWAY CLERK & ADMINISTRATOR CONTROL PORTAL         ║\n" +
                    "╚════════════════════════════════════════════════════════════════╝" + RESET);
            System.out.println("  [1] Add New Railway Station (Enforces Unique Station ID)");
            System.out.println("  [2] Add New Track Connection (Graph Weighted Edge)");
            System.out.println("  [3] Add New Train to Fleet (Enforces Unique Train ID)");
            System.out.println("  [4] View All Trains (Demonstrates Custom MergeSort & QuickSort)");
            System.out.println("  [5] Inspect Waiting List FIFO Queue for a Train");
            System.out.println("  [6] View All Passenger Reservations / Chart");
            System.out.println("  [7] View Railway Network Graph Topology");
            System.out.println("  [0] Return to Main Menu");
            System.out.println("──────────────────────────────────────────────────────────────────");
            System.out.print("  Enter choice [0-7]: ");

            int choice = readInt();
            switch (choice) {
                case 1:
                    handleAddStation();
                    break;
                case 2:
                    handleAddTrack();
                    break;
                case 3:
                    handleAddTrain();
                    break;
                case 4:
                    handleViewTrains();
                    break;
                case 5:
                    handleInspectWaitingList();
                    break;
                case 6:
                    handleViewAllReservations();
                    break;
                case 7:
                    handleViewGraphTopology();
                    break;
                case 0:
                    inAdmin = false;
                    break;
                default:
                    System.out.println("  " + RED + "Invalid choice. Select between 0 and 7." + RESET);
                    pause();
            }
        }
    }

    private void handleAddStation() {
        System.out.println("\n" + CYAN + "─── ADD NEW STATION ───" + RESET);
        System.out.print("  Enter Unique Station Code (e.g., GKP, JAT, BSB): ");
        String id = scanner.nextLine().trim();
        System.out.print("  Enter Full Station Name: ");
        String name = scanner.nextLine().trim();

        try {
            Station station = networkService.addStation(id, name);
            System.out.println("  " + GREEN + "Station added successfully: " + station + RESET);
        } catch (Exception e) {
            System.out.println("  " + RED + "Error: " + e.getMessage() + RESET);
        }
        pause();
    }

    private void handleAddTrack() {
        System.out.println("\n" + CYAN + "─── ADD NEW RAILWAY TRACK (GRAPH EDGE) ───" + RESET);
        System.out.print("  Enter Source Station Code: ");
        String src = scanner.nextLine().trim();
        System.out.print("  Enter Destination Station Code: ");
        String dst = scanner.nextLine().trim();
        System.out.print("  Enter Track Distance in Kilometers: ");
        double dist = readDouble();

        try {
            RouteEdge edge = networkService.addTrack(src, dst, dist);
            System.out.println("  " + GREEN + "Track connection created: " + edge + RESET);
        } catch (Exception e) {
            System.out.println("  " + RED + "Error: " + e.getMessage() + RESET);
        }
        pause();
    }

    private void handleAddTrain() {
        System.out.println("\n" + CYAN + "─── ADD NEW TRAIN TO FLEET ───" + RESET);
        System.out.print("  Enter Unique Train ID / Number (e.g., 12001): ");
        String id = scanner.nextLine().trim();
        System.out.print("  Enter Train Name: ");
        String name = scanner.nextLine().trim();
        System.out.print("  Enter Source Station Code: ");
        String src = scanner.nextLine().trim();
        System.out.print("  Enter Destination Station Code: ");
        String dst = scanner.nextLine().trim();
        System.out.print("  Enter Intermediate Stops (comma-separated codes, or leave empty): ");
        String stopsLine = scanner.nextLine().trim();
        List<String> stops = new ArrayList<>();
        if (!stopsLine.isEmpty()) {
            for (String s : stopsLine.split(",")) {
                stops.add(s.trim().toUpperCase());
            }
        }
        System.out.print("  Enter Total Passenger Seat Capacity: ");
        int seats = readInt();
        System.out.print("  Enter Fare Rate per KM (e.g. 1.25): ");
        double fareRate = readDouble();

        try {
            Train train = trainService.addTrain(id, name, src, dst, stops, seats, fareRate);
            System.out.println("  " + GREEN + "Train added successfully: " + train.getName() + " [" + train.getId() + "]" + RESET);
        } catch (Exception e) {
            System.out.println("  " + RED + "Error: " + e.getMessage() + RESET);
        }
        pause();
    }

    private void handleViewTrains() {
        System.out.println("\n" + CYAN + "─── VIEW TRAINS FLEET (DSA SORTING) ───" + RESET);
        System.out.println("  Sort by:");
        System.out.println("  [1] Available Seats Descending (Custom MergeSort - O(N log N))");
        System.out.println("  [2] Train Number / ID Ascending (Custom QuickSort - O(N log N))");
        System.out.println("  [3] Train Name Alphabetical (Custom MergeSort - O(N log N))");
        System.out.print("  Choose sorting option [1-3]: ");
        int sortChoice = readInt();

        List<Train> trains;
        String sortDescription;
        if (sortChoice == 1) {
            trains = trainService.getAllTrainsSortedByAvailableSeats();
            sortDescription = "Sorted by Available Seats (MergeSort)";
        } else if (sortChoice == 2) {
            trains = trainService.getAllTrainsSortedById();
            sortDescription = "Sorted by Train Number (QuickSort)";
        } else if (sortChoice == 3) {
            trains = trainService.getAllTrainsSortedByName();
            sortDescription = "Sorted by Train Name (MergeSort)";
        } else {
            trains = trainService.getAllTrains();
            sortDescription = "Default Fleet Order";
        }

        System.out.println("\n  " + BOLD + "Train Fleet (" + sortDescription + "):" + RESET);
        System.out.println("  ┌────────┬─────────────────────────┬─────────┬─────────┬────────┬────────┬───────┐");
        System.out.printf("  │ %-6s │ %-23s │ %-7s │ %-7s │ %-6s │ %-6s │ %-5s │\n",
                "Train#", "Name", "From", "To", "Total", "Avail", "WL");
        System.out.println("  ├────────┼─────────────────────────┼─────────┼─────────┼────────┼────────┼───────┤");
        for (Train t : trains) {
            System.out.printf("  │ %-6s │ %-23s │ %-7s │ %-7s │ %-6d │ %-6d │ %-5d │\n",
                    t.getId(), t.getName(), t.getSource().getId(), t.getDestination().getId(),
                    t.getTotalSeats(), t.getAvailableSeats(), t.getWaitingListCount());
        }
        System.out.println("  └────────┴─────────────────────────┴─────────┴─────────┴────────┴────────┴───────┘");
        pause();
    }

    private void handleInspectWaitingList() {
        System.out.println("\n" + CYAN + "─── INSPECT WAITING LIST FIFO QUEUE ───" + RESET);
        System.out.print("  Enter Train ID: ");
        String trainId = scanner.nextLine().trim();

        Train train = trainService.getTrainById(trainId);
        if (train == null) {
            System.out.println("  " + RED + "Train '" + trainId + "' not found." + RESET);
            pause();
            return;
        }

        CustomQueue<Reservation> wlQueue = train.getWaitingList();
        System.out.println("\n  " + BOLD + "Waiting List Queue for Train " + train.getName() + " [" + train.getId() + "]:" + RESET);
        System.out.println("  Current Queue Depth: " + wlQueue.size() + " passenger(s)");

        if (wlQueue.isEmpty()) {
            System.out.println("  " + GREEN + "No passengers currently in waiting list. All bookings are confirmed!" + RESET);
        } else {
            System.out.println("  ┌──────┬────────────┬────────────────────────┬─────────┬─────────┐");
            System.out.printf("  │ %-4s │ %-10s │ %-22s │ %-7s │ %-7s │\n", "Pos", "PNR", "Passenger", "From", "To");
            System.out.println("  ├──────┼────────────┼────────────────────────┼─────────┼─────────┤");
            int pos = 1;
            for (Reservation r : wlQueue) {
                System.out.printf("  │ %-4d │ %-10s │ %-22s │ %-7s │ %-7s │\n",
                        pos++, r.getPnr(), r.getPassenger().getName(),
                        r.getSourceStation().getId(), r.getDestinationStation().getId());
            }
            System.out.println("  └──────┴────────────┴────────────────────────┴─────────┴─────────┘");
            System.out.println("  " + PURPLE + "Note: When a confirmed ticket is cancelled, Pos #1 will be dequeued and confirmed." + RESET);
        }
        pause();
    }

    private void handleViewAllReservations() {
        System.out.println("\n" + CYAN + "─── PASSENGER RESERVATION MANIFEST ───" + RESET);
        List<Reservation> reservations = reservationService.getAllReservations();
        if (reservations.isEmpty()) {
            System.out.println("  No reservations made yet.");
        } else {
            System.out.println("  ┌────────────┬──────────────────┬────────┬──────────────┬──────────────┬───────────────┬──────────┐");
            System.out.printf("  │ %-10s │ %-16s │ %-6s │ %-12s │ %-12s │ %-13s │ %-8s │\n",
                    "PNR", "Passenger", "Train#", "From", "To", "Status", "Fare (₹)");
            System.out.println("  ├────────────┼──────────────────┼────────┼──────────────┼──────────────┼───────────────┼──────────┤");
            for (Reservation r : reservations) {
                String statusStr = r.getStatusDisplay();
                System.out.printf("  │ %-10s │ %-16s │ %-6s │ %-12s │ %-12s │ %-13s │ %-8.2f │\n",
                        r.getPnr(), r.getPassenger().getName(), r.getTrainId(),
                        r.getSourceStation().getId(), r.getDestinationStation().getId(),
                        statusStr, r.getFare());
            }
            System.out.println("  └────────────┴──────────────────┴────────┴──────────────┴──────────────┴───────────────┴──────────┘");
            System.out.println("  Total Bookings in System: " + reservations.size());
        }
        pause();
    }

    private void handleViewGraphTopology() {
        System.out.println("\n" + CYAN + "─── RAILWAY NETWORK GRAPH TOPOLOGY ───" + RESET);
        Graph<Station> graph = networkService.getNetworkGraph();
        System.out.println("  Network Graph Statistics: " + graph.getVertexCount() + " Stations (Vertices), "
                + graph.getEdgeCount() + " Track Edges");
        System.out.println("\n  Adjacency List Representation:");
        for (Station station : graph.getVertices()) {
            System.out.print("  " + BOLD + station.getId() + RESET + " (" + station.getName() + ") ──▶ ");
            List<Graph.Edge<Station>> edges = graph.getNeighbors(station);
            if (edges.isEmpty()) {
                System.out.println("[Isolated]");
            } else {
                for (int i = 0; i < edges.size(); i++) {
                    Graph.Edge<Station> e = edges.get(i);
                    System.out.print(e.getDestination().getId() + " [" + e.getWeight() + " km]");
                    if (i < edges.size() - 1) System.out.print(", ");
                }
                System.out.println();
            }
        }
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

    private double readDouble() {
        try {
            String s = scanner.nextLine().trim();
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
}
