package com.railway.service;

import com.railway.dsa.DijkstraResult;
import com.railway.dsa.Graph;
import com.railway.dsa.SearchingAlgorithms;
import com.railway.dsa.SortingAlgorithms;
import com.railway.model.RouteEdge;
import com.railway.model.Station;

import java.util.*;

/**
 * Service managing Railway Stations and the Track Network Graph.
 * Executes Dijkstra's algorithm for shortest-path calculations and
 * DFS for alternative route exploration.
 */
public class RailwayNetworkService {
    private final Map<String, Station> stationMap;
    private final Graph<Station> networkGraph;
    private final List<RouteEdge> routeEdges;

    public RailwayNetworkService() {
        this.stationMap = new LinkedHashMap<>();
        this.networkGraph = new Graph<>();
        this.routeEdges = new ArrayList<>();
    }

    /**
     * Adds a new station to the railway network.
     * Enforces uniqueness of Station ID.
     */
    public synchronized Station addStation(String id, String name) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Station ID cannot be empty.");
        }
        String cleanId = id.trim().toUpperCase();
        if (stationMap.containsKey(cleanId)) {
            throw new IllegalArgumentException("Station with ID '" + cleanId + "' already exists.");
        }
        Station station = new Station(cleanId, name);
        stationMap.put(cleanId, station);
        networkGraph.addVertex(station);
        return station;
    }

    /**
     * Adds a track (bidirectional edge) between two stations.
     */
    public synchronized RouteEdge addTrack(String sourceId, String destId, double distanceKm) {
        Station src = getStation(sourceId);
        Station dst = getStation(destId);

        if (src == null) {
            throw new IllegalArgumentException("Source station '" + sourceId + "' not found.");
        }
        if (dst == null) {
            throw new IllegalArgumentException("Destination station '" + destId + "' not found.");
        }
        if (src.equals(dst)) {
            throw new IllegalArgumentException("Source and destination stations cannot be identical.");
        }
        if (distanceKm <= 0) {
            throw new IllegalArgumentException("Track distance must be greater than 0 km.");
        }

        networkGraph.addEdge(src, dst, distanceKm, true);
        RouteEdge edge = new RouteEdge(src, dst, distanceKm);
        routeEdges.add(edge);
        return edge;
    }

    /**
     * Retrieves station by ID.
     */
    public Station getStation(String id) {
        if (id == null) return null;
        return stationMap.get(id.trim().toUpperCase());
    }

    /**
     * Checks if a station ID exists.
     */
    public boolean hasStation(String id) {
        if (id == null) return false;
        return stationMap.containsKey(id.trim().toUpperCase());
    }

    /**
     * Returns all stations sorted alphabetically by ID.
     */
    public List<Station> getAllStations() {
        List<Station> stations = new ArrayList<>(stationMap.values());
        SortingAlgorithms.mergeSort(stations, Comparator.comparing(Station::getId));
        return stations;
    }

    /**
     * Returns all route edges (tracks).
     */
    public List<RouteEdge> getAllTracks() {
        return Collections.unmodifiableList(routeEdges);
    }

    /**
     * Searches stations by name or ID (substring match).
     */
    public List<Station> searchStations(String query) {
        return SearchingAlgorithms.searchBySubstring(
                new ArrayList<>(stationMap.values()),
                query,
                s -> s.getName() + " " + s.getId()
        );
    }

    /**
     * Finds the shortest route between two stations using Dijkstra's Algorithm on the railway graph.
     */
    public DijkstraResult<Station> findShortestRoute(String sourceId, String destId) {
        Station src = getStation(sourceId);
        Station dst = getStation(destId);

        if (src == null || dst == null) {
            return DijkstraResult.unreachable();
        }

        return networkGraph.findShortestPath(src, dst);
    }

    /**
     * Finds alternative routes between two stations using DFS traversal.
     */
    public List<List<Station>> findAlternativeRoutes(String sourceId, String destId, int maxHops) {
        Station src = getStation(sourceId);
        Station dst = getStation(destId);

        if (src == null || dst == null) {
            return Collections.emptyList();
        }

        return networkGraph.findAllPaths(src, dst, maxHops);
    }

    /**
     * Computes distance for a sequence of stations.
     */
    public double getRouteDistance(List<Station> path) {
        return networkGraph.calculatePathDistance(path);
    }

    public Graph<Station> getNetworkGraph() {
        return networkGraph;
    }
}
