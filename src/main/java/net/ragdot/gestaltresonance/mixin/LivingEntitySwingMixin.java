package net.ragdot.gestaltresonance.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the player's arm-swing animation while the gestalt is mining.
 *
 * Two paths:
 *  - Server-side: cancels LivingEntity.swing() before it sends ClientboundAnimatePacket
 *    to tracking clients. Uses a server-side pick() raycast instead of the synced
 *    isMining() flag to avoid the 1-tick lag where the sync packet hasn't arrived yet.
 *  - Client-side (local player only): uses direct input + crosshair detection for
 *    zero-lag suppression of the local arm-swing visual.
 *
 * Combat swings (HIT chain, charged strike, guard) are excluded; entity targets are
 * unaffected because pick() returns ENTITY, not BLOCK.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void gestaltresonance$suppressMiningSwing(InteractionHand hand, boolean fromServer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (!self.level().isClientSide()) {
            // Server-side: cancel before ClientboundAnimatePacket is broadcast to other clients.
            // Use pick() instead of state.isMining() to avoid the 1-tick sync lag that lets
            // the first few swing packets through before SyncMiningStateC2S is processed.
            if (self instanceof ServerPlayer sp) {
                PlayerGestaltState state = sp.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                if (!state.isSummoned()) return;
                GestaltAction action = state.getAction();
                boolean combatActive = action == GestaltAction.HIT_1 || action == GestaltAction.HIT_2
                        || action == GestaltAction.HIT_3 || action == GestaltAction.CHARGED_STRIKE_WINDUP
                        || action == GestaltAction.CHARGED_STRIKE_TRAVEL || action == GestaltAction.GUARD
                        || action == GestaltAction.LEDGE_GRAB || action == GestaltAction.THROW;
                if (!combatActive) {
                    HitResult hit = sp.pick(GestaltCosts.mineRangeFor(state), 0f, false);
                    if (hit.getType() == HitResult.Type.BLOCK) ci.cancel();
                }
            }
            return;
        }

        // Client-side: suppress only for the local player; remote players are handled server-side.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player != self) return;
        LocalPlayer player = mc.player;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        if (!mc.options.keyAttack.isDown()) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return;
        if (Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(player.getEyePosition()) > GestaltCosts.mineRangeFor(state)) return;

        ci.cancel();
    }
}
