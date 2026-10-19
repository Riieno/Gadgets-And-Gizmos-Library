package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

// Track the machine output that existed before a worker submitted its input
public record WorkerOutputLedger(Map<WorkerResourceKey, Long> baseline){
    public WorkerOutputLedger{
        baseline = baseline == null ? Map.of() : Map.copyOf(baseline);
    }

    // Capture actual output inventory contents at the start of one processing visit
    public static WorkerOutputLedger capture(WorkerEndpointSnapshot snapshot){
        Map<WorkerResourceKey, Long> amounts = new LinkedHashMap<>();
        if(snapshot != null){
            for(var resource : snapshot.resources()) amounts.merge(resource.resource(), resource.amount(), Long::sum);
        }
        return new WorkerOutputLedger(amounts);
    }

    // Count only output added since the input was submitted
    public long produced(WorkerResourceKey resource, long available){
        return Math.max(0L, available - baseline.getOrDefault(resource, 0L));
    }

    // Confirm that a claimed packet was actually removed from a live output port
    public static boolean confirmsExtraction(WorkerResourcePacket packet, long before, long after){
        return packet != null && !packet.isEmpty() && confirmsExtraction(packet.amount(), before, after);
    }

    // Validate one handler extraction before it becomes worker cargo
    public static boolean confirmsExtraction(long amount, long before, long after){
        return amount > 0L && before >= amount && before - Math.max(0L, after) >= amount;
    }

    // Save the baseline with an interrupted machine order
    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        baseline.forEach((resource, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("Resource", resource.toTag());
            entry.putLong("Amount", amount);
            entries.add(entry);
        });
        tag.put("Entries", entries);
        return tag;
    }

    // Restore an interrupted machine order without claiming earlier output
    public static WorkerOutputLedger fromTag(CompoundTag tag){
        Map<WorkerResourceKey, Long> amounts = new LinkedHashMap<>();
        if(tag != null){
            for(Tag entry : tag.getList("Entries", Tag.TAG_COMPOUND)){
                CompoundTag row = (CompoundTag)entry;
                amounts.put(WorkerResourceKey.fromTag(row.getCompound("Resource")),
                        Math.max(0L, row.getLong("Amount")));
            }
        }
        return new WorkerOutputLedger(amounts);
    }
}
