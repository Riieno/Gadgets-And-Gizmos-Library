package com.rieno.gadgetsandgizmos.lib.chat;

import java.util.List;
import java.util.Optional;

// Match an exact chat prefix and preserve each remaining argument as text
public final class ChatArguments{
    private ChatArguments(){}

    public static Optional<List<String>> capture(String startWord, String message){
        if(startWord == null || startWord.isBlank() || message == null || !message.startsWith(startWord)) return Optional.empty();
        int end = startWord.length();
        if(message.length() > end && !Character.isWhitespace(message.charAt(end))) return Optional.empty();
        String rest = message.substring(end).strip();
        return Optional.of(rest.isEmpty() ? List.of() : List.of(rest.split("\\p{javaWhitespace}+")));
    }
}

