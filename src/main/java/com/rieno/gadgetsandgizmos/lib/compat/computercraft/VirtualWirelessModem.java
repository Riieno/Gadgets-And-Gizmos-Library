package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketNetwork;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.api.network.PacketSender;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

// Supply native modem packets to headless computers without placing modem blocks
public final class VirtualWirelessModem implements IPeripheral, PacketSender, PacketReceiver, AutoCloseable{
    private final PacketNetwork network;
    private final double range;
    private final Set<Integer> channels = new HashSet<>();
    private final Set<IComputerAccess> computers = new HashSet<>();
    private volatile Level level;
    private volatile Vec3 position = Vec3.ZERO;
    private boolean closed;

    public VirtualWirelessModem(PacketNetwork network, double range){
        if(!Double.isFinite(range) || range < 0) throw new IllegalArgumentException("Invalid modem range");
        this.network = java.util.Objects.requireNonNull(network);
        this.range = range;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update immutable coordinate snapshots on the owning game thread
    public void position(Level level, Vec3 position){ this.level = level; this.position = position; }
    @Override public Level getLevel(){ return level; }
    @Override public Vec3 getPosition(){ return position; }
    @Override public double getRange(){ return range; }
    @Override public boolean isInterdimensional(){ return false; }
    @Override public String getType(){ return "modem"; }
    @Override public boolean equals(IPeripheral other){ return other == this; }

    @Override public synchronized void attach(IComputerAccess computer){
        if(closed) return;
        if(computers.add(computer) && computers.size() == 1) network.addReceiver(this);
    }

    @Override public synchronized void detach(IComputerAccess computer){
        computers.remove(computer);
        if(computers.isEmpty()){ network.removeReceiver(this); channels.clear(); }
    }

    @Override public synchronized String getSenderID(){
        if(computers.size() != 1) return "virtual_modem";
        var computer = computers.iterator().next();
        return computer.getID() + "_" + computer.getAttachmentName();
    }

    @LuaFunction public synchronized void open(int channel) throws LuaException{
        check(channel);
        if(!channels.contains(channel) && channels.size() >= 128) throw new LuaException("Too many open channels");
        if(!closed) channels.add(channel);
    }

    @LuaFunction public synchronized boolean isOpen(int channel) throws LuaException{ check(channel); return channels.contains(channel); }
    @LuaFunction public synchronized void close(int channel) throws LuaException{ check(channel); channels.remove(channel); }
    @LuaFunction public synchronized void closeAll(){ channels.clear(); }
    @LuaFunction public boolean isWireless(){ return true; }

    @LuaFunction public void transmit(int channel, int replyChannel, Object payload) throws LuaException{
        check(channel); check(replyChannel);
        synchronized(this){ if(closed || computers.isEmpty() || level == null) return; }
        network.transmitSameDimension(new Packet(channel, replyChannel, payload, this), range);
    }

    @Override public synchronized void receiveSameDimension(Packet packet, double distance){
        if(closed || packet.sender() == this || !channels.contains(packet.channel())) return;
        for(var computer : computers) computer.queueEvent("modem_message", computer.getAttachmentName(),
                packet.channel(), packet.replyChannel(), packet.payload(), distance);
    }

    @Override public void receiveDifferentDimension(Packet packet){}

    @Override public synchronized void close(){
        closed = true; network.removeReceiver(this); computers.clear(); channels.clear();
    }

    private static void check(int channel) throws LuaException{
        if(channel < 0 || channel > 65535) throw new LuaException("Expected number in range 0-65535");
    }
}
