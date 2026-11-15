package com.rieno.gadgetsandgizmos.lib.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptSourceEditsTest{
    @Test void unchangedCodeAndUndoneEditsDoNotSave(){
        var edits = new ScriptSourceEdits();
        edits.edit("node", "old", "old");
        assertNull(edits.submit(1));
        edits.edit("node", "old", "new");
        edits.edit("node", "new", "old");
        assertFalse(edits.pending());
        assertNull(edits.submit(2));
    }

    @Test void newerCodeWaitsForItsOwnAcknowledgementAndOtherFunctionsStayIndependent(){
        var edits = new ScriptSourceEdits();
        edits.edit("first", "old", "new");
        var first = edits.submit(1);
        edits.edit("first", "new", "newer");
        assertNull(edits.submit(2));
        edits.edit("second", "second old", "second new");
        assertEquals("second", edits.submit(3).id());
        assertEquals(first, edits.acknowledge(1, true));
        var next = edits.submit(4);
        assertEquals("new", next.previous());
        assertEquals("newer", next.source());
        edits.acknowledge(4, true); edits.acknowledge(3, true);
        assertFalse(edits.pending());
        assertFalse(edits.saving());
        assertNull(edits.submit(5));
    }

    @Test void failuresDoNotRetryUntilThePlayerEditsAgain(){
        var edits = new ScriptSourceEdits();
        edits.edit("node", "old", "new");
        edits.submit(1); edits.acknowledge(1, false);
        assertFalse(edits.pending());
        assertEquals("Code save failed", edits.status());
        assertNull(edits.submit(2));
        edits.edit("node", "new", "fixed");
        assertEquals("old", edits.submit(3).previous());
    }

    @Test void rejectedCodeRebasesForTheNextEditAndExplicitSaveCanRetry(){
        var edits = new ScriptSourceEdits();
        edits.edit("node", "old", "edited"); edits.submit(1);
        edits.acknowledge(1, false, "stored", "Code changed elsewhere");
        assertEquals("Code save failed", edits.status("node"));
        assertEquals("Code changed elsewhere", edits.error("node"));
        assertNull(edits.submit(2));
        edits.retry();
        var retry = edits.submit(3);
        assertEquals("stored", retry.previous()); assertEquals("edited", retry.source());
        edits.acknowledge(3, true);
        assertEquals("Code saved", edits.status("node"));
        assertEquals("", edits.error("node"));
    }

    @Test void failuresAreScopedAndIncomingSnapshotsKeepUnsavedCode(){
        var edits = new ScriptSourceEdits();
        edits.edit("first", "old", "new"); edits.submit(1);
        edits.acknowledge(1, false, "stored", "Save again");
        assertEquals("", edits.status("unrelated"));
        assertEquals(java.util.Map.of("first", "new"), edits.pendingSources());
        edits.edit("first", "new", "next");
        assertEquals("stored", edits.submit(2).previous());
        edits.edit("first", "next", "newest");
        edits.acknowledge(2, false, "other source", "Code changed elsewhere");
        assertTrue(edits.pending());
        assertEquals("other source", edits.submit(3).previous());
    }

    @Test void acknowledgedEditsAcceptANewerSavedDocumentBaseline(){
        var edits = new ScriptSourceEdits();
        edits.edit("node", "old", "saved"); edits.submit(1); edits.acknowledge(1, true);
        edits.edit("node", "shared update", "local edit");
        var save = edits.submit(2);
        assertEquals("shared update", save.previous());
        assertEquals("local edit", save.source());
    }
}
