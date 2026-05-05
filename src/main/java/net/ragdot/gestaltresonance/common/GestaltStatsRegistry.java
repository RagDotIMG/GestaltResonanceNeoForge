package net.ragdot.gestaltresonance.common;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Maps Gestalt IDs to their base statistics. */
public final class GestaltStatsRegistry {

    private static final Map<ResourceLocation, GestaltStats> STATS = new HashMap<>();

    static {
        // Strength 3 | Speed 3 | Durability 3 | Range 2 | Resonance 1
        STATS.put(GestaltIds.AMEN_BREAK, new GestaltStats(3, 3, 3, 2, 1));
        // Strength 2 | Speed 3 | Durability 2 | Range 4 | Resonance 5
        STATS.put(GestaltIds.SPILLWAYS,  new GestaltStats(2, 3, 2, 4, 5));
    }

    @Nullable
    public static GestaltStats getStats(ResourceLocation gestaltId) {
        return STATS.get(gestaltId);
    }

    private GestaltStatsRegistry() {}
}
