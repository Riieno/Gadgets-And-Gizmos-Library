package com.rieno.gadgetsandgizmos.lib.geometry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;

// Share one straight scissor linkage layout between model placement and block hitboxes
public record ScissorLinkageLayout(int sectionCount, double extension){
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the measured span, including temporary overextension during pose interpolation
    public ScissorLinkageLayout{
        if(sectionCount < 1 || !Double.isFinite(extension) || extension < 0){
            throw new IllegalArgumentException("A positive section count and finite nonnegative span are required");
        }
    }

    // Divide the complete span evenly between the crossed arm pairs
    public double sectionLength(){
        return extension / sectionCount;
    }

    // Keep the rods at unit length while folding across the linkage axis
    public double sideSpan(){
        double length = sectionLength();
        return Math.sqrt(Math.max(0, 1 - length * length));
    }

    // Locate a section measured back from the moving end
    public double distanceFromHead(int idx){
        if(idx < 0 || idx >= sectionCount) throw new IndexOutOfBoundsException(idx);
        return extension - (idx + .5) * sectionLength();
    }

    // Select the unit block cell containing a section's centre
    public int cellForSection(int idx){
        return Math.max(1, (int)Math.round(distanceFromHead(idx)));
    }

    // Fit the model to the fold and keep it connected during temporary overextension
    public double modelScale(Profile profile){
        double length = sectionLength();
        return (profile.minScale() + (profile.maxScale() - profile.minScale()) * Math.min(1, length))
                * Math.max(1, length);
    }

    // Keep a cell's interaction shape on the closest crossed arm pair
    public VoxelShape cellShape(Direction facing, int cell, Profile profile){
        if(extension <= .001 || cell < 1) return Shapes.empty();
        int nearest = -1;
        double best = Double.POSITIVE_INFINITY;
        for(int idx = 0; idx < sectionCount; idx++){
            if(cellForSection(idx) != cell) continue;
            double score = Math.abs(cell - distanceFromHead(idx));
            if(score >= best) continue;
            nearest = idx;
            best = score;
        }
        if(nearest < 0) return Shapes.empty();
        double exposedStart = cell + 1 - extension;
        if(exposedStart >= 1 - 1E-5) return Shapes.empty();
        double center = cell - distanceFromHead(nearest) + profile.headOffset();
        VoxelShape res = Shapes.or(armShape(center, false, exposedStart, profile),
                armShape(center, true, exposedStart, profile));
        Quaterniond rotation = new Quaterniond().rotationTo(new Vector3d(0, 1, 0),
                new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()));
        return Shapes.join(VoxelShapeTransforms.rotate(res, rotation, new Vec3(.5, .5, .5)),
                Shapes.block(), BooleanOp.AND).optimize();
    }

    // Bound one rod in the local upward-facing cell before rotating it
    private VoxelShape armShape(double center, boolean reversed, double exposedStart, Profile profile){
        Vector3d dir = new Vector3d(reversed ? -sideSpan() : sideSpan(), sectionLength(), 0).normalize();
        double halfLength = modelScale(profile) * .5;
        double x = reversed ? -profile.lateralOffset() : 0;
        double radius = profile.radius();
        double minX = .5 + x - Math.abs(dir.x) * halfLength - radius;
        double maxX = .5 + x + Math.abs(dir.x) * halfLength + radius;
        double minY = Math.max(.5 + center - dir.y * halfLength - radius,
                Math.max(0, Math.min(1, exposedStart)));
        double maxY = .5 + center + dir.y * halfLength + radius;
        if(maxY - minY <= 1E-5) return Shapes.empty();
        return Shapes.create(new AABB(minX, minY, .5 - radius, maxX, maxY, .5 + radius));
    }

    // Describe a consumer's model offsets, rod thickness and fold scales
    public record Profile(double headOffset, double lateralOffset, double radius, double minScale, double maxScale){
        // Reject invalid model dimensions before building render or collision geometry
        public Profile{
            if(!Double.isFinite(headOffset) || !Double.isFinite(lateralOffset) || !Double.isFinite(radius)
                    || !Double.isFinite(minScale) || !Double.isFinite(maxScale)
                    || radius <= 0 || minScale <= 0 || maxScale < minScale){
                throw new IllegalArgumentException("Finite offsets and positive rod dimensions are required");
            }
        }
    }
}
