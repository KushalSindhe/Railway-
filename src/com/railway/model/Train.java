package com.railway.model;

import com.railway.dsa.CustomQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a Train operating on the railway network.
 * Maintains route stops, seat capacity, availability, and an encapsulated
 * CustomQueue data structure managing the Waiting List.
 */
public class Train implements Comparable<Train> {
    private final String id;
    private final String name;
    private final Station source;
    private final Station destination;
    private final List<Station> routeStops;
    private final int totalSeats;
    private int availableSeats;
    private final double farePerKm;
    private final CustomQueue<Reservation> waitingList;

    public Train(String id, String name, Station source, Station destination,
                 List<Station> routeStops, int totalSeats, double farePerKm) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Train ID cannot be empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Train name cannot be empty.");
        }
        if (source == null || destination == null) {
            throw new IllegalArgumentException("Source and destination cannot be null.");
        }
        if (totalSeats <= 0) {
            throw new IllegalArgumentException("Total seats must be strictly positive.");
        }

        this.id = id.trim();
        this.name = name.trim();
        this.source = source;
        this.destination = destination;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
        this.farePerKm = farePerKm > 0 ? farePerKm : 1.25;
        this.waitingList = new CustomQueue<>();

        this.routeStops = new ArrayList<>();
        if (routeStops != null && !routeStops.isEmpty()) {
            this.routeStops.addAll(routeStops);
        } else {
            this.routeStops.add(source);
            this.routeStops.add(destination);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Station getSource() {
        return source;
    }

    public Station getDestination() {
        return destination;
    }

    public List<Station> getRouteStops() {
        return Collections.unmodifiableList(routeStops);
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public synchronized int getAvailableSeats() {
        return availableSeats;
    }

    public double getFarePerKm() {
        return farePerKm;
    }

    public CustomQueue<Reservation> getWaitingList() {
        return waitingList;
    }

    public synchronized int getWaitingListCount() {
        return waitingList.size();
    }

    /**
     * Checks whether the train stops at both source and destination in the specified directional order.
     */
    public boolean servesRoute(Station from, Station to) {
        if (from == null || to == null) return false;
        int fromIndex = -1;
        int toIndex = -1;

        for (int i = 0; i < routeStops.size(); i++) {
            if (routeStops.get(i).equals(from) && fromIndex == -1) {
                fromIndex = i;
            }
            if (routeStops.get(i).equals(to)) {
                toIndex = i;
            }
        }

        return fromIndex != -1 && toIndex != -1 && fromIndex < toIndex;
    }

    /**
     * Allocates a seat if available.
     * @return Seat number (1 to totalSeats), or -1 if no seats available.
     */
    public synchronized int allocateSeat() {
        if (availableSeats > 0) {
            int allocatedSeatNumber = (totalSeats - availableSeats) + 1;
            availableSeats--;
            return allocatedSeatNumber;
        }
        return -1;
    }

    /**
     * Releases a seat.
     */
    public synchronized void releaseSeat() {
        if (availableSeats < totalSeats) {
            availableSeats++;
        }
    }

    /**
     * Enqueues a reservation onto this train's waiting list queue.
     * @return 1-based position in waiting list.
     */
    public synchronized int enqueueWaitingList(Reservation reservation) {
        waitingList.enqueue(reservation);
        return waitingList.size();
    }

    /**
     * Dequeues the next reservation in the FIFO waiting list.
     */
    public synchronized Reservation dequeueWaitingList() {
        if (!waitingList.isEmpty()) {
            return waitingList.dequeue();
        }
        return null;
    }

    /**
     * Removes a reservation from the waiting list if cancelled.
     */
    public synchronized boolean removeFromWaitingList(Reservation reservation) {
        return waitingList.remove(reservation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Train train = (Train) o;
        return Objects.equals(id, train.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public int compareTo(Train other) {
        return this.id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s -> %s | Seats Avail: %d/%d | WL: %d",
                id, name, source.getId(), destination.getId(), availableSeats, totalSeats, waitingList.size());
    }
}
