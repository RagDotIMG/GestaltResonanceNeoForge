package net.ragdot.gestaltresonance.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses "gear" render layers (armor, held items, elytra, cape, custom head) on a player
 * who is currently soul-projecting. The body double renders these instead, so the projecting
 * player's soul appears as a clean translucent silhouette.
 *
 * Other layers (notably {@code GestaltPlayerLayer}, which extends {@code RenderLayer} directly
 * and is not in the suppression class set) continue to render — the gestalt visually accompanies
 * the soul, not the body double.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererSkipGearMixin {

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private void gestaltresonance$skipGearLayers(RenderLayer<?, ?> layer,
                                                  PoseStack pose, MultiBufferSource buf, int light,
                                                  Entity entity,
                                                  float f1, float f2, float f3,
                                                  float f4, float f5, float f6) {
        if (entity instanceof Player p) {
            PlayerGestaltState state = p.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.isSoulProjecting() && gestaltresonance$isGearLayer(layer)) {
                return;
            }
        }
        // Fall through to the original render call
        @SuppressWarnings({"rawtypes", "unchecked"})
        RenderLayer raw = layer;
        raw.render(pose, buf, light, entity, f1, f2, f3, f4, f5, f6);
    }

    @Unique
    private static boolean gestaltresonance$isGearLayer(RenderLayer<?, ?> layer) {
        // PlayerItemInHandLayer extends ItemInHandLayer, so the latter check covers both.
        return layer instanceof HumanoidArmorLayer<?, ?, ?>
                || layer instanceof ItemInHandLayer<?, ?>
                || layer instanceof ElytraLayer<?, ?>
                || layer instanceof CapeLayer
                || layer instanceof CustomHeadLayer<?, ?>;
    }
}
