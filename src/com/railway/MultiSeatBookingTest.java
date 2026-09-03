package com.railway;

import com.railway.model.*;
import com.railway.service.*;
import java.util.ArrayList;
import java.util.List;

public class MultiSeatBookingTest {
    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  BHARAT RAILWAYS: MULTI-SEAT BOOKING VERIFICATION TEST SUITE  ");
        System.out.println("===============================================================");

        RailwayNetworkService networkService = new RailwayNetworkService();
        TrainService trainService = new TrainService(networkService);
        ReservationService resService = new ReservationService(networkService, trainService);

        SampleDataLoader.loadSampleData(networkService, trainService, resService);

        trainService.addTrain("TEST-100", "Multi-Seat Express", "NDLS", "CSMT",
                java.util.Arrays.asList("NDLS", "CSMT"), 50, 1.50, "06:00", "14:00", "8h 00m", 10, 10);
        Train train = trainService.getTrainDirect("TEST-100");
        System.out.println("Using Test Train: " + train.getName() + " (" + train.getId() + ")");
        System.out.println("Initial Available Seats: " + train.getAvailableSeats());

        // Test 1: Single seat booking via bookTicket()
        System.out.println("\n[Test 1] Booking Single Seat (Default / Backwards Compatibility)...");
        Reservation r1 = resService.bookTicket(train.getId(), "Aarav Sharma", 30, "M", "ID-01",
                train.getSource().getId(), train.getDestination().getId(), "3 Tier AC", "kushal");
        assert r1.getSeatCount() == 1 : "Expected seatCount 1, got " + r1.getSeatCount();
        assert r1.getSeatNumbers().size() == 1 : "Expected 1 seat allocated";
        assert r1.getPassengers().size() == 1 : "Expected 1 passenger";
        assert "Aarav Sharma".equals(r1.getPassenger().getName());
        assert r1.getStatus() == BookingStatus.CONFIRMED;
        System.out.printf("  ✓ PNR: %s | Seats: %s | Fare: ₹%.2f | Status: %s\n",
                r1.getPnr(), r1.getSeatNumbersDisplay(), r1.getFare(), r1.getStatusDisplay());

        // Test 2: Multi-Seat Booking (3 Seats) with passenger manifest
        System.out.println("\n[Test 2] Booking 3 Seats with Passenger Manifest...");
        List<Passenger> group3 = new ArrayList<>();
        group3.add(new Passenger("ID-P1", "Vikramaditya Roy", 35, "M"));
        group3.add(new Passenger("ID-P2", "Ananya Roy", 32, "F"));
        group3.add(new Passenger("ID-P3", "Ishaan Roy", 7, "M"));

        int seatsBefore3 = train.getAvailableSeats();
        Reservation r3 = resService.bookTickets(train.getId(), group3,
                train.getSource().getId(), train.getDestination().getId(),
                "3 Tier AC", "kushal", "GENERAL");

        assert r3.getSeatCount() == 3 : "Expected seatCount 3, got " + r3.getSeatCount();
        assert r3.getSeatNumbers().size() == 3 : "Expected 3 distinct seats allocated";
        assert r3.getPassengers().size() == 3 : "Expected 3 passengers in manifest";
        assert r3.getStatus() == BookingStatus.CONFIRMED;
        assert train.getAvailableSeats() == seatsBefore3 - 3 : "Available seats should decrease by 3";

        // Check that seats are distinct
        assert !r3.getSeatNumbers().get(0).equals(r3.getSeatNumbers().get(1));
        assert !r3.getSeatNumbers().get(1).equals(r3.getSeatNumbers().get(2));

        // Total fare should be 3 * singleFare
        double singleFare = r1.getFare();
        double expectedFare = singleFare * 3;
        assert Math.abs(r3.getFare() - expectedFare) < 0.05 : "Expected total fare " + expectedFare + " but got " + r3.getFare();

