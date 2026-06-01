package net.ragdot.gestaltresonance.common.passive;

import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.common.GestaltIds;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps Gestalt IDs to their passive ability implementations.
 */
public final class GestaltPassiveRegistry {

    private static final Map<ResourceLocation, GestaltPassive> PASSIVES = new HashMap<>();

    static {
        PASSIVES.put(GestaltIds.AMEN_BREAK,  new AmenBreakPassive());
        PASSIVES.put(GestaltIds.SPILLWAYS,   new SpillwaysPassive());
        PASSIVES.put(GestaltIds.FLOAT_PLAY,  new FloatPlayPassive());
    }

    @Nullable
    public static GestaltPassive getPassive(ResourceLocation gestaltId) {
        return PASSIVES.get(gestaltId);
    }

    private GestaltPassiveRegistry() {}
}
