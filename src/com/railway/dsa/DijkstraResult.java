package com.railway.dsa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the result of running Dijkstra's Algorithm on a Railway Graph.
 * Holds the ordered path of nodes (stations) and the total accumulated distance (km).
 *
 * @param <T> The vertex type (e.g. Station or String).
 */
public class DijkstraResult<T> {
    private final List<T> path;
    private final double totalDistance;
    private final boolean reachable;

    public DijkstraResult(List<T> path, double totalDistance, boolean reachable) {
        this.path = path != null ? path : Collections.emptyList();
        this.totalDistance = totalDistance;
        this.reachable = reachable;
    }

    public static <T> DijkstraResult<T> unreachable() {
        return new DijkstraResult<>(new ArrayList<>(), Double.POSITIVE_INFINITY, false);
    }

    public List<T> getPath() {
        return path;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public boolean isReachable() {
        return reachable;
    }

    @Override
    public String toString() {
        if (!reachable) {
            return "No reachable route found.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            sb.append(path.get(i));
            if (i < path.size() - 1) {
                sb.append(" -> ");
            }
        }
        sb.append(String.format(" (Total Distance: %.1f km)", totalDistance));
        return sb.toString();
    }
}
