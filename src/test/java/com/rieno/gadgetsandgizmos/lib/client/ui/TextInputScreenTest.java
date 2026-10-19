package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Preserve a confirmation callback's screen transition after the dialog closes
class TextInputScreenTest{
    @Test void confirmationCanLeaveTheParentForReaderMode(){
        List<String> events = new ArrayList<>();
        Dialog dialog = new Dialog(events, val -> events.add("reader"));
        dialog.submit();
        assertEquals(List.of("parent", "reader"), events);
    }

    @Test void cancelReturnsToTheParentWithoutRunningTheAction(){
        List<String> events = new ArrayList<>();
        Dialog dialog = new Dialog(events, val -> events.add("reader"));
        dialog.onClose();
        assertEquals(List.of("parent"), events);
    }

    private static final class Dialog extends TextInputScreen{
        private final List<String> events;
        private Dialog(List<String> events, Consumer<String> accept){
            super(null, Component.empty(), "", accept, 64);
            this.events = events;
        }
        private void submit(){ confirm(); }
        @Override public void onClose(){ events.add("parent"); }
    }
}
