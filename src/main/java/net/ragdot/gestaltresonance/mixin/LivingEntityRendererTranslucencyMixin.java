package net.ragdot.gestaltresonance.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.ragdot.gestaltresonance.client.GhostRenderState;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets {@link GhostRenderState} ThreadLocals on render() entry and clears them on exit
 * so the {@code @ModifyArg} (and {@code EntityModelGhostMixin} for layers) can cheaply
 * apply translucency to the correct entity without re-querying attachments per call.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTranslucencyMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void gestaltresonance$preRender(LivingEntity entity, float yaw, float partial,
                                            com.mojang.blaze3d.vertex.PoseStack pose,
                                            net.minecraft.client.renderer.MultiBufferSource buf,
                                            int light, CallbackInfo ci) {
        if (entity instanceof Player p) {
            PlayerGestaltState state = p.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.isTimePhaseActive()) {
                // Cancel entirely — alpha=0 doesn't suppress cutout armor render types
                ci.cancel();
                return;
            } else if (state.isSoulProjecting()) {
                GhostRenderState.projecting.set(Boolean.TRUE);
                GhostRenderState.translucencyMode.set(1);
            } else if (state.isPhaseOutActive()) {
                GhostRenderState.projecting.set(Boolean.TRUE);
                GhostRenderState.translucencyMode.set(2);
            } else if (state.isPhaseCourtActive()) {
                GhostRenderState.projecting.set(Boolean.TRUE);
                GhostRenderState.translucencyMode.set(3);
            } else if (state.isSummoned() && GestaltIds.FLOAT_PLAY.equals(state.getGestaltId())
                    && p == Minecraft.getInstance().player) {
                int food = p.getFoodData().getFoodLevel();
                float t = GestaltCosts.spotLateScale(food);
                if (t > 0f) {
                    int alpha = Math.round((1.0f - t * (1.0f - GestaltCosts.FLOAT_PLAY_MIN_OPACITY)) * 255);
                    GhostRenderState.floatPlayAlpha.set(alpha);
                    GhostRenderState.projecting.set(Boolean.TRUE);
                    GhostRenderState.translucencyMode.set(4);
                }
            }
        } else if (entity instanceof SpawnIllusionEntity se) {
            if (!se.isBodyDoubleMode()) {
                float alpha = computeIllusionAlpha(se.getAgeTicks());
                int a = Math.round(alpha * 255);
                GhostRenderState.illusionArgb.set((a << 24) | (0x42 << 16) | (0x12 << 8) | 0x85);
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void gestaltresonance$postRender(LivingEntity entity, float yaw, float partial,
                                             com.mojang.blaze3d.vertex.PoseStack pose,
                                             net.minecraft.client.renderer.MultiBufferSource buf,
                                             int light, CallbackInfo ci) {
        GhostRenderState.clear();
    }

    @ModifyArg(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4)
    private int gestaltresonance$modifyColor(int color) {
        int alpha = GhostRenderState.resolveAlpha();
        if (alpha == -1) return color;
        return alpha | (color & 0x00FFFFFF);
    }

    private static float computeIllusionAlpha(int age) {
        if (age < GestaltCosts.ILLUSION_FADE_START) {
            return GestaltCosts.ILLUSION_BASE_OPACITY;
        } else if (age < GestaltCosts.ILLUSION_FADE_START + GestaltCosts.ILLUSION_FADE_DURATION) {
            float t = (age - GestaltCosts.ILLUSION_FADE_START) / (float) GestaltCosts.ILLUSION_FADE_DURATION;
            return GestaltCosts.ILLUSION_BASE_OPACITY
                    - t * (GestaltCosts.ILLUSION_BASE_OPACITY - GestaltCosts.ILLUSION_FADE_OPACITY);
        } else {
            return GestaltCosts.ILLUSION_FADE_OPACITY;
        }
    }
}
