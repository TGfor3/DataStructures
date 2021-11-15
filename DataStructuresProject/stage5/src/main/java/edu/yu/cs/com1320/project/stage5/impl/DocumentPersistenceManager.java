package edu.yu.cs.com1320.project.stage5.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashMap;

import javax.xml.bind.DatatypeConverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.PersistenceManager;

/**
 * created by the document store and given to the BTree via a call to BTree.setPersistenceManager
 */
public class DocumentPersistenceManager implements PersistenceManager<URI, Document> {
    private File baseDir;
    public DocumentPersistenceManager(File baseDir){
        if (baseDir == null) {
            this.baseDir = new File(System.getProperty("user.dir"));
        }else{
            this.baseDir = baseDir;
        }
    }

    private JsonSerializer<Document> documentToJson = (Document doc, Type type, JsonSerializationContext context) ->{
       JsonObject json = new JsonObject();
       Gson gson = new Gson();
       json.addProperty((doc.getDocumentTxt()!= null ? "Text":"Binary"), (doc.getDocumentTxt() != null ? doc.getDocumentTxt(): DatatypeConverter.printBase64Binary(doc.getDocumentBinaryData())));
       json.addProperty("URI", doc.getKey().toString());
       json.addProperty("WordMap", gson.toJson(doc.getWordMap()));
       return json;
    };

    private JsonDeserializer<Document> jsonToDocument = (JsonElement json, Type type, JsonDeserializationContext context) -> {
        Gson gson = new Gson();
        DocumentImpl doc;
        URI uri = null;
        try{
            uri = new URI(json.getAsJsonObject().get("URI").getAsString());
        }catch(URISyntaxException e){
        }
        HashMap<String,Integer> map = gson.fromJson(json.getAsJsonObject().get("WordMap").getAsString(),new TypeToken<HashMap<String, Integer>>(){}.getType());
        if (json.getAsJsonObject().get("Text") == null) {
            doc = new DocumentImpl(uri, DatatypeConverter.parseBase64Binary(json.getAsJsonObject().get("Binary").getAsString()));
        }else{
            doc = new DocumentImpl(uri, json.getAsJsonObject().get("Text").getAsString());
        }
        doc.setWordMap(map);
        return doc;
    };

    @Override
    public void serialize(URI uri, Document val) throws IOException {
        if (uri == null || val == null) {
            throw new IllegalArgumentException();
        }
        Gson gson = new GsonBuilder().registerTypeAdapter(Document.class, documentToJson).setPrettyPrinting().create();
        Type type = new TypeToken<Document>(){}.getType();
        String path = uri.getRawPath();
        //path.replace("http:", "");
        path.replaceAll("//", File.separator);
        String filePath = (new File(path)).getParent();
        File file = null;
        if (uri.getAuthority() != null) {
            file = new File(this.baseDir, uri.getAuthority()+File.separator+filePath);
        }else{
            file = new File(this.baseDir, File.separator + filePath);
        }
        file.mkdirs();
        if (uri.getAuthority() != null) {
            file = new File(this.baseDir, uri.getAuthority()+File.separator+path+".json");
        }else{
            file = new File(this.baseDir, File.separator + path + ".json");
        }
        file.createNewFile();
        FileWriter fw = new FileWriter(file);
        fw.write(gson.toJson(val,type));
        fw.close();
    }

    @Override
    public Document deserialize(URI uri) throws IOException {
        Gson gson = new GsonBuilder().registerTypeAdapter(Document.class, jsonToDocument).create();
        Type type = new TypeToken<Document>(){}.getType();
        File file = new File(this.baseDir,uri.getAuthority()+File.separator+uri.getRawPath()+".json");
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String s;
        String x = "";
        while((s = reader.readLine()) != null){
            x += (s + "\n");
        }
        reader.close();
        delete(uri);
        return gson.fromJson(x, type);
    }
    
    public boolean delete(URI uri) throws IOException {
        String path = uri.getRawPath();
        path.replaceAll("//", File.separator);
        File file = new File(this.baseDir, uri.getAuthority()+File.separator+uri.getPath()+".json");
        try {
            Files.delete(file.toPath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
