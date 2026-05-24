package net.ragdot.gestaltresonance.common;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.ragdot.gestaltresonance.common.block.PopSproutBlock;

/**
 * Server-side persistent tracker for per-player PopSprout positions.
 * Enforces the per-player cap (4 + (level-1)/3); evicts oldest sprout when exceeded.
 * Stored in overworld SavedData so it persists across sessions.
 */
public class PopSproutTracker extends SavedData {

    private static final String DATA_ID = "gestaltresonance_pop_sprouts";

    /** Dimension + block position pair for cross-world sprout tracking. */
    private record DimPos(ResourceKey<Level> dim, BlockPos pos) {}

    private final Map<UUID, LinkedList<DimPos>> sprouts = new HashMap<>();

    // ── Factory ──────────────────────────────────────────────────────────────

    public static SavedData.Factory<PopSproutTracker> factory() {
        return new SavedData.Factory<>(PopSproutTracker::new, PopSproutTracker::load, null);
    }

    public static PopSproutTracker get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_ID);
    }

    // ── Cap formula ───────────────────────────────────────────────────────────

    public int getCap(int gestaltLevel) {
        return 4 + (gestaltLevel - 1) / 3;
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Register a new sprout for the player. If the cap is exceeded, the oldest sprout
     * is removed from the world and de-registered before the new one is added.
     */
    public void addSprout(MinecraftServer server, UUID playerUuid, ResourceKey<Level> dim,
                          BlockPos pos, int gestaltLevel) {
        LinkedList<DimPos> list = sprouts.computeIfAbsent(playerUuid, k -> new LinkedList<>());
        int cap = getCap(gestaltLevel);
        while (list.size() >= cap) {
            DimPos oldest = list.removeFirst();
            ServerLevel oldestLevel = server.getLevel(oldest.dim());
            if (oldestLevel != null && oldestLevel.getBlockState(oldest.pos()).getBlock() instanceof PopSproutBlock) {
                oldestLevel.removeBlock(oldest.pos(), false);
            }
        }
        list.addLast(new DimPos(dim, pos));
        setDirty();
    }

    /**
     * Returns the most-recently-added pop sprout (across all players) that still
     * exists in the world within {@code radius} blocks of {@code center}.
     * Iterates each player's list newest-first and returns the first valid match.
     */
    @javax.annotation.Nullable
    public BlockPos findNewestInRange(ServerLevel level, net.minecraft.world.phys.Vec3 center, double radius) {
        double rSq = radius * radius;
        ResourceKey<Level> dim = level.dimension();
        for (LinkedList<DimPos> list : sprouts.values()) {
            var it = list.descendingIterator();
            while (it.hasNext()) {
                DimPos dp = it.next();
                if (!dp.dim().equals(dim)) continue;
                if (dp.pos().distToCenterSqr(center.x, center.y, center.z) > rSq) continue;
                if (!(level.getBlockState(dp.pos()).getBlock() instanceof PopSproutBlock)) continue;
                return dp.pos();
            }
        }
        return null;
    }

    /** Remove a sprout from tracking (called when it detonates or is broken). */
    public void removeSprout(UUID playerUuid, BlockPos pos) {
        LinkedList<DimPos> list = sprouts.get(playerUuid);
        if (list == null) return;
        list.removeIf(dp -> dp.pos().equals(pos));
        if (list.isEmpty()) sprouts.remove(playerUuid);
        setDirty();
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, LinkedList<DimPos>> entry : sprouts.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("uuid", entry.getKey());
            ListTag posListTag = new ListTag();
            for (DimPos dp : entry.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putString("dim", dp.dim().location().toString());
                posTag.putLong("pos", dp.pos().asLong());
                posListTag.add(posTag);
            }
            playerTag.put("positions", posListTag);
            playersTag.add(playerTag);
        }
        tag.put("players", playersTag);
        return tag;
    }

    private static PopSproutTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        PopSproutTracker tracker = new PopSproutTracker();
        ListTag playersTag = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playersTag.size(); i++) {
            CompoundTag playerTag = playersTag.getCompound(i);
            UUID uuid = playerTag.getUUID("uuid");
            LinkedList<DimPos> list = new LinkedList<>();
            ListTag posListTag = playerTag.getList("positions", Tag.TAG_COMPOUND);
            for (int j = 0; j < posListTag.size(); j++) {
                CompoundTag posTag = posListTag.getCompound(j);
                ResourceKey<Level> dim = ResourceKey.create(
                        Registries.DIMENSION,
                        ResourceLocation.parse(posTag.getString("dim")));
                BlockPos pos = BlockPos.of(posTag.getLong("pos"));
                list.addLast(new DimPos(dim, pos));
            }
            if (!list.isEmpty()) tracker.sprouts.put(uuid, list);
        }
        return tracker;
    }
}
