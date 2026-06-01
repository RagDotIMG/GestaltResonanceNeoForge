package net.ragdot.gestaltresonance.common.power.spillways;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.entity.TearProjectileEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

public final class SpillwaysPower1B {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.SPILLWAYS, GestaltPowerSlot.POWER_1, GestaltPowerModifier.NONE);

    public static void register() {
        GestaltPowerRegistry.register(KEY, SpillwaysPower1B::activate);
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned() || !state.isAwakened()) { playFail(player); return; }
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[0][0]) { playFail(player); return; }
        if (player.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) { playFail(player); return; }

        long currentTick = player.getServer().getTickCount();
        if (state.hasPowerCooldown(KEY.slot(), KEY.modifier(), currentTick)) { playFail(player); return; }
        if (state.getTotalGestaltXp() < GestaltCosts.TEARS_FOR_FEARS_XP_COST) { playFail(player); return; }

        if (!(player.level() instanceof ServerLevel serverLevel)) { playFail(player); return; }

        // Enforce per-player active bubble cap
        int maxTears = GestaltCosts.tearsMaxCount(state.getGestaltLevel());
        int activeTears = serverLevel.getEntitiesOfClass(
                TearProjectileEntity.class,
                player.getBoundingBox().inflate(GestaltCosts.TEARS_FOR_FEARS_DEST_RANGE + 10),
                e -> e.isOwnedBy(player.getUUID())
        ).size();
        if (activeTears >= maxTears) { playFail(player); return; }

        // Destination: raycast along look vector; use HitResult.getLocation() regardless of hit/miss
        Vec3 eye = player.getEyePosition();
        Vec3 dest = serverLevel.clip(new ClipContext(
                eye,
                eye.add(player.getLookAngle().scale(GestaltCosts.TEARS_FOR_FEARS_DEST_RANGE)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        )).getLocation();

        // Urgency from player health
        float hp = player.getHealth();
        int urgency = hp > 15.0f ? 1 : hp > 10.0f ? 2 : 3;

        Vec3 spawnPos = eye.add(player.getLookAngle().scale(GestaltCosts.TEARS_FOR_FEARS_SPAWN_OFFSET));

        TearProjectileEntity tear = new TearProjectileEntity(
                GestaltEntities.TEAR_PROJECTILE.get(), serverLevel, dest, urgency);
        tear.setOwner(player);
        tear.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        serverLevel.addFreshEntity(tear);

        state.spendGestaltXp(GestaltCosts.TEARS_FOR_FEARS_XP_COST);
        state.setPowerCooldown(KEY.slot(), KEY.modifier(), currentTick + GestaltCosts.TEARS_FOR_FEARS_COOLDOWN_TICKS);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        player.causeFoodExhaustion(GestaltCosts.TEARS_FOR_FEARS_EXHAUSTION);
        GestaltNetworking.syncGestaltXpToPlayer(player);
        GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.TEARS_FOR_FEARS_COOLDOWN_TICKS);
    }

    private SpillwaysPower1B() {}
}
