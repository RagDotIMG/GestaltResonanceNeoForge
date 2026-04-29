package net.ragdot.gestaltresonance.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * playStepSound() is overridden in Player without calling super, so targeting
 * Player.class ensures the injection actually fires at runtime.
 * Uses Player (not ServerPlayer) so it suppresses sounds on both sides.
 */
@Mixin(Player.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void gestaltresonance$playStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        PlayerGestaltState gestaltState = self.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (gestaltState.isSummoned() && GestaltIds.AMEN_BREAK.equals(gestaltState.getGestaltId())) {
            ci.cancel();
        }
    }
}
