package net.ragdot.gestaltresonance.common.power.amen_break;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.entity.TimePhaseBodyDoubleEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltAttackEvents;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.GestaltStats;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.GhostPlayerHandler;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

import java.util.Comparator;
import java.util.List;

/**
 * Amen Break — Phase Court (Power 3G).
 *
 * Guard activation: deducts 75 resonance, opens a 160-tick ghost window, spawns a body double
 * illusion at player's position. Cooldown (2400 ticks) starts at activation.
 * During the window, tier-1 abilities are replaced by Break Core variants; all other
 * powers and melee/charged-strike are blocked.
 *
 * Break Core 1B: marks the nearest entity within 3 blocks for a recording/dragback sequence
 * that begins when the Phase Court window ends.
 * Break Core 1S: primes any block (bypasses normal primeable whitelist).
 * Break Core 1G: full Queen Killer windup, then 3× damage + target freeze + delayed explosions.
 */
public final class AmenBreakPower3G {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.AMEN_BREAK, GestaltPowerSlot.POWER_3, GestaltPowerModifier.GUARD);

    public static final AmenBreakPower3G EVENT_LISTENER = new AmenBreakPower3G();

    private static final ResourceLocation PHASE_COURT_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "phase_court_slow");
    private static final double PHASE_COURT_SLOW_AMOUNT = -0.3;

    /** ThreadLocal flag: set true while releasing final dragback damage so the banking listener lets it through. */
    private static final ThreadLocal<Boolean> releasingFinalDamage = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AmenBreakPower3G() {}

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        GestaltPowerRegistry.register(KEY, AmenBreakPower3G::activate);
    }

    // ── Activation ────────────────────────────────────────────────────────────

    public static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned()) return;
        if (!player.isCreative() && !state.isAwakened()) return;
        if (!player.isCreative() && state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[2][2]) return;
        if (!state.isGuarding()) return;
        if (state.isPhaseCourtActive()) return;
        if (state.isPhaseOutActive()) return;
        if (!player.isCreative() && state.hasPhaseCourtCooldown()) { playFail(player); return; }

        net.minecraft.world.item.ItemStack heldItem = player.getInventory().getSelected();
        boolean hasCatalyst = !player.isCreative() && !heldItem.isEmpty()
                && GestaltCosts.POWER_3_CATALYSTS.contains(heldItem.getItem());

        boolean takeoverFromTimePhase = state.isTimePhaseActive();
        int resonanceCost = takeoverFromTimePhase
                ? GestaltCosts.PHASE_COURT_FROM_TIME_PHASE_RESONANCE_COST
                : GestaltCosts.PHASE_COURT_RESONANCE_COST;

        if (!player.isCreative() && !hasCatalyst) {
            if (state.getResonanceValue() < resonanceCost) {
                playFail(player);
                return;
            }
            state.setResonanceValue(state.getResonanceValue() - resonanceCost);
        }
        if (hasCatalyst) heldItem.shrink(1);

        // Time Phase takeover: tear down Time Phase silently (no teleport, no damage release),
        // skip spawning a Phase Court body double — the user runs Phase Court without one.
        if (takeoverFromTimePhase) {
            AmenBreakPower3S.tearDownForPhaseCourt(player, state);
            state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        }

        // Set cooldown starting now
        state.setPhaseCourtCooldownTicks(GestaltCosts.PHASE_COURT_COOLDOWN_TICKS);

        // Spawn body double at player's position — skipped on Time Phase takeover.
        if (!takeoverFromTimePhase) {
            SpawnIllusionEntity bodyDouble = new SpawnIllusionEntity(GestaltEntities.SPAWN_ILLUSION.get(), player.level());
            bodyDouble.setPos(player.getX(), player.getY(), player.getZ());
            bodyDouble.setOwnerData(player.getUUID(), player.getLookAngle());
            bodyDouble.setBodyDoubleMode(true);
            bodyDouble.setSlim(TimePhaseBodyDoubleEntity.detectSlimModel(player));
            bodyDouble.setDestination(computeDestination(player));
            bodyDouble.copyEquipmentFrom(player);
            player.level().addFreshEntity(bodyDouble);
            state.setPhaseCourtBodyDoubleId(bodyDouble.getId());
        } else {
            state.setPhaseCourtBodyDoubleId(-1);
        }

        // Enable ghost window
        state.setPhaseCourtActive(true);
        state.setPhaseCourtTicksRemaining(GestaltCosts.PHASE_COURT_GHOST_TICKS);
        state.setBreakCoreUsed(false);
        state.setBreakCoreMarkedEntityId(-1);

        // Drop guard; enable ghost
        state.clearGuard();
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        GhostPlayerHandler.setGhostState(player, true);
        applySlowModifier(player);
        GestaltNetworking.syncGuardToTracking(player, false);
        GestaltNetworking.syncPhaseCourtToPlayer(player);
        GestaltNetworking.syncResonanceToPlayer(player);

        player.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
        GestaltResonance.LOGGER.debug("AmenBreak Phase Court activated for {}", player.getName().getString());
    }

    // ── Per-tick logic ────────────────────────────────────────────────────────

    public static void tick(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean changed = false;

        // Tick the activation cooldown countdown
        if (state.getPhaseCourtCooldownTicks() > 0) {
            state.setPhaseCourtCooldownTicks(state.getPhaseCourtCooldownTicks() - 1);
            changed = true;
        }

        // ── Active Phase Court window ────────────────────────────────────────
        if (state.isPhaseCourtActive()) {
            if (!state.isSummoned()) {
                endGhostWindow(player, state);
                return;
            }
            // Freeze the Phase Court timer while 1G is winding up or the post-hit sequence runs.
            // Post-hit ends the window; windup is just paused until the hit (or miss) resolves.
            if (state.getPhaseCourtPostHitTick() <= 0 && state.getAction() != GestaltAction.POWER_1G_WINDUP) {
                int remaining = state.getPhaseCourtTicksRemaining() - 1;
                if (remaining <= 0) {
                    endGhostWindow(player, state);
                    return;
                }
                state.setPhaseCourtTicksRemaining(remaining);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                GestaltNetworking.syncPhaseCourtToPlayer(player);
                return;
            }
            // Post-hit is active — fall through to tickPostHit below
        }

        // ── Break Core 1G post-hit freeze sequence ───────────────────────────
        if (state.getPhaseCourtPostHitTick() > 0) {
            changed |= tickPostHit(player, state);
        }

        // ── Break Core 1B recording phase ────────────────────────────────────
        if (state.isBreakCoreRecording()) {
            changed |= tickRecording(player, state);
        }

        // ── Break Core 1B dragback phase ─────────────────────────────────────
        if (state.isBreakCoreDragback()) {
            changed |= tickDragback(player, state);
        }

        if (changed) {
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        }
    }

    private static boolean tickPostHit(ServerPlayer player, PlayerGestaltState state) {
        int tick = state.getPhaseCourtPostHitTick() + 1;
        state.setPhaseCourtPostHitTick(tick);

        Entity targetEntity = player.level().getEntity(state.getPhaseCourtPostHitTargetId());
        Vec3 lockPos = state.getPhaseCourtPostHitTargetPos();
        Vec3 playerLockPos = state.getPhaseCourtPostHitPlayerPos();

        // Ticks 1-10: freeze player + target
        if (tick <= GestaltCosts.PHASE_COURT_1G_PLAYER_LOCK_TICKS) {
            if (playerLockPos != null) {
                player.teleportTo(playerLockPos.x, playerLockPos.y, playerLockPos.z);
            }
            if (targetEntity != null && lockPos != null) {
                targetEntity.setDeltaMovement(Vec3.ZERO);
                targetEntity.teleportTo(lockPos.x, lockPos.y, lockPos.z);
            }
        }

        // At tick 10: end Phase Court window (if still active somehow)
        if (tick == GestaltCosts.PHASE_COURT_1G_PLAYER_LOCK_TICKS) {
            if (state.isPhaseCourtActive()) {
                endGhostWindow(player, state);
                // Re-read after endGhostWindow mutates state
                state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            }
        }

        // Ticks 11-20: freeze target only
        if (tick > GestaltCosts.PHASE_COURT_1G_PLAYER_LOCK_TICKS && tick <= GestaltCosts.PHASE_COURT_1G_TARGET_LOCK_TICKS) {
            if (targetEntity != null && lockPos != null) {
                targetEntity.setDeltaMovement(Vec3.ZERO);
                targetEntity.teleportTo(lockPos.x, lockPos.y, lockPos.z);
            }
        }

        // At tick 20: restore target AI
        if (tick == GestaltCosts.PHASE_COURT_1G_TARGET_LOCK_TICKS) {
            if (targetEntity instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }

        // Ticks 42, 44: area explosions that exclude the target (can't die yet)
        if ((tick == 42 || tick == 44) && lockPos != null) {
            detonatePostHit(player, lockPos, targetEntity);
        }
        // Tick 46: final explosion includes the target — allowed to die here, 5× damage
        if (tick == 46 && lockPos != null) {
            detonatePostHit(player, lockPos, null, GestaltCosts.PHASE_COURT_1G_FINAL_EXPLOSION_MULT);
        }

        // Tick 47: clear post-hit state
        if (tick >= 47) {
            state.setPhaseCourtPostHitTick(0);
            state.setPhaseCourtPostHitTargetId(-1);
            state.setPhaseCourtPostHitTargetPos(null);
            state.setPhaseCourtPostHitPlayerPos(null);
        }
        return true;
    }

    private static boolean tickRecording(ServerPlayer player, PlayerGestaltState state) {
        int recordTick = state.getBreakCoreRecordTick() + 1;
        state.setBreakCoreRecordTick(recordTick);

        Entity markedEntity = player.level().getEntity(state.getBreakCoreMarkedEntityId());
        if (!(markedEntity instanceof LivingEntity living) || !living.isAlive()) {
            if (markedEntity != null) {
                releaseAllBankedDamage(player, state, markedEntity.position().add(0, markedEntity.getBbHeight() * 0.5, 0));
            }
            clearAfterimages(player, state);
            state.clearBreakCoreState();
            return true;
        }

        // Record snapshot every SNAPSHOT_INTERVAL ticks
        if (recordTick % GestaltCosts.PHASE_COURT_SNAPSHOT_INTERVAL == 0) {
            int idx = state.getBreakCoreSnapshotCount();
            if (idx < GestaltCosts.PHASE_COURT_SNAPSHOT_COUNT) {
                Vec3 pos = living.position();
                state.getBreakCoreSnapshots()[idx] = pos;
                state.setBreakCoreSnapshotCount(idx + 1);

                // Spawn client-only afterimage on the activating player only.
                int afterimageId = GestaltNetworking.nextAfterimageId();
                GestaltNetworking.sendSpawnAfterimage(player, afterimageId,
                        pos.x, pos.y, pos.z, markedEntity.getId(), 0.7f, 0.03f, AmenBreakPower3S.TINT_VIOLET);
                state.getBreakCoreAfterimageIds()[idx] = afterimageId;
            }
        }

        // At end of recording phase: start dragback
        if (recordTick >= GestaltCosts.PHASE_COURT_RECORD_DURATION) {
            state.setBreakCoreRecording(false);
            if (state.getBreakCoreSnapshotCount() > 0) {
                state.setBreakCoreDragback(true);
                state.setBreakCoreDragbackIndex(state.getBreakCoreSnapshotCount() - 1);
                if (markedEntity instanceof Mob mob) {
                    mob.setNoAi(true);
                }
            } else {
                state.clearBreakCoreState();
            }
        }
        return true;
    }

    private static boolean tickDragback(ServerPlayer player, PlayerGestaltState state) {
        Entity markedEntity = player.level().getEntity(state.getBreakCoreMarkedEntityId());
        if (!(markedEntity instanceof LivingEntity living) || !living.isAlive()) {
            // Target died during dragback — release banked damage at current position and clear
            if (markedEntity != null) {
                releaseAllBankedDamage(player, state, markedEntity.position().add(0, markedEntity.getBbHeight() * 0.5, 0));
            }
            clearAfterimages(player, state);
            state.clearBreakCoreState();
            return true;
        }

        int idx = state.getBreakCoreDragbackIndex();
        if (idx < 0) {
            // All waypoints reached — final explosion + banked damage
            releasingFinalDamage.set(Boolean.TRUE);
            try {
                Vec3 midpoint = living.position().add(0, living.getBbHeight() * 0.5, 0);
                releaseAllBankedDamage(player, state, midpoint);
            } finally {
                releasingFinalDamage.set(Boolean.FALSE);
            }
            if (living instanceof Mob mob) {
                mob.setNoAi(false);
            }
            clearAfterimages(player, state);
            state.clearBreakCoreState();
            return true;
        }

        Vec3 waypoint = state.getBreakCoreSnapshots()[idx];
        if (waypoint == null) {
            state.setBreakCoreDragbackIndex(idx - 1);
            return true;
        }

        Vec3 current = living.position();
        Vec3 toWaypoint = waypoint.subtract(current);
        double dist = toWaypoint.length();

        if (dist <= GestaltCosts.PHASE_COURT_CONTACT_RADIUS) {
            // Reached waypoint — detonate afterimage (non-final, damage multiplied)
            Vec3 waypointCenter = waypoint.add(0, living.getBbHeight() * 0.5, 0);
            int level = state.getGestaltLevel();
            float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.PHASE_COURT_EXPLOSION_BASE_RADIUS, level);
            float damage = GestaltExplosionUtil.scaledDamage(GestaltCosts.PHASE_COURT_EXPLOSION_BASE_DAMAGE, level)
                    * GestaltCosts.PHASE_COURT_EXPLOSION_DAMAGE_MULT
                    * GestaltCosts.PHASE_COURT_1B_DRAGBACK_MULT;
            GestaltExplosionUtil.detonate(
                    player.level(), waypointCenter, radius, damage,
                    GestaltDamageTypes.gestalt(player.level(), player),
                    null, living);

            // Discard the client-only afterimage at this waypoint
            int afterimageId = state.getBreakCoreAfterimageIds()[idx];
            if (afterimageId >= 0) {
                GestaltNetworking.sendDiscardAfterimage(player, afterimageId);
            }

            state.setBreakCoreDragbackIndex(idx - 1);
        } else {
            // Move entity toward waypoint
            Vec3 step = toWaypoint.normalize().scale(Math.min(GestaltCosts.PHASE_COURT_DRAGBACK_SPEED, dist));
            living.setDeltaMovement(step);
            living.setPos(current.x + step.x, current.y + step.y, current.z + step.z);
        }
        return true;
    }

    // ── End ghost window ──────────────────────────────────────────────────────

    static void endGhostWindow(ServerPlayer player, PlayerGestaltState state) {
        // Discard body double
        if (state.getPhaseCourtBodyDoubleId() >= 0) {
            Entity bodyDouble = player.level().getEntity(state.getPhaseCourtBodyDoubleId());
            if (bodyDouble != null) bodyDouble.discard();
            state.setPhaseCourtBodyDoubleId(-1);
        }

        state.setPhaseCourtActive(false);
        state.setPhaseCourtTicksRemaining(0);
        GhostPlayerHandler.setGhostState(player, false);
        removeSlowModifier(player);

        // If Break Core 1B marked an entity: start recording phase
        if (state.getBreakCoreMarkedEntityId() >= 0) {
            Entity marked = player.level().getEntity(state.getBreakCoreMarkedEntityId());
            if (marked instanceof LivingEntity living && living.isAlive()) {
                state.setBreakCoreRecording(true);
                state.setBreakCoreRecordTick(0);
                state.setBreakCoreSnapshotCount(0);
            } else {
                // Entity is gone — skip to clear
                state.setBreakCoreMarkedEntityId(-1);
            }
        }

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncPhaseCourtToPlayer(player);

        player.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 0.8f, 1.3f);
        GestaltResonance.LOGGER.debug("AmenBreak Phase Court ghost window ended for {}", player.getName().getString());
    }

    // ── Break Core 1B — mark entity ──────────────────────────────────────────

    public static void activateBreakCore1B(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        LivingEntity target = findClosestInFront(player, 3.0);
        if (target != null) {
            state.setBreakCoreMarkedEntityId(target.getId());
            player.playNotifySound(GestaltSounds.GESTALT_AB_2G.get(), SoundSource.PLAYERS, 1.0f, 1.5f);
            GestaltResonance.LOGGER.debug("AmenBreak Break Core 1B marked entity {} for {}", target.getId(), player.getName().getString());
        } else {
            player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        state.setBreakCoreUsed(true);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncPhaseCourtToPlayer(player);
    }

    // ── Break Core 1S — prime any block ──────────────────────────────────────

    public static void activateBreakCore1S(ServerPlayer player) {
        AmenBreakPower1S.activateBypassWhitelist(player);
    }

    // ── Break Core 1G — Queen Killer routing ─────────────────────────────────

    public static void activateBreakCore1G(ServerPlayer player) {
        // Delegate to standard 1G activate — all existing checks apply (guard, XP, level, cooldown)
        AmenBreakPower1G.activate(player);
        // If activation succeeded, the windup will start; onBreakCore1GHit() is called from 1G.tickWindup
    }

    /** Called from AmenBreakPower1G.onHit when Phase Court is active. */
    public static void onBreakCore1GHit(ServerPlayer player, LivingEntity target, PlayerGestaltState state) {
        // 3× damage using the standard hit formula
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int strength = stats != null ? stats.strength() : 1;
        int clamped = Math.max(1, Math.min(strength, GestaltAttackEvents.BASE_DAMAGE_BY_STRENGTH.length - 1));
        float baseDamage = GestaltAttackEvents.BASE_DAMAGE_BY_STRENGTH[clamped]
                + state.getGestaltLevel() * 0.5f;
        float finalDamage = baseDamage * GestaltCosts.POWER_1G_DAMAGE_MULTIPLIER * GestaltCosts.PHASE_COURT_1G_HIT_MULT;

        // Freeze target (AI off)
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
        }
        Vec3 lockPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        // Save state before hurt() so the death-prevention listener sees the target ID
        state.setBreakCoreUsed(true);
        state.setPhaseCourtPostHitTick(1);
        state.setPhaseCourtPostHitTargetId(target.getId());
        state.setPhaseCourtPostHitTargetPos(lockPos);
        state.setPhaseCourtPostHitPlayerPos(player.position());
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        target.hurt(GestaltDamageTypes.gestalt(player.level(), player), finalDamage);

        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                GestaltSounds.GESTALT_HEAVY_IMPACT.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.9f);

        GestaltNetworking.syncPhaseCourtToPlayer(player);
        GestaltResonance.LOGGER.debug("AmenBreak Break Core 1G hit for {}", player.getName().getString());
    }

    // ── Disarm (death / logout / respawn) ────────────────────────────────────

    public static void disarm(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isPhaseCourtActive()
                && state.getPhaseCourtPostHitTick() == 0
                && !state.isBreakCoreRecording()
                && !state.isBreakCoreDragback()) return;

        if (state.isPhaseCourtActive()) {
            // Discard body double
            if (state.getPhaseCourtBodyDoubleId() >= 0) {
                Entity bodyDouble = player.level().getEntity(state.getPhaseCourtBodyDoubleId());
                if (bodyDouble != null) bodyDouble.discard();
            }
            GhostPlayerHandler.setGhostState(player, false);
            removeSlowModifier(player);
        }

        // Restore target AI if in post-hit freeze
        if (state.getPhaseCourtPostHitTick() > 0 && state.getPhaseCourtPostHitTargetId() >= 0) {
            Entity target = player.level().getEntity(state.getPhaseCourtPostHitTargetId());
            if (target instanceof Mob mob) mob.setNoAi(false);
        }

        // Restore marked entity AI if in dragback
        if (state.isBreakCoreDragback() && state.getBreakCoreMarkedEntityId() >= 0) {
            Entity marked = player.level().getEntity(state.getBreakCoreMarkedEntityId());
            if (marked instanceof Mob mob) mob.setNoAi(false);
        }

        clearAfterimages(player, state);

        state.setPhaseCourtActive(false);
        state.setPhaseCourtTicksRemaining(0);
        state.setPhaseCourtBodyDoubleId(-1);
        state.setPhaseCourtPostHitTick(0);
        state.setPhaseCourtPostHitTargetId(-1);
        state.setPhaseCourtPostHitTargetPos(null);
        state.setPhaseCourtPostHitPlayerPos(null);
        state.clearBreakCoreState();

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncPhaseCourtToPlayer(player);
    }

    // ── Death prevention during post-hit freeze ───────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dying = event.getEntity();
        if (dying.level().getServer() == null) return;
        for (ServerPlayer player : dying.level().getServer().getPlayerList().getPlayers()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            int postHitTick = state.getPhaseCourtPostHitTick();
            // Allow death only at tick 46+ (final explosion); block early death
            if (postHitTick > 0 && postHitTick < 46
                    && state.getPhaseCourtPostHitTargetId() == dying.getId()) {
                event.setCanceled(true);
                dying.setHealth(1.0f);
                return;
            }
        }
    }

    // ── Damage banking during dragback ────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (releasingFinalDamage.get() == Boolean.TRUE) return;

        // Find if any player has this entity marked in dragback
        if (target.level().getServer() == null) return;
        for (ServerPlayer player : target.level().getServer().getPlayerList().getPlayers()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if ((state.isBreakCoreDragback() || state.isBreakCoreRecording())
                    && state.getBreakCoreMarkedEntityId() == target.getId()) {
                state.addBreakCoreBankedDamage(event.getAmount());
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                event.setCanceled(true);
                return;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static LivingEntity findClosestInFront(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(reach));
        AABB sweep = new AABB(eye, end).inflate(
                GestaltAttackEvents.HITBOX_HALF_WIDTH,
                GestaltAttackEvents.HITBOX_HALF_HEIGHT,
                GestaltAttackEvents.HITBOX_HALF_WIDTH);

        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class, sweep,
                e -> e != player && e.isAlive() && !e.isInvulnerable());
        if (targets.isEmpty()) return null;
        return targets.stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(eye)))
                .orElse(null);
    }

    private static void detonatePostHit(ServerPlayer player, Vec3 center, Entity excluded) {
        detonatePostHit(player, center, excluded, 1.0f);
    }

    private static void detonatePostHit(ServerPlayer player, Vec3 center, Entity excluded, float damageMultiplier) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        int level = state.getGestaltLevel();
        float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.PHASE_COURT_EXPLOSION_BASE_RADIUS, level);
        float damage = GestaltExplosionUtil.scaledDamage(GestaltCosts.PHASE_COURT_EXPLOSION_BASE_DAMAGE, level) * damageMultiplier;
        GestaltExplosionUtil.detonate(
                player.level(), center, radius, damage,
                GestaltDamageTypes.gestalt(player.level(), player),
                null, excluded);
    }

    private static void releaseAllBankedDamage(ServerPlayer player, PlayerGestaltState state, Vec3 pos) {
        float banked = state.getBreakCoreBankedDamage();
        int level = state.getGestaltLevel();
        float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.PHASE_COURT_EXPLOSION_BASE_RADIUS, level);
        float baseDamage = GestaltExplosionUtil.scaledDamage(GestaltCosts.PHASE_COURT_EXPLOSION_BASE_DAMAGE, level)
                * GestaltCosts.PHASE_COURT_1B_DRAGBACK_MULT;
        float finalDamage = baseDamage + banked;
        GestaltExplosionUtil.detonate(
                player.level(), pos, radius, finalDamage,
                GestaltDamageTypes.gestalt(player.level(), player),
                null);
    }

    private static void clearAfterimages(ServerPlayer player, PlayerGestaltState state) {
        // Send a bulk clear — covers any per-id afterimages still alive on the client.
        // Other client-side state (Time Phase afterimages, if any) is independent of this call site;
        // this method is only invoked during Phase Court tear-down where there are no overlapping users.
        int[] ids = state.getBreakCoreAfterimageIds();
        boolean any = false;
        for (int id : ids) if (id >= 0) { any = true; break; }
        if (any) GestaltNetworking.sendClearAfterimages(player);
    }

    private static void applySlowModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(PHASE_COURT_SLOW_ID);
        attr.addOrUpdateTransientModifier(new AttributeModifier(
                PHASE_COURT_SLOW_ID, PHASE_COURT_SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static Vec3 computeDestination(ServerPlayer player) {
        HitResult hit = player.pick(GestaltCosts.ILLUSION_DESTINATION_RANGE, 0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = ((BlockHitResult) hit).getBlockPos();
            return new Vec3(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
        }
        Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(GestaltCosts.ILLUSION_DESTINATION_RANGE));
        int groundY = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) end.x, (int) end.z);
        return new Vec3(end.x, groundY, end.z);
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void removeSlowModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(PHASE_COURT_SLOW_ID);
    }
}
