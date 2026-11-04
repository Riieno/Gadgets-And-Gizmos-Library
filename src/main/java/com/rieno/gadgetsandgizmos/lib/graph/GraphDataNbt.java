package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataValue;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Convert structured graph data without changing NBT tag kinds
public final class GraphDataNbt{
    private GraphDataNbt(){
    }

    public static GraphDataValue fromTag(Tag tag){
        Objects.requireNonNull(tag, "tag");
        return switch(tag.getId()){
            case Tag.TAG_BYTE -> new GraphDataValue.NumericValue(
                    GraphDataValue.NumberKind.BYTE, ((ByteTag) tag).getAsByte());
            case Tag.TAG_SHORT -> new GraphDataValue.NumericValue(
                    GraphDataValue.NumberKind.SHORT, ((ShortTag) tag).getAsShort());
            case Tag.TAG_INT -> new GraphDataValue.NumericValue(
                    GraphDataValue.NumberKind.INT, ((IntTag) tag).getAsInt());
            case Tag.TAG_LONG -> new GraphDataValue.NumericValue(
                    GraphDataValue.NumberKind.LONG, ((LongTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> new GraphDataValue.NumericValue(
                    GraphDataValue.NumberKind.FLOAT, ((FloatTag) tag).getAsFloat());
            case Tag.TAG_DOUBLE -> new GraphDataValue.NumericValue(
                    GraphDataValue.NumberKind.DOUBLE, ((DoubleTag) tag).getAsDouble());
            case Tag.TAG_STRING -> new GraphDataValue.StringValue(tag.getAsString());
            case Tag.TAG_LIST -> fromList((ListTag) tag);
            case Tag.TAG_COMPOUND -> fromCompound((CompoundTag) tag);
            case Tag.TAG_BYTE_ARRAY -> new GraphDataValue.ByteArrayValue(
                    toByteList(((ByteArrayTag) tag).getAsByteArray()));
            case Tag.TAG_INT_ARRAY -> new GraphDataValue.IntArrayValue(
                    Arrays.stream(((IntArrayTag) tag).getAsIntArray()).boxed().toList());
            case Tag.TAG_LONG_ARRAY -> new GraphDataValue.LongArrayValue(
                    Arrays.stream(((LongArrayTag) tag).getAsLongArray()).boxed().toList());
            default -> throw new IllegalArgumentException(
                    "Unsupported graph data tag: " + tag.getId());
        };
    }

    public static Tag toTag(GraphDataValue value){
        Objects.requireNonNull(value, "value");
        if(value instanceof GraphDataValue.NumericValue numeric){
            Number number = numeric.value();
            return switch(numeric.kind()){
                case BYTE -> ByteTag.valueOf(number.byteValue());
                case SHORT -> ShortTag.valueOf(number.shortValue());
                case INT -> IntTag.valueOf(number.intValue());
                case LONG -> LongTag.valueOf(number.longValue());
                case FLOAT -> FloatTag.valueOf(number.floatValue());
                case DOUBLE -> DoubleTag.valueOf(number.doubleValue());
            };
        }
        if(value instanceof GraphDataValue.StringValue string){
            return StringTag.valueOf(string.value());
        }
        if(value instanceof GraphDataValue.ListValue list){
            ListTag res = new ListTag();
            list.values().forEach(entry -> res.add(toTag(entry)));
            return res;
        }
        if(value instanceof GraphDataValue.CompoundValue compound){
            return toCompound(compound);
        }
        if(value instanceof GraphDataValue.ByteArrayValue array){
            byte[] res = new byte[array.values().size()];
            for(int idx = 0; idx < res.length; idx++){
                res[idx] = array.values().get(idx);
            }
            return new ByteArrayTag(res);
        }
        if(value instanceof GraphDataValue.IntArrayValue array){
            return new IntArrayTag(array.values());
        }
        if(value instanceof GraphDataValue.LongArrayValue array){
            return new LongArrayTag(array.values());
        }
        throw new IllegalArgumentException("Unsupported graph data value: " + value);
    }

    public static GraphDataValue.CompoundValue fromCompound(CompoundTag tag){
        Objects.requireNonNull(tag, "tag");
        Map<String, GraphDataValue> values = new LinkedHashMap<>();
        for(String key : tag.getAllKeys()){
            values.put(key, fromTag(Objects.requireNonNull(tag.get(key), key)));
        }
        return new GraphDataValue.CompoundValue(values);
    }

    public static CompoundTag toCompound(GraphDataValue.CompoundValue value){
        CompoundTag res = new CompoundTag();
        value.values().forEach((key, entry) -> res.put(key, toTag(entry)));
        return res;
    }

    private static GraphDataValue.ListValue fromList(ListTag tag){
        List<GraphDataValue> values = new ArrayList<>(tag.size());
        for(int idx = 0; idx < tag.size(); idx++){
            values.add(fromTag(tag.get(idx)));
        }
        return new GraphDataValue.ListValue(values);
    }

    private static List<Byte> toByteList(byte[] values){
        List<Byte> res = new ArrayList<>(values.length);
        for(byte value : values) res.add(value);
        return List.copyOf(res);
    }
}
