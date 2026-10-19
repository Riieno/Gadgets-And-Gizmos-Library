package com.rieno.gadgetsandgizmos.lib.client.tablet;

public interface TabletAppClientState{
    // Persist only editable values, without retaining screens or live world objects
    default net.minecraft.nbt.CompoundTag saveDraft(){ return new net.minecraft.nbt.CompoundTag(); }

    // Restore this app's editable values when its tablet is reopened
    default void loadDraft(net.minecraft.nbt.CompoundTag draft){}
    // Create the state for one screen, never global
    // TabletAppClientScale createScreenSize();

    // void render (TabletAppClientContext ctx, TabletAppClientState state);

    // default boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState state, double mouseX, double mouseY, int button){ return false;}
    // default boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState state, double mouseX, double mouseY, double scrollX, double scrollY){ return false;}
    // default boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState state, int keyCode, int scanCode, int modifiers){ return false;}
    // default boolean charTyped(TabletAppClientContext ctx, TabletAppClientState state, char codePoint, int modifiers){ return false;}
}
