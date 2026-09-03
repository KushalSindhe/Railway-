package com.railway;

import com.railway.model.*;
import com.railway.service.*;
import java.util.*;

public class GeneralUnreservedTest {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RUNNING GENERAL UNRESERVED SEATING TEST SUITE   ");
        System.out.println("==================================================");

        RailwayNetworkService net = new RailwayNetworkService();
        TrainService trainService = new TrainService(net);
        ReservationService resService = new ReservationService(net, trainService);

        SampleDataLoader.loadSampleData(net, trainService, resService);

        trainService.addTrain("GEN-TEST", "General Test Express", "NDLS", "CSMT",
                Arrays.asList("NDLS", "CSMT"), 10, 1.50, "06:00", "14:00", "8h 00m", 5, 5);
        Train train = trainService.getTrainDirect("GEN-TEST");

        int initialAvailable = train.getAvailableSeats();

        // 1. Book a General class ticket
        System.out.println("\n[Test 1] Booking General (Unreserved) class ticket...");
        Reservation genRes = resService.bookTicket("GEN-TEST", "Rahul Sharma", 29, "M", "ID-101",
                "NDLS", "CSMT", "General", "rahul", "GENERAL");

        assert genRes.isGeneralClass() : "Ticket must be identified as General class";
        assert genRes.getStatus() == BookingStatus.CONFIRMED : "General ticket must be confirmed";
        assert genRes.getSeatNumber() == 0 : "General ticket must have seatNumber = 0 (no reserved seat assigned)";
        assert genRes.getSeatNumbers().isEmpty() : "General ticket must have empty reserved seat numbers list";
        assert genRes.getSeatNumbersDisplay().contains("Unreserved") : "Seat display must indicate Unreserved seating: " + genRes.getSeatNumbersDisplay();
        assert genRes.getStatusDisplay().contains("Unreserved Open Seating") : "Status display must indicate Unreserved: " + genRes.getStatusDisplay();
        assert train.getAvailableSeats() == initialAvailable : "Train reserved seats count must not be reduced by General unreserved booking! Expected: " + initialAvailable + ", Got: " + train.getAvailableSeats();
        System.out.println("  [PASS] General booking allocated no reserved seats: " + genRes.getSeatNumbersDisplay());
        System.out.println("  [PASS] Train reserved seats remained untouched: " + train.getAvailableSeats() + "/" + train.getTotalSeats());

        // 2. Book a multi-passenger General class ticket (3 passengers)
        System.out.println("\n[Test 2] Booking Multi-Passenger General class ticket (3 passengers)...");
        List<Passenger> group = Arrays.asList(
                new Passenger("ID-P1", "Aarav Gupta", 32, "M"),
                new Passenger("ID-P2", "Ananya Gupta", 28, "F"),
                new Passenger("ID-P3", "Rohan Gupta", 6, "M")
        );
        Reservation multiGen = resService.bookTickets("GEN-TEST", group, "NDLS", "CSMT", "General", "rahul", "GENERAL");

        assert multiGen.isGeneralClass() : "Multi-passenger ticket must be General class";
        assert multiGen.getSeatCount() == 3 : "Seat count must be 3";
        assert multiGen.getSeatNumbers().isEmpty() : "No reserved seats should be assigned";
        assert multiGen.getSeatNumbersDisplay().contains("Unreserved") : "Display must state Unreserved: " + multiGen.getSeatNumbersDisplay();
        assert train.getAvailableSeats() == initialAvailable : "Train reserved seats must remain untouched by multi General booking";
        System.out.println("  [PASS] 3-Passenger General ticket booked without reserved seats: " + multiGen.getSeatNumbersDisplay());

        // 3. Book a 3 Tier AC ticket and verify it DOES allocate reserved seats
        System.out.println("\n[Test 3] Booking 3 Tier AC ticket (verifying reserved seats are still allocated for AC)...");
        Reservation acRes = resService.bookTicket("GEN-TEST", "Vikram Malhotra", 45, "M", "ID-201",
                "NDLS", "CSMT", "3 Tier AC", "vikram", "GENERAL");

        assert !acRes.isGeneralClass() : "3 Tier AC ticket must NOT be General class";
        assert acRes.getSeatNumber() == 1 : "3 Tier AC ticket must allocate Seat #1";
        assert acRes.getSeatNumbers().contains(1) : "Seat list must contain seat 1";
        assert acRes.getSeatNumbersDisplay().equals("Seat #1") : "Seat display must be Seat #1";
        assert train.getAvailableSeats() == initialAvailable - 1 : "Train available seats must decrease by 1 for reserved class";
        System.out.println("  [PASS] AC Class booked with reserved seat: " + acRes.getSeatNumbersDisplay());
        System.out.println("  [PASS] Train available reserved seats: " + train.getAvailableSeats() + "/" + train.getTotalSeats());

        // 4. Cancel General ticket and verify 20% refund without releasing numbered seats
        System.out.println("\n[Test 4] Cancelling General ticket...");
        ReservationService.CancellationResult cancelRes = resService.cancelTicket(genRes.getPnr());
        assert cancelRes.isSuccess() : "Cancellation must succeed";
        assert cancelRes.getRefundAmount() > 0 : "Refund amount must be > 0";
        assert cancelRes.message().contains("Unreserved") : "Cancel message should indicate Unreserved General ticket: " + cancelRes.message();
        assert train.getAvailableSeats() == initialAvailable - 1 : "Reserved seats count should remain unchanged upon General cancel";
        System.out.println("  [PASS] General ticket cancelled with 20% refund: " + cancelRes.message());

        System.out.println("\n==================================================");
        System.out.println("  ALL 4 GENERAL UNRESERVED TESTS PASSED 100%!     ");
        System.out.println("==================================================");
    }
}
