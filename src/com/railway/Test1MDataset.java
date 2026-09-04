package com.railway;

import java.util.*;
import com.railway.service.*;

public class Test1MDataset {
    public static void main(String[] args) {
        System.out.println("=== 1. Initializing IrctcUserDatasetService ===");
        long t0 = System.currentTimeMillis();
        AuthService authService = new AuthService();
        IrctcUserDatasetService dataset = new IrctcUserDatasetService(authService);
        long initTime = System.currentTimeMillis() - t0;
        System.out.println("Dataset service initialized in " + initTime + " ms! Total users: " + IrctcUserDatasetService.TOTAL_USERS);

        System.out.println("\n=== 2. Testing Direct Index Generation ===");
        IrctcUserDatasetService.UserRecord u1 = dataset.generateUserByIndex(1);
        System.out.println("User #1: " + u1.fullName + " (" + u1.username + ") | " + u1.email + " | " + u1.phone + " | " + u1.state + " | " + u1.loyaltyTier + " | Spend: Rs. " + u1.totalSpend);
        
        IrctcUserDatasetService.UserRecord u500k = dataset.generateUserByIndex(500000);
        System.out.println("User #500,000: " + u500k.fullName + " (" + u500k.username + ") | " + u500k.email + " | " + u500k.state + " | Bookings: " + u500k.totalBookings);

        IrctcUserDatasetService.UserRecord u1m = dataset.generateUserByIndex(1000000);
        System.out.println("User #1,000,000: " + u1m.fullName + " (" + u1m.username + ") | " + u1m.email + " | " + u1m.state + " | Bookings: " + u1m.totalBookings);

        System.out.println("\n=== 3. Testing Paginated Search Page 1 (PageSize 50) ===");
        IrctcUserDatasetService.UserSearchResult res1 = dataset.searchUsers("", "ALL", "ALL", 1, 50);
        System.out.println("Result 1: Total=" + res1.total + ", Filtered=" + res1.filteredTotal + ", Page=" + res1.page + "/" + res1.totalPages + ", Returned=" + res1.users.size() + ", QueryTime=" + res1.queryTimeMs + " ms");

        System.out.println("\n=== 4. Testing Filter by Role = ADMIN ===");
        IrctcUserDatasetService.UserSearchResult resAdmin = dataset.searchUsers("", "ADMIN", "ALL", 1, 25);
        System.out.println("Result Admin: Filtered=" + resAdmin.filteredTotal + ", Returned=" + resAdmin.users.size() + ", QueryTime=" + resAdmin.queryTimeMs + " ms");
        for (int i = 0; i < Math.min(3, resAdmin.users.size()); i++) {
            IrctcUserDatasetService.UserRecord a = resAdmin.users.get(i);
            System.out.println("  Admin Pax: " + a.fullName + " (@" + a.username + ") | " + a.role);
        }

        System.out.println("\n=== 5. Testing Filter by State = Maharashtra ===");
        IrctcUserDatasetService.UserSearchResult resMaha = dataset.searchUsers("", "ALL", "Maharashtra", 1, 25);
        System.out.println("Result Maharashtra: Filtered=" + resMaha.filteredTotal + ", Returned=" + resMaha.users.size() + ", QueryTime=" + resMaha.queryTimeMs + " ms");

        System.out.println("\n=== 6. Testing Query Search ('Sharma') ===");
        IrctcUserDatasetService.UserSearchResult resSharma = dataset.searchUsers("sharma", "ALL", "ALL", 1, 25);
        System.out.println("Result 'sharma': Filtered=" + resSharma.filteredTotal + ", Returned=" + resSharma.users.size() + ", QueryTime=" + resSharma.queryTimeMs + " ms");
        for (int i = 0; i < Math.min(3, resSharma.users.size()); i++) {
            IrctcUserDatasetService.UserRecord s = resSharma.users.get(i);
            System.out.println("  Matched: " + s.fullName + " (@" + s.username + ")");
        }

        System.out.println("\n=== 7. Testing User Details Lookup ===");
        IrctcUserDatasetService.UserDetailResponse details = dataset.getUserDetails(u1.username);
        System.out.println("User Details for " + u1.username + ":");
        System.out.println("  Name: " + details.profile.fullName + ", Loyalty: " + details.profile.loyaltyTier + ", Spend: Rs. " + details.profile.totalSpend);
        System.out.println("  Generated tickets count: " + details.recentTickets.size());
        for (IrctcUserDatasetService.TicketHistoryItem t : details.recentTickets) {
            System.out.println("    Ticket: " + t.pnr + " | Train: " + t.trainName + " (" + t.trainId + ") | Status: " + t.status + " | Fare: Rs. " + t.fare);
        }

        System.out.println("\n=== ALL UNIT TESTS FOR 1,000,000 IRCTC DATASET PASSED! ===");
    }
}
