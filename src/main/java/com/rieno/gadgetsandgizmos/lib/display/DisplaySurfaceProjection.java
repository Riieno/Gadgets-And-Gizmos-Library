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

    // Get a normalized point for a vertically cropped display surface
    public static VisiblePoint normalizedVisiblePoint(
            BlockPos rootPosition, BlockPos clickedPosition,
            Direction screenRight, Vec3 localHitPosition,
            int screenWidth, int screenHeight, int pixelsPerBlock,
            int borderPixels, int visiblePixelsPerRow, int sourceTopPixels) {
        if (rootPosition == null || clickedPosition == null
                || screenRight == null || localHitPosition == null) {
            return new VisiblePoint(0.0D, 0.0D, false);
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
        int rowPixels = Math.max(1, visiblePixelsPerRow);
        int inset = Math.max(0, borderPixels);
        double pixelX = (cellX + horizontal) * pixelScale - inset;
        double pixelY = cellY * rowPixels + (1.0D - localY) * pixelScale
                - inset - Math.max(0, sourceTopPixels);
        double contentWidth = Math.max(1,
                screenWidth * pixelScale - inset * 2);
        double contentHeight = Math.max(1,
                screenHeight * rowPixels - inset * 2);
        double normalizedX = pixelX / contentWidth;
        double normalizedY = pixelY / contentHeight;
        boolean inside = normalizedX >= 0.0D && normalizedX <= 1.0D
                && normalizedY >= 0.0D && normalizedY <= 1.0D;
        return new VisiblePoint(
                clamp(normalizedX), clamp(normalizedY), inside);
    }

    // Clamp the display surface projection
    private static double clamp(double val) {
        return Math.max(0.0D, Math.min(1.0D, val));
    }

    // Store the point
    public record Point(double x, double y) {
    }

    // Store a normalized point and whether it struck the visible surface
    public record VisiblePoint(double x, double y, boolean inside) {
    }
}
