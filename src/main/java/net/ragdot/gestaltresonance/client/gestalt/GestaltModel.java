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
import net.ragdot.gestaltresonance.common.GestaltCosts;
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

    /** Optional — return null if this gestalt doesn't support the charged-strike windup pose. */
    @Nullable protected AnimationDefinition windupAnimation() { return null; }

    /** Optional — return null if this gestalt doesn't support a swim pose. */
    @Nullable protected AnimationDefinition swimAnimation() { return null; }

    /** Optional — return null if this gestalt doesn't support a wall-slide pose. */
    @Nullable protected AnimationDefinition wallSlideAnimation() { return null; }

    /** Optional — return null if this gestalt doesn't support the 1G power animation. */
    @Nullable protected AnimationDefinition power1GAnimation() { return null; }

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
    public static void removePlayer(UUID playerUuid) {
        perPlayer.remove(playerUuid);
    }

    public static void notifyChainEnd(UUID playerUuid) {
        AnimData data = perPlayer.get(playerUuid);
        if (data == null) return;
        if (data.hitState.isStarted()) data.hitState.stop();
        data.prevHitAction = GestaltAction.IDLE;
    }

    /**
     * Prevent the intro animation from firing for a player in the current/next {@link #setupAnim}
     * call. Used by the first-person renderer, where the intro should never play.
     */
    public void skipIntroFor(UUID playerId) {
        AnimData data = perPlayer.computeIfAbsent(playerId, k -> {
            AnimData d = new AnimData();
            d.wasSummoned = true;
            return d;
        });
        data.wasSummoned = true;
        if (data.introState.isStarted()) data.introState.stop();
        data.introStartedAt = -1.0F;
    }

    /**
     * Reset all animation state for a player whose gestalt just became unsummoned.
     * Called via callback when the client receives a summoned→false state sync, so
     * the next summon's rising-edge check fires correctly even though setupAnim is
     * not called while the gestalt is invisible.
     */
    public static void notifyUnsummon(UUID playerUuid) {
        AnimData data = perPlayer.get(playerUuid);
        if (data == null) return;
        data.wasSummoned = false;
        data.introStartedAt = -1.0F;
        data.introState.stop();
        data.hitState.stop();
        data.prevHitAction = GestaltAction.IDLE;
        data.grabState.stop();
        data.miningState.stop();
        data.guardState.stop();
        data.throwState.stop();
        data.swimState.stop();
        data.windupState.stop();
        data.power1GState.stop();
        data.wasMining = false;
        data.wasGuarding = false;
        data.wasThrowing = false;
        data.wasSwimming = false;
        data.wasLedgeGrabbing = false;
        data.wasWallSliding = false;
        data.wallSlideState.stop();
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

        // ── Phase 1: cache animation definitions and compute all flags ───────

        AnimationDefinition intro      = introAnimation();
        AnimationDefinition grabAnim      = grabAnimation();
        AnimationDefinition wallSlideAnim = wallSlideAnimation();
        AnimationDefinition power1GAnim   = power1GAnimation();
        AnimationDefinition windupAnim = windupAnimation();
        AnimationDefinition miningAnim = miningAnimation();
        AnimationDefinition guardAnim  = guardAnimation();
        AnimationDefinition throwAnim  = throwAnimation();
        AnimationDefinition swimAnim   = swimAnimation();

        GestaltAction action = state.getAction();
        boolean isHitAction  = action == GestaltAction.HIT_1
                            || action == GestaltAction.HIT_2
                            || action == GestaltAction.HIT_3;
        AnimationDefinition hitAnim = isHitAction ? getHitAnimation(action) : null;

        boolean ledgeGrabbing = state.isLedgeGrabbing();
        boolean wallSliding   = action == GestaltAction.WALL_SLIDE;
        boolean guarding      = action == GestaltAction.GUARD;
        boolean throwing      = action == GestaltAction.THROW;
        boolean swimming      = player.isSwimming();
        boolean mining        = miningAnim != null && isMining(player);
        boolean power1GActive = action == GestaltAction.POWER_1G_WINDUP;
        boolean winding       = action == GestaltAction.CHARGED_STRIKE_WINDUP
                             || action == GestaltAction.CHARGED_STRIKE_TRAVEL;

        float introLenTicks = intro.lengthInSeconds() * 20.0F;

        // ── Phase 2: start/stop every state unconditionally before any return ─
        // This ensures was* flags and AnimationState objects are always current,
        // regardless of which rendering path exits early below.

        // Intro — rising edge on summon; interrupted by hit chain
        if (summoned && !data.wasSummoned) {
            data.introState.start((int) ageInTicks);
            data.introStartedAt = ageInTicks;
        }
        data.wasSummoned = summoned;
        boolean introActive = data.introState.isStarted()
                && data.introStartedAt >= 0
                && (ageInTicks - data.introStartedAt) < introLenTicks;
        if (introActive && isHitAction) {
            data.introState.stop();
            introActive = false;
        } else if (!introActive && data.introState.isStarted()) {
            data.introState.stop();
        }

        // Ledge grab
        if (ledgeGrabbing && grabAnim != null && !data.grabState.isStarted()) {
            data.grabState.start((int) ageInTicks);
        } else if (!ledgeGrabbing && data.wasLedgeGrabbing && data.grabState.isStarted()) {
            data.grabState.stop();
        }
        data.wasLedgeGrabbing = ledgeGrabbing;

        // Wall slide — level-triggered like ledge grab (static hold pose)
        if (wallSliding && wallSlideAnim != null && !data.wallSlideState.isStarted()) {
            data.wallSlideState.start((int) ageInTicks);
        } else if (!wallSliding && data.wasWallSliding && data.wallSlideState.isStarted()) {
            data.wallSlideState.stop();
        }
        data.wasWallSliding = wallSliding;

        // Hit chain
        if (isHitAction && hitAnim != null) {
            if (action != data.prevHitAction) {
                data.hitState.stop();
                data.hitState.start((int) ageInTicks);
            }
            data.prevHitAction = action;
        } else if (data.prevHitAction != GestaltAction.IDLE) {
            data.hitState.stop();
            data.prevHitAction = GestaltAction.IDLE;
        }

        // 1G power
        if (power1GActive && power1GAnim != null) {
            if (!data.power1GState.isStarted()) data.power1GState.start((int) ageInTicks);
        } else if (!power1GActive && data.power1GState.isStarted()) {
            data.power1GState.stop();
        }

        // Charged-strike windup (held through travel phase)
        if (winding && windupAnim != null) {
            if (!data.windupState.isStarted()) data.windupState.start((int) ageInTicks);
        } else if (!winding && data.windupState.isStarted()) {
            data.windupState.stop();
        }

        // Idle — always ticking for phase continuity across transitions
        if (!data.idleState.isStarted()) data.idleState.start(0);
        data.idleState.updateTime(ageInTicks, 1.0F);

        // Mining
        if (mining && !data.wasMining) {
            data.miningState.start((int) ageInTicks);
        } else if (!mining && data.wasMining && data.miningState.isStarted()) {
            data.miningState.stop();
        }
        data.wasMining = mining;

        // Guard
        if (guarding && !data.wasGuarding && guardAnim != null) {
            data.guardState.start((int) ageInTicks);
        } else if (!guarding && data.wasGuarding && data.guardState.isStarted()) {
            data.guardState.stop();
        }
        data.wasGuarding = guarding;

        // Throw
        if (throwing && !data.wasThrowing && throwAnim != null) {
            data.throwState.start((int) ageInTicks);
        } else if (!throwing && data.wasThrowing && data.throwState.isStarted()) {
            data.throwState.stop();
        }
        data.wasThrowing = throwing;

        // Swim
        if (swimming && !data.wasSwimming && swimAnim != null) {
            data.swimState.start((int) ageInTicks);
        } else if (!swimming && data.wasSwimming && data.swimState.isStarted()) {
            data.swimState.stop();
        }
        data.wasSwimming = swimming;

        // ── Phase 3: render — highest priority wins (early returns are safe now) ─

        if (introActive) {
            data.introState.updateTime(ageInTicks, 1.0F);
            this.animate(data.introState, intro, ageInTicks);
            return;
        }

        if (ledgeGrabbing && grabAnim != null) {
            data.grabState.updateTime(ageInTicks, 1.0F);
            this.animate(data.grabState, grabAnim, ageInTicks);
            return;
        }

        if (wallSliding && wallSlideAnim != null && data.wallSlideState.isStarted()) {
            data.wallSlideState.updateTime(ageInTicks, 1.0F);
            this.animate(data.wallSlideState, wallSlideAnim, ageInTicks);
            return;
        }

        if (isHitAction && hitAnim != null) {
            data.hitState.updateTime(ageInTicks, 1.0F);
            this.animate(data.hitState, hitAnim, ageInTicks);
            return;
        }

        if (power1GActive && power1GAnim != null) {
            data.power1GState.updateTime(ageInTicks, 1.0F);
            this.animate(data.power1GState, power1GAnim, ageInTicks);
            return;
        }

        if (winding && windupAnim != null) {
            data.windupState.updateTime(ageInTicks, 1.0F);
            this.animate(data.windupState, windupAnim, ageInTicks);
            return;
        }

        // Play: mining > throw > guard > swim > idle
        if (mining && data.miningState.isStarted()) {
            data.miningState.updateTime(ageInTicks, 1.0F);
            this.animate(data.miningState, miningAnim, ageInTicks);
        } else if (throwing && throwAnim != null && data.throwState.isStarted()) {
            data.throwState.updateTime(ageInTicks, 1.0F);
            this.animate(data.throwState, throwAnim, ageInTicks);
        } else if (guarding && guardAnim != null && data.guardState.isStarted()) {
            data.guardState.updateTime(ageInTicks, 1.0F);
            this.animate(data.guardState, guardAnim, ageInTicks);
        } else if (swimming && swimAnim != null && data.swimState.isStarted()) {
            data.swimState.updateTime(ageInTicks, 1.0F);
            this.animate(data.swimState, swimAnim, ageInTicks);
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
        if (mc.player == player) {
            // Local player: detect via input so the animation has zero round-trip lag.
            if (!mc.options.keyAttack.isDown()) return false;
            if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return false;
            PlayerGestaltState localState = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            return Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(player.getEyePosition()) <= GestaltCosts.mineRangeFor(localState);
        }
        // Remote players: use server-synced state pushed via SyncMiningStateS2C.
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        return state.isMining();
    }

    private static class AnimData {
        final AnimationState introState  = new AnimationState();
        final AnimationState idleState   = new AnimationState();
        final AnimationState guardState  = new AnimationState();
        final AnimationState throwState  = new AnimationState();
        final AnimationState grabState       = new AnimationState();
        final AnimationState wallSlideState  = new AnimationState();
        final AnimationState miningState = new AnimationState();
        final AnimationState hitState    = new AnimationState();
        final AnimationState windupState = new AnimationState();
        final AnimationState swimState   = new AnimationState();
        final AnimationState power1GState = new AnimationState();
        float introStartedAt   = -1.0F;
        boolean wasSummoned       = false;
        boolean wasGuarding       = false;
        boolean wasThrowing       = false;
        boolean wasLedgeGrabbing  = false;
        boolean wasWallSliding    = false;
        boolean wasMining         = false;
        boolean wasSwimming       = false;
        GestaltAction prevHitAction = GestaltAction.IDLE;
    }
}
