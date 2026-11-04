package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

// Resolve sound labels, owning mods and sound-event tags for searchable pickers
public final class SoundCatalog{
    private static final Map<ResourceLocation, Supplier<ItemStack>> ICONS = new LinkedHashMap<>();

    private SoundCatalog(){}

    public record Entry(ResourceLocation id, Component name, ItemStack icon, ItemSearchQuery.Entry searchable){
        public Entry{
            Objects.requireNonNull(id); Objects.requireNonNull(name);
            Objects.requireNonNull(icon); Objects.requireNonNull(searchable);
        }
    }

    // Include every registered sound, retaining registry IDs as the saved selection
    public static List<Entry> registered(SoundManager manager){
        Minecraft client = Minecraft.getInstance();
        HolderLookup.Provider registries = client.level != null ? client.level.registryAccess()
                : client.getConnection() == null ? null : client.getConnection().registryAccess();
        return registered(manager, registries);
    }

    // Resolve song descriptions and disc items from the current world's synced registries
    public static List<Entry> registered(SoundManager manager, HolderLookup.Provider registries){
        var metadata = new SoundCatalogMetadata(registries);
        return registeredIds().stream().map(id -> {
            var event = manager.getSoundEvent(id);
            Component subtitle = event == null ? null : event.getSubtitle();
            Component name = metadata.name(id);
            if(name == null) name = subtitle == null ? Component.literal(id.getPath().replace('.', ' ').replace('_', ' ')) : subtitle;
            String mod = ModList.get() == null ? id.getNamespace() : ModList.get().getModContainerById(id.getNamespace())
                    .map(container -> container.getModInfo().getDisplayName()).orElse(id.getNamespace());
            List<String> tags = BuiltInRegistries.SOUND_EVENT.getHolder(id).stream()
                    .flatMap(holder -> holder.tags()).map(tag -> tag.location().toString()).toList();
            Supplier<ItemStack> custom = ICONS.get(id);
            ItemStack icon = custom == null ? ItemStack.EMPTY : custom.get();
            icon = icon == null || icon.isEmpty() ? metadata.icon(id) : icon.copy();
            return new Entry(id, name, icon, new ItemSearchQuery.Entry(id.toString(), name.getString(), mod, tags));
        }).toList();
    }

    // Register an owning mod's explicit icon when its sound and item IDs differ
    public static void registerIcon(ResourceLocation sound, Supplier<ItemStack> icon){
        Objects.requireNonNull(sound); Objects.requireNonNull(icon);
        if(ICONS.putIfAbsent(sound, icon) != null) throw new IllegalArgumentException("Duplicate sound icon: " + sound);
    }

    public static List<ResourceLocation> registeredIds(){ return BuiltInRegistries.SOUND_EVENT.keySet().stream().sorted().toList(); }
}
