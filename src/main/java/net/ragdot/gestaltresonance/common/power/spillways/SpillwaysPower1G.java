package net.ragdot.gestaltresonance.common.power.spillways;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Spillways — Dominion (Power 1G).
 * Z+Right-click: freeze a hostile mob in place inside a bubble until re-targeted or re-activated.
 * Cap: 1 target (levels 3–9), 2 targets (level 10+).
 */
public final class SpillwaysPower1G {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.SPILLWAYS, GestaltPowerSlot.POWER_1, GestaltPowerModifier.GUARD);

    private record DominionTarget(LivingEntity entity, Vec3 frozenPos, boolean wasNoGravity, boolean wasNoPhysics) {}

    /** Server-side map of player UUID → their currently dominated targets. */
    private static final Map<UUID, List<DominionTarget>> ACTIVE = new HashMap<>();

    /** Server-side set of dominated entity network IDs — used for damage immunity. */
    private static final Set<Integer> DOMINATED_ENTITY_IDS = new HashSet<>();

    public static void register() {
        GestaltPowerRegistry.register(KEY, SpillwaysPower1G::activate);
        NeoForge.EVENT_BUS.addListener(SpillwaysPower1G::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SpillwaysPower1G::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(SpillwaysPower1G::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(SpillwaysPower1G::onProjectileImpact);
    }

    /** Cancels all damage to dominated mobs; arrows hitting the bubble break Dominion instead. */
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (DOMINATED_ENTITY_IDS.contains(event.getEntity().getId())) {
            event.setCanceled(true);
            if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
                releaseEntityById(event.getEntity().getId());
            }
            return;
        }
        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity != null && DOMINATED_ENTITY_IDS.contains(sourceEntity.getId())) {
            event.setCanceled(true);
        }
    }

    /** If a dominated mob fires an arrow, Dominion breaks; fireballs are absorbed silently. */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();

        if (entity instanceof AbstractArrow arrow) {
            Entity owner = arrow.getOwner();
            if (owner != null && DOMINATED_ENTITY_IDS.contains(owner.getId())) {
                releaseEntityById(owner.getId());
            }
            return;
        }

        if (entity instanceof Fireball fireball) {
            Entity owner = fireball.getOwner();
            if (owner != null && DOMINATED_ENTITY_IDS.contains(owner.getId())) {
                event.setCanceled(true);
                owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.0f, 1.2f);
            }
        }
    }

    /**
     * Cancels fireballs that hit the bubble before their onHit runs (prevents explosion creation).
     * Discards the fireball and plays a fire extinguish sound.
     */
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Fireball fireball)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult ehr)) return;
        if (!DOMINATED_ENTITY_IDS.contains(ehr.getEntity().getId())) return;

        event.setCanceled(true);
        fireball.discard();
        ehr.getEntity().level().playSound(null, fireball.getX(), fireball.getY(), fireball.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.0f, 1.2f);
    }

    /** Neutralises explosions triggered by dominated Creepers — no block or entity damage. */
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getIndirectSourceEntity();
        if (source != null && DOMINATED_ENTITY_IDS.contains(source.getId())) {
            event.getAffectedBlocks().clear();
            event.getAffectedEntities().clear();
        }
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned() || !state.isAwakened()) { playFail(player); return; }
        if (!GestaltIds.SPILLWAYS.equals(state.getGestaltId())) { playFail(player); return; }
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[0][2]) { playFail(player); return; }
        if (player.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) { playFail(player); return; }

        GestaltAction action = state.getAction();
        if (action != GestaltAction.IDLE && action != GestaltAction.GUARD) { playFail(player); return; }

        long currentTick = player.getServer().getTickCount();
        if (state.hasPowerCooldown(KEY.slot(), KEY.modifier(), currentTick)) { playFail(player); return; }

        if (state.getTotalGestaltXp() < GestaltCosts.DOMINION_XP_COST) { playFail(player); return; }

        ArrayList<CompoundTag> storedMobs = player.getData(GestaltAttachments.DOMINION_STORED_MOBS.get());

        // A passive mob is stored — entity targeting is blocked; block-surface targets release it
        if (!storedMobs.isEmpty()) {
            HitResult hit = player.pick(GestaltCosts.DOMINION_RANGE, 0.0f, false);
            if (!(hit instanceof BlockHitResult blockHit)) { playFail(player); return; }
            releaseStoredMob(player, storedMobs, blockHit);
            applyCost(player, state, currentTick);
            return;
        }

        LivingEntity target = findTarget(player);
        if (target == null) { playFail(player); return; }

        // Passive mob / pet branch — store and remove from world (cap always 1)
        if (!(target instanceof Enemy)) {
            CompoundTag nbt = new CompoundTag();
            target.save(nbt);
            storedMobs.add(nbt);
            player.setData(GestaltAttachments.DOMINION_STORED_MOBS.get(), storedMobs);
            target.discard();
            GestaltNetworking.syncStoredMobToPlayer(player, nbt);
            applyCost(player, state, currentTick);
            return;
        }

        List<DominionTarget> active = ACTIVE.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());

        // Re-targeting the same already-dominated mob → release (toggle off)
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).entity() == target) {
                release(active.remove(i));
                return;
            }
        }

        // New target: enforce hostile-only cap
        int cap = dominionCap(state.getGestaltLevel());
        while (active.size() >= cap) {
            release(active.remove(0));
        }

        // Freeze the new target, disable collision, and float it up 0.4 blocks
        boolean wasNoGravity = target.isNoGravity();
        boolean wasNoPhysics = target.noPhysics;
        target.setNoGravity(true);
        target.noPhysics = true;
        target.setDeltaMovement(Vec3.ZERO);
        Vec3 floatPos = target.position().add(0.0, 0.4, 0.0);
        target.setPos(floatPos.x, floatPos.y, floatPos.z);
        active.add(new DominionTarget(target, floatPos, wasNoGravity, wasNoPhysics));
        DOMINATED_ENTITY_IDS.add(target.getId());

        GestaltNetworking.syncDominionState(target, true);
        applyCost(player, state, currentTick);
    }

    private static void applyCost(ServerPlayer player, PlayerGestaltState state, long currentTick) {
        state.spendGestaltXp(GestaltCosts.DOMINION_XP_COST);
        state.setPowerCooldown(KEY.slot(), KEY.modifier(), currentTick + GestaltCosts.DOMINION_COOLDOWN_TICKS);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        player.causeFoodExhaustion(0.5f);

        GestaltNetworking.syncGestaltXpToPlayer(player);
        GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.DOMINION_COOLDOWN_TICKS);
        GestaltNetworking.syncPowerCooldown(player,
                KEY.slot().ordinal() * 3 + KEY.modifier().ordinal(),
                GestaltCosts.DOMINION_COOLDOWN_TICKS);
    }

    // ── Per-tick maintenance (called from GestaltResonance.onServerTick) ─────

    public static void tick(ServerPlayer player) {
        List<DominionTarget> active = ACTIVE.get(player.getUUID());
        if (active == null || active.isEmpty()) return;

        active.removeIf(dt -> {
            LivingEntity entity = dt.entity();
            if (!entity.isAlive() || entity.isRemoved()) {
                // Auto-release: restore gravity, collision, notify clients
                DOMINATED_ENTITY_IDS.remove(entity.getId());
                entity.setNoGravity(dt.wasNoGravity());
                entity.noPhysics = dt.wasNoPhysics();
                GestaltNetworking.syncDominionState(entity, false);
                return true;
            }
            // Maintain freeze with gentle vertical bobble (±0.1 blocks, ~5 second period)
            entity.setDeltaMovement(Vec3.ZERO);
            double bobble = 0.1 * Math.sin(player.serverLevel().getGameTime() * 0.06);
            entity.setPos(dt.frozenPos().x, dt.frozenPos().y + bobble, dt.frozenPos().z);
            // Bubble protects from fire
            if (entity.isOnFire()) entity.clearFire();
            return false;
        });
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /** Release all dominated targets for this player (on death / logout). */
    public static void disarm(ServerPlayer player) {
        List<DominionTarget> active = ACTIVE.remove(player.getUUID());
        if (active == null) return;
        for (DominionTarget dt : active) {
            if (dt.entity().isAlive() && !dt.entity().isRemoved()) {
                release(dt);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Spawns the most recently stored passive mob at a safe position adjacent to the hit block face.
     * Walks upward up to 8 blocks to avoid spawning inside solid blocks (standard 2-block mob height).
     */
    private static void releaseStoredMob(ServerPlayer player, ArrayList<CompoundTag> storedMobs, BlockHitResult blockHit) {
        CompoundTag nbt = storedMobs.remove(storedMobs.size() - 1);
        player.setData(GestaltAttachments.DOMINION_STORED_MOBS.get(), storedMobs);
        GestaltNetworking.syncStoredMobToPlayer(player, new CompoundTag());

        ServerLevel level = player.serverLevel();
        BlockPos spawnPos = blockHit.getBlockPos().relative(blockHit.getDirection());
        for (int i = 0; i < 8; i++) {
            if (level.getBlockState(spawnPos).getCollisionShape(level, spawnPos).isEmpty()
                    && level.getBlockState(spawnPos.above()).getCollisionShape(level, spawnPos.above()).isEmpty()) break;
            spawnPos = spawnPos.above();
        }

        final double rx = spawnPos.getX() + 0.5;
        final double ry = spawnPos.getY();
        final double rz = spawnPos.getZ() + 0.5;

        Entity mob = EntityType.loadEntityRecursive(nbt, level, e -> { e.setPos(rx, ry, rz); return e; });
        if (mob != null) {
            level.addFreshEntityWithPassengers(mob);
        }
    }

    /** Searches all active lists and releases the dominated entity with the given network ID. */
    private static void releaseEntityById(int entityId) {
        for (List<DominionTarget> active : ACTIVE.values()) {
            for (int i = 0; i < active.size(); i++) {
                if (active.get(i).entity().getId() == entityId) {
                    release(active.remove(i));
                    return;
                }
            }
        }
    }

    /** Release a single dominated target: restore gravity, collision, and notify clients. */
    private static void release(DominionTarget dt) {
        LivingEntity entity = dt.entity();
        DOMINATED_ENTITY_IDS.remove(entity.getId());
        entity.setNoGravity(dt.wasNoGravity());
        entity.noPhysics = dt.wasNoPhysics();
        GestaltNetworking.syncDominionState(entity, false);
    }

    /** How many simultaneous targets this player can dominate. */
    private static int dominionCap(int level) {
        return level >= GestaltCosts.DOMINION_CAP_LEVEL ? 2 : 1;
    }

    /**
     * Finds the closest Enemy LivingEntity within {@link GestaltCosts#DOMINION_RANGE} along
     * the player's look vector via AABB-ray intersection.
     */
    private static LivingEntity findTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = GestaltCosts.DOMINION_RANGE;
        Vec3 end = eye.add(look.scale(range));

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        List<LivingEntity> candidates = player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> !(e instanceof Player) && e != player && e.isAlive());

        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity entity : candidates) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double distSq = eye.distanceToSqr(hit.get());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = entity;
                }
            }
        }
        return best;
    }

    private SpillwaysPower1G() {}
}
