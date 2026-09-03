package com.railway.model;

import java.util.Objects;

/**
 * Represents a Railway Station (Node in the Railway Network Graph).
 * Station IDs must be unique across the railway network.
 */
public class Station implements Comparable<Station> {
    private final String id;
    private final String name;
    private final String state;
    private final String district;
    private final String zone;
    private final double mapX;
    private final double mapY;

    public Station(String id, String name, String state, String district, String zone, double mapX, double mapY) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Station ID cannot be empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Station name cannot be empty.");
        }
        this.id = id.trim().toUpperCase();
        this.name = name.trim();
        this.state = (state != null && !state.trim().isEmpty()) ? state.trim() : "All India";
        this.district = (district != null && !district.trim().isEmpty()) ? district.trim() : this.name;
        this.zone = (zone != null && !zone.trim().isEmpty()) ? zone.trim() : "IR";
        this.mapX = mapX;
        this.mapY = mapY;
    }

    public Station(String id, String name) {
        this(id, name, "All India", name, "IR", 450.0, 240.0);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public String getDistrict() {
        return district;
    }

    public String getZone() {
        return zone;
    }

    public double getMapX() {
        return mapX;
    }

    public double getMapY() {
        return mapY;
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
