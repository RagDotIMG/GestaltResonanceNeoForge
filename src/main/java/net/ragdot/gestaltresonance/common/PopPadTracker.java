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
import net.ragdot.gestaltresonance.common.block.PopPadBlock;

/**
 * Server-side persistent tracker for per-player PopPad placements.
 * Enforces {@link GestaltCosts#popPadCap}; evicts the oldest pad when exceeded.
 */
public class PopPadTracker extends SavedData {

    private static final String DATA_ID = "gestaltresonance_pop_pads";

    private record DimPos(ResourceKey<Level> dim, BlockPos pos) {}

    private final Map<UUID, LinkedList<DimPos>> pads = new HashMap<>();
    /** Reverse map: block pos → owner UUID. Used to look up owner on block removal. */
    private final Map<DimPos, UUID> posToOwner = new HashMap<>();

    public static SavedData.Factory<PopPadTracker> factory() {
        return new SavedData.Factory<>(PopPadTracker::new, PopPadTracker::load, null);
    }

    public static PopPadTracker get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_ID);
    }

    /**
     * Register a new pad for the player. If the cap is exceeded, the oldest pad is
     * removed from the world and de-registered.
     */
    public void addPad(MinecraftServer server, UUID playerUuid, ResourceKey<Level> dim,
                       BlockPos pos, int gestaltLevel) {
        LinkedList<DimPos> list = pads.computeIfAbsent(playerUuid, k -> new LinkedList<>());
        int cap = GestaltCosts.popPadCap(gestaltLevel);
        while (list.size() >= cap) {
            DimPos oldest = list.removeFirst();
            posToOwner.remove(oldest);
            ServerLevel level = server.getLevel(oldest.dim());
            if (level != null && level.getBlockState(oldest.pos()).getBlock() instanceof PopPadBlock) {
                level.removeBlock(oldest.pos(), false);
            }
        }
        DimPos entry = new DimPos(dim, pos);
        list.addLast(entry);
        posToOwner.put(entry, playerUuid);
        setDirty();
    }

    /**
     * Called from {@link PopPadBlock#onRemove} to deregister the pad at {@code pos}.
     */
    public void removeAt(ServerLevel level, BlockPos pos) {
        DimPos key = new DimPos(level.dimension(), pos);
        UUID owner = posToOwner.remove(key);
        if (owner == null) return;
        LinkedList<DimPos> list = pads.get(owner);
        if (list != null) {
            list.removeIf(dp -> dp.dim().equals(level.dimension()) && dp.pos().equals(pos));
            if (list.isEmpty()) pads.remove(owner);
        }
        setDirty();
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, LinkedList<DimPos>> entry : pads.entrySet()) {
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

    private static PopPadTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        PopPadTracker tracker = new PopPadTracker();
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
            if (!list.isEmpty()) tracker.pads.put(uuid, list);
        }
        return tracker;
    }
}
