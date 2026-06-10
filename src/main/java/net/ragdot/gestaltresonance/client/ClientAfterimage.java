package net.ragdot.gestaltresonance.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Client-side afterimage record. Visible only to the player who received the spawn packet.
 * Used by both Time Phase (3S) and Phase Court (3G) so other players can't see the ghosts.
 *
 * If the source entity may be discarded before the window ends (e.g. the body double death
 * afterimage), supply {@code cachedTexture} and {@code cachedRenderer} at creation time.
 * The manager uses those values instead of re-looking up the entity every frame.
 */
@OnlyIn(Dist.CLIENT)
public class ClientAfterimage {
    public final int id;
    public final double x, y, z;
    public final int sourceEntityId;
    public float opacity;
    public final float fadeRate;
    public final int tint;
    @Nullable public final ResourceLocation cachedTexture;
    @Nullable public final LivingEntityRenderer<?, ?> cachedRenderer;

    public ClientAfterimage(int id, double x, double y, double z, int sourceEntityId,
                            float opacity, float fadeRate, int tint) {
        this(id, x, y, z, sourceEntityId, opacity, fadeRate, tint, null, null);
    }

    public ClientAfterimage(int id, double x, double y, double z, int sourceEntityId,
                            float opacity, float fadeRate, int tint,
                            @Nullable ResourceLocation cachedTexture,
                            @Nullable LivingEntityRenderer<?, ?> cachedRenderer) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.sourceEntityId = sourceEntityId;
        this.opacity = opacity;
        this.fadeRate = fadeRate;
        this.tint = tint;
        this.cachedTexture = cachedTexture;
        this.cachedRenderer = cachedRenderer;
    }
}
