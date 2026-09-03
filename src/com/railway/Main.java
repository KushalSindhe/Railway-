package com.railway;

import com.railway.service.RailwayNetworkService;
import com.railway.service.ReservationService;
import com.railway.service.SampleDataLoader;
import com.railway.service.TrainService;
import com.railway.ui.ConsoleUI;
import com.railway.web.EmbeddedWebServer;

/**
 * Main application launcher for the Railway Reservation & Route Management System.
 */
public class Main {
    private static final int DEFAULT_WEB_PORT = 8080;

    public static void main(String[] args) {
        System.out.println("Initializing Railway Reservation & Route Management System...");

        // 1. Initialize core services
        RailwayNetworkService networkService = new RailwayNetworkService();
        TrainService trainService = new TrainService(networkService);
        ReservationService reservationService = new ReservationService(networkService, trainService);
        com.railway.service.AuthService authService = new com.railway.service.AuthService();

        // 2. Bootstrap realistic sample network, fleet, and bookings
        SampleDataLoader.loadSampleData(networkService, trainService, reservationService);
        System.out.println("Sample stations, track graph, and trains loaded successfully.");

        // 3. Start embedded web dashboard in background
        EmbeddedWebServer webServer = new EmbeddedWebServer(DEFAULT_WEB_PORT, networkService, trainService, reservationService, authService);
        webServer.start();
        System.out.println("Built-in Web Dashboard live at: http://localhost:" + DEFAULT_WEB_PORT);

        boolean webOnly = false;
        for (String arg : args) {
            if ("--web-only".equalsIgnoreCase(arg) || "--web".equalsIgnoreCase(arg)) {
                webOnly = true;
                break;
            }
        }

        if (webOnly) {
            System.out.println("Running in dedicated Web Server Daemon mode...");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                webServer.stop();
            }
            return;
        }

        // 4. Start interactive Console UI
        ConsoleUI consoleUI = new ConsoleUI(networkService, trainService, reservationService, authService, DEFAULT_WEB_PORT);
        try {
            consoleUI.start();
        } finally {
            // Clean up resources on exit
            webServer.stop();
        }
    }
}
