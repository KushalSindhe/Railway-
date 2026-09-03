package com.railway.dsa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Custom Sorting Algorithms implementation (MergeSort and QuickSort).
 * 
 * DSA Justification:
 * Sorting is a fundamental requirement in the Railway Management System:
 * 1. Sorting Trains by Available Seats (helps passengers find trains with confirmed seats quickly).
 * 2. Sorting Trains by Train Number/ID (enables O(log N) Binary Search).
 * 3. Sorting Reservations by Booking Time / PNR (helps clerks generate chronologically ordered passenger charts).
 * 
 * Algorithms Implemented:
 * - MergeSort: Stable divide-and-conquer algorithm with guaranteed O(N log N) worst-case time complexity.
 *   Essential when relative order of equal elements must be preserved (e.g., preserving booking priority).
 * - QuickSort: Highly cache-efficient in-place divide-and-conquer sorting with O(N log N) average time.
 * 
 * Time Complexities:
 * - MergeSort: Best O(N log N), Average O(N log N), Worst O(N log N). Space: O(N).
 * - QuickSort: Best O(N log N), Average O(N log N), Worst O(N^2). Space: O(log N).
 */
public class SortingAlgorithms {

    /**
     * Sorts a list in-place using MergeSort with a custom Comparator.
     * Guaranteed O(N log N) time complexity.
     */
    public static <T> void mergeSort(List<T> list, Comparator<? super T> comparator) {
        if (list == null || list.size() <= 1) return;
        List<T> auxiliary = new ArrayList<>(list);
        mergeSortInternal(list, auxiliary, 0, list.size() - 1, comparator);
    }

    private static <T> void mergeSortInternal(List<T> list, List<T> aux, int left, int right, Comparator<? super T> comp) {
        if (left >= right) return;
        int mid = left + (right - left) / 2;
        mergeSortInternal(list, aux, left, mid, comp);
        mergeSortInternal(list, aux, mid + 1, right, comp);
        merge(list, aux, left, mid, right, comp);
    }

    private static <T> void merge(List<T> list, List<T> aux, int left, int mid, int right, Comparator<? super T> comp) {
        for (int i = left; i <= right; i++) {
            aux.set(i, list.get(i));
        }

        int i = left;
        int j = mid + 1;
        int k = left;

        while (i <= mid && j <= right) {
            if (comp.compare(aux.get(i), aux.get(j)) <= 0) {
                list.set(k++, aux.get(i++));
            } else {
                list.set(k++, aux.get(j++));
            }
        }

        while (i <= mid) {
            list.set(k++, aux.get(i++));
        }
        while (j <= right) {
            list.set(k++, aux.get(j++));
        }
    }

    /**
     * Sorts a list in-place using QuickSort with a custom Comparator.
     * Average O(N log N) time complexity.
     */
    public static <T> void quickSort(List<T> list, Comparator<? super T> comparator) {
        if (list == null || list.size() <= 1) return;
        quickSortInternal(list, 0, list.size() - 1, comparator);
    }

    private static <T> void quickSortInternal(List<T> list, int low, int high, Comparator<? super T> comp) {
        if (low < high) {
            int pivotIndex = partition(list, low, high, comp);
            quickSortInternal(list, low, pivotIndex - 1, comp);
            quickSortInternal(list, pivotIndex + 1, high, comp);
        }
    }

    private static <T> int partition(List<T> list, int low, int high, Comparator<? super T> comp) {
        // Median-of-three or last element as pivot
        T pivot = list.get(high);
        int i = low - 1;

        for (int j = low; j < high; j++) {
            if (comp.compare(list.get(j), pivot) <= 0) {
                i++;
                swap(list, i, j);
            }
        }
        swap(list, i + 1, high);
        return i + 1;
    }

    private static <T> void swap(List<T> list, int i, int j) {
        T temp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, temp);
    }
}
