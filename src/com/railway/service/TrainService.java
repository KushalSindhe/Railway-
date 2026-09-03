package com.railway.service;

import com.railway.dsa.SearchingAlgorithms;
import com.railway.dsa.SortingAlgorithms;
import com.railway.model.Station;
import com.railway.model.Train;

import java.util.*;

/**
 * Service managing Trains in the railway system.
 * Demonstrates:
 * - Train addition and validation (unique Train ID).
 * - Multi-criteria train searching.
 * - Logarithmic Binary Search for train lookup by ID.
 * - MergeSort and QuickSort implementations for ranking trains by seats, ID, and name.
 */
public class TrainService {
    private final Map<String, Train> trainMap;
    private final RailwayNetworkService networkService;

    public TrainService(RailwayNetworkService networkService) {
        this.networkService = networkService;
        this.trainMap = new java.util.concurrent.ConcurrentHashMap<>();
    }

    /**
     * Adds a new train to the fleet with default timings and 2 RAC seats.
     */
    public synchronized Train addTrain(String trainId, String name, String sourceId, String destId,
                                       List<String> stopStationIds, int totalSeats, double farePerKm) {
        int rac = totalSeats <= 5 ? 0 : 2;
        int eq = totalSeats <= 5 ? 0 : 2;
        return addTrain(trainId, name, sourceId, destId, stopStationIds, totalSeats, farePerKm, "06:00", "14:30", "8h 30m", rac, eq);
    }

    /**
     * Adds a new train with scheduled departure/arrival timings, journey duration, RAC quota, and emergency seats.
     */
    public synchronized Train addTrain(String trainId, String name, String sourceId, String destId,
                                       List<String> stopStationIds, int totalSeats, double farePerKm,
                                       String departureTime, String arrivalTime, String duration,
                                       int racSeats, int emergencySeats) {
        return addTrain(trainId, name, sourceId, destId, stopStationIds, totalSeats, farePerKm,
                departureTime, arrivalTime, duration, racSeats, emergencySeats,
                Train.SEATS_1A, Train.SEATS_2A, Train.SEATS_3A, Train.SEATS_SL, Train.SEATS_GN);
    }

    /**
     * Adds a new train with explicit per-class seat capacities (1A, 2A, 3A, SL, GN).
     */
    public synchronized Train addTrain(String trainId, String name, String sourceId, String destId,
                                       List<String> stopStationIds, int totalSeats, double farePerKm,
                                       String departureTime, String arrivalTime, String duration,
                                       int racSeats, int emergencySeats,
                                       int seats1A, int seats2A, int seats3A, int seatsSL, int seatsGN) {
        if (trainId == null || trainId.trim().isEmpty()) {
            throw new IllegalArgumentException("Train ID cannot be empty.");
        }
        String cleanId = trainId.trim();
        if (trainMap.containsKey(cleanId)) {
            throw new IllegalArgumentException("Train with ID '" + cleanId + "' already exists.");
        }

        Station source = networkService.getStation(sourceId);
        Station destination = networkService.getStation(destId);

        if (source == null) {
            throw new IllegalArgumentException("Source station '" + sourceId + "' does not exist.");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination station '" + destId + "' does not exist.");
        }
        if (source.equals(destination)) {
            throw new IllegalArgumentException("Source and destination cannot be the same station.");
        }

        List<Station> routeStops = new ArrayList<>();
        routeStops.add(source);

        if (stopStationIds != null) {
            for (String stopId : stopStationIds) {
                Station stopStation = networkService.getStation(stopId);
                if (stopStation == null) {
                    throw new IllegalArgumentException("Intermediate station '" + stopId + "' does not exist.");
                }
                if (!stopStation.equals(source) && !stopStation.equals(destination) && !routeStops.contains(stopStation)) {
                    routeStops.add(stopStation);
                }
            }
        }
        if (!routeStops.contains(destination)) {
            routeStops.add(destination);
        }

        Train train = new Train(cleanId, name, source, destination, routeStops, totalSeats, farePerKm,
                departureTime, arrivalTime, duration, racSeats, emergencySeats,
                seats1A, seats2A, seats3A, seatsSL, seatsGN);
        trainMap.put(cleanId, train);
        return train;
    }

    /**
     * Retrieves a train by ID using Binary Search.
     * Demonstrates O(log N) search on an ordered fleet list.
     */
    public Train getTrainById(String trainId) {
        if (trainId == null) return null;
        List<Train> sortedTrains = getAllTrainsSortedById();
        int index = SearchingAlgorithms.binarySearch(sortedTrains, trainId.trim(), Train::getId);
        return index >= 0 ? sortedTrains.get(index) : null;
    }

    /**
     * Fast O(1) hash map lookup as alternative to binary search.
     */
    public Train getTrainDirect(String trainId) {
        if (trainId == null) return null;
        return trainMap.get(trainId.trim());
    }

