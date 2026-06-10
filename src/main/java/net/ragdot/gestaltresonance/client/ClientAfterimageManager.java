package net.ragdot.gestaltresonance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only registry of active afterimages. Receives spawn/discard/clear packets and
 * renders each ghost as a violet-tinted translucent copy of its source entity. Other players
 * never see these — they live entirely on the receiving client.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientAfterimageManager {

    private static final Map<Integer, ClientAfterimage> AFTERIMAGES = new LinkedHashMap<>();

    private ClientAfterimageManager() {}

    public static void add(ClientAfterimage afterimage) {
        AFTERIMAGES.put(afterimage.id, afterimage);
    }

    public static void remove(int id) {
        AFTERIMAGES.remove(id);
    }

    public static void clear() {
        AFTERIMAGES.clear();
    }

    public static void onClientLevelTick(LevelTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != event.getLevel()) return;
        Iterator<ClientAfterimage> it = AFTERIMAGES.values().iterator();
        while (it.hasNext()) {
            ClientAfterimage a = it.next();
            if (a.fadeRate > 0f) {
                a.opacity -= a.fadeRate;
                if (a.opacity <= 0f) it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (AFTERIMAGES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Camera camera = event.getCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        for (ClientAfterimage ghost : AFTERIMAGES.values()) {
            EntityModel<LivingEntity> model;
            ResourceLocation texture;
            int packedLight;

            if (ghost.cachedTexture != null && ghost.cachedRenderer != null) {
                @SuppressWarnings("unchecked")
                EntityModel<LivingEntity> m = (EntityModel<LivingEntity>) ghost.cachedRenderer.getModel();
                model = m;
                texture = ghost.cachedTexture;
                packedLight = 0xF000F0; // max light — entity may no longer be in the level
            } else {
                Entity source = level.getEntity(ghost.sourceEntityId);
                if (!(source instanceof LivingEntity living)) continue;
                @SuppressWarnings("unchecked")
                EntityRenderer<LivingEntity> renderer =
                        (EntityRenderer<LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(living);
                if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) continue;
                @SuppressWarnings("unchecked")
                EntityModel<LivingEntity> m = (EntityModel<LivingEntity>) livingRenderer.getModel();
                model = m;
                texture = renderer.getTextureLocation(living);
                packedLight = mc.getEntityRenderDispatcher().getPackedLightCoords(living,
                        event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }

            poseStack.pushPose();
            poseStack.translate(ghost.x - camX, ghost.y - camY, ghost.z - camZ);
            poseStack.scale(-1f, -1f, 1f);
            poseStack.translate(0f, -1.5f, 0f);

            int color = colorWithOpacity(ghost.opacity, ghost.tint);
            model.renderToBuffer(poseStack,
                    buffer.getBuffer(RenderType.entityTranslucentCull(texture)),
                    packedLight, OverlayTexture.NO_OVERLAY, color);

            poseStack.popPose();
        }

        buffer.endBatch();
    }

    private static int colorWithOpacity(float opacity, int tint) {
        int alpha = Math.max(0, Math.min(255, (int)(opacity * 255)));
        return (alpha << 24) | (tint & 0x00FFFFFF);
    }
}
