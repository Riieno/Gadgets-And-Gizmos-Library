package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CompoundTagHistoryTest{
    @Test
    void compactHistoryMatchesCompleteDocumentsAfterArbitraryRollbacks(){
        var history = new CompoundTagHistory(12);
        var expected = new ArrayList<CompoundTag>();
        var random = new Random(1234);
        for(int step = 0; step < 200; step++){
            if(expected.isEmpty() || random.nextBoolean()){
                var tag = expected.isEmpty() ? new CompoundTag() : expected.getFirst().copy();
                tag.putInt("field" + random.nextInt(8), step);
                tag.remove("field" + random.nextInt(8));
                history.push(tag);
                expected.addFirst(tag.copy());
                if(expected.size() > 12) expected.removeLast();
                tag.putInt("external_mutation", 1);
            }else{
                int idx = random.nextInt(expected.size());
                history.remove(idx);
                expected.remove(idx);
            }
            assertEquals(expected.size(), history.size());
            for(int idx = 0; idx < expected.size(); idx++) assertEquals(expected.get(idx), history.get(idx));
        }
        history.clear();
        assertEquals(0, history.size());
    }
}
