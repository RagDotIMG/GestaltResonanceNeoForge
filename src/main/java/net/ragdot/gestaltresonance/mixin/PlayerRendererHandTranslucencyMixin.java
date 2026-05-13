package net.ragdot.gestaltresonance.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the local player's first-person hand at ~30% opacity while soul-projecting.
 *
 * Vanilla {@code PlayerRenderer.renderHand} renders the bare arm via
 * {@code RenderType.entitySolid} (which has {@code NO_TRANSPARENCY} — alpha is ignored)
 * and the sleeve via {@code RenderType.entityTranslucent}. Two changes are needed for
 * true alpha:
 *  1. {@link #gestaltresonance$swapArmRenderType} swaps the bare-arm render type to
 *     {@code entityTranslucent} when projecting, so alpha actually blends.
 *  2. {@link #gestaltresonance$pre} and {@link #gestaltresonance$post} bracket the call
 *     with {@code RenderSystem.setShaderColor(1, 1, 1, 0.3f)} to set the alpha.
 *
 * The held item (rendered separately by {@code ItemInHandRenderer}) is suppressed by a
 * sibling mixin so the soul appears to have empty translucent hands.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererHandTranslucencyMixin {

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void gestaltresonance$pre(PoseStack pose, MultiBufferSource buf, int light,
                                      AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
                                      CallbackInfo ci) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isSoulProjecting()) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 0.3f);
        } else if (state.isSummoned() && player.getInventory().getSelected().isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void gestaltresonance$post(PoseStack pose, MultiBufferSource buf, int light,
                                       AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
                                       CallbackInfo ci) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isSoulProjecting()) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    @Redirect(method = "renderHand",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType gestaltresonance$swapArmRenderType(ResourceLocation loc, PoseStack pose,
                                                          MultiBufferSource buf, int light,
                                                          AbstractClientPlayer player,
                                                          ModelPart arm, ModelPart sleeve) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isSoulProjecting()) {
            return RenderType.entityTranslucent(loc);
        }
        return RenderType.entitySolid(loc);
    }
}
