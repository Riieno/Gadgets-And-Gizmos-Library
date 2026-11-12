package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphProfile;
import com.rieno.gadgetsandgizmos.lib.graph.GraphProfileFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Present selected runtime timings without inspecting or evaluating graph nodes
public final class GraphProfilerPanel{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private GraphProfile profile = GraphProfile.EMPTY;
    private Map<String, String> names = Map.of();
    private List<String> rows = List.of();
    private Map<String, String> labels = Map.of();
    private boolean simulated;
    private int scroll, maximumScroll;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public boolean simulated(){ return simulated; }
    public GraphProfile profile(){ return profile; }

    public void clear(){ profile = GraphProfile.EMPTY; labels = Map.of(); rows = List.of(); scroll = 0; }

    public String label(String id){ return labels.getOrDefault(id, "\u2014 \u03bcs"); }

    public int color(String id){
        var val = profile.nodes().get(id);
        return val == null || val.calls() == 0 ? 0xFF9BAABA : GraphProfileFormat.color(val.averageNanos());
    }

    public void update(GraphProfile profile){ this.profile = profile; rebuild(); }

    public void nodes(Map<String, String> names){
        if(this.names.equals(names)) return;
        this.names = new LinkedHashMap<>(names);
        rebuild();
    }

    private void rebuild(){
        var labels = new LinkedHashMap<String, String>();
        profile.nodes().forEach((id, val) -> {
            if(val.calls() > 0) labels.put(id, GraphProfileFormat.micros(val.averageNanos()));
        });
        this.labels = labels;
        rows = names.keySet().stream().sorted(Comparator.comparingDouble((String id) -> {
            var val = profile.nodes().get(id);
            return val == null ? 0 : -val.averageNanos();
        }).thenComparing(names::get)).toList();
    }

    // Return true only when the selected profiling source changes
    public boolean click(double mouseX, double mouseY, int x, int y, int width){
        if(mouseX < x + 8 || mouseX >= x + width - 8 || mouseY < y + 28 || mouseY >= y + 46) return false;
        boolean next = mouseX >= x + width / 2;
        if(next == simulated) return false;
        simulated = next;
        clear();
        return true;
    }

    public void scroll(double amount){ scroll = Math.clamp(scroll - (int)Math.signum(amount) * 30, 0, maximumScroll); }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            int primary, int secondary, int accent){
        graphics.drawString(font, "Profiler", x + 8, y + 9, primary, false);
        int split = x + width / 2;
        graphics.fill(x + 8, y + 28, split - 1, y + 46, simulated ? 0x553F5264 : accent);
        graphics.fill(split + 1, y + 28, x + width - 8, y + 46, simulated ? accent : 0x553F5264);
        graphics.drawCenteredString(font, "Runtime", (x + 8 + split) / 2, y + 33, primary);
        graphics.drawCenteredString(font, "Simulated", (split + x + width - 8) / 2, y + 33, primary);
        graphics.enableScissor(x + 4, y + 50, x + width - 4, y + height - 4);
        String[] summary = {
                "Samples: " + profile.ticks() + " ticks",
                "Graph tick avg: " + GraphProfileFormat.micros(profile.averageNanos()),
                "Graph tick last: " + GraphProfileFormat.micros(profile.lastNanos()),
                "Graph tick peak: " + GraphProfileFormat.micros(profile.maxNanos()),
                "Compiled nodes: " + profile.nodes().size(),
                "Node self avg / peak per call",
                "Green <10, amber <100, red >=100 \u03bcs",
                "Wall time includes runtime bookkeeping",
                "Preview reads excluded; idle = no calls"
        };
        int content = 14 + summary.length * 16 + rows.size() * 42 + (profile.ticks() == 0 ? 18 : 0);
        maximumScroll = Math.max(0, content - (height - 50));
        scroll = Math.clamp(scroll, 0, maximumScroll);
        int top = y + 56 - scroll;
        for(String text : summary){
            graphics.drawString(font, font.plainSubstrByWidth(text, width - 16), x + 8, top, secondary, false);
            top += 16;
        }
        top += 8;
        if(profile.ticks() == 0){
            graphics.drawString(font, "Waiting for graph execution", x + 8, top, secondary, false);
            top += 18;
        }
        for(String id : rows){
            if(top + 40 >= y + 50 && top < y + height){
                var val = profile.nodes().get(id);
                boolean sampled = val != null && val.calls() > 0;
                graphics.drawString(font, font.plainSubstrByWidth(names.get(id), width - 16), x + 8, top, primary, false);
                String timing = sampled ? GraphProfileFormat.micros(val.averageNanos()) + " / "
                        + GraphProfileFormat.micros(val.maxSelfNanos()) : "Idle / unavailable";
                graphics.drawString(font, timing, x + 8, top + 12,
                        sampled ? GraphProfileFormat.color(val.averageNanos()) : secondary, false);
                if(sampled) graphics.drawString(font, "Calls: " + val.calls() + "  incl avg: "
                        + GraphProfileFormat.micros(val.averageInclusiveNanos()), x + 8, top + 24, secondary, false);
            }
            top += 42;
        }
        graphics.disableScissor();
    }

    // Draw a stopwatch with native GUI primitives at toolbar size
    public static void drawIcon(GuiGraphics graphics, int x, int y, int color){
        graphics.renderOutline(x + 3, y + 4, 10, 10, color);
        graphics.fill(x + 6, y + 1, x + 10, y + 3, color);
        graphics.fill(x + 7, y + 3, x + 9, y + 5, color);
        graphics.fill(x + 12, y + 2, x + 14, y + 4, color);
        graphics.fill(x + 7, y + 6, x + 8, y + 10, color);
        graphics.fill(x + 8, y + 9, x + 11, y + 10, color);
    }
}
