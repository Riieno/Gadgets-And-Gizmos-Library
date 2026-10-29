package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.nbt.CompoundTag;

// Persist a mounted display's app tab and presentation independently of its viewers
public final class MountedTabletState{
    private String tab = "contents";
    private Presentation presentation = Presentation.CONTENTS;

    public String tab(){ return tab; }
    public Presentation presentation(){ return presentation; }

    public boolean setPresentation(Presentation val){
        if(val == null || val == presentation) return false;
        presentation = val;
        return true;
    }

    // Persist names so enum ordering never changes saved presentation settings
    public enum Presentation{
        CONTENTS("contents", false, true), TABLET("tablet", true, false),
        NONE("none", false, false), BOTH("both", true, true);

        private final String id;
        private final boolean tablet;
        private final boolean contents;
        Presentation(String id, boolean tablet, boolean contents){ this.id = id; this.tablet = tablet; this.contents = contents; }
        public String id(){ return id; }
        public boolean tablet(){ return tablet; }
        public boolean contents(){ return contents; }
        public Presentation next(boolean hasContents){
            if(!hasContents) return tablet ? NONE : TABLET;
            return switch(this){ case CONTENTS -> TABLET; case TABLET -> NONE; case NONE -> BOTH; case BOTH -> CONTENTS; };
        }
        public static Presentation fromId(String id){
            for(var val : values()) if(val.id.equals(id)) return val;
            return CONTENTS;
        }
    }

    public boolean select(String value){
        if(value == null || value.isBlank() || value.length() > 64 || value.equals(tab)) return false;
        tab = value;
        return true;
    }

    public void read(CompoundTag tag){
        tab = tag.contains("MountedTabletTab") ? tag.getString("MountedTabletTab") : "contents";
        presentation = Presentation.fromId(tag.getString("MountedTabletPresentation"));
    }

    public void write(CompoundTag tag){
        tag.putString("MountedTabletTab", tab);
        tag.putString("MountedTabletPresentation", presentation.id());
    }

    public interface Host{
        MountedTabletState mountedTabletState();
    }
}
