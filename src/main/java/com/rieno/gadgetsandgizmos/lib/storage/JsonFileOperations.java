package com.rieno.gadgetsandgizmos.lib.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Consumer;

// Edit existing named JSON documents without allowing names to escape their directory.
public final class JsonFileOperations{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonFileOperations(){}

    // Preserve the file's identity and unrelated properties; replace only after the update succeeds.
    public static boolean update(Path directory, String id, Consumer<JsonObject> editor) throws IOException{
        Objects.requireNonNull(editor, "editor");
        Path file = existingFile(directory, id);
        if(file == null) return false;
        JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if(!parsed.isJsonObject()) return false;
        JsonObject document = parsed.getAsJsonObject();
        editor.accept(document);
        Path temporary = Files.createTempFile(file.getParent(), ".json-update-", ".tmp");
        try{
            Files.writeString(temporary, GSON.toJson(document), StandardCharsets.UTF_8);
            try{
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }catch(AtomicMoveNotSupportedException ignored){
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }finally{
            Files.deleteIfExists(temporary);
        }
    }

    // Remove exactly one existing JSON file; directories and symbolic links are excluded.
    public static boolean delete(Path directory, String id) throws IOException{
        Path file = existingFile(directory, id);
        return file != null && Files.deleteIfExists(file);
    }

    private static Path existingFile(Path directory, String id){
        if(directory == null || id == null || id.isBlank() || id.contains("/") || id.contains("\\")) return null;
        try{
            Path root = directory.toAbsolutePath().normalize();
            Path file = root.resolve(id + ".json").normalize();
            return root.equals(file.getParent()) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? file : null;
        }catch(InvalidPathException ignored){
            return null;
        }
    }
}
