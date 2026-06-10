package net.ragdot.gestaltresonance.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side: handles the gestalt throw ability (sneak+jump boost).
 *
 * On activation: applies a velocity impulse scaled by pitch and strength, costs 3 exhaustion,
 * and sets the player's action to THROW for 8 ticks so the animation can play.
 *
 * Pitch mapping (player.getXRot(), -90=up, 0=forward):
 *   t = 0  (pitch 0°  / forward): 80% horizontal, 20% vertical
 *   t = 1  (pitch -90° / up):      0% horizontal, 100% vertical
 *
 * Speed formula: totalSpeed = BASE_SPEED + strength * SPEED_PER_STRENGTH
 * Calibrated so forward throw travels roughly (3 + strength) blocks horizontally.
 */
public class GestaltThrowEvents {

    private static final double BASE_SPEED = 1.3;
    private static final double SPEED_PER_STRENGTH = 0.3;
    private static final int THROW_DURATION_TICKS = 8;
    private static final float THROW_EXHAUSTION = 3.0f;

    // Throw fall-damage reduction: extend safe-fall threshold by 2 blocks (vanilla 3 → 5).
    private static final float FALL_DISTANCE_REDUCTION = 2.0f;

    private static final Map<UUID, Integer> throwTimerMap = new HashMap<>();
    // Tick when the player last threw. Protection is valid for THROW_PROTECTION_TICKS after the throw.
    // Timestamp window avoids the cleanup-loop timing hazards of the previous Set approach.
    private static final Map<UUID, Long> throwProtectionTickMap = new HashMap<>();
    // Max throw arc is ~25 ticks; 100 gives generous margin with no false positives.
    private static final long THROW_PROTECTION_TICKS = 100L;

    // -------------------------------------------------------------------------
    // Public API called from GestaltNetworking
    // -------------------------------------------------------------------------

    /**
     * Pure velocity computation, callable from both client and server.
     * Pitch decomposes total speed into horizontal/vertical components.
     */
    public static Vec3 computeThrowVelocity(Player player, PlayerGestaltState state) {
        float pitchDeg = Math.max(-90f, Math.min(0f, player.getXRot()));
        double t = -pitchDeg / 90.0;

        double hRatio = 0.7 * (1.0 - t);
        double vRatio = 0.3 + 0.2 * t;

        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int strength = (stats != null) ? Math.max(1, stats.strength()) : 1;
        double totalSpeed = BASE_SPEED + strength * SPEED_PER_STRENGTH;

        Vec3 look = player.getLookAngle();
        double lookXZLen = Math.sqrt(look.x * look.x + look.z * look.z);
        double lookNormX = lookXZLen > 0 ? look.x / lookXZLen : 0.0;
        double lookNormZ = lookXZLen > 0 ? look.z / lookXZLen : 1.0;

        return new Vec3(
                lookNormX * hRatio * totalSpeed,
                vRatio * totalSpeed,
                lookNormZ * hRatio * totalSpeed);
    }

    public static void handleThrowInput(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned()) return;
        if (state.getAction() != GestaltAction.IDLE) return;
        // onGround check skipped here: client validates onGround before sending,
        // and by the time the packet arrives the client may already have left ground
        // due to its own vanilla jump (which the client overrides via LivingJumpEvent).

        Vec3 velocity = computeThrowVelocity(player, state);
        player.setDeltaMovement(velocity);

        // --- Costs ---
        player.getFoodData().addExhaustion(THROW_EXHAUSTION);

