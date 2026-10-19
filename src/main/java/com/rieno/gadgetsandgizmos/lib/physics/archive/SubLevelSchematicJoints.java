package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Restore imported attachments from metadata which travels with native saved sublevels
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class SubLevelSchematicJoints{
    static final String KEY = "gadgetsngizmos:schematic_joints";
    private static final Map<ServerLevel, Map<SubLevelSchematic.Joint, Active>> ACTIVE = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<SubLevelSchematic.Joint, Failed>> FAILED = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<ServerSubLevel, Frames>> FRAMES = new WeakHashMap<>();

    private SubLevelSchematicJoints(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read retained native plot-local frames for topology and archive consumers
    public static List<SubLevelSchematic.Joint> retained(ServerSubLevel body){
        return retained(body.getUserDataTag(), body.getUniqueId());
    }

    // Create every requested joint before releasing a completed construction
    static void install(ServerLevel level, SubLevelSchematic schematic, List<ServerSubLevel> bodies){
        if(schematic.joints().isEmpty()) return;
        Map<UUID, ServerSubLevel> mapped = new HashMap<>();
        for(int idx = 0; idx < bodies.size(); idx++) mapped.put(schematic.bodies().get(idx).id(), bodies.get(idx));
        List<Active> created = new ArrayList<>();
        try{
            for(var src : schematic.joints()){
                var first = mapped.get(src.first()); var second = mapped.get(src.second());
                var joint = new SubLevelSchematic.Joint(first.getUniqueId(), second.getUniqueId(), src.type(),
                        src.firstAnchor().add(Vec3.atLowerCornerOf(first.getPlot().getCenterBlock())),
                        src.secondAnchor().add(Vec3.atLowerCornerOf(second.getPlot().getCenterBlock())),
                        src.orientation(), src.firstAxis(), src.secondAxis());
                created.add(attach(level, joint, first, second));
            }
        }catch(ReflectiveOperationException | RuntimeException err){
            created.forEach(SubLevelSchematicJoints::remove);
            throw new IllegalArgumentException("Schematic welds could not be restored", err);
        }
        var active = ACTIVE.computeIfAbsent(level, val -> new HashMap<>());
        for(var val : created){
            CompoundTag data = val.first().getUserDataTag();
            data = data == null ? new CompoundTag() : data.copy();
            ListTag joints = data.getList(KEY, Tag.TAG_COMPOUND);
            joints.add(SubLevelSchematicFiles.writeJoint(val.joint())); data.put(KEY, joints);
            val.first().setUserDataTag(data);
            active.put(val.joint(), val);
        }
        SableAssemblyTopologyInvalidation.invalidate(level);
    }

    // Recreate attachments only when both saved bodies are loaded in their owning world
    @SubscribeEvent public static void onLevelTick(LevelTickEvent.Post evt){
        if(!(evt.getLevel() instanceof ServerLevel level)) return;
        var container = SubLevelContainer.getContainer(level);
        if(container == null) return;
        var active = ACTIVE.computeIfAbsent(level, val -> new HashMap<>());
        var failed = FAILED.computeIfAbsent(level, val -> new HashMap<>());
        var frames = FRAMES.computeIfAbsent(level, val -> new HashMap<>());
        frames.keySet().removeIf(body -> body.isRemoved() || container.getSubLevel(body.getUniqueId()) != body);
        failed.values().removeIf(val -> container.getSubLevel(val.joint().first()) != val.first()
                || container.getSubLevel(val.joint().second()) != val.second());
        active.values().removeIf(val -> {
            if(container.getSubLevel(val.joint().first()) == val.first() && !val.first().isRemoved()
                    && container.getSubLevel(val.joint().second()) == val.second() && !val.second().isRemoved()
                    && val.handle().isValid()) return false;
            remove(val);
            SableAssemblyTopologyInvalidation.invalidate(level);
            return true;
        });
        for(var body : container.getAllSubLevels()){
            if(!(body instanceof ServerSubLevel first) || first.isRemoved()) continue;
            CompoundTag data = first.getUserDataTag();
            Frames cached = frames.get(first);
            if(cached == null || cached.data() != data){
                cached = new Frames(data, retained(first)); frames.put(first, cached);
            }
            for(var joint : cached.joints()){
                if(active.containsKey(joint) || failed.containsKey(joint)) continue;
                var target = container.getSubLevel(joint.second());
                if(!(target instanceof ServerSubLevel second) || second.isRemoved()) continue;
                try{
                    active.put(joint, attach(level, joint, first, second));
                    SableAssemblyTopologyInvalidation.invalidate(level);
                }catch(ReflectiveOperationException | RuntimeException err){
                    LogUtils.getLogger().error("Cannot restore schematic weld between {} and {}", joint.first(), joint.second(), err);
                    // Keep saved frames available for a later load without retrying every second
                    failed.put(joint, new Failed(joint, first, second));
                }
            }
        }
    }

    // Let the native pipeline dispose its handles when the world unloads
    @SubscribeEvent public static void onLevelUnload(LevelEvent.Unload evt){
        if(evt.getLevel() instanceof ServerLevel level){ ACTIVE.remove(level); FAILED.remove(level); FRAMES.remove(level); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read only valid frames owned by the saved body without accepting external endpoints as owners
    static List<SubLevelSchematic.Joint> retained(CompoundTag data, UUID owner){
        if(data == null) return List.of();
        ListTag rows = data.getList(KEY, Tag.TAG_COMPOUND);
        List<SubLevelSchematic.Joint> res = new ArrayList<>();
        for(int idx = 0; idx < rows.size() && idx < 1024; idx++){
            try{
                var joint = SubLevelSchematicFiles.readJoint(rows.getCompound(idx));
                if(joint.first().equals(owner)) res.add(joint);
            }catch(RuntimeException err){ LogUtils.getLogger().warn("Ignoring invalid saved schematic weld for {}", owner); }
        }
        return List.copyOf(res);
    }

    // Create the matching native fixed, free or rotary constraint
    private static Active attach(ServerLevel level, SubLevelSchematic.Joint joint, ServerSubLevel first, ServerSubLevel second)
            throws ReflectiveOperationException{
        Vector3d posA = vector(joint.firstAnchor()), posB = vector(joint.secondAnchor());
        Object config = switch(joint.type()){
            case FIXED -> SableConstraintApi.fixedConfiguration(posA, posB, joint.orientation());
            case FREE -> SableConstraintApi.freeConfiguration(posA, posB, joint.orientation());
            case BEARING -> SableConstraintApi.rotaryConfiguration(posA, posB, vector(joint.firstAxis()), vector(joint.secondAxis()));
        };
        Object created = SableConstraintApi.addConstraint(SubLevelContainer.getContainer(level).physicsSystem().getPipeline(), first, second, config);
        if(!(created instanceof PhysicsConstraintHandle handle) || !handle.isValid())
            throw new IllegalArgumentException("The native physics pipeline rejected a schematic weld");
        handle.setContactsEnabled(false);
        return new Active(joint, first, second, handle);
    }

    // Release a valid native handle before discarding its loaded references
    private static void remove(Active val){ if(val.handle().isValid()) val.handle().remove(); }
    private static Vector3d vector(Vec3 val){ return new Vector3d(val.x, val.y, val.z); }
    private record Active(SubLevelSchematic.Joint joint, ServerSubLevel first, ServerSubLevel second, PhysicsConstraintHandle handle){}
    private record Failed(SubLevelSchematic.Joint joint, ServerSubLevel first, ServerSubLevel second){}
    private record Frames(CompoundTag data, List<SubLevelSchematic.Joint> joints){}
}
