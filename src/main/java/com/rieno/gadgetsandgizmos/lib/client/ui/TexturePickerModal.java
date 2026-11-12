package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

// Browse resource-pack and mod textures without loading every image into GPU memory
public final class TexturePickerModal{
    private TexturePickerModal(){}

    // Read resource identifiers only and leave image loading to the visible cards
    public static List<PaintedSelectionModal.Entry> catalog(ResourceManager resources){
        return resources.listResources("textures", id -> id.getPath().endsWith(".png")).keySet().stream()
                .sorted().map(id -> new PaintedSelectionModal.Entry(id.toString(), Component.literal(id.getPath()), ItemStack.EMPTY,
                        new ItemSearchQuery.Entry(id.toString(), id.getPath(), id.getNamespace(),
                                Arrays.asList(id.getPath().split("/"))),
                        List.of(Component.literal(id.toString()), Component.literal(id.getNamespace())))).toList();
    }

    // Open the shared painted selector with texture thumbnails and staged acceptance
    public static PaintedSelectionModal open(PaintedSelectionModal.Appearance appearance, Font font, Component title,
            ResourceManager resources, ResourceLocation initial, Consumer<ResourceLocation> accepted, Runnable closed){
        return new PaintedSelectionModal(appearance, font, title, catalog(resources),
                initial == null ? List.of() : List.of(initial.toString()), false, ids -> {
                    if(!ids.isEmpty()) accepted.accept(ResourceLocation.parse(ids.getFirst()));
                }, closed).withThumbnails((graphics, bounds, entry) -> {
                    for(int y = bounds.y(); y < bounds.bottom(); y += 4){
                        for(int x = bounds.x(); x < bounds.right(); x += 4){
                            int col = ((x - bounds.x()) / 4 + (y - bounds.y()) / 4) % 2 == 0 ? 0xFFAAAAAA : 0xFF555555;
                            graphics.fill(x, y, Math.min(bounds.right(), x + 4), Math.min(bounds.bottom(), y + 4), col);
                        }
                    }
                    new GuiTextureRegion(ResourceLocation.parse(entry.id()), 1, 1, 0, 0, 1, 1)
                            .draw(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
                });
    }
}
