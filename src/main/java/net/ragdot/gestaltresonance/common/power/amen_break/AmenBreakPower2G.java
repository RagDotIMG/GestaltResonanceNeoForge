package net.ragdot.gestaltresonance.common.power.amen_break;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.GhostPlayerHandler;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.passive.AmenBreakPassive;
import net.ragdot.gestaltresonance.common.passive.GestaltPassive;
import net.ragdot.gestaltresonance.common.passive.GestaltPassiveRegistry;

/**
 * Amen Break — Phase Out (Power 2G).
 *
 * Toggle: X pressed while guarding flips {@code phaseOutArmed}. No cost, no cooldown.
 * Trigger: when armed and non-void damage would land, pay (resonance + gestaltXP ≥ 25),
 * cancel the hit, enable a 60-tick ghost window, and auto-summon if unsummoned.
 * Cooldown: 2400 ticks starting when the ghost window ends.
 */
public final class AmenBreakPower2G {

    public static final AmenBreakPower2G EVENT_LISTENER = new AmenBreakPower2G();

    private AmenBreakPower2G() {}

    // ── Toggle ────────────────────────────────────────────────────────────────

    public static void toggle(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        // Only toggle if player has AmenBreak, is guarding, and is not in an active ghost window
        if (!GestaltIds.AMEN_BREAK.equals(state.getGestaltId())) return;
        if (!state.isGuarding()) return;
        if (state.isPhaseOutActive()) return;

        state.setPhaseOutArmed(!state.isPhaseOutArmed());
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        GestaltNetworking.syncPhaseOutToPlayer(player);
        GestaltResonance.LOGGER.debug("AmenBreak Phase Out armed={} for {}", state.isPhaseOutArmed(), player.getName().getString());
    }

    // ── Per-tick logic ────────────────────────────────────────────────────────

    public static void tick(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean changed = false;

        // Tick the cooldown countdown
        if (state.getPhaseOutCooldownTicks() > 0) {
            state.setPhaseOutCooldownTicks(state.getPhaseOutCooldownTicks() - 1);
            changed = true;
        }

        // Tick the active ghost window
        if (state.isPhaseOutActive()) {
            // If gestalt crashed during the ghost window, end Phase Out early
            if (!state.isSummoned()) {
                endGhostWindow(player, state);
                return;
            }

            int remaining = state.getPhaseOutTicksRemaining() - 1;
            if (remaining <= 0) {
                endGhostWindow(player, state);
                return;
            }
            state.setPhaseOutTicksRemaining(remaining);
            changed = true;
        }

        if (changed) {
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncPhaseOutToPlayer(player);
        }
    }

    private static void endGhostWindow(ServerPlayer player, PlayerGestaltState state) {
        state.setPhaseOutActive(false);
        state.setPhaseOutTicksRemaining(0);
        state.setPhaseOutCooldownTicks(GestaltCosts.PHASE_OUT_COOLDOWN_TICKS);
        GhostPlayerHandler.setGhostState(player, false);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncPhaseOutToPlayer(player);

        // Re-materialization sound
        player.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 0.8f, 1.3f);
        GestaltResonance.LOGGER.debug("AmenBreak Phase Out ghost window ended for {}", player.getName().getString());
    }

    // ── Damage trigger ────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isPhaseOutArmed()) return;
        if (state.isPhaseOutActive()) return;
        if (state.hasPhaseOutCooldown()) return;
        if (!state.isAwakened()) return;
        if (!GestaltIds.AMEN_BREAK.equals(state.getGestaltId())) return;

        // Soul projection guard: if projecting, Phase Out does not trigger
        if (state.isSoulProjecting()) return;

        // Damage that bypasses invulnerability (void, /kill) is not intercepted by Phase Out
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        // Incoming damage must be > 0 (after potential modifiers already applied at HIGH)
        if (event.getAmount() <= 0f) return;

        // Affordability: Math.max(0, resonance) + gestaltXP >= 25
        int resonance = Math.max(0, state.getResonanceValue());
        int gestaltXp = state.getTotalGestaltXp();
        if (resonance + gestaltXp < GestaltCosts.PHASE_OUT_COST_TOTAL) return;

        // Pay costs: resonance first, remainder from gestaltXP
        int resonancePay = Math.min(GestaltCosts.PHASE_OUT_COST_TOTAL, resonance);
        int xpPay = GestaltCosts.PHASE_OUT_COST_TOTAL - resonancePay;
        state.setResonanceValue(state.getResonanceValue() - resonancePay);
        if (xpPay > 0) state.spendGestaltXp(xpPay);

        // Cancel the triggering hit
        event.setCanceled(true);

        // Auto-summon if not summoned (best-effort)
        if (!state.isSummoned()) {
            autoSummon(player, state);
            // Re-read state after potential summon
            state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        }

        // Enable ghost window
        state.setPhaseOutActive(true);
        state.setPhaseOutTicksRemaining(GestaltCosts.PHASE_OUT_GHOST_TICKS);
        state.setPhaseOutArmed(false); // auto-disarm after trigger (re-arm manually)
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        GhostPlayerHandler.setGhostState(player, true);
        GestaltNetworking.syncPhaseOutToPlayer(player);
        GestaltNetworking.syncResonanceToPlayer(player);
        if (xpPay > 0) GestaltNetworking.syncGestaltXpToPlayer(player);

        player.playNotifySound(GestaltSounds.GESTALT_HEAVY_IMPACT.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
        GestaltResonance.LOGGER.debug("AmenBreak Phase Out triggered for {}", player.getName().getString());
    }

    // ── Auto-summon (best-effort; Phase Out fires regardless) ─────────────────

    private static void autoSummon(ServerPlayer player, PlayerGestaltState state) {
        if (!state.isAwakened()) return;
        long currentTick = player.getServer().getTickCount();
        if (state.hasCrashCooldown(currentTick)) return;
        if (player.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) return;
        // AmenBreak: block summon if a cat is nearby
        if (AmenBreakPassive.hasCatNearby(player)) return;

        state.setSummoned(true);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        GestaltPassive passive = GestaltPassiveRegistry.getPassive(state.getGestaltId());
        if (passive != null) passive.onActivate(player);

        player.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        GestaltNetworking.syncToTracking(player);
    }

    /** Disarm Phase Out (called on death / logout to reset toggle state). */
    public static void disarm(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isPhaseOutArmed() && !state.isPhaseOutActive()) return;
        state.setPhaseOutArmed(false);
        if (state.isPhaseOutActive()) {
            state.setPhaseOutActive(false);
            state.setPhaseOutTicksRemaining(0);
            GhostPlayerHandler.setGhostState(player, false);
        }
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncPhaseOutToPlayer(player);
    }
}
