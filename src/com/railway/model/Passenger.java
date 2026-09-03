package com.railway.model;

import java.util.Objects;

/**
 * Represents a Railway Passenger with personal identification.
 */
public class Passenger {
    private final String id;
    private final String name;
    private final int age;
    private final String gender;

    public Passenger(String id, String name, int age, String gender) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Passenger ID cannot be empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Passenger name cannot be empty.");
        }
        if (age <= 0 || age > 120) {
            throw new IllegalArgumentException("Passenger age must be between 1 and 120.");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.age = age;
        this.gender = (gender != null && !gender.trim().isEmpty()) ? gender.trim().toUpperCase() : "O";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public String getGender() {
        return gender;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Passenger passenger = (Passenger) o;
        return Objects.equals(id, passenger.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " (" + gender + ", " + age + " yrs, ID: " + id + ")";
    }
}
