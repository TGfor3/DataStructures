package edu.yu.cs.com1320.project.impl;
import edu.yu.cs.com1320.project.Stack;
public class StackImpl<T> implements Stack<T>
{
    private class Entry<T>{
		T t;
        Entry<T> next;
		Entry(T val){
            this.t = val;
		}
		private void setNext(Entry<T> e){
			this.next = e;
		}
	}

    private Entry<T> head;
    private int size;
    public StackImpl(){
        this.head = null;
        this.size = 0;
    }

    public void push(T element) {
        Entry newVal = new Entry<T>(element);
        newVal.setNext(head);
        head = newVal;
        this.size++;
    }

    public T pop() {
        if (this.size == 0) {
            return null;
        }
        T top = head.t;
        this.head = head.next;
        this.size--;
        return top;
    }

    public T peek() {
        if (this.size == 0) {
            return null;
        }
        return head.t;
    }

    public int size() {
        return this.size;
    }    
}
