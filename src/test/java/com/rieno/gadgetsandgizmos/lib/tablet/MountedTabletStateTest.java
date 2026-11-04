package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MountedTabletStateTest{
    @Test void legacyTabsLoadWithHiddenTabletsAndVisibleContents(){
        var tag = new CompoundTag(); tag.putString("MountedTabletTab", "energy");
        var state = new MountedTabletState(); state.read(tag);
        assertEquals("energy", state.tab());
        assertFalse(state.presentation().tablet()); assertTrue(state.presentation().contents());
    }
    @Test void everyPresentationSurvivesSaveAndReload(){
        for(var mode : MountedTabletState.Presentation.values()){
            var state = new MountedTabletState(); state.select("items"); state.setPresentation(mode);
            var tag = new CompoundTag(); state.write(tag);
            var loaded = new MountedTabletState(); loaded.read(tag);
            assertEquals(mode, loaded.presentation()); assertEquals("items", loaded.tab());
        }
    }
    @Test void tankAndTabletCyclesRemainSeparate(){
        var mode = MountedTabletState.Presentation.CONTENTS;
        for(var next : new MountedTabletState.Presentation[]{MountedTabletState.Presentation.TABLET,
                MountedTabletState.Presentation.NONE, MountedTabletState.Presentation.BOTH, MountedTabletState.Presentation.CONTENTS}){
            mode = mode.next(true); assertEquals(next, mode);
        }
        assertEquals(MountedTabletState.Presentation.TABLET, mode.next(false));
        assertEquals(MountedTabletState.Presentation.NONE, mode.next(false).next(false));
        assertEquals(MountedTabletState.Presentation.CONTENTS, MountedTabletState.Presentation.fromId("future_mode"));
    }
}
