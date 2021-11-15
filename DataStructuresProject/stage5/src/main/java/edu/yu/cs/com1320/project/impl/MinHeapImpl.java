package edu.yu.cs.com1320.project.impl;

import java.util.NoSuchElementException;

import edu.yu.cs.com1320.project.MinHeap;

public class MinHeapImpl<E extends Comparable<E>> extends MinHeap<E> {
    public MinHeapImpl(){
        this.elements = (E[])new Comparable[10];
    }
    public void reHeapify(Comparable element) {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        int index = getArrayIndex(element);
        upHeap(index);
        downHeap(index);        
    }
    protected int getArrayIndex(Comparable element) {
        for (int i = 1; i <= this.count; i++) {
            if (this.elements[i].equals((E)element)) {
                return i;
            }
        }
        throw new NoSuchElementException();
    }
    protected void doubleArraySize() {
        E[] temp = (E[])new Comparable[this.elements.length * 2];
        for (int x = 1; x <= this.count; x++){
            temp[x] = this.elements[x];
        }
        this.elements = temp;    
    }
}
