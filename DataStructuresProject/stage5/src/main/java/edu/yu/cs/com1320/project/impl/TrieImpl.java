package edu.yu.cs.com1320.project.impl;

import java.util.*;

import edu.yu.cs.com1320.project.Trie;

public class TrieImpl<Value> implements Trie<Value>{
    TreeNode<Value> root;
    private class TreeNode<Value>{
        TreeNode<Value>[] links;
        private final int SIZE = 36;
        private Set<Value> values;
        TreeNode(){
            this.links = new TreeNode[SIZE];
            this.values = new HashSet<>();
        }
        private boolean hasChildren(){
            for (TreeNode x : this.links) {
                if (x != null) {
                    return true;
                }
            }
            return false;
        }
        private Set<TreeNode> getChildren(){
            Set<TreeNode> children = new HashSet<>();
            for (TreeNode child : this.links) {
                if (child != null) {
                    children.add(child);
                }
            }
            return children;
        }
    }
    public TrieImpl(){
    }
     /**
     * add the given value at the given key
     * @param key
     * @param val
     */
    public void put(String key, Object val) {
        if (key == null || key.isBlank() || val == null) {
            return;
        }
        if (key.contains("[^A-Za-z0-9]")) {
            throw new IllegalArgumentException("You supplied a character outside of the alphabet.");
        }
        key = key.toLowerCase();        
        this.root = put(this.root, key, (Value)val, 0);        
    }
    private TreeNode put(TreeNode<Value> node, String key, Value val, int d){
        if (node == null) {
            node = new TreeNode<Value>();
        }
        if(d == key.length()){
            node.values.add(val);
            return node;
        }
        char c = key.charAt(d);
        node.links[charToIndex(c)] = put(node.links[charToIndex(c)], key, val,d + 1);
        //Always just going to be the root node
        return node;
    }

    /**
     * get all exact matches for the given key, sorted in descending order.
     * Search is CASE INSENSITIVE.
     * @param key
     * @param comparator used to sort  values
     * @return a List of matching Values, in descending order
     */
    public List getAllSorted(String key, Comparator comparator) {
        Set<Value> values = new HashSet<>();
        if (key == null || key.isEmpty()) {
            return new ArrayList<>(values);
        }
        if (key.contains("[^A-Za-z0-9]")) {
            throw new IllegalArgumentException("You supplied a character outside of the alphabet.");
        }
        key = key.toLowerCase();
        TreeNode node = get(this.root, key, 0);
        List<Value> results = new ArrayList<>(values);
        if (node != null) {
            results.addAll(node.values);
            Collections.sort(results, comparator);   
        }
        return results;
    }

    private TreeNode get(TreeNode node, String key, int d){
        if (node == null || key == null || key.isBlank()) {
            return null;
        }
        if (d == key.length()) {
            return node;
        }
        char k = key.charAt(d);
        return get(node.links[charToIndex(k)], key, ++d);
    }

     /**
     * get all matches which contain a String with the given prefix, sorted in descending order.
     * For example, if the key is "Too", you would return any value that contains "Tool", "Too", "Tooth", "Toodle", etc.
     * Search is CASE INSENSITIVE.
     * @param prefix
     * @param comparator used to sort values
     * @return a List of all matching Values containing the given prefix, in descending order
     */

    public List getAllWithPrefixSorted(String prefix, Comparator comparator) {
        if (prefix == null || prefix.isBlank() || comparator == null) {
            return new ArrayList<Value>();
        }
        Set<Value> results = new HashSet<>();
        if (prefix.contains("[^A-Za-z0-9]")) {
            throw new IllegalArgumentException("You supplied a character outside of the alphabet.");
        }
        TreeNode node = get(this.root, prefix.toLowerCase(), 0);
        if (node == null) {
            return new ArrayList<Value>(results);
        }
        results.addAll(node.values);
        Queue<TreeNode> children = getAllChildren(node);
        while (children.size() > 0) {            
                results.addAll(children.remove().values);           
        }
        List<Value> listResults = new ArrayList<>(results);
        Collections.sort(listResults, comparator);
        return listResults;
    }

