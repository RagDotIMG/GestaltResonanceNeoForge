package net.ragdot.gestaltresonance.common;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.entity.BodyDoubleEntity;
import net.ragdot.gestaltresonance.common.entity.BodyDoubleHitEvent;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

/**
 * Server-side orchestrator for the Soul Projection ability.
 *
 * Lifecycle:
 *   - {@link #activateSoulProjection(ServerPlayer)} — triggered by SoulProjectionActivateC2S
 *     when the player presses G while guarding. Validates state, spawns the body double,
 *     enables ghost + flight, syncs to clients.
 *   - {@link #tickSoulProjection(ServerPlayer)} — runs every server tick. Handles cooldown
 *     decrement, hunger drain, hunger-crash exit, and range CLAMPING (server-authoritative).
 *   - {@link #teardown(ServerPlayer, SoulProjectionExitType, DamageSource, float)} — single
 *     shared exit path that snaps the player back to the body double, restores all altered
 *     state, applies exit-type-specific damage, sets the cooldown, and syncs.
 *
 * Range clamping uses the stored {@code soulProjectionAnchor} on the player's state, NOT
 * an entity lookup, so it can't be defeated by a same-tick race when the body double has
 * been added to the level but isn't yet in {@code Level.getEntity(int)}.
 */
public class GestaltSoulProjectionEvents {

