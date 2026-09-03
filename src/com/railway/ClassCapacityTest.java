package com.railway;

import com.railway.model.*;
import com.railway.service.*;
import java.util.*;

/**
 * Dedicated verification test suite for class-specific seat capacities:
 * 1A (1st Class): 25 seats
 * 2A (2nd Class): 50 seats
 * 3A (3 Tier AC): 70 seats
 * Sleeper (SL): 150 seats
 * General (GN): 200 seats
 * Total Train Capacity: 495 seats
 */
public class ClassCapacityTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  CLASS CAPACITY TEST: 1A=25, 2A=50, 3A=70, SL=150, GN=200");
        System.out.println("=================================================");

        int passed = 0;
        int total = 5;

        RailwayNetworkService networkService = new RailwayNetworkService();
        TrainService trainService = new TrainService(networkService);
        ReservationService resService = new ReservationService(networkService, trainService);
        SampleDataLoader.loadSampleData(networkService, trainService, resService);

        // TEST 1: Default express train class capacities
        System.out.print("Test 1: Verifying default class capacities on Express fleet (495 total)... ");
        Train karnatakaExp = trainService.getTrainDirect("12628");
        assert karnatakaExp != null;
        assert karnatakaExp.getTotalSeats() == 495 : "Total seats should be 495, got " + karnatakaExp.getTotalSeats();
        assert karnatakaExp.getSeats1A() == 25 : "1A should have 25 seats, got " + karnatakaExp.getSeats1A();
        assert karnatakaExp.getSeats2A() == 50 : "2A should have 50 seats, got " + karnatakaExp.getSeats2A();
        assert karnatakaExp.getSeats3A() == 70 : "3A should have 70 seats, got " + karnatakaExp.getSeats3A();
        assert karnatakaExp.getSeatsSL() == 150 : "SL should have 150 seats, got " + karnatakaExp.getSeatsSL();
        assert karnatakaExp.getSeatsGN() == 200 : "GN should have 200 seats, got " + karnatakaExp.getSeatsGN();
        System.out.println("PASSED (1A: 25, 2A: 50, 3A: 70, SL: 150, GN: 200 | Total: 495)");
        passed++;

        // TEST 2: Independent seat allocation across each class
        System.out.print("Test 2: Verifying independent seat allocation per class coach... ");
        // Book 1A ticket
        int initial1A = karnatakaExp.getAvailableSeats1A();
        int initial2A = karnatakaExp.getAvailableSeats2A();
        int initial3A = karnatakaExp.getAvailableSeats3A();
        int initialSL = karnatakaExp.getAvailableSeatsSL();
        int initialGN = karnatakaExp.getAvailableSeatsGN();

        Reservation res1A = resService.bookTicket("12628", "Executive VIP", 45, "M", "VIP-01", "SBC", "NDLS", "1st Class", "testuser");
        assert res1A.getStatus() == BookingStatus.CONFIRMED : "1A ticket should be confirmed";
        assert res1A.getSeatNumber() == 1 : "First 1A passenger should get Seat #1 in Coach H1, got " + res1A.getSeatNumber();
        assert karnatakaExp.getAvailableSeats1A() == initial1A - 1 : "1A available seats should decrement by 1";
        assert karnatakaExp.getAvailableSeats2A() == initial2A : "2A available seats should remain unchanged";
        assert karnatakaExp.getAvailableSeats3A() == initial3A : "3A available seats should remain unchanged";
        assert karnatakaExp.getAvailableSeatsSL() == initialSL : "SL available seats should remain unchanged";
        assert karnatakaExp.getAvailableSeatsGN() == initialGN : "GN available seats should remain unchanged";

        // Book 2A ticket
        Reservation res2A = resService.bookTicket("12628", "AC Two Tier Passenger", 34, "F", "2A-01", "SBC", "NDLS", "2nd Class", "testuser");
        assert res2A.getStatus() == BookingStatus.CONFIRMED;
        assert res2A.getSeatNumber() == 1 : "First 2A passenger should get Seat #1 in Coach A2, got " + res2A.getSeatNumber();
        assert karnatakaExp.getAvailableSeats2A() == initial2A - 1;

        // Book Sleeper ticket
        Reservation resSL = resService.bookTicket("12628", "Sleeper Passenger", 29, "M", "SL-01", "SBC", "NDLS", "Sleeper", "testuser");
        assert resSL.getStatus() == BookingStatus.CONFIRMED;
        assert resSL.getSeatNumber() == 1 : "First SL passenger should get Seat #1 in Coach S1, got " + resSL.getSeatNumber();
        assert karnatakaExp.getAvailableSeatsSL() == initialSL - 1;
        System.out.println("PASSED (Class inventories are completely isolated)");
        passed++;

        // TEST 3: General class unreserved seating behavior with 200 capacity
        System.out.print("Test 3: Verifying General class (200 seats) unreserved open seating... ");
        int gnBefore = karnatakaExp.getAvailableSeatsGN();
        Reservation resGN = resService.bookTicket("12628", "General Passenger", 22, "M", "GN-01", "SBC", "NDLS", "General", "testuser");
        assert resGN.getStatus() == BookingStatus.CONFIRMED : "General ticket should be confirmed";
        assert resGN.getSeatNumber() == 0 : "General ticket must not have a reserved seat number (seatNumber = 0)";
        assert resGN.getSeatNumbers().isEmpty() : "General ticket seatNumbers list must be empty";
        assert resGN.isGeneralClass() : "isGeneralClass() should return true";
        assert karnatakaExp.getAvailableSeatsGN() == gnBefore - 1 : "General capacity should decrement from 200";
        System.out.println("PASSED (General: unreserved open seating with 200 capacity)");
        passed++;

        // TEST 4: Multi-seat booking in specific class
        System.out.print("Test 4: Verifying multi-seat booking in 1st Class (1A: 25 seats)... ");
        List<Passenger> vipGroup = Arrays.asList(
                new Passenger("VIP-02", "Ambassador Alpha", 50, "M"),
                new Passenger("VIP-03", "Ambassador Beta", 48, "F"),
                new Passenger("VIP-04", "Ambassador Gamma", 52, "M")
        );
        int avail1ABefore = karnatakaExp.getAvailableSeats1A();
        Reservation resGroup = resService.bookTickets("12628", vipGroup, "SBC", "NDLS", "1st Class", "testuser", "GENERAL");
        assert resGroup.getStatus() == BookingStatus.CONFIRMED;
        assert resGroup.getSeatCount() == 3;
        assert resGroup.getSeatNumbers().size() == 3;
        assert resGroup.getSeatNumbers().equals(Arrays.asList(2, 3, 4)) : "Should allocate seats #2, #3, #4 in Coach H1";
        assert karnatakaExp.getAvailableSeats1A() == avail1ABefore - 3;
        System.out.println("PASSED (Allocated 3 seats: #2, #3, #4 from 1A capacity 25)");
        passed++;

        // TEST 5: Cancellation & seat release back to specific class
        System.out.print("Test 5: Verifying cancellation restores seat to correct class... ");
        int avail1ABeforeCancel = karnatakaExp.getAvailableSeats1A();
        ReservationService.CancellationResult cr1A = resService.cancelTicket(res1A.getPnr());
        assert cr1A.isSuccess();
        assert karnatakaExp.getAvailableSeats1A() == avail1ABeforeCancel + 1 : "Cancelled 1A seat should restore 1A availability";

        int availGNBeforeCancel = karnatakaExp.getAvailableSeatsGN();
        ReservationService.CancellationResult crGN = resService.cancelTicket(resGN.getPnr());
        assert crGN.isSuccess();
        assert karnatakaExp.getAvailableSeatsGN() == availGNBeforeCancel + 1 : "Cancelled General ticket should restore 200 General capacity";
        System.out.println("PASSED (1A and General capacities properly restored on cancellation)");
        passed++;

        System.out.println("=================================================");
        System.out.println("  ALL TESTS PASSED (" + passed + "/" + total + ") SUCCESS!");
        System.out.println("=================================================");
    }
}
