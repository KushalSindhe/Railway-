package com.railway.service;

import com.railway.dsa.CustomQueue;
import com.railway.dsa.DijkstraResult;
import com.railway.dsa.SortingAlgorithms;
import com.railway.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service managing Ticket Bookings, Cancellations, and the Waiting List Queue.
 * 
 * Key Business Rules & DSA Application:
 * 1. Seat Booking:
 *    - If availableSeats > 0: Allocates seat, sets status CONFIRMED.
 *    - If availableSeats == 0: Enqueues into train's CustomQueue<Reservation>, sets status WAITING_LIST.
 * 2. Ticket Cancellation & Waitlist Promotion:
 *    - If confirmed ticket is cancelled, the head of the FIFO queue is dequeued.
 *    - The waitlisted passenger is automatically promoted to CONFIRMED with the released seat.
 *    - If waitlist is empty, the seat is released back to available inventory.
 *    - If a waitlisted ticket is cancelled, it is removed from the queue.
 */
public class ReservationService {
    private final RailwayNetworkService networkService;
    private final TrainService trainService;
    private final Map<String, Reservation> reservationMap; // Key: PNR
    private final AtomicInteger pnrCounter;

    public static class CancellationResult {
        private final boolean success;
        private final String message;
        private final Reservation cancelledReservation;
        private final Reservation promotedReservation; // Non-null if a waitlisted passenger got promoted

        public CancellationResult(boolean success, String message, Reservation cancelledReservation, Reservation promotedReservation) {
            this.success = success;
            this.message = message;
            this.cancelledReservation = cancelledReservation;
            this.promotedReservation = promotedReservation;
        }

        public boolean isSuccess() {
            return success;
        }

        public String message() {
            return message;
        }

        public Reservation getCancelledReservation() {
            return cancelledReservation;
        }

        public Reservation getPromotedReservation() {
            return promotedReservation;
        }
    }

    public ReservationService(RailwayNetworkService networkService, TrainService trainService) {
        this.networkService = networkService;
        this.trainService = trainService;
        this.reservationMap = new LinkedHashMap<>();
        this.pnrCounter = new AtomicInteger(100101);
    }

    /**
     * Generates a unique, standardized PNR string.
     */
    private synchronized String generateUniquePnr() {
        return "PNR-" + pnrCounter.getAndIncrement();
    }

    /**
     * Books a ticket for a passenger on a specific train between source and destination stations.
     */
    public synchronized Reservation bookTicket(String trainId, String passengerName, int age,
                                               String gender, String passengerId,
                                               String sourceId, String destId) {
        return bookTicket(trainId, passengerName, age, gender, passengerId, sourceId, destId, "1st Class");
    }

    /**
     * Books a ticket with specified travel class (1st Class, 2nd Class, 3 Tier AC, Sleeper, General).
     */
    public synchronized Reservation bookTicket(String trainId, String passengerName, int age,
                                               String gender, String passengerId,
                                               String sourceId, String destId, String travelClass) {
        return bookTicket(trainId, passengerName, age, gender, passengerId, sourceId, destId, travelClass, null);
    }

    /**
     * Books a ticket linked to an authenticated user account with specified travel class.
     */
    public synchronized Reservation bookTicket(String trainId, String passengerName, int age,
                                               String gender, String passengerId,
                                               String sourceId, String destId, String travelClass,
                                               String bookedByUsername) {
        Train train = trainService.getTrainDirect(trainId);
        if (train == null) {
            throw new IllegalArgumentException("Train '" + trainId + "' not found.");
        }

        Station source = networkService.getStation(sourceId);
        Station dest = networkService.getStation(destId);

        if (source == null || dest == null) {
            throw new IllegalArgumentException("Invalid source or destination station.");
        }

        if (!train.servesRoute(source, dest)) {
            throw new IllegalArgumentException("Train " + train.getName() + " does not operate from "
                    + source.getId() + " to " + dest.getId() + ".");
        }

        // Calculate distance and fare with travel class multiplier
        DijkstraResult<Station> routeResult = networkService.findShortestRoute(source.getId(), dest.getId());
        double distance = routeResult.isReachable() ? routeResult.getTotalDistance() : 350.0;

        String selectedClass = (travelClass != null && !travelClass.trim().isEmpty()) ? travelClass.trim() : "1st Class";
        double classMultiplier = 1.0;
        String lowerClass = selectedClass.toLowerCase();
        if (lowerClass.contains("1st") || lowerClass.contains("first") || lowerClass.contains("1a")) {
            classMultiplier = 2.4;
            selectedClass = "1st Class";
        } else if (lowerClass.contains("2nd") || lowerClass.contains("second") || lowerClass.contains("2a")) {
            classMultiplier = 1.8;
            selectedClass = "2nd Class";
        } else if (lowerClass.contains("3 tier") || lowerClass.contains("3a") || lowerClass.contains("three")) {
            classMultiplier = 1.25;
            selectedClass = "3 Tier AC";
        } else if (lowerClass.contains("sleeper") || lowerClass.contains("sl")) {
            classMultiplier = 0.65;
            selectedClass = "Sleeper";
        } else if (lowerClass.contains("general") || lowerClass.contains("gn") || lowerClass.contains("second sitting")) {
            classMultiplier = 0.35;
            selectedClass = "General";
        }

        double fare = Math.round(distance * train.getFarePerKm() * classMultiplier * 100.0) / 100.0;
        if (fare < 40.0) fare = 40.0; // Minimum railway base fare

        Passenger passenger = new Passenger(passengerId, passengerName, age, gender);
        String pnr = generateUniquePnr();

        int seat = train.allocateSeat();
        Reservation reservation;

        if (seat > 0) {
            // Seat available -> Confirmed
            reservation = new Reservation(pnr, passenger, train.getId(), train.getName(),
                    source, dest, seat, BookingStatus.CONFIRMED, 0, fare, LocalDateTime.now(), selectedClass, bookedByUsername);
        } else {
            // No seats -> Placed in Waiting List Queue (FIFO)
            int wlPos = train.getWaitingListCount() + 1;
            reservation = new Reservation(pnr, passenger, train.getId(), train.getName(),
                    source, dest, 0, BookingStatus.WAITING_LIST, wlPos, fare, LocalDateTime.now(), selectedClass, bookedByUsername);
            train.enqueueWaitingList(reservation);
        }

        reservationMap.put(pnr, reservation);
        return reservation;
    }

