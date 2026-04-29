package net.ragdot.gestaltresonance.client.gestalt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all gestalt models. Handles the shared animation state machine so
 * concrete gestalts only need to declare their {@link AnimationDefinition}s.
 *
 * Animation priority: intro → guard (if active) → idle (looping fallback).
 * The idle state is always updated for phase continuity across guard transitions.
 */
public abstract class GestaltModel extends HierarchicalModel<AbstractClientPlayer> {

    /** One-shot entrance animation, replayed on every summon. Required. */
    protected abstract AnimationDefinition introAnimation();

    /** Looping default-pose animation. Required. */
    protected abstract AnimationDefinition idleAnimation();

    /** Optional — return null if this gestalt doesn't support guarding. */
    @Nullable protected AnimationDefinition guardAnimation() { return null; }

    /** Optional — return null if this gestalt doesn't support throwing. */
    @Nullable protected AnimationDefinition throwAnimation() { return null; }

    /** Optional — return null if this gestalt doesn't support a hit reaction. */
    @Nullable protected AnimationDefinition hitAnimation() { return null; }

    /** Optional — return null if this gestalt doesn't support a ledge grab pose. */
    @Nullable protected AnimationDefinition grabAnimation() { return null; }

    /** Optional — return null if this gestalt doesn't support a mining animation. */
    @Nullable protected AnimationDefinition miningAnimation() { return null; }

    /** Optional — return null if this gestalt doesn't support the first melee hit. */
    @Nullable protected AnimationDefinition hit1Animation() { return null; }

    /** Optional — return null if this gestalt doesn't support the second melee hit. */
    @Nullable protected AnimationDefinition hit2Animation() { return null; }

    /** Optional — return null if this gestalt doesn't support the third melee hit. */
    @Nullable protected AnimationDefinition hit3Animation() { return null; }

    // Static so chain transitions from the network handler can reset animation state
    // for any GestaltModel instance (one per gestalt subclass) sharing the same player.
    private static final Map<UUID, AnimData> perPlayer = new HashMap<>();

    /**
     * Force-reset the hit-chain animation state for a player. Called by the network
     * handler whenever the action transitions out of HIT_1/2/3, regardless of what
     * comes next. This guarantees a clean restart on the next chain even if the
     * client never observes the IDLE state in {@link #setupAnim} (race condition
     * where IDLE and a fresh HIT_1 packet are processed in the same tick).
     */
    public static void notifyChainEnd(UUID playerUuid) {
        AnimData data = perPlayer.get(playerUuid);
        if (data == null) return;
        if (data.hitState.isStarted()) data.hitState.stop();
        data.prevHitAction = GestaltAction.IDLE;
    }

