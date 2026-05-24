package net.ragdot.gestaltresonance.client.entity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class GestaltIllusionParticle extends TextureSheetParticle {

    // Must match the frametime in gestalt_illusion_particle.png.mcmeta
    private static final int FRAME_TICKS = 4;

    // Alpha per animation frame — tweak these to taste.
    // Frame index cycles with the animation: 0 = first frame, 3 = last frame.
    private static final float[] FRAME_ALPHA = {1.0f, 0.95f, 0.80f, 0.75f};

    protected GestaltIllusionParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.lifetime = 30;
        this.alpha = FRAME_ALPHA[0];
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 0.97f;
        this.xd = (random.nextDouble() - 0.5) * 0.02;
        this.yd = 0.008 + random.nextDouble() * 0.008;
        this.zd = (random.nextDouble() - 0.5) * 0.02;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        int frame = (this.age / FRAME_TICKS) % FRAME_ALPHA.length;
        this.alpha = FRAME_ALPHA[frame];
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new GestaltIllusionParticle(level, x, y, z, sprites);
        }
    }
}
