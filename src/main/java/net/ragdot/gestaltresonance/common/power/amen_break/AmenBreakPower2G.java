package net.ragdot.gestaltresonance.common.power.amen_break;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import net.minecraft.server.level.ServerLevel;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltParticles;
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

    private static final ResourceLocation PHASE_OUT_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "phase_out_slow");
    private static final double PHASE_OUT_SLOW_AMOUNT = -0.3;

    private AmenBreakPower2G() {}

    // ── Toggle ────────────────────────────────────────────────────────────────

    public static void toggle(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        // Only toggle if player has AmenBreak, is guarding, meets level requirement, and is not in an active ghost window
        if (!GestaltIds.AMEN_BREAK.equals(state.getGestaltId())) return;
        if (!state.isGuarding()) return;
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[1][2]) return;
        if (state.isPhaseOutActive()) return;
        if (state.isPhaseCourtActive()) return;

        state.setPhaseOutArmed(!state.isPhaseOutArmed());
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
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

            if (remaining >= GestaltCosts.PHASE_OUT_GHOST_TICKS - 10
                    && player.level() instanceof ServerLevel sl) {
                for (int i = 0; i < 2; i++) {
                    double px = player.getX() + (player.getRandom().nextDouble() - 0.5) * 0.6;
                    double py = player.getY() + player.getRandom().nextDouble() * 1.8;
                    double pz = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.6;
                    sl.sendParticles(GestaltParticles.GESTALT_ILLUSION.get(), px, py, pz, 1, 0, 0, 0, 0);
                }
            }
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
        removeSlowModifier(player);
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

        // Soul projection or Phase Court: Phase Out does not trigger
        if (state.isSoulProjecting()) return;
        if (state.isPhaseCourtActive()) return;

        // Damage that bypasses invulnerability (void, /kill) is not intercepted by Phase Out
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        // Only trigger on entity-originated damage (mobs, players, projectiles, dispenser traps).
        // Pure environmental sources (fall, lava, fire, drowning, etc.) have neither an attacker
        // nor a direct entity. Dispenser traps have no attacker but do have a direct projectile entity.
        if (event.getSource().getEntity() == null && event.getSource().getDirectEntity() == null) return;

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
        applySlowModifier(player);
        GestaltNetworking.syncPhaseOutToPlayer(player);
        GestaltNetworking.syncResonanceToPlayer(player);
        if (xpPay > 0) GestaltNetworking.syncGestaltXpToPlayer(player);

        player.playNotifySound(GestaltSounds.GESTALT_AB_2G.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
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

    private static void applySlowModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(PHASE_OUT_SLOW_ID);
        attr.addOrUpdateTransientModifier(new AttributeModifier(
                PHASE_OUT_SLOW_ID, PHASE_OUT_SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void removeSlowModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(PHASE_OUT_SLOW_ID);
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
            removeSlowModifier(player);
        }
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncPhaseOutToPlayer(player);
    }
}
