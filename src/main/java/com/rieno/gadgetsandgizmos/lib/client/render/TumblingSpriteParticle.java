package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

// Let lightweight sprite pieces tumble, drift and settle against world geometry
public final class TumblingSpriteParticle extends TextureSheetParticle{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ClientParticleBudget budget;
    private final float spin;
    private final float phase;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain one randomly selected sprite for the particle's lifetime
    private TumblingSpriteParticle(ClientLevel level, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet sprites, float size, int lifetime,
            ClientParticleBudget budget){
        super(level, x, y, z);
        this.budget = budget;
        xd = vx;
        yd = vy;
        zd = vz;
        quadSize = size * (0.7F + random.nextFloat() * 0.6F);
        this.lifetime = lifetime + random.nextInt(Math.max(1, lifetime / 2));
        gravity = 0.3F;
        friction = 0.96F;
        hasPhysics = false;
        phase = random.nextFloat() * Mth.TWO_PI;
        spin = (0.12F + random.nextFloat() * 0.2F) * (random.nextBoolean() ? 1.0F : -1.0F);
        roll = oRoll = phase;
        pickSprite(sprites);
        setSize(0.04F, 0.04F);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Slow the launch into a flutter and fade the settled pieces
    @Override
    public void tick(){
        oRoll = roll;
        super.tick();
        if(removed) return;
        if(!onGround){
            roll += spin;
            xd += Mth.sin(age * 0.3F + phase) * 0.003D;
            zd += Mth.cos(age * 0.23F + phase) * 0.003D;
        }
        alpha = Mth.clamp((lifetime - age) / 12.0F, 0.0F, 1.0F);
    }

    // Stop at solid root-world and SubLevel surfaces
    @Override
    public void move(double x, double y, double z){
        Vec3 motion = WorldParticleCollision.clipMotion(level, new Vec3(this.x, this.y, this.z),
                new Vec3(x, y, z));
        super.move(motion.x, motion.y, motion.z);
        onGround = y < 0.0D && Math.abs(motion.y - y) > 1.0E-6D;
        if(Math.abs(motion.x - x) > 1.0E-6D) xd = 0.0D;
        if(Math.abs(motion.y - y) > 1.0E-6D) yd = 0.0D;
        if(Math.abs(motion.z - z) > 1.0E-6D) zd = 0.0D;
    }

    // Tilt each piece as it spins instead of keeping a flat billboard
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks){
        float time = age + partialTicks;
        Quaternionf rotation = new Quaternionf(camera.rotation())
                .rotateZ(Mth.lerp(partialTicks, oRoll, roll))
                .rotateY(Mth.sin(time * spin + phase) * 1.2F);
        renderRotatedQuad(buffer, camera, rotation, partialTicks);
    }

    // Use normal world lighting and the particle atlas
    @Override
    public ParticleRenderType getRenderType(){
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // Return the reserved slot exactly once
    @Override
    public void remove(){
        if(removed) return;
        super.remove();
        budget.release(level);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bind an owning mod's simple particle type and sprite set to tumbling motion
    public static final class Provider implements ParticleProvider<SimpleParticleType>{
        private final SpriteSet sprites;
        private final float size;
        private final int lifetime;
        private final ClientParticleBudget budget = new ClientParticleBudget(2048, 128);

        // Supply the nominal half-size and minimum lifetime in client ticks
        public Provider(SpriteSet sprites, float size, int lifetime){
            if(sprites == null || !Float.isFinite(size) || size <= 0.0F || lifetime < 1){
                throw new IllegalArgumentException("Sprites, a positive size and lifetime are required");
            }
            this.sprites = sprites;
            this.size = size;
            this.lifetime = lifetime;
        }

        // Respect the shared live and per-tick limits for this provider
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double vx, double vy, double vz){
            if(!budget.tryAcquire(level)) return null;
            return new TumblingSpriteParticle(level, x, y, z, vx, vy, vz, sprites, size, lifetime, budget);
        }
    }
}