    private Queue<TreeNode> getAllChildren(TreeNode node){
        //Base Case is covered in the if statement: If it has no children, then it won't recurse again
        Queue<TreeNode> queue = new LinkedList<>();
        if (node.hasChildren()) {
            for(TreeNode x: (Set<TreeNode>)node.getChildren()){
                    queue.add(x);
                    queue.addAll(getAllChildren(x));   

            }
        }
        return queue;
    }

     /**
     * Delete the subtree rooted at the last character of the prefix.
     * Search is CASE INSENSITIVE.
     * @param prefix
     * @return a Set of all Values that were deleted.
     */
    public Set deleteAllWithPrefix(String prefix) {
        Set<Value> deletedValues = new HashSet<>();
        if (prefix == null || prefix.isBlank()) {
            return deletedValues;
        }
        if (prefix.contains("[^A-Za-z0-9]")) {
            throw new IllegalArgumentException("You supplied a character outside of the alphabet.");
        }
        prefix = prefix.toLowerCase();
        TreeNode node = get(this.root, prefix, 0);
        if (node == null) {
            return deletedValues;
        }
        deletedValues.addAll(node.values);
        for(TreeNode x: getAllChildren(node)){
            deletedValues.addAll(x.values);
        }
        node = null;
        cleanUp(prefix);
        return deletedValues;
    }

        /**
     * Delete all values from the node of the given key (do not remove the values from other nodes in the Trie)
     * @param key
     * @return a Set of all Values that were deleted.
     */
    public Set deleteAll(String key) {
        if (key.contains("[^A-Za-z0-9]")) {
            throw new IllegalArgumentException("You supplied a character outside of the alphabet.");
        }
        key = key.toLowerCase();
        Set<Value> deletedVals = new HashSet<Value>();
        if (key != null && !key.isBlank()) {
            TreeNode node = get(this.root, key, 0);
            if (node != null) {
                deletedVals.addAll(node.values);
                node.values.clear();
                if (!node.hasChildren()) {
                    cleanUp(key);
                }
            }
        }
        return deletedVals;
    }

    /**
     * Remove the given value from the node of the given key (do not remove the value from other nodes in the Trie)
     * @param key
     * @param val
     * @return the value which was deleted. If the key did not contain the given value, return null.
     */
    public Value delete(String key, Value val) {
        if (key == null || key.isBlank() || val == null) {
            return null;
        }
        if (key.contains("[^A-Za-z0-9]")) {
            throw new IllegalArgumentException("You supplied a character outside of the alphabet.");
        }
        key = key.toLowerCase();
        TreeNode<Value> node = get(this.root, key, 0);
        Value deleted = null;
        if (node == null) {
            return deleted;
        }
        for (Value value : node.values) {
            if (value.equals(val)) {
                deleted = value;
                node.values.remove(value);
                break;
            }
        }
        //Only going to need to clean up if this is a leaf and stores no values
        if (!node.hasChildren() && node.values.isEmpty()) {
            cleanUp(key);
        }
        return deleted;
    }
    private int charToIndex(char c){
        if (c > 96) {
            //It must be a lower-case character, 'a' has a value of 97,
            //and should be place in the 10th index
            return c - 87;
        }else{
            //Must be an integer, and therefore, 0 is placed in the 0 index
            return c - 48;
        }
    }
    private void cleanUp(String key){
        TreeNode<Value> pointer = this.root;
        TreeNode<Value> holder = null;
        char c = 0;
        for (int i = 0; i < key.length(); i++) {
            if (pointer == null) {
                break;
            }
            c = key.charAt(i);
            if (!pointer.values.isEmpty()) {
                holder = pointer;
            }
            pointer = pointer.links[charToIndex(c)];
        }
        if (holder != null) {
            holder.links[charToIndex(c)] = null;
        }
        
    }    
}