        // --- Start throw action ---
        state.setAction(GestaltAction.THROW);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        throwProtectionTickMap.put(player.getUUID(), (long) player.getServer().getTickCount());
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.THROW);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.5f);

        throwTimerMap.put(player.getUUID(), THROW_DURATION_TICKS);
    }

    /** Cancel a pending throw timer (e.g. on unsummon). */
    public static void cancelThrow(ServerPlayer player) {
        UUID uuid = player.getUUID();
        throwProtectionTickMap.remove(uuid);
        if (throwTimerMap.remove(uuid) != null) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.getAction() == GestaltAction.THROW) {
                state.setAction(GestaltAction.IDLE);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        throwTimerMap.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                    if (state.getAction() == GestaltAction.THROW) {
                        state.setAction(GestaltAction.IDLE);
                        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
                    }
                }
                return true;
            }
            entry.setValue(remaining);
            return false;
        });

        // Purge expired throw-protection timestamps (safety net for disconnects without cancelThrow).
        long currentTick = event.getServer().getTickCount();
        throwProtectionTickMap.entrySet().removeIf(e -> currentTick - e.getValue() > THROW_PROTECTION_TICKS);
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        boolean hasThrowProtection = false;
        if (player instanceof ServerPlayer sp2) {
            Long throwTick = throwProtectionTickMap.get(sp2.getUUID());
            hasThrowProtection = throwTick != null
                    && sp2.getServer().getTickCount() - throwTick <= THROW_PROTECTION_TICKS;
        }

        // Step 1: throw protection — subtract 2 blocks from effective fall distance.
        if (hasThrowProtection) {
            throwProtectionTickMap.remove(((ServerPlayer) player).getUUID());
            event.setDistance(Math.max(0f, event.getDistance() - FALL_DISTANCE_REDUCTION));
        }

        // Step 2: fall break — applied independently, uses distance already reduced by throw.
        boolean didFallBreak = state.isSummoned() && state.isGuarding()
                && event.getDistance() >= GestaltCosts.FALL_BREAK_MIN_DISTANCE;
        if (didFallBreak) {
            float rawDistance = event.getDistance();
            event.setDistance(Math.max(0f, rawDistance - GestaltCosts.FALL_BREAK_DISTANCE_REDUCTION));
            event.setDamageMultiplier(event.getDamageMultiplier() * GestaltCosts.FALL_BREAK_DAMAGE_MULTIPLIER);
            if (player instanceof ServerPlayer sp) {
                boolean transferred = applyFallBreakKill(sp, event);
                GestaltNetworking.broadcastFallBreakImpact(sp);
                sp.playNotifySound(GestaltSounds.GESTALT_HEAVY_IMPACT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                sp.level().playSound(sp, sp.getX(), sp.getY(), sp.getZ(),
                        GestaltSounds.GESTALT_HEAVY_IMPACT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                if (!transferred && rawDistance >= GestaltCosts.FALL_BREAK_CRASH_DISTANCE) {
                    GestaltAcquisitionEvents.crashGestalt(sp);
                }
            }
        } else if (hasThrowProtection && player instanceof ServerPlayer sp) {
            // Throw landing without fall break: still trigger the gestalt impact shake.
            GestaltNetworking.broadcastFallBreakImpact(sp);
        }
    }

    /**
     * If the player is contacting any living entity at fall landing, redirect the remaining
     * fall damage to those entities (player takes zero). Registers hits for resonance tracking.
     */
    /** Returns true if fall damage was redirected to a mob (player is fully unharmed). */
    private static boolean applyFallBreakKill(ServerPlayer sp, LivingFallEvent event) {
        float transferDamage = Math.max(0f, (event.getDistance() - 3f) * event.getDamageMultiplier());
        if (transferDamage <= 0f) return false;

        List<LivingEntity> contacts = sp.level().getEntitiesOfClass(
                LivingEntity.class,
                sp.getBoundingBox().inflate(0.3),
                e -> !(e instanceof Player));
        if (contacts.isEmpty()) return false;

        long tick = sp.getServer().getTickCount();
        var src = GestaltDamageTypes.gestalt(sp.level(), sp);
        for (LivingEntity target : contacts) {
            target.hurt(src, transferDamage);
            GestaltResonanceEvents.trackFallBreakHit(target.getUUID(), sp.getUUID(), tick);
        }
        event.setDistance(0f);
        return true;
    }
}
