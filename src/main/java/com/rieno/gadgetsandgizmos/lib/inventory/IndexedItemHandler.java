package com.rieno.gadgetsandgizmos.lib.inventory;

import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import java.util.List;

// Retain combined inventory semantics with indexed member lookup
public final class IndexedItemHandler extends CombinedInvWrapper{
    private final int uniformSlots;

    public IndexedItemHandler(IItemHandlerModifiable... handlers){
        super(handlers.clone());
        int slots = itemHandler.length == 0 ? 0 : itemHandler[0].getSlots();
        boolean uniform = slots > 0;
        int prev = 0;
        for(int idx = 0; idx < baseIndex.length; idx++){
            if(baseIndex[idx] < prev) throw new IllegalArgumentException("Combined inventory exceeds the slot limit");
            uniform &= baseIndex[idx] - prev == slots;
            prev = baseIndex[idx];
        }
        uniformSlots = uniform ? slots : 0;
    }

    @Override
    protected int getIndexForSlot(int slot){
        if(slot < 0 || slot >= slotCount) return -1;
        if(uniformSlots > 0) return slot / uniformSlots;
        int low = 0, high = baseIndex.length;
        while(low < high){
            int idx = (low + high) >>> 1;
            if(slot < baseIndex[idx]) high = idx;
            else low = idx + 1;
        }
        return low;
    }

    // Reuse a view only while every ordered member and slot count remains identical
    public static final class Cache{
        private IndexedItemHandler view;

        public IndexedItemHandler get(List<? extends IItemHandlerModifiable> handlers){
            if(view != null && view.itemHandler.length == handlers.size()){
                boolean unchanged = true;
                int start = 0;
                for(int idx = 0; idx < handlers.size(); idx++){
                    IItemHandlerModifiable handler = handlers.get(idx);
                    if(view.itemHandler[idx] != handler || handler.getSlots() != view.baseIndex[idx] - start){
                        unchanged = false;
                        break;
                    }
                    start = view.baseIndex[idx];
                }
                if(unchanged) return view;
            }
            view = new IndexedItemHandler(handlers.toArray(IItemHandlerModifiable[]::new));
            return view;
        }
    }
}
