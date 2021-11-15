package edu.yu.cs.com1320.project.stage5.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import edu.yu.cs.com1320.project.impl.BTreeImpl;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.DocumentStore;
import edu.yu.cs.com1320.project.*;

public class DocumentStoreImpl implements DocumentStore{
    private BTreeImpl<URI, Document> tree;
    private StackImpl<Undoable> stack;
    private MinHeapImpl<HeapThing> heap;
    private TrieImpl<URI> trie;
    private Map<URI,HeapThing> uriMap;
    private Set<URI> writtenToDisk;
    private Integer maxDocs;
    private Integer maxBytes;
    private int currentBytes;
    private int numDocs;
    
    private class HeapThing implements Comparable<HeapThing> {
        private URI uri;
        private HeapThing(URI uri){
            this.uri = uri;
        }
        @Override
        public int compareTo(HeapThing o){
            int x;
            x = getAndPutBackInMemory(uri).compareTo(getAndPutBackInMemory(o.uri));
            
            return x;
        }
    }

    public DocumentStoreImpl(){
        this.tree = new BTreeImpl<>();
        this.stack = new StackImpl<>();
        this.trie = new TrieImpl<>();
        this.heap = new MinHeapImpl<>();
        this.tree.setPersistenceManager(new DocumentPersistenceManager(new File(System.getProperty("user.dir"))));
        this.uriMap = new HashMap<>();
        this.writtenToDisk = new HashSet<>();
    }

    public DocumentStoreImpl(File baseDir){
        this();
        this.tree.setPersistenceManager(new DocumentPersistenceManager(baseDir));
    }

    public int putDocument(InputStream input, URI uri, DocumentFormat format) throws IOException {
        if(uri == null || uri.toString().isBlank()|| format == null){
			throw new IllegalArgumentException();
		}
        Function<URI,Boolean> func;
        if (input == null) {
            Document delete = tree.get(uri);
            return(deleteDocument(uri) ? delete.hashCode(): 0);
        }
        byte[] bytes = new byte[input.available()];
        input.read(bytes);
        Document returned;
        Document doc = null;
        if (format == DocumentFormat.TXT) {
            doc = new DocumentImpl(uri, new String(bytes));
            returned = this.tree.put(uri, doc);
        }else{
            doc = new DocumentImpl(uri, bytes);
            returned = this.tree.put(uri,doc);
        }
        //Everything was just moved to disk and anything added goes straight to disk
        if ((this.maxDocs != null && this.maxDocs == 0) || (this.maxBytes != null && this.maxBytes < bytes.length)) {
            try{
                this.tree.moveToDisk(uri);
                this.writtenToDisk.add(uri);
            }catch(Exception e){
                throw new IllegalStateException();
            }
            return returned.hashCode();
        }
        boolean wasOnDisk = false;
        if (returned != null) {
            doc.setLastUseTime(returned.getLastUseTime());
            putOrDeleteTrie(returned, true);
            removeFromHeap(returned);
            wasOnDisk = this.writtenToDisk.remove(returned.getKey());
        }
        Set<URI> pushedToDisk = cleanUp(true,(returned == null ? getMemory(doc): (this.writtenToDisk.contains(returned.getKey()) ? bytes.length: bytes.length - getMemory(returned))));
        putOrDeleteTrie(doc, false);
        func = (returned == null ? getLambda(doc, true, pushedToDisk, false): getLambda(returned, false, pushedToDisk, wasOnDisk));
        putInHeap(doc);
        this.stack.push(new GenericCommand<URI>(uri, func));
        return (returned != null ? returned.hashCode(): 0);
    }

