package net.ragdot.gestaltresonance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.client.gestalt.GestaltModel;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player render layer that draws the summoned Gestalt behind and slightly to the right of the player.
 * Rotates with the player's body yaw (not head yaw) and uses partial tick interpolation.
 *
 * Summon/unsummon VFX: multi-pass jitter + alpha fade driven by per-gestalt summon progress.
 *
 * To add new Gestalt appearances:
 * 1. Create a new model class (see AmenBreakModel)
 * 2. Add a texture at assets/gestaltresonance/textures/gestalt/<id>.png
 * 3. Add a branch in this layer's render() to pick the right model+texture based on gestaltId
 */
public class GestaltPlayerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Fallback texture if the gestalt has no skin definitions registered. */
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "textures/gestalt/amen_break/default.png");

    /** Returns the texture to use for the given player's currently selected skin, with fallbacks. */
    public static ResourceLocation textureFor(PlayerGestaltState state) {
        ResourceLocation gestaltId = state.getGestaltId();
        ResourceLocation selected = state.getSelectedSkin();
        net.ragdot.gestaltresonance.common.skin.GestaltSkin skin = null;
        if (selected != null && !selected.equals(PlayerGestaltState.NONE)) {
            skin = net.ragdot.gestaltresonance.common.skin.GestaltSkinRegistry.getSkin(gestaltId, selected);
        }
        if (skin == null) {
            skin = net.ragdot.gestaltresonance.common.skin.GestaltSkinRegistry.getDefaultSkin(gestaltId);
        }
        return skin != null ? skin.texture() : FALLBACK_TEXTURE;
    }

    // Offset behind the player (negative Z = behind) and slightly to the right (positive X)
    private static final float OFFSET_X = 0.6F;
    private static final float OFFSET_Y = 0.0F;
    private static final float OFFSET_Z = 0.8F;

    // Fraction of the remaining yaw difference to close each tick (0=no smoothing, 1=instant snap).
    // At 20 ticks/s, 0.10 means ~90% of a turn takes ~22 ticks (~1.1 s).
    private static final float YAW_SMOOTH_FACTOR = 0.20f;

    // Per-player smoothed body yaw. Stores {previousTickSmoothed, currentTickSmoothed}.
    // Updated once per game tick; interpolated in render using partialTick.
    private static final Map<UUID, float[]> smoothedYawMap = new HashMap<>();

    // ── Fall-break impact shake (client-side cosmetic) ──
    /** Game tick at which a fall break shake started, per player UUID. */
    private static final Map<UUID, Long> impactShakeStartTick = new HashMap<>();
    /** Duration of the shake decay in ticks (ranges within 5–8 per spec). */
    private static final int IMPACT_SHAKE_DURATION = 7;
    /** Peak shake amplitude in blocks at impact instant; decays linearly to 0. */
    private static final float IMPACT_SHAKE_AMPLITUDE = 0.18f;

    /** Mark the given player's gestalt as just-impacted; renderers will shake it briefly. */
    public static void triggerImpactShake(UUID playerUuid, long currentTick) {
        impactShakeStartTick.put(playerUuid, currentTick);
    }

    /**
     * Returns the current XYZ shake offset for the given player, or [0,0,0] if no shake is active.
     * The shake decays linearly over {@link #IMPACT_SHAKE_DURATION} ticks. The output array is a
     * fresh allocation per call — small enough that this is fine for its rare use.
     */
    public static float[] getShakeOffset(UUID playerUuid, long currentTick, float partialTick) {
        Long start = impactShakeStartTick.get(playerUuid);
        if (start == null) return new float[]{0f, 0f, 0f};
        float elapsed = (currentTick - start) + partialTick;
        if (elapsed >= IMPACT_SHAKE_DURATION) {
            impactShakeStartTick.remove(playerUuid);
            return new float[]{0f, 0f, 0f};
        }
        float intensity = 1f - elapsed / IMPACT_SHAKE_DURATION;
        float seed = elapsed * 11.7f + playerUuid.hashCode() * 0.001f;
        float dx = (float) Math.sin(seed * 7.3f) * IMPACT_SHAKE_AMPLITUDE * intensity;
        float dy = (float) Math.sin(seed * 9.1f) * IMPACT_SHAKE_AMPLITUDE * 0.5f * intensity;
        float dz = (float) Math.sin(seed * 8.5f) * IMPACT_SHAKE_AMPLITUDE * intensity;
        return new float[]{dx, dy, dz};
    }

    // Ledge-grab offsets: gestalt moves to the wall side of the player.
    private static final float LEDGE_GRAB_OFFSET_X = OFFSET_X;
    private static final float LEDGE_GRAB_OFFSET_Y = 0.4F;
    private static final float LEDGE_GRAB_OFFSET_Z = .5F;

    // Guard offsets: gestalt moves in front of the player, following head yaw.
    // In model space (after 180° body rotation): +X = camera-left, -Z = player-front.
    private static final float GUARD_OFFSET_X = 0.5F;
    private static final float GUARD_OFFSET_Z = -0.5F;

    // Attack offsets: 0.5 blocks further forward and 0.2 blocks further left than guard.
    private static final float ATTACK_OFFSET_X = 0.7F;
    private static final float ATTACK_OFFSET_Z = -1.0F;


    private final Map<ResourceLocation, GestaltModel> models;

    private GestaltModel resolveModel(PlayerGestaltState state) {
        GestaltModel m = models.get(state.getGestaltId());
        return m != null ? m : models.values().iterator().next();
    }

    /**
     * Call once per game tick per player. When the gestalt is idle, advances the smoothed body
     * yaw toward the player's actual body yaw so the gestalt eases into turns instead of
     * snapping. When not idle (e.g. ledge grab) the smoothed yaw is snapped to the current
     * body yaw so it doesn't drift while the override is active.
     */
    public static void tickSmoothedYaw(Player player) {
        float targetYaw = player.yBodyRot;
        UUID uuid = player.getUUID();
        float[] yaws = smoothedYawMap.get(uuid);
        if (yaws == null) {
            smoothedYawMap.put(uuid, new float[]{targetYaw, targetYaw});
            return;
        }

        net.ragdot.gestaltresonance.common.PlayerGestaltState state =
                player.getData(net.ragdot.gestaltresonance.common.GestaltAttachments.PLAYER_GESTALT_STATE.get());

        float newSmoothed;
        if (state.isIdle() || state.getAction() == GestaltAction.THROW) {
            float current = yaws[1];
            // Shortest-path delta to avoid spinning the long way around
            float delta = Mth.wrapDegrees(targetYaw - current);
            newSmoothed = current + delta * YAW_SMOOTH_FACTOR;
        } else {
            // Not idle — snap to current body yaw so smoothing resumes cleanly after the action ends
            newSmoothed = targetYaw;
        }
        yaws[0] = yaws[1];
        yaws[1] = newSmoothed;
    }

    public GestaltPlayerLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer,
            Map<ResourceLocation, GestaltModel> models) {
        super(renderer);
        this.models = models;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        float progress = state.getSummonProgress(partialTick);

        // Fully hidden — don't render at all
        if (progress <= 0.001f) return;

        GestaltAction action = state.getAction();
        // Throw and charged-strike travel are rendered in world space by GestaltFirstPersonRenderer.
        if (action == GestaltAction.THROW) return;
        if (action == GestaltAction.CHARGED_STRIKE_TRAVEL) return;
        // Charged-strike HIT_3 (the strike phase) renders at the target via the world-space path too.
        if (action == GestaltAction.HIT_3 && state.getChargedStrikeTargetEntityId() >= 0) return;

        boolean grabbing = action == GestaltAction.LEDGE_GRAB;
        boolean wallSliding = action == GestaltAction.WALL_SLIDE;
        boolean guarding = action == GestaltAction.GUARD || action == GestaltAction.CHARGED_STRIKE_WINDUP;
        boolean attacking = action == GestaltAction.HIT_1 || action == GestaltAction.HIT_2 || action == GestaltAction.HIT_3
                || action == GestaltAction.POWER_1G_WINDUP;
        boolean mining = !grabbing && !wallSliding && !guarding && !attacking && isLocalPlayerMining(player);

        float xOffset = (grabbing || wallSliding) ? LEDGE_GRAB_OFFSET_X : attacking ? ATTACK_OFFSET_X : (guarding || mining) ? GUARD_OFFSET_X : OFFSET_X;
        float yOffset = (grabbing || wallSliding) ? LEDGE_GRAB_OFFSET_Y : OFFSET_Y;
        float zOffset = (grabbing || wallSliding) ? LEDGE_GRAB_OFFSET_Z : attacking ? ATTACK_OFFSET_Z : (guarding || mining) ? GUARD_OFFSET_Z : OFFSET_Z;

        float[] shake = getShakeOffset(player.getUUID(), player.level().getGameTime(), partialTick);
        xOffset += shake[0];
        yOffset += shake[1];
        zOffset += shake[2];

        GestaltModel model = resolveModel(state);
        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // The render layer is already in player-body-yaw-rotated space.
        // yawCorrection rotates from body-yaw space into the gestalt's target facing.
        //
        // - LEDGE_GRAB: lock to wall face direction.
        // - GUARD:      follow head yaw (netHeadYaw is head relative to body).
        // - IDLE:       smoothed body yaw that lags turns.
        float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float yawCorrection = 0f;
        if (grabbing && state.getLedgeFace() != null) {
            float ledgeYaw = directionToYaw(state.getLedgeFace());
            yawCorrection = ledgeYaw - bodyYaw;
        } else if (wallSliding && state.getWallSlideFace() != null) {
            float slideYaw = directionToYaw(state.getWallSlideFace());
            yawCorrection = slideYaw - bodyYaw;
        } else if (attacking) {
            yawCorrection = netHeadYaw + 20F;
        } else if (guarding || mining) {
            yawCorrection = netHeadYaw;
        } else if (state.isIdle()) {
            // Skip smoothing correction in GUI contexts (inventory screen etc.) where vanilla
            // temporarily overrides yBodyRot to face the camera, making world-space smoothedYaw
            // produce a wrong offset. In those cases yawCorrection=0 is correct (gestalt stays
            // at the fixed behind-player offset as displayed on screen).
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null || mc.player != player) {
                float[] yaws = smoothedYawMap.get(player.getUUID());
                float smoothedYaw = (yaws != null) ? Mth.lerp(partialTick, yaws[0], yaws[1]) : bodyYaw;
                yawCorrection = smoothedYaw - bodyYaw;
            }
        }

        // Pitch correction: tilt gestalt up/down to follow head pitch when guarding/attacking.
        // Positive headPitch = looking down; XP by +headPitch tilts local +Z toward -Y (downward).
        float pitchCorrection = (guarding || mining || attacking) ? headPitch : 0f;

        // Fully summoned — render normally without VFX overhead
        if (progress >= 0.999f) {
            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(textureFor(state)));
            poseStack.pushPose();
            if (yawCorrection != 0) {
                poseStack.mulPose(Axis.YP.rotationDegrees(yawCorrection));
            }
            if (pitchCorrection != 0) {
                poseStack.mulPose(Axis.XP.rotationDegrees(pitchCorrection));
            }
            poseStack.translate(xOffset, yOffset, zOffset);
            renderGestaltModel(poseStack, vc, packedLight, 0.9f, model);
            poseStack.popPose();
            return;
        }

        renderGestaltWithSummonVfx(poseStack, bufferSource, packedLight, player, partialTick,
                progress, xOffset, yOffset, zOffset, yawCorrection, pitchCorrection, model);
    }

    /**
     * Renders the gestalt with summon/unsummon VFX: multi-pass jitter + alpha fade.
     *
     * @param progress  summon progress in [0..1]; 0 = hidden, 1 = fully visible
     * @param zOffset   Z offset (changes during ledge grab)
     */
    private void renderGestaltWithSummonVfx(PoseStack poseStack, MultiBufferSource bufferSource,
                                            int packedLight, AbstractClientPlayer player,
                                            float partialTick, float progress, float xOffset,
                                            float yOffset, float zOffset, float yawCorrection,
                                            float pitchCorrection, GestaltModel model) {

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean crashing = state.isCrashingOut();

        float intensity = 1.0f - progress;

        // Crash VFX: more passes and wider jitter offset than a normal dismiss
        int passes = crashing
                ? Mth.clamp(1 + (int) (intensity * 11.0f), 1, 12)
                : Mth.clamp(1 + (int) (intensity * 6.0f), 1, 7);

        float maxOffset = crashing
                ? 0.06f + intensity * 0.22f
                : 0.03f + intensity * 0.10f;

        // Base alpha tracks progress directly
        float baseAlpha = progress;

        // Use entity id as a stable seed component (no per-frame Random allocation)
        int gestaltId = player.getId();
        float gameTime = player.level().getGameTime();

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(textureFor(state)));

        // --- Jitter passes: each slightly offset with reduced alpha ---
        for (int i = 0; i < passes; i++) {
            // Deterministic seed from gestalt id, time, and pass index
            float seed = gestaltId * 1337.0f + (gameTime + partialTick) * 3.0f + i * 17.0f;

            float dx = (float) Math.sin(seed * 1.7f) * maxOffset;
            float dy = (float) Math.sin(seed * 2.3f) * maxOffset * 0.5f;  // less vertical jitter
            float dz = (float) Math.sin(seed * 1.1f) * maxOffset;

            // Alpha per pass: spread baseAlpha across all passes with a 0.35 factor
            float alpha = baseAlpha * (0.35f / passes);

            poseStack.pushPose();
            if (yawCorrection != 0) {
                poseStack.mulPose(Axis.YP.rotationDegrees(yawCorrection));
            }
            if (pitchCorrection != 0) {
                poseStack.mulPose(Axis.XP.rotationDegrees(pitchCorrection));
            }
            poseStack.translate(xOffset + dx, yOffset + dy, zOffset + dz);
            renderGestaltModel(poseStack, vertexConsumer, packedLight, alpha, model);
            poseStack.popPose();
        }

        // --- Final crisp pass: only when progress is past the initial flicker stage ---
        if (progress > 0.15f) {
            poseStack.pushPose();
            if (yawCorrection != 0) {
                poseStack.mulPose(Axis.YP.rotationDegrees(yawCorrection));
            }
            if (pitchCorrection != 0) {
                poseStack.mulPose(Axis.XP.rotationDegrees(pitchCorrection));
            }
            poseStack.translate(xOffset, yOffset, zOffset);
            renderGestaltModel(poseStack, vertexConsumer, packedLight, baseAlpha, model);
            poseStack.popPose();
        }
    }

    private static boolean isLocalPlayerMining(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player) {
            // Local player: detect via input so the layer reflects the local state with no lag.
            if (!mc.options.keyAttack.isDown()) return false;
            if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return false;
            PlayerGestaltState layerState = player.getData(net.ragdot.gestaltresonance.common.GestaltAttachments.PLAYER_GESTALT_STATE.get());
            return Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(player.getEyePosition()) <= GestaltCosts.mineRangeFor(layerState);
        }
        // Remote players: use server-synced state.
        return player.getData(net.ragdot.gestaltresonance.common.GestaltAttachments.PLAYER_GESTALT_STATE.get()).isMining();
    }

    /**
     * Convert the ledge face Direction to the yaw the gestalt should face.
     * ledgeFace is the outward normal FROM the wall TOWARD the player, so the
     * gestalt must face the opposite direction (into the wall).
     *   face=NORTH → player hangs on north side → block is south → gestalt faces south (0°)
     *   face=SOUTH → player hangs on south side → block is north → gestalt faces north (180°)
     *   face=WEST  → player hangs on west side  → block is east  → gestalt faces east  (-90°)
     *   face=EAST  → player hangs on east side  → block is west  → gestalt faces west  (90°)
     */
    private static float directionToYaw(Direction face) {
        return switch (face) {
            case NORTH -> 0;
            case SOUTH -> 180;
            case WEST  -> -90;
            case EAST  -> 90;
            default    -> 0;
        };
    }

    private void renderGestaltModel(PoseStack poseStack, VertexConsumer vertexConsumer,
                                    int packedLight, float alpha, GestaltModel model) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        if (a <= 0) return;
        int color = (a << 24) | 0x00FFFFFF;  // ARGB: alpha + white
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);
    }
}
