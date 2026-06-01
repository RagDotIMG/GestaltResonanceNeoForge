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
import net.ragdot.gestaltresonance.common.block.PopVineBlock;

/**
 * Server-side persistent tracker for per-player PopVine placements.
 * Tracks vine anchors (the topmost block of each vine). Enforces
 * {@link GestaltCosts#popVineCap}; evicts the oldest vine when exceeded.
 */
public class PopVineTracker extends SavedData {

    private static final String DATA_ID = "gestaltresonance_pop_vines";

    private record DimPos(ResourceKey<Level> dim, BlockPos pos) {}

    private final Map<UUID, LinkedList<DimPos>> vines = new HashMap<>();
    /** Reverse map: anchor pos → owner UUID. Used to look up owner on block removal. */
    private final Map<DimPos, UUID> posToOwner = new HashMap<>();

    public static SavedData.Factory<PopVineTracker> factory() {
        return new SavedData.Factory<>(PopVineTracker::new, PopVineTracker::load, null);
    }

    public static PopVineTracker get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_ID);
    }

    /**
     * Register a new vine for the player. If the cap is exceeded, the oldest vine's
     * blocks are removed from the world and its entry is de-registered.
     *
     * @param anchor the topmost block position of the vine (start pos from placePopVine)
     */
    public void addVine(MinecraftServer server, UUID playerUuid, ResourceKey<Level> dim,
                        BlockPos anchor, int gestaltLevel) {
        LinkedList<DimPos> list = vines.computeIfAbsent(playerUuid, k -> new LinkedList<>());
        int cap = GestaltCosts.popVineCap(gestaltLevel);
        while (list.size() >= cap) {
            DimPos oldest = list.removeFirst();
            posToOwner.remove(oldest);
            ServerLevel level = server.getLevel(oldest.dim());
            if (level != null) {
                // Vine grows downward: try anchor, anchor-1, anchor-2
                for (int i = 0; i < 3; i++) {
                    BlockPos p = oldest.pos().below(i);
                    if (level.getBlockState(p).getBlock() instanceof PopVineBlock) {
                        level.removeBlock(p, false);
                    }
                }
            }
        }
        DimPos entry = new DimPos(dim, anchor);
        list.addLast(entry);
        posToOwner.put(entry, playerUuid);
        setDirty();
    }

    /**
     * Called from {@link PopVineBlock#onRemove} to deregister the vine whose anchor
     * is at {@code pos}. Non-anchor blocks are silently ignored (only the anchor is tracked).
     */
    public void removeAt(ServerLevel level, BlockPos pos) {
        DimPos key = new DimPos(level.dimension(), pos);
        UUID owner = posToOwner.remove(key);
        if (owner == null) return;
        LinkedList<DimPos> list = vines.get(owner);
        if (list != null) {
            list.removeIf(dp -> dp.dim().equals(level.dimension()) && dp.pos().equals(pos));
            if (list.isEmpty()) vines.remove(owner);
        }
        setDirty();
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, LinkedList<DimPos>> entry : vines.entrySet()) {
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

    private static PopVineTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        PopVineTracker tracker = new PopVineTracker();
        ListTag playersTag = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playersTag.size(); i++) {
            CompoundTag playerTag = playersTag.getCompound(i);
            UUID uuid = playerTag.getUUID("uuid");
            LinkedList<DimPos> list = new LinkedList<>();
            ListTag posListTag = playerTag.getList("positions", Tag.TAG_COMPOUND);
            for (int j = 0; j < posListTag.size(); j++) {
                CompoundTag posTag = posListTag.getCompound(j);
                ResourceKey<Level> dim = ResourceKey.create(
                        Registries.DIMENSION, ResourceLocation.parse(posTag.getString("dim")));
                BlockPos pos = BlockPos.of(posTag.getLong("pos"));
                DimPos dp = new DimPos(dim, pos);
                list.addLast(dp);
                tracker.posToOwner.put(dp, uuid);
            }
            if (!list.isEmpty()) tracker.vines.put(uuid, list);
        }
        return tracker;
    }
}
