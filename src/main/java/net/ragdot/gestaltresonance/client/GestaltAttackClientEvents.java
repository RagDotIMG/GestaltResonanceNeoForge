package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.AttackInputC2S;
import net.ragdot.gestaltresonance.common.network.ReleaseChargedStrikeC2S;
import net.ragdot.gestaltresonance.common.network.StartChargedStrikeC2S;
import net.ragdot.gestaltresonance.common.network.SyncMiningStateC2S;

/**
 * Client-side: intercepts attack key presses and sends them to the server as
 * AttackInputC2S when the gestalt is summoned.
 *
 * Consumes keyAttack clicks BEFORE vanilla processes them so the vanilla melee
 * hit does not fire when the gestalt chain takes over. Mining (hold-to-break)
 * uses keyAttack.isDown() and is unaffected by consumeClick().
 *
 * If the chain is inactive and the player is targeting a minable block in range,
 * the click is NOT consumed — vanilla handles mining as normal.
 */
@EventBusSubscriber(modid = GestaltResonance.MODID, value = Dist.CLIENT)
public class GestaltAttackClientEvents {

    /** Tracks the previous tick's attack-key state so we can detect press/release edges. */
    private static boolean wasAttackHeld = false;
    /** Last mining state we synced to the server, so we only push on transitions. */
    private static boolean lastMiningSynced = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive()) return;

        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) {
            wasAttackHeld = false;
            return;
        }

        GestaltAction action = state.getAction();
        boolean chainActive = action == GestaltAction.HIT_1
                || action == GestaltAction.HIT_2
                || action == GestaltAction.HIT_3;
        boolean attackHeld = mc.options.keyAttack.isDown();

        // ── Charged strike: press → start windup, release → fire/abort ─────
        // Only GUARD is a valid entry. To strike mid-combo, the player switches to GUARD first
        // (right-click during HIT_1/HIT_2 immediately transitions to GUARD on the server).
        boolean freshPress = attackHeld && !wasAttackHeld;
        if (freshPress && action == GestaltAction.GUARD) {
            PacketDistributor.sendToServer(new StartChargedStrikeC2S());
        }
        boolean freshRelease = !attackHeld && wasAttackHeld;
        if (freshRelease && action == GestaltAction.CHARGED_STRIKE_WINDUP) {
            int targetId = pickTargetEntityId(mc);
            PacketDistributor.sendToServer(new ReleaseChargedStrikeC2S(targetId));
        }
        wasAttackHeld = attackHeld;

        // While charged strike is active, do NOT consume keyAttack clicks (no chain advancement).
        if (action == GestaltAction.CHARGED_STRIKE_WINDUP || action == GestaltAction.CHARGED_STRIKE_TRAVEL) {
            return;
        }

        if (!chainActive) {
            // If targeting a block in close range, let vanilla handle mining (hold-to-break)
            if (mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() != HitResult.Type.MISS
                    && Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(mc.player.getEyePosition()) <= GestaltCosts.mineRangeFor(state)) {
                return;
            }
        }

        // Consume discrete attack clicks and forward to server
        while (mc.options.keyAttack.consumeClick()) {
            PacketDistributor.sendToServer(new AttackInputC2S());
        }

        // Sync mining state on transitions so other players see the mining pose.
        // (Local detection lives here because it depends on local input + crosshair pick.)
        boolean nowMining = !chainActive
                && action != GestaltAction.GUARD
                && action != GestaltAction.LEDGE_GRAB
                && action != GestaltAction.THROW
                && attackHeld
                && mc.hitResult instanceof BlockHitResult bhr
                && bhr.getType() != HitResult.Type.MISS
                && Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(mc.player.getEyePosition()) <= GestaltCosts.mineRangeFor(state);
        if (nowMining != lastMiningSynced) {
            lastMiningSynced = nowMining;
            PacketDistributor.sendToServer(new SyncMiningStateC2S(nowMining));
            // Update local state immediately so the local renderer is consistent on this tick.
            state.setMining(nowMining);
            mc.player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        }
    }

    /** Returns the id of the LivingEntity the crosshair is pointing at, or -1. Server validates range. */
    private static int pickTargetEntityId(Minecraft mc) {
        Entity picked = mc.crosshairPickEntity;
        if (picked instanceof LivingEntity le && le.isAlive()) return le.getId();
        return -1;
    }
}
