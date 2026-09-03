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
        this.trainMap = new LinkedHashMap<>();
    }

    /**
     * Adds a new train to the fleet.
     * Enforces unique train ID and validates stations.
     */
    public synchronized Train addTrain(String trainId, String name, String sourceId, String destId,
                                       List<String> stopStationIds, int totalSeats, double farePerKm) {
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

        Train train = new Train(cleanId, name, source, destination, routeStops, totalSeats, farePerKm);
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
}
