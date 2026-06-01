package net.ragdot.gestaltresonance.common.entity;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseMineEntity extends Entity {

    public static final int STATE_IDLE = 0;
    public static final int STATE_MARKING = 1;
    public static final int STATE_DRAGBACK = 2;

    private static final EntityDataAccessor<String> DATA_OWNER_UUID =
            SynchedEntityData.defineId(PhaseMineEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FACING =
            SynchedEntityData.defineId(PhaseMineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(PhaseMineEntity.class, EntityDataSerializers.INT);

    // Server-only state
    private LivingEntity markedEntity;
    private final Vec3[] snapshots = new Vec3[GestaltCosts.PHASE_MINE_SNAPSHOT_COUNT];
    private final PhaseAfterimageEntity[] afterimages = new PhaseAfterimageEntity[GestaltCosts.PHASE_MINE_SNAPSHOT_COUNT];
    private int snapshotCount = 0;
    private int markPhaseTimer = 0;
    private int dragbackIndex = -1;
    private float bankedDamage = 0f;
    private int ownerGestaltLevel = 1;
    // Set to true only during the final explosion call so the banking event lets that hit through
    private boolean releasingFinalDamage = false;

    // Maps each marked entity UUID → the mine currently marking it (server-side only)
    private static final Map<UUID, PhaseMineEntity> MARKED_ENTITIES = new ConcurrentHashMap<>();
    // Maps owner UUID → set of their active mines (server-side only)
    private static final Map<UUID, Set<PhaseMineEntity>> OWNER_MINES = new ConcurrentHashMap<>();

    public PhaseMineEntity(EntityType<? extends PhaseMineEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public PhaseMineEntity(Level level, double x, double y, double z, UUID ownerUuid, Direction facing) {
        this(GestaltEntities.PHASE_MINE.get(), level);
        this.setPos(x, y, z);
        this.setOwnerUuid(ownerUuid);
        this.setFacing(facing);
        this.setNoGravity(true);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide()) {
            UUID owner = getOwnerUuid();
            if (owner != null)
                OWNER_MINES.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(this);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_UUID, "");
        builder.define(DATA_FACING, Direction.UP.ordinal());
        builder.define(DATA_STATE, STATE_IDLE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        switch (getState()) {
            case STATE_IDLE     -> tickIdle();
            case STATE_MARKING  -> tickMarking();
            case STATE_DRAGBACK -> tickDragback();
        }
    }

    // ── IDLE ─────────────────────────────────────────────────────────────────

    private void tickIdle() {
        UUID ownerUuid = getOwnerUuid();
        AABB hitbox = getBoundingBox().inflate(0.25);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, hitbox,
                e -> (ownerUuid == null || !ownerUuid.equals(e.getUUID()))
                        && !MARKED_ENTITIES.containsKey(e.getUUID())
                        && !(e instanceof SpawnIllusionEntity));
        if (!candidates.isEmpty()) {
            startMarking(candidates.get(0));
        }
    }

    // ── MARKING ──────────────────────────────────────────────────────────────

    private void startMarking(LivingEntity entity) {
        markedEntity = entity;
        MARKED_ENTITIES.put(entity.getUUID(), this);
        snapshotCount = 0;
        markPhaseTimer = 0;
        bankedDamage = 0f;

        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null && level() instanceof ServerLevel sl) {
            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                ownerGestaltLevel = owner.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get()).getGestaltLevel();
            }
        }

        recordSnapshot(); // snapshot[0] at tick 0
        setState(STATE_MARKING);
    }

    private void tickMarking() {
        markPhaseTimer++;

        if (markedEntity == null || !markedEntity.isAlive()) {
            cancelMark();
            return;
        }

        // Snapshot at ticks 20, 40, 60
        if (markPhaseTimer % GestaltCosts.PHASE_MINE_SNAPSHOT_INTERVAL == 0
                && snapshotCount < GestaltCosts.PHASE_MINE_SNAPSHOT_COUNT) {
            recordSnapshot();
        }

        if (markPhaseTimer >= GestaltCosts.PHASE_MINE_MARK_DURATION) {
            startDragback();
        }
    }

    private void recordSnapshot() {
        Vec3 pos = markedEntity.position();
        snapshots[snapshotCount] = pos;
        // Spawn afterimage entity at this position so clients can see the ghost
        PhaseAfterimageEntity ghost = new PhaseAfterimageEntity(
                level(), pos.x, pos.y, pos.z, markedEntity.getId());
        level().addFreshEntity(ghost);
        afterimages[snapshotCount] = ghost;
        snapshotCount++;
    }

    // ── DRAGBACK ─────────────────────────────────────────────────────────────

    private void startDragback() {
        if (markedEntity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        dragbackIndex = snapshotCount - 1;
        setState(STATE_DRAGBACK);
    }

    private void tickDragback() {
        if (markedEntity == null || !markedEntity.isAlive()) {
            this.discard();
            return;
        }

        if (dragbackIndex < 0) {
            this.discard();
            return;
        }

        Vec3 target = snapshots[dragbackIndex];
        Vec3 current = markedEntity.position();
        Vec3 delta = target.subtract(current);
        double dist = delta.length();

        if (dist <= GestaltCosts.PHASE_MINE_CONTACT_RADIUS) {
            // Despawn the afterimage at this waypoint
            if (afterimages[dragbackIndex] != null) {
                afterimages[dragbackIndex].discard();
                afterimages[dragbackIndex] = null;
            }

            boolean isFinal = (dragbackIndex == 0);
            Vec3 explosionCenter = isFinal
                    ? markedEntity.position().add(0, markedEntity.getBbHeight() * 0.5, 0)
                    : target;
            triggerExplosion(explosionCenter, isFinal);

            if (isFinal) {
                this.discard();
            } else {
                dragbackIndex--;
            }
        } else {
            double speed = Math.min(GestaltCosts.PHASE_MINE_DRAGBACK_SPEED, dist);
            Vec3 step = delta.normalize().scale(speed);
            Vec3 next = current.add(step);
            markedEntity.setPos(next.x, next.y, next.z);
            markedEntity.setDeltaMovement(Vec3.ZERO);
            if (markedEntity instanceof ServerPlayer sp) {
                sp.resetFallDistance();
            }
        }
    }

    private void triggerExplosion(Vec3 pos, boolean isFinal) {
        UUID ownerUuid = getOwnerUuid();
        ServerPlayer owner = null;
        if (level() instanceof ServerLevel sl && ownerUuid != null) {
            owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
        }

        float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.PHASE_MINE_EXPLOSION_BASE_RADIUS, ownerGestaltLevel);
        float damage = GestaltExplosionUtil.scaledDamage(GestaltCosts.PHASE_MINE_EXPLOSION_BASE_DAMAGE, ownerGestaltLevel)
                * GestaltCosts.PHASE_MINE_EXPLOSION_DAMAGE_MULTIPLIER;

        if (isFinal) {
            damage += bankedDamage;
            releasingFinalDamage = true; // tell banking event to let this hit through
        }

        DamageSource src = (owner != null)
                ? GestaltDamageTypes.gestalt(level(), owner)
                : level().damageSources().source(GestaltDamageTypes.GESTALT, null, null);

        // Banking event intercepts all hits on the marked entity (marking + intermediate explosions).
        // releasingFinalDamage flag signals the event to allow the final explosion through.
        GestaltExplosionUtil.detonate(level(), pos, radius, damage, src, null);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void cancelMark() {
        if (markedEntity != null) {
            MARKED_ENTITIES.remove(markedEntity.getUUID());
            markedEntity = null;
        }
        discardAllAfterimages();
        setState(STATE_IDLE);
    }

    private void discardAllAfterimages() {
        for (int i = 0; i < snapshotCount; i++) {
            if (afterimages[i] != null) {
                afterimages[i].discard();
                afterimages[i] = null;
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            UUID owner = getOwnerUuid();
            if (owner != null) {
                Set<PhaseMineEntity> set = OWNER_MINES.get(owner);
                if (set != null) {
                    set.remove(this);
                    if (set.isEmpty()) OWNER_MINES.remove(owner, set);
                }
            }
            if (markedEntity != null) {
                MARKED_ENTITIES.remove(markedEntity.getUUID());
                if (markedEntity instanceof Mob mob && getState() == STATE_DRAGBACK) {
                    mob.setNoAi(false);
                }
                markedEntity = null;
                discardAllAfterimages();
            }
        }
        super.remove(reason);
    }

    // ── Banking API ───────────────────────────────────────────────────────────

    public void bankDamage(float amount) {
        bankedDamage += amount;
    }

    /** True only during the final explosion's detonate call — banking event skips interception. */
    public boolean isReleasingFinalDamage() {
        return releasingFinalDamage;
    }

    @Nullable
    public static PhaseMineEntity getMarkingMine(UUID entityUuid) {
        return MARKED_ENTITIES.get(entityUuid);
    }

    // ── Synced data accessors ─────────────────────────────────────────────────

    public void setState(int state) {
        entityData.set(DATA_STATE, state);
    }

    public int getState() {
        return entityData.get(DATA_STATE);
    }

    public void setOwnerUuid(UUID uuid) {
        entityData.set(DATA_OWNER_UUID, uuid.toString());
    }

    @Nullable
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

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("OwnerUUID")) setOwnerUuid(UUID.fromString(tag.getString("OwnerUUID")));
        if (tag.contains("Facing")) setFacing(Direction.values()[tag.getByte("Facing")]);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUuid();
        if (owner != null) tag.putString("OwnerUUID", owner.toString());
        tag.putByte("Facing", (byte) getFacing().ordinal());
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }

    // ── Static utility methods ────────────────────────────────────────────────

    /** Remove all Phase Mines owned by this UUID in the given level. */
    public static void dismissMines(Level level, UUID ownerUuid) {
        if (level.isClientSide) return;
        Set<PhaseMineEntity> mines = OWNER_MINES.get(ownerUuid);
        if (mines == null) return;
        mines.stream().filter(e -> e.level() == level).toList().forEach(Entity::discard);
    }

    /** Count active Phase Mines owned by this UUID in the given level. */
    public static int countMines(Level level, UUID ownerUuid) {
        if (level.isClientSide) return 0;
        Set<PhaseMineEntity> mines = OWNER_MINES.get(ownerUuid);
        if (mines == null) return 0;
        return (int) mines.stream().filter(e -> e.level() == level && !e.isRemoved()).count();
    }

    /**
     * If the player already has {@code limit} mines, discard the oldest one (highest tickCount)
     * to make room for the new mine about to be spawned.
     */
    public static void dismissOldestIfAtLimit(Level level, UUID ownerUuid, int limit) {
        if (level.isClientSide) return;
        Set<PhaseMineEntity> mines = OWNER_MINES.get(ownerUuid);
        if (mines == null) return;
        List<PhaseMineEntity> inLevel = mines.stream()
                .filter(e -> e.level() == level && !e.isRemoved())
                .toList();
        if (inLevel.size() >= limit) {
            inLevel.stream()
                    .max(Comparator.comparingInt(e -> e.tickCount))
                    .ifPresent(Entity::discard);
        }
    }
}
