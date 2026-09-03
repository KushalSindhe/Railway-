package com.railway.dsa;

import java.util.*;

/**
 * Generic Adjacency List Graph Data Structure with Dijkstra's Shortest Path Algorithm.
 * 
 * DSA Justification:
 * The railway network is inherently a weighted graph G = (V, E) where:
 * - Vertices (V) represent Railway Stations.
 * - Edges (E) represent direct railway tracks between adjacent stations.
 * - Edge Weights represent track distance (in kilometers).
 * 
 * The Adjacency List representation is optimal for sparse railway graphs where each
 * station connects to an average of 2-5 direct junctions, yielding O(V + E) space
 * compared to O(V^2) of an adjacency matrix.
 * 
 * Shortest path discovery between any two stations is solved via Dijkstra's Algorithm
 * utilizing a Min-Priority Queue for greedy selection of the nearest unvisited station.
 * 
 * Time Complexities:
 * - Add Vertex: O(1)
 * - Add Edge: O(1)
 * - Dijkstra's Algorithm: O((V + E) log V) with PriorityQueue
 * - All Paths (DFS): O(V!) worst-case, constrained by depth for route alternatives
 * Space Complexity: O(V + E)
 *
 * @param <T> The vertex type (e.g. Station).
 */
public class Graph<T> {

    public static class Edge<T> {
        private final T source;
        private final T destination;
        private final double weight;

        public Edge(T source, T destination, double weight) {
            this.source = source;
            this.destination = destination;
            this.weight = weight;
        }

        public T getSource() {
            return source;
        }

        public T getDestination() {
            return destination;
        }

        public double getWeight() {
            return weight;
        }

        @Override
        public String toString() {
            return source + " -> " + destination + " (" + weight + " km)";
        }
    }

    private static class NodeDistance<T> implements Comparable<NodeDistance<T>> {
        final T node;
        final double distance;

        NodeDistance(T node, double distance) {
            this.node = node;
            this.distance = distance;
        }

        @Override
        public int compareTo(NodeDistance<T> other) {
            return Double.compare(this.distance, other.distance);
        }
    }

    private final Map<T, List<Edge<T>>> adjacencyList;

    public Graph() {
        this.adjacencyList = new LinkedHashMap<>();
    }

    /**
     * Adds a vertex to the graph if it doesn't already exist.
     */
    public boolean addVertex(T vertex) {
        if (vertex == null) return false;
        if (!adjacencyList.containsKey(vertex)) {
            adjacencyList.put(vertex, new ArrayList<>());
            return true;
        }
        return false;
    }

    /**
     * Adds a weighted edge between source and destination.
     * Can be directed or bidirectional (standard for two-way railway tracks).
     */
    public void addEdge(T source, T destination, double weight, boolean bidirectional) {
        addVertex(source);
        addVertex(destination);

        adjacencyList.get(source).add(new Edge<>(source, destination, weight));
        if (bidirectional) {
            adjacencyList.get(destination).add(new Edge<>(destination, source, weight));
        }
    }

    /**
     * Checks if the graph contains the given vertex.
     */
    public boolean containsVertex(T vertex) {
        return adjacencyList.containsKey(vertex);
    }

    /**
     * Returns a set of all vertices in the graph.
     */
    public Set<T> getVertices() {
        return adjacencyList.keySet();
    }

    /**
     * Returns the outgoing edges from the given vertex.
     */
    public List<Edge<T>> getNeighbors(T vertex) {
        return adjacencyList.getOrDefault(vertex, Collections.emptyList());
    }

