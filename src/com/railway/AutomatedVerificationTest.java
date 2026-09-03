package com.railway;

import com.railway.dsa.*;
import com.railway.model.*;
import com.railway.service.*;

import java.util.*;

/**
 * Programmatic verification suite testing Graph Dijkstra, Waiting List FIFO Queue,
 * Auto-Promotion on Cancellation, MergeSort, QuickSort, and Binary Search.
 */
public class AutomatedVerificationTest {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("     RUNNING AUTOMATED VERIFICATION FOR RAILWAY SYSTEM            ");
        System.out.println("==================================================================");

        int totalTests = 0;
        int passedTests = 0;

        // --- TEST 1: Graph Adjacency List & Dijkstra's Algorithm ---
        totalTests++;
        System.out.print("[TEST 1] Graph Dijkstra Shortest Route Calculation: ");
        RailwayNetworkService netService = new RailwayNetworkService();
        netService.addStation("NDLS", "New Delhi");
        netService.addStation("BPL", "Bhopal");
        netService.addStation("NGP", "Nagpur");
        netService.addStation("HYB", "Hyderabad");
        netService.addStation("SBC", "Bengaluru");

        netService.addTrack("NDLS", "BPL", 700.0);
        netService.addTrack("BPL", "NGP", 390.0);
        netService.addTrack("NGP", "HYB", 500.0);
        netService.addTrack("HYB", "SBC", 620.0);

        DijkstraResult<Station> dijkstra = netService.findShortestRoute("NDLS", "SBC");
        if (dijkstra.isReachable() && Math.abs(dijkstra.getTotalDistance() - 2210.0) < 0.01 && dijkstra.getPath().size() == 5) {
            System.out.println("PASSED! (Path: " + dijkstra + ")");
            passedTests++;
        } else {
            System.out.println("FAILED! Distance: " + dijkstra.getTotalDistance());
        }

        // --- TEST 2: Custom FIFO Queue ADT ---
        totalTests++;
        System.out.print("[TEST 2] Custom Linked-Node Queue FIFO Ordering: ");
        CustomQueue<String> queue = new CustomQueue<>();
        queue.enqueue("First");
        queue.enqueue("Second");
        queue.enqueue("Third");

        boolean qPass = queue.size() == 3 &&
                "First".equals(queue.dequeue()) &&
                "Second".equals(queue.dequeue()) &&
                "Third".equals(queue.dequeue()) &&
                queue.isEmpty();
        if (qPass) {
            System.out.println("PASSED! (FIFO property strictly verified)");
            passedTests++;
        } else {
            System.out.println("FAILED!");
        }

        // --- TEST 3: Booking, Waiting List & Auto-Promotion on Cancellation ---
        totalTests++;
        System.out.print("[TEST 3] Waiting List Queue & Auto-Promotion upon Cancellation: ");
        TrainService trainService = new TrainService(netService);
        ReservationService resService = new ReservationService(netService, trainService);

        // Train with capacity of exactly 2 seats
        trainService.addTrain("TEST-101", "Express One", "NDLS", "SBC", Arrays.asList("NDLS", "SBC"), 2, 1.0);

        // Booking 1: Confirmed (Seat 1)
        Reservation r1 = resService.bookTicket("TEST-101", "Alice", 30, "F", "P1", "NDLS", "SBC");
        // Booking 2: Confirmed (Seat 2)
        Reservation r2 = resService.bookTicket("TEST-101", "Bob", 35, "M", "P2", "NDLS", "SBC");
        // Booking 3: Exceeds capacity -> Must be Waiting List WL-1
        Reservation r3 = resService.bookTicket("TEST-101", "Charlie", 28, "M", "P3", "NDLS", "SBC");
        // Booking 4: Waiting List WL-2
        Reservation r4 = resService.bookTicket("TEST-101", "Diana", 26, "F", "P4", "NDLS", "SBC");

        boolean bookingChecks = (r1.getStatus() == BookingStatus.CONFIRMED && r1.getSeatNumber() == 1) &&
                               (r2.getStatus() == BookingStatus.CONFIRMED && r2.getSeatNumber() == 2) &&
                               (r3.getStatus() == BookingStatus.WAITING_LIST && r3.getWaitingListNumber() == 1) &&
                               (r4.getStatus() == BookingStatus.WAITING_LIST && r4.getWaitingListNumber() == 2);

