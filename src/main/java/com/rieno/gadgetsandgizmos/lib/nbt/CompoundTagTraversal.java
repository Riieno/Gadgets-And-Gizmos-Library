package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

// Visit nested compounds before mutating their own keys without copying every key set
public final class CompoundTagTraversal{
    private CompoundTagTraversal(){}

    public static int visitPostOrder(Tag root, Predicate<CompoundTag> visitor){
        return visitPostOrder(root, visitor, (parent, key) -> true);
    }

    // The visitor may change the current compound after its children have been visited
    public static int visitPostOrder(Tag root, Predicate<CompoundTag> visitor,
                                     BiPredicate<CompoundTag, String> descend){
        int changed = 0;
        if(root instanceof CompoundTag compound){
            for(String key : compound.getAllKeys()){
                Tag child = compound.get(key);
                if((child instanceof CompoundTag || child instanceof ListTag) && descend.test(compound, key)){
                    changed += visitPostOrder(child, visitor, descend);
                }
            }
            if(visitor.test(compound)) changed++;
        }else if(root instanceof ListTag list && (list.getElementType() == Tag.TAG_COMPOUND || list.getElementType() == Tag.TAG_LIST)){
            for(int idx = 0; idx < list.size(); idx++) changed += visitPostOrder(list.get(idx), visitor, descend);
        }
        return changed;
    }
}
