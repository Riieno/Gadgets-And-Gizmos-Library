package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

// Keep editor undo documents as differences while preserving collection restore order
public final class DocumentHistory<T> extends AbstractCollection<T>{
    private final CompoundTagHistory history;
    private final Function<T, CompoundTag> encode;
    private final Function<CompoundTag, T> decode;

    public DocumentHistory(int capacity, Function<T, CompoundTag> encode, Function<CompoundTag, T> decode){
        history = new CompoundTagHistory(capacity);
        this.encode = encode;
        this.decode = decode;
    }

    public void push(T val){ history.push(encode.apply(val)); }

    public T pop(){
        if(isEmpty()) throw new NoSuchElementException();
        T val = decode.apply(history.get(0));
        history.remove(0);
        return val;
    }

    public T pollFirst(){ return isEmpty() ? null : pop(); }

    public T removeLast(){
        if(isEmpty()) throw new NoSuchElementException();
        int idx = history.size() - 1;
        T val = decode.apply(history.get(idx));
        history.remove(idx);
        return val;
    }

    @Override public boolean add(T val){ history.append(encode.apply(val)); return true; }
    @Override public int size(){ return history.size(); }
    @Override public void clear(){ history.clear(); }

    @Override public Iterator<T> iterator(){
        return new Iterator<>(){
            private int idx;
            @Override public boolean hasNext(){ return idx < history.size(); }
            @Override public T next(){
                if(!hasNext()) throw new NoSuchElementException();
                return decode.apply(history.get(idx++));
            }
        };
    }
}
