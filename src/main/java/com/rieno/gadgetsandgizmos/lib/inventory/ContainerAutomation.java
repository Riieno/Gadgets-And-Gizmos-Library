package com.rieno.gadgetsandgizmos.lib.inventory;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Persist container ownership and desired stock settings
public final class ContainerAutomation{
    private UUID owner;
    private UUID controllerSubLevelId;
    private BlockPos controllerPos;
    private final List<StockTarget> targets = new ArrayList<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Claim settings when the first player configures the container
    public boolean claim(UUID playerId){
        if(owner == null) owner = playerId;
        return playerId != null && playerId.equals(owner);
    }

    public UUID owner(){ return owner; }
    @Deprecated public boolean locked(){ return false; }
    @Deprecated public boolean push(){ return false; }
    @Deprecated public boolean pull(){ return false; }
    @Deprecated public ItemStack filter(){ return ItemStack.EMPTY; }
    public List<StockTarget> targets(){ return List.copyOf(targets); }
    public UUID controllerSubLevelId(){ return controllerSubLevelId; }
    public BlockPos controllerPos(){ return controllerPos; }
    public void setController(UUID subLevelId, BlockPos pos){ controllerSubLevelId = subLevelId; controllerPos = pos == null ? null : pos.immutable(); }
    // Ignore retired container controls while retaining their public signatures
    @Deprecated public void setLocked(boolean val){}
    @Deprecated public void setPush(boolean val){}
    @Deprecated public void setPull(boolean val){}
    @Deprecated public void setFilter(ItemStack stack){}
    @Deprecated public boolean accepts(Level level, ItemStack stack){ return true; }

    // Replace an exact stock target without exceeding the bounded target list
    public boolean setTarget(ItemStack item, int amount, String task){
        if(item == null || item.isEmpty() || amount < 0 || amount > 1_000_000 || task != null && task.length() > 64) return false;
        targets.removeIf(target -> ItemStack.isSameItemSameComponents(target.item(), item));
        if(amount == 0) return true;
        if(targets.size() >= 16) return false;
        targets.add(new StockTarget(item.copyWithCount(1), amount, task == null ? "" : task.strip()));
        return true;
    }

    // Write configuration without serializing any live inventory
    public CompoundTag toTag(HolderLookup.Provider provider){
        CompoundTag tag = new CompoundTag();
        if(owner != null) tag.putUUID("Owner", owner);
        if(controllerPos != null){
            tag.putLong("ControllerPos", controllerPos.asLong());
            if(controllerSubLevelId != null) tag.putUUID("ControllerSubLevel", controllerSubLevelId);
        }
        ListTag rows = new ListTag();
        for(StockTarget target : targets){
            CompoundTag row = new CompoundTag();
            row.put("Item", target.item().save(provider));
            row.putInt("Amount", target.amount());
            row.putString("Task", target.task());
            rows.add(row);
        }
        tag.put("Stock", rows);
        return tag;
    }

    // Read bounded saved settings
    public static ContainerAutomation fromTag(CompoundTag tag, HolderLookup.Provider provider){
        ContainerAutomation res = new ContainerAutomation();
        if(tag.hasUUID("Owner")) res.owner = tag.getUUID("Owner");
        if(tag.contains("ControllerPos")) res.controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
        if(tag.hasUUID("ControllerSubLevel")) res.controllerSubLevelId = tag.getUUID("ControllerSubLevel");
        ListTag rows = tag.getList("Stock", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < rows.size() && idx < 16; idx++){
            CompoundTag row = rows.getCompound(idx);
            res.setTarget(ItemStack.parseOptional(provider, row.getCompound("Item")), row.getInt("Amount"), row.getString("Task"));
        }
        return res;
    }

    // Keep exact components and one optional worker task name for replenishment
    public record StockTarget(ItemStack item, int amount, String task){
        public StockTarget{ item = item.copyWithCount(1); }
        @Override public ItemStack item(){ return item.copy(); }
        public long deficit(long stored, long pending){ return Math.max(0L, (long) amount - stored - pending); }
    }
}
