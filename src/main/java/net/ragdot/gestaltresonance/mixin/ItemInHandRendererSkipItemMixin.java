package net.ragdot.gestaltresonance.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses first-person held-item rendering for soul-projecting players. The item
 * appears on the body double instead, so the soul has empty translucent hands.
 *
 * {@code ItemInHandRenderer.renderItem} is also called for the swing animation in arm
 * transforms; when the local player is projecting we skip it. Other callers
 * (e.g. for non-player entities) are unaffected because of the {@code instanceof Player}
 * guard.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererSkipItemMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void gestaltresonance$skipForProjectingPlayer(LivingEntity entity, ItemStack stack,
                                                          ItemDisplayContext ctx, boolean leftHand,
                                                          PoseStack pose, MultiBufferSource buf,
                                                          int light, CallbackInfo ci) {
        if (entity instanceof Player p) {
            PlayerGestaltState state = p.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.isSoulProjecting()) {
                ci.cancel();
            }
        }
    }
}
