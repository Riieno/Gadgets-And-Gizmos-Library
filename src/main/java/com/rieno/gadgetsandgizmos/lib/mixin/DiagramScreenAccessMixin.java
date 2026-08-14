package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.DiagramScreenAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

// Open stable hosted-diagram controls without runtime reflection
@Mixin(DiagramScreen.class)
public abstract class DiagramScreenAccessMixin implements DiagramScreenAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the diagram viewport orientation
    @Invoker("updateViewportOrientation")
    @Override
    public abstract void ct$updateViewportOrientation();

    // Rotate the hosted diagram
    @Invoker("rotateDiagram")
    @Override
    public abstract void ct$rotateDiagram(int yawSteps, int pitchSteps);

    // Render the hosted diagram contents
    @Invoker("renderContents")
    @Override
    public abstract void ct$renderContents(SubLevel subLevel, float partialTick);

    // Release the hosted diagram framebuffers
    @Invoker("freeFramebuffers")
    @Override
    public abstract void ct$freeFramebuffers();

    // Check if the diagram paper is visible
    @Accessor("paperVisible")
    @Override
    public abstract boolean ct$isPaperVisible();

    // Set the diagram paper visibility
    @Accessor("paperVisible")
    @Override
    public abstract void ct$setPaperVisible(boolean visible);

    // Set the previous paper offset
    @Accessor("lastPaperOffset")
    @Override
    public abstract void ct$setLastPaperOffset(float offset);

    // Set the current paper offset
    @Accessor("paperOffset")
    @Override
    public abstract void ct$setPaperOffset(float offset);

    // Set the previous tab offset
    @Accessor("lastTabOffset")
    @Override
    public abstract void ct$setLastTabOffset(float offset);

    // Set the current tab offset
    @Accessor("tabOffset")
    @Override
    public abstract void ct$setTabOffset(float offset);

    // Set the render timer
    @Accessor("renderTime")
    @Override
    public abstract void ct$setRenderTime(float renderTime);
}
