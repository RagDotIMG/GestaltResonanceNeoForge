package net.ragdot.gestaltresonance.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltCosts;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Body double for Time Phase (3S). Urgency-driven: fights tactically at low urgency,
 * transitions to sprinting toward its destination as time runs out. Acts as an aggro
 * magnet — redirecting nearby hostiles to target it instead of the owner.
 */
public class TimePhaseBodyDoubleEntity extends PathfinderMob {

    // ── Synced data ───────────────────────────────────────────────────────────

    private static final EntityDataAccessor<String> DATA_OWNER_UUID_STR =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_MAINHAND =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_OFFHAND =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_FEET =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_LEGS =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_CHEST =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_HEAD =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_IS_SLIM =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.BOOLEAN);

    // ── State ─────────────────────────────────────────────────────────────────

    @Nullable private UUID ownerUuid;

    /** Initial facing yaw at spawn; used by DriftWanderGoal when no destination is set. */
    float initialYaw = 0f;

    /** Set by Time Skip — immediately activates full-urgency destination travel. */
    boolean hasWalkTarget = false;
    @Nullable Vec3 walkTarget = null;

    /** Server-side tick counter for urgency computation. */
    private int ticksAlive = 0;

    /** Accumulated damage that would have been dealt to this entity; echoed to the owner on despawn. */
    private float bankedDamage = 0f;


    private static final ResourceLocation COPY_STATS_HP_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "time_phase_bd_hp");
    private static final ResourceLocation COPY_STATS_ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "time_phase_bd_armor");
    private static final ResourceLocation COPY_STATS_TOUGHNESS_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "time_phase_bd_toughness");

    // ── Urgency ───────────────────────────────────────────────────────────────

    /** 0 → 1 over the full Time Phase window. Drives all behavioral thresholds. */
    public float getUrgency() {
        return Math.min(1f, ticksAlive / (float) GestaltCosts.TIME_PHASE_GHOST_TICKS);
    }

    // ── AI thresholds (tuning knobs in one place) ─────────────────────────────

    /** Threat score at or above which the entity flees rather than fighting. */
    private static final float FLEE_THREAT_THRESHOLD   = 18f;
    /** Threat score below which the entity will initiate combat. */
    private static final float ENGAGE_THREAT_THRESHOLD = 7f;
    /** Urgency at which the aggro-redirect goal shuts off. */
    private static final float URGENCY_REDIRECT_STOP   = 0.70f;
    /** Urgency at which destination travel activates autonomously. */
    private static final float URGENCY_TRAVEL_START    = 0.30f;
    /** Urgency above which combat is suppressed (except finisher kills). */
    private static final float URGENCY_ENGAGE_STOP     = 0.75f;
    /** Radius (blocks) scanned for hostiles to redirect. */
    private static final double AGGRO_RADIUS           = 20.0;
    /** Radius (blocks) at which a lethal threat triggers fleeing. */
    private static final double FLEE_RADIUS            = 14.0;
    /** Radius (blocks) within which the entity will initiate/continue combat. */
    private static final float ENGAGE_RANGE            = 16f;
    /** Squared melee contact range. */
    private static final float ATTACK_RANGE_SQ         = 2.5f * 2.5f;
    /** Ticks between melee swings. */
    private static final int   ATTACK_COOLDOWN_TICKS   = 20;
    /** Multiplier on attack damage used to define a "finisher" (can one-shot). */
    private static final float FINISHER_THRESHOLD_MULT = 1.3f;
    /** Score delta below which the body double won't switch targets (hysteresis). */
    private static final float TARGET_SWITCH_HYSTERESIS = 2f;

    // ── Construction ──────────────────────────────────────────────────────────

    public TimePhaseBodyDoubleEntity(EntityType<? extends TimePhaseBodyDoubleEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,     200.0)
                .add(Attributes.MOVEMENT_SPEED,   0.2)
                .add(Attributes.FOLLOW_RANGE,    32.0)
                .add(Attributes.ATTACK_DAMAGE,    8.0);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            ticksAlive++;
        }
    }

    // ── External API (called by AmenBreakPower3S) ─────────────────────────────

    public void setInitialYaw(float yaw) {
        this.initialYaw = yaw;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yRotO = yaw;
    }

    /** Sets the destination for gradual urgency-scaled travel (normal prediction phase). Does NOT force sprint. */
    public void setBodyDoubleDestination(Vec3 target) {
        this.walkTarget = target;
    }

    /** Called when Time Skip fires — forces immediate full-speed destination travel. */
    public void setWalkTarget(Vec3 target) {
        this.walkTarget = target;
        this.hasWalkTarget = true;
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AggroMagnetGoal());
        this.goalSelector.addGoal(2, new FleeLethalThreatGoal());
        this.goalSelector.addGoal(3, new SmartEngageGoal());
        this.goalSelector.addGoal(4, new TravelToDestinationGoal());
        this.goalSelector.addGoal(5, new DriftWanderGoal());
    }

    // ── Goal: Aggro Magnet ────────────────────────────────────────────────────

    /**
     * Periodically scans nearby hostile mobs and redirects them to target this entity instead of
     * the owner. Stops once urgency is high enough that dragging new enemies becomes counterproductive.
     */
    private class AggroMagnetGoal extends Goal {
        private int cooldown = 0;

        AggroMagnetGoal() { /* no movement flag — runs alongside other goals */ }

        @Override
        public boolean canUse() {
            return !level().isClientSide() && getUrgency() < URGENCY_REDIRECT_STOP;
        }

        @Override
        public boolean canContinueToUse() {
            return getUrgency() < URGENCY_REDIRECT_STOP;
        }

        @Override
        public void tick() {
            if (--cooldown > 0) return;
            cooldown = 10;

            @Nullable Player owner = ownerUuid != null ? level().getPlayerByUUID(ownerUuid) : null;

            level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(AGGRO_RADIUS),
                    e -> e.isAlive() && e instanceof Enemy
                            && e.getTarget() != TimePhaseBodyDoubleEntity.this
                            && assessThreat(e) < FLEE_THREAT_THRESHOLD) // don't try to bait bosses
                    .forEach(mob -> {
                        // Redirect if targeting the owner, or if within close range and untargeted
                        LivingEntity currentTarget = mob.getTarget();
                        if (currentTarget == owner || currentTarget == null
                                || mob.distanceTo(TimePhaseBodyDoubleEntity.this) < 10f) {
                            mob.setTarget(TimePhaseBodyDoubleEntity.this);
                        }
                    });
        }
    }

    // ── Goal: Flee Lethal Threat ──────────────────────────────────────────────

    /**
     * Sprints away from Dragon, Wither, Warden, or any mob whose threat score is dangerously high.
     * Always overrides other movement goals.
     */
    private class FleeLethalThreatGoal extends Goal {
        @Nullable private LivingEntity threat;
        private double fleeX, fleeY, fleeZ;

        FleeLethalThreatGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (level().isClientSide()) return false;
            threat = findLethalThreat();
            if (threat == null) return false;
            Vec3 away = position().subtract(threat.position());
            double len = away.length();
            Vec3 dir = len > 0 ? away.scale(1.0 / len) : new Vec3(1, 0, 0);
            Vec3 fleePos = position().add(dir.scale(12));
            fleeX = fleePos.x;
            fleeY = fleePos.y;
            fleeZ = fleePos.z;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return findLethalThreat() != null && !getNavigation().isDone();
        }

        @Override
        public void start() {
            getNavigation().moveTo(fleeX, fleeY, fleeZ, 2.0);
        }

        @Override
        public void tick() {
            if (threat == null) return;
            // Recalculate flee direction each tick so it tracks a moving threat
            Vec3 away = position().subtract(threat.position());
            double len = away.length();
            Vec3 dir = len > 0 ? away.scale(1.0 / len) : new Vec3(1, 0, 0);
            Vec3 fleePos = position().add(dir.scale(12));
            getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 2.0);
        }

        @Nullable
        private LivingEntity findLethalThreat() {
            return level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(FLEE_RADIUS),
                    e -> e != TimePhaseBodyDoubleEntity.this && e.isAlive()
                            && assessThreat(e) >= FLEE_THREAT_THRESHOLD)
                    .stream()
                    .min(Comparator.comparingDouble(e -> distanceToSqr(e.position())))
                    .orElse(null);
        }
    }

    // ── Goal: Travel To Destination ───────────────────────────────────────────

    /**
     * Moves toward the destination set by Time Skip ({@link #setWalkTarget}).
     * Dormant at low urgency; activates at {@link #URGENCY_TRAVEL_START} and accelerates
     * toward a sprint at full urgency. Activates immediately at full speed when Time Skip fires.
     */
    private class TravelToDestinationGoal extends Goal {

        TravelToDestinationGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (level().isClientSide() || walkTarget == null) return false;
            return hasWalkTarget || getUrgency() >= URGENCY_TRAVEL_START;
        }

        @Override
        public boolean canContinueToUse() {
            if (walkTarget == null) return false;
            if (!(hasWalkTarget || getUrgency() >= URGENCY_TRAVEL_START)) return false;
            return !getNavigation().isDone();
        }

        @Override
        public void start() {
            navigate();
        }

        @Override
        public void tick() {
            if (getNavigation().isDone()) navigate();
        }

        private void navigate() {
            if (walkTarget == null) return;
            float speed = hasWalkTarget ? 2.5f : (1.0f + getUrgency() * 1.5f);
            getNavigation().moveTo(walkTarget.x, walkTarget.y, walkTarget.z, speed);
        }
    }

    // ── Goal: Smart Engage ────────────────────────────────────────────────────

    /**
     * Selects and attacks targets based on threat scoring. Prefers low-HP mobs and avoids
     * dangerous fights. At high urgency, only takes finisher kills. Uses hysteresis to
     * prevent flip-flopping between equally-scored targets.
     */
    private class SmartEngageGoal extends Goal {
        @Nullable private LivingEntity target;
        private int attackCooldown = 0;
        private float currentTargetScore = Float.MAX_VALUE;

        SmartEngageGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            if (level().isClientSide()) return false;
            // Once destination travel has started, yield MOVE to TravelToDestinationGoal
            if (walkTarget != null && getUrgency() >= URGENCY_TRAVEL_START) return false;
            LivingEntity candidate = selectTarget(null);
            if (candidate == null) return false;
            target = candidate;
            currentTargetScore = combatScore(candidate);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null || !target.isAlive()) return false;
            // Yield to travel once destination is set
            if (walkTarget != null && getUrgency() >= URGENCY_TRAVEL_START) return false;
            if (distanceTo(target) > ENGAGE_RANGE * 1.5f) return false;
            float urgency = getUrgency();
            float myAttack = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
            boolean isFinisher = target.getHealth() <= myAttack * FINISHER_THRESHOLD_MULT;
            if (urgency >= URGENCY_ENGAGE_STOP && !isFinisher) return false;
            if (assessThreat(target) >= FLEE_THREAT_THRESHOLD) return false;
            // Periodically re-evaluate whether a clearly better target exists
            LivingEntity better = selectTarget(target);
            if (better != null && combatScore(better) < currentTargetScore - TARGET_SWITCH_HYSTERESIS) {
                target = better;
                currentTargetScore = combatScore(better);
            }
            return true;
        }

        @Override
        public void stop() {
            target = null;
            currentTargetScore = Float.MAX_VALUE;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (target == null) return;
            getLookControl().setLookAt(target, 30f, 30f);
            double distSq = distanceToSqr(target.position());
            if (distSq > ATTACK_RANGE_SQ) {
                getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
            } else {
                getNavigation().stop();
                if (--attackCooldown <= 0) {
                    attackCooldown = ATTACK_COOLDOWN_TICKS;
                    swing(InteractionHand.MAIN_HAND);
                    target.hurt(damageSources().mobAttack(TimePhaseBodyDoubleEntity.this),
                            (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
                }
            }
        }

        /** Lower score = higher priority. Balances kill time against threat level. */
        private float combatScore(LivingEntity e) {
            float myAttack = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
            float killTime = e.getHealth() / Math.max(0.1f, myAttack);
            float threat = assessThreat(e);
            return killTime + threat * 0.5f;
        }

        @Nullable
        private LivingEntity selectTarget(@Nullable LivingEntity currentTarget) {
            float urgency = getUrgency();
            float myAttack = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);

            return level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(ENGAGE_RANGE), e -> {
                        if (e == TimePhaseBodyDoubleEntity.this || !e.isAlive()
                                || e.isInvulnerable() || e instanceof Player
                                || !(e instanceof Enemy)) return false;
                        float threat = assessThreat(e);
                        if (threat >= FLEE_THREAT_THRESHOLD) return false;
                        boolean isFinisher = e.getHealth() <= myAttack * FINISHER_THRESHOLD_MULT;
                        if (urgency >= URGENCY_ENGAGE_STOP) return isFinisher;
                        return threat < ENGAGE_THREAT_THRESHOLD || isFinisher;
                    })
                    .stream()
                    .min(Comparator.comparingDouble(e -> combatScore((LivingEntity) e)))
                    .orElse(null);
        }
    }

    // ── Goal: Drift Wander ────────────────────────────────────────────────────

    /**
     * Fallback idle movement. Biases toward the destination when one is known;
     * otherwise drifts in the initial facing direction. Adds noise so the motion
     * looks natural rather than mechanical.
     */
    private class DriftWanderGoal extends Goal {
        private int recalcTimer = 0;

        DriftWanderGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() { return !hasWalkTarget && !level().isClientSide(); }

        @Override
        public boolean canContinueToUse() { return !hasWalkTarget; }

        @Override
        public void start() { recalcTarget(); }

        @Override
        public void tick() {
            if (++recalcTimer >= 40 || getNavigation().isDone()) {
                recalcTimer = 0;
                recalcTarget();
            }
        }

        private void recalcTarget() {
            Vec3 dest;
            double spread = 4.0;
            if (walkTarget != null && getUrgency() < URGENCY_TRAVEL_START) {
                // Drift toward destination with noise
                Vec3 toward = walkTarget.subtract(position()).normalize().scale(8);
                dest = position().add(toward)
                        .add((random.nextDouble() - 0.5) * spread, 0,
                             (random.nextDouble() - 0.5) * spread);
            } else {
                // Walk in initial facing direction with noise
                double rad = Math.toRadians(initialYaw);
                double dx = -Math.sin(rad) * 10 + (random.nextDouble() - 0.5) * spread;
                double dz =  Math.cos(rad) * 10 + (random.nextDouble() - 0.5) * spread;
                dest = position().add(dx, 0, dz);
            }
            getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0);
        }
    }

    // ── Threat Assessment ─────────────────────────────────────────────────────

    /**
     * Scores a mob's danger level. Dragon/Wither/Warden return {@link Float#MAX_VALUE}.
     * Score is based on attack damage × speed, with enchantment and ranged bonuses.
     * Higher = more dangerous.
     */
    static float assessThreat(LivingEntity mob) {
        if (mob instanceof EnderDragon || mob instanceof WitherBoss || mob instanceof Warden)
            return Float.MAX_VALUE;

        AttributeInstance attackAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        float attack = attackAttr != null ? (float) attackAttr.getValue() : 0f;
        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        float speed = speedAttr != null ? (float) speedAttr.getValue() : 0.2f;

        // Rough enchantment bonus — each enchantment on the weapon adds ~0.8 effective damage
        ItemStack weapon = mob.getMainHandItem();
        if (!weapon.isEmpty()) {
            ItemEnchantments enchants = weapon.get(DataComponents.ENCHANTMENTS);
            if (enchants != null) attack += enchants.size() * 0.8f;
        }

        // Ranged mobs can hit without closing distance — elevated threat
        boolean ranged = mob instanceof AbstractIllager
                || mob instanceof AbstractSkeleton
                || mob instanceof Blaze
                || mob instanceof Ghast
                || mob instanceof Witch;

        float score = attack * (1f + speed * 2.5f);
        if (ranged) score *= 1.3f;
        return score;
    }

    // ── Equipment / Owner ─────────────────────────────────────────────────────

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
        entityData.set(DATA_OWNER_UUID_STR, uuid.toString());
    }

    @Nullable
    public UUID getOwnerUuid() {
        String s = entityData.get(DATA_OWNER_UUID_STR);
        if (s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    /**
     * Mirrors the owner's current health and armor so the body double's death threshold
     * reflects how much damage would actually kill the owner at activation time.
     */
    public void copyStatsFrom(Player player) {
        float currentHp = player.getHealth();
        AttributeInstance maxHp = getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            double hpDelta = currentHp - maxHp.getBaseValue();
            maxHp.removeModifier(COPY_STATS_HP_ID);
            if (hpDelta != 0) {
                maxHp.addOrUpdateTransientModifier(new AttributeModifier(
                        COPY_STATS_HP_ID, hpDelta, AttributeModifier.Operation.ADD_VALUE));
            }
            setHealth(currentHp);
        }
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            double armorVal = player.getAttributeValue(Attributes.ARMOR);
            armor.removeModifier(COPY_STATS_ARMOR_ID);
            if (armorVal != 0) {
                armor.addOrUpdateTransientModifier(new AttributeModifier(
                        COPY_STATS_ARMOR_ID, armorVal, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        AttributeInstance toughness = getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null) {
            double toughnessVal = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            toughness.removeModifier(COPY_STATS_TOUGHNESS_ID);
            if (toughnessVal != 0) {
                toughness.addOrUpdateTransientModifier(new AttributeModifier(
                        COPY_STATS_TOUGHNESS_ID, toughnessVal, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    public boolean isSlim() { return entityData.get(DATA_IS_SLIM); }

    public void copyEquipmentFrom(Player player) {
        entityData.set(DATA_SLOT_MAINHAND, player.getItemBySlot(EquipmentSlot.MAINHAND).copy());
        entityData.set(DATA_SLOT_OFFHAND,  player.getItemBySlot(EquipmentSlot.OFFHAND).copy());
        entityData.set(DATA_SLOT_FEET,     player.getItemBySlot(EquipmentSlot.FEET).copy());
        entityData.set(DATA_SLOT_LEGS,     player.getItemBySlot(EquipmentSlot.LEGS).copy());
        entityData.set(DATA_SLOT_CHEST,    player.getItemBySlot(EquipmentSlot.CHEST).copy());
        entityData.set(DATA_SLOT_HEAD,     player.getItemBySlot(EquipmentSlot.HEAD).copy());
        entityData.set(DATA_IS_SLIM, detectSlimModel(player));
    }

    /**
     * Detects whether the player uses the slim (Alex) skin model.
     * Checks the GameProfile textures property first; falls back to the UUID-based
     * Minecraft default-skin rule.
     */
    public static boolean detectSlimModel(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            var textures = sp.getGameProfile().getProperties().get("textures");
            if (!textures.isEmpty()) {
                try {
                    String decoded = new String(
                        java.util.Base64.getDecoder().decode(textures.iterator().next().value()));
                    if (decoded.contains("\"slim\"")) return true;
                } catch (Exception ignored) {}
            }
        }
        // Default skin: Minecraft uses UUID hashCode parity for Alex vs Steve
        return (player.getUUID().hashCode() & 1) != 0;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> entityData.get(DATA_SLOT_MAINHAND);
            case OFFHAND  -> entityData.get(DATA_SLOT_OFFHAND);
            case FEET     -> entityData.get(DATA_SLOT_FEET);
            case LEGS     -> entityData.get(DATA_SLOT_LEGS);
            case CHEST    -> entityData.get(DATA_SLOT_CHEST);
            case HEAD     -> entityData.get(DATA_SLOT_HEAD);
            default -> ItemStack.EMPTY;
        };
    }

    // ── Misc Overrides ────────────────────────────────────────────────────────

    public float getBankedDamage() { return bankedDamage; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && amount > 0 && hurtTime <= 0) {
            hurtTime = hurtDuration; // prevent rapid-fire hits before invulnerability window
            bankedDamage += amount;
            // Simulate knockback — find the hit direction from the attacker
            net.minecraft.world.entity.Entity attacker = source.getDirectEntity();
            if (attacker == null) attacker = source.getEntity();
            if (attacker != null) {
                knockback(0.4, attacker.getX() - getX(), attacker.getZ() - getZ());
            }
        }
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    // ── Synced Data ───────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_UUID_STR, "");
        builder.define(DATA_SLOT_MAINHAND, ItemStack.EMPTY);
        builder.define(DATA_SLOT_OFFHAND,  ItemStack.EMPTY);
        builder.define(DATA_SLOT_FEET,     ItemStack.EMPTY);
        builder.define(DATA_SLOT_LEGS,     ItemStack.EMPTY);
        builder.define(DATA_SLOT_CHEST,    ItemStack.EMPTY);
        builder.define(DATA_SLOT_HEAD,     ItemStack.EMPTY);
        builder.define(DATA_IS_SLIM,       false);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("OwnerUUID", ownerUuid);
        tag.putFloat("InitialYaw", initialYaw);
        tag.putInt("TicksAlive", ticksAlive);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            ownerUuid = tag.getUUID("OwnerUUID");
            entityData.set(DATA_OWNER_UUID_STR, ownerUuid.toString());
        }
        if (tag.contains("InitialYaw")) initialYaw = tag.getFloat("InitialYaw");
        if (tag.contains("TicksAlive")) ticksAlive = tag.getInt("TicksAlive");
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Removes all Time Phase body doubles owned by the given UUID from this level. */
    public static void dismissExistingDoubles(Level level, UUID ownerUuid) {
        if (level.isClientSide) return;
        ((net.minecraft.server.level.ServerLevel) level).getEntities().getAll().forEach(e -> {
            if (e instanceof TimePhaseBodyDoubleEntity tpbd && ownerUuid.equals(tpbd.getOwnerUuid()))
                tpbd.discard();
        });
    }
}
