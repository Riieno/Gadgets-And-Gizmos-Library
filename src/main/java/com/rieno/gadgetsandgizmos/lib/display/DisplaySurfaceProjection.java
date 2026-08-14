package com.rieno.gadgetsandgizmos.lib.display;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

// Resolve a clicked display block into one position across the complete screen network
public final class DisplaySurfaceProjection {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the display surface projection
    private DisplaySurfaceProjection() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the normalized point
    public static Point normalizedPoint(
            BlockPos rootPosition,
            BlockPos clickedPosition,
            Direction screenRight,
            Vec3 localHitPosition,
            int screenWidth,
            int screenHeight
    ) {
        return normalizedPoint(rootPosition, clickedPosition, screenRight,
                localHitPosition, screenWidth, screenHeight, 1, 0);
    }

    // Get the normalized point
    public static Point normalizedPoint(
            BlockPos rootPosition,
            BlockPos clickedPosition,
            Direction screenRight,
            Vec3 localHitPosition,
            int screenWidth,
            int screenHeight,
            int pixelsPerBlock,
            int borderPixels
    ) {
        if (rootPosition == null || clickedPosition == null
                || screenRight == null || localHitPosition == null) {
            return new Point(0.0D, 0.0D);
        }
        BlockPos offset = clickedPosition.subtract(rootPosition);
        int cellX = offset.getX() * screenRight.getStepX()
                + offset.getZ() * screenRight.getStepZ();
        int cellY = rootPosition.getY() - clickedPosition.getY();
        double localX = localHitPosition.x - clickedPosition.getX();
        double localY = localHitPosition.y - clickedPosition.getY();
        double localZ = localHitPosition.z - clickedPosition.getZ();
        double horizontal = screenRight.getAxis() == Direction.Axis.X
                ? (screenRight.getStepX() > 0 ? localX : 1.0D - localX)
                : (screenRight.getStepZ() > 0 ? localZ : 1.0D - localZ);
        int pixelScale = Math.max(1, pixelsPerBlock);
        int inset = Math.max(0, borderPixels);
        double pixelX = (cellX + horizontal) * pixelScale - inset;
        double pixelY = (cellY + 1.0D - localY) * pixelScale - inset;
        double contentWidth = Math.max(1, screenWidth * pixelScale - inset * 2);
        double contentHeight = Math.max(1, screenHeight * pixelScale - inset * 2);
        return new Point(clamp(pixelX / contentWidth), clamp(pixelY / contentHeight));
    }

    // Clamp the display surface projection
    private static double clamp(double val) {
        return Math.max(0.0D, Math.min(1.0D, val));
    }

    // Store the point
    public record Point(double x, double y) {
    }
}
