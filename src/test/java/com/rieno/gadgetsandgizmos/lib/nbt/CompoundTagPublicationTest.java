package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompoundTagPublicationTest{
    @Test
    void changingTelemetryDoesNotResendTheUnchangedCandidateList(){
        var source = new CompoundTagPublication();
        var reader = new CompoundTagReplica();
        CompoundTag document = new CompoundTag();
        ListTag candidates = new ListTag();
        for(int idx = 0; idx < 500; idx++){
            CompoundTag unit = new CompoundTag(); unit.putInt("Id", idx); candidates.add(unit);
        }
        document.put("Candidates", candidates);
        document.putDouble("Speed", 1);
        var initial = source.publish(document);
        assertTrue(reader.accept(CompoundTagPublication.Update.fromTag(initial.toTag())));
        assertNull(source.publish(document));
        document.putDouble("Speed", 2);
        var next = source.publish(document);
        assertFalse(next.full());
        assertTrue(next.patch().toString().length() < 100);
        assertTrue(reader.accept(CompoundTagPublication.Update.fromTag(next.toTag())));
        assertEquals(document, reader.document());
        assertFalse(reader.accept(next));
        assertTrue(CompoundTagDelta.affectsPath(next.patch(), "Speed"));
        assertFalse(CompoundTagDelta.affectsPath(next.patch(), "Candidates"));
        var acknowledged = source.publish(document, true);
        assertFalse(acknowledged.full());
        assertTrue(acknowledged.patch().isEmpty());
        assertTrue(reader.accept(acknowledged));
        document.remove("Candidates");
        var removed = source.publish(document);
        assertTrue(CompoundTagDelta.affectsPath(removed.patch(), "Candidates", "Nested"));
        assertTrue(reader.accept(removed));
        assertFalse(reader.document().contains("Candidates"));
    }

    @Test
    void cacheRecoveryRequiresAFreshStreamAndPreservesDetachedInputData(){
        var source = new CompoundTagPublication();
        var reader = new CompoundTagReplica();
        CompoundTag document = new CompoundTag(); document.putInt("Value", 1);
        var initial = source.publish(document);
        document.putInt("Value", 2);
        assertEquals(1, initial.patch().getInt("Value"));
        var next = source.publish(document);
        assertFalse(reader.accept(next));
        source.reset();
        var recovered = source.publish(document);
        assertNotEquals(initial.streamId(), recovered.streamId());
        assertTrue(reader.accept(recovered));
        assertEquals(2, reader.document().getInt("Value"));
        assertNull(source.publish(document));
    }
}
