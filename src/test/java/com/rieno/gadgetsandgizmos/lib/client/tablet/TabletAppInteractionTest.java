package com.rieno.gadgetsandgizmos.lib.client.tablet;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TabletAppInteractionTest{
    // Preserve editable state when the screen is replaced, while isolating tablets and connections
    @Test void restoresDraftAfterClosingAndReopening(){
        var app = ResourceLocation.withDefaultNamespace("requests");
        var renderer = mock(TabletAppClientRenderer.class);
        when(renderer.createScreenState()).thenAnswer(call -> new Draft());
        Object connection = new Object();
        UUID tablet = UUID.randomUUID();
        var session = new TabletAppClientSession();
        session.bindDrafts(connection, tablet);
        ((Draft) session.stateFor(app, renderer)).amount = "17";
        session.clear();
        var reopened = new TabletAppClientSession();
        reopened.bindDrafts(connection, tablet);
        assertEquals("17", ((Draft) reopened.stateFor(app, renderer)).amount);
        reopened.bindDrafts(connection, UUID.randomUUID());
        assertEquals("", ((Draft) reopened.stateFor(app, renderer)).amount);
        reopened.bindDrafts(new Object(), tablet);
        assertEquals("", ((Draft) reopened.stateFor(app, renderer)).amount);
    }

    // A placed projection and an open screen must share edits without an older view overwriting them on close
    @Test void synchronizesDraftsBetweenTwoViews(){
        var app = ResourceLocation.withDefaultNamespace("requests");
        var renderer = mock(TabletAppClientRenderer.class);
        when(renderer.createScreenState()).thenAnswer(call -> new Draft());
        Object connection = new Object();
        UUID tablet = UUID.randomUUID();
        var projection = new TabletAppClientSession();
        var screen = new TabletAppClientSession();
        projection.bindDrafts(connection, tablet);
        screen.bindDrafts(connection, tablet);
        projection.stateFor(app, renderer);
        ((Draft) screen.stateFor(app, renderer)).amount = "29";
        screen.saveDrafts();
        projection.clear();
        assertEquals("29", ((Draft) projection.stateFor(app, renderer)).amount);
        ((Draft) screen.stateFor(app, renderer)).amount = "31";
        screen.saveDrafts();
        assertEquals("31", ((Draft) projection.stateFor(app, renderer)).amount);
    }

    // A physical tablet must hand right-clicked inputs to its host text dialog and accept the result
    @Test void projectedInputOpensHostTextDialog(){
        var ui = mock(TabletAppClientUi.class);
        when(ui.projected()).thenReturn(true);
        doAnswer(call -> {
            Consumer<String> accept = call.getArgument(3);
            accept.accept("23");
            return null;
        }).when(ui).editText(eq("Amount"), eq("2"), eq(128), any());
        var panel = new TabletAppPanel();
        var font = mock(Font.class);
        var ctx = new TabletAppClientContext(null, null, null, mock(GuiGraphics.class), font,
                20, 30, 200, 100, null, 0, 0, null, null, ui);
        panel.setValue("amount", "2");
        panel.begin(ctx);
        panel.input(ctx, "amount", 5, 5, 80, "Amount");
        assertTrue(panel.click(30, 40, 1));
        assertEquals("23", panel.value("amount"));
        assertFalse(panel.charTyped('9'));
        verify(ui).editText(eq("Amount"), eq("2"), eq(128), any());
    }

    private static final class Draft implements TabletAppClientState{
        private String amount = "";
        @Override public CompoundTag saveDraft(){
            CompoundTag tag = new CompoundTag();
            tag.putString("Amount", amount);
            return tag;
        }
        @Override public void loadDraft(CompoundTag tag){ amount = tag.getString("Amount"); }
    }
}
