package com.railway.service;

import com.railway.model.Train;
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
        // 1. Add Major Railway Stations Across Indian States with State, District, Zone, and SVG Map Coordinates
        // Northern India
        networkService.addStation("NDLS", "New Delhi", "Delhi", "Central Delhi", "NR", 380.0, 120.0);
        networkService.addStation("ASR", "Amritsar Junction", "Punjab", "Amritsar", "NR", 340.0, 80.0);
        networkService.addStation("CDG", "Chandigarh", "Chandigarh", "Chandigarh", "NR", 370.0, 95.0);
        networkService.addStation("JAT", "Jammu Tawi", "Jammu & Kashmir", "Jammu", "NR", 360.0, 45.0);

        // Western & Central India
        networkService.addStation("JP", "Jaipur Junction", "Rajasthan", "Jaipur", "NWR", 330.0, 160.0);
        networkService.addStation("ADI", "Ahmedabad Junction", "Gujarat", "Ahmedabad", "WR", 260.0, 220.0);
        networkService.addStation("ST", "Surat", "Gujarat", "Surat", "WR", 270.0, 260.0);
        networkService.addStation("BPL", "Bhopal Junction", "Madhya Pradesh", "Bhopal", "WCR", 400.0, 225.0);
        networkService.addStation("CSMT", "Mumbai CSMT", "Maharashtra", "Mumbai", "CR", 260.0, 315.0);
        networkService.addStation("PUNE", "Pune Junction", "Maharashtra", "Pune", "CR", 290.0, 335.0);
        networkService.addStation("NGP", "Nagpur Junction", "Maharashtra", "Nagpur", "CR", 440.0, 275.0);
        networkService.addStation("MAO", "Madgaon Junction", "Goa", "South Goa", "KR", 290.0, 385.0);

        // Eastern & North-Eastern India
        networkService.addStation("LKO", "Lucknow Charbagh", "Uttar Pradesh", "Lucknow", "NR", 480.0, 160.0);
        networkService.addStation("BSB", "Varanasi Junction", "Uttar Pradesh", "Varanasi", "NER", 540.0, 185.0);
        networkService.addStation("PNBE", "Patna Junction", "Bihar", "Patna", "ECR", 600.0, 185.0);
        networkService.addStation("HWH", "Howrah (Kolkata)", "West Bengal", "Howrah", "ER", 670.0, 240.0);
        networkService.addStation("BBS", "Bhubaneswar", "Odisha", "Khurda", "ECoR", 620.0, 290.0);
        networkService.addStation("GHY", "Guwahati", "Assam", "Kamrup", "NFR", 760.0, 175.0);

        // Southern India
        networkService.addStation("HYB", "Hyderabad Deccan", "Telangana", "Hyderabad", "SCR", 430.0, 345.0);
        networkService.addStation("BZA", "Vijayawada Junction", "Andhra Pradesh", "Krishna", "SCR", 480.0, 365.0);
        networkService.addStation("SBC", "KSR Bengaluru", "Karnataka", "Bengaluru", "SWR", 400.0, 420.0);
        networkService.addStation("MAS", "Chennai Central", "Tamil Nadu", "Chennai", "SR", 470.0, 415.0);
        networkService.addStation("ERS", "Ernakulam (Kochi)", "Kerala", "Ernakulam", "SR", 370.0, 465.0);
        networkService.addStation("TVC", "Thiruvananthapuram", "Kerala", "Thiruvananthapuram", "SR", 385.0, 485.0);

        // 2. Add Track Connections (Graph Edges with Accurate Distances in km)
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

        // New Tracks Connecting Extended Pan-India Network
        networkService.addTrack("NDLS", "LKO", 490.0);
        networkService.addTrack("LKO", "BSB", 300.0);
        networkService.addTrack("BSB", "PNBE", 230.0);
        networkService.addTrack("PNBE", "HWH", 535.0);
        networkService.addTrack("HWH", "GHY", 990.0);
        networkService.addTrack("HWH", "BBS", 440.0);
        networkService.addTrack("BBS", "BZA", 790.0);
        networkService.addTrack("BZA", "MAS", 430.0);
        networkService.addTrack("HYB", "BZA", 310.0);
        networkService.addTrack("SBC", "ERS", 580.0);
        networkService.addTrack("ERS", "TVC", 205.0);
        networkService.addTrack("PUNE", "MAO", 510.0);
        networkService.addTrack("MAO", "ERS", 690.0);
        networkService.addTrack("SBC", "MAS", 350.0);
        networkService.addTrack("CSMT", "NGP", 830.0);
        networkService.addTrack("NGP", "BZA", 610.0);
        networkService.addTrack("BPL", "LKO", 580.0);
        networkService.addTrack("NDLS", "JP", 300.0);
        networkService.addTrack("JP", "ADI", 620.0);
        networkService.addTrack("NDLS", "CDG", 260.0);
        networkService.addTrack("CDG", "ASR", 230.0);
        networkService.addTrack("ASR", "JAT", 210.0);

        // 3. Add Premier Trains with Scheduled Timings, Duration, and RAC/Emergency Quotas (Bidirectional Corridors)
        // Fleet uses standard 495 seats capacity (1A: 25, 2A: 50, 3A: 70, Sleeper: 150, General: 200)
        final int fleetCapacity = Train.TOTAL_CONFIGURED_SEATS; // 495 seats

        // Karnataka Express: NDLS ⇄ BPL ⇄ NGP ⇄ HYB ⇄ SBC
        trainService.addTrain("12627", "Karnataka Express", "NDLS", "SBC",
                Arrays.asList("NDLS", "BPL", "NGP", "HYB", "SBC"),
                fleetCapacity, 1.35, "20:20", "13:30", "41h 10m", 2, 2);
        trainService.addTrain("12628", "Karnataka Express (Return)", "SBC", "NDLS",
                Arrays.asList("SBC", "HYB", "NGP", "BPL", "NDLS"),
                fleetCapacity, 1.35, "19:20", "12:00", "40h 40m", 2, 2);

        // Mumbai Rajdhani: NDLS ⇄ JP ⇄ ADI ⇄ ST ⇄ CSMT
        trainService.addTrain("12952", "Mumbai Rajdhani Express", "NDLS", "CSMT",
                Arrays.asList("NDLS", "JP", "ADI", "ST", "CSMT"),
                fleetCapacity, 1.85, "16:55", "08:35", "15h 40m", 2, 2);
        trainService.addTrain("12951", "August Kranti Rajdhani", "CSMT", "NDLS",
                Arrays.asList("CSMT", "ST", "ADI", "JP", "NDLS"),
                fleetCapacity, 1.85, "17:05", "08:45", "15h 40m", 2, 2);

        // Grand Trunk Express: NDLS ⇄ BPL ⇄ NGP ⇄ BZA ⇄ MAS
        trainService.addTrain("12616", "Grand Trunk Express", "NDLS", "MAS",
                Arrays.asList("NDLS", "BPL", "NGP", "BZA", "MAS"),
                fleetCapacity, 1.40, "16:10", "04:30", "36h 20m", 2, 2);
        trainService.addTrain("12615", "Grand Trunk Express (Return)", "MAS", "NDLS",
                Arrays.asList("MAS", "BZA", "NGP", "BPL", "NDLS"),
                fleetCapacity, 1.40, "18:50", "06:30", "35h 40m", 2, 2);

        // Kerala Superfast Express: NDLS ⇄ BPL ⇄ NGP ⇄ BZA ⇄ SBC ⇄ ERS ⇄ TVC
        trainService.addTrain("12626", "Kerala Express", "NDLS", "TVC",
                Arrays.asList("NDLS", "BPL", "NGP", "BZA", "SBC", "ERS", "TVC"),
                fleetCapacity, 1.42, "20:10", "14:30", "42h 20m", 2, 2);
        trainService.addTrain("12625", "Kerala Express (Return)", "TVC", "NDLS",
                Arrays.asList("TVC", "ERS", "SBC", "BZA", "NGP", "BPL", "NDLS"),
                fleetCapacity, 1.42, "11:15", "04:55", "41h 40m", 2, 2);

        // Howrah Mail: MAS ⇄ BZA ⇄ BBS ⇄ HWH
        trainService.addTrain("12840", "Howrah Mail", "MAS", "HWH",
                Arrays.asList("MAS", "BZA", "BBS", "HWH"),
                fleetCapacity, 1.40, "19:00", "03:50", "32h 50m", 2, 2);
        trainService.addTrain("12839", "Howrah Mail (Return)", "HWH", "MAS",
                Arrays.asList("HWH", "BBS", "BZA", "MAS"),
                fleetCapacity, 1.40, "23:55", "07:10", "31h 15m", 2, 2);

        // Vande Bharat Express: NDLS ⇄ LKO ⇄ BSB
        trainService.addTrain("22436", "Vande Bharat Express", "NDLS", "BSB",
                Arrays.asList("NDLS", "LKO", "BSB"),
                fleetCapacity, 2.10, "06:00", "14:00", "8h 00m", 2, 2);
        trainService.addTrain("22435", "Vande Bharat Express (Return)", "BSB", "NDLS",
                Arrays.asList("BSB", "LKO", "NDLS"),
                fleetCapacity, 2.10, "15:00", "23:00", "8h 00m", 2, 2);

        // Howrah Rajdhani / Poorva Express: NDLS ⇄ LKO ⇄ BSB ⇄ PNBE ⇄ HWH
        trainService.addTrain("12302", "Howrah Rajdhani Express", "NDLS", "HWH",
                Arrays.asList("NDLS", "LKO", "BSB", "PNBE", "HWH"),
                fleetCapacity, 1.90, "16:55", "09:55", "17h 00m", 2, 2);
        trainService.addTrain("12301", "Howrah Rajdhani (Return)", "HWH", "NDLS",
                Arrays.asList("HWH", "PNBE", "BSB", "LKO", "NDLS"),
                fleetCapacity, 1.90, "16:50", "10:05", "17h 15m", 2, 2);

        // Saraighat Express: HWH ⇄ GHY
        trainService.addTrain("12345", "Saraighat Express", "HWH", "GHY",
                Arrays.asList("HWH", "GHY"),
                fleetCapacity, 1.35, "15:55", "10:05", "18h 10m", 2, 2);
        trainService.addTrain("12346", "Saraighat Express (Return)", "GHY", "HWH",
                Arrays.asList("GHY", "HWH"),
                fleetCapacity, 1.35, "12:20", "05:15", "16h 55m", 2, 2);

        // Northeast Express: NDLS ⇄ LKO ⇄ BSB ⇄ PNBE ⇄ GHY
        trainService.addTrain("12506", "Northeast Express", "NDLS", "GHY",
                Arrays.asList("NDLS", "LKO", "BSB", "PNBE", "GHY"),
                fleetCapacity, 1.38, "07:40", "16:50", "33h 10m", 2, 2);
        trainService.addTrain("12505", "Northeast Express (Return)", "GHY", "NDLS",
                Arrays.asList("GHY", "PNBE", "BSB", "LKO", "NDLS"),
                fleetCapacity, 1.38, "12:40", "21:50", "33h 10m", 2, 2);

        // Deccan Queen: CSMT ⇄ PUNE
        trainService.addTrain("12124", "Deccan Queen", "CSMT", "PUNE",
                Arrays.asList("CSMT", "PUNE"),
                fleetCapacity, 1.15, "07:10", "10:25", "3h 15m", 2, 2);
        trainService.addTrain("12123", "Deccan Queen (Return)", "PUNE", "CSMT",
                Arrays.asList("PUNE", "CSMT"),
                fleetCapacity, 1.15, "17:15", "20:25", "3h 10m", 2, 2);

        // Konkan Kanya Express: CSMT ⇄ PUNE ⇄ MAO
        trainService.addTrain("10111", "Konkan Kanya Express", "CSMT", "MAO",
                Arrays.asList("CSMT", "PUNE", "MAO"),
                fleetCapacity, 1.32, "23:05", "10:45", "11h 40m", 2, 2);
        trainService.addTrain("10112", "Konkan Kanya Express (Return)", "MAO", "CSMT",
                Arrays.asList("MAO", "PUNE", "CSMT"),
                fleetCapacity, 1.32, "18:00", "05:40", "11h 40m", 2, 2);

        // Shatabdi Express: MAS ⇄ SBC
        trainService.addTrain("12008", "Shatabdi Express", "MAS", "SBC",
                Arrays.asList("MAS", "SBC"),
                fleetCapacity, 1.60, "06:00", "11:00", "5h 00m", 2, 2);
        trainService.addTrain("12007", "Shatabdi Express (Return)", "SBC", "MAS",
                Arrays.asList("SBC", "MAS"),
                fleetCapacity, 1.60, "16:25", "21:30", "5h 05m", 2, 2);

        // Netravati Express: CSMT ⇄ PUNE ⇄ MAO ⇄ ERS ⇄ TVC
        trainService.addTrain("16345", "Netravati Express", "CSMT", "TVC",
                Arrays.asList("CSMT", "PUNE", "MAO", "ERS", "TVC"),
                fleetCapacity, 1.36, "11:40", "18:05", "30h 25m", 2, 2);
        trainService.addTrain("16346", "Netravati Express (Return)", "TVC", "CSMT",
                Arrays.asList("TVC", "ERS", "MAO", "PUNE", "CSMT"),
                fleetCapacity, 1.36, "09:15", "17:05", "31h 50m", 2, 2);

        // Uttar Sampark Kranti: NDLS ⇄ CDG ⇄ ASR ⇄ JAT
        trainService.addTrain("12445", "Uttar Sampark Kranti", "NDLS", "JAT",
                Arrays.asList("NDLS", "CDG", "ASR", "JAT"),
                fleetCapacity, 1.40, "20:50", "06:05", "9h 15m", 2, 2);
        trainService.addTrain("12446", "Uttar Sampark Kranti (Return)", "JAT", "NDLS",
                Arrays.asList("JAT", "ASR", "CDG", "NDLS"),
                fleetCapacity, 1.40, "19:45", "05:10", "9h 25m", 2, 2);

        // Coromandel Express: HWH ⇄ BBS ⇄ BZA ⇄ MAS
        trainService.addTrain("12841", "Coromandel Express", "HWH", "MAS",
                Arrays.asList("HWH", "BBS", "BZA", "MAS"),
                fleetCapacity, 1.45, "15:20", "17:00", "25h 40m", 2, 2);
        trainService.addTrain("12842", "Coromandel Express (Return)", "MAS", "HWH",
                Arrays.asList("MAS", "BZA", "BBS", "HWH"),
                fleetCapacity, 1.45, "07:00", "08:30", "25h 30m", 2, 2);

        // Telangana Express: NDLS ⇄ BPL ⇄ NGP ⇄ HYB
        trainService.addTrain("12724", "Telangana Express", "NDLS", "HYB",
                Arrays.asList("NDLS", "BPL", "NGP", "HYB"),
                fleetCapacity, 1.30, "16:00", "17:10", "25h 10m", 2, 2);
        trainService.addTrain("12723", "Telangana Express (Return)", "HYB", "NDLS",
                Arrays.asList("HYB", "NGP", "BPL", "NDLS"),
                fleetCapacity, 1.30, "06:00", "07:40", "25h 40m", 2, 2);

        // Gitanjali Express: CSMT ⇄ NGP ⇄ BBS ⇄ HWH
        trainService.addTrain("12859", "Gitanjali Express", "CSMT", "HWH",
                Arrays.asList("CSMT", "NGP", "BBS", "HWH"),
                fleetCapacity, 1.38, "06:00", "12:30", "30h 30m", 2, 2);
        trainService.addTrain("12860", "Gitanjali Express (Return)", "HWH", "CSMT",
                Arrays.asList("HWH", "BBS", "NGP", "CSMT"),
                fleetCapacity, 1.38, "14:05", "21:20", "31h 15m", 2, 2);

        // Gujarat Mail: ADI ⇄ ST ⇄ CSMT
        trainService.addTrain("12902", "Gujarat Mail", "ADI", "CSMT",
                Arrays.asList("ADI", "ST", "CSMT"),
                fleetCapacity, 1.35, "22:50", "06:15", "7h 25m", 2, 2);
        trainService.addTrain("12901", "Gujarat Mail (Return)", "CSMT", "ADI",
                Arrays.asList("CSMT", "ST", "ADI"),
                fleetCapacity, 1.35, "21:40", "05:15", "7h 35m", 2, 2);

        // 4. Create Initial Sample Bookings on Karnataka Express
        // Seat #1 Confirmed (Linked to demo user 'passenger')
        reservationService.bookTicket("12627", "Aarav Sharma", 32, "M", "GOV-88210", "NDLS", "SBC", "3 Tier AC", "passenger");

        // 5. Stochastic Seat Inventory & Waiting List Initialization
        // Dynamically configures realistic confirmed seat availability & waiting lists across the 36 trains
        trainService.randomizeAllTrainInventories();
    }
}
