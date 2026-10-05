package com.rieno.gadgetsandgizmos.lib.scm;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Select the collision bodies which remain visible during docking
public final class ScmDockingCollisionPolicy{
    private ScmDockingCollisionPolicy(){}

    // Combine own bodies with the selected docking assembly only during capture
    public static Set<UUID> excludedSubLevels(Set<UUID> ownBodies,
                                              Set<UUID> dockBodies,
                                              boolean dockingCapture){
        Set<UUID> excluded = new HashSet<>();
        if(ownBodies != null) excluded.addAll(ownBodies);
        if(dockingCapture && dockBodies != null) excluded.addAll(dockBodies);
        return Set.copyOf(excluded);
    }

    // Keep only protected moving bodies visible during docking
    public static Set<UUID> excludedExceptProtected(Set<UUID> ownBodies,
                                                     Set<UUID> dockBodies,
                                                     Set<UUID> loadedBodies,
                                                     Set<UUID> protectedBodies){
        Set<UUID> excluded = new HashSet<>();
        if(loadedBodies != null){
            for(UUID body : loadedBodies){
                if(body != null && (protectedBodies == null
                        || !protectedBodies.contains(body))) excluded.add(body);
            }
        }
        if(ownBodies != null) excluded.addAll(ownBodies);
        if(dockBodies != null) excluded.addAll(dockBodies);
        return Set.copyOf(excluded);
    }
}
