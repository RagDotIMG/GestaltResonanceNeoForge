package net.ragdot.gestaltresonance.common.skin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

import java.util.List;

/**
 * Server-side: matches gameplay events against {@link SkinUnlockCondition}s of the
 * player's current gestalt. Every unlock check verifies the player has the gestalt
 * the skin belongs to — events don't unlock skins on gestalts the player doesn't possess.
 */
public class GestaltSkinUnlockEvents {

    private static final int BIOME_POLL_INTERVAL = 20;
    private int biomePollCounter = 0;

    // -------------------------------------------------------------------------
    // Public API: try to unlock a skin matching a condition predicate.
    // -------------------------------------------------------------------------

    /**
     * Iterate the current gestalt's skins; for any skin still locked whose condition matches
     * the predicate, unlock it and notify the client. Returns the number of skins unlocked.
     */
    public static int tryUnlock(ServerPlayer player, java.util.function.Predicate<SkinUnlockCondition> matcher) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isAwakened()) return 0;
        ResourceLocation gestaltId = state.getGestaltId();
        if (gestaltId.equals(PlayerGestaltState.NONE)) return 0;

        List<GestaltSkin> skins = GestaltSkinRegistry.getSkins(gestaltId);
        int unlocked = 0;
        for (GestaltSkin skin : skins) {
            if (skin.isDefault()) continue;
            if (state.isSkinUnlocked(skin.id())) continue;
            if (!matcher.test(skin.condition())) continue;
            if (state.unlockSkin(skin.id())) {
                unlocked++;
                GestaltNetworking.sendSkinUnlockedToast(player, skin.id());
            }
        }
        if (unlocked > 0) {
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncUnlockedSkinsToOwner(player);
        }
        return unlocked;
    }

    // -------------------------------------------------------------------------
    // Event hooks
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        tryUnlock(killer, cond ->
                cond instanceof SkinUnlockCondition.KillMob k && k.entityType() == dead.getType());
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        // The current stack on the item entity is what's LEFT after pickup (often empty);
        // we want what was actually picked up, which is the original stack.
        ItemStack stack = event.getOriginalStack();
        if (stack.isEmpty()) return;
        tryUnlock(player, cond ->
                cond instanceof SkinUnlockCondition.PickUpItem p && stack.is(p.item()));
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (!state.is(Blocks.VAULT)) return;
        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.is(Items.TRIAL_KEY) && !held.is(Items.OMINOUS_TRIAL_KEY)) return;

        tryUnlock(player, cond -> cond instanceof SkinUnlockCondition.LootVault);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        biomePollCounter++;
        if (biomePollCounter < BIOME_POLL_INTERVAL) return;
        biomePollCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Holder<Biome> biome = player.level().getBiome(player.blockPosition());
            tryUnlock(player, cond ->
                    cond instanceof SkinUnlockCondition.VisitBiome v && SkinUnlockCondition.biomeMatches(v, biome));
        }
    }

    // -------------------------------------------------------------------------
    // Called explicitly by other systems (level-up, crash) — not events.
    // -------------------------------------------------------------------------

    /** Call after the gestalt's level changes. Unlocks any GestaltLevel-condition skin whose threshold was reached. */
    public static void checkLevelUnlock(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        int level = state.getGestaltLevel();
        tryUnlock(player, cond ->
                cond instanceof SkinUnlockCondition.GestaltLevel g && level >= g.level());
    }

    /**
     * Call after the gestalt crashes. Increments the crash counter (capped at the largest
     * registered threshold so it can't run away) and unlocks any threshold-reached skin.
     */
    public static void onGestaltCrash(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        int maxThreshold = highestCrashThreshold(state.getGestaltId());
        if (maxThreshold <= 0) return;

        if (state.getGestaltCrashCount() < maxThreshold) {
            state.incrementGestaltCrashCount();
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        }

        int currentCount = state.getGestaltCrashCount();
        tryUnlock(player, cond ->
                cond instanceof SkinUnlockCondition.GestaltCrashCount c && currentCount >= c.count());
    }

    /** Largest crash-count threshold registered for the gestalt; 0 if none. */
    private static int highestCrashThreshold(ResourceLocation gestaltId) {
        int max = 0;
        for (GestaltSkin skin : GestaltSkinRegistry.getSkins(gestaltId)) {
            if (skin.condition() instanceof SkinUnlockCondition.GestaltCrashCount c && c.count() > max) {
                max = c.count();
            }
        }
        return max;
    }
}
