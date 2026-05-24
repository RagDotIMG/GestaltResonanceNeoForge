package net.ragdot.gestaltresonance.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltParticles;

/**
 * A stationary ghost entity spawned at each position snapshot during Phase Mine's marking phase.
 * Stores the network ID of the entity being marked so the client can look it up and render
 * a violet-tinted, semi-transparent copy of that entity's model at this position.
 */
public class PhaseAfterimageEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_SOURCE_ID =
            SynchedEntityData.defineId(PhaseAfterimageEntity.class, EntityDataSerializers.INT);

    public PhaseAfterimageEntity(EntityType<? extends PhaseAfterimageEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public PhaseAfterimageEntity(Level level, double x, double y, double z, int sourceEntityId) {
        this(GestaltEntities.PHASE_AFTERIMAGE.get(), level);
        this.setPos(x, y, z);
        this.setSourceEntityId(sourceEntityId);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SOURCE_ID, -1);
    }

    public void setSourceEntityId(int id) {
        entityData.set(DATA_SOURCE_ID, id);
    }

    public int getSourceEntityId() {
        return entityData.get(DATA_SOURCE_ID);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 5 != 0) return;
        if (!(level() instanceof ServerLevel sl)) return;
        for (int i = 0; i < 2; i++) {
            double px = getX() + (random.nextDouble() - 0.5) * getBbWidth();
            double py = getY() + random.nextDouble() * getBbHeight();
            double pz = getZ() + (random.nextDouble() - 0.5) * getBbWidth();
            sl.sendParticles(GestaltParticles.GESTALT_ILLUSION.get(), px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }
}
