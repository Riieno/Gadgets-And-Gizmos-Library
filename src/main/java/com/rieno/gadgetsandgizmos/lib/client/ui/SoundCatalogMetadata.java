package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Index real sound associations once when a picker opens
final class SoundCatalogMetadata{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, Item> MATERIAL_ICONS = Map.ofEntries(
            Map.entry("wood", Items.OAK_PLANKS), Map.entry("metal", Items.IRON_BLOCK), Map.entry("wool", Items.WHITE_WOOL),
            Map.entry("wooden_door", Items.OAK_DOOR), Map.entry("wooden_trapdoor", Items.OAK_TRAPDOOR),
            Map.entry("wooden_button", Items.OAK_BUTTON), Map.entry("wooden_pressure_plate", Items.OAK_PRESSURE_PLATE),
            Map.entry("metal_pressure_plate", Items.HEAVY_WEIGHTED_PRESSURE_PLATE), Map.entry("fence_gate", Items.OAK_FENCE_GATE),
            Map.entry("hanging_sign", Items.OAK_HANGING_SIGN), Map.entry("sign", Items.OAK_SIGN));
    private final Map<ResourceLocation, Component> names = new LinkedHashMap<>();
    private final Map<ResourceLocation, ItemStack> discs = new LinkedHashMap<>();
    private final Map<ResourceLocation, ItemStack> associated = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    SoundCatalogMetadata(HolderLookup.Provider registries){
        HolderLookup.RegistryLookup<JukeboxSong> songs = registries == null ? null : registries.lookup(Registries.JUKEBOX_SONG).orElse(null);
        if(songs != null) songs.listElements().forEach(holder -> {
            JukeboxSong song = holder.value();
            names.putIfAbsent(song.soundEvent().value().getLocation(), song.description());
        });
        List<Item> items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        for(Item item : items){
            ItemStack stack = new ItemStack(item);
            var playable = stack.get(DataComponents.JUKEBOX_PLAYABLE);
            if(playable != null){
                var song = playable.song().holder().or(() -> songs == null ? java.util.Optional.empty() : songs.get(playable.song().key()));
                song.ifPresent(holder -> {
                    ResourceLocation sound = holder.value().soundEvent().value().getLocation();
                    names.putIfAbsent(sound, holder.value().description());
                    discs.putIfAbsent(sound, stack);
                });
            }
            Equipable equipment = Equipable.get(stack);
            if(equipment != null){
                ResourceLocation sound = equipment.getEquipSound().value().getLocation();
                if(equipment.getEquipmentSlot() == EquipmentSlot.CHEST) associated.put(sound, stack);
                else associated.putIfAbsent(sound, stack);
            }
        }
        BuiltInRegistries.BLOCK.stream().sorted(Comparator.comparingInt(block -> BuiltInRegistries.BLOCK.getKey(block).getPath().length()))
                .forEach(block -> {
                    if(block.asItem() == Items.AIR) return;
                    ItemStack stack = new ItemStack(block);
                    var sounds = block.defaultBlockState().getSoundType();
                    for(var sound : List.of(sounds.getBreakSound(), sounds.getStepSound(), sounds.getPlaceSound(), sounds.getHitSound(), sounds.getFallSound())){
                        associated.putIfAbsent(sound.getLocation(), stack);
                    }
                });
    }

    Component name(ResourceLocation sound){ return names.get(sound); }

    // Prefer explicit song and owner matches before shared material sounds
    ItemStack icon(ResourceLocation sound){
        ItemStack disc = discs.get(sound);
        if(disc != null) return disc.copy();
        String path = sound.getPath();
        String[] parts = path.split("[./]");
        boolean scoped = parts.length > 1 && List.of("block", "item", "entity").contains(parts[0]);
        if(scoped){
            String owner = parts[1];
            ItemStack icon = "entity".equals(parts[0]) ? entityIcon(sound.getNamespace(), owner) : itemIcon(sound.getNamespace(), owner);
            if(!icon.isEmpty()) return icon;
            if("block".equals(parts[0]) && "minecraft".equals(sound.getNamespace())){
                Item material = MATERIAL_ICONS.get(owner);
                if(material != null) return new ItemStack(material);
            }
        }
        ItemStack linked = associated.get(sound);
        if(linked != null) return linked.copy();
        String candidate = (scoped ? path.substring(parts[0].length() + 1) : path).replace('.', '_').replace('/', '_');
        while(!candidate.isEmpty()){
            ItemStack icon = itemIcon(sound.getNamespace(), candidate);
            if(!icon.isEmpty()) return icon;
            int end = candidate.lastIndexOf('_');
            if(end < 0) break;
            candidate = candidate.substring(0, end);
        }
        String family = scoped ? parts[1] : path.replace('.', '_').replace('/', '_');
        ItemStack related = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals(sound.getNamespace()) && id.getPath().endsWith("_" + family))
                .filter(id -> BuiltInRegistries.ITEM.get(id) != Items.AIR)
                .min(Comparator.comparingInt((ResourceLocation id) -> id.getPath().length()).thenComparing(ResourceLocation::toString))
                .map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id))).orElse(ItemStack.EMPTY);
        if(!related.isEmpty()) return related;
        return new ItemStack(Items.NOTE_BLOCK);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static ItemStack itemIcon(String namespace, String path){
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, path)).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack entityIcon(String namespace, String path){
        var entity = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, path)).orElse(null);
        SpawnEggItem egg = entity == null ? null : SpawnEggItem.byId(entity);
        return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
    }
}
