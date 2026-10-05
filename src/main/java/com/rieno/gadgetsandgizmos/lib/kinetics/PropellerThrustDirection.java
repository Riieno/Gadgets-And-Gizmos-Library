package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Select which direction a clockwise propeller moves air
public enum PropellerThrustDirection implements INamedIconOptions {
    PULL_WHEN_CLOCKWISE("pull_when_clockwise", "Toward", 1),
    PUSH_WHEN_CLOCKWISE("push_when_clockwise", "Away", -1);

    // Translated option label
    private final String translationKey;
    // Direction value accepted by external controls
    private final String controlValue;
    // Propulsion sign relative to shaft rotation
    private final int sign;

    // Initialize one thrust direction
    PropellerThrustDirection(String name, String controlValue, int sign){
        translationKey = "gadgetsngizmos.propeller." + name;
        this.controlValue = controlValue;
        this.sign = sign;
    }

    // Get the option icon
    @Override
    public AllIcons getIcon(){
        return this == PULL_WHEN_CLOCKWISE ? AllIcons.I_REFRESH : AllIcons.I_ROTATE_CCW;
    }

    // Get the translated option label
    @Override
    public String getTranslationKey(){
        return translationKey;
    }

    // Get the direction value for external controls
    public String controlValue(){
        return controlValue;
    }

    // List direction values in the same order as the block selector
    public static List<String> controlOptions(){
        return List.of(PULL_WHEN_CLOCKWISE.controlValue, PUSH_WHEN_CLOCKWISE.controlValue);
    }

    // Parse a direction without changing the current selection on invalid input
    public static @Nullable PropellerThrustDirection fromControlValue(@Nullable String val){
        if(val == null) return null;
        for(PropellerThrustDirection direction : values()){
            if(direction.controlValue.equalsIgnoreCase(val.trim())) return direction;
        }
        return null;
    }

    // Resolve propulsion speed without changing shaft rotation
    public double signedSpeed(Direction facing, double speed){
        return facing.getAxisDirection().getStep() * speed * sign;
    }
}
