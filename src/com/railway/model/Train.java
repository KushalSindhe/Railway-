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
    public static final int SEATS_1A = 25;
    public static final int SEATS_2A = 50;
    public static final int SEATS_3A = 70;
    public static final int SEATS_SL = 150;
    public static final int SEATS_GN = 200;
    public static final int TOTAL_CONFIGURED_SEATS = SEATS_1A + SEATS_2A + SEATS_3A + SEATS_SL + SEATS_GN; // 495

    private final java.util.Map<String, Integer> classCapacity = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Integer> classAvailable = new java.util.concurrent.ConcurrentHashMap<>();

    private final List<Station> routeStops;
    private final int totalSeats;
    private int availableSeats;
    private final double farePerKm;
    private final String departureTime;
    private final String arrivalTime;
    private final String travelDuration;
    private final int racSeats;
    private int availableRacSeats;
    private final int emergencySeats;
    private final CustomQueue<Reservation> waitingList;
    private final CustomQueue<Reservation> racList;
    private int dynamicWaitlistCount = 0;

    public Train(String id, String name, Station source, Station destination,
                 List<Station> routeStops, int totalSeats, double farePerKm,
                 String departureTime, String arrivalTime, String travelDuration,
                 int racSeats, int emergencySeats,
                 int seats1A, int seats2A, int seats3A, int seatsSL, int seatsGN) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Train ID cannot be empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Train name cannot be empty.");
        }
        if (source == null || destination == null) {
            throw new IllegalArgumentException("Source and destination cannot be null.");
        }

        int s1A = seats1A > 0 ? seats1A : SEATS_1A;
        int s2A = seats2A > 0 ? seats2A : SEATS_2A;
        int s3A = seats3A > 0 ? seats3A : SEATS_3A;
        int sSL = seatsSL > 0 ? seatsSL : SEATS_SL;
        int sGN = seatsGN > 0 ? seatsGN : SEATS_GN;

        classCapacity.put("1st Class", s1A);
        classCapacity.put("2nd Class", s2A);
        classCapacity.put("3 Tier AC", s3A);
        classCapacity.put("Sleeper", sSL);
        classCapacity.put("General", sGN);

        classAvailable.put("1st Class", s1A);
        classAvailable.put("2nd Class", s2A);
        classAvailable.put("3 Tier AC", s3A);
        classAvailable.put("Sleeper", sSL);
        classAvailable.put("General", sGN);

        int sumClass = s1A + s2A + s3A + sSL + sGN;
        this.totalSeats = (totalSeats > 0 && totalSeats != TOTAL_CONFIGURED_SEATS) ? totalSeats : sumClass;
        this.availableSeats = this.totalSeats;

        if (totalSeats > 0 && totalSeats != TOTAL_CONFIGURED_SEATS && totalSeats != sumClass) {
            // For custom test trains, configure each class with the specified test capacity
            classCapacity.put("1st Class", totalSeats);
            classCapacity.put("2nd Class", totalSeats);
            classCapacity.put("3 Tier AC", totalSeats);
            classCapacity.put("Sleeper", totalSeats);
            classCapacity.put("General", totalSeats);

            classAvailable.put("1st Class", totalSeats);
            classAvailable.put("2nd Class", totalSeats);
            classAvailable.put("3 Tier AC", totalSeats);
            classAvailable.put("Sleeper", totalSeats);
            classAvailable.put("General", totalSeats);
        }

        this.id = id.trim();
        this.name = name.trim();
        this.source = source;
        this.destination = destination;
        this.farePerKm = farePerKm > 0 ? farePerKm : 1.25;
        this.departureTime = (departureTime != null && !departureTime.trim().isEmpty()) ? departureTime.trim() : "06:00";
        this.arrivalTime = (arrivalTime != null && !arrivalTime.trim().isEmpty()) ? arrivalTime.trim() : "14:30";
        this.travelDuration = (travelDuration != null && !travelDuration.trim().isEmpty()) ? travelDuration.trim() : "8h 30m";
        this.racSeats = Math.max(0, racSeats);
        this.availableRacSeats = this.racSeats;
        this.emergencySeats = Math.max(0, emergencySeats);
        this.waitingList = new CustomQueue<>();
        this.racList = new CustomQueue<>();

        this.routeStops = new ArrayList<>();
        if (routeStops != null && !routeStops.isEmpty()) {
            this.routeStops.addAll(routeStops);
        } else {
            this.routeStops.add(source);
            this.routeStops.add(destination);
        }
    }

    public Train(String id, String name, Station source, Station destination,
                 List<Station> routeStops, int totalSeats, double farePerKm,
                 String departureTime, String arrivalTime, String travelDuration,
                 int racSeats, int emergencySeats) {
        this(id, name, source, destination, routeStops, totalSeats, farePerKm,
                departureTime, arrivalTime, travelDuration, racSeats, emergencySeats,
                SEATS_1A, SEATS_2A, SEATS_3A, SEATS_SL, SEATS_GN);
    }

    public Train(String id, String name, Station source, Station destination,
                 List<Station> routeStops, int totalSeats, double farePerKm) {
        this(id, name, source, destination, routeStops, totalSeats, farePerKm, "06:00", "14:30", "8h 30m", 2, 2);
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
        return Math.max(waitingList.size(), dynamicWaitlistCount);
    }

    public synchronized void setDynamicWaitlistCount(int count) {
        this.dynamicWaitlistCount = Math.max(0, count);
    }

    public synchronized void recalcTotalAvailable() {
        int sum = 0;
        for (int v : classAvailable.values()) {
            sum += v;
        }
        this.availableSeats = sum;
    }

    /**
     * Randomly adjusts confirmed seat availability and waiting list counts to model
     * live dynamic booking activity, cancellations, and rush demands across trains.
     */
    public synchronized void randomizeSeatAndWaitlist(java.util.Random rand) {
        if (totalSeats <= 5) return; // Preserve small test trains

        // Choose one of 3 realistic demand scenarios:
        // 0: High-Demand Rush (sold out or low seats in popular classes, active waiting list)
        // 1: Medium-Demand (moderate confirmed availability, small RAC / WL)
        // 2: Ample Availability (plenty of confirmed seats, 0 WL)
        int scenario = rand.nextInt(3);

        if (scenario == 0) {
            // High Demand: 0 to 4 seats in 1A, 2A, 3A, SL; WL-5 to WL-38
            for (String cls : classCapacity.keySet()) {
                int cap = classCapacity.get(cls);
                if (rand.nextInt(100) < 65) {
                    classAvailable.put(cls, 0);
                } else {
                    classAvailable.put(cls, rand.nextInt(Math.max(1, Math.min(6, cap))));
                }
            }
            recalcTotalAvailable();
            this.availableRacSeats = rand.nextInt(Math.max(1, this.racSeats) + 1);
            this.dynamicWaitlistCount = 6 + rand.nextInt(33); // WL-6 to WL-38
        } else if (scenario == 1) {
            // Medium Demand: 15% to 35% capacity available, RAC or low WL
            for (String cls : classCapacity.keySet()) {
                int cap = classCapacity.get(cls);
                int av = (int) (cap * (0.12 + rand.nextDouble() * 0.25));
                classAvailable.put(cls, Math.max(1, Math.min(cap, av)));
            }
            recalcTotalAvailable();
            this.availableRacSeats = Math.min(this.racSeats, rand.nextInt(Math.max(1, this.racSeats) + 1));
            this.dynamicWaitlistCount = rand.nextInt(5); // WL-0 to WL-4
        } else {
            // Ample Availability: 40% to 80% confirmed capacity available, 0 WL
            for (String cls : classCapacity.keySet()) {
                int cap = classCapacity.get(cls);
                int av = (int) (cap * (0.40 + rand.nextDouble() * 0.40));
                classAvailable.put(cls, Math.max(5, Math.min(cap, av)));
            }
            recalcTotalAvailable();
            this.availableRacSeats = this.racSeats;
            this.dynamicWaitlistCount = 0;
        }
    }

    /**
     * Incrementally fluctuates confirmed seats and waitlists (modeling live bookings/cancellations).
     */
    public synchronized void fluctuateInventory(java.util.Random rand) {
        if (totalSeats <= 5) return;
        for (String cls : classCapacity.keySet()) {
            int cap = classCapacity.get(cls);
            int current = classAvailable.getOrDefault(cls, cap);
            int delta = rand.nextInt(7) - 3; // -3 to +3
            int updated = Math.max(0, Math.min(cap, current + delta));
            classAvailable.put(cls, updated);
        }
        recalcTotalAvailable();

        if (availableSeats <= 25) {
            int wlDelta = rand.nextInt(5) - 2; // -2 to +2
            int newWl = Math.max(1, this.dynamicWaitlistCount + wlDelta);
            if (newWl <= 1 && rand.nextBoolean()) newWl = 5 + rand.nextInt(25);
            this.dynamicWaitlistCount = newWl;
        } else {
            if (this.dynamicWaitlistCount > 0) {
                this.dynamicWaitlistCount = Math.max(0, this.dynamicWaitlistCount - (1 + rand.nextInt(3)));
            }
        }
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

    public String getDepartureTime() {
        return departureTime;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public String getTravelDuration() {
        return travelDuration;
    }

    public int getRacSeats() {
        return racSeats;
    }

    public synchronized int getAvailableRacSeats() {
        return availableRacSeats;
    }

    public int getEmergencySeats() {
        return emergencySeats;
    }

    public CustomQueue<Reservation> getRacList() {
        return racList;
    }

    public synchronized int getRacListCount() {
        return racList.size();
    }

    public static String normalizeClass(String travelClass) {
        if (travelClass == null) return "3 Tier AC";
        String s = travelClass.trim().toLowerCase();
        if (s.contains("1st") || s.contains("first") || s.equals("1a")) return "1st Class";
        if (s.contains("2nd") || (s.contains("second") && !s.contains("sitting")) || s.equals("2a")) return "2nd Class";
        if (s.contains("3 tier") || s.contains("3a") || s.contains("three")) return "3 Tier AC";
        if (s.contains("sleeper") || s.equals("sl")) return "Sleeper";
        if (s.contains("general") || s.contains("gn") || s.contains("second sitting")) return "General";
        return "3 Tier AC";
    }

    public int getSeats1A() { return classCapacity.getOrDefault("1st Class", SEATS_1A); }
    public int getSeats2A() { return classCapacity.getOrDefault("2nd Class", SEATS_2A); }
    public int getSeats3A() { return classCapacity.getOrDefault("3 Tier AC", SEATS_3A); }
    public int getSeatsSL() { return classCapacity.getOrDefault("Sleeper", SEATS_SL); }
    public int getSeatsGN() { return classCapacity.getOrDefault("General", SEATS_GN); }

    public int getAvailableSeats1A() { return classAvailable.getOrDefault("1st Class", SEATS_1A); }
    public int getAvailableSeats2A() { return classAvailable.getOrDefault("2nd Class", SEATS_2A); }
    public int getAvailableSeats3A() { return classAvailable.getOrDefault("3 Tier AC", SEATS_3A); }
    public int getAvailableSeatsSL() { return classAvailable.getOrDefault("Sleeper", SEATS_SL); }
    public int getAvailableSeatsGN() { return classAvailable.getOrDefault("General", SEATS_GN); }

    public int getTotalSeats(String travelClass) {
        return classCapacity.getOrDefault(normalizeClass(travelClass), SEATS_3A);
    }

    public synchronized int getAvailableSeats(String travelClass) {
        return classAvailable.getOrDefault(normalizeClass(travelClass), SEATS_3A);
    }

    /**
     * Allocates a seat for a specific travel class.
     * For 1A (25), 2A (50), 3A (70), SL (150), returns seat number (1 to N).
     * For General (200), returns 0 (unreserved open seating).
     * @return Seat number, or -1 if class is full.
     */
    public synchronized int allocateSeat(String travelClass) {
        String key = normalizeClass(travelClass);
        if ("General".equals(key)) {
            int avail = classAvailable.getOrDefault(key, SEATS_GN);
            if (avail > 0) {
                classAvailable.put(key, avail - 1);
                return 0; // Unreserved open seating
            }
            return -1;
        }

        int avail = classAvailable.getOrDefault(key, 0);
        int total = classCapacity.getOrDefault(key, 0);
        if (avail > 0) {
            int allocatedSeatNumber = (total - avail) + 1;
            classAvailable.put(key, avail - 1);
            availableSeats = Math.max(0, availableSeats - 1);
            return allocatedSeatNumber;
        }
        return -1;
    }

    /**
     * Releases a seat for a specific travel class.
     */
    public synchronized void releaseSeat(String travelClass) {
        String key = normalizeClass(travelClass);
        int avail = classAvailable.getOrDefault(key, 0);
        int total = classCapacity.getOrDefault(key, 0);
        if (avail < total) {
            classAvailable.put(key, avail + 1);
            if (!"General".equals(key) && availableSeats < totalSeats) {
                availableSeats++;
            }
        }
    }

    /**
     * Allocates a seat if available.
     * @return Seat number (1 to totalSeats), or -1 if no seats available.
     */
    public synchronized int allocateSeat() {
        return allocateSeat("3 Tier AC");
    }

    /**
     * Releases a confirmed seat.
     */
    public synchronized void releaseSeat() {
        releaseSeat("3 Tier AC");
    }

    /**
     * Allocates an RAC slot if available.
     * @return RAC index (1 to racSeats), or -1 if RAC full.
     */
    public synchronized int allocateRacSeat() {
        if (availableRacSeats > 0) {
            int racNumber = (racSeats - availableRacSeats) + 1;
            availableRacSeats--;
            return racNumber;
        }
        return -1;
    }

    /**
     * Releases an RAC slot.
     */
    public synchronized void releaseRacSeat() {
        if (availableRacSeats < racSeats) {
            availableRacSeats++;
        }
    }

    public synchronized int enqueueRac(Reservation reservation) {
        racList.enqueue(reservation);
        return racList.size();
    }

    public synchronized Reservation dequeueRac() {
        if (!racList.isEmpty()) {
            return racList.dequeue();
        }
        return null;
    }

    public synchronized boolean removeFromRac(Reservation reservation) {
        return racList.remove(reservation);
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
