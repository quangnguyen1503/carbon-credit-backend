package com.example.carbon_credit.MatchingEngine;

import java.util.*;

public class IndexedHeap<T extends Comparable<T>> implements Iterable<T> {
    private final List<T> heap;
    private final Map<T, Integer> indexMap;
    private final boolean isMaxHeap;

    /**
     * Constructor
     * @param isMaxHeap true for max heap (highest priority first), false for min heap
     */
    public IndexedHeap(boolean isMaxHeap) {
        this.heap = new ArrayList<>();
        this.indexMap = new HashMap<>();
        this.isMaxHeap = isMaxHeap;
    }

    /**
     * Get number of elements in heap
     */
    public int size() {
        return heap.size();
    }

    /**
     * Check if heap is empty
     */
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    /**
     * Peek at root element without removing it - O(1)
     */
    public T peek() {
        return heap.isEmpty() ? null : heap.get(0);
    }

    /**
     * Insert new element - O(log n)
     * @throws IllegalArgumentException if item already exists
     */
    public void insert(T item) {
        if (indexMap.containsKey(item)) {
            throw new IllegalArgumentException("Item already exists in the heap");
        }

        heap.add(item);
        indexMap.put(item, heap.size() - 1);
        heapifyUp(heap.size() - 1);
    }

    /**
     * Extract and remove root element - O(log n)
     */
    public T extract() {
        if (heap.isEmpty()) {
            return null;
        }

        T root = heap.get(0);
        removeAt(0);
        return root;
    }

    /**
     * Remove specific item by value - O(log n)
     */
    public boolean remove(T item) {
        Integer index = indexMap.get(item);
        if (index == null) {
            return false;
        }
        removeAt(index);
        return true;
    }

    /**
     * Remove element ad specific index - O(log n)
     */
    public void removeAt(int index) {
        if (index < 0 || index >= heap.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + heap.size());
        }

        int lastIndex = heap.size() - 1;

        if (index == lastIndex) {
            T item = heap.remove(lastIndex);
            indexMap.remove(item);
            return;
        }

        swap(index, lastIndex);
        T removedItem = heap.remove(lastIndex);
        indexMap.remove(removedItem);

        if(index < heap.size()) {
            heapifyUp(index);
            heapifyDown(index);
        }
    }

    /**
     * Check if item exists in heap - O(1)
     */
    public boolean contains(T item) {
        return indexMap.containsKey(item);
    }

    /**
     * Clear all elements
     */
    public void clear() {
        heap.clear();
        indexMap.clear();
    }

    /**
     * Bubble up element to maintain heap property
     */
    private void heapifyUp(int index) {
        while (index > 0) {
            int parentIndex = (index - 1) / 2;

            if (compare(heap.get(index), heap.get(parentIndex)) <= 0) {
                break;
            }

            swap(index, parentIndex);
            index = parentIndex;
        }
    }

    /**
     * Bubble down element to maintain heap property
     */
    private void heapifyDown(int index) {
        while (true) {
            int largest = index;
            int leftChild = 2 * index + 1;
            int rightChild = 2 * index + 2;

            if (leftChild < heap.size() && compare(heap.get(leftChild), heap.get(largest)) > 0) {
                largest = leftChild;
            }

            if (rightChild < heap.size() && compare(heap.get(rightChild), heap.get(largest)) > 0) {
                largest = rightChild;
            }

            if (largest == index) {
                break;
            }

            swap(index, largest);
            index = largest;
        }
    }

    /**
     * Compare two elements based on heap type
     */
    private int compare(T a, T b) {
        int result = a.compareTo(b);
        return isMaxHeap ? result : -result;
    }

    /**
     * Swap two elements and update index map
     */
    private void swap(int i, int j) {
        T temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);

        indexMap.put(heap.get(i), i);
        indexMap.put(heap.get(j), j);
    }

    /**
     * Get all elements in sorted order without destroying the heap
     * More efficient than rebuilding sorted list from dictionary keys
     */
    public List<T> getSortedElements() {
        if (heap.isEmpty()) {
            return new ArrayList<>();
        }

        // Clone the heap to avoid destroying original
        List<T> tempHeap = new ArrayList<>(heap);
        List<T> result = new ArrayList<>(heap.size());

        // Extract all elements (they come out in sorted order)
        while (!tempHeap.isEmpty()) {
            result.add(tempHeap.get(0));

            // Remove root
            int lastIndex = tempHeap.size() - 1;
            if (lastIndex == 0) {
                tempHeap.remove(0);
                break;
            }

            // Move last to root and heapify down
            tempHeap.set(0, tempHeap.get(lastIndex));
            tempHeap.remove(lastIndex);
            heapifyDownTemp(tempHeap, 0);
        }

        return result;
    }

    /**
     * Heapify down for temporary heap (used in getSortedElements)
     */
    private void heapifyDownTemp(List<T> tempHeap, int index) {
        while (true) {
            int largest = index;
            int leftChild = 2 * index + 1;
            int rightChild = 2 * index + 2;

            if (leftChild < tempHeap.size() && compare(tempHeap.get(leftChild), tempHeap.get(largest)) > 0) {
                largest = leftChild;
            }

            if (rightChild < tempHeap.size() && compare(tempHeap.get(rightChild), tempHeap.get(largest)) > 0) {
                largest = rightChild;
            }

            if (largest == index) {
                break;
            }

            // Swap
            T temp = tempHeap.get(index);
            tempHeap.set(index, tempHeap.get(largest));
            tempHeap.set(largest, temp);

            index = largest;
        }
    }

    @Override
    public Iterator<T> iterator() {
        return heap.iterator();
    }

    /**
     * Get string representation for debugging
     */
    @Override
    public String toString() {
        return "IndexedHeap{" +
                "size=" + heap.size() +
                ", isMaxHeap=" + isMaxHeap +
                ", elements=" + heap +
                '}';
    }

}