    @Override
    public final void setupAnim(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean summoned = state.isSummoned();

        AnimData data = perPlayer.get(player.getUUID());
        if (data == null) {
            data = new AnimData();
            data.wasSummoned = summoned;
            perPlayer.put(player.getUUID(), data);
        }

        // Rising edge of summoned → kick off a fresh intro
        if (summoned && !data.wasSummoned) {
            data.introState.start((int) ageInTicks);
            data.introStartedAt = ageInTicks;
        }
        data.wasSummoned = summoned;

        AnimationDefinition intro = introAnimation();
        float introLenTicks = intro.lengthInSeconds() * 20.0F;
        boolean introActive = data.introState.isStarted()
                && data.introStartedAt >= 0
                && (ageInTicks - data.introStartedAt) < introLenTicks;

        if (introActive) {
            data.introState.updateTime(ageInTicks, 1.0F);
            this.animate(data.introState, intro, ageInTicks);
            return;
        }

        if (data.introState.isStarted()) {
            data.introState.stop();
        }

        // Ledge grab: static pose overrides everything except intro
        boolean ledgeGrabbing = state.isLedgeGrabbing();
        AnimationDefinition grabAnim = grabAnimation();
        if (ledgeGrabbing && grabAnim != null) {
            if (!data.grabState.isStarted()) {
                data.grabState.start((int) ageInTicks);
            }
            data.grabState.updateTime(ageInTicks, 1.0F);
            this.animate(data.grabState, grabAnim, ageInTicks);
            data.wasLedgeGrabbing = true;
            return;
        }
        if (data.wasLedgeGrabbing && data.grabState.isStarted()) {
            data.grabState.stop();
        }
        data.wasLedgeGrabbing = ledgeGrabbing;

        // Hit chain: plays whichever hit animation matches the current action
        GestaltAction hitAction = state.getAction();
        boolean isHitAction = hitAction == GestaltAction.HIT_1
                || hitAction == GestaltAction.HIT_2
                || hitAction == GestaltAction.HIT_3;
        if (isHitAction) {
            AnimationDefinition hitAnim = getHitAnimation(hitAction);
            if (hitAnim != null) {
                if (hitAction != data.prevHitAction) {
                    data.hitState.stop();
                    data.hitState.start((int) ageInTicks);
                }
                data.prevHitAction = hitAction;
                data.hitState.updateTime(ageInTicks, 1.0F);
                this.animate(data.hitState, hitAnim, ageInTicks);
                return;
            }
        }
        if (!isHitAction && data.prevHitAction != GestaltAction.IDLE) {
            data.hitState.stop();
            data.prevHitAction = GestaltAction.IDLE;
        }

        // Idle always progresses for phase continuity across transitions
        if (!data.idleState.isStarted()) {
            data.idleState.start(0);
        }
        data.idleState.updateTime(ageInTicks, 1.0F);

        // Mining
        AnimationDefinition miningAnim = miningAnimation();
        boolean mining = miningAnim != null && isMining(player);
        if (mining && !data.wasMining) {
            data.miningState.start((int) ageInTicks);
        }
        if (!mining && data.wasMining && data.miningState.isStarted()) {
            data.miningState.stop();
        }
        data.wasMining = mining;

        // Guard
        boolean guarding = state.getAction() == GestaltAction.GUARD;
        AnimationDefinition guardAnim = guardAnimation();
        if (guarding && !data.wasGuarding && guardAnim != null) {
            data.guardState.start((int) ageInTicks);
        }
        if (!guarding && data.wasGuarding) {
            data.guardState.stop();
        }
        data.wasGuarding = guarding;

        // Play: mining > guard > idle
        if (mining) {
            data.miningState.updateTime(ageInTicks, 1.0F);
            this.animate(data.miningState, miningAnim, ageInTicks);
        } else if (guarding && guardAnim != null && data.guardState.isStarted()) {
            data.guardState.updateTime(ageInTicks, 1.0F);
            this.animate(data.guardState, guardAnim, ageInTicks);
        } else {
            this.animate(data.idleState, idleAnimation(), ageInTicks);
        }
    }

    private AnimationDefinition getHitAnimation(GestaltAction action) {
        return switch (action) {
            case HIT_1 -> hit1Animation();
            case HIT_2 -> hit2Animation();
            case HIT_3 -> hit3Animation();
            default    -> null;
        };
    }

    private static boolean isMining(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player) return false;
        if (!mc.options.keyAttack.isDown()) return false;
        if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return false;
        return Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(player.getEyePosition()) <= 3.5;
    }

    private static class AnimData {
        final AnimationState introState  = new AnimationState();
        final AnimationState idleState   = new AnimationState();
        final AnimationState guardState  = new AnimationState();
        final AnimationState grabState   = new AnimationState();
        final AnimationState miningState = new AnimationState();
        final AnimationState hitState    = new AnimationState();
        float introStartedAt   = -1.0F;
        boolean wasSummoned       = false;
        boolean wasGuarding       = false;
        boolean wasLedgeGrabbing  = false;
        boolean wasMining         = false;
        GestaltAction prevHitAction = GestaltAction.IDLE;
    }
}