    /**
     * Retrieves all reservations associated with a specific user account (newest first).
     */
    public synchronized List<Reservation> getReservationsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleanUser = username.trim().toLowerCase();
        List<Reservation> list = new ArrayList<>();
        for (Reservation res : reservationMap.values()) {
            if (cleanUser.equalsIgnoreCase(res.getBookedByUsername())) {
                list.add(res);
            }
        }
        list.sort((r1, r2) -> r2.getBookingTime().compareTo(r1.getBookingTime()));
        return list;
    }

    /**
     * Cancels a reservation by PNR.
     * Demonstrates Queue Dequeue & Waitlist Auto-Promotion.
     */
    public synchronized CancellationResult cancelTicket(String pnr) {
        if (pnr == null || pnr.trim().isEmpty()) {
            return new CancellationResult(false, "PNR cannot be empty.", null, null);
        }

        Reservation reservation = reservationMap.get(pnr.trim().toUpperCase());
        if (reservation == null) {
            return new CancellationResult(false, "Reservation with PNR " + pnr + " was not found.", null, null);
        }

        if (reservation.getStatus() == BookingStatus.CANCELLED) {
            return new CancellationResult(false, "Ticket " + pnr + " is already cancelled.", reservation, null);
        }

        Train train = trainService.getTrainDirect(reservation.getTrainId());
        if (train == null) {
            return new CancellationResult(false, "Associated train not found in system.", reservation, null);
        }

        BookingStatus previousStatus = reservation.getStatus();
        reservation.setStatus(BookingStatus.CANCELLED);
        Reservation promotedReservation = null;

        if (previousStatus == BookingStatus.CONFIRMED) {
            int releasedSeatNumber = reservation.getSeatNumber();
            reservation.setSeatNumber(0);

            // Check if there is a passenger waiting in the train's FIFO queue
            if (train.getWaitingListCount() > 0) {
                // Dequeue first passenger in queue (FIFO)
                promotedReservation = train.dequeueWaitingList();
                if (promotedReservation != null) {
                    promotedReservation.setStatus(BookingStatus.CONFIRMED);
                    promotedReservation.setSeatNumber(releasedSeatNumber);
                    promotedReservation.setWaitingListNumber(0);

                    // Update waiting list positions for remaining queue members
                    updateWaitingListPositions(train);

                    return new CancellationResult(true,
                            "Ticket " + pnr + " cancelled. Released Seat #" + releasedSeatNumber +
                            " automatically assigned to Waitlist passenger " + promotedReservation.getPassenger().getName() +
                            " (PNR: " + promotedReservation.getPnr() + ")!",
                            reservation, promotedReservation);
                }
            }

            // If no one on waitlist, return seat to available inventory
            train.releaseSeat();
            return new CancellationResult(true,
                    "Ticket " + pnr + " cancelled successfully. Seat #" + releasedSeatNumber +
                    " released back to train inventory.",
                    reservation, null);

        } else if (previousStatus == BookingStatus.WAITING_LIST) {
            // Cancelled from waitlist -> remove from queue
            train.removeFromWaitingList(reservation);
            updateWaitingListPositions(train);

            return new CancellationResult(true,
                    "Waitlisted ticket " + pnr + " removed from waiting list.",
                    reservation, null);
        }

        return new CancellationResult(true, "Ticket cancelled.", reservation, null);
    }

    /**
     * Updates the 1-based display position for remaining passengers in the waiting list queue.
     */
    private void updateWaitingListPositions(Train train) {
        int pos = 1;
        for (Reservation res : train.getWaitingList()) {
            res.setWaitingListNumber(pos++);
        }
    }

    /**
     * Looks up a reservation by PNR.
     */
    public Reservation getReservation(String pnr) {
        if (pnr == null) return null;
        return reservationMap.get(pnr.trim().toUpperCase());
    }

    /**
     * Returns all reservations.
     */
    public List<Reservation> getAllReservations() {
        List<Reservation> list = new ArrayList<>(reservationMap.values());
        SortingAlgorithms.mergeSort(list, (r1, r2) -> r2.getBookingTime().compareTo(r1.getBookingTime()));
        return list;
    }

    /**
     * Returns all reservations for a specific train (passenger manifest / chart).
     */
    public List<Reservation> getReservationsForTrain(String trainId) {
        List<Reservation> list = new ArrayList<>();
        for (Reservation r : reservationMap.values()) {
            if (r.getTrainId().equalsIgnoreCase(trainId)) {
                list.add(r);
            }
        }
        return list;
    }

    /**
     * Checks seat availability and waiting list depth for a train.
     */
    public String getAvailabilitySummary(String trainId) {
        Train train = trainService.getTrainDirect(trainId);
        if (train == null) return "Train not found";

        if (train.getAvailableSeats() > 0) {
            return "AVAILABLE - " + train.getAvailableSeats() + " seat(s)";
        } else {
            return "WL - " + train.getWaitingListCount() + " waiting";
        }
    }
}
