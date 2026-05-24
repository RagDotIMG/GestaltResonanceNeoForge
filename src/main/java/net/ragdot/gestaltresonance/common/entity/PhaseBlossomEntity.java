package net.ragdot.gestaltresonance.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.jetbrains.annotations.NotNull;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltParticles;
import net.ragdot.gestaltresonance.common.PhaseBlossomZoneTracker;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PhaseBlossomEntity extends Entity {

    private static final EntityDataAccessor<String> DATA_OWNER_UUID =
            SynchedEntityData.defineId(PhaseBlossomEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FACING =
            SynchedEntityData.defineId(PhaseBlossomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_HIT_POS =
            SynchedEntityData.defineId(PhaseBlossomEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_COLLAPSING =
            SynchedEntityData.defineId(PhaseBlossomEntity.class, EntityDataSerializers.BOOLEAN);

    public PhaseBlossomEntity(EntityType<? extends PhaseBlossomEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public PhaseBlossomEntity(Level level, double x, double y, double z,
                              UUID ownerUuid, Direction facing, BlockPos hitPos) {
        this(GestaltEntities.PHASE_BLOSSOM.get(), level);
        this.setPos(x, y, z);
        this.setOwnerUuid(ownerUuid);
        this.setFacing(facing);
        this.setHitPos(hitPos);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_UUID, "");
        builder.define(DATA_FACING, Direction.UP.ordinal());
        builder.define(DATA_HIT_POS, Optional.empty());
        builder.define(DATA_COLLAPSING, false);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        // Server: synced data already populated from constructor — register solid blocks now.
        // Client: DATA_HIT_POS is still empty (synced data packet not yet received);
        //         registration is deferred to onSyncedDataUpdated() below.
        if (!level().isClientSide()) {
            getHitPos().ifPresent(hitPos ->
                PhaseBlossomZoneTracker.register(level(), solidBlocks(hitPos, getFacing())));
        }
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide()) return;

        if (key == DATA_HIT_POS) {
            // Client: synced data arrived — register the zone now.
            getHitPos().ifPresent(hitPos ->
                PhaseBlossomZoneTracker.register(level(), solidBlocks(hitPos, getFacing())));
        } else if (key == DATA_COLLAPSING && isCollapsing()) {
            // Client: blossom is collapsing — unregister zone immediately so collision restores
            // in sync with the server instead of waiting for the entity removal packet.
            getHitPos().ifPresent(hitPos ->
                PhaseBlossomZoneTracker.unregister(level(), computeBlocks(hitPos, getFacing())));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        // Deferred discard: one tick after DATA_COLLAPSING was broadcast so the synced-data
        // packet arrives at clients before the entity removal packet.
        if (isCollapsing()) {
            this.discard();
            return;
        }

        if (tickCount % 20 != 0) return;
        Optional<BlockPos> hitPosOpt = getHitPos();
        if (hitPosOpt.isEmpty()) return;

        if (level() instanceof ServerLevel sl) {
            Set<BlockPos> zone = computeBlocks(hitPosOpt.get(), getFacing());
            for (BlockPos pos : zone) {
                // Skip air and other already-passable blocks — they weren't registered
                // in the tracker and shouldn't visually suggest they're phased.
                if (!PhaseBlossomZoneTracker.isPhased(sl, pos)) continue;
                double px = pos.getX() + 0.1 + level().random.nextDouble() * 0.8;
                double py = pos.getY() + 0.1 + level().random.nextDouble() * 0.8;
                double pz = pos.getZ() + 0.1 + level().random.nextDouble() * 0.8;
                sl.sendParticles(GestaltParticles.GESTALT_ILLUSION.get(), px, py, pz, 1, 0, 0, 0, 0);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // Unregister any remaining zone entries (no-op if already cleared by setCollapsing).
        getHitPos().ifPresent(hitPos ->
            PhaseBlossomZoneTracker.unregister(level(), computeBlocks(hitPos, getFacing())));
        super.remove(reason);
    }

    // ── Collapsing (ordered zone teardown) ────────────────────────────────────

    /**
     * Initiates a two-phase collapse:
     * 1. Immediately unregisters the zone on the server so blocks are solid again.
     * 2. Broadcasts DATA_COLLAPSING=true so clients unregister their zones before
     *    the entity removal packet arrives (avoids a 1-tick client desync).
     * 3. tick() will call discard() on the next server tick.
     */
    public void setCollapsing(boolean collapsing) {
        entityData.set(DATA_COLLAPSING, collapsing);
        if (!level().isClientSide() && collapsing) {
            getHitPos().ifPresent(hitPos ->
                PhaseBlossomZoneTracker.unregister(level(), computeBlocks(hitPos, getFacing())));
        }
    }

    public boolean isCollapsing() {
        return entityData.get(DATA_COLLAPSING);
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    /**
     * All 27 positions in the 3×3×3 cube behind {@code hitPos} (depth 0–2 inward from facing).
     */
    public static Set<BlockPos> computeBlocks(BlockPos hitPos, Direction facing) {
        Direction inward = facing.getOpposite();
        Direction u, v;
        switch (facing.getAxis()) {
            case Y -> { u = Direction.NORTH; v = Direction.EAST; }
            case X -> { u = Direction.NORTH; v = Direction.UP; }
            default -> { u = Direction.EAST;  v = Direction.UP; }
        }

        Set<BlockPos> result = new HashSet<>(27);
        for (int depth = 0; depth < 3; depth++) {
            BlockPos layer = hitPos.relative(inward, depth);
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    result.add(layer.relative(u, a).relative(v, b));
                }
            }
        }
        return result;
    }

    /**
     * Subset of {@link #computeBlocks} containing only blocks with non-empty collision shapes.
     * Air, water, powdered snow, etc. are excluded — they're already passable and don't need tracking.
     */
    private Set<BlockPos> solidBlocks(BlockPos hitPos, Direction facing) {
        Level level = level();
        Set<BlockPos> full = computeBlocks(hitPos, facing);
        Set<BlockPos> solid = new HashSet<>(full.size());
        for (BlockPos pos : full) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.empty()).isEmpty()) {
                solid.add(pos);
            }
        }
        return solid;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    public static Optional<PhaseBlossomEntity> findBlossom(ServerLevel level, UUID ownerUuid) {
        AABB worldBounds = new AABB(
                -30_000_000, level.getMinBuildHeight(), -30_000_000,
                 30_000_000, level.getMaxBuildHeight(),  30_000_000);
        return level.getEntitiesOfClass(PhaseBlossomEntity.class, worldBounds,
                        e -> ownerUuid.equals(e.getOwnerUuid()))
                .stream().findFirst();
    }

    public static void dismissBlossom(ServerLevel level, UUID ownerUuid) {
        findBlossom(level, ownerUuid).ifPresent(e -> e.setCollapsing(true));
    }

    // ── Synced data accessors ─────────────────────────────────────────────────

    public void setOwnerUuid(UUID uuid) {
        entityData.set(DATA_OWNER_UUID, uuid.toString());
    }

    public UUID getOwnerUuid() {
        String s = entityData.get(DATA_OWNER_UUID);
        if (s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    public void setFacing(Direction dir) {
        entityData.set(DATA_FACING, dir.ordinal());
    }

    public Direction getFacing() {
        return Direction.values()[entityData.get(DATA_FACING)];
    }

    public void setHitPos(BlockPos pos) {
        entityData.set(DATA_HIT_POS, Optional.of(pos));
    }

    public Optional<BlockPos> getHitPos() {
        return entityData.get(DATA_HIT_POS);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("OwnerUUID")) setOwnerUuid(UUID.fromString(tag.getString("OwnerUUID")));
        if (tag.contains("Facing")) setFacing(Direction.values()[tag.getByte("Facing")]);
        if (tag.contains("HitPosX")) {
            setHitPos(new BlockPos(tag.getInt("HitPosX"), tag.getInt("HitPosY"), tag.getInt("HitPosZ")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUuid();
        if (owner != null) tag.putString("OwnerUUID", owner.toString());
        tag.putByte("Facing", (byte) getFacing().ordinal());
        getHitPos().ifPresent(pos -> {
            tag.putInt("HitPosX", pos.getX());
            tag.putInt("HitPosY", pos.getY());
            tag.putInt("HitPosZ", pos.getZ());
        });
    }

    @Override
    public boolean shouldBeSaved() { return true; }

    @Override
    public boolean isPickable() { return false; }
}
