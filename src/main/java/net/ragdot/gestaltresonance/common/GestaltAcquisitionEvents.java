package net.ragdot.gestaltresonance.common;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GhostPlayerHandler;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.GestaltThrowEvents;
import net.ragdot.gestaltresonance.common.passive.GestaltPassive;
import net.ragdot.gestaltresonance.common.passive.GestaltPassiveRegistry;

/**
 * ═══════════════════════════════════════════════════════════════
 * GESTALT ACQUISITION STATE MACHINE
 * ═══════════════════════════════════════════════════════════════
 *
 * Flow:
 *   1. Brush Crying Obsidian → drops NETHER_TEAR, block becomes Obsidian
 *   2. Smelt NETHER_TEAR → RESONANT_POWDER (data-driven recipe)
 *   3. Anvil: RESONANT_POWDER + AMETHYST_SHARD → SOUL_VESSEL_EMPTY
 *   4. Right-click Soul Fire/Soul Campfire with SOUL_VESSEL_EMPTY
 *      → extinguish fire, give SOUL_VESSEL_FRAGILE
 *   5. Right-click a LivingEntity with SOUL_VESSEL_FRAGILE → "seed" mob
 *   6. Seeded mob dies with player kill credit → player becomes dormant
 *      - pendingGestaltType = mob's entity type key (mob type decides gestalt)
 *   7. While dormant, drain 1 XP point/second (1 pt/20 ticks)
 *      - Target: 315 XP points (equivalent to levels 0→15)
 *      - Safe: never crashes at 0 XP, never goes negative
 *   8. When consumed >= 315 → dormant=false, awakened=true,
 *      awakenedGestaltType = pendingGestaltType
 *
 * Constants:
 *   DEFAULT_TARGET_XP = 315 (in PlayerGestaltState)
 *   DRAIN_INTERVAL    = 20 ticks (1 second)
 *   DRAIN_AMOUNT      = 1 XP point per interval
 * ═══════════════════════════════════════════════════════════════
 */
public class GestaltAcquisitionEvents {

    /** Ticks between XP drain pulses (20 = 1 second). */
    private static final int DRAIN_INTERVAL = 20;
    /** XP points drained per pulse. */
    private static final int DRAIN_AMOUNT = 1;

    /**
     * Force-dismiss the gestalt with the full crash flow: cancel actions, set re-summon
     * cooldown, deactivate passive, play dissolve sound, broadcast crash + state sync,
     * and notify the skin-unlock listener so the crash count is bumped.
     *
     * Used by hunger crash, fall break crash, and any future crash-trigger.
     */
    public static void crashGestalt(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        boolean wasGuarding = state.isGuarding();
        long currentTick = player.getServer().getTickCount();

        // If a Phase Out ghost window is active, end it (crash supersedes the protection)
        if (state.isPhaseOutActive()) {
            state.setPhaseOutActive(false);
            state.setPhaseOutTicksRemaining(0);
            GhostPlayerHandler.setGhostState(player, false);
        }

        GestaltThrowEvents.cancelThrow(player);
        state.clearGuard();
        state.clearLedgeGrab();
        state.setSummoned(false);
        state.setAction(GestaltAction.IDLE);
        state.setCrashUntilTick(currentTick + GestaltCosts.CRASH_COOLDOWN_TICKS);
        GestaltPassive passive = GestaltPassiveRegistry.getPassive(state.getGestaltId());
        if (passive != null) passive.onDeactivate(player);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        player.playNotifySound(GestaltSounds.GESTALT_DISSOLVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        GestaltNetworking.syncCrashToTracking(player);
        GestaltNetworking.syncToTracking(player);
        if (wasGuarding) GestaltNetworking.syncGuardToTracking(player, false);

        net.ragdot.gestaltresonance.common.skin.GestaltSkinUnlockEvents.onGestaltCrash(player);
    }


    // ── Crying Obsidian brushing ──────────────────────────────
    // Handled via PlayerInteractEvent.RightClickBlock as a proxy:
    // when a player right-clicks Crying Obsidian with a Brush item,
    // drop Nether Tear and convert to Obsidian.
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());

