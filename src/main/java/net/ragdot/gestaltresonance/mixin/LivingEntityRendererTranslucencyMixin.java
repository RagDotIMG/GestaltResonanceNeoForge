package net.ragdot.gestaltresonance.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders soul-projecting players at ~30% opacity by modifying the color int passed to
 * {@code Model.renderToBuffer}. The alpha byte (high 8 bits) of {@code color} blends if
 * the active render type supports it.
 *
 * A {@link ThreadLocal} flag is set on render() entry and cleared on exit so the
 * {@code @ModifyArg} can cheaply check whether the entity currently being rendered is a
 * soul-projecting player without re-querying the attachment per-call.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTranslucencyMixin {

    private static final ThreadLocal<Boolean> projecting = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 1 = soul projecting (~30%), 2 = phase out (~20%), 0 = none. */
    private static final ThreadLocal<Integer> translucencyMode = ThreadLocal.withInitial(() -> 0);

    /** Alpha = 0x4C ≈ 30% (soul projection). */
    private static final int TRANSLUCENT_ALPHA_PROJECTION = 0x4C << 24;
    /** Alpha = 0x33 ≈ 20% (Phase Out ghost window). */
    private static final int TRANSLUCENT_ALPHA_PHASE_OUT = 0x33 << 24;

    @Inject(method = "render", at = @At("HEAD"))
    private void gestaltresonance$preRender(LivingEntity entity, float yaw, float partial,
                                            com.mojang.blaze3d.vertex.PoseStack pose,
                                            net.minecraft.client.renderer.MultiBufferSource buf,
                                            int light, CallbackInfo ci) {
        if (entity instanceof Player p) {
            PlayerGestaltState state = p.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.isSoulProjecting()) {
                projecting.set(Boolean.TRUE);
                translucencyMode.set(1);
            } else if (state.isPhaseOutActive()) {
                projecting.set(Boolean.TRUE);
                translucencyMode.set(2);
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void gestaltresonance$postRender(LivingEntity entity, float yaw, float partial,
                                             com.mojang.blaze3d.vertex.PoseStack pose,
                                             net.minecraft.client.renderer.MultiBufferSource buf,
                                             int light, CallbackInfo ci) {
        projecting.remove();
        translucencyMode.remove();
    }

    @ModifyArg(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4)
    private int gestaltresonance$modifyColor(int color) {
        if (projecting.get() == Boolean.TRUE) {
            int alpha = translucencyMode.get() == 2
                    ? TRANSLUCENT_ALPHA_PHASE_OUT
                    : TRANSLUCENT_ALPHA_PROJECTION;
            return alpha | (color & 0x00FFFFFF);
        }
        return color;
    }
}
