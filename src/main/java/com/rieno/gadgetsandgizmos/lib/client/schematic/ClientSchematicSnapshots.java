package com.rieno.gadgetsandgizmos.lib.client.schematic;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.schematic.SchematicSnapshotPackets;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicSnapshotProvider;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicSnapshotScope;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicSnapshotTransfer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

// Wait for authoritative data before allowing the client to save or upload its schematic
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class ClientSchematicSnapshots{
    private static Pending pending;
    private static boolean capturing;

    private ClientSchematicSnapshots(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Return true when capture is deferred; selections without providers keep their ordinary path
    public static boolean request(Level level, BlockPos first, BlockPos second, Runnable capture){
        if(capturing || level == null || first == null || second == null) return false;
        if(Minecraft.getInstance().player == null || level != Minecraft.getInstance().level) return false;
        if(pending != null) return true;
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        var positions = new java.util.ArrayList<BlockPos>();
        int view = Minecraft.getInstance().options.getEffectiveRenderDistance() + 2;
        BlockPos center = Minecraft.getInstance().player.blockPosition();
        int minX = Math.max(min.getX() >> 4, (center.getX() >> 4) - view);
        int maxX = Math.min(max.getX() >> 4, (center.getX() >> 4) + view);
        int minZ = Math.max(min.getZ() >> 4, (center.getZ() >> 4) - view);
        int maxZ = Math.min(max.getZ() >> 4, (center.getZ() >> 4) + view);
        for(int x = minX; x <= maxX; x++){
            for(int z = minZ; z <= maxZ; z++){
                var chunk = level.getChunk(x, z, ChunkStatus.FULL, false);
                if(!(chunk instanceof LevelChunk loaded)) continue;
                for(var blockEntity : loaded.getBlockEntities().values()){
                    BlockPos pos = blockEntity.getBlockPos();
                    if(blockEntity instanceof SchematicSnapshotProvider
                            && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) positions.add(pos.immutable());
                }
            }
        }
        if(positions.isEmpty()) return false;
        if(positions.size() > SchematicSnapshotPackets.MAX_BLOCKS){ fail(); return true; }
        pending = new Pending(level, UUID.randomUUID(), positions, capture);
        PacketDistributor.sendToServer(new SchematicSnapshotPackets.Request(pending.id, positions));
        return true;
    }

    // Accept only ordered chunks belonging to the current selection and resume after complete validation
    public static void accept(SchematicSnapshotPackets.Segment segment){
        Pending cur = pending;
        if(cur == null || !cur.id.equals(segment.id())) return;
        if(cur.level != Minecraft.getInstance().level || !segment.success()){ fail(); return; }
        try{
            cur.transfer.accept(segment);
            if(cur.transfer.isComplete()){
                pending = null;
                capturing = true;
                try(var scope = SchematicSnapshotScope.enter(cur.transfer.snapshots())){ cur.capture.run(); }
                finally{ capturing = false; }
            }
        }catch(RuntimeException err){ fail(); }
    }

    // Retire unanswered requests and never resume them after changing worlds
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(pending != null && (++pending.ticks > 600 || pending.level != Minecraft.getInstance().level)) fail();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut evt){ pending = null; }

    private static void fail(){
        pending = null;
        var player = Minecraft.getInstance().player;
        if(player != null) player.displayClientMessage(Component.literal("Could not save schematic block data. Please try again."), false);
    }

    private static final class Pending{
        private final Level level;
        private final UUID id;
        private final Runnable capture;
        private final SchematicSnapshotTransfer transfer;
        private int ticks;

        private Pending(Level level, UUID id, List<BlockPos> positions, Runnable capture){
            this.level = level;
            this.id = id;
            this.transfer = new SchematicSnapshotTransfer(id, positions);
            this.capture = capture;
        }
    }
}
