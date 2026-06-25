package net.ragdot.gestaltresonance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.ragdot.gestaltresonance.common.network.DominionStateSyncS2C;
import net.ragdot.gestaltresonance.common.network.DominionStoredMobSyncS2C;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the Dominion bubble overlay and jitter VFX on dominated entities.
 *
 * Pre event: cancels normal render, re-renders entity with 4 pulsing-translucent jitter
 * ghost passes plus one crisp opaque center pass, then draws the bubble billboard.
 * Post event: only fires for jitter sub-renders (guard returns immediately).
 */
public final class DominionBubbleRenderer {

    private static final ResourceLocation BUBBLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("gestaltresonance", "textures/entity/dominion_bubble.png");
    private static final RenderType BUBBLE_RENDER_TYPE = RenderType.entityTranslucent(BUBBLE_TEXTURE);

    /** Entity network IDs currently under Dominion — updated by server sync packets. */
    private static final Set<Integer> DOMINATED_IDS = ConcurrentHashMap.newKeySet();

    /** Render-thread guard — prevents the Pre handler from canceling its own sub-renders. */
    private static final Set<Integer> JITTER_RENDERING = new HashSet<>();

    /** Client-side reconstructed entity for the stored passive mob miniature display. */
    private static LivingEntity storedMobDisplay = null;

    private DominionBubbleRenderer() {}

    // ── Sync ─────────────────────────────────────────────────────────────────

    public static void onSync(DominionStateSyncS2C packet) {
        if (packet.active()) {
            DOMINATED_IDS.add(packet.entityId());
        } else {
            DOMINATED_IDS.remove(packet.entityId());
        }
    }

    public static void onStoredMobSync(DominionStoredMobSyncS2C packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (packet.nbt().isEmpty()) {
            storedMobDisplay = null;
        } else {
            Entity loaded = EntityType.loadEntityRecursive(packet.nbt(), mc.level, e -> e);
            storedMobDisplay = loaded instanceof LivingEntity le ? le : null;
        }
    }

