package com.railway.model;

import java.util.Objects;

/**
 * Represents a Railway Station (Node in the Railway Network Graph).
 * Station IDs must be unique across the railway network.
 */
public class Station implements Comparable<Station> {
    private final String id;
    private final String name;

    public Station(String id, String name) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Station ID cannot be empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Station name cannot be empty.");
        }
        this.id = id.trim().toUpperCase();
        this.name = name.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Station station = (Station) o;
        return Objects.equals(id, station.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public int compareTo(Station other) {
        return this.id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return name + " [" + id + "]";
    }
}
