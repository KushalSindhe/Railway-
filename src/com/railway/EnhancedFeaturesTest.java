package com.railway;

import com.railway.model.*;
import com.railway.service.*;

public class EnhancedFeaturesTest {
    public static void main(String[] args) {
        System.out.println("Starting Enhanced Features Verification Test...");

        RailwayNetworkService networkService = new RailwayNetworkService();
        TrainService trainService = new TrainService(networkService);
        ReservationService resService = new ReservationService(networkService, trainService);

        SampleDataLoader.loadSampleData(networkService, trainService, resService);

        // 1. Verify Pan-India Stations
        System.out.println("1. Testing Pan-India Stations...");
        Station ndls = networkService.getStation("NDLS");
        Station csmT = networkService.getStation("CSMT");
        Station sbc = networkService.getStation("SBC");
        Station bsb = networkService.getStation("BSB");
        Station ghy = networkService.getStation("GHY");
        assert ndls != null && "Delhi".equals(ndls.getState());
        assert csmT != null && "Maharashtra".equals(csmT.getState());
        assert sbc != null && "Karnataka".equals(sbc.getState());
        assert bsb != null && "Uttar Pradesh".equals(bsb.getState());
        assert ghy != null && "Assam".equals(ghy.getState());
        System.out.println("  ✓ Pan-India stations verified across states!");

        // 2. Verify Train Timings
        System.out.println("2. Testing Train Timings & Duration...");
        Train rajdhani = trainService.getTrainDirect("12952");
        assert rajdhani != null;
        assert "16:55".equals(rajdhani.getDepartureTime());
        assert "08:35".equals(rajdhani.getArrivalTime());
        assert "15h 40m".equals(rajdhani.getTravelDuration());
        System.out.println("  ✓ Train timings verified: Dep " + rajdhani.getDepartureTime() + ", Arr " + rajdhani.getArrivalTime() + " (" + rajdhani.getTravelDuration() + ")");

        // 3. Verify Emergency Quota (2x Fare)
        System.out.println("3. Testing Emergency Quota (2x Fare)...");
        Reservation genTicket = resService.bookTicket("12952", "General Passenger", 28, "M", "GEN-01", "NDLS", "CSMT", "3 Tier AC", "demo", "GENERAL");
        Reservation emgTicket = resService.bookTicket("12952", "Emergency Passenger", 35, "F", "EMG-01", "NDLS", "CSMT", "3 Tier AC", "demo", "EMERGENCY");
        System.out.println("  General Fare: ₹" + genTicket.getFare() + " | Emergency Fare (2x): ₹" + emgTicket.getFare());
        assert Math.abs(emgTicket.getFare() - (genTicket.getFare() * 2.0)) < 0.05 : "Emergency fare should be double!";
        assert "EMERGENCY".equals(emgTicket.getQuota());
        System.out.println("  ✓ Emergency Quota 2x fare verified!");

        // 4. Verify RAC & Waiting List Allocation
        System.out.println("4. Testing Allocation Hierarchy (AVL -> RAC -> WL)...");
        trainService.addTrain("TEST-RAC", "RAC Test Express", "NDLS", "CSMT",
                java.util.Arrays.asList("NDLS", "CSMT"), 3, 1.85, "16:55", "08:35", "15h 40m", 2, 2);

        Reservation cnf1 = resService.bookTicket("TEST-RAC", "First CNF Passenger", 28, "M", "CNF-01", "NDLS", "CSMT", "3 Tier AC", "demo", "GENERAL");
        Reservation cnf2 = resService.bookTicket("TEST-RAC", "Second CNF Passenger", 35, "F", "CNF-02", "NDLS", "CSMT", "3 Tier AC", "demo", "GENERAL");
        Reservation cnf3 = resService.bookTicket("TEST-RAC", "Third CNF Passenger", 40, "M", "CNF-03", "NDLS", "CSMT", "3 Tier AC", "demo", "GENERAL");
        assert cnf3.getStatus() == BookingStatus.CONFIRMED : "Should be Confirmed";
        System.out.println("  3rd Ticket: " + cnf3.getStatusDisplay());

        // Book 4th ticket -> Should get RAC 1
        Reservation rac1 = resService.bookTicket("TEST-RAC", "RAC One Passenger", 24, "F", "RAC-01", "NDLS", "CSMT", "3 Tier AC", "demo", "GENERAL");
        assert rac1.getStatus() == BookingStatus.RAC : "Should be RAC";
        assert rac1.getRacNumber() == 1 : "RAC number should be 1";
        System.out.println("  4th Ticket: " + rac1.getStatusDisplay());

        // Book 5th ticket -> Should get RAC 2
        Reservation rac2 = resService.bookTicket("TEST-RAC", "RAC Two Passenger", 26, "M", "RAC-02", "NDLS", "CSMT", "3 Tier AC", "demo", "GENERAL");
        assert rac2.getStatus() == BookingStatus.RAC : "Should be RAC";
        assert rac2.getRacNumber() == 2 : "RAC number should be 2";
        System.out.println("  5th Ticket: " + rac2.getStatusDisplay());

        // Book 6th ticket -> Should get Waiting List 1
        Reservation wl1 = resService.bookTicket("TEST-RAC", "WL One Passenger", 50, "M", "WL-01", "NDLS", "CSMT", "3 Tier AC", "demo", "GENERAL");
        assert wl1.getStatus() == BookingStatus.WAITING_LIST : "Should be Waiting List";
        assert wl1.getWaitingListNumber() == 1 : "WL number should be 1";
        System.out.println("  6th Ticket: " + wl1.getStatusDisplay());

        // 5. Verify Ticket Cancellation with 20% Refund and RAC -> CNF promotion!
        System.out.println("5. Testing 20% Refund and RAC Cascade Promotion...");
        double originalFare = cnf1.getFare();
        ReservationService.CancellationResult cancelResult = resService.cancelTicket(cnf1.getPnr());
        assert cancelResult.isSuccess();
        double expectedRefund = Math.round(originalFare * 0.20 * 100.0) / 100.0;
        double expectedCharge = Math.round((originalFare - expectedRefund) * 100.0) / 100.0;
        assert Math.abs(cancelResult.getRefundAmount() - expectedRefund) < 0.05 : "Refund mismatch";
        assert Math.abs(cancelResult.getCancellationCharge() - expectedCharge) < 0.05 : "Charge mismatch";
        System.out.println("  ✓ 20% Refund: ₹" + cancelResult.getRefundAmount() + " (Charge: ₹" + cancelResult.getCancellationCharge() + ")");
        System.out.println("  ✓ RAC Promotion: " + (cancelResult.getPromotedReservation() != null ? cancelResult.getPromotedReservation().getPassenger().getName() + " promoted to CONFIRMED!" : "None"));
        assert cancelResult.getPromotedReservation() != null;
        assert cancelResult.getPromotedReservation().getStatus() == BookingStatus.CONFIRMED;

        // Verify that rac1 was promoted to CONFIRMED!
        assert rac1.getStatus() == BookingStatus.CONFIRMED : "RAC 1 should be promoted to CONFIRMED!";
        assert rac1.getSeatNumber() > 0 : "RAC 1 should now have a seat number!";
        System.out.println("  ✓ RAC 1 promoted to Confirmed: " + rac1.getStatusDisplay());

        // Verify that wl1 was promoted to RAC!
        assert wl1.getStatus() == BookingStatus.RAC : "WL 1 should be promoted to RAC!";
        System.out.println("  ✓ WL 1 promoted to RAC: " + wl1.getStatusDisplay());

        System.out.println("\n>>> ALL ENHANCED BACKEND FEATURES VERIFIED SUCCESSFULLY! <<<");
    }
}
