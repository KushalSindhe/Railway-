package com.railway.dsa;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Custom Searching Algorithms implementation (Binary Search and Linear/Prefix Search).
 * 
 * DSA Justification:
 * Searching is central to querying railway services:
 * 1. Binary Search: Performs logarithmic O(log N) lookup in a sorted collection (e.g. searching
 *    for a train by its unique Train ID or a station by its Station Code). Greatly outperforms
 *    linear scans as the fleet/network grows.
 * 2. Linear & Prefix Search: Allows flexible substring and prefix matching for passenger queries,
 *    such as typing "Bang" to match "Bengaluru" or "Raj" to match "Rajdhani Express".
 * 
 * Time Complexities:
 * - Binary Search: O(log N)
 * - Linear Search: O(N)
 * - Prefix Filter: O(N * M) where M is pattern length.
 */
public class SearchingAlgorithms {

    /**
     * Generic iterative Binary Search on a sorted list.
     * 
     * @param list Sorted list of elements
     * @param targetKey The key to find
     * @param keyExtractor Function extracting comparable key from each element
     * @param <T> Element type
     * @param <K> Key type (must be Comparable)
     * @return Index of target element if found, or -1 if not found.
     */
    public static <T, K extends Comparable<K>> int binarySearch(List<T> list, K targetKey, Function<T, K> keyExtractor) {
        if (list == null || list.isEmpty() || targetKey == null) {
            return -1;
        }

        int low = 0;
        int high = list.size() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            K midKey = keyExtractor.apply(list.get(mid));

            int cmp = midKey.compareTo(targetKey);
            if (cmp == 0) {
                return mid; // Exact match found
            } else if (cmp < 0) {
                low = mid + 1; // Search right half
            } else {
                high = mid - 1; // Search left half
            }
        }

        return -1; // Not found
    }

    /**
     * Linear Search that finds the first element whose extracted key equals targetKey.
     * Time Complexity: O(N)
     */
    public static <T, K> T linearSearch(List<T> list, K targetKey, Function<T, K> keyExtractor) {
        if (list == null || targetKey == null) return null;
        for (T item : list) {
            K key = keyExtractor.apply(item);
            if (targetKey.equals(key)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Filters list for items whose string representation or key contains the query string (case-insensitive).
     * Time Complexity: O(N)
     */
    public static <T> List<T> searchBySubstring(List<T> list, String query, Function<T, String> stringExtractor) {
        List<T> results = new ArrayList<>();
        if (list == null || query == null) return results;

        String normalizedQuery = query.trim().toLowerCase();
        for (T item : list) {
            String value = stringExtractor.apply(item);
            if (value != null && value.toLowerCase().contains(normalizedQuery)) {
                results.add(item);
            }
        }
        return results;
    }
}
