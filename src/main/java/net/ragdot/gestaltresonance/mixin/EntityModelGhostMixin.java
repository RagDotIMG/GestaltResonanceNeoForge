package net.ragdot.gestaltresonance.mixin;

import net.minecraft.client.model.HierarchicalModel;
import net.ragdot.gestaltresonance.client.GhostRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Applies ghost/translucency alpha to every {@code ModelPart.render} call within
 * {@link HierarchicalModel#renderToBuffer}, covering render layers (armor, gestalt model,
 * modded wearables) that {@link LivingEntityRendererTranslucencyMixin}'s outer
 * {@code @ModifyArg} misses because those calls originate inside sub-methods.
 *
 * {@link GhostRenderState} ThreadLocals are set and cleared by
 * {@link LivingEntityRendererTranslucencyMixin} for the full duration of each
 * {@code LivingEntityRenderer.render()} call.
 */
@Mixin(HierarchicalModel.class)
public abstract class EntityModelGhostMixin {

    @ModifyArg(
            method = "renderToBuffer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4)
    private int gestaltresonance$modifyLayerColor(int color) {
        int alpha = GhostRenderState.resolveAlpha();
        if (alpha == -1) return color;
        return alpha | (color & 0x00FFFFFF);
    }
}
