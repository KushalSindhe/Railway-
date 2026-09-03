package com.railway.model;

import java.util.Objects;

/**
 * Represents a direct railway track connecting two stations with a specified distance.
 * Corresponds to an Edge in the Railway Network Graph.
 */
public class RouteEdge {
    private final Station source;
    private final Station destination;
    private final double distanceKm;

    public RouteEdge(Station source, Station destination, double distanceKm) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("Source and destination stations cannot be null.");
        }
        if (source.equals(destination)) {
            throw new IllegalArgumentException("Source and destination stations cannot be identical.");
        }
        if (distanceKm <= 0) {
            throw new IllegalArgumentException("Track distance must be strictly positive.");
        }
        this.source = source;
        this.destination = destination;
        this.distanceKm = distanceKm;
    }

    public Station getSource() {
        return source;
    }

    public Station getDestination() {
        return destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteEdge routeEdge = (RouteEdge) o;
        return Objects.equals(source, routeEdge.source) &&
               Objects.equals(destination, routeEdge.destination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, destination);
    }

    @Override
    public String toString() {
        return source.getId() + " <--> " + destination.getId() + " (" + distanceKm + " km)";
    }
}