        // Check if holding a brush
        if (!held.is(Items.BRUSH)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.CRYING_OBSIDIAN)) return;

        // Convert to obsidian
        level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);

        // Drop nether tear
        ItemStack tear = new ItemStack(GestaltResonance.NETHER_TEAR.get());
        player.drop(tear, false);

        // Damage brush
        held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));

        event.setCanceled(true);
    }

    // ── Anvil recipe: Resonant Powder + Amethyst Shard → Soul Vessel (Empty) ──
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (left.is(GestaltResonance.RESONANT_POWDER.get()) && right.is(Items.AMETHYST_SHARD)) {
            event.setOutput(new ItemStack(GestaltResonance.SOUL_VESSEL_EMPTY.get()));
            event.setCost(3);
            event.setMaterialCost(1);
        }
    }

    // ── Seeded mob death → start dormant gestalt ──────────────
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player) return;

        MobSeededData seededData = entity.getData(GestaltAttachments.MOB_SEEDED_DATA.get());
        if (!seededData.isSeeded()) return;

        // Find killer player
        Player killer = null;
        if (event.getSource().getEntity() instanceof Player p) {
            killer = p;
        } else if (entity.getKillCredit() instanceof Player p) {
            killer = p;
        }
        if (killer == null) return;

        // seedTypeKey now stores the gestalt ID (e.g. "gestaltresonance:amen_break"), not entity type
        String gestaltIdStr = seededData.getSeedTypeKey();
        if (gestaltIdStr == null || gestaltIdStr.isEmpty()) return;

        PlayerGestaltState state = killer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        // Don't overwrite an already awakened gestalt
        if (state.isAwakened()) {
            killer.displayClientMessage(Component.literal("You already possess an awakened gestalt."), true);
            return;
        }
        if (state.isDormant()) {
            killer.displayClientMessage(Component.literal("A gestalt is already forming within you."), true);
            return;
        }

        state.setDormant(true);
        state.setPendingGestaltType(gestaltIdStr);
        state.setConsumedXpPoints(0);
        killer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltMobEffects.syncDormantEffect(killer);
        GestaltMobEffects.applyInvasiveSoul(killer);

        GestaltResonance.LOGGER.debug("[GestaltAcquisition] AWARD: player={} pendingGestaltType={}",
                killer.getName().getString(), gestaltIdStr);
        killer.displayClientMessage(
                Component.literal("The creature's essence flows into you... gestalt dormant. (" + gestaltIdStr + ")"),
                false
        );
    }

    // ── Server tick: drain XP while dormant + exhaustion while summoned ──
    private int drainTickCounter = 0;
    private int exhaustTickCounter = 0;
    private int decayTickCounter = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // --- Summoned exhaustion drain (every 5 ticks) ---
        exhaustTickCounter++;
        if (exhaustTickCounter >= GestaltCosts.SUMMON_DRAIN_INTERVAL) {
            exhaustTickCounter = 0;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                PlayerGestaltState st = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                if (st.isSummoned()) {
                    player.causeFoodExhaustion(GestaltCosts.SUMMON_DRAIN);
                }
            }
        }

        // --- Guard damage decay (every 5 ticks, while not guarding) ---
        decayTickCounter++;
        if (decayTickCounter >= GestaltCosts.GUARD_DECAY_INTERVAL) {
            decayTickCounter = 0;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                PlayerGestaltState st = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                if (st.isSummoned() && !st.isGuarding() && st.getGuardDamageAccumulated() > 0) {
                    st.addGuardDamageAccumulated(-1.0f);
                    player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), st);
                }
            }
        }

        // --- Dormant XP drain (every 20 ticks) ---
        drainTickCounter++;
        if (drainTickCounter < DRAIN_INTERVAL) return;
        drainTickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

            // Hunger crash: force-unsummon when food level is at or below threshold
            if (state.isSummoned() && player.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) {
                crashGestalt(player);
                state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            }

            if (!state.isDormant()) continue;

            int currentXp = XpHelper.getTotalXp(player);
            int drain = Math.min(DRAIN_AMOUNT, currentXp); // never go negative
            if (drain > 0) {
                XpHelper.setTotalXp(player, currentXp - drain);
                state.setConsumedXpPoints(state.getConsumedXpPoints() + drain);
            }
            // else: player has 0 XP, just wait

            // Refresh dormant effect icon
            GestaltMobEffects.syncDormantEffect(player);

            // Check if target reached
            if (state.getConsumedXpPoints() >= state.getTargetXpPoints()) {
                state.setDormant(false);
                state.setAwakened(true);
                String gType = state.getPendingGestaltType();
                state.setAwakenedGestaltType(gType);
                state.setGestaltId(ResourceLocation.parse(gType));
                state.setPendingGestaltType("");
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                GestaltMobEffects.syncDormantEffect(player);
                GestaltNetworking.syncToTracking(player);
                player.playNotifySound(GestaltSounds.GESTALT_AWAKEN.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                GestaltResonance.LOGGER.debug("[GestaltAcquisition] AWAKEN: player={} awakenedGestaltType={}",
                        player.getName().getString(), gType);
                player.displayClientMessage(
                        Component.literal("Your gestalt has awakened! ").append(
                                Component.translatable("gestalt." + state.getAwakenedGestaltType().replace(":", "."))),
                        false
                );
            } else {
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            }
        }
    }
}
