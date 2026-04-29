package net.ragdot.gestaltresonance.common;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Maps Gestalt IDs to their base statistics. */
public final class GestaltStatsRegistry {

    private static final Map<ResourceLocation, GestaltStats> STATS = new HashMap<>();

    static {
        // Strength 4 | Speed 3 | Durability 3 | Range 2 | Resonance 2
        STATS.put(GestaltIds.AMEN_BREAK, new GestaltStats(4, 3, 3, 2, 2));
    }

    @Nullable
    public static GestaltStats getStats(ResourceLocation gestaltId) {
        return STATS.get(gestaltId);
    }

    private GestaltStatsRegistry() {}
}
