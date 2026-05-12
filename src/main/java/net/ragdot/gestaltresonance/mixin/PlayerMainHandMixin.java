package net.ragdot.gestaltresonance.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.ragdot.gestaltresonance.common.GestaltMiningEvents;
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
 * getMainHandItem() is defined on LivingEntity, not Player — we target LivingEntity
 * and guard on instanceof Player so non-player entities are unaffected.
 *
 * If the player is actually holding something, this mixin is a no-op.
 */
@Mixin(LivingEntity.class)
public class PlayerMainHandMixin {

    @Inject(method = "getMainHandItem", at = @At("RETURN"), cancellable = true)
    private void gestalt$virtualMainHandItem(CallbackInfoReturnable<ItemStack> cir) {
        if (!((Object) this instanceof Player self)) return;
        if (!cir.getReturnValue().isEmpty()) return;
        // Compute fresh every call — raycast off the player's current crosshair to pick
        // the right tool TYPE for the block being looked at. No cache, so the tool
        // updates instantly as the crosshair moves between blocks (matters for Jade).
        ItemStack virtual = GestaltMiningEvents.computeVirtualTool(self);
        if (virtual != null) cir.setReturnValue(virtual);
    }
}
