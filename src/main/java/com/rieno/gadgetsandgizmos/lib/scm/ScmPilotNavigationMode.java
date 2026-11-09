package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

// Navigate native ground, surface and flying pilots from their live capability profile
public final class ScmPilotNavigationMode implements ScmControlMode{
    // Retain owning-mod identity and hardware callbacks without retaining a host or world
    private final ResourceLocation id;
    private final String name;
    private final Predicate<BlockEntity> available;
    private final Function<BlockEntity, ScmPilotControl.Profile> profiles;
    private final ScmPilotControl.Profile profile;

    // Register one optional native mode independently of built-in vehicle toggles
    public ScmPilotNavigationMode(ResourceLocation id, String name, Predicate<BlockEntity> available,
                                 Function<BlockEntity, ScmPilotControl.Profile> profiles){
        this(id, name, available, profiles, ScmPilotControl.Profile.GROUND);
    }

    // Bind a detached capability snapshot for one control sample
    private ScmPilotNavigationMode(ResourceLocation id, String name, Predicate<BlockEntity> available,
                                  Function<BlockEntity, ScmPilotControl.Profile> profiles, ScmPilotControl.Profile profile){
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.available = Objects.requireNonNull(available);
        this.profiles = profiles;
        this.profile = Objects.requireNonNull(profile);
    }

    // Preserve the consuming mod's saved vehicle id
    @Override public ResourceLocation id(){ return id; }
    // Present the owning mod's vehicle label
    @Override public String displayName(){ return name; }
    // Require the native controls on this craft
    @Override public boolean isAvailable(BlockEntity host){ return available.test(host); }
    // Refresh the running gear while retaining the same saved vehicle choice
    @Override public ScmControlMode bind(BlockEntity host){
        return profiles == null ? this : new ScmPilotNavigationMode(id, name, available, null, profiles.apply(host));
    }
    // Keep ordinary walking on the ground while allowing wall and air navigation in three dimensions
    @Override public boolean usesGroundNavigation(){ return !profile.canClimb() && !profile.canFly() && !profile.flying(); }
    // Keep climbing capability distinct from flight when placing automatic route handoffs
    @Override public boolean usesSurfaceNavigation(){ return !profile.canFly() && !profile.flying(); }
    // Leave terrain contact and climbing transitions under the native controller
    @Override public boolean avoidsWorldCollisions(){ return false; }

    // Follow a route in the current support plane or use free movement for a flying native pilot
    @Override public ControlOutput navigate(ControlInput input){
        if(profile.canFly() || profile.flying()){
            return ScmControlModeRegistry.resolve("airship").navigate(input);
        }
        return surfaceControl(input, null);
    }

    // Align the native pilot independently from connector translation
    @Override public ControlOutput navigate(ControlInput input, Vec3 headingDirection){
        if(!usesSurfaceNavigation()) return ScmControlModeRegistry.resolve("airship").navigate(input, headingDirection);
        return surfaceControl(input, headingDirection);
    }

    // Turn on the spot before translating and retain lateral or reverse movement during docking
    private ControlOutput surfaceControl(ControlInput input, Vec3 headingDirection){
        Vec3 up = profile.canClimb() ? input.up() : new Vec3(0, 1, 0);
        Vec3 forward = tangent(input.forward(), up);
        Vec3 path = tangent(input.pathDirection(), up);
        Vec3 error = input.target().subtract(input.position());
        if(path.lengthSqr() < 1.0E-12D && profile.canClimb() && error.length() > input.tolerance()) path = forward;
        if(forward.lengthSqr() < 1.0E-12D || headingDirection == null && path.lengthSqr() < 1.0E-12D){
            return new ControlOutput(Vec3.ZERO, Vec3.ZERO, false, 0, 0);
        }
        double distance = headingDirection != null || !profile.canClimb()
                ? error.subtract(up.scale(error.dot(up))).length() : error.length();
        double speed = Math.min(input.targetSpeed(), input.travelSpeedLimit());
        if(!input.transitWaypoint()) speed = Math.min(speed, Math.max(0, distance - input.tolerance()) * 2);
        double direction = input.reverseRecovery() ? -1 : 1;
        Vec3 heading = headingDirection == null ? path.scale(direction) : tangent(headingDirection, up);
        if(heading.lengthSqr() < 1.0E-12D) heading = forward;
        double angle = Math.atan2(forward.cross(heading).dot(up), Math.clamp(forward.dot(heading), -1, 1));
        double steer = ScmControlAxes.groundSteeringDemand(angle, input.angularVelocity().dot(up), input.steeringFeedForward());
        boolean aligned = Math.abs(angle) <= Math.toRadians(12) && Math.abs(input.angularVelocity().dot(up)) <= 0.25D;
        if(!aligned) speed = 0;
        Vec3 movement = headingDirection == null ? forward.scale(direction) : path;
        if(speed <= 1.0E-5D) movement = Vec3.ZERO;
        if(headingDirection == null && distance <= input.tolerance()) steer = 0;
        return new ControlOutput(movement, up.scale(steer), false, 0, movement.lengthSqr() > 0 ? direction : 0,
                input.targetSpeed() > 0 ? speed / input.targetSpeed() : 0);
    }

    // Project guidance onto the support surface, including walls and inverted ceilings
    private static Vec3 tangent(Vec3 dir, Vec3 up){ return dir.subtract(up.scale(dir.dot(up))).normalize(); }
}
