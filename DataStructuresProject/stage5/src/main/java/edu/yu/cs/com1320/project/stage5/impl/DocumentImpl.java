package edu.yu.cs.com1320.project.stage5.impl;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.yu.cs.com1320.project.stage5.Document;

public class DocumentImpl implements Document
{
	private URI uri;
	private String txt;
	private byte[] binaryData;
	private HashMap<String, Integer> map;
	private Long lastUsedTime;

	public DocumentImpl(URI uri, String txt){
		if (uri == null || txt == null || txt.isEmpty() || uri.toString().isEmpty()) {
			throw new IllegalArgumentException();
		}
		this.uri = uri;
		this.txt = txt;
		String copy = txt;
		copy = copy.replaceAll("[^A-Za-z0-9\\s+]", "").toLowerCase();
		String [] words = copy.split(" ",0);
		map = new HashMap<>();
		for (String word : words) {
			if (!word.equals("")) {
				this.map.put(word, this.map.getOrDefault(word, 0) + 1);	
			}	
		}
	}

	public DocumentImpl(URI uri, byte[] binaryData){
		if (uri == null || binaryData == null || uri.toString().isEmpty() || binaryData.length == 0) {
			throw new IllegalArgumentException();
		}
		this.uri = uri;
		this.binaryData = binaryData;
		map = new HashMap<>();
	}
	public String getDocumentTxt(){
		return this.txt;
	}

    /**
	 * @return content of binary data document
	 */
	public byte[] getDocumentBinaryData() {
		return this.binaryData;
	}

    /**
     * @return URI which uniquely identifies this document
     */
    public URI getKey(){
    	return this.uri;
    }

    @Override
	public int hashCode() {
    	int result = uri.hashCode();
    	result = 31 * result + (txt != null ? txt.hashCode() : 0);
		result = 31 * result + Arrays.hashCode(binaryData);	
    	return result;
	}
	@Override
	public boolean equals(Object o){
		return this.hashCode() == o.hashCode() && o != null;
	}

	@Override
	public int compareTo(Document o) {
		return this.lastUsedTime.compareTo(o.getLastUseTime());
	}
	
	public int wordCount(String word) {
		if (this.binaryData != null){
			return 0;
		}
		if (word.contains("\\s+")) {
			throw new IllegalArgumentException("The word contained a space");
		}
		word = word.replaceAll("[^A-Za-z0-9]", "");
		word = word.toLowerCase();
		return this.map.getOrDefault(word,0);
	}
	
	public Set<String> getWords() {
		return this.map.keySet();
	}

	public long getLastUseTime() {
		return this.lastUsedTime;
	}

	public void setLastUseTime(long timeInNanoseconds) {
		this.lastUsedTime = timeInNanoseconds;
	}

	public Map<String, Integer> getWordMap() {
		return this.map;
	}

	public void setWordMap(Map<String, Integer> wordMap) {
		this.map = new HashMap<String,Integer>(wordMap);
		
	}
}
