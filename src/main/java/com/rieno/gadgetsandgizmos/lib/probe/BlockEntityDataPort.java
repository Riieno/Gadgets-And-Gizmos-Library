package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.List;

// Describe one typed block entity graph data port
public record BlockEntityDataPort(String id, String type, Access access, List<String> options) {
    // Initialize the block entity data port
    public BlockEntityDataPort {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Block entity data port id cannot be blank");
        }
        id = id.strip();
        type = type == null || type.isBlank() ? "any" : type.strip();
        access = access == null ? Access.READ : access;
        options = options == null ? List.of() : List.copyOf(options);
    }

    // Create a readable port
    public static BlockEntityDataPort readable(String id, String type) {
        return new BlockEntityDataPort(id, type, Access.READ, List.of());
    }

    // Create a writable port
    public static BlockEntityDataPort writable(String id, String type) {
        return new BlockEntityDataPort(id, type, Access.WRITE, List.of());
    }

    // Create a readable and writable port
    public static BlockEntityDataPort readWrite(String id, String type) {
        return new BlockEntityDataPort(id, type, Access.READ_WRITE, List.of());
    }

    // Define the data access values
    public enum Access {
        READ,
        WRITE,
        READ_WRITE;

        // Check if this can be read
        public boolean canRead() {
            return this == READ || this == READ_WRITE;
        }

        // Check if this can be written
        public boolean canWrite() {
            return this == WRITE || this == READ_WRITE;
        }
    }
}
