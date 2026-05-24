package net.ragdot.gestaltresonance.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.PhaseBlossomZoneTracker;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes players with Amen Break summoned invisible to sculk sensors and
 * suppresses their step sounds by overriding isSteppingCarefully() to return true.
 *
 * The vibration system checks isSteppingCarefully() on the source entity in
 * VibrationSystem.User.isValidVibration() — if true AND the event is in the
 * IGNORE_VIBRATIONS_SNEAKING tag, the sculk sensor ignores it. This is exactly
 * how vanilla sneaking works. Additionally, Entity.move() skips gameEvent(STEP)
 * when isSteppingCarefully() returns true, preventing step sounds.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void gestaltresonance$suppressWallInPhaseZone(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        BlockPos eyePos = BlockPos.containing(self.getEyePosition());
        if (PhaseBlossomZoneTracker.isPhased(self.level(), eyePos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isSteppingCarefully", at = @At("HEAD"), cancellable = true)
    private void gestaltresonance$isSteppingCarefully(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player player) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.isSummoned() && GestaltIds.AMEN_BREAK.equals(state.getGestaltId())) {
                cir.setReturnValue(true);
            }
        }
    }
}
