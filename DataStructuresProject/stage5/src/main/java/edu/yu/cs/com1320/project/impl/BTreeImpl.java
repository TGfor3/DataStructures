package edu.yu.cs.com1320.project.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.yu.cs.com1320.project.BTree;
import edu.yu.cs.com1320.project.stage5.PersistenceManager;

public class BTreeImpl<Key extends Comparable<Key>, Value> implements BTree{
    private static final int MAX = 4;
    private Node root;
    private Node leftMostChild;
    private int height;
    private int numPairs;
    private PersistenceManager pm;
    private Set<Key> keysOnDisk;

    private static final class Node{
        private int entryCount;
        private Entry[] entries;
        private Node next;
        private Node previous;

        private Node(int k)
        {
            this.entries = new Entry[BTreeImpl.MAX];
            this.entryCount = k;
            
        }
        private void setNext(Node next){
        this.next = next;
        }
        private Node getNext(){
            return this.next;
        }
        private void setPrevious(Node prev){
            this.previous = prev;
        }
        private Node getPrevious(){
            return this.previous;
        }
        private Entry[] getEntries()
        {
            return Arrays.copyOf(this.entries, this.entryCount);
        }

    }
    private static class Entry{
        private Comparable key;
        private Object val;
        private Node child;
        private Entry(Comparable k, Object v, Node child){
            this.key = k;
            this.val = v;
            this.child = child;
        }
        private Comparable getKey(){
            return this.key;
        }
        private Object getValue(){
            return this.val;
        }
        private Node getChild(){
            return this.child;
        }
    }

    public BTreeImpl(){
        this.root = new Node(0);
        this.leftMostChild = this.root;
        this.keysOnDisk = new HashSet<>();
    }
    public Value get(Comparable k){
        if(k == null){
            throw new IllegalArgumentException();
        }
        Entry entry = get(this.root, k, this.height);
        Value val = null;
        if (entry != null && entry.val == null && this.keysOnDisk.remove(k)) {
            try{
                val = (Value)pm.deserialize(k);
                put(k, val);
            }catch(IOException e){
            }
        }else if(entry != null && entry.val != null){
            val = (Value)entry.val;
        }
        return val;
    }
    private Entry get(Node currentNode, Comparable key, int height){
        Entry[] entries = currentNode.entries;
        //We are at the lowest level of the tree, so the value is either here or not in the tree
        if (height == 0) {
            for (int i = 0; i < currentNode.entryCount; i++) {
                if (isEqual(currentNode.entries[i].key, key)) {
                    return entries[i];
                }
            }
            return null;
        }else{
            for (int i = 0; i < currentNode.entryCount; i++) {
                if(i + 1 == currentNode.entryCount || isLess(key, entries[i+1].key)){
                    return get(entries[i].child, key, height -1);
                }
            }
            return null;
        }

    }
    public Value put(Comparable k, Object v){
        if (k == null) {
            throw new IllegalArgumentException();
        }
        if (v == null) {
            return (Value)delete(k);
        }
        Value holder;
        Entry entry = get(this.root, k, this.height);
        if (entry != null && entry.val == null && this.keysOnDisk.contains(k)) {
            try {
                holder = (Value)pm.deserialize(k);
                this.keysOnDisk.remove(k);
                entry.val = v; 
                return holder; 
            } catch (IOException e){
            }
        }else if(entry != null && entry.val != null){
            holder = (Value)entry.val;
            entry.val = v;
            return holder;
        }else if(entry != null && entry.key != null){
            entry.val = v;
            return null;
        }
        Node node = put(this.root, (Key)k,(Value)v,this.height);
        this.numPairs++;
        if (node == null) {
            return null;
        }
        Node newRoot = new Node(2);
        newRoot.entries[0] = new Entry(this.root.entries[0].key, null, this.root);
        newRoot.entries[1] = new Entry(node.entries[0].key, null, node);
        this.root = newRoot;
        this.height++;
        return null;
    }
    
