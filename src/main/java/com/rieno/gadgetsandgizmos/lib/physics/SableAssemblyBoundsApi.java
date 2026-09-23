package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// Resolve a conservative size envelope around one connected Sable assembly
public final class SableAssemblyBoundsApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable assembly bounds API
    private SableAssemblyBoundsApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the assembly envelope around a world-space reference point
    public static Envelope envelope(
            @Nullable Collection<? extends SubLevel> subLevels,
            @Nullable Vec3 referencePosition
    ) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        if (subLevels != null) {
            for (SubLevel subLevel : subLevels) {
                if (subLevel == null || subLevel.isRemoved()) {
                    continue;
                }
                var bounds = subLevel.boundingBox();
                if (bounds == null || !finiteBounds(
                        bounds.minX(), bounds.minY(), bounds.minZ(),
                        bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                    continue;
                }
                minX = Math.min(minX, bounds.minX());
                minY = Math.min(minY, bounds.minY());
                minZ = Math.min(minZ, bounds.minZ());
                maxX = Math.max(maxX, bounds.maxX());
                maxY = Math.max(maxY, bounds.maxY());
                maxZ = Math.max(maxZ, bounds.maxZ());
            }
        }
        if (!Double.isFinite(minX) || maxX < minX || maxY < minY || maxZ < minZ) {
            return Envelope.DEFAULT;
        }
        Vec3 reference = referencePosition == null
                || !Double.isFinite(referencePosition.x)
                || !Double.isFinite(referencePosition.y)
                || !Double.isFinite(referencePosition.z)
                ? new Vec3((minX + maxX) * 0.5D, (minY + maxY) * 0.5D,
                (minZ + maxZ) * 0.5D)
                : referencePosition;
        double radiusX = Math.max(Math.abs(minX - reference.x), Math.abs(maxX - reference.x));
        double radiusZ = Math.max(Math.abs(minZ - reference.z), Math.abs(maxZ - reference.z));
        double spatialRadiusSqr = 0.0D;
        if (subLevels != null) {
            for (SubLevel subLevel : subLevels) {
                if (subLevel == null || subLevel.isRemoved()) {
                    continue;
                }
                var bounds = subLevel.boundingBox();
                if (bounds == null || !finiteBounds(
                        bounds.minX(), bounds.minY(), bounds.minZ(),
                        bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                    continue;
                }
                for (double x : new double[]{bounds.minX(), bounds.maxX()}) {
                    for (double y : new double[]{bounds.minY(), bounds.maxY()}) {
                        for (double z : new double[]{bounds.minZ(), bounds.maxZ()}) {
                            double dx = x - reference.x;
                            double dy = y - reference.y;
                            double dz = z - reference.z;
                            spatialRadiusSqr = Math.max(spatialRadiusSqr,
                                    dx * dx + dy * dy + dz * dz);
                        }
                    }
                }
            }
        }
        return new Envelope(
                Math.sqrt(radiusX * radiusX + radiusZ * radiusZ),
                maxY - minY,
                reference.y - minY,
                Math.sqrt(spatialRadiusSqr));
    }

    // Check if the bounds are finite and ordered
    private static boolean finiteBounds(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
                && maxX >= minX && maxY >= minY && maxZ >= minZ;
    }

    // Store the conservative assembly size around its control reference point
    public record Envelope(double horizontalRadius, double height, double bottomOffset,
                           double spatialRadius) {
        // Default envelope
        public static final Envelope DEFAULT = new Envelope(1.0D, 1.0D, 0.5D);

        // Initialize a standard non-rotating envelope
        public Envelope(double horizontalRadius, double height, double bottomOffset) {
            this(horizontalRadius, height, bottomOffset,
                    Math.max(horizontalRadius,
                            Math.max(bottomOffset, height - bottomOffset)));
        }

        // Initialize the envelope
        public Envelope {
            horizontalRadius = finitePositive(horizontalRadius, 1.0D);
            height = finitePositive(height, 1.0D);
            bottomOffset = Double.isFinite(bottomOffset)
                    ? Math.max(0.0D, bottomOffset) : height * 0.5D;
            spatialRadius = Math.max(
                    finitePositive(spatialRadius, horizontalRadius),
                    Math.max(horizontalRadius,
                            Math.max(bottomOffset, height - bottomOffset)));
        }

        // Get a center-to-target arrival tolerance which covers the complete hull plus padding.
        public double targetOverlapTolerance(double padding) {
            double safePadding = Double.isFinite(padding) ? Math.max(0.0D, padding) : 0.0D;
            return spatialRadius + safePadding;
        }

        // Normalize one positive finite value
        private static double finitePositive(double val, double fallback) {
            return Double.isFinite(val) ? Math.max(0.01D, val) : fallback;
        }
    }
}
