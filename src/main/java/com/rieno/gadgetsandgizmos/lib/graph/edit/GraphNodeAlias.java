package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

// Define the shared graph node alias contract
public final class GraphNodeAlias {
    public static final String DATA_KEY = "Alias";
    public static final int MAX_LENGTH = 64;

    private GraphNodeAlias() {
    }

    // Normalize one graph node alias
    public static String normalize(String alias) {
        return alias == null ? "" : alias.strip();
    }

    // Check if one graph node alias can be stored
    public static boolean isValid(String alias) {
        String normalized = normalize(alias);
        if (normalized.length() > MAX_LENGTH) {
            return false;
        }
        return normalized.codePoints().noneMatch(Character::isISOControl);
    }
}
