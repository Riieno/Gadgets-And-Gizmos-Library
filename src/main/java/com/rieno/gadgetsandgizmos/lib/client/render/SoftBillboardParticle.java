package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Draw a soft billboard through the shared depth fading layer
public abstract class SoftBillboardParticle extends SingleQuadParticle {
    private static final int METABALL_COLUMNS = 4;
    private static final int METABALL_ROWS = 4;
    private static final int METABALL_FRAMES = METABALL_COLUMNS * METABALL_ROWS;

    // Initialize the soft billboard
    protected SoftBillboardParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    // Use the shared soft particle layer
    @Override
    public ParticleRenderType getRenderType() {
        if (usesStreakTexture()) {
            return isEmissive() ? SoftParticleRenderTypes.fastEmissiveStreakBillboard()
                    : SoftParticleRenderTypes.fastStreakBillboard();
        }
        if (usesMetaballTexture()) {
            if (usesFastRendering()) {
                return isEmissive() ? SoftParticleRenderTypes.fastEmissiveMetaballBillboard()
                        : SoftParticleRenderTypes.fastMetaballBillboard();
            }
            return isEmissive() ? SoftParticleRenderTypes.emissiveMetaballBillboard()
                    : SoftParticleRenderTypes.metaballBillboard();
        }
        if (usesFastRendering()) {
            return isEmissive() ? SoftParticleRenderTypes.fastEmissiveBillboard()
                    : SoftParticleRenderTypes.fastBillboard();
        }
        return isEmissive() ? SoftParticleRenderTypes.emissiveBillboard()
                : SoftParticleRenderTypes.softBillboard();
    }

    // Select a soft streak texture for directed particles
    protected boolean usesStreakTexture() {
        return false;
    }

    // Render without copying the scene depth buffer
    protected boolean usesFastRendering() {
        return false;
    }

    // Return the world direction for an elongated particle
    protected Vec3 getStretchedAxis() {
        return Vec3.ZERO;
    }

    // Return half the length of an elongated particle
    protected float getStretchedHalfLength() {
        return 0.0F;
    }

    // Return how far a stretched particle extends against its travel direction
    protected float getStretchedTrailingLength() {
        return getStretchedHalfLength();
    }

    // Return how far a stretched particle extends along its travel direction
    protected float getStretchedLeadingLength() {
        return getStretchedHalfLength();
    }

    // Return the width of an optional outer plume layer
    protected float getOuterLayerWidthScale() {
        return 1.0F;
    }

    // Return the opacity of an optional outer plume layer
    protected float getOuterLayerOpacity() {
        return 0.0F;
    }

    // Align elongated particles with their direction of travel
    @Override
    public void render(VertexConsumer vertices, Camera camera, float partialTick) {
        Vec3 axis = getStretchedAxis();
        float trailingLength = getStretchedTrailingLength();
        float leadingLength = getStretchedLeadingLength();
        if (axis.lengthSqr() < 1.0E-8D || trailingLength + leadingLength <= 0.0F) {
            super.render(vertices, camera, partialTick);
            return;
        }
        Vec3 center = new Vec3(Mth.lerp(partialTick, this.xo, this.x),
                Mth.lerp(partialTick, this.yo, this.y), Mth.lerp(partialTick, this.zo, this.z));
        Vec3 unit = axis.normalize();
        Vec3 side = unit.cross(camera.getPosition().subtract(center));
        if (side.lengthSqr() < 1.0E-8D) {
            Vector3f cameraRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera.rotation());
            side = new Vec3(cameraRight.x(), cameraRight.y(), cameraRight.z())
                    .subtract(unit.scale(unit.dot(new Vec3(cameraRight.x(), cameraRight.y(), cameraRight.z()))));
        }
        if (side.lengthSqr() < 1.0E-8D) side = unit.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-8D) side = unit.cross(new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 unitSide = side.normalize();
        Vec3 trailing = unit.scale(trailingLength);
        Vec3 leading = unit.scale(leadingLength);
        Vec3 relative = center.subtract(camera.getPosition());
        int light = this.getLightColor(partialTick);
        float width = this.getQuadSize(partialTick);
        float outerOpacity = getOuterLayerOpacity();
        if (outerOpacity > 0.0F && getOuterLayerWidthScale() > 1.0F) {
            renderStretchedQuad(vertices, relative, trailing, leading,
                    unitSide.scale(width * getOuterLayerWidthScale()), light, this.alpha * outerOpacity);
        }
        renderStretchedQuad(vertices, relative, trailing, leading,
                unitSide.scale(width), light, this.alpha);
    }

    // Write the four corners of a stretched plume layer
    private void renderStretchedQuad(VertexConsumer vertices, Vec3 center, Vec3 trailing, Vec3 leading,
            Vec3 width, int light, float opacity) {
        vertex(vertices, center.subtract(trailing).add(width), getU1(), getV1(), light, opacity);
        vertex(vertices, center.add(leading).add(width), getU1(), getV0(), light, opacity);
        vertex(vertices, center.add(leading).subtract(width), getU0(), getV0(), light, opacity);
        vertex(vertices, center.subtract(trailing).subtract(width), getU0(), getV1(), light, opacity);
    }

    // Include the full elongated quad in frustum checks
    @Override
    public AABB getRenderBoundingBox(float partialTick) {
        float radius = Math.max(this.getQuadSize(partialTick) * getOuterLayerWidthScale(),
                Math.max(getStretchedTrailingLength(), getStretchedLeadingLength()));
        if (radius <= 0.0F) return super.getRenderBoundingBox(partialTick);
        return new AABB(this.x - radius, this.y - radius, this.z - radius,
                this.x + radius, this.y + radius, this.z + radius);
    }

    // Write one colored particle vertex
    private void vertex(VertexConsumer vertices, Vec3 pos, float u, float v, int light, float opacity) {
        vertices.addVertex((float) pos.x, (float) pos.y, (float) pos.z)
                .setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, opacity).setLight(light);
    }

    // Select the animated metaball texture for this billboard
    protected boolean usesMetaballTexture() {
        return false;
    }

    // Advance the shared metaball texture each tick
    protected int getMetaballFrame() {
        return this.age;
    }

    // Mark billboards that should render as colored light
    protected boolean isEmissive() {
        return false;
    }

    // Keep emissive billboards bright in the particle shader
    @Override
    public int getLightColor(float partialTick) {
        return isEmissive() ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    // Get the left edge of the procedural quad
    @Override
    protected float getU0() {
        return usesMetaballTexture() ? Math.floorMod(getMetaballFrame(), METABALL_FRAMES)
                % METABALL_COLUMNS / (float) METABALL_COLUMNS : 0.0F;
    }

    // Get the right edge of the procedural quad
    @Override
    protected float getU1() {
        return usesMetaballTexture() ? (Math.floorMod(getMetaballFrame(), METABALL_FRAMES)
                % METABALL_COLUMNS + 1) / (float) METABALL_COLUMNS : 1.0F;
    }

    // Get the top edge of the procedural quad
    @Override
    protected float getV0() {
        return usesMetaballTexture() ? Math.floorMod(getMetaballFrame(), METABALL_FRAMES)
                / METABALL_COLUMNS / (float) METABALL_ROWS : 0.0F;
    }

    // Get the bottom edge of the procedural quad
    @Override
    protected float getV1() {
        return usesMetaballTexture() ? (Math.floorMod(getMetaballFrame(), METABALL_FRAMES)
                / METABALL_COLUMNS + 1) / (float) METABALL_ROWS : 1.0F;
    }
}
