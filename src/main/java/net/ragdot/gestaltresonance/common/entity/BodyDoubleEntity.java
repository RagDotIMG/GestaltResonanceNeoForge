package net.ragdot.gestaltresonance.common.entity;

import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.ragdot.gestaltresonance.common.GestaltAttachments;

public class BodyDoubleEntity extends LivingEntity {

    private static final EntityDataAccessor<String> DATA_OWNER_UUID_STR =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_IS_SLIM =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.BOOLEAN);

    // Snapshot of the owner's equipment at projection time. Synced to the client so the
    // body double's renderer can show the owner's armor/held items.
    private static final EntityDataAccessor<ItemStack> DATA_HELMET =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_CHEST =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_LEGS =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_FEET =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_MAINHAND =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_OFFHAND =
            SynchedEntityData.defineId(BodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);

    public BodyDoubleEntity(EntityType<? extends BodyDoubleEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_UUID_STR, "");
        builder.define(DATA_OWNER_NAME, "");
        builder.define(DATA_IS_SLIM, false);
        builder.define(DATA_HELMET, ItemStack.EMPTY);
        builder.define(DATA_CHEST, ItemStack.EMPTY);
        builder.define(DATA_LEGS, ItemStack.EMPTY);
        builder.define(DATA_FEET, ItemStack.EMPTY);
        builder.define(DATA_MAINHAND, ItemStack.EMPTY);
        builder.define(DATA_OFFHAND, ItemStack.EMPTY);
    }

    /** Snapshot the owner's armor + hand items into the body double's synced data. */
    public void copyEquipmentFrom(Player owner) {
        entityData.set(DATA_HELMET,   owner.getItemBySlot(EquipmentSlot.HEAD).copy());
        entityData.set(DATA_CHEST,    owner.getItemBySlot(EquipmentSlot.CHEST).copy());
        entityData.set(DATA_LEGS,     owner.getItemBySlot(EquipmentSlot.LEGS).copy());
        entityData.set(DATA_FEET,     owner.getItemBySlot(EquipmentSlot.FEET).copy());
        entityData.set(DATA_MAINHAND, owner.getItemBySlot(EquipmentSlot.MAINHAND).copy());
        entityData.set(DATA_OFFHAND,  owner.getItemBySlot(EquipmentSlot.OFFHAND).copy());
    }

    public void setSlim(boolean slim) { entityData.set(DATA_IS_SLIM, slim); }
    public boolean isSlim() { return entityData.get(DATA_IS_SLIM); }

    public void setOwner(UUID uuid, String name) {
        entityData.set(DATA_OWNER_UUID_STR, uuid.toString());
        entityData.set(DATA_OWNER_NAME, name);
    }

    @Nullable
    public UUID getOwnerUuid() {
        String s = entityData.get(DATA_OWNER_UUID_STR);
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getOwnerName() {
        return entityData.get(DATA_OWNER_NAME);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            NeoForge.EVENT_BUS.post(new BodyDoubleHitEvent(ownerUuid, source, amount, level()));
        }
        return true;
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPushedByFluid(net.neoforged.neoforge.fluids.FluidType type) { return false; }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD     -> entityData.get(DATA_HELMET);
            case CHEST    -> entityData.get(DATA_CHEST);
            case LEGS     -> entityData.get(DATA_LEGS);
            case FEET     -> entityData.get(DATA_FEET);
            case MAINHAND -> entityData.get(DATA_MAINHAND);
            case OFFHAND  -> entityData.get(DATA_OFFHAND);
            default       -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // Body doubles are server-controlled snapshots — no external writes.
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of(
                entityData.get(DATA_FEET),
                entityData.get(DATA_LEGS),
                entityData.get(DATA_CHEST),
                entityData.get(DATA_HELMET));
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return List.of(entityData.get(DATA_MAINHAND), entityData.get(DATA_OFFHAND));
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return false; }

    /** Body doubles drop nothing on death — they're a transient projection construct. */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {}

    @Override
    public boolean shouldDropExperience() { return false; }

    /** Self-discard if the owner is no longer soul projecting — catches missed teardown paths. */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 20 != 0) return;
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null) { discard(); return; }
        if (!(level() instanceof ServerLevel sl)) return;
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null || !owner.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get()).isSoulProjecting()) {
            discard();
        }
    }

    /** Remove all body doubles owned by the given UUID from this level. */
    public static void dismissExistingDoubles(Level level, UUID ownerUuid) {
        if (level.isClientSide) return;
        ((ServerLevel) level).getEntities().getAll().forEach(e -> {
            if (e instanceof BodyDoubleEntity bde && ownerUuid.equals(bde.getOwnerUuid()))
                bde.discard();
        });
    }
}
