package net.ragdot.gestaltresonance.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
import net.ragdot.gestaltresonance.common.block.PopDripBlock;

/**
 * Server-side persistent tracker for per-player PopDrip vines.
 * Tracks the END block position of each vine. Enforces {@link GestaltCosts#popDripCap};
 * evicts the oldest vine (removing its blocks top-down) when exceeded.
 *
 * No posToOwner reverse map is needed because {@link net.ragdot.gestaltresonance.common.block.PopDripBlockEntity}
 * already stores the owner UUID and calls {@link #removeDrip} from onRemove.
 */
public class PopDripTracker extends SavedData {

    private static final String DATA_ID = "gestaltresonance_pop_drips";

    private record DimPos(ResourceKey<Level> dim, BlockPos pos) {}

    private final Map<UUID, LinkedList<DimPos>> drips = new HashMap<>();

    public static SavedData.Factory<PopDripTracker> factory() {
        return new SavedData.Factory<>(PopDripTracker::new, PopDripTracker::load, null);
    }

    public static PopDripTracker get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_ID);
    }

    /**
     * Register a new vine for the player. If the cap is exceeded, the oldest vine's
     * blocks are removed from the world (top-down so the END block fires last and its
     * scheduleChainRemoval call finds no remaining blocks).
     *
     * @param endPos the END block position (lowest block of the vine)
     */
    public void addDrip(MinecraftServer server, UUID playerUuid, ResourceKey<Level> dim,
                        BlockPos endPos, int gestaltLevel) {
        LinkedList<DimPos> list = drips.computeIfAbsent(playerUuid, k -> new LinkedList<>());
        int cap = GestaltCosts.popDripCap(gestaltLevel);
        while (list.size() >= cap) {
            DimPos oldest = list.removeFirst();
            ServerLevel level = server.getLevel(oldest.dim());
            if (level != null) {
                // Collect the chain upward from the END block
                List<BlockPos> chain = new ArrayList<>();
                BlockPos p = oldest.pos();
                while (level.getBlockState(p).getBlock() instanceof PopDripBlock) {
                    chain.add(p);
                    p = p.above();
                }
                // Remove top-down: END fires last, by which point all blocks above are gone
                // so scheduleChainRemoval(sl, endPos.above()) finds nothing and is a no-op.
                for (int i = chain.size() - 1; i >= 0; i--) {
                    level.removeBlock(chain.get(i), false);
                }
            }
        }
        list.addLast(new DimPos(dim, endPos));
        setDirty();
    }

    /**
     * Deregister a vine when its END block is removed.
     * Called from {@link PopDripBlock#onRemove} for END=true blocks.
     */
    public void removeDrip(UUID playerUuid, BlockPos endPos) {
        LinkedList<DimPos> list = drips.get(playerUuid);
        if (list == null) return;
        list.removeIf(dp -> dp.pos().equals(endPos));
        if (list.isEmpty()) drips.remove(playerUuid);
        setDirty();
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, LinkedList<DimPos>> entry : drips.entrySet()) {
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

    private static PopDripTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        PopDripTracker tracker = new PopDripTracker();
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
                list.addLast(new DimPos(dim, pos));
            }
            if (!list.isEmpty()) tracker.drips.put(uuid, list);
        }
        return tracker;
    }
}
