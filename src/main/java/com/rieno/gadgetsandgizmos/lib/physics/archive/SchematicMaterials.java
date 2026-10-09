package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourcePacket;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Calculate Create item requirements and reserve real component-matching worker stock
public final class SchematicMaterials{
    private SchematicMaterials(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Collect block costs using Create's registered special requirements
    public static List<Requirement> requirements(ServerLevel level, SubLevelSchematic schematic){
        List<Requirement> res = new ArrayList<>();
        for(var body : schematic.bodies()) for(var block : body.blocks()){
            var state = block.state();
            if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue;
            if(state.hasProperty(BlockStateProperties.BED_PART)
                    && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) continue;
            BlockEntity be = SchematicBlockData.create(level, block.pos(), state, block.data());
            ItemRequirement req = ItemRequirement.of(state, be);
            if(req.isInvalid()) throw new IllegalArgumentException("No survival schematic requirement exists for " + state.getBlock().getName().getString());
            for(var stack : req.getRequiredItems()){
                if(stack.stack.isEmpty()) continue;
                boolean strict = stack instanceof ItemRequirement.StrictNbtStackRequirement;
                boolean damage = stack.usage == ItemRequirement.ItemUseType.DAMAGE;
                int idx = -1;
                for(int i = 0; i < res.size(); i++){
                    Requirement prev = res.get(i);
                    if(prev.strict() == strict && prev.damage() == damage
                            && (strict ? ItemStack.isSameItemSameComponents(prev.stack(), stack.stack)
                            : ItemStack.isSameItem(prev.stack(), stack.stack))){ idx = i; break; }
                }
                if(idx < 0) res.add(new Requirement(stack.stack.copyWithCount(1), stack.stack.getCount(), strict, damage));
                else{
                    Requirement prev = res.get(idx);
                    res.set(idx, new Requirement(prev.stack(), Math.addExact(prev.amount(), stack.stack.getCount()), strict, damage));
                }
            }
        }
        res.sort(Comparator.comparing(Requirement::strict).reversed().thenComparing(req -> req.key().id().toString()));
        return List.copyOf(res);
    }

    // Account for stock shared by multiple requirements without extracting anything
    public static List<Shortage> shortages(List<Requirement> requirements, List<? extends WorkerEndpoint> endpoints){
        Map<WorkerEndpoint, Map<WorkerResourceKey, Long>> used = new HashMap<>();
        List<Shortage> res = new ArrayList<>();
        for(Requirement req : requirements){
            long remaining = req.count();
            for(WorkerEndpoint endpoint : endpoints){
                if(!endpoint.isAvailable() || !endpoint.canExtract(req.key())) continue;
                long available = endpoint.availableMatchingItem(req.key(), req::matches);
                if(!req.strict() || req.damage()) available -= used.getOrDefault(endpoint, Map.of()).getOrDefault(req.key(), 0L);
                long take = Math.min(remaining, Math.max(0, available));
                used.computeIfAbsent(endpoint, val -> new HashMap<>()).merge(req.key(), take, Long::sum);
                remaining -= take;
            }
            res.add(new Shortage(req, req.count() - remaining, remaining));
        }
        return List.copyOf(res);
    }

    // Withdraw the complete bill before a build starts and refund a failed reservation
    public static Reservation reserve(ServerPlayer player, Vec3 refundPos, List<Requirement> requirements,
                                      List<? extends WorkerEndpoint> endpoints){
        if(!player.getServer().isSameThread()) throw new IllegalStateException("Material reservation requires the server thread");
        if(shortages(requirements, endpoints).stream().anyMatch(row -> row.missing() > 0)) throw new IllegalArgumentException("Schematic materials are still missing");
        Reservation res = new Reservation(player, refundPos);
        try{
            for(Requirement req : requirements){
                long remaining = req.count();
                for(WorkerEndpoint endpoint : endpoints){
                    if(!endpoint.isAvailable() || !endpoint.canExtract(req.key())) continue;
                    while(remaining > 0){
                        WorkerResourcePacket packet = endpoint.extractMatchingItem(req.key(), remaining, req::matches, false);
                        if(packet.isEmpty()) break;
                        res.receipts.add(new Receipt(endpoint, packet, req.damage() ? req.amount() : 0));
                        remaining -= packet.amount();
                    }
                }
                if(remaining != 0) throw new IllegalArgumentException("Worker stock changed; refresh the material list");
            }
            return res;
        }catch(RuntimeException err){ res.refund(); throw err; }
    }

    // Describe one component-aware consume or tool requirement
    public record Requirement(ItemStack stack, int amount, boolean strict, boolean damage){
        // Keep caller stacks independent from the material plan
        public Requirement{
            if(amount < 1) throw new IllegalArgumentException("Material counts must be positive");
            stack = stack.copyWithCount(1);
        }
        // Return an independent item example
        @Override public ItemStack stack(){ return stack.copy(); }
        // Resolve the worker resource key
        public WorkerResourceKey key(){ return new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem())); }
        // Reserve one durable tool or the entire consumed quantity
        public int count(){ return damage ? 1 : amount; }
        // Match components when Create requires them and require enough tool durability
        public boolean matches(ItemStack val){
            return (strict ? ItemStack.isSameItemSameComponents(stack, val) : ItemStack.isSameItem(stack, val))
                    && (!damage || val.isDamageableItem() && val.getMaxDamage() - val.getDamageValue() >= amount);
        }
    }

    // Expose required, available and missing quantities to any consumer UI
    public record Shortage(Requirement requirement, long available, long missing){}

    // Hold withdrawn packets until construction succeeds or is rolled back
    public static final class Reservation{
        private final ServerPlayer player;
        private final ServerLevel level;
        private final Vec3 refundPos;
        private final List<Receipt> receipts = new ArrayList<>();
        private boolean closed;

        // Keep the original player and world for refund fallback
        private Reservation(ServerPlayer player, Vec3 refundPos){
            this.player = player; this.level = player.serverLevel(); this.refundPos = refundPos;
        }

        // Consume materials and return tools with their construction wear applied
        public void commit(){
            if(closed) return;
            closed = true;
            for(Receipt receipt : receipts){
                if(receipt.damage() == 0) continue;
                ItemStack stack = ItemStack.parseOptional(level.registryAccess(), receipt.packet().payload().getCompound("Stack"));
                stack.setDamageValue(stack.getDamageValue() + receipt.damage());
                if(stack.getDamageValue() >= stack.getMaxDamage()) continue;
                var data = receipt.packet().payload().copy();
                data.put("Stack", stack.saveOptional(level.registryAccess()));
                returnPacket(receipt.endpoint(), new WorkerResourcePacket(receipt.packet().resource(), 1, data));
            }
        }

        // Return every withdrawn packet once after an aborted build
        public void refund(){
            if(closed) return;
            closed = true;
            for(Receipt receipt : receipts) returnPacket(receipt.endpoint(), receipt.packet());
        }

        // Prefer original storage and retain leftovers in player inventory or world drops
        private void returnPacket(WorkerEndpoint endpoint, WorkerResourcePacket packet){
            long accepted = 0;
            try{
                if(endpoint.isAvailable() && endpoint.canInsert(packet.resource())) accepted = endpoint.insert(packet, false);
            }catch(RuntimeException ignored){}
            long remaining = packet.amount() - Math.max(0, Math.min(packet.amount(), accepted));
            ItemStack template = ItemStack.parseOptional(level.registryAccess(), packet.payload().getCompound("Stack"));
            if(template.isEmpty()) throw new IllegalStateException("Reserved material packet contains no item");
            while(remaining > 0){
                int count = (int) Math.min(remaining, template.getMaxStackSize());
                ItemStack stack = template.copyWithCount(count);
                if(player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) player.getInventory().add(stack);
                if(!stack.isEmpty()) level.addFreshEntity(new ItemEntity(level, refundPos.x, refundPos.y, refundPos.z, stack));
                remaining -= count;
            }
        }
    }

    // Retain the source endpoint and exact withdrawn item components
    private record Receipt(WorkerEndpoint endpoint, WorkerResourcePacket packet, int damage){}
}
