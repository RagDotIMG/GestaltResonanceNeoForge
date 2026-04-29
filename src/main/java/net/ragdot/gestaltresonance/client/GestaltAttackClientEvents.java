package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
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
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.AttackInputC2S;

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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive()) return;

        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        GestaltAction action = state.getAction();
        boolean chainActive = action == GestaltAction.HIT_1
                || action == GestaltAction.HIT_2
                || action == GestaltAction.HIT_3;

        if (!chainActive) {
            // If targeting a block in close range, let vanilla handle mining (hold-to-break)
            if (mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() != HitResult.Type.MISS
                    && Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(mc.player.getEyePosition()) <= 3.5) {
                return;
            }
        }

        // Consume discrete attack clicks and forward to server
        while (mc.options.keyAttack.consumeClick()) {
            PacketDistributor.sendToServer(new AttackInputC2S());
        }
    }
}