    private Function<URI,Boolean> getLambda(Document doc, boolean justDelete, Set<URI> pushedToDisk, boolean wasOnDisk){
        Function<URI,Boolean> func;
        if (justDelete) {
            func = uri2 -> {
                for (URI uri : pushedToDisk) {
                    cleanUp(true, getMemory(getAndPutBackInMemory(uri)));
                    //Brings it back into memory
                    Document document = this.tree.get(uri);
                    //Add it back to Heap, will also update lastUseTime
                    putInHeap(document);
                }
                return putOrDeleteHeapTrieTree(doc, true, Long.MIN_VALUE);
            };
        }else{
            func = uri2 ->{
                for (URI uri : pushedToDisk) {
                    try {
                        cleanUp(true, getMemory(getAndPutBackInMemory(uri)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //Brings it back into memory
                    Document document = this.tree.get(uri);
                    //Add it back to Heap, will also update lastUseTime
                    putInHeap(document);
                }
                Document oldNew = this.tree.put(doc.getKey(), doc);
                //If returned was on disk, the undo has to put it back on disk
                if (wasOnDisk) {
                    try {
                        this.tree.moveToDisk(doc.getKey());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                putOrDeleteHeapTrieTree(oldNew, true, Long.MIN_VALUE);
                return putOrDeleteHeapTrieTree(doc, false, System.nanoTime());
            };
        }
        return func;
    }

    public Document getDocument(URI uri) {
        if (uri == null || uri.toString().isBlank()) {
			throw new IllegalArgumentException();
		}
		Document doc = this.tree.get(uri);
		if (doc == null) {
			return null;
		}
        doc.setLastUseTime(System.nanoTime());
        this.heap.reHeapify(this.uriMap.get(doc.getKey()));
        if (this.writtenToDisk.remove(uri)) {
            cleanUp(true, getMemory(doc));
            putInHeap(doc);
        }       
        return doc;
    }

    @Override
    public boolean deleteDocument(URI uri) {
        Function<URI, Boolean> func;
        Document docToDelete = this.tree.get(uri);
        if (docToDelete == null) {
            func = uri2->{
                return true;
            };
        }else{
            putOrDeleteHeapTrieTree(docToDelete, true, Long.MIN_VALUE);
            func = uri2 ->{
                return putOrDeleteHeapTrieTree(docToDelete, false, System.nanoTime());  
            };
        }
        GenericCommand<URI> comm = new GenericCommand<URI>(uri, func);
        this.stack.push(comm);
        return docToDelete != null;
    }
    public void undo() throws IllegalStateException {
        if (this.stack.size() == 0) {
			throw new IllegalStateException();
		}
		Undoable command = this.stack.pop();
		command.undo();  
    }

    @Override
    public void undo(URI uri) throws IllegalStateException {
        if (uri == null || uri.toString().isBlank()) {
            throw new IllegalArgumentException();
        }
        if (this.stack.size() == 0) {
            throw new IllegalStateException();
        }
        StackImpl<Undoable> temp = new StackImpl<>();
        boolean match = false;
        for(int i = this.stack.size(); i > 0; i--){
            if (this.stack.peek() instanceof CommandSet) {
                CommandSet<URI> commSet = (CommandSet) this.stack.pop();
                match = commSet.undo(uri);
                if (commSet.size() > 0) {
					temp.push(commSet);
				}
				if (match) {
					break;
				}
            }else{
                GenericCommand<URI> comm = (GenericCommand)this.stack.pop();
                if (comm.getTarget().equals(uri)) {
                    match = comm.undo();
                    break;
                }
                temp.push(comm);
            }	
        }   
        while (temp.size() > 0) {
            this.stack.push((Undoable)temp.pop());
        }
        if (!match) {
            throw new IllegalStateException();
        }
    }
    public List<Document> search(String keyword) {
        if (keyword.contains("\\s+")) {
            throw new IllegalArgumentException("The keyword must be one word only");
        }
        Comparator<URI> func = (URI uri1, URI uri2)->{
            return this.tree.get(uri2).wordCount(makeRude(keyword)) - this.tree.get(uri1).wordCount(makeRude(keyword));
        };
        List<URI> uris = this.trie.getAllSorted(makeRude(keyword), func);
        List<Document> docs = new ArrayList<>();
        for (URI uri : uris) {
            //!Need to deal with documents being brought back into storage
            Document doc = this.tree.get(uri);
            docs.add(doc);
            doc.setLastUseTime(System.nanoTime());
            if (this.writtenToDisk.remove(uri)){
                cleanUp(true, getMemory(doc));
                putInHeap(doc);
            }
            this.heap.reHeapify(this.uriMap.get(uri));
            
        }
        return docs;
    }

    @Override
    public List<Document> searchByPrefix(String keywordPrefix) {
        if (keywordPrefix.contains("\\s+")) {
			throw new IllegalArgumentException("The keywordPrefix must be one word only");
		}
        Comparator<URI> func = (URI uri1, URI uri2) ->{
            String prefix = makeRude(keywordPrefix);
			int counter1 = (this.tree.get(uri1).wordCount(prefix) > 0 ? this.tree.get(uri1).wordCount(prefix) - 1: 0);
			int counter2 = (this.tree.get(uri2).wordCount(prefix) > 0 ? this.tree.get(uri2).wordCount(prefix) - 1 : 0);
			for (String word : this.tree.get(uri1).getWords()) {
				if (word.startsWith(prefix)) {
					counter1++;
                    counter1 += getAndPutBackInMemory(uri1).wordCount(word) - 1;     
				}
				if (word.equals(prefix) && getAndPutBackInMemory(uri1).wordCount(word) > 1) {
					counter1 += getAndPutBackInMemory(uri1).wordCount(word) - 1;
				}				
			}
			for (String word : getAndPutBackInMemory(uri2).getWords()) {
				if (word.startsWith(prefix)) {
					counter2++;
					counter2 += getAndPutBackInMemory(uri2).wordCount(word) - 1;
				}
				if (word.equals(prefix) && getAndPutBackInMemory(uri2).wordCount(word) > 1) {
					counter2 += (getAndPutBackInMemory(uri2).wordCount(word) - 1);
				}
			}
			return counter2 - counter1;
        };
        List<URI> results = this.trie.getAllWithPrefixSorted(makeRude(keywordPrefix), func);
        List<Document> docResults = new ArrayList<>();
        long x = System.nanoTime();
        for (URI uri : results) {
            Document doc = this.tree.get(uri);
            if (this.writtenToDisk.remove(uri)) {
                cleanUp(true, getMemory(doc));
                putInHeap(doc);
            }
            doc.setLastUseTime(x);
            this.heap.reHeapify(this.uriMap.get(uri));
            docResults.add(doc);
        }
        return docResults;
    }

    @Override
    public Set<URI> deleteAll(String keyword) {
        if (keyword.contains("\\s+")) {
			throw new IllegalArgumentException("The keyword must be one word only");
		}
        CommandSet<URI> commSet = new CommandSet<>();
        Set<URI> deletedURIs = this.trie.deleteAll(makeRude(keyword));
        for(URI uri: deletedURIs){
            Document doc = this.tree.get(uri);
            putOrDeleteHeapTrieTree(doc, true, Long.MIN_VALUE);
            Function<URI, Boolean> func = uri2->{
                return putOrDeleteHeapTrieTree(doc, false, System.nanoTime());
            };
            commSet.addCommand(new GenericCommand<URI>(uri, func));
        }
        if (!commSet.isEmpty()) {
            this.stack.push(commSet);    
        }
        return deletedURIs;
    }

    @Override
    public Set<URI> deleteAllWithPrefix(String keywordPrefix) {
        if (keywordPrefix.contains("\\s+")) {
			throw new IllegalArgumentException("The keyword must be one word only");
		}
        CommandSet<URI> commSet = new CommandSet<>();
        long time = System.nanoTime();
        Set<URI> deletedURIs = (Set<URI>)this.trie.deleteAllWithPrefix(makeRude(keywordPrefix));
        for(URI uri: deletedURIs){
            Document doc = this.tree.get(uri);
            putOrDeleteHeapTrieTree(doc, true, Long.MIN_VALUE);
            Function<URI,Boolean> func = uri2->{
                return putOrDeleteHeapTrieTree(doc, false, time);
            };
            commSet.addCommand(new GenericCommand<URI>(doc.getKey(), func));
        }
        if (!commSet.isEmpty()) {
            this.stack.push(commSet);
        }
        return deletedURIs;
    }
    public void setMaxDocumentCount(int limit) {
        this.maxDocs = limit;
        cleanUp(false, 0);        
    }
    public void setMaxDocumentBytes(int limit) {
        this.maxBytes = limit;
        cleanUp(false, 0);   
    }
    private void putOrDeleteTrie(Document doc, boolean delete){
        for (String word : doc.getWords()) {
            if (delete) {
                this.trie.delete(word, doc.getKey());
            }else{
                this.trie.put(word,doc.getKey());
                doc.setLastUseTime(System.nanoTime());
            }
        }
    }
    private void putInHeap(Document doc){
        doc.setLastUseTime(System.nanoTime());
        HeapThing h = new HeapThing(doc.getKey());
        this.heap.insert(h);
        this.uriMap.put(doc.getKey(), h);
    }
    private void removeFromHeap(Document doc){
        doc.setLastUseTime(Long.MIN_VALUE);
        this.heap.reHeapify(uriMap.get(doc.getKey()));
        this.heap.remove();
    }
    //Make Insensitive
	private String makeRude(String key){
		key = key.replaceAll("[^A-Za-z0-9]", "");
        return key.toLowerCase();
	}
    private boolean putOrDeleteHeapTrieTree(Document doc, boolean delete, long time){
        if (delete) {
            doc.setLastUseTime(time);
            this.heap.reHeapify(this.uriMap.get(doc.getKey()));
            this.heap.remove();
            for (String word : doc.getWords()) {
                this.trie.delete(word, doc.getKey());
            }
            this.tree.put(doc.getKey(),null);
            this.numDocs--;
            this.currentBytes -= getMemory(doc);
        }else{
            this.tree.put(doc.getKey(), doc);
            cleanUp(true, getMemory(doc));
            HeapThing h = new HeapThing(doc.getKey());
            doc.setLastUseTime(time);
            this.heap.insert(h);
            this.uriMap.put(doc.getKey(), h);
            for (String word : doc.getWords()) {
                this.trie.put(word, doc.getKey());
            }
        }
        return true;
    }
    private Set<URI> cleanUp(boolean put, int addedMemory){
        int val;
        Set<URI> removedFromMemory = new HashSet<>();
        if (put) {
            if (this.maxBytes != null) {
                val = this.currentBytes + addedMemory;
                while(val > this.maxBytes){
                    val = deleteForLimit(true, val, removedFromMemory);
                }
            }
            if (this.maxDocs != null) {
                val = this.numDocs + 1;
                while(val > this.maxDocs){
                    val = deleteForLimit(false, val, removedFromMemory);
                }
            }
            this.currentBytes += addedMemory;
            this.numDocs++;
        }else{
            if (this.maxBytes != null) {
                while(this.currentBytes > this.maxBytes){
                    deleteForLimit(true, this.currentBytes, removedFromMemory);
                }
            }
            if (this.maxDocs != null) {
                while(this.numDocs > this.maxDocs){
                    deleteForLimit(false, this.numDocs, removedFromMemory);
                }
            }
        }
        return removedFromMemory;
    }
    private int deleteForLimit(boolean bytes, int currentTarget, Set<URI> removedDocs){
        HeapThing thing;
        Document doc;
        try{
            thing = this.heap.remove();
            //Not going to bring back anything from memory bc if it's in memory it shouldnt be in the heap
            doc = this.tree.get(thing.uri);
            this.tree.moveToDisk(thing.uri);
            this.writtenToDisk.add(thing.uri);
            removedDocs.add(thing.uri);
        }catch(NoSuchElementException e){
            return 0;
        }catch(Exception o){
            return 0;
        }
        this.numDocs--;
        this.currentBytes -= getMemory(doc);
        return(bytes ? currentTarget - getMemory(doc): currentTarget - 1);
        
    }
    private int getMemory(Document doc){
        return(doc.getDocumentBinaryData() != null ? doc.getDocumentBinaryData().length: doc.getDocumentTxt().getBytes().length);
    }
    private Document getAndPutBackInMemory(URI uri){
        Document doc = this.tree.get(uri);
        if (this.writtenToDisk.contains(uri)) {
            try{
                this.tree.moveToDisk(uri);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        return doc;
    }





    //TODO! Deal with crazy cases
    //TODO! Deal with writing to Disk
    //!Test
    //Test deserialize on a document not there
    //!If something is written to disk, the number of bytes and number of docs is lowered.
    //! If a get or search now brings something back into memory, those numbers need to be increased and subsequently cleaned up
/*If memory limit is set to 500
current consumption is 450
least used doc is 51
new doc is 51
old doc at same uri is 1
 */
//Sometimes has to be in the tree and sometimes has to be out
//See what i did in stage4 and dont include tree in big method
//WHy is it getting put in twice?
//!Need to deal with documents being brought back into storage

//Do a get and then see if the uri was written to disk. If so, write it back to disk?
//!TODO Now i need to deal with the trie and it using documents piazza @648

}
