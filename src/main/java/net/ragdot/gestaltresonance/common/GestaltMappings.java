package net.ragdot.gestaltresonance.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;

/**
 * Maps host mob EntityType to the Gestalt ID it can yield.
 * Only mapped mobs produce a gestalt on death; all others return null.
 */
public final class GestaltMappings {

    /**
     * Returns the gestalt ResourceLocation for the given host entity type,
     * or null if that mob cannot yield a gestalt.
     */
    @Nullable
    public static ResourceLocation gestaltFromHost(EntityType<?> type) {
        if (type == EntityType.CREEPER)    return GestaltIds.AMEN_BREAK;
        if (type == EntityType.GLOW_SQUID) return GestaltIds.SPILLWAYS;
        return null;
    }

    private GestaltMappings() {}
}