        // Now cancel Alice's confirmed ticket (r1).
        // This MUST release Seat 1 and automatically dequeue and promote Charlie (r3) to CONFIRMED with Seat 1!
        ReservationService.CancellationResult cancelRes = resService.cancelTicket(r1.getPnr());

        boolean promoChecks = cancelRes.isSuccess() &&
                              r1.getStatus() == BookingStatus.CANCELLED &&
                              r3.getStatus() == BookingStatus.CONFIRMED &&
                              r3.getSeatNumber() == 1 &&
                              r3.getWaitingListNumber() == 0 &&
                              r4.getWaitingListNumber() == 1; // Diana moved to WL-1

        if (bookingChecks && promoChecks) {
            System.out.println("PASSED! (Auto-promoted " + r3.getPassenger().getName() + " to Seat #" + r3.getSeatNumber() + ")");
            passedTests++;
        } else {
            System.out.println("FAILED! Booking checks: " + bookingChecks + ", Promo checks: " + promoChecks);
        }

        // --- TEST 4: Binary Search Algorithm ---
        totalTests++;
        System.out.print("[TEST 4] Binary Search in Sorted Fleet: ");
        List<Train> fleet = new ArrayList<>();
        fleet.add(new Train("1001", "T1", netService.getStation("NDLS"), netService.getStation("SBC"), null, 10, 1.0));
        fleet.add(new Train("1005", "T2", netService.getStation("NDLS"), netService.getStation("SBC"), null, 10, 1.0));
        fleet.add(new Train("1009", "T3", netService.getStation("NDLS"), netService.getStation("SBC"), null, 10, 1.0));
        fleet.add(new Train("1015", "T4", netService.getStation("NDLS"), netService.getStation("SBC"), null, 10, 1.0));

        int foundIdx = SearchingAlgorithms.binarySearch(fleet, "1009", Train::getId);
        int notFoundIdx = SearchingAlgorithms.binarySearch(fleet, "9999", Train::getId);

        if (foundIdx == 2 && notFoundIdx == -1) {
            System.out.println("PASSED! (Found key at index " + foundIdx + " in O(log N))");
            passedTests++;
        } else {
            System.out.println("FAILED! Found index: " + foundIdx);
        }

        // --- TEST 5: Custom MergeSort Algorithm ---
        totalTests++;
        System.out.print("[TEST 5] Custom MergeSort Algorithm: ");
        List<Integer> numbers = new ArrayList<>(Arrays.asList(45, 12, 89, 2, 67, 33, 1));
        SortingAlgorithms.mergeSort(numbers, Integer::compareTo);
        boolean sortPass = numbers.equals(Arrays.asList(1, 2, 12, 33, 45, 67, 89));
        if (sortPass) {
            System.out.println("PASSED! Sorted array: " + numbers);
            passedTests++;
        } else {
            System.out.println("FAILED! Output: " + numbers);
        }

        // --- TEST 6: Custom QuickSort Algorithm ---
        totalTests++;
        System.out.print("[TEST 6] Custom QuickSort Algorithm: ");
        List<String> names = new ArrayList<>(Arrays.asList("SBC", "NDLS", "CSMT", "MAS", "HWH", "ADI"));
        SortingAlgorithms.quickSort(names, String::compareTo);
        boolean qSortPass = names.equals(Arrays.asList("ADI", "CSMT", "HWH", "MAS", "NDLS", "SBC"));
        if (qSortPass) {
            System.out.println("PASSED! Sorted strings: " + names);
            passedTests++;
        } else {
            System.out.println("FAILED! Output: " + names);
        }

        System.out.println("==================================================================");
        System.out.printf("  TOTAL VERIFICATION SCORE: %d / %d TESTS PASSED (%.1f%%)\n",
                passedTests, totalTests, (passedTests * 100.0 / totalTests));
        System.out.println("==================================================================");

        if (passedTests == totalTests) {
            System.out.println(">>> ALL ALGORITHMS AND BUSINESS RULES FULLY VALIDATED! <<<");
        }
    }
}
