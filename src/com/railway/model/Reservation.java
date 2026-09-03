package com.railway.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a passenger ticket reservation in the Railway system.
 * Holds PNR, Passenger(s), Train details, Seat Number(s), Status, and Fare.
 */
public class Reservation implements Comparable<Reservation> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String pnr;
    private final Passenger passenger; // Primary passenger (lead passenger)
    private final String trainId;
    private final String trainName;
    private final Station sourceStation;
    private final Station destinationStation;
    private final double fare;
    private final LocalDateTime bookingTime;
    private final String travelClass;
    private final String bookedByUsername;
    private final String quota;
    private final String departureTime;
    private final String arrivalTime;

    private int seatNumber; // Primary seat number (0 if on Waiting List or RAC)
    private BookingStatus status;
    private int waitingListNumber; // 0 if confirmed or RAC
    private int racNumber; // 0 if confirmed or WL
    private double refundAmount; // 0.0 unless cancelled

    // Multiple Seats and Passenger Manifest Support
    private int seatCount;
    private List<Integer> seatNumbers = new ArrayList<>();
    private List<Passenger> passengers = new ArrayList<>();
    private String travelDate;

    public Reservation(String pnr, Passenger passenger, String trainId, String trainName,
                       Station sourceStation, Station destinationStation,
                       int seatNumber, BookingStatus status, int waitingListNumber, int racNumber,
                       double fare, LocalDateTime bookingTime, String travelClass, String bookedByUsername,
                       String quota, String departureTime, String arrivalTime) {
        this.pnr = pnr;
        this.passenger = passenger;
        this.trainId = trainId;
        this.trainName = trainName;
        this.sourceStation = sourceStation;
        this.destinationStation = destinationStation;
        this.seatNumber = seatNumber;
        this.status = status;
        this.waitingListNumber = waitingListNumber;
        this.racNumber = racNumber;
        this.fare = fare;
        this.bookingTime = bookingTime != null ? bookingTime : LocalDateTime.now();
        this.travelClass = (travelClass != null && !travelClass.trim().isEmpty()) ? travelClass.trim() : "1st Class";
        this.bookedByUsername = (bookedByUsername != null && !bookedByUsername.trim().isEmpty()) ? bookedByUsername.trim().toLowerCase() : null;
        this.quota = (quota != null && !quota.trim().isEmpty()) ? quota.trim().toUpperCase() : "GENERAL";
        this.departureTime = (departureTime != null && !departureTime.trim().isEmpty()) ? departureTime.trim() : "06:00";
        this.arrivalTime = (arrivalTime != null && !arrivalTime.trim().isEmpty()) ? arrivalTime.trim() : "14:30";
        this.refundAmount = 0.0;
        this.seatCount = 1;
        this.seatNumbers = new ArrayList<>();
        if (seatNumber > 0) {
            this.seatNumbers.add(seatNumber);
        }
        this.passengers = new ArrayList<>();
        if (passenger != null) {
            this.passengers.add(passenger);
        }
    }

    public Reservation(String pnr, Passenger passenger, String trainId, String trainName,
                       Station sourceStation, Station destinationStation,
                       int seatNumber, BookingStatus status, int waitingListNumber,
                       double fare, LocalDateTime bookingTime, String travelClass, String bookedByUsername) {
        this(pnr, passenger, trainId, trainName, sourceStation, destinationStation, seatNumber, status, waitingListNumber, 0, fare, bookingTime, travelClass, bookedByUsername, "GENERAL", "06:00", "14:30");
    }

    public Reservation(String pnr, Passenger passenger, String trainId, String trainName,
                       Station sourceStation, Station destinationStation,
                       int seatNumber, BookingStatus status, int waitingListNumber,
                       double fare, LocalDateTime bookingTime, String travelClass) {
        this(pnr, passenger, trainId, trainName, sourceStation, destinationStation, seatNumber, status, waitingListNumber, 0, fare, bookingTime, travelClass, null, "GENERAL", "06:00", "14:30");
    }

    public Reservation(String pnr, Passenger passenger, String trainId, String trainName,
                       Station sourceStation, Station destinationStation,
                       int seatNumber, BookingStatus status, int waitingListNumber,
                       double fare, LocalDateTime bookingTime) {
        this(pnr, passenger, trainId, trainName, sourceStation, destinationStation, seatNumber, status, waitingListNumber, 0, fare, bookingTime, "1st Class", null, "GENERAL", "06:00", "14:30");
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

    public String getQuota() {
        return quota;
    }

    public int getRacNumber() {
        return racNumber;
    }

    public void setRacNumber(int racNumber) {
        this.racNumber = racNumber;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public double getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(double refundAmount) {
        this.refundAmount = refundAmount;
    }

    public int getSeatCount() {
        if (seatCount <= 0) {
            return (seatNumbers != null && !seatNumbers.isEmpty()) ? seatNumbers.size() : 1;
        }
        return seatCount;
    }

    public void setSeatCount(int seatCount) {
        this.seatCount = seatCount;
    }

    public List<Integer> getSeatNumbers() {
        return seatNumbers != null ? Collections.unmodifiableList(seatNumbers) : Collections.emptyList();
    }

    public void setSeatNumbers(List<Integer> seatNumbers) {
        this.seatNumbers = (seatNumbers != null) ? new ArrayList<>(seatNumbers) : new ArrayList<>();
        if (!this.seatNumbers.isEmpty()) {
            this.seatNumber = this.seatNumbers.get(0);
            this.seatCount = this.seatNumbers.size();
        }
    }

    public List<Passenger> getPassengers() {
        return passengers != null ? Collections.unmodifiableList(passengers) : Collections.emptyList();
    }

    public void setPassengers(List<Passenger> passengers) {
        this.passengers = (passengers != null) ? new ArrayList<>(passengers) : new ArrayList<>();
    }

    public String getTravelDate() {
        if (travelDate == null || travelDate.trim().isEmpty()) {
            return bookingTime != null ? bookingTime.toLocalDate().toString() : java.time.LocalDate.now().toString();
        }
        return travelDate.trim();
    }

    public void setTravelDate(String travelDate) {
        this.travelDate = travelDate;
    }

    public String getFormattedTravelDate() {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(getTravelDate());
            return d.format(DateTimeFormatter.ofPattern("dd MMM yyyy (EEEE)"));
        } catch (Exception e) {
            return getTravelDate();
        }
    }

    public boolean isGeneralClass() {
        return travelClass != null && (travelClass.equalsIgnoreCase("General")
                || travelClass.toLowerCase().contains("general")
                || travelClass.toLowerCase().contains("second sitting"));
    }

    public String getSeatNumbersDisplay() {
        if (isGeneralClass()) {
            return status == BookingStatus.CANCELLED ? "Cancelled" : "Unreserved (Open Seating - Coach GS1)";
        }
        if (status == BookingStatus.CONFIRMED) {
            if (seatNumbers != null && seatNumbers.size() > 1) {
                return "Seats: #" + seatNumbers.stream().map(Object::toString).collect(Collectors.joining(", #"));
            } else if (seatNumber > 0) {
                return "Seat #" + seatNumber;
            }
            return "Seat Confirmed";
        } else if (status == BookingStatus.RAC) {
            return "RAC-" + racNumber + (getSeatCount() > 1 ? " (" + getSeatCount() + " Seats)" : "");
        } else if (status == BookingStatus.WAITING_LIST) {
            return "WL-" + waitingListNumber + (getSeatCount() > 1 ? " (" + getSeatCount() + " Seats)" : "");
        } else {
            return "Cancelled";
        }
    }

    public String getPassengersDisplay() {
        if (passengers != null && !passengers.isEmpty()) {
            return passengers.stream().map(p -> p.getName() + " (" + p.getAge() + p.getGender() + ")").collect(Collectors.joining(", "));
        }
        return passenger != null ? passenger.getName() + " (" + passenger.getAge() + passenger.getGender() + ")" : "Passenger";
    }

    public String getStatusDisplay() {
        if (isGeneralClass()) {
            if (status == BookingStatus.CONFIRMED) {
                return "CONFIRMED (Unreserved Open Seating - Coach GS1)";
            } else if (status == BookingStatus.CANCELLED) {
                return refundAmount > 0
                        ? String.format("CANCELLED (Refund 20%%: ₹%.2f)", refundAmount)
                        : "CANCELLED";
            }
        }
        if (status == BookingStatus.CONFIRMED) {
            if (seatNumbers != null && seatNumbers.size() > 1) {
                return "CONFIRMED (" + getSeatNumbersDisplay() + " • " + seatNumbers.size() + " Seats)";
            }
            return "CONFIRMED (Seat: " + seatNumber + ")";
        } else if (status == BookingStatus.RAC) {
            return "RAC (RAC-" + racNumber + (getSeatCount() > 1 ? " • " + getSeatCount() + " Seats" : "") + ")";
        } else if (status == BookingStatus.WAITING_LIST) {
            return "WAITING LIST (WL-" + waitingListNumber + (getSeatCount() > 1 ? " • " + getSeatCount() + " Seats" : "") + ")";
        } else {
            return refundAmount > 0
                    ? String.format("CANCELLED (Refund 20%%: ₹%.2f)", refundAmount)
                    : "CANCELLED";
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