    private Node put(Node currentNode, Key key, Value val, int height)
    {
        int j;
        Entry newEntry = new Entry(key, val, null);
        //external node
        if (height == 0)
        {
            //find index in currentNode’s entry[] to insert new entry
            //we look for key < entry.key since we want to leave j
            //pointing to the slot to insert the new entry, hence we want to find
            //the first entry in the current node that key is LESS THAN
            for (j = 0; j < currentNode.entryCount; j++)
            {
                if (isLess(key, currentNode.entries[j].key))
                {
                    break;
                }
            }
        }
        // internal node
        else
        {
            //find index in node entry array to insert the new entry
            for (j = 0; j < currentNode.entryCount; j++)
            {
                //if (we are at the last key in this node OR the key we
                //are looking for is less than the next key, i.e. the
                //desired key must be added to the subtree below the current entry),
                //then do a recursive call to put on the current entry’s child
                if ((j + 1 == currentNode.entryCount) || isLess(key, currentNode.entries[j + 1].key))
                {
                    //increment j (j++) after the call so that a new entry created by a split
                    //will be inserted in the next slot
                    Node newNode = this.put(currentNode.entries[j++].child, key, val, height - 1);
                    if (newNode == null)
                    {
                        return null;
                    }
                    //if the call to put returned a node, it means I need to add a new entry to
                    //the current node
                    newEntry.key = newNode.entries[0].key;
                    newEntry.val = null;
                    newEntry.child = newNode;
                    break;
                }
            }
        }
        //shift entries over one place to make room for new entry
        for (int i = currentNode.entryCount; i > j; i--)
        {
            currentNode.entries[i] = currentNode.entries[i - 1];
        }
        //add new entry
        currentNode.entries[j] = newEntry;
        currentNode.entryCount++;
        if (currentNode.entryCount < BTreeImpl.MAX)
        {
            //no structural changes needed in the tree
            //so just return null
            return null;
        }
        else
        {
            //will have to create new entry in the parent due
            //to the split, so return the new node, which is
            //the node for which the new entry will be created
            return this.split(currentNode, height);
        }
    }
    private Node split(Node currentNode, int height)
    {
        Node newNode = new Node(BTreeImpl.MAX / 2);
        //by changing currentNode.entryCount, we will treat any value
        //at index higher than the new currentNode.entryCount as if
        //it doesn't exist
        currentNode.entryCount = BTreeImpl.MAX / 2;
        //copy top half of h into t
        for (int j = 0; j < BTreeImpl.MAX / 2; j++)
        {
            newNode.entries[j] = currentNode.entries[BTreeImpl.MAX / 2 + j];
        }
        //external node
        if (height == 0)
        {
            newNode.setNext(currentNode.getNext());
            newNode.setPrevious(currentNode);
            currentNode.setNext(newNode);
        }
        return newNode;
    }

    public void moveToDisk(Comparable k) throws Exception {
        if(k == null){
            throw new IllegalArgumentException();
        }
        Entry pointer = get(this.root, k, this.height);
        pm.serialize((Key)k, (Value)pointer.val);
        pointer.val = null;
        this.keysOnDisk.add((Key)k);
        return;
    }
    private boolean isEqual(Comparable<Key> x, Comparable<Key> y){
        return x.compareTo((Key)y) == 0;
    }
    private boolean isLess(Comparable<Key> x, Comparable<Key> y){
        return x.compareTo((Key)y) < 0;
    }
    public void setPersistenceManager(PersistenceManager pm) {
        this.pm = pm;    
    }
    private Value delete(Comparable<Key> k){
        Entry entry = get(this.root, k, this.height);
        Value val = null;
        if (entry != null && entry.val == null && this.keysOnDisk.remove(k)) {
            try{
                val = (Value)pm.deserialize(k);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        if (entry != null && entry.val != null) {
            val = (Value)entry.val;
            entry.val = null;    
        }
        return val;
    }
}
