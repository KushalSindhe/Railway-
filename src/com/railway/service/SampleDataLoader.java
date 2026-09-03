package com.railway.service;

import java.util.Arrays;

/**
 * Initializes and populates realistic sample data for instant demonstration.
 * Includes major Indian railway junctions, track connections with distances,
 * express trains with configured seat inventories, and initial bookings.
 */
public class SampleDataLoader {

    public static void loadSampleData(RailwayNetworkService networkService,
                                      TrainService trainService,
                                      ReservationService reservationService) {
        // 1. Add Stations
        networkService.addStation("NDLS", "New Delhi");
        networkService.addStation("CSMT", "Mumbai CSMT");
        networkService.addStation("SBC", "KSR Bengaluru");
        networkService.addStation("MAS", "Chennai Central");
        networkService.addStation("HWH", "Howrah (Kolkata)");
        networkService.addStation("HYB", "Hyderabad Deccan");
        networkService.addStation("PUNE", "Pune Junction");
        networkService.addStation("ADI", "Ahmedabad Junction");
        networkService.addStation("BPL", "Bhopal Junction");
        networkService.addStation("NGP", "Nagpur Junction");

        // 2. Add Track Connections (Graph Edges with Distances in km)
        networkService.addTrack("NDLS", "BPL", 700.0);
        networkService.addTrack("BPL", "NGP", 390.0);
        networkService.addTrack("NGP", "HYB", 500.0);
        networkService.addTrack("HYB", "SBC", 620.0);

        networkService.addTrack("NDLS", "ADI", 930.0);
        networkService.addTrack("ADI", "CSMT", 490.0);
        networkService.addTrack("CSMT", "PUNE", 190.0);
        networkService.addTrack("PUNE", "SBC", 840.0);

        networkService.addTrack("SBC", "MAS", 360.0);
        networkService.addTrack("NGP", "HWH", 1120.0);
        networkService.addTrack("MAS", "HWH", 1660.0);
        networkService.addTrack("BPL", "CSMT", 830.0);

        // 3. Add Trains (Small seat capacities chosen intentionally for seamless Waitlist demonstration)
        // Karnataka Express: NDLS -> BPL -> NGP -> HYB -> SBC
        trainService.addTrain(
                "12627",
                "Karnataka Express",
                "NDLS",
                "SBC",
                Arrays.asList("NDLS", "BPL", "NGP", "HYB", "SBC"),
                2, // Total seats = 2 to quickly trigger and demonstrate Waiting List!
                1.35
        );

        // Mumbai Rajdhani: NDLS -> ADI -> CSMT
        trainService.addTrain(
                "12952",
                "Mumbai Rajdhani Express",
                "NDLS",
                "CSMT",
                Arrays.asList("NDLS", "ADI", "CSMT"),
                3,
                1.85
        );

        // Shatabdi Express: MAS -> SBC
        trainService.addTrain(
                "12008",
                "Shatabdi Express",
                "MAS",
                "SBC",
                Arrays.asList("MAS", "SBC"),
                4,
                1.60
        );

        // Deccan Queen: CSMT -> PUNE
        trainService.addTrain(
                "12124",
                "Deccan Queen",
                "CSMT",
                "PUNE",
                Arrays.asList("CSMT", "PUNE"),
                5,
                1.15
        );

        // Howrah Mail: MAS -> HWH
        trainService.addTrain(
                "12840",
                "Howrah Mail",
                "MAS",
                "HWH",
                Arrays.asList("MAS", "HWH"),
                3,
                1.40
        );

        // Telangana Express: NDLS -> HYB
        trainService.addTrain(
                "12724",
                "Telangana Express",
                "NDLS",
                "HYB",
                Arrays.asList("NDLS", "BPL", "NGP", "HYB"),
                3,
                1.30
        );

        // 4. Create Initial Sample Bookings on Karnataka Express
        // Seat #1 Confirmed (Linked to demo user 'passenger')
        reservationService.bookTicket("12627", "Aarav Sharma", 32, "M", "GOV-88210", "NDLS", "SBC", "3 Tier AC", "passenger");
        // Train 12627 now has 1 seat remaining out of 2.
    }
}
