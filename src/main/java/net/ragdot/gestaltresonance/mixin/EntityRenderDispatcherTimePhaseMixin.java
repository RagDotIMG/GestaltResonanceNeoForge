package net.ragdot.gestaltresonance.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses both model and shadow rendering for entities tracked by the local player's
 * Time Phase. RenderLivingEvent.Pre cancels only the model — the shadow is drawn separately
 * by the dispatcher, so we early-return at the dispatcher level instead.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherTimePhaseMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void gestaltresonance$suppressTimePhase(
            E entity, double x, double y, double z, float rotYaw, float partialTicks,
            PoseStack pose, MultiBufferSource buf, int packedLight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isTimePhaseActive()) return;
        if (entity == mc.player) return;
        if (entity.getId() == state.getTimePhaseBodyDoubleId()) return;
        int[] tracked = state.getTimePhaseTrackedIds();
        int count = state.getTimePhaseTrackedCount();
        for (int i = 0; i < count; i++) {
            if (tracked[i] == entity.getId()) {
                ci.cancel();
                return;
            }
        }
    }
}
