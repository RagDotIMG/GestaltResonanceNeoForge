package net.ragdot.gestaltresonance.common;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which block positions are currently intangible due to an active Phase Blossom.
 * Maintained on both sides: entities register/unregister their zone on add/remove,
 * and the collision mixin queries this registry to suppress block collisions.
 */
public final class PhaseBlossomZoneTracker {

    private static final Map<ResourceKey<Level>, Set<BlockPos>> SERVER_ZONES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> CLIENT_ZONES = new ConcurrentHashMap<>();

    private PhaseBlossomZoneTracker() {}

    public static boolean isPhased(BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level)) return false;
        var map = level.isClientSide ? CLIENT_ZONES : SERVER_ZONES;
        Set<BlockPos> zone = map.get(level.dimension());
        return zone != null && zone.contains(pos);
    }

    public static void register(Level level, Collection<BlockPos> blocks) {
        var map = level.isClientSide ? CLIENT_ZONES : SERVER_ZONES;
        map.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).addAll(blocks);
    }

    public static void unregister(Level level, Collection<BlockPos> blocks) {
        var map = level.isClientSide ? CLIENT_ZONES : SERVER_ZONES;
        Set<BlockPos> zone = map.get(level.dimension());
        if (zone != null) {
            zone.removeAll(blocks);
            if (zone.isEmpty()) map.remove(level.dimension());
        }
    }
}