    private static final ResourceLocation FLIGHT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "soul_projection_flight");

    // ── Event subscriptions (instance, registered to NeoForge.EVENT_BUS) ───────

    /** Body double was hit on the server → forced exit with doubled damage. */
    @SubscribeEvent
    public void onBodyDoubleHit(BodyDoubleHitEvent event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(event.getOwnerUuid());
        if (player == null) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSoulProjecting()) return;
        teardown(player, SoulProjectionExitType.FORCED, event.getSource(), event.getAmount());
    }

    /** Right-click own body double while projecting → clean exit. 20-tick grace period after spawn
     *  so the right-click that activated soul projection (still held from guard) doesn't
     *  immediately CLEAN-exit on the just-spawned body double. */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof BodyDoubleEntity bodyDouble)) return;
        if (!player.getUUID().equals(bodyDouble.getOwnerUuid())) return;
        if (bodyDouble.tickCount < 20) {
            event.setCanceled(true); // swallow the interaction during grace
            return;
        }
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSoulProjecting()) return;
        teardown(player, SoulProjectionExitType.CLEAN, null, 0f);
        event.setCanceled(true);
    }

    // ── Activation ─────────────────────────────────────────────────────────────

    public static void activateSoulProjection(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        // Validations (per spec)
        if (!state.isSummoned()) return;
        if (!state.isAwakened()) return;
        if (state.isSoulProjecting()) return;
        if (state.getSoulProjectionCooldownTicks() > 0) return;
        if (player.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) return;
        if (!state.isGuarding()) return;

        // One-per-player invariant: dismiss any orphaned body double for this UUID
        BodyDoubleEntity.dismissExistingDoubles(player.level(), player.getUUID());

        // Spawn the new body double
        ServerLevel level = (ServerLevel) player.level();
        BodyDoubleEntity bodyDouble = GestaltEntities.BODY_DOUBLE.get().create(level);
        if (bodyDouble == null) return;
        bodyDouble.setOwner(player.getUUID(), player.getName().getString());
        bodyDouble.copyEquipmentFrom(player);

        // Match owner's max health (visual consistency if a health bar shows)
        AttributeInstance maxHealth = bodyDouble.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(player.getMaxHealth());
        }
        bodyDouble.setHealth(player.getMaxHealth());

        bodyDouble.moveTo(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        // Show the owner's player name above the body double via vanilla name-tag plumbing.
        bodyDouble.setCustomName(player.getDisplayName());
        bodyDouble.setCustomNameVisible(true);
        level.addFreshEntity(bodyDouble);

        Vec3 anchor = new Vec3(player.getX(), player.getY(), player.getZ());

        // Push the player forward 0.8 blocks in their facing direction so they don't
        // immediately right-click the body double (which would CLEAN-exit them right back).
        // Done AFTER addFreshEntity so the body double stays at the anchor.
        float yawRad = (float) Math.toRadians(player.getYRot());
        double pushX = -Math.sin(yawRad) * 0.8;
        double pushZ =  Math.cos(yawRad) * 0.8;
        player.teleportTo(player.getX() + pushX, player.getY(), player.getZ() + pushZ);

        // Update state
        state.clearGuard();
        state.setSoulProjecting(true);
        state.setBodyDoubleEntityId(bodyDouble.getId());
        state.setSoulProjectionAnchor(anchor);
        state.setSoulProjectionMaxRange((float) GestaltCosts.soulProjectionRangeFor(state));
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        // Apply effects (after state, so GhostPlayerHandler reads the updated state)
        GhostPlayerHandler.setGhostState(player, true);
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null) flight.addOrUpdateTransientModifier(new AttributeModifier(
                FLIGHT_MODIFIER_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        player.getAbilities().flying = true;
        player.getAbilities().setFlyingSpeed(GestaltCosts.SOUL_PROJECTION_FLY_SPEED);
        player.onUpdateAbilities();

        // Sync
        GestaltNetworking.syncGuardToTracking(player, false);
        GestaltNetworking.syncSoulProjectionToTracking(player, true, bodyDouble.getId(), anchor);
    }

    // ── Per-tick logic ─────────────────────────────────────────────────────────

    public static void tickSoulProjection(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        // Cooldown decrements regardless of projecting state
        if (state.getSoulProjectionCooldownTicks() > 0) {
            state.setSoulProjectionCooldownTicks(state.getSoulProjectionCooldownTicks() - 1);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        }

        if (!state.isSoulProjecting()) return;

        // Gestalt was desummoned mid-projection (e.g. crash) → clean teardown
        if (!state.isSummoned()) {
            teardown(player, SoulProjectionExitType.CLEAN, null, 0f);
            return;
        }

        // Hunger crash exit
        if (player.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) {
            teardown(player, SoulProjectionExitType.CRASH, null, 0f);
            return;
        }

        // Hunger drain (1 exhaustion per second)
        if (player.getServer() != null && player.getServer().getTickCount() % 20 == 0) {
            player.causeFoodExhaustion(GestaltCosts.SOUL_PROJECTION_HUNGER_DRAIN_PER_SECOND);
        }

        // Range clamp (server-authoritative): use the stored anchor — no entity lookup race
        Vec3 anchor = state.getSoulProjectionAnchor();
        if (anchor == null) {
            // Defensive: state inconsistent, bail cleanly
            teardown(player, SoulProjectionExitType.CLEAN, null, 0f);
            return;
        }

        double maxRange = state.getSoulProjectionMaxRange();
        if (maxRange <= 0.0) {
            // Defensive: recompute if it wasn't set
            maxRange = GestaltCosts.soulProjectionRangeFor(state);
        }

        Vec3 toPlayer = player.position().subtract(anchor);
        double distSq = toPlayer.lengthSqr();
        if (distSq > maxRange * maxRange) {
            Vec3 normal = toPlayer.scale(1.0 / Math.sqrt(distSq));
            Vec3 clamped = anchor.add(normal.scale(maxRange));
            // Hard-lock pattern (mirrors LedgeGrabLogic.tickPlayer): setPos + hurtMarked
            player.setPos(clamped.x, clamped.y, clamped.z);
            player.hurtMarked = true;
            // Zero outward velocity so the player slides along the sphere instead of
            // hammering the boundary every tick.
            Vec3 vel = player.getDeltaMovement();
            double outward = vel.dot(normal);
            if (outward > 0) {
                player.setDeltaMovement(vel.subtract(normal.scale(outward)));
            }
        }
    }

    // ── Shared teardown for all four exit types ───────────────────────────────

    public static void teardown(ServerPlayer player, SoulProjectionExitType exitType,
                                @Nullable DamageSource source, float rawDamage) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSoulProjecting()) {
            // State already cleared (race condition): still clean up any orphaned body double.
            BodyDoubleEntity.dismissExistingDoubles(player.level(), player.getUUID());
            return;
        }

        Vec3 anchor = state.getSoulProjectionAnchor();

        // 1. Clear ghost state BEFORE any damage so the GhostPlayerHandler doesn't cancel it.
        GhostPlayerHandler.setGhostState(player, false);

        // 2. Restore flight (skip if creative/spectator — they keep their own flight)
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (!player.isCreative() && !player.isSpectator()) {
            if (flight != null) flight.removeModifier(FLIGHT_MODIFIER_ID);
            player.getAbilities().flying = false;
            player.getAbilities().setFlyingSpeed(GestaltCosts.DEFAULT_FLY_SPEED);
            player.onUpdateAbilities();
        } else {
            // Even in creative, restore the default fly speed so we don't leave it halved
            player.getAbilities().setFlyingSpeed(GestaltCosts.DEFAULT_FLY_SPEED);
            player.onUpdateAbilities();
        }

        // 3. Snap player back to the anchor position (proper client sync)
        Vec3 snapPos = anchor != null ? anchor : player.position();
        if (anchor != null) {
            player.teleportTo(anchor.x, anchor.y, anchor.z);
        }

        // 4. Dismiss body double
        BodyDoubleEntity.dismissExistingDoubles(player.level(), player.getUUID());

        // 5. Clear soul projection state, set cooldown
        int cooldown = exitType == SoulProjectionExitType.CLEAN
                ? GestaltCosts.SOUL_PROJECTION_COOLDOWN_CLEAN_TICKS
                : GestaltCosts.SOUL_PROJECTION_COOLDOWN_HARD_TICKS;
        state.setSoulProjecting(false);
        state.setBodyDoubleEntityId(-1);
        state.setSoulProjectionAnchor(null);
        state.setSoulProjectionMaxRange(0f);
        state.setSoulProjectionCooldownTicks(cooldown);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        // 6. Apply exit-type-specific damage AFTER state cleanup.
        float damageDealt = applyExitDamage(player, exitType, source, rawDamage);

        // 7. Sync to clients
        GestaltNetworking.syncSoulProjectionToTracking(player, false, -1, snapPos);
        GestaltNetworking.sendSoulProjectionYank(player, exitType, damageDealt, snapPos);
    }

    /** Returns the damage actually dealt (0 for CLEAN, possibly less than requested for clamped EMERGENCY). */
    private static float applyExitDamage(ServerPlayer player, SoulProjectionExitType exitType,
                                         @Nullable DamageSource source, float rawDamage) {
        switch (exitType) {
            case CLEAN -> {
                return 0f;
            }
            case EMERGENCY -> {
                float dmg = GestaltCosts.SOUL_PROJECTION_EMERGENCY_DAMAGE;
                // Clamp: cannot kill — leave player with at least 1 HP.
                if (player.getHealth() <= dmg) {
                    if (player.getHealth() > 1f) player.setHealth(1f);
                    return Math.max(0f, player.getHealth() - 1f);
                }
                player.hurt(player.damageSources().generic(), dmg);
                return dmg;
            }
            case FORCED -> {
                float dmg = rawDamage * GestaltCosts.SOUL_PROJECTION_FORCED_DAMAGE_MULTIPLIER;
                DamageSource src = source != null ? source : player.damageSources().generic();
                player.hurt(src, dmg);
                return dmg;
            }
            case CRASH -> {
                float dmg = GestaltCosts.SOUL_PROJECTION_CRASH_DAMAGE;
                player.hurt(player.damageSources().starve(), dmg);
                state_incrementCrashCount(player);
                return dmg;
            }
        }
        return 0f;
    }

    private static void state_incrementCrashCount(ServerPlayer player) {
        PlayerGestaltState s = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        s.incrementGestaltCrashCount();
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), s);
    }

    // ── Server-side handler for SoulProjectionActionC2S (PICKUP) ───────────────

    public static void handlePickup(ServerPlayer player, int targetEntityId) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSoulProjecting()) return;
        net.minecraft.world.entity.Entity entity = player.level().getEntity(targetEntityId);
        if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) return;
        if (player.distanceToSqr(itemEntity) > 9.0) return; // ~3 block reach

        net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;
        if (player.getInventory().add(stack)) {
            // add() consumes from the stack on success
            if (stack.isEmpty()) {
                itemEntity.discard();
            }
        }
        // If add() failed (inventory full), do nothing — spec says don't drop.
    }
}
