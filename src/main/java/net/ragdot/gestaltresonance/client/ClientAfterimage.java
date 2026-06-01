package net.ragdot.gestaltresonance.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side afterimage record. Visible only to the player who received the spawn packet.
 * Used by both Time Phase (3S) and Phase Court (3G) so other players can't see the ghosts.
 */
@OnlyIn(Dist.CLIENT)
public class ClientAfterimage {
    public final int id;
    public final double x, y, z;
    public final int sourceEntityId;
    public float opacity;
    public final float fadeRate;
    public final int tint;

    public ClientAfterimage(int id, double x, double y, double z, int sourceEntityId,
                            float opacity, float fadeRate, int tint) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.sourceEntityId = sourceEntityId;
        this.opacity = opacity;
        this.fadeRate = fadeRate;
        this.tint = tint;
    }
}
