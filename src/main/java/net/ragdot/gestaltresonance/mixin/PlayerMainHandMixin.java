package net.ragdot.gestaltresonance.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltMiningEvents;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When the gestalt is summoned and the player's actual main hand is empty,
 * reports the cached virtual tool (the right tool type and tier for the block
 * currently being targeted) as the held item. This makes every system that
 * calls getMainHandItem() — vanilla drop generation, isCorrectToolForDrops,
 * Jade, and any other mod — naturally treat the gestalt as a real tool without
 * requiring a Jade dependency or any per-mod special casing.
 *
 * If the player is actually holding something, this mixin is a no-op.
 */
@Mixin(Player.class)
public class PlayerMainHandMixin {

    @Inject(method = "getMainHandItem", at = @At("RETURN"), cancellable = true)
    private void gestalt$virtualMainHandItem(CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty()) return;
        Player self = (Player) (Object) this;
        PlayerGestaltState state = self.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;
        ItemStack virtual = GestaltMiningEvents.getVirtualTool(self.getUUID());
        if (virtual != null) cir.setReturnValue(virtual);
    }
}
