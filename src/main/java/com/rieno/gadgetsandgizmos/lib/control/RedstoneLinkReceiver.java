package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.function.Supplier;

// Own a passive Create receiver independently of a controller's applied channel bindings
public final class RedstoneLinkReceiver implements IRedstoneLinkable, DistanceIndependentLink, AutoCloseable{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Supplier<BlockPos> position;
    private Level level;
    private Couple<RedstoneLinkNetworkHandler.Frequency> key;
    private int strength;
    private boolean closed;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public RedstoneLinkReceiver(Supplier<BlockPos> position){ this.position = Objects.requireNonNull(position); }

    // Refresh on the owning thread; rebinding and dimension changes release the old network
    public void bind(Level next, ItemStack first, ItemStack second){
        if(closed) throw new IllegalStateException("Receiver is closed");
        var nextKey = Couple.create(RedstoneLinkNetworkHandler.Frequency.of(first),
                RedstoneLinkNetworkHandler.Frequency.of(second));
        boolean bound = next != null && (!first.isEmpty() || !second.isEmpty());
        if(level == next && Objects.equals(key, nextKey) && bound){
            var network = Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(level, this);
            if(network.contains(this)) return;
        }
        detach();
        key = nextKey;
        if(!bound) return;
        level = next;
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);
        int signal = 0;
        for(var source : Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(level, this)){
            if(source != this && source.isAlive()) signal = Math.max(signal, source.getTransmittedStrength());
        }
        setReceivedStrength(signal);
    }

    public int strength(){ return strength; }
    @Override public int getTransmittedStrength(){ return 0; }
    @Override public void setReceivedStrength(int signal){ strength = Mth.clamp(signal, 0, 15); }
    @Override public boolean isListening(){ return level != null && !closed; }
    @Override public boolean isAlive(){ return level != null && !closed; }
    @Override public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey(){ return key; }
    @Override public BlockPos getLocation(){ return position.get(); }

    @Override public void close(){
        if(closed) return;
        detach();
        closed = true;
    }

    private void detach(){
        if(level != null) Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
        level = null;
        strength = 0;
    }
}