    /**
     * Finds the shortest route between source and destination using Dijkstra's Algorithm.
     * 
     * @param source Starting vertex
     * @param destination Target vertex
     * @return DijkstraResult with path, total distance, and reachability.
     */
    public DijkstraResult<T> findShortestPath(T source, T destination) {
        if (!containsVertex(source) || !containsVertex(destination)) {
            return DijkstraResult.unreachable();
        }

        if (source.equals(destination)) {
            return new DijkstraResult<>(Collections.singletonList(source), 0.0, true);
        }

        Map<T, Double> distances = new HashMap<>();
        Map<T, T> predecessors = new HashMap<>();
        Set<T> visited = new HashSet<>();
        PriorityQueue<NodeDistance<T>> pq = new PriorityQueue<>();

        // Initialize distances to infinity
        for (T vertex : adjacencyList.keySet()) {
            distances.put(vertex, Double.POSITIVE_INFINITY);
        }

        distances.put(source, 0.0);
        pq.add(new NodeDistance<>(source, 0.0));

        while (!pq.isEmpty()) {
            NodeDistance<T> current = pq.poll();
            T u = current.node;

            if (visited.contains(u)) continue;
            visited.add(u);

            if (u.equals(destination)) {
                break; // Target reached with minimal distance
            }

            for (Edge<T> edge : getNeighbors(u)) {
                T v = edge.getDestination();
                if (visited.contains(v)) continue;

                double newDist = distances.get(u) + edge.getWeight();
                if (newDist < distances.get(v)) {
                    distances.put(v, newDist);
                    predecessors.put(v, u);
                    pq.add(new NodeDistance<>(v, newDist));
                }
            }
        }

        double totalDist = distances.get(destination);
        if (Double.isInfinite(totalDist)) {
            return DijkstraResult.unreachable();
        }

        // Reconstruct path from destination backwards to source
        LinkedList<T> path = new LinkedList<>();
        T curr = destination;
        while (curr != null) {
            path.addFirst(curr);
            curr = predecessors.get(curr);
        }

        return new DijkstraResult<>(path, totalDist, true);
    }

    /**
     * Finds all simple paths between source and destination using Depth First Search (DFS).
     * Useful for displaying alternative routes.
     */
    public List<List<T>> findAllPaths(T source, T destination, int maxDepth) {
        List<List<T>> allPaths = new ArrayList<>();
        if (!containsVertex(source) || !containsVertex(destination)) {
            return allPaths;
        }

        Set<T> visited = new HashSet<>();
        List<T> currentPath = new ArrayList<>();
        currentPath.add(source);
        visited.add(source);

        dfsPaths(source, destination, visited, currentPath, allPaths, maxDepth);
        return allPaths;
    }

    private void dfsPaths(T current, T destination, Set<T> visited, List<T> currentPath,
                          List<List<T>> allPaths, int maxDepth) {
        if (current.equals(destination)) {
            allPaths.add(new ArrayList<>(currentPath));
            return;
        }

        if (currentPath.size() >= maxDepth) {
            return;
        }

        for (Edge<T> edge : getNeighbors(current)) {
            T neighbor = edge.getDestination();
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                currentPath.add(neighbor);

                dfsPaths(neighbor, destination, visited, currentPath, allPaths, maxDepth);

                currentPath.remove(currentPath.size() - 1);
                visited.remove(neighbor);
            }
        }
    }

    /**
     * Calculates the cumulative distance of an ordered list of stations forming a route.
     */
    public double calculatePathDistance(List<T> path) {
        if (path == null || path.size() < 2) return 0.0;
        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            T u = path.get(i);
            T v = path.get(i + 1);
            boolean found = false;
            for (Edge<T> edge : getNeighbors(u)) {
                if (edge.getDestination().equals(v)) {
                    total += edge.getWeight();
                    found = true;
                    break;
                }
            }
            if (!found) return -1.0; // Disconnected path segment
        }
        return total;
    }

    /**
     * Returns total vertex count.
     */
    public int getVertexCount() {
        return adjacencyList.size();
    }

    /**
     * Returns total edge count.
     */
    public int getEdgeCount() {
        int count = 0;
        for (List<Edge<T>> edges : adjacencyList.values()) {
            count += edges.size();
        }
        return count;
    }
}
