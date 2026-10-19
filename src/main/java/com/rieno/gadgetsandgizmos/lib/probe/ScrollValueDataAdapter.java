package com.rieno.gadgetsandgizmos.lib.probe;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

// Expose named Create value behaviours through their clamped setters and callbacks
public final class ScrollValueDataAdapter<T extends BlockEntity> implements BlockEntityDataAdapter<T>{
    private final Class<T> type;
    private final Function<T, Map<String, ScrollValueBehaviour>> behaviours;
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>(){
        @Override
        protected List<Field> computeValue(Class<?> type){
            List<Field> fields = new ArrayList<>();
            for(Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()){
                for(Field field : current.getDeclaredFields()){
                    if(!Modifier.isStatic(field.getModifiers())
                            && ScrollValueBehaviour.class.isAssignableFrom(field.getType())
                            && field.trySetAccessible()) fields.add(field);
                }
            }
            fields.sort(java.util.Comparator.comparing(Field::getName));
            return List.copyOf(fields);
        }
    };

    public ScrollValueDataAdapter(Class<T> type, Function<T, Map<String, ScrollValueBehaviour>> behaviours){
        this.type = Objects.requireNonNull(type);
        this.behaviours = Objects.requireNonNull(behaviours);
    }

    @Override
    public Class<T> targetType(){ return type; }

    @Override
    public boolean isSupplemental(){ return true; }

    @Override
    public List<BlockEntityDataPort> ports(T target){
        List<BlockEntityDataPort> ports = new ArrayList<>();
        behaviours.apply(target).forEach((id, behaviour) -> {
            if(id == null || id.isBlank() || behaviour == null) return;
            List<String> options = options(behaviour);
            String type = behaviour instanceof ScrollOptionBehaviour<?> ? "string" : "number";
            if(!BlockEntityDataAccessPolicy.isItemContentMutation(id, type))
                ports.add(new BlockEntityDataPort(id, type, BlockEntityDataPort.Access.READ_WRITE, options));
        });
        return List.copyOf(ports);
    }

    @Override
    public GraphValue read(T target, String port){
        ScrollValueBehaviour behaviour = behaviours.apply(target).get(port);
        if(behaviour == null) return null;
        if(behaviour instanceof ScrollOptionBehaviour<?> option)
            return GraphValue.string(option.get().name().toLowerCase(Locale.ROOT));
        return GraphValue.number(behaviour.getValue());
    }

    @Override
    public boolean write(T target, String port, GraphValue value){
        if(target == null || target.isRemoved() || target.getLevel() == null
                || target.getLevel().isClientSide || value == null
                || BlockEntityDataAccessPolicy.isItemContentMutation(port)) return false;
        ScrollValueBehaviour behaviour = behaviours.apply(target).get(port);
        if(behaviour == null) return false;
        int next;
        if(behaviour instanceof ScrollOptionBehaviour<?>){
            List<String> options = options(behaviour);
            String requested = value.asString().strip().replace(' ', '_').toLowerCase(Locale.ROOT);
            next = options.indexOf(requested);
            if(next < 0) return false;
        }else{
            if(!(value.value() instanceof Number number) || !Double.isFinite(number.doubleValue())) return false;
            next = (int)Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(number.doubleValue())));
        }
        behaviour.setValue(next);
        return true;
    }

    // Find named behaviour fields without discovering or invoking arbitrary setters
    public static Map<String, ScrollValueBehaviour> fieldBehaviours(BlockEntity target){
        if(target == null) return Map.of();
        Map<String, ScrollValueBehaviour> result = new LinkedHashMap<>();
        for(Field field : FIELDS.get(target.getClass())){
            try{
                if(!(field.get(target) instanceof ScrollValueBehaviour behaviour)) continue;
                if(behaviour instanceof ScrollOptionBehaviour<?> && options(behaviour).size() <= 1) continue;
                String id = field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
                result.putIfAbsent(id, behaviour);
            }catch(IllegalAccessException ignored){}
        }
        return result;
    }

    private static List<String> options(ScrollValueBehaviour behaviour){
        if(!(behaviour instanceof ScrollOptionBehaviour<?> option)) return List.of();
        Enum<?> selected = option.get();
        List<String> options = new ArrayList<>();
        for(Enum<?> value : selected.getDeclaringClass().getEnumConstants())
            options.add(value.name().toLowerCase(Locale.ROOT));
        return List.copyOf(options);
    }
}
