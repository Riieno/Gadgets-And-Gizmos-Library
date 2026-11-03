package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class DocumentHistoryTest{
    @Test
    void undoRedoAndFailedEditRestorePreserveDetachedDocumentsAndOrder(){
        var undo = new DocumentHistory<CompoundTag>(64, CompoundTag::copy, CompoundTag::copy);
        var redo = new DocumentHistory<CompoundTag>(64, CompoundTag::copy, CompoundTag::copy);
        CompoundTag document = new CompoundTag();
        for(int idx = 0; idx < 80; idx++){
            undo.push(document);
            document.putInt("Value", idx + 1);
        }
        assertEquals(64, undo.size());
        var retained = new ArrayDeque<>(undo);
        for(int idx = 0; idx < 5; idx++){
            redo.push(document);
            document = undo.pop();
        }
        assertEquals(75, document.getInt("Value"));
        for(int idx = 0; idx < 5; idx++){
            undo.push(document);
            document = redo.pop();
        }
        assertEquals(80, document.getInt("Value"));
        assertEquals(retained.stream().toList(), undo.stream().toList());
        undo.clear();
        undo.addAll(retained);
        assertEquals(79, undo.pollFirst().getInt("Value"));
        assertEquals(16, undo.removeLast().getInt("Value"));
    }
}
