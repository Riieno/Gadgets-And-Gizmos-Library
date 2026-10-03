package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import java.util.List;
import static org.mockito.Mockito.*;

final class InventoryTestBootstrap{
    private InventoryTestBootstrap(){}

    static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }
}
