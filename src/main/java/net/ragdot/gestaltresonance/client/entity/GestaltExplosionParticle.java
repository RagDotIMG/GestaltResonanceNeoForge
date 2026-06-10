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
public class GestaltExplosionParticle extends TextureSheetParticle {

    private static final int FRAME_TICKS = 4;
    private static final float[] FRAME_ALPHA = {1.0f, 0.8f, 0.5f, 0.2f};

    protected GestaltExplosionParticle(ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.lifetime = 20;
        this.alpha = FRAME_ALPHA[0];
        this.hasPhysics = true;
        this.gravity = 0.04f;
        this.friction = 0.82f;
        this.xd = dx + (random.nextDouble() - 0.5) * 0.8;
        this.yd = dy + (random.nextDouble() - 0.5) * 0.8;
        this.zd = dz + (random.nextDouble() - 0.5) * 0.8;
        this.quadSize = 0.2f + random.nextFloat() * 0.15f;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        int frame = Math.min((this.age / FRAME_TICKS), FRAME_ALPHA.length - 1);
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
            return new GestaltExplosionParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