    /**
     * Renders a miniature of the stored passive mob in first-person view only.
     * Positioned 0.5 blocks forward and 0.5 blocks to the player's right, at 1/4 scale,
     * with the same jitter, pulsing-alpha, blue-tint, and bubble overlay as dominated mobs.
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (storedMobDisplay == null || mc.level == null) return;

        Camera camera = event.getCamera();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float gameTime = mc.level.getGameTime() + partialTick;

        double yawRad = Math.toRadians(camera.getYRot());
        double pitchRad = Math.toRadians(camera.getXRot());

        double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fwdY = -Math.sin(pitchRad);
        double fwdZ =  Math.cos(yawRad) * Math.cos(pitchRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        // Camera-relative position: 0.6 forward, 0.2 right
        double relX = fwdX * 0.6 + rightX * 0.2;
        double relY = fwdY * 0.6;
        double relZ = fwdZ * 0.6 + rightZ * 0.2;

        // camera.getYRot() + 180f makes the entity face toward the player;
        // rotating with the camera means no apparent spinning from the player's view.
        float displayYaw = camera.getYRot() + 180f;

        // LivingEntityRenderer lerps between yBodyRotO and yBodyRot each frame.
        // These fields are NOT serialized in entity NBT so the loaded entity has
        // mismatched old/new values, causing rapid oscillation that looks like spinning.
        // Force all rotation fields to the same value so the lerp is always stable.
        storedMobDisplay.yBodyRot  = displayYaw;
        storedMobDisplay.yBodyRotO = displayYaw;
        storedMobDisplay.yHeadRot  = displayYaw;
        storedMobDisplay.yHeadRotO = displayYaw;
        storedMobDisplay.setYRot(displayYaw);
        storedMobDisplay.yRotO     = displayYaw;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer =
                (EntityRenderer<Entity>) mc.getEntityRenderDispatcher().getRenderer(storedMobDisplay);

        // Same pulsing alpha + blue tint as dominated hostile mobs
        float alphaMod = Mth.clamp((100f + 40f * Mth.sin(gameTime * 0.12f)) / 255f, 60f / 255f, 140f / 255f);
        ResourceLocation entityTex = renderer.getTextureLocation(storedMobDisplay);
        VertexConsumer sharedVcBase = bufferSource.getBuffer(RenderType.entityTranslucent(entityTex));
        MultiBufferSource alphaSource = rt -> new AlphaVertexConsumer(sharedVcBase, alphaMod, 0.20f, 0.25f, 0.80f);

        float scale = 0.25f;
        // Jitter scaled proportionally with the miniature.
        // Seed capped to 15 bits (0–32767) so adding gameTime*3 stays within float precision.
        float maxOffset = 0.032f * scale;
        float mobSeed = (float)(storedMobDisplay.getUUID().getLeastSignificantBits() & 0x7FFFL);

        // 4 ghost jitter passes
        for (int i = 0; i < 4; i++) {
            float seed = mobSeed + gameTime * 3.0f + i * 17.0f;
            float dx = (float) Math.sin(seed * 1.7f) * maxOffset;
            float dy = (float) Math.sin(seed * 2.3f) * maxOffset * 0.5f;
            float dz = (float) Math.sin(seed * 1.1f) * maxOffset;

            PoseStack ps = new PoseStack();
            applyInverseBob(ps, partialTick);
            ps.translate(relX + dx, relY + dy, relZ + dz);
            ps.scale(scale, scale, scale);
            renderer.render(storedMobDisplay, displayYaw, partialTick, ps, alphaSource, LightTexture.FULL_BRIGHT);
        }

        // Center pass
        PoseStack cps = new PoseStack();
        applyInverseBob(cps, partialTick);
        cps.translate(relX, relY, relZ);
        cps.scale(scale, scale, scale);
        renderer.render(storedMobDisplay, displayYaw, partialTick, cps, alphaSource, LightTexture.FULL_BRIGHT);

        bufferSource.endBatch();

        // Bubble overlay — rendered after endBatch so entity depth is already committed
        renderMiniatureBubble(camera, gameTime, partialTick, bufferSource, relX, relY, relZ, scale);
        bufferSource.endBatch();
    }

    private static void renderMiniatureBubble(Camera camera, float gameTime, float partialTick,
            MultiBufferSource.BufferSource bufferSource,
            double relX, double relY, double relZ, float scale) {

        float bSeed = (float)(storedMobDisplay.getUUID().getLeastSignificantBits() & 0x7FFFL) + gameTime * 3.0f + 99.0f;
        float bShake = 0.010f * scale;

        // Entity center in camera-relative space
        double cx = relX;
        double cy = relY + storedMobDisplay.getBbHeight() * scale / 2.0;
        double cz = relZ;

        // Push bubble toward camera by scaled half-width + margin (mirrors hostile renderBubble)
        double camDist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        float frontPush = storedMobDisplay.getBbWidth() * scale / 2.0f + 0.015f;
        double pushX = 0, pushY = 0, pushZ = 0;
        if (camDist > 0.001) {
            pushX = (-cx / camDist) * frontPush;
            pushY = (-cy / camDist) * frontPush;
            pushZ = (-cz / camDist) * frontPush;
        }

        double bx = cx + pushX + (float) Math.sin(bSeed * 1.7f) * bShake;
        double by = cy + pushY + (float) Math.sin(bSeed * 2.3f) * bShake * 0.5f;
        double bz = cz + pushZ + (float) Math.sin(bSeed * 1.1f) * bShake;

        int alpha = (int) Mth.clamp(160f + 60f * Mth.sin(gameTime * 0.12f), 100f, 220f);
        float bubbleScale = (Math.max(storedMobDisplay.getBbWidth(), storedMobDisplay.getBbHeight()) * 0.9f + 0.5f) * scale;

        PoseStack fresh = new PoseStack();
        applyInverseBob(fresh, partialTick);
        fresh.translate(bx, by, bz);
        fresh.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        fresh.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        if (storedMobDisplay.getBbHeight() <= 1.9f) {
            fresh.mulPose(Axis.ZP.rotationDegrees(90f));
        }
        fresh.scale(bubbleScale, bubbleScale, bubbleScale);

        VertexConsumer vc = bufferSource.getBuffer(BUBBLE_RENDER_TYPE);
        Matrix4f m = fresh.last().pose();
        float h = 0.5f;
        vc.addVertex(m, -h,  h, 0f).setColor(255, 255, 255, alpha).setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f);
        vc.addVertex(m, -h, -h, 0f).setColor(255, 255, 255, alpha).setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f);
        vc.addVertex(m,  h, -h, 0f).setColor(255, 255, 255, alpha).setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f);
        vc.addVertex(m,  h,  h, 0f).setColor(255, 255, 255, alpha).setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f);
    }

    /**
     * Prepends the inverse of GameRenderer's bobHurt + bobView transforms to ps, so that
     * entities rendered with a fresh PoseStack are immune to the view-bob that is already
     * baked into the GL modelview matrix at AFTER_ENTITIES stage.
     *
     * bobHurt is always applied; bobView is only applied when the option is enabled.
     * Order: inverse(bobView) first, then inverse(bobHurt) — mirrors the reverse of
     * GameRenderer.renderLevel() which calls bobHurt then bobView.
     */
    private static void applyInverseBob(PoseStack ps, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Inverse of bobView (applied last in GameRenderer, so inverted first here)
        if (mc.options.bobView().get()) {
            float walkF = player.walkDist - player.walkDistO;
            float f1 = -(player.walkDist + walkF * partialTick);
            float f2 = Mth.lerp(partialTick, player.oBob, player.bob);
            float sinWalk = Mth.sin(f1 * Mth.PI);
            float cosWalk  = Mth.cos(f1 * Mth.PI);
            float cosWalk2 = Mth.cos(f1 * Mth.PI - 0.2F);
            // forward: translate(sin*f2*0.5, -|cos*f2|, 0) → ZP(sin*f2*3) → XP(|cos2*f2|*5)
            // inverse (reverse order, negate each):
            ps.mulPose(Axis.XP.rotationDegrees(-(float) Math.abs(cosWalk2 * f2) * 5.0F));
            ps.mulPose(Axis.ZP.rotationDegrees(-(sinWalk * f2 * 3.0F)));
            ps.translate(-(sinWalk * f2 * 0.5F), (float) Math.abs(cosWalk * f2), 0.0F);
        }

        // Inverse of bobHurt (applied first in GameRenderer, so inverted second here)
        float hurtF = (float) player.hurtTime - partialTick;
        if (!player.isDeadOrDying() && hurtF >= 0.0F && player.hurtDuration > 0) {
            hurtF /= (float) player.hurtDuration;
            float sinHurt = Mth.sin(hurtF * hurtF * hurtF * hurtF * Mth.PI);
            float hurtDir = player.getHurtDir();
            // forward: YP(-hurtDir) → ZP(-sin*14) → YP(hurtDir)
            // inverse: YP(-hurtDir) → ZP(sin*14) → YP(hurtDir)
            ps.mulPose(Axis.YP.rotationDegrees(-hurtDir));
            ps.mulPose(Axis.ZP.rotationDegrees(sinHurt * 14.0F));
            ps.mulPose(Axis.YP.rotationDegrees(hurtDir));
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        int entityId = entity.getId();

        if (JITTER_RENDERING.contains(entityId)) return;
        if (!DOMINATED_IDS.contains(entityId)) return;

        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        float partialTick = event.getPartialTick();

        double ex = Mth.lerp(partialTick, entity.xo, entity.getX());
        double ey = Mth.lerp(partialTick, entity.yo, entity.getY());
        double ez = Mth.lerp(partialTick, entity.zo, entity.getZ());
        Vec3 camPos = camera.getPosition();
        double relX = ex - camPos.x;
        double relY = ey - camPos.y;
        double relZ = ez - camPos.z;

        float gameTime = mc.level.getGameTime() + partialTick;
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        int packedLight = event.getPackedLight();
        float bodyRot = entity.yBodyRot;

        @SuppressWarnings("unchecked")
        EntityRenderer<LivingEntity> renderer =
                (EntityRenderer<LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(entity);

        // Pulsing alpha in sync with the bubble, deep dark blue tint
        float alphaMod = Mth.clamp((100f + 40f * Mth.sin(gameTime * 0.12f)) / 255f, 60f / 255f, 140f / 255f);

        ResourceLocation entityTex = renderer.getTextureLocation(entity);
        VertexConsumer sharedVcBase = bufferSource.getBuffer(RenderType.entityTranslucent(entityTex));
        MultiBufferSource alphaSource = rt -> new AlphaVertexConsumer(sharedVcBase, alphaMod, 0.20f, 0.25f, 0.80f);

        JITTER_RENDERING.add(entityId);
        try {
            // 4 ghost passes: translucent, pulsing alpha, sin-based jitter offsets
            int passes = 4;
            float maxOffset = 0.040f;
            for (int i = 0; i < passes; i++) {
                float seed = entityId * 1337.0f + gameTime * 3.0f + i * 17.0f;
                float dx = (float) Math.sin(seed * 1.7f) * maxOffset;
                float dy = (float) Math.sin(seed * 2.3f) * maxOffset * 0.5f;
                float dz = (float) Math.sin(seed * 1.1f) * maxOffset;

                PoseStack ps = new PoseStack();
                ps.translate(relX + dx, relY + dy, relZ + dz);
                renderer.render(entity, bodyRot, partialTick, ps, alphaSource, packedLight);
            }

            // Center pass: same alphaSource — confirms opacity applies to the main body too
            PoseStack ps = new PoseStack();
            ps.translate(relX, relY, relZ);
            renderer.render(entity, bodyRot, partialTick, ps, alphaSource, packedLight);

        } finally {
            JITTER_RENDERING.remove(entityId);
        }

        renderBubble(entity, camera, partialTick, mc, bufferSource, packedLight);
    }

    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (JITTER_RENDERING.contains(entity.getId())) return;
        if (!DOMINATED_IDS.contains(entity.getId())) return;

        // Safety fallback — Pre cancels for dominated entities so Post never fires for them
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        renderBubble(entity, mc.getEntityRenderDispatcher().camera,
                event.getPartialTick(), mc, event.getMultiBufferSource(), event.getPackedLight());
    }

    private static void renderBubble(LivingEntity entity, Camera camera, float partialTick,
                                     Minecraft mc, MultiBufferSource bufferSource, int packedLight) {
        double ex = Mth.lerp(partialTick, entity.xo, entity.getX());
        double ey = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() / 2.0;
        double ez = Mth.lerp(partialTick, entity.zo, entity.getZ());

        Vec3 camPos = camera.getPosition();
        double dx = camPos.x - ex;
        double dy = camPos.y - ey;
        double dz = camPos.z - ez;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.001) return;

        float frontOffset = entity.getBbWidth() / 2.0f + 0.05f;
        double bx = ex + dx / dist * frontOffset - camPos.x;
        double by = ey + dy / dist * frontOffset - camPos.y;
        double bz = ez + dz / dist * frontOffset - camPos.z;

        float gameTime = mc.level.getGameTime() + partialTick;
        float bSeed = entity.getId() * 1337.0f + gameTime * 3.0f + 99.0f;
        float bShake = 0.010f;
        bx += (float) Math.sin(bSeed * 1.7f) * bShake;
        by += (float) Math.sin(bSeed * 2.3f) * bShake * 0.5f;
        bz += (float) Math.sin(bSeed * 1.1f) * bShake;
        int alpha = (int) Mth.clamp(160f + 60f * Mth.sin(gameTime * 0.12f), 100f, 220f);
        float scale = Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.9f + 0.5f;

        PoseStack fresh = new PoseStack();
        fresh.translate(bx, by, bz);
        fresh.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        fresh.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        // Wider-than-tall mobs (spider, cave spider) get the bubble rotated on its side
        if (entity.getBbWidth() > entity.getBbHeight()) {
            fresh.mulPose(Axis.ZP.rotationDegrees(90f));
        }
        fresh.scale(scale, scale, scale);

        VertexConsumer vc = bufferSource.getBuffer(BUBBLE_RENDER_TYPE);
        Matrix4f m = fresh.last().pose();
        float h = 0.5f;
        vc.addVertex(m, -h,  h, 0f).setColor(255, 255, 255, alpha).setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0f, 0f, 1f);
        vc.addVertex(m, -h, -h, 0f).setColor(255, 255, 255, alpha).setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0f, 0f, 1f);
        vc.addVertex(m,  h, -h, 0f).setColor(255, 255, 255, alpha).setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0f, 0f, 1f);
        vc.addVertex(m,  h,  h, 0f).setColor(255, 255, 255, alpha).setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0f, 0f, 1f);
    }

    // ── VertexConsumer alpha wrapper ──────────────────────────────────────────

    /**
     * Forwards all vertex data to a delegate consumer, scaling RGBA on setColor.
     * RGB tint multipliers shift the mob's colors (e.g. deep dark blue: 0.20, 0.25, 0.80).
     */
    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;
        private final float tintR, tintG, tintB;

        AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
            this(delegate, alpha, 1f, 1f, 1f);
        }

        AlphaVertexConsumer(VertexConsumer delegate, float alpha, float tintR, float tintG, float tintB) {
            this.delegate = delegate;
            this.alpha = alpha;
            this.tintR = tintR;
            this.tintG = tintG;
            this.tintB = tintB;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor((int)(r * tintR), (int)(g * tintG), (int)(b * tintB), (int)(a * alpha)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