        System.out.printf("  ✓ PNR: %s | Allocated: %s | Passengers: %s\n",
                r3.getPnr(), r3.getSeatNumbersDisplay(), r3.getPassengersDisplay());
        System.out.printf("  ✓ Total Fare: ₹%.2f (Single: ₹%.2f x 3 Seats)\n", r3.getFare(), singleFare);

        // Test 3: Emergency Quota Multi-Seat Booking (2 Seats = 2x Fare per seat)
        System.out.println("\n[Test 3] Booking 2 Seats under Emergency Quota (2x Fare)...");
        List<Passenger> groupEmg = new ArrayList<>();
        groupEmg.add(new Passenger("ID-E1", "Dr. Rajesh Gupta", 48, "M"));
        groupEmg.add(new Passenger("ID-E2", "Sunita Gupta", 45, "F"));

        Reservation rEmg = resService.bookTickets(train.getId(), groupEmg,
                train.getSource().getId(), train.getDestination().getId(),
                "3 Tier AC", "kushal", "EMERGENCY");

        assert rEmg.getSeatCount() == 2;
        assert "EMERGENCY".equals(rEmg.getQuota());
        double expectedEmgFare = singleFare * 2.0 * 2; // 2x rate * 2 seats
        assert Math.abs(rEmg.getFare() - expectedEmgFare) < 0.05 : "Emergency 2-seat fare mismatch";
        System.out.printf("  ✓ Emergency PNR: %s | Seats: %s | Quota: %s | Total Fare: ₹%.2f\n",
                rEmg.getPnr(), rEmg.getSeatNumbersDisplay(), rEmg.getQuota(), rEmg.getFare());

        // Test 4: Maximum Multi-Seat Booking (6 Seats)
        System.out.println("\n[Test 4] Booking Maximum Allowed Seats (6 Seats in single PNR)...");
        List<Passenger> group6 = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            group6.add(new Passenger("ID-G" + i, "Family Member " + i, 20 + i * 5, (i % 2 == 0 ? "F" : "M")));
        }
        Reservation r6 = resService.bookTickets(train.getId(), group6,
                train.getSource().getId(), train.getDestination().getId(),
                "3 Tier AC", "kushal", "GENERAL");

        assert r6.getSeatCount() == 6 : "Expected seatCount 6, got " + r6.getSeatCount();
        assert r6.getSeatNumbers().size() == 6 : "Expected 6 seats allocated";
        assert r6.getPassengers().size() == 6;
        System.out.printf("  ✓ PNR: %s | 6 Seats Allocated: %s | Fare: ₹%.2f\n",
                r6.getPnr(), r6.getSeatNumbersDisplay(), r6.getFare());

        // Test 5: Cancellation of Multi-Seat Ticket with 20% Refund and Seat Release
        System.out.println("\n[Test 5] Cancelling Multi-Seat Ticket (3 Seats) & Verifying 20% Refund & Inventory Release...");
        int availBeforeCancel = train.getAvailableSeats();
        ReservationService.CancellationResult cancelResult = resService.cancelTicket(r3.getPnr());

        assert cancelResult.isSuccess() : "Cancellation should succeed";
        double expectedRefund = r3.getFare() * 0.20;
        assert Math.abs(cancelResult.getRefundAmount() - expectedRefund) < 0.05 :
                "Expected 20% refund ₹" + expectedRefund + " but got ₹" + cancelResult.getRefundAmount();
        assert train.getAvailableSeats() == availBeforeCancel + 3 :
                "All 3 seats should be released back to inventory!";
        assert r3.getStatus() == BookingStatus.CANCELLED;

        System.out.printf("  ✓ Cancelled PNR: %s | Original Fare: ₹%.2f | 20%% Refund: ₹%.2f | Seats Released: 3\n",
                r3.getPnr(), r3.getFare(), cancelResult.getRefundAmount());
        System.out.printf("  ✓ Inventory successfully increased from %d to %d available seats\n",
                availBeforeCancel, train.getAvailableSeats());

        System.out.println("\n===============================================================");
        System.out.println("  ALL MULTI-SEAT BOOKING TESTS PASSED PERFECTLY! (5/5)        ");
        System.out.println("===============================================================");
    }
}
