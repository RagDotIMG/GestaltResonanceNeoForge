package net.ragdot.gestaltresonance.common.power.amen_break;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.GhostPlayerHandler;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.ai.WalkRecordedPathGoal;
import net.ragdot.gestaltresonance.common.entity.TimePhaseBodyDoubleEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

import java.util.List;
import java.util.Optional;

/**
 * Amen Break — Time Phase (Power 3S).
 *
 * Catch-up phase (ticks 0–80): player slowed 90%, fading client-only afterimages track entity positions.
 * Prediction phase (ticks 80–200): player speed restored, destinations computed, persistent destination
 * afterimages spawn + intermediate fading afterimages every 20 ticks. Body double walks toward the
 * player's look-ray block destination, arriving at tick 200, at which point the player teleports there.
 *
 * C or Shift+C during prediction phase triggers Time Skip — entities teleport to destinations,
 * banked damage releases as explosions.
 *
 * Guard+C during Time Phase tears Time Phase down silently (no damage release, no teleport),
 * teleports tracked entities to tick-0 positions, walks hostile mobs through their recorded path,
 * and starts Phase Court without spawning a new Phase Court body double.
 */
public final class AmenBreakPower3S {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.AMEN_BREAK, GestaltPowerSlot.POWER_3, GestaltPowerModifier.SNEAK);

    public static final AmenBreakPower3S EVENT_LISTENER = new AmenBreakPower3S();

    private static final ResourceLocation TIME_PHASE_PLAYER_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "time_phase_player_slow");
    private static final ResourceLocation TIME_PHASE_ENTITY_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "time_phase_entity_slow");

    private static final java.util.Random random = new java.util.Random();

    // Afterimage tint colors (packed RGB)
    static final int TINT_VIOLET = (0x61 << 16) | (0x1A << 8) | 0x85;  // normal path
    static final int TINT_BLUE   = (0x10 << 16) | (0x40 << 8) | 0xCC;  // start of prediction
    static final int TINT_GREEN  = (0x10 << 16) | (0xCC << 8) | 0x40;  // entity destinations
    static final int TINT_RED    = (0xFF << 16) | (0x20 << 8) | 0x20;  // body double death

    private AmenBreakPower3S() {}

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        GestaltPowerRegistry.register(KEY, AmenBreakPower3S::activate);
    }

    // ── Activation ────────────────────────────────────────────────────────────

    public static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned()) return;
        if (!player.isCreative() && !state.isAwakened()) return;
        if (!player.isCreative() && state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[2][1]) return;
        if (state.isTimePhaseActive()) { activateTimeSkip(player); return; }
        if (!player.isCreative() && state.hasTimePhaseCooldown()) { playFail(player); return; }

        net.minecraft.world.item.ItemStack heldItem = player.getInventory().getSelected();
        boolean hasCatalyst = !player.isCreative() && !heldItem.isEmpty()
                && GestaltCosts.POWER_3_CATALYSTS.contains(heldItem.getItem());

        if (!player.isCreative() && !hasCatalyst) {
            if (state.getResonanceValue() < GestaltCosts.TIME_PHASE_RESONANCE_COST) {
                playFail(player);
                return;
            }
            state.setResonanceValue(state.getResonanceValue() - GestaltCosts.TIME_PHASE_RESONANCE_COST);
        }
        if (hasCatalyst) heldItem.shrink(1);

        state.setTimePhaseCooldownTicks(GestaltCosts.TIME_PHASE_COOLDOWN_TICKS);

        // Spawn body double
        TimePhaseBodyDoubleEntity bodyDouble = new TimePhaseBodyDoubleEntity(
                GestaltEntities.TIME_PHASE_BODY_DOUBLE.get(), player.level());
        bodyDouble.setPos(player.getX(), player.getY(), player.getZ());
        bodyDouble.setOwnerUuid(player.getUUID());
        bodyDouble.copyEquipmentFrom(player);
        bodyDouble.copyStatsFrom(player);
        bodyDouble.setInitialYaw(player.getYRot());
        player.level().addFreshEntity(bodyDouble);
        state.setTimePhaseBodyDoubleId(bodyDouble.getId());

        // Scan for nearby entities to track (excludes Time Phase body double via isInvulnerable? no — explicit)
        AABB scanBox = player.getBoundingBox().inflate(GestaltCosts.TIME_PHASE_SCAN_RADIUS);
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(
                LivingEntity.class, scanBox,
                e -> e != player && e != bodyDouble && e.isAlive() && !e.isInvulnerable());

        int count = Math.min(nearby.size(), GestaltCosts.TIME_PHASE_MAX_ENTITIES);
        int[] trackedIds = state.getTimePhaseTrackedIds();
        Vec3[][] snapshots = state.getTimePhaseSnapshots();

        for (int i = 0; i < count; i++) {
            LivingEntity entity = nearby.get(i);
            trackedIds[i] = entity.getId();
            snapshots[i][0] = entity.position();
            AttributeInstance attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(TIME_PHASE_ENTITY_SLOW_ID);
                attr.addOrUpdateTransientModifier(new AttributeModifier(
                        TIME_PHASE_ENTITY_SLOW_ID, GestaltCosts.TIME_PHASE_ENTITY_SLOW,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        state.setTimePhaseTrackedCount(count);

        state.setTimePhaseActive(true);
        state.setTimePhasePredictionPhase(false);
        state.setTimePhaseTicksRemaining(GestaltCosts.TIME_PHASE_GHOST_TICKS);
        state.setTimePhaseRecordTick(0);
        state.setTimePhaseBodyDoubleDestination(null);

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GhostPlayerHandler.setGhostState(player, true);
        applyPlayerSlowModifier(player);
        GestaltNetworking.syncTimePhaseToPlayer(player);
        GestaltNetworking.syncResonanceToPlayer(player);

        player.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 1.0f, 0.7f);
        GestaltResonance.LOGGER.debug("AmenBreak Time Phase activated for {}", player.getName().getString());
    }

    // ── Per-tick logic ────────────────────────────────────────────────────────

    public static void tick(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean changed = false;

        if (state.getTimePhaseCooldownTicks() > 0) {
            state.setTimePhaseCooldownTicks(state.getTimePhaseCooldownTicks() - 1);
            changed = true;
        }

        if (!state.isTimePhaseActive()) {
            if (changed) player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            return;
        }

        // Body double death: banked damage exceeds the double's effective HP.
        // Despawn the entity and leave a red afterimage — window continues.
        if (state.getTimePhaseBodyDoubleId() >= 0) {
            Entity bdCheck = player.level().getEntity(state.getTimePhaseBodyDoubleId());
            if (bdCheck instanceof TimePhaseBodyDoubleEntity tpbd
                    && tpbd.getBankedDamage() >= tpbd.getMaxHealth()) {
                handleBodyDoubleDeath(player, state, tpbd);
                state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            }
        }

        int recordTick = state.getTimePhaseRecordTick() + 1;
        state.setTimePhaseRecordTick(recordTick);

        int count = state.getTimePhaseTrackedCount();
        int[] trackedIds = state.getTimePhaseTrackedIds();
        Vec3[][] snapshots = state.getTimePhaseSnapshots();
        Vec3[] destinations = state.getTimePhaseDestinations();

        if (recordTick % GestaltCosts.TIME_PHASE_SNAPSHOT_INTERVAL == 0
                && player.level() instanceof ServerLevel sl) {
            int snapshotSlot = recordTick / GestaltCosts.TIME_PHASE_SNAPSHOT_INTERVAL;

            if (snapshotSlot <= 4) {
                // ── Catch-up phase: fading afterimage at entity's current position ────────
                for (int i = 0; i < count; i++) {
                    Entity entity = sl.getEntity(trackedIds[i]);
                    if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                    snapshots[i][snapshotSlot] = living.position();
                    // Skip fading at slot 4 — transitionToPredictionPhase spawns a blue persistent there
                    if (snapshotSlot < 4) {
                        spawnFadingAfterimage(player, living.getX(), living.getY(), living.getZ(), living.getId());
                    }
                }

                if (recordTick == GestaltCosts.TIME_PHASE_OBSERVATION_TICKS) {
                    transitionToPredictionPhase(player, state, sl);
                    return;
                }

            } else if (snapshotSlot <= 8) {
                // ── Prediction phase: fading afterimage 20 ticks ahead on predicted path ──
                // progress = (recordTick + 20 - 80) / 120 = (recordTick - 60) / 120
                double progress = Math.max(0.0, Math.min(1.0, (recordTick - 60.0) / 120.0));
                for (int i = 0; i < count; i++) {
                    Entity entity = sl.getEntity(trackedIds[i]);
                    if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                    Vec3 dest = destinations[i];
                    Vec3 startPos = snapshots[i][4];
                    if (dest == null || startPos == null) continue;
                    Vec3 predictedPos = startPos.add(dest.subtract(startPos).scale(progress));
                    spawnFadingAfterimage(player, predictedPos.x, predictedPos.y, predictedPos.z, living.getId());
                }
            }
        }

        int remaining = state.getTimePhaseTicksRemaining() - 1;
        if (remaining <= 0) {
            endTimePhaseWindow(player, state, false);
            return;
        }
        state.setTimePhaseTicksRemaining(remaining);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        if (recordTick % 20 == 0) {
            GestaltNetworking.syncTimePhaseToPlayer(player);
        }
    }

    private static void transitionToPredictionPhase(ServerPlayer player, PlayerGestaltState state, ServerLevel sl) {
        state.setTimePhasePredictionPhase(true);
        removePlayerSlowModifier(player);

        int count = state.getTimePhaseTrackedCount();
        int[] trackedIds = state.getTimePhaseTrackedIds();
        Vec3[] destinations = state.getTimePhaseDestinations();
        int ticksRemaining = state.getTimePhaseTicksRemaining();
        Vec3[][] snapshots = state.getTimePhaseSnapshots();

        // ── Step 1: body double destination (player future position) computed first
        //    so mobs chasing the player can aim for it.
        Vec3 bdDest = null;
        Entity bdEntity = sl.getEntity(state.getTimePhaseBodyDoubleId());
        if (bdEntity instanceof TimePhaseBodyDoubleEntity bd && bd.isAlive()) {
            bdDest = computeBodyDoubleDestination(player);
            state.setTimePhaseBodyDoubleDestination(bdDest);
            bd.setBodyDoubleDestination(bdDest);
            int bdAfterimageId = GestaltNetworking.nextAfterimageId();
            GestaltNetworking.sendSpawnAfterimage(player, bdAfterimageId,
                    bdDest.x, bdDest.y, bdDest.z, bd.getId(), 0.5f, 0f, TINT_GREEN);
            state.setTimePhaseBodyDoubleAfterimageId(bdAfterimageId);
        }

        // ── Step 2: first pass — compute destinations using current logic
        for (int i = 0; i < count; i++) {
            Entity entity = sl.getEntity(trackedIds[i]);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            destinations[i] = computeEntityDestination(living, ticksRemaining);
        }

        // ── Step 3: second pass — redirect chasers to their target's future destination
        //    If mob A is chasing the player, aim it at the player's future position (bdDest).
        //    If mob A is chasing tracked mob B, aim it at B's already-computed destination.
        for (int i = 0; i < count; i++) {
            Entity entity = sl.getEntity(trackedIds[i]);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            LivingEntity attackTarget = mob.getTarget();
            if (attackTarget == null || !attackTarget.isAlive()) continue;

            Vec3 targetFuture = null;
            if (attackTarget.getUUID().equals(player.getUUID())) {
                targetFuture = bdDest;
            } else {
                for (int j = 0; j < count; j++) {
                    if (trackedIds[j] == attackTarget.getId()) {
                        targetFuture = destinations[j];
                        break;
                    }
                }
            }
            if (targetFuture == null) continue;

            Vec3 spread = targetFuture.add(
                    (random.nextDouble() * 2 - 1) * GestaltCosts.TIME_PHASE_DESTINATION_RADIUS,
                    0,
                    (random.nextDouble() * 2 - 1) * GestaltCosts.TIME_PHASE_DESTINATION_RADIUS);
            Vec3 grounded = findValidLandingPos(sl, spread.x, spread.z,
                    spread.y, GestaltCosts.TIME_PHASE_BODY_DOUBLE_DROP_SCAN);
            destinations[i] = grounded != null ? grounded : mob.position();
        }

        // ── Step 4: spawn afterimages now that final destinations are set
        for (int i = 0; i < count; i++) {
            Entity entity = sl.getEntity(trackedIds[i]);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            Vec3 dest = destinations[i];
            Vec3 startPos = snapshots[i][4];
            if (startPos != null) {
                spawnPersistentAfterimage(player, startPos.x, startPos.y, startPos.z, living.getId(), TINT_BLUE);
            }
            if (dest != null) {
                spawnPersistentAfterimage(player, dest.x, dest.y, dest.z, living.getId(), TINT_GREEN);
            }
        }

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncTimePhaseToPlayer(player);
        player.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 0.8f, 1.5f);
        GestaltResonance.LOGGER.debug("AmenBreak Time Phase prediction phase started for {}",
                player.getName().getString());
    }

    // ── Time Skip ─────────────────────────────────────────────────────────────

    public static void activateTimeSkip(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isTimePhaseActive()) return;
        if (!state.isTimePhasePredictionPhase()) { playFail(player); return; }
        endTimePhaseWindow(player, state, true);
        player.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    // ── End window (natural expiration or Time Skip) ─────────────────────────

    static void endTimePhaseWindow(ServerPlayer player, PlayerGestaltState state, boolean applyTeleport) {
        // Release banked damage (always). Teleport to destinations only on Time Skip.
        int count = state.getTimePhaseTrackedCount();
        int[] trackedIds = state.getTimePhaseTrackedIds();
        Vec3[] destinations = state.getTimePhaseDestinations();
        float[] banked = state.getTimePhaseBankedDamage();

        for (int i = 0; i < count; i++) {
            Entity entity = player.level().getEntity(trackedIds[i]);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            if (applyTeleport) {
                Vec3 dest = destinations[i];
                if (dest != null) {
                    living.teleportTo(dest.x, dest.y, dest.z);
                    if (player.level() instanceof ServerLevel) {
                        spawnFadingAfterimage(player, dest.x, dest.y, dest.z, living.getId());
                    }
                }
            }

            if (banked[i] > 0) {
                Vec3 midpoint = living.position().add(0, living.getBbHeight() * 0.5, 0);
                int level = state.getGestaltLevel();
                float radius = GestaltExplosionUtil.scaledRadius(
                        GestaltCosts.TIME_PHASE_EXPLOSION_BASE_RADIUS, level);
                float dmg = GestaltExplosionUtil.scaledDamage(
                        GestaltCosts.TIME_PHASE_EXPLOSION_BASE_DAMAGE, level)
                        + banked[i];
                GestaltExplosionUtil.detonate(
                        player.level(), midpoint, radius, dmg,
                        GestaltDamageTypes.gestalt(player.level(), player), null);
            }

            AttributeInstance attr = living.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) attr.removeModifier(TIME_PHASE_ENTITY_SLOW_ID);
        }

        // Discard body double and teleport player to its destination.
        Vec3 bdDest = state.getTimePhaseBodyDoubleDestination();
        float bankedFromDouble = state.getTimePhaseBodyDoubleBankedDamage(); // persisted if entity died early
        if (state.getTimePhaseBodyDoubleId() >= 0) {
            Entity bd = player.level().getEntity(state.getTimePhaseBodyDoubleId());
            if (bd instanceof TimePhaseBodyDoubleEntity tpbd) {
                bankedFromDouble += tpbd.getBankedDamage();
            }
            if (bd != null) bd.discard();
            state.setTimePhaseBodyDoubleId(-1);
        }
        if (bdDest != null) {
            player.teleportTo(bdDest.x, bdDest.y, bdDest.z);
        }

        cleanupTimePhase(player, state);
        player.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 0.8f, 1.2f);

        // Echo 80% of damage the body double absorbed back to the player
        if (bankedFromDouble > 0) {
            player.hurt(player.damageSources().magic(), bankedFromDouble * 0.8f);
        }
    }

    // ── Body double death (banked damage overflow) ────────────────────────────

    private static void handleBodyDoubleDeath(ServerPlayer player, PlayerGestaltState state,
                                              TimePhaseBodyDoubleEntity bd) {
        Vec3 deathPos = bd.position();

        // Persist banked damage so endTimePhaseWindow() can echo it back
        state.setTimePhaseBodyDoubleBankedDamage(bd.getBankedDamage());

        // Discard the green destination afterimage (no longer valid)
        int destAfterimageId = state.getTimePhaseBodyDoubleAfterimageId();
        if (destAfterimageId >= 0) {
            GestaltNetworking.sendDiscardAfterimage(player, destAfterimageId);
            state.setTimePhaseBodyDoubleAfterimageId(-1);
        }

        // Spawn bright red persistent afterimage. Send the packet before discard so the client
        // can snapshot the body double's renderer/texture while it's still in the world.
        int deathAfterimageId = GestaltNetworking.nextAfterimageId();
        GestaltNetworking.sendSpawnAfterimage(player, deathAfterimageId,
                deathPos.x, deathPos.y, deathPos.z, bd.getId(), 1.0f, 0f, TINT_RED);

        bd.discard();
        player.level().playSound(null, deathPos.x, deathPos.y, deathPos.z,
                GestaltSounds.GESTALT_DISSOLVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        // Player will teleport to the death position when the window ends
        state.setTimePhaseBodyDoubleDestination(deathPos);

        state.setTimePhaseBodyDoubleId(-1);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncTimePhaseToPlayer(player);
    }

    // ── Phase Court takeover ─────────────────────────────────────────────────

    /**
     * Called by AmenBreakPower3G when Phase Court activates during an active Time Phase.
     * Discards the body double (no player teleport, no banked-damage release), teleports tracked
     * entities back to tick-0 positions, gives hostile mobs a goal that walks them through their
     * recorded catch-up path. The caller (3G) is then responsible for re-applying ghost state.
     */
    public static void tearDownForPhaseCourt(ServerPlayer player, PlayerGestaltState state) {
        int count = state.getTimePhaseTrackedCount();
        int[] trackedIds = state.getTimePhaseTrackedIds();
        Vec3[][] snapshots = state.getTimePhaseSnapshots();

        for (int i = 0; i < count; i++) {
            Entity entity = player.level().getEntity(trackedIds[i]);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            Vec3 origin = snapshots[i][0];
            if (origin != null) {
                living.teleportTo(origin.x, origin.y, origin.z);
            }
            AttributeInstance attr = living.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) attr.removeModifier(TIME_PHASE_ENTITY_SLOW_ID);

            if (living instanceof Mob mob && living instanceof Enemy) {
                Vec3[] path = new Vec3[] {
                        snapshots[i][1], snapshots[i][2], snapshots[i][3], snapshots[i][4]
                };
                mob.goalSelector.addGoal(0, new WalkRecordedPathGoal(mob, path));
            }
        }

        if (state.getTimePhaseBodyDoubleId() >= 0) {
            Entity bd = player.level().getEntity(state.getTimePhaseBodyDoubleId());
            if (bd != null) bd.discard();
            state.setTimePhaseBodyDoubleId(-1);
        }

        cleanupTimePhase(player, state);
    }

    // ── Disarm (death / logout / respawn) ────────────────────────────────────

    public static void disarm(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isTimePhaseActive() && state.getTimePhaseCooldownTicks() == 0) return;

        if (state.isTimePhaseActive()) {
            if (state.getTimePhaseBodyDoubleId() >= 0) {
                Entity bd = player.level().getEntity(state.getTimePhaseBodyDoubleId());
                if (bd != null) bd.discard();
            }
            int count = state.getTimePhaseTrackedCount();
            int[] trackedIds = state.getTimePhaseTrackedIds();
            for (int i = 0; i < count; i++) {
                Entity entity = player.level().getEntity(trackedIds[i]);
                if (entity instanceof LivingEntity living) {
                    AttributeInstance attr = living.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (attr != null) attr.removeModifier(TIME_PHASE_ENTITY_SLOW_ID);
                }
            }
        }

        cleanupTimePhase(player, state);
    }

    /**
     * Shared teardown: clears ghost state, player slow, all client-side afterimages, state flags,
     * and syncs the new state to the owning client.
     */
    private static void cleanupTimePhase(ServerPlayer player, PlayerGestaltState state) {
        GhostPlayerHandler.setGhostState(player, false);
        removePlayerSlowModifier(player);
        GestaltNetworking.sendClearAfterimages(player);

        state.setTimePhaseActive(false);
        state.setTimePhaseTicksRemaining(0);
        state.clearTimePhaseState();
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncTimePhaseToPlayer(player);
    }

    // ── Damage banking ────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (target.level().getServer() == null) return;

        for (ServerPlayer player : target.level().getServer().getPlayerList().getPlayers()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isTimePhaseActive()) continue;

            int[] ids = state.getTimePhaseTrackedIds();
            int count = state.getTimePhaseTrackedCount();
            for (int i = 0; i < count; i++) {
                if (ids[i] == target.getId()) {
                    state.addTimePhaseBankedDamage(i, event.getAmount());
                    player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Ray-cast from the player's eye to find the body double destination.
     * Uses findValidLandingPos to ensure the result is on top of a solid block with 2 free spaces above.
     * Falls back to the ray's end point if no valid ground is found.
     */
    private static Vec3 computeBodyDoubleDestination(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(GestaltCosts.TIME_PHASE_BODY_DOUBLE_MAX_RANGE));

        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        double x, z, startY;
        if (hit.getType() == HitResult.Type.BLOCK) {
            x = hit.getLocation().x;
            z = hit.getLocation().z;
            startY = hit.getBlockPos().getY() + 1.0;
        } else {
            x = end.x;
            z = end.z;
            startY = end.y;
        }

        Vec3 valid = findValidLandingPos(level, x, z, startY, GestaltCosts.TIME_PHASE_BODY_DOUBLE_DROP_SCAN);
        return valid != null ? valid : new Vec3(x, startY, z);
    }

    /** Compute a destination for a tracked entity: nav path → walk target memory → extrapolation, + random XZ spread. */
    private static Vec3 computeEntityDestination(LivingEntity living, int ticksRemaining) {
        Vec3 destination = null;

        if (living instanceof Mob mob) {
            if (destination == null) {
                var path = mob.getNavigation().getPath();
                if (path != null && !path.isDone()) {
                    var target = path.getTarget();
                    destination = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
                }
            }
            if (destination == null && mob.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                Optional<WalkTarget> walkOpt = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
                if (walkOpt.isPresent()) {
                    var walkPos = walkOpt.get().getTarget().currentBlockPosition();
                    destination = new Vec3(walkPos.getX() + 0.5, walkPos.getY(), walkPos.getZ() + 0.5);
                }
            }
        }

        if (destination == null) {
            Vec3 movement = living.getDeltaMovement();
            double speed = movement.horizontalDistance();
            if (speed > 0.001) {
                double extrapolateTicks = ticksRemaining * 0.05 / Math.max(speed, 0.001);
                destination = living.position().add(movement.normalize().scale(extrapolateTicks));
            } else {
                destination = living.position();
            }
        }

        destination = destination.add(
                (random.nextDouble() * 2 - 1) * GestaltCosts.TIME_PHASE_DESTINATION_RADIUS,
                0,
                (random.nextDouble() * 2 - 1) * GestaltCosts.TIME_PHASE_DESTINATION_RADIUS);

        Vec3 grounded = findValidLandingPos(living.level(), destination.x, destination.z,
                destination.y, GestaltCosts.TIME_PHASE_BODY_DOUBLE_DROP_SCAN);
        return grounded != null ? grounded : living.position();
    }

    /**
     * Scans downward from (x, startY, z) to find the highest block column where:
     *   - the block at y has a non-empty collision shape (solid ground),
     *   - the block at y+1 (feet) is passable,
     *   - the block at y+2 (head) is passable.
     * Returns the feet position Vec3(x, y+1, z), or null if no valid spot is found within scanRange.
     */
    @javax.annotation.Nullable
    private static Vec3 findValidLandingPos(Level level, double x, double z, double startY, int scanRange) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int topY = (int) Math.floor(startY);

        // Scan upward first — handles destination inside solid terrain (e.g. inside a hill)
        int maxY = Math.min(level.getMaxBuildHeight() - 2, topY + scanRange);
        for (int y = topY; y <= maxY; y++) {
            BlockPos ground = new BlockPos(bx, y, bz);
            BlockPos feet  = new BlockPos(bx, y + 1, bz);
            BlockPos head  = new BlockPos(bx, y + 2, bz);
            if (!level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                return new Vec3(x, y + 1.0, z);
            }
        }

        // Scan downward — handles destination in mid-air above terrain
        int minY = Math.max(level.getMinBuildHeight(), topY - scanRange);
        for (int y = topY - 1; y >= minY; y--) {
            BlockPos ground = new BlockPos(bx, y, bz);
            BlockPos feet  = new BlockPos(bx, y + 1, bz);
            BlockPos head  = new BlockPos(bx, y + 2, bz);
            if (!level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                return new Vec3(x, y + 1.0, z);
            }
        }

        return null;
    }

    /** Spawn a 70%-opacity, ~23-tick fading afterimage in VIOLET (normal path markers). */
    private static void spawnFadingAfterimage(ServerPlayer player, double x, double y, double z, int sourceEntityId) {
        int id = GestaltNetworking.nextAfterimageId();
        GestaltNetworking.sendSpawnAfterimage(player, id, x, y, z, sourceEntityId, 0.7f, 0.03f, TINT_VIOLET);
    }

    /** Spawn a 50%-opacity persistent afterimage with the given tint. */
    private static void spawnPersistentAfterimage(ServerPlayer player, double x, double y, double z,
                                                   int sourceEntityId, int tint) {
        int id = GestaltNetworking.nextAfterimageId();
        GestaltNetworking.sendSpawnAfterimage(player, id, x, y, z, sourceEntityId, 0.5f, 0f, tint);
    }

    private static void applyPlayerSlowModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(TIME_PHASE_PLAYER_SLOW_ID);
        attr.addOrUpdateTransientModifier(new AttributeModifier(
                TIME_PHASE_PLAYER_SLOW_ID, GestaltCosts.TIME_PHASE_CATCHUP_SLOW,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void removePlayerSlowModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(TIME_PHASE_PLAYER_SLOW_ID);
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
