package com.railway.model;

/**
 * Enumeration representing the current status of a Railway Reservation.
 */
public enum BookingStatus {
    CONFIRMED("CNF", "Confirmed"),
    WAITING_LIST("WL", "Waiting List"),
    CANCELLED("CAN", "Cancelled");

    private final String code;
    private final String description;

    BookingStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description + " (" + code + ")";
    }
}
