package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

// Carry shader material ids and block centres through secondary terrain compilation
public final class TerrainBlockMetadata{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Hooks IRIS = resolve();
    private static final ThreadLocal<Map<VertexConsumer, Block>> CONTEXTS = ThreadLocal.withInitial(IdentityHashMap::new);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private TerrainBlockMetadata(){}

    // Scope one terrain block's material and section-local centre to its vertex writes
    public static void renderBlock(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                   VertexConsumer vertices, Runnable draw){
        renderBlock(IRIS, level, state, pos, vertices, draw);
    }

    // Restore an outer block when a model recursively renders another block
    static void renderBlock(Hooks hooks, BlockAndTintGetter level, BlockState state, BlockPos pos,
                            VertexConsumer vertices, Runnable draw){
        if(hooks == null || !hooks.builder.isInstance(vertices)){
            draw.run();
            return;
        }
        try{
            @SuppressWarnings("unchecked")
            Object2IntMap<BlockState> ids = (Object2IntMap<BlockState>) hooks.ids.invoke(hooks.settings);
            if(ids == null){
                draw.run();
                return;
            }
            Block ctx = new Block(ids.getInt(state), (byte) state.getLightEmission(level, pos),
                    pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
            Map<VertexConsumer, Block> contexts = CONTEXTS.get();
            Block prev = contexts.put(vertices, ctx);
            try{
                hooks.begin(vertices, ctx);
                draw.run();
            }finally{
                if(prev == null){
                    contexts.remove(vertices);
                    hooks.end.invoke(vertices);
                }else{
                    contexts.put(vertices, prev);
                    hooks.begin(vertices, prev);
                }
            }
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Shader block metadata could not be applied", err);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the optional terrain writer without loading Iris on clients that lack it
    private static Hooks resolve(){
        try{
            Class<?> builder = Class.forName("net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder");
            Class<?> settings = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
            return new Hooks(builder, settings.getField("INSTANCE").get(null), settings.getMethod("getBlockStateIds"),
                    builder.getMethod("beginBlock", int.class, byte.class, byte.class, int.class, int.class, int.class),
                    builder.getMethod("endBlock"));
        }catch(ClassNotFoundException err){
            return null;
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Shader terrain metadata contract is unavailable", err);
        }
    }

    private record Block(int id, byte emission, int x, int y, int z){}

    record Hooks(Class<?> builder, Object settings, Method ids, Method begin, Method end){
        // Terrain vertices use render type zero in Iris's block writer
        private void begin(VertexConsumer vertices, Block ctx) throws ReflectiveOperationException{
            begin.invoke(vertices, ctx.id, (byte) 0, ctx.emission, ctx.x, ctx.y, ctx.z);
        }
    }
}
