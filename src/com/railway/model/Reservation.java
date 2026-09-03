package com.railway.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a passenger ticket reservation in the Railway system.
 * Holds PNR, Passenger, Train details, Seat Number, Status, and Fare.
 */
public class Reservation implements Comparable<Reservation> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String pnr;
    private final Passenger passenger;
    private final String trainId;
    private final String trainName;
    private final Station sourceStation;
    private final Station destinationStation;
    private final double fare;
    private final LocalDateTime bookingTime;
    private final String travelClass;
    private final String bookedByUsername;

    private int seatNumber; // 0 if on Waiting List
    private BookingStatus status;
    private int waitingListNumber; // 0 if confirmed

    public Reservation(String pnr, Passenger passenger, String trainId, String trainName,
                       Station sourceStation, Station destinationStation,
                       int seatNumber, BookingStatus status, int waitingListNumber,
                       double fare, LocalDateTime bookingTime, String travelClass, String bookedByUsername) {
        this.pnr = pnr;
        this.passenger = passenger;
        this.trainId = trainId;
        this.trainName = trainName;
        this.sourceStation = sourceStation;
        this.destinationStation = destinationStation;
        this.seatNumber = seatNumber;
        this.status = status;
        this.waitingListNumber = waitingListNumber;
        this.fare = fare;
        this.bookingTime = bookingTime != null ? bookingTime : LocalDateTime.now();
        this.travelClass = (travelClass != null && !travelClass.trim().isEmpty()) ? travelClass.trim() : "1st Class";
        this.bookedByUsername = (bookedByUsername != null && !bookedByUsername.trim().isEmpty()) ? bookedByUsername.trim().toLowerCase() : null;
    }

    public Reservation(String pnr, Passenger passenger, String trainId, String trainName,
                       Station sourceStation, Station destinationStation,
                       int seatNumber, BookingStatus status, int waitingListNumber,
                       double fare, LocalDateTime bookingTime, String travelClass) {
        this(pnr, passenger, trainId, trainName, sourceStation, destinationStation, seatNumber, status, waitingListNumber, fare, bookingTime, travelClass, null);
    }

    public Reservation(String pnr, Passenger passenger, String trainId, String trainName,
                       Station sourceStation, Station destinationStation,
                       int seatNumber, BookingStatus status, int waitingListNumber,
                       double fare, LocalDateTime bookingTime) {
        this(pnr, passenger, trainId, trainName, sourceStation, destinationStation, seatNumber, status, waitingListNumber, fare, bookingTime, "1st Class", null);
    }

    public String getPnr() {
        return pnr;
    }

    public Passenger getPassenger() {
        return passenger;
    }

    public String getTrainId() {
        return trainId;
    }

    public String getTrainName() {
        return trainName;
    }

    public Station getSourceStation() {
        return sourceStation;
    }

    public Station getDestinationStation() {
        return destinationStation;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(int seatNumber) {
        this.seatNumber = seatNumber;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public int getWaitingListNumber() {
        return waitingListNumber;
    }

    public void setWaitingListNumber(int waitingListNumber) {
        this.waitingListNumber = waitingListNumber;
    }

    public double getFare() {
        return fare;
    }

    public LocalDateTime getBookingTime() {
        return bookingTime;
    }

    public String getFormattedBookingTime() {
        return bookingTime.format(FORMATTER);
    }

    public String getStatusDisplay() {
        if (status == BookingStatus.CONFIRMED) {
            return "CONFIRMED (Seat: " + seatNumber + ")";
        } else if (status == BookingStatus.WAITING_LIST) {
            return "WAITING LIST (WL-" + waitingListNumber + ")";
        } else {
            return "CANCELLED";
        }
    }

    public String getTravelClass() {
        return travelClass;
    }

    public String getBookedByUsername() {
        return bookedByUsername;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reservation that = (Reservation) o;
        return Objects.equals(pnr, that.pnr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pnr);
    }

    @Override
    public int compareTo(Reservation other) {
        return this.bookingTime.compareTo(other.bookingTime);
    }

    @Override
    public String toString() {
        return String.format("PNR: %s | Passenger: %s | Train: %s (%s) | %s -> %s | Class: %s | Status: %s | Fare: ₹%.2f",
                pnr, passenger.getName(), trainName, trainId,
                sourceStation.getId(), destinationStation.getId(),
                travelClass, getStatusDisplay(), fare);
    }
}
