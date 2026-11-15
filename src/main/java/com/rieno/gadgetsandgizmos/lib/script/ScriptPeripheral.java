package com.rieno.gadgetsandgizmos.lib.script;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;

// Describe live peripheral names and methods without loading the optional CC engine on clients
public record ScriptPeripheral(String name, String type, List<String> aliases, List<String> methods, List<String> references){
    public ScriptPeripheral{
        aliases = aliases.stream().distinct().sorted().toList();
        methods = methods.stream().distinct().sorted().toList();
        references = references.stream().distinct().sorted().toList();
    }

    public boolean accepts(String val){ return name.equals(val) || aliases.contains(val); }
    public String preferredName(){ return aliases.isEmpty() ? name : aliases.getFirst(); }

    public CompoundTag toTag(){
        var tag = new CompoundTag(); tag.putString("Name", name); tag.putString("Type", type);
        tag.put("Aliases", strings(aliases)); tag.put("Methods", strings(methods)); tag.put("References", strings(references));
        return tag;
    }

    public static ScriptPeripheral fromTag(CompoundTag tag){
        return new ScriptPeripheral(tag.getString("Name"), tag.getString("Type"), strings(tag, "Aliases"), strings(tag, "Methods"), strings(tag, "References"));
    }

    private static ListTag strings(List<String> vals){ var res = new ListTag(); vals.forEach(val -> res.add(StringTag.valueOf(val))); return res; }
    private static List<String> strings(CompoundTag tag, String key){ return tag.getList(key, Tag.TAG_STRING).stream().map(Tag::getAsString).toList(); }
}
