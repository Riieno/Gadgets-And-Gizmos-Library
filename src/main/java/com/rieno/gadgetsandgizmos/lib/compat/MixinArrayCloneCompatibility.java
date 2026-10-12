package com.rieno.gadgetsandgizmos.lib.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Preserve array cloning when Mixin 0.8.7 cannot resolve array method owners
public final class MixinArrayCloneCompatibility{
    private MixinArrayCloneCompatibility(){
    }

    // Register a workaround for one target and one mixin during plugin loading
    public static void register(String targetClassName, String mixinClassName){
        Object active = MixinEnvironment.getDefaultEnvironment().getActiveTransformer();
        if(!(active instanceof IMixinTransformer transformer)){
            throw new IllegalStateException("Mixin transformer is unavailable");
        }
        Extensions extensions = (Extensions)transformer.getExtensions();
        extensions.add(new ArrayCloneExtension(targetClassName.replace('.', '/'), mixinClassName));
    }

    // Use Arrays.copyOf without changing array type, length or shallow-copy semantics
    public static boolean replaceArrayClones(ClassNode node){
        boolean changed = false;
        for(var method : node.methods){
            boolean methodChanged = false;
            for(var instruction : method.instructions.toArray()){
                if(!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !call.owner.startsWith("[") || !call.name.equals("clone")
                        || !call.desc.equals("()Ljava/lang/Object;")) continue;
                String array = call.owner.length() == 2 ? call.owner : "[Ljava/lang/Object;";
                InsnList length = new InsnList();
                length.add(new InsnNode(Opcodes.DUP));
                length.add(new InsnNode(Opcodes.ARRAYLENGTH));
                method.instructions.insertBefore(call, length);
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "java/util/Arrays", "copyOf", "(" + array + "I)" + array, false));
                methodChanged = true;
            }
            if(methodChanged){
                method.maxStack++;
                changed = true;
            }
        }
        return changed;
    }

    private static final class ArrayCloneExtension implements IExtension{
        private final String target;
        private final String mixin;

        private ArrayCloneExtension(String target, String mixin){
            this.target = target;
            this.mixin = mixin;
        }

        @Override
        public boolean checkActive(MixinEnvironment environment){
            return true;
        }

        @Override
        public void preApply(ITargetClassContext context){
            if(!context.getClassNode().name.equals(target)) return;
            try{
                Field mixins = findField(context.getClass(), "mixins");
                for(Object entry : (Iterable<?>)mixins.get(context)){
                    if(!(entry instanceof IMixinInfo info) || !info.getClassName().equals(mixin)) continue;
                    ClassNode node = info.getClassNode(0);
                    if(!replaceArrayClones(node)) continue;
                    Method getState = info.getClass().getDeclaredMethod("getState");
                    getState.setAccessible(true);
                    Object state = getState.invoke(info);
                    findField(state.getClass(), "classNode").set(state, node);
                }
            }catch(ReflectiveOperationException err){
                throw new IllegalStateException("Cannot prepare array cloning in " + mixin, err);
            }
        }

        private static Field findField(Class<?> type, String name) throws NoSuchFieldException{
            for(Class<?> current = type; current != null; current = current.getSuperclass()){
                try{
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                }catch(NoSuchFieldException ignored){
                }
            }
            throw new NoSuchFieldException(name);
        }

        @Override
        public void postApply(ITargetClassContext context){
        }

        @Override
        public void export(MixinEnvironment environment, String name, boolean force, ClassNode node){
        }
    }
}
