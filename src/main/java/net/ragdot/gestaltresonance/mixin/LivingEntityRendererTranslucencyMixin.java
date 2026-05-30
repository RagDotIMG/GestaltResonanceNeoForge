package net.ragdot.gestaltresonance.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;
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
    /** Non-null while rendering a SpawnIllusionEntity; stores the packed ARGB tint. */
    private static final ThreadLocal<Integer> illusionArgb = ThreadLocal.withInitial(() -> null);

    /** Alpha = 0x4C ≈ 30% (soul projection). */
    private static final int TRANSLUCENT_ALPHA_PROJECTION = 0x4C << 24;
    /** Alpha = 0x33 ≈ 20% (Phase Out ghost window). */
    private static final int TRANSLUCENT_ALPHA_PHASE_OUT = 0x33 << 24;
    /** Alpha for Phase Court ghost window — sourced from GestaltCosts.PHASE_COURT_GHOST_ALPHA. */
    private static final int TRANSLUCENT_ALPHA_PHASE_COURT = GestaltCosts.PHASE_COURT_GHOST_ALPHA << 24;

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
            } else if (state.isPhaseCourtActive()) {
                projecting.set(Boolean.TRUE);
                translucencyMode.set(3);
            }
        } else if (entity instanceof SpawnIllusionEntity se) {
            if (se.isBodyDoubleMode()) {
                // Body double renders at full opacity — no tint
            } else {
                float alpha = computeIllusionAlpha(se.getAgeTicks());
                int a = Math.round(alpha * 255);
                illusionArgb.set((a << 24) | (0x42 << 16) | (0x12 << 8) | 0x85);
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
        illusionArgb.remove();
    }

    @ModifyArg(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4)
    private int gestaltresonance$modifyColor(int color) {
        Integer illusion = illusionArgb.get();
        if (illusion != null) return illusion;
        if (projecting.get() == Boolean.TRUE) {
            int alpha = switch (translucencyMode.get()) {
                case 2  -> TRANSLUCENT_ALPHA_PHASE_OUT;
                case 3  -> TRANSLUCENT_ALPHA_PHASE_COURT;
                default -> TRANSLUCENT_ALPHA_PROJECTION;
            };
            return alpha | (color & 0x00FFFFFF);
        }
        return color;
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