    /**
     * Searches for all trains that operate between source and destination stations.
     */
    public List<Train> searchTrains(String sourceId, String destId) {
        Station src = networkService.getStation(sourceId);
        Station dst = networkService.getStation(destId);

        if (src == null || dst == null) {
            return Collections.emptyList();
        }

        List<Train> matchingTrains = new ArrayList<>();
        for (Train train : trainMap.values()) {
            if (train.servesRoute(src, dst)) {
                matchingTrains.add(train);
            }
        }

        // By default, sort matching trains by available seats descending (using MergeSort)
        SortingAlgorithms.mergeSort(matchingTrains, (t1, t2) -> Integer.compare(t2.getAvailableSeats(), t1.getAvailableSeats()));
        return matchingTrains;
    }

    /**
     * Resolves an existing direct train or dynamically generates a corridor intercity express
     * along the shortest railway track path connecting source and destination stations.
     */
    public synchronized Train findOrCreateCorridorTrain(Station src, Station dst) {
        if (src == null || dst == null || src.equals(dst)) return null;

        List<Train> existing = searchTrains(src.getId(), dst.getId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        com.railway.dsa.DijkstraResult<Station> result = networkService.findShortestRoute(src.getId(), dst.getId());
        List<Station> path = result.getPath();
        if (path == null || path.size() < 2) {
            path = Arrays.asList(src, dst);
        }

        String trainId = "EXP-" + src.getId() + "-" + dst.getId();
        if (trainMap.containsKey(trainId)) {
            return trainMap.get(trainId);
        }

        double distance = result.isReachable() ? result.getTotalDistance() : 500.0;
        int speedKmph = 75;
        double hoursNeeded = distance / speedKmph;
        int hours = (int) hoursNeeded;
        int mins = (int) ((hoursNeeded - hours) * 60);
        String durationStr = hours + "h " + (mins < 10 ? "0" + mins : mins) + "m";

        String trainName = "Bharat " + src.getName() + " - " + dst.getName() + " Superfast";
        List<String> stopCodes = new ArrayList<>();
        for (Station s : path) {
            stopCodes.add(s.getId());
        }

        return addTrain(
                trainId,
                trainName,
                src.getId(),
                dst.getId(),
                stopCodes,
                250,
                1.45,
                "07:00",
                String.format("%02d:%02d", (7 + hours) % 24, mins),
                durationStr,
                25,
                10
        );
    }

    /**
     * Returns all trains in the fleet.
     */
    public List<Train> getAllTrains() {
        return new ArrayList<>(trainMap.values());
    }

    /**
     * Returns all trains sorted by Train ID using QuickSort (O(N log N)).
     */
    public List<Train> getAllTrainsSortedById() {
        List<Train> trains = new ArrayList<>(trainMap.values());
        SortingAlgorithms.quickSort(trains, Comparator.comparing(Train::getId));
        return trains;
    }

    /**
     * Returns all trains sorted by Available Seats descending using MergeSort (O(N log N)).
     */
    public List<Train> getAllTrainsSortedByAvailableSeats() {
        List<Train> trains = new ArrayList<>(trainMap.values());
        SortingAlgorithms.mergeSort(trains, (t1, t2) -> Integer.compare(t2.getAvailableSeats(), t1.getAvailableSeats()));
        return trains;
    }

    /**
     * Returns all trains sorted alphabetically by Name using MergeSort (O(N log N)).
     */
    public List<Train> getAllTrainsSortedByName() {
        List<Train> trains = new ArrayList<>(trainMap.values());
        SortingAlgorithms.mergeSort(trains, Comparator.comparing(Train::getName));
        return trains;
    }

    /**
     * Searches trains by name or number with prefix/substring matching.
     */
    public List<Train> searchTrainsByText(String query) {
        return SearchingAlgorithms.searchBySubstring(
                new ArrayList<>(trainMap.values()),
                query,
                t -> t.getId() + " " + t.getName()
        );
    }

    /**
     * Retrieves a train by its unique ID.
     */
    public Train getTrain(String trainId) {
        if (trainId == null) return null;
        return trainMap.get(trainId.trim());
    }

    /**
     * Randomly reconfigures confirmed available seats and waitlist numbers across all trains
     * to simulate a busy, dynamic national railway network with diverse booking demands.
     */
    public synchronized void randomizeAllTrainInventories() {
        java.util.Random rand = new java.util.Random();
        for (Train train : trainMap.values()) {
            train.randomizeSeatAndWaitlist(rand);
        }
    }

    /**
     * Incrementally fluctuates confirmed seats and waitlists to simulate real-time bookings and cancellations.
     */
    public synchronized void fluctuateAllTrainInventories() {
        java.util.Random rand = new java.util.Random();
        for (Train train : trainMap.values()) {
            train.fluctuateInventory(rand);
        }
    }
}
