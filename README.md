# 🚆 Railway Reservation & Route Management System

A production-grade, object-oriented Java console application designed to model a national railway network, schedule trains, calculate shortest travel paths via Graph algorithms, book and cancel tickets, and manage dynamic waiting lists via custom FIFO queues.

Built with **Pure Core Java 21** (Zero External Dependencies) with an optional built-in zero-dependency HTTP server and visual interactive web dashboard.

---

## 📌 Project Overview & Highlights

- **Dual-User Architecture**:
  - **Passenger Portal**: Search trains by route, inspect real-time seat availability & fare, book tickets, cancel tickets (triggers auto-promotion), check PNR status, and compute shortest paths across the national railway network.
  - **Clerk / Administrator Portal**: Add new stations (enforcing unique station codes), lay track connections (graph edges with distances), add new trains, inspect fleet sorted by seats or train ID, and view passenger manifests.
- **Custom DSA Implemented from Scratch**:
  - **Graph (Adjacency List)**: Models stations as vertices and railway tracks as weighted edges ($O(V + E)$).
  - **Dijkstra's Algorithm**: Min-Priority Queue implementation to compute optimal routes and total mileage ($O((V+E)\log V)$).
  - **Custom FIFO Queue**: Linked-node Queue ADT driving the train Waiting List ($O(1)$ enqueue and dequeue).
  - **Custom Sorting Algorithms**: `MergeSort` (stable $O(N \log N)$) and `QuickSort` ($O(N \log N)$ average) for multi-criteria sorting.
  - **Custom Searching Algorithms**: Iterative `BinarySearch` ($O(\log N)$) on sorted fleets and linear prefix searching.
- **Strict Business Logic & Invariants**:
  - Unique station codes (e.g., `NDLS`, `CSMT`, `SBC`) and train numbers (e.g., `12627`).
  - Automatic escalation: When a confirmed ticket is cancelled, the head of the FIFO queue is automatically dequeued and promoted to `CONFIRMED` with the released seat!
  - Robust input sanitization preventing application crashes on invalid inputs.
- **Bonus Feature**:
  - Built-in embedded HTTP Web Server (`com.sun.net.httpserver.HttpServer`) on `http://localhost:8080` with a modern Single-Page Application visual dashboard.

---

## 📂 Project Architecture

```
railway-reservation-system/
├── src/
│   └── com/
│       └── railway/
│           ├── Main.java                        # System bootstrap & coordinator
│           ├── AutomatedVerificationTest.java   # Automated unit test suite
│           ├── dsa/                             # Custom Data Structures & Algorithms
│           │   ├── CustomQueue.java             # Linked-node FIFO Queue ADT
│           │   ├── Graph.java                   # Adjacency List Graph & Dijkstra
│           │   ├── DijkstraResult.java          # Dijkstra route & distance model
│           │   ├── SortingAlgorithms.java       # Custom MergeSort & QuickSort
│           │   └── SearchingAlgorithms.java     # Binary Search & Prefix Search
│           ├── model/                           # Domain Entities
│           │   ├── Station.java                 # Station node
│           │   ├── RouteEdge.java               # Track connection
│           │   ├── Train.java                   # Train with waitlist queue
│           │   ├── Passenger.java               # Passenger details
│           │   ├── Reservation.java             # Ticket & PNR details
│           │   └── BookingStatus.java           # CNF, WL, CANCELLED enum
│           ├── service/                         # Business Logic
│           │   ├── RailwayNetworkService.java   # Graph operations & routing
│           │   ├── TrainService.java            # Fleet search & sorting
│           │   ├── ReservationService.java      # Bookings, cancellations, auto-promotion
│           │   └── SampleDataLoader.java        # Pre-loaded realistic dataset
│           ├── ui/                              # User Interface
│           │   ├── ConsoleUI.java               # Main menu & styling
│           │   ├── PassengerMenu.java           # Passenger workflow
│           │   └── AdminMenu.java               # Administrator workflow
│           └── web/                             # Optional Web Server
│               └── EmbeddedWebServer.java       # Zero-dependency HTTP Server & Dashboard
├── bin/                                         # Compiled bytecode
├── run.bat                                      # 1-click Windows compilation & launch script
├── README.md                                    # Documentation
└── VIVA_GUIDE.md                                # 100-mark assessment preparation guide
```

---

## 🚀 How to Run

### Option 1: Double-Click Batch File (Windows)
Double-click `run.bat` in the project root directory. It will compile all source files and launch the interactive console interface.

### Option 2: Command Line (PowerShell or Command Prompt)
```powershell
# Navigate to project root
cd "C:\Users\Kushal Sindhe\.gemini\antigravity\scratch\railway-reservation-system"

# Compile all source files
javac -encoding UTF-8 -d bin src\com\railway\dsa\*.java src\com\railway\model\*.java src\com\railway\service\*.java src\com\railway\ui\*.java src\com\railway\web\*.java src\com\railway\*.java

# Launch the Application
java -cp bin com.railway.Main
```

### Option 3: Run Automated Test Suite
To verify all DSA algorithms and business rules:
```powershell
java -cp bin com.railway.AutomatedVerificationTest
```

---

## 🌐 Built-in Web Dashboard
Once the application starts, open your browser and navigate to:
```
http://localhost:8080/
```
The dashboard allows visual calculation of shortest paths via Dijkstra, real-time train seat tracking, instant ticket booking, and PNR status checks.

---

## 🔑 Administrative Credentials
- **Role**: Reservation Clerk / Administrator
- **Default Passcode**: `admin123`
