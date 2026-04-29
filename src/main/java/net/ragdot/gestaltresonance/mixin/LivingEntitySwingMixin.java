package net.ragdot.gestaltresonance.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the player's arm-swing animation while the gestalt is mining
 * (summoned + attack key held + block targeted within gestalt range).
 * Regular combat swings (targeting entities) are unaffected.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void gestaltresonance$suppressMiningSwing(InteractionHand hand, boolean fromServer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player != self) return;
        LocalPlayer player = mc.player;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        if (!mc.options.keyAttack.isDown()) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return;
        if (Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(player.getEyePosition()) > 3.5) return;

        ci.cancel();
    }
}
