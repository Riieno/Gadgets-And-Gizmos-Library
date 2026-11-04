package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

// Delay an explicit inventory action while requiring the same input and an empty output.
public final class TimedItemSlotAction{
    public enum State{ IDLE, RUNNING, READY, CANCELLED }

    private ItemStack input = ItemStack.EMPTY;
    private int inputSlot;
    private int outputSlot;
    private int duration;
    private int progress;
    private int action;

    public boolean start(Container container, int inputSlot, int outputSlot, int action, int duration){
        if(isActive() || duration < 1 || container.getItem(inputSlot).isEmpty() || !container.getItem(outputSlot).isEmpty()) return false;
        this.input = container.getItem(inputSlot).copy();
        this.inputSlot = inputSlot;
        this.outputSlot = outputSlot;
        this.action = action;
        this.duration = duration;
        progress = 0;
        return true;
    }

    public State tick(Container container){
        if(!isActive()) return State.IDLE;
        if(!ItemStack.matches(input, container.getItem(inputSlot)) || !container.getItem(outputSlot).isEmpty()){
            cancel();
            return State.CANCELLED;
        }
        progress++;
        return progress >= duration ? State.READY : State.RUNNING;
    }

    public boolean isActive(){ return !input.isEmpty(); }
    public int action(){ return action; }
    public int progress(){ return progress; }
    public int duration(){ return duration; }

    public void cancel(){
        input = ItemStack.EMPTY;
        progress = 0;
    }
}
