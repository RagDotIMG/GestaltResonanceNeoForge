package net.ragdot.gestaltresonance.common.power.spillways;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.PrimedBlockEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Spillways — Moist Air (Power 2G).
 *
 * Toggle: X+Guard flips the field on/off.
 * While active, within a 7-block sphere:
 *   - PrimedTnt / PrimedBlockEntity are cancelled on join; block is queued for restoration
 *     in the same server tick (EntityJoinLevelEvent disallows world writes — queued instead).
 *   - Explosions are suppressed as a fallback (handles creepers and edge cases).
 *   - Passive (non-Enemy) living entities have air supply restored every 3 ticks.
 */
public final class SpillwaysPower2G {

    public static final SpillwaysPower2G EVENT_LISTENER = new SpillwaysPower2G();

    private static final double RADIUS    = 7.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;

    // Block restorations queued in EntityJoinLevelEvent (world writes are disallowed there).
    // Flushed each server tick from GestaltResonance.onServerTick — same tick, no gap.
    private record RestoreTask(ServerLevel level, BlockPos pos, BlockState state) {}
    private static final Queue<RestoreTask> RESTORE_QUEUE = new ArrayDeque<>();

    private SpillwaysPower2G() {}

    // ── Toggle ────────────────────────────────────────────────────────────────

    public static void toggle(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!GestaltIds.SPILLWAYS.equals(state.getGestaltId())) return;
        GestaltAction action = state.getAction();
        if (action != GestaltAction.IDLE && action != GestaltAction.GUARD) return;
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[1][2]) return;

        state.setMoistAirActive(!state.isMoistAirActive());
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
        GestaltNetworking.syncMoistAirToPlayer(player);
    }

    // ── Per-tick restoration flush (called before player loop in onServerTick) ─

    /**
     * Must be called once per server tick (before the player loop).
     * Drains the block-restore queue queued from EntityJoinLevelEvent.
     */
    public static void flushRestorations() {
        RestoreTask task;
        while ((task = RESTORE_QUEUE.poll()) != null) {
            if (task.level().getBlockState(task.pos()).isAir()) {
                task.level().setBlock(task.pos(), task.state(), Block.UPDATE_ALL);
            }
        }
    }

    // ── Per-tick logic (per-player) ───────────────────────────────────────────

    public static void tick(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isMoistAirActive()) return;

        AABB box = player.getBoundingBox().inflate(RADIUS);
        Vec3 playerPos = player.position();
        ServerLevel level = player.serverLevel();

        // Every tick: bounce creepers back from detonation threshold (swell >= 28 of 30)
        level.getEntitiesOfClass(Creeper.class, box,
                        e -> e.position().distanceToSqr(playerPos) <= RADIUS_SQ)
                .forEach(creeper -> {
                    if (creeper.getSwelling(1.0f) >= 1.0f) {
                        creeper.setSwellDir(-1);
                    }
                });

        if (player.getServer().getTickCount() % 3 != 0) return;

        // Every 3 ticks: extinguish fire blocks in radius
        BlockPos playerBP = player.blockPosition();
        for (BlockPos bp : BlockPos.betweenClosed(playerBP.offset(-7, -7, -7), playerBP.offset(7, 7, 7))) {
            if (bp.distSqr(playerBP) > RADIUS_SQ) continue;
            BlockState bs = level.getBlockState(bp);
            Block block = bs.getBlock();
            if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
                level.removeBlock(bp, false);
            } else if (bs.hasProperty(BlockStateProperties.LIT) && bs.getValue(BlockStateProperties.LIT)) {
                level.setBlock(bp, bs.setValue(BlockStateProperties.LIT, Boolean.FALSE), Block.UPDATE_ALL);
            }
        }

        // Every 3 ticks: restore air supply of passive mobs
        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class, box,
                e -> !(e instanceof Player)
                        && !(e instanceof Enemy)
                        && e.position().distanceToSqr(playerPos) <= RADIUS_SQ);

        for (LivingEntity entity : entities) {
            int maxAir = entity.getMaxAirSupply();
            if (entity.getAirSupply() < maxAir) {
                entity.setAirSupply(maxAir);
            }
        }
    }

    // ── Explosion / priming suppression ──────────────────────────────────────

    /**
     * Cancel PrimedTnt and PrimedBlockEntity before they join the level.
     * Queue block restoration — world writes are disallowed inside this event.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        net.minecraft.world.entity.Entity entity = event.getEntity();

        boolean isPrimedTnt   = entity instanceof PrimedTnt;
        boolean isPrimedBlock = entity.getType() == GestaltEntities.PRIMED_BLOCK.get();
        if (!isPrimedTnt && !isPrimedBlock) return;

        if (!isInMoistAirField(event.getLevel(), entity.position())) return;

        event.setCanceled(true);

        // Queue block restoration — safe world write deferred to flushRestorations()
        BlockPos bp = entity.blockPosition();
        BlockState restore = isPrimedTnt
                ? Blocks.TNT.defaultBlockState()
                : ((PrimedBlockEntity) entity).getBlockState();
        RESTORE_QUEUE.offer(new RestoreTask((ServerLevel) event.getLevel(), bp, restore));
    }

    /** Fallback: cancel any explosion whose centre is inside the field (covers creepers). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onExplosionStart(ExplosionEvent.Start event) {
        if (event.getLevel().isClientSide()) return;
        if (isInMoistAirField(event.getLevel(), event.getExplosion().center())) {
            event.setCanceled(true);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static boolean isInMoistAirField(Level level, Vec3 pos) {
        if (!(level instanceof ServerLevel sl)) return false;
        for (ServerPlayer player : sl.players()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isMoistAirActive()) continue;
            if (!GestaltIds.SPILLWAYS.equals(state.getGestaltId())) continue;
            if (player.position().distanceToSqr(pos) <= RADIUS_SQ) return true;
        }
        return false;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /** Disarm Moist Air (called on death/logout to reset toggle state). */
    public static void disarm(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isMoistAirActive()) return;
        state.setMoistAirActive(false);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncMoistAirToPlayer(player);
    }
}
