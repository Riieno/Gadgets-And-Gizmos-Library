package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// List only decodable block schematics and retain their source format for browser filters
public final class SchematicCatalogue{
    // Bound metadata caching without retaining decoded templates or uploaded bytes
    private static final int CACHE_LIMIT = 512;
    private static final Map<Path, Inspection> CACHE = new LinkedHashMap<>(16, 0.75F, true);

    // Keep catalogue inspection on the static API
    private SchematicCatalogue(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Validate source files before returning any entry to a shared or private browser
    public static List<Entry> catalogue(Path directory, String idPrefix, HolderGetter<Block> registry,
                                         int maximumBodies, int maximumBlocks) throws IOException{
        if(registry == null || maximumBodies < 1 || maximumBlocks < 1) throw new IllegalArgumentException("Invalid schematic catalogue limits");
        List<Entry> res = new ArrayList<>();
        for(var file : SubLevelSchematicFiles.catalogue(directory, idPrefix)){
            Path path = directory.resolve(file.name()).toAbsolutePath().normalize();
            try{
                long size = Files.size(path);
                FileTime modified = Files.getLastModifiedTime(path);
                Inspection inspected;
                synchronized(CACHE){ inspected = CACHE.get(path); }
                if(inspected == null || inspected.size() != size || !inspected.modified().equals(modified)
                        || inspected.registry() != registry || inspected.maximumBodies() != maximumBodies || inspected.maximumBlocks() != maximumBlocks){
                    SubLevelSchematic.Format format = null;
                    ResourceLocation source = SchematicProvenance.UNKNOWN;
                    try{
                        byte[] data = SubLevelSchematicFiles.read(directory, idPrefix, file);
                        var tag = SubLevelSchematicFiles.readTag(data);
                        var schematic = SubLevelSchematicFiles.decode(registry, tag, maximumBodies, maximumBlocks);
                        format = schematic.bodies().getFirst().importFrame().format();
                        source = SchematicProvenance.source(tag, format);
                    }catch(IOException | RuntimeException err){
                        // Incompatible or malformed files stay outside every returned catalogue
                    }
                    inspected = new Inspection(size, modified, registry, maximumBodies, maximumBlocks, format, source);
                    synchronized(CACHE){
                        CACHE.put(path, inspected);
                        while(CACHE.size() > CACHE_LIMIT) CACHE.remove(CACHE.keySet().iterator().next());
                    }
                }
                if(inspected.format() != null) res.add(new Entry(file, inspected.format(), inspected.source()));
            }catch(IOException err){
                // Files removed or renamed during a scan are absent from this catalogue
            }
        }
        return List.copyOf(res);
    }

    // Preserve the original file identity alongside its validated format
    public record Entry(SubLevelSchematicFiles.Entry file, SubLevelSchematic.Format format, ResourceLocation source){
        public Entry(SubLevelSchematicFiles.Entry file, SubLevelSchematic.Format format){
            this(file, format, SchematicProvenance.source(new net.minecraft.nbt.CompoundTag(), format));
        }
    }

    // Recheck metadata when file content, registries or configured limits change
    private record Inspection(long size, FileTime modified, HolderGetter<Block> registry,
                              int maximumBodies, int maximumBlocks, SubLevelSchematic.Format format, ResourceLocation source){}
}
