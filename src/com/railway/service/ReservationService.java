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
        private final Reservation promotedReservation; // Non-null if a waitlisted/RAC passenger got promoted
        private final double refundAmount;
        private final double cancellationCharge;
        private final String refundTxnId;

        public CancellationResult(boolean success, String message, Reservation cancelledReservation,
                                  Reservation promotedReservation, double refundAmount,
                                  double cancellationCharge, String refundTxnId) {
            this.success = success;
            this.message = message;
            this.cancelledReservation = cancelledReservation;
            this.promotedReservation = promotedReservation;
            this.refundAmount = refundAmount;
            this.cancellationCharge = cancellationCharge;
            this.refundTxnId = refundTxnId;
        }

        public CancellationResult(boolean success, String message, Reservation cancelledReservation, Reservation promotedReservation) {
            this(success, message, cancelledReservation, promotedReservation, 0.0, 0.0, "");
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

        public double getRefundAmount() {
            return refundAmount;
        }

        public double getCancellationCharge() {
            return cancellationCharge;
        }

        public String getRefundTxnId() {
            return refundTxnId;
        }
    }

    public ReservationService(RailwayNetworkService networkService, TrainService trainService) {
        this.networkService = networkService;
        this.trainService = trainService;
        this.reservationMap = new java.util.concurrent.ConcurrentHashMap<>();
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
        return bookTicket(trainId, passengerName, age, gender, passengerId, sourceId, destId, travelClass, bookedByUsername, "GENERAL");
    }

    /**
     * Books a ticket supporting Quotas (GENERAL vs EMERGENCY) with authentic pricing & seat allocation.
     * Emergency quota can be booked within 24 hours of departure at 2x the original price.
     * Dynamically resolves the train that travels through the selected stations if none specified or if mismatched.
     */
    public synchronized Reservation bookTicket(String trainId, String passengerName, int age,
                                               String gender, String passengerId,
                                               String sourceId, String destId, String travelClass,
                                               String bookedByUsername, String quota) {
        return bookTicket(trainId, passengerName, age, gender, passengerId, sourceId, destId, travelClass, bookedByUsername, quota, 1);
    }

    /**
     * Books multiple seats for a primary passenger and companions.
     */
    public synchronized Reservation bookTicket(String trainId, String passengerName, int age,
                                               String gender, String passengerId,
                                               String sourceId, String destId, String travelClass,
                                               String bookedByUsername, String quota, int seatCount) {
        return bookTicket(trainId, passengerName, age, gender, passengerId, sourceId, destId, travelClass, bookedByUsername, quota, seatCount, null);
    }

    /**
     * Books multiple seats for a primary passenger and companions with explicit travel date.
     */
    public synchronized Reservation bookTicket(String trainId, String passengerName, int age,
                                               String gender, String passengerId,
                                               String sourceId, String destId, String travelClass,
                                               String bookedByUsername, String quota, int seatCount, String travelDate) {
        int count = Math.max(1, Math.min(6, seatCount));
        List<Passenger> list = new ArrayList<>();
        list.add(new Passenger(passengerId, passengerName, age, gender));
        if (count > 1) {
            for (int i = 2; i <= count; i++) {
                list.add(new Passenger(passengerId + "-P" + i, passengerName + " (Guest " + i + ")", age, gender));
            }
        }
        return bookTickets(trainId, list, sourceId, destId, travelClass, bookedByUsername, quota, travelDate);
    }

    /**
     * Books multiple tickets for an explicit list of passengers (up to 6 passengers).
     */
    public synchronized Reservation bookTickets(String trainId, List<Passenger> passengers,
                                                String sourceId, String destId, String travelClass,
                                                String bookedByUsername, String quota) {
        return bookTickets(trainId, passengers, sourceId, destId, travelClass, bookedByUsername, quota, null);
    }

    /**
     * Books multiple tickets with designated travel/journey date.
     */
    public synchronized Reservation bookTickets(String trainId, List<Passenger> passengers,
                                                String sourceId, String destId, String travelClass,
                                                String bookedByUsername, String quota, String travelDate) {
        if (passengers == null || passengers.isEmpty()) {
            throw new IllegalArgumentException("At least one passenger required for booking.");
        }
        int seatCount = Math.max(1, Math.min(6, passengers.size()));
        Station source = networkService.getStation(sourceId);
        Station dest = networkService.getStation(destId);

        if (source == null || dest == null) {
            throw new IllegalArgumentException("Invalid source or destination station.");
        }

        Train train = (trainId != null && !trainId.trim().isEmpty()) ? trainService.getTrainDirect(trainId) : null;
        if (train == null || !train.servesRoute(source, dest)) {
            List<Train> matchingTrains = trainService.searchTrains(source.getId(), dest.getId());
            if (!matchingTrains.isEmpty()) {
                train = matchingTrains.get(0);
            } else {
                train = trainService.findOrCreateCorridorTrain(source, dest);
            }
        }

        if (train == null) {
            throw new IllegalArgumentException("No train service available between " + source.getName() + " and " + dest.getName() + ".");
        }

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

        double singleFare = Math.round(distance * train.getFarePerKm() * classMultiplier * 100.0) / 100.0;
        if (singleFare < 40.0) singleFare = 40.0;

        String selectedQuota = (quota != null && !quota.trim().isEmpty()) ? quota.trim().toUpperCase() : "GENERAL";
        boolean isEmergency = "EMERGENCY".equals(selectedQuota) || "TATKAL".equals(selectedQuota);
        if (isEmergency) {
            singleFare = Math.round(singleFare * 2.0 * 100.0) / 100.0;
            selectedQuota = "EMERGENCY";
        }

        double totalFare = Math.round(singleFare * seatCount * 100.0) / 100.0;
        Passenger primaryPassenger = passengers.get(0);
        String pnr = generateUniquePnr();

        boolean isGeneral = "General".equalsIgnoreCase(selectedClass)
                || selectedClass.toLowerCase().contains("general")
                || selectedClass.toLowerCase().contains("second sitting");

        Reservation reservation;
        if (isGeneral) {
            // General class (Second Sitting Unreserved): 200 seats capacity, open unreserved seating.
            for (int i = 0; i < seatCount; i++) {
                train.allocateSeat("General");
            }
            reservation = new Reservation(pnr, primaryPassenger, train.getId(), train.getName(),
                    source, dest, 0, BookingStatus.CONFIRMED, 0, 0, totalFare,
                    LocalDateTime.now(), selectedClass, bookedByUsername, selectedQuota,
                    train.getDepartureTime(), train.getArrivalTime());
            reservation.setSeatNumbers(new ArrayList<>());
            reservation.setSeatCount(seatCount);
            reservation.setPassengers(passengers);
        } else {
            List<Integer> allocatedSeats = new ArrayList<>();
            for (int i = 0; i < seatCount; i++) {
                int s = train.allocateSeat(selectedClass);
                if (s > 0) {
                    allocatedSeats.add(s);
                }
            }

            if (!allocatedSeats.isEmpty()) {
                reservation = new Reservation(pnr, primaryPassenger, train.getId(), train.getName(),
                        source, dest, allocatedSeats.get(0), BookingStatus.CONFIRMED, 0, 0, totalFare,
                        LocalDateTime.now(), selectedClass, bookedByUsername, selectedQuota,
                        train.getDepartureTime(), train.getArrivalTime());
                reservation.setSeatNumbers(allocatedSeats);
                reservation.setSeatCount(seatCount);
                reservation.setPassengers(passengers);
            } else {
                int racNo = train.allocateRacSeat();
                if (racNo > 0) {
                    reservation = new Reservation(pnr, primaryPassenger, train.getId(), train.getName(),
                            source, dest, 0, BookingStatus.RAC, 0, racNo, totalFare,
                            LocalDateTime.now(), selectedClass, bookedByUsername, selectedQuota,
                            train.getDepartureTime(), train.getArrivalTime());
                    reservation.setSeatCount(seatCount);
                    reservation.setPassengers(passengers);
                    train.enqueueRac(reservation);
                } else {
                int wlPos = train.getWaitingListCount() + 1;
                reservation = new Reservation(pnr, primaryPassenger, train.getId(), train.getName(),
                        source, dest, 0, BookingStatus.WAITING_LIST, wlPos, 0, totalFare,
                        LocalDateTime.now(), selectedClass, bookedByUsername, selectedQuota,
                        train.getDepartureTime(), train.getArrivalTime());
                reservation.setSeatCount(seatCount);
                reservation.setPassengers(passengers);
                train.enqueueWaitingList(reservation);
                }
            }
        }

        if (travelDate != null && !travelDate.trim().isEmpty()) {
            reservation.setTravelDate(travelDate.trim());
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
     * Calculates 20% refund of the ticket price (80% cancellation fee deducted).
     * Demonstrates Queue Dequeue & Waitlist/RAC Auto-Promotion Cascade.
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
            return new CancellationResult(false, "Ticket " + pnr + " is already cancelled.", reservation, null,
                    reservation.getRefundAmount(), reservation.getFare() - reservation.getRefundAmount(), "");
        }

        Train train = trainService.getTrainDirect(reservation.getTrainId());
        if (train == null) {
            return new CancellationResult(false, "Associated train not found in system.", reservation, null);
        }

        BookingStatus previousStatus = reservation.getStatus();
        reservation.setStatus(BookingStatus.CANCELLED);

        // Refund Policy: 80% refunded to passenger, 20% retained as Railway cancellation/clerkage fee
        double cancellationCharge = Math.round(reservation.getFare() * 0.20 * 100.0) / 100.0;
        double refundAmount = Math.round((reservation.getFare() - cancellationCharge) * 100.0) / 100.0;
        reservation.setRefundAmount(refundAmount);
        String refundTxnId = "REFUND-RAIL-" + Math.abs((pnr + System.currentTimeMillis()).hashCode() % 900000 + 100000);

        Reservation promotedReservation = null;

        if (previousStatus == BookingStatus.CONFIRMED) {
            List<Integer> releasedSeats = new ArrayList<>(reservation.getSeatNumbers());
            if (releasedSeats.isEmpty() && reservation.getSeatNumber() > 0) {
                releasedSeats.add(reservation.getSeatNumber());
            }
            reservation.setSeatNumber(0);
            reservation.setSeatNumbers(Collections.emptyList());

            for (int releasedSeatNumber : releasedSeats) {
                if (train.getRacListCount() > 0) {
                    Reservation promotedRac = train.dequeueRac();
                    if (promotedRac != null) {
                        promotedRac.setStatus(BookingStatus.CONFIRMED);
                        promotedRac.setSeatNumber(releasedSeatNumber);
                        promotedRac.setSeatNumbers(Collections.singletonList(releasedSeatNumber));
                        promotedRac.setRacNumber(0);
                        promotedReservation = promotedRac;

                        if (train.getWaitingListCount() > 0) {
                            Reservation promotedWl = train.dequeueWaitingList();
                            if (promotedWl != null) {
                                promotedWl.setStatus(BookingStatus.RAC);
                                promotedWl.setRacNumber(train.getRacSeats() - train.getAvailableRacSeats() + 1);
                                promotedWl.setWaitingListNumber(0);
                                train.enqueueRac(promotedWl);
                                updateWaitingListPositions(train);
                            }
                        } else {
                            train.releaseRacSeat();
                        }
                        continue;
                    }
                }

                if (train.getWaitingListCount() > 0) {
                    Reservation promotedWl = train.dequeueWaitingList();
                    if (promotedWl != null) {
                        promotedWl.setStatus(BookingStatus.CONFIRMED);
                        promotedWl.setSeatNumber(releasedSeatNumber);
                        promotedWl.setSeatNumbers(Collections.singletonList(releasedSeatNumber));
                        promotedWl.setWaitingListNumber(0);
                        updateWaitingListPositions(train);
                        promotedReservation = promotedWl;
                        continue;
                    }
                }
                train.releaseSeat(reservation.getTravelClass());
            }

            String msg;
            if (reservation.isGeneralClass()) {
                for (int i = 0; i < reservation.getSeatCount(); i++) {
                    train.releaseSeat("General");
                }
                msg = "General Unreserved Ticket " + pnr + " cancelled successfully. 80% Refund of ₹" + String.format("%.2f", refundAmount) +
                        " processed (20% cancellation fee ₹" + String.format("%.2f", cancellationCharge) + " retained, Txn: " + refundTxnId + "). (Unreserved General Seating - Released from 200 General capacity).";
            } else {
                msg = "Ticket " + pnr + " cancelled successfully. 80% Refund of ₹" + String.format("%.2f", refundAmount) +
                        " processed (20% cancellation fee ₹" + String.format("%.2f", cancellationCharge) + " retained, Txn: " + refundTxnId + "). Released " + (releasedSeats.size() > 1 ? releasedSeats.size() + " seats" : "Seat #" + (releasedSeats.isEmpty() ? "" : releasedSeats.get(0))) + "!";
                if (promotedReservation != null) {
                    msg += " Assigned to waiting passenger " + promotedReservation.getPassenger().getName() + "!";
                }
            }
            return new CancellationResult(true, msg, reservation, promotedReservation, refundAmount, cancellationCharge, refundTxnId);

        } else if (previousStatus == BookingStatus.RAC) {
            train.removeFromRac(reservation);
            if (train.getWaitingListCount() > 0) {
                Reservation promotedWl = train.dequeueWaitingList();
                if (promotedWl != null) {
                    promotedWl.setStatus(BookingStatus.RAC);
                    promotedWl.setRacNumber(reservation.getRacNumber());
                    promotedWl.setWaitingListNumber(0);
                    train.enqueueRac(promotedWl);
                    updateWaitingListPositions(train);
                    promotedReservation = promotedWl;
                }
            } else {
                train.releaseRacSeat();
            }

            return new CancellationResult(true,
                    "RAC Ticket " + pnr + " cancelled. 80% Refund of ₹" + String.format("%.2f", refundAmount) + " credited (20% cancellation charge ₹" + String.format("%.2f", cancellationCharge) + " retained).",
                    reservation, promotedReservation, refundAmount, cancellationCharge, refundTxnId);

        } else if (previousStatus == BookingStatus.WAITING_LIST) {
            train.removeFromWaitingList(reservation);
            updateWaitingListPositions(train);

            return new CancellationResult(true,
                    "Waiting List Ticket " + pnr + " cancelled. 80% Refund of ₹" + String.format("%.2f", refundAmount) + " credited (20% cancellation charge ₹" + String.format("%.2f", cancellationCharge) + " retained).",
                    reservation, null, refundAmount, cancellationCharge, refundTxnId);
        }

        return new CancellationResult(true, "Ticket cancelled. 80% Refund of ₹" + String.format("%.2f", refundAmount) +
                " processed (20% cancellation charge ₹" + String.format("%.2f", cancellationCharge) + " retained).", reservation, null, refundAmount, cancellationCharge, refundTxnId);
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
