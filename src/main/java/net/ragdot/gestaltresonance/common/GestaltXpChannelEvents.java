package net.ragdot.gestaltresonance.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

/**
 * Server-side: drives the player→gestalt XP channel.
 *
 * Activation requirements (validated in {@link #handleStart}):
 *   - awakened gestalt
 *   - player XP > 0
 *   - gestalt below max level
 *
 * Ramp (ticks elapsed since channel start):
 *   <  10  windup, no drain
 *   10–30  1 player-point per 20 ticks
 *   30–60  1 per 10
 *   60–100 1 per 5
 *   100+   1 per 2
 *
 * The fractional drain accumulates each tick. When the player accumulator passes 1, that many
 * whole points are deducted from the player's XP and the gestalt portion (after efficiency
 * tax) is added to the gestalt accumulator. When the gestalt accumulator passes 1, those
 * whole points are committed to {@link PlayerGestaltState#addGestaltExperience}.
 */
public class GestaltXpChannelEvents {

    // The 10-tick windup is gated client-side (see GestaltKeybinds): the client only sends Start
    // after the player has held the chord for 10 ticks. Server-side drain begins immediately on
    // Start, so the ramp constants below are shifted by -10 relative to the original spec.

    /** Fraction of player XP that reaches the gestalt after the efficiency tax. */
    private static final float TRANSFER_EFFICIENCY = 0.6f;

    /** Movement-speed slow modifier active only while channeling.
     *  Tuned to approximate vanilla's eating slowdown (input ×0.2). */
    private static final ResourceLocation CHANNEL_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "channel_slow");
    private static final double CHANNEL_SLOW_AMOUNT = -0.8;

    // -------------------------------------------------------------------------
    // C2S handlers (called from GestaltNetworking)
    // -------------------------------------------------------------------------

    public static void handleStart(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isChannelingXp()) return;
        if (!state.isAwakened()) return;
        if (state.getGestaltLevel() >= PlayerGestaltState.MAX_GESTALT_LEVEL) return;
        if (XpHelper.getTotalXp(player) <= 0) return;

        state.setChannelingXp(true);
        state.setChannelStartTick(player.getServer().getTickCount());
        state.setChannelPlayerXpAccumulator(0f);
        state.setChannelGestaltXpAccumulator(0f);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        applySlowModifier(player);
        GestaltNetworking.syncChannelStateToTracking(player, true, false);
    }

    public static void handleStop(ServerPlayer player) {
        stopChannel(player, false);
    }

    /**
     * @param broken true when the channel was forcibly interrupted (e.g. damage), false on
     *               clean stop (release, completion).
     */
    public static void stopChannel(ServerPlayer player, boolean broken) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isChannelingXp()) return;

        // Commit any leftover whole gestalt points before clearing.
        flushGestaltAccumulator(player, state);

        state.setChannelingXp(false);
        state.setChannelPlayerXpAccumulator(0f);
        state.setChannelGestaltXpAccumulator(0f);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        removeSlowModifier(player);
        GestaltNetworking.syncChannelStateToTracking(player, false, broken);
    }

    // -------------------------------------------------------------------------
    // Per-tick drain
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isChannelingXp()) continue;

            long elapsed = event.getServer().getTickCount() - state.getChannelStartTick();
            float drainPerTick = drainRatePerTick(elapsed);
            if (drainPerTick <= 0f) continue;

            float playerAcc = state.getChannelPlayerXpAccumulator() + drainPerTick;
            int wholeDrain = (int) Math.floor(playerAcc);

            if (wholeDrain > 0) {
                int currentXp = XpHelper.getTotalXp(player);
                int actualDrain = Math.min(wholeDrain, currentXp);
                if (actualDrain > 0) {
                    XpHelper.setTotalXp(player, currentXp - actualDrain);
                    state.setChannelGestaltXpAccumulator(
                            state.getChannelGestaltXpAccumulator() + actualDrain * TRANSFER_EFFICIENCY);
                }
                playerAcc -= wholeDrain;
                state.setChannelPlayerXpAccumulator(playerAcc);

                // Commit any whole gestalt points.
                flushGestaltAccumulator(player, state);

                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            } else {
                state.setChannelPlayerXpAccumulator(playerAcc);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            }

            // Interrupt conditions: out of XP, or gestalt maxed.
            if (XpHelper.getTotalXp(player) <= 0
                    || state.getGestaltLevel() >= PlayerGestaltState.MAX_GESTALT_LEVEL) {
                stopChannel(player, false);
            }
        }
    }

    /** Damage forcibly breaks the channel. */
    @SubscribeEvent
    public void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isChannelingXp()) return;
        if (event.getAmount() <= 0f) return;
        stopChannel(player, true);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static float drainRatePerTick(long elapsed) {
        // Spec windows are 10–30 / 30–60 / 60–100 / 100+ measured from the original key-press;
        // since the client gates the first 10 ticks, we shift each window by -10.
        if (elapsed < 20) return 1f / 20f;
        if (elapsed < 50) return 1f / 10f;
        if (elapsed < 90) return 1f / 5f;
        return 1f / 2f;
    }

    private static void flushGestaltAccumulator(ServerPlayer player, PlayerGestaltState state) {
        float acc = state.getChannelGestaltXpAccumulator();
        int whole = (int) Math.floor(acc);
        if (whole <= 0) return;

        int oldLevel = state.getGestaltLevel();
        state.addGestaltExperience(whole);
        state.setChannelGestaltXpAccumulator(acc - whole);

        // Resonance: +GAIN_XP_CHANNEL per GAIN_XP_CHANNEL_THRESHOLD gestalt XP received.
        // Modify the state object directly (callers write it back); don't go through applyResonance
        // to avoid the state being overwritten after this method returns.
        int resoAcc = state.getXpChannelResonanceAccumulator() + whole;
        int resoGain = (resoAcc / GestaltCosts.GAIN_XP_CHANNEL_THRESHOLD) * GestaltCosts.GAIN_XP_CHANNEL;
        state.setXpChannelResonanceAccumulator(resoAcc % GestaltCosts.GAIN_XP_CHANNEL_THRESHOLD);
        if (resoGain > 0 && state.isAwakened()) {
            GestaltStats channelStats = GestaltStatsRegistry.getStats(state.getGestaltId());
            if (channelStats != null) {
                state.addResonance(resoGain, channelStats);
                GestaltResonanceEvents.recordResonanceGain(player.getUUID(), player.getServer().getTickCount());
                GestaltNetworking.syncResonanceToPlayer(player);
            }
        }

        // Send the XP/level sync to the owning player; tracking clients only need channel state.
        GestaltNetworking.syncGestaltXpToPlayer(player);

        if (state.getGestaltLevel() != oldLevel) {
            net.ragdot.gestaltresonance.common.skin.GestaltSkinUnlockEvents.checkLevelUnlock(player);
        }
    }

    private static void applySlowModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(CHANNEL_SLOW_ID);
        attr.addOrUpdateTransientModifier(new AttributeModifier(
                CHANNEL_SLOW_ID, CHANNEL_SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void removeSlowModifier(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(CHANNEL_SLOW_ID);
    }
}
