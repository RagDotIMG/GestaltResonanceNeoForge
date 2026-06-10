package net.ragdot.gestaltresonance.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class SpillwaysLightManager {

    // ── Player passive light ──────────────────────────────────────────────────
    private static final Map<UUID, BlockPos>           playerLightPos = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> playerLightDim = new HashMap<>();

    // ── Tear entity lights (indexed by entity UUID, with owner lookup) ────────
    private static final Map<UUID, BlockPos>           tearLightPos   = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> tearLightDim   = new HashMap<>();
    // owner UUID → set of entity UUIDs they own that have registered lights
    private static final Map<UUID, Set<UUID>>          ownerToTears   = new HashMap<>();

    // ── Tick (player passive) ─────────────────────────────────────────────────

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 2 != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

            if (!state.isSummoned() || !GestaltIds.SPILLWAYS.equals(state.getGestaltId())) {
                removePlayerLight(server, uuid);
                continue;
            }

            ServerLevel level = player.serverLevel();
            BlockPos newPos = player.blockPosition();
            ResourceKey<Level> newDim = level.dimension();

            BlockPos oldPos = playerLightPos.get(uuid);
            ResourceKey<Level> oldDim = playerLightDim.get(uuid);

            boolean dimChanged = oldDim == null || !newDim.equals(oldDim);
            boolean posChanged = oldPos == null || !newPos.equals(oldPos);

            if (oldPos != null && (dimChanged || posChanged)) {
                clearLightAt(server, oldDim, oldPos);
                playerLightPos.remove(uuid);
                playerLightDim.remove(uuid);
            }

            if (posChanged || dimChanged) {
                BlockState at = level.getBlockState(newPos);
                if (at.isAir()) {
                    level.setBlock(newPos,
                        Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, GestaltCosts.SPILLWAYS_LIGHT_LEVEL),
                        Block.UPDATE_CLIENTS);
                    playerLightPos.put(uuid, newPos);
                    playerLightDim.put(uuid, newDim);
                }
            }
        }
    }

    // ── Tear entity light registration ────────────────────────────────────────

    public static void registerTearLight(UUID ownerUUID, UUID entityUUID,
                                          ResourceKey<Level> dim, BlockPos pos) {
        tearLightPos.put(entityUUID, pos);
        tearLightDim.put(entityUUID, dim);
        ownerToTears.computeIfAbsent(ownerUUID, k -> new HashSet<>()).add(entityUUID);
    }

    /** Called when a tear entity cleans up its own light block normally. */
    public static void unregisterTearLight(UUID entityUUID) {
        tearLightPos.remove(entityUUID);
        tearLightDim.remove(entityUUID);
        // ownerToTears retains the UUID; clearAllForPlayer will ignore nulls on lookup
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Removes the player's passive light block and any tear entity lights they own.
     * Call on logout, respawn, and dimension change.
     */
    public static void clearAllForPlayer(MinecraftServer server, UUID playerUUID) {
        removePlayerLight(server, playerUUID);

        Set<UUID> entityIds = ownerToTears.remove(playerUUID);
        if (entityIds == null) return;
        for (UUID entityId : entityIds) {
            BlockPos pos = tearLightPos.remove(entityId);
            ResourceKey<Level> dim = tearLightDim.remove(entityId);
            if (pos != null && dim != null) {
                clearLightAt(server, dim, pos);
            }
        }
    }

    public static void removePlayerLight(MinecraftServer server, UUID uuid) {
        BlockPos pos = playerLightPos.remove(uuid);
        ResourceKey<Level> dim = playerLightDim.remove(uuid);
        if (pos != null && dim != null) {
            clearLightAt(server, dim, pos);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void clearLightAt(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos) {
        ServerLevel level = server.getLevel(dim);
        if (level == null) return;
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private SpillwaysLightManager() {}
}
