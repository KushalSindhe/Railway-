package com.railway.dsa;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Custom Generic FIFO (First-In, First-Out) Queue implementation.
 * 
 * DSA Justification:
 * Used to manage the Waiting List for train reservations.
 * In a fair railway reservation system, requests that cannot be immediately confirmed
 * must be placed on a waiting list and serviced strictly in the order they arrived (FIFO).
 * When a confirmed ticket is cancelled, the head of the queue is dequeued and promoted.
 * 
 * Time Complexities:
 * - Enqueue: O(1)
 * - Dequeue: O(1)
 * - Peek: O(1)
 * - Size: O(1)
 * Space Complexity: O(N) where N is the number of queued reservations.
 *
 * @param <T> The type of elements held in this queue.
 */
public class CustomQueue<T> implements Iterable<T> {

    private static class Node<T> {
        T data;
        Node<T> next;

        Node(T data) {
            this.data = data;
            this.next = null;
        }
    }

    private Node<T> front;
    private Node<T> rear;
    private int size;

    public CustomQueue() {
        this.front = null;
        this.rear = null;
        this.size = 0;
    }

    /**
     * Inserts an element at the rear of the queue.
     * Time Complexity: O(1)
     */
    public void enqueue(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot enqueue null element.");
        }
        Node<T> newNode = new Node<>(item);
        if (isEmpty()) {
            front = newNode;
            rear = newNode;
        } else {
            rear.next = newNode;
            rear = newNode;
        }
        size++;
    }

    /**
     * Removes and returns the element at the front of the queue.
     * Time Complexity: O(1)
     */
    public T dequeue() {
        if (isEmpty()) {
            throw new NoSuchElementException("Queue is empty. Cannot dequeue.");
        }
        T item = front.data;
        front = front.next;
        if (front == null) {
            rear = null;
        }
        size--;
        return item;
    }

    /**
     * Retrieves, but does not remove, the element at the front of the queue.
     * Time Complexity: O(1)
     */
    public T peek() {
        if (isEmpty()) {
            throw new NoSuchElementException("Queue is empty. Cannot peek.");
        }
        return front.data;
    }

    /**
     * Checks if the queue is empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of elements in the queue.
     */
    public int size() {
        return size;
    }

    /**
     * Removes a specific item from the queue (e.g., if passenger cancels a waitlisted ticket).
     * Time Complexity: O(N)
     */
    public boolean remove(T item) {
        if (isEmpty() || item == null) {
            return false;
        }

        if (front.data.equals(item)) {
            dequeue();
            return true;
        }

        Node<T> current = front;
        while (current.next != null) {
            if (current.next.data.equals(item)) {
                if (current.next == rear) {
                    rear = current;
                }
                current.next = current.next.next;
                size--;
                return true;
            }
            current = current.next;
        }
        return false;
    }

    /**
     * Returns the 1-based index (position) of an item in the queue.
     * Returns -1 if not found.
     */
    public int getPosition(T item) {
        int pos = 1;
        Node<T> current = front;
        while (current != null) {
            if (current.data.equals(item)) {
                return pos;
            }
            pos++;
            current = current.next;
        }
        return -1;
    }

    /**
     * Returns all elements as a List for viewing/reporting.
     */
    public List<T> toList() {
        List<T> list = new ArrayList<>();
        Node<T> current = front;
        while (current != null) {
            list.add(current.data);
            current = current.next;
        }
        return list;
    }

    @Override
    public Iterator<T> iterator() {
        return toList().iterator();
    }
}
