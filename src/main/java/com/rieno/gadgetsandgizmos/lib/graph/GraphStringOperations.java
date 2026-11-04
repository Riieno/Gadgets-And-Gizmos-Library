package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Split literal text and collect regular-expression matches for graph consumers
public final class GraphStringOperations{
    // Keep saved split choices independent of the consuming node
    public enum SplitMode{
        DELIMITER("Delimiter"),
        REGEX("Regex");

        // Retain the serialized choice and dropdown label
        private final String id;

        // Assign the stable split choice
        SplitMode(String id){
            this.id = id;
        }

        // Return the serialized split choice
        public String id(){
            return id;
        }

        // List split choices in dropdown order
        public static List<String> optionIds(){
            return List.of(DELIMITER.id, REGEX.id);
        }

        // Keep missing and unknown choices on literal splitting
        public static SplitMode fromId(String id){
            return REGEX.id.equalsIgnoreCase(id) ? REGEX : DELIMITER;
        }
    }

    // Prevent construction of the shared string operations
    private GraphStringOperations(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Return literal pieces or complete regex matches according to the selected mode
    public static List<String> split(String input, String separator, SplitMode mode){
        return mode == SplitMode.REGEX ? regexMatches(input, separator, false)
                : splitLiteral(input, separator, false);
    }

    // Retain empty pieces and optionally split an empty delimiter into Unicode characters
    public static List<String> splitLiteral(String input, String delimiter, boolean splitEmptyDelimiter){
        String val = input == null ? "" : input;
        String separator = delimiter == null ? "" : delimiter;
        if(separator.isEmpty()){
            return splitEmptyDelimiter ? val.codePoints().mapToObj(Character::toString).toList() : List.of(val);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        int idx;
        while((idx = val.indexOf(separator, start)) >= 0){
            parts.add(val.substring(start, idx));
            start = idx + separator.length();
        }
        parts.add(val.substring(start));
        return List.copyOf(parts);
    }

    // Collect every match, optionally followed by its captures in group order
    public static List<String> regexMatches(String input, String pattern, boolean includeGroups){
        List<String> matches = new ArrayList<>();
        try{
            Matcher matcher = Pattern.compile(pattern == null || pattern.isEmpty() ? ".*" : pattern)
                    .matcher(input == null ? "" : input);
            while(matcher.find()){
                int groups = includeGroups ? matcher.groupCount() : 0;
                for(int idx = 0; idx <= groups; idx++){
                    String val = matcher.group(idx);
                    matches.add(val == null ? "" : val);
                }
            }
        }catch(PatternSyntaxException ignored){
            return List.of();
        }
        return List.copyOf(matches);
    }
}
