package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;

// Supply a read-only lens at a live entity's interpolated eye position
public final class EntityViewSource implements ViewSource{
    private final Entity entity;

    public EntityViewSource(Entity entity){ this.entity = java.util.Objects.requireNonNull(entity); }

    @Override
    public ViewPose viewPose(float partialTick){
        var owner = SableLevelApi.containing(entity);
        var orientation = new Quaterniond().rotationYXZ(Math.toRadians(180 - entity.getViewYRot(partialTick)),
                Math.toRadians(-entity.getViewXRot(partialTick)), 0);
        if(owner != null) orientation = new Quaterniond(owner.logicalPose().orientation()).mul(orientation);
        return new ViewPose(SableTransformApi.toWorldPosition(owner, entity.getEyePosition(partialTick)), orientation, 70);
    }

    @Override public Entity viewEntity(){ return entity; }
    @Override public void controlView(double panDelta, double tiltDelta, double zoomSteps){}
    @Override public boolean isViewAvailable(){ return entity.isAlive() && !entity.isRemoved(); }
}
