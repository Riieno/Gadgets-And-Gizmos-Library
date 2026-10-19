package com.rieno.gadgetsandgizmos.lib.client.schedule;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

// Read defaults, selectors and validation from each registered input's native widgets
public final class CreateScheduleInputFields{
    // Retain the native widgets and their serialized field names
    private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
    private final Map<String, List<Component>> selectors = new LinkedHashMap<>();
    private final ModularGuiLine line = new ModularGuiLine(){
        // Retain editable fields as Create adds them to the configuration line
        @Override public void add(Pair<AbstractWidget, String> field){
            super.add(field);
            if(!"Dummy".equals(field.getSecond())) widgets.put(field.getSecond(), field.getFirst());
        }
    };

    // Build a detached native configuration line without attaching it to a screen
    public CreateScheduleInputFields(IScheduleInput input, Font font){
        input.initConfigurationWidgets(new ModularGuiLineBuilder(font, line, 0, 4){
            // Capture option labels through the same public builder contract used by Create
            @Override public ModularGuiLineBuilder addSelectionScrollInput(int x, int width,
                    BiConsumer<SelectionScrollInput, Label> transform, String key){
                SelectionScrollInput selector = new SelectionScrollInput(x, 0, width, 18){
                    // Retain all options before passing them to the native selector
                    @Override public ScrollInput forOptions(List<? extends Component> opts){
                        selectors.put(key, List.copyOf(opts));
                        return super.forOptions(opts);
                    }
                };
                Label label = new Label(x + 5, 4, Component.empty());
                label.withShadow();
                transform.accept(selector, label);
                selector.writingTo(label);
                line.add(Pair.of(label, "Dummy"));
                line.add(Pair.of(selector, key));
                return this;
            }
        });
        line.loadValues(input.getData(), widget -> {}, widget -> {});
    }

    // Include defaults that the input deliberately omits from its saved data
    public CompoundTag data(IScheduleInput input){
        CompoundTag data = input.getData().copy();
        line.saveValues(data);
        return data;
    }

    // Get every native selector option in its serialized integer order
    public List<Component> choices(String key){
        return selectors.getOrDefault(key, List.of());
    }

    // Validate an inline edit with the input's native filter and numeric bounds
    public CompoundTag edit(IScheduleInput input, String key, String val){
        AbstractWidget widget = widgets.get(key);
        if(widget instanceof EditBox box){
            box.setValue(val);
            if(!box.getValue().equals(val)) return null;
        }else if(widget instanceof ScrollInput scroll){
            try{
                int requested = Integer.parseInt(val);
                scroll.setState(requested);
                if(scroll.getState() != requested) return null;
            }catch(NumberFormatException err){
                return null;
            }
        }else return null;
        return data(input);
    }
}
