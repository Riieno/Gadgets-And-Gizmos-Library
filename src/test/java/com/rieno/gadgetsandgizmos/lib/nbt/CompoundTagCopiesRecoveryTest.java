package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompoundTagCopiesRecoveryTest{
    @Test void absentPhysicalFieldsRemoveStaleSnapshotValues(){
        CompoundTag stored = new CompoundTag();
        stored.putString("Attachment", "old_block");
        stored.putString("Owner", "old_player");
        stored.putString("Program", "retained_graph");
        CompoundTag recovered = CompoundTagCopies.copyReplacing(stored, new CompoundTag(),
                Set.of("Attachment", "Owner"));
        assertFalse(recovered.contains("Attachment"));
        assertFalse(recovered.contains("Owner"));
        assertEquals("retained_graph", recovered.getString("Program"));
        assertEquals("old_block", stored.getString("Attachment"));
    }

    @Test void replacementFieldsAndProgramPayloadsRemainIndependent(){
        CompoundTag stored = new CompoundTag();
        CompoundTag graph = new CompoundTag();
        graph.putString("Name", "saved_graph");
        stored.put("Program", graph);
        CompoundTag physical = new CompoundTag();
        CompoundTag attachment = new CompoundTag();
        attachment.putString("Mount", "wall");
        physical.put("Attachment", attachment);
        physical.putString("Program", "unrelated_override");
        CompoundTag recovered = CompoundTagCopies.copyReplacing(stored, physical, Set.of("Attachment"));
        attachment.putString("Mount", "floor");
        graph.putString("Name", "changed_graph");
        assertEquals("wall", recovered.getCompound("Attachment").getString("Mount"));
        assertEquals("saved_graph", recovered.getCompound("Program").getString("Name"));
        recovered.getCompound("Attachment").putString("Mount", "ceiling");
        assertEquals("floor", attachment.getString("Mount"));
    }

    @Test void missingSnapshotsStillApplyAuthoritativeAbsence(){
        CompoundTag stored = new CompoundTag();
        stored.putString("Attachment", "old_block");
        assertFalse(CompoundTagCopies.copyReplacing(stored, null, Set.of("Attachment")).contains("Attachment"));
        assertEquals(stored, CompoundTagCopies.copyReplacing(stored, new CompoundTag(), null));
        CompoundTag physical = new CompoundTag();
        physical.putString("Attachment", "new_block");
        assertEquals("new_block", CompoundTagCopies.copyReplacing(null, physical,
                Set.of("Attachment")).getString("Attachment"));
    }
}
