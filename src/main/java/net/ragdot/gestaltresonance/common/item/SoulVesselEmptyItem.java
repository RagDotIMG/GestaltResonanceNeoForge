package net.ragdot.gestaltresonance.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltDataComponents;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

import java.util.List;

/**
 * Soul Vessel (Empty) — multi-purpose vessel item.
 *
 * Use-on-block: Right-click Soul Fire / Soul Campfire to consume 1 vessel,
 *   extinguish the fire, and receive a Soul Vessel (Fragile).
 *
 * Use-in-air (sneaking):
 *   - If vessel has NO stored gestalt and player is awakened → STORE gestalt into vessel.
 *   - If vessel HAS stored gestalt and player has no active gestalt → RESTORE gestalt to player.
 *
 * Stored gestalt data is kept as a DataComponent (gestaltresonance:stored_gestalt).
 * 20-tick cooldown between store/restore operations.
 */
public class SoulVesselEmptyItem extends Item {

    private static final long SWAP_COOLDOWN_TICKS = 20L;

    public SoulVesselEmptyItem(Properties properties) {
        super(properties);
    }

    // ---- Soul fire charging (existing behavior) ----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        boolean isSoulFire = state.is(Blocks.SOUL_FIRE);
        boolean isSoulCampfire = state.is(Blocks.SOUL_CAMPFIRE)
                && state.hasProperty(CampfireBlock.LIT)
                && state.getValue(CampfireBlock.LIT);

        if (!isSoulFire && !isSoulCampfire) {
            return InteractionResult.PASS;
        }

        // Don't do soul fire charging if this vessel has stored gestalt data
        ItemStack held = context.getItemInHand();
        if (hasStoredGestalt(held)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            // Extinguish
            if (isSoulFire) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else {
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
            }

            // Consume one empty vessel and give fragile vessel
            held.shrink(1);

            var player = context.getPlayer();
            if (player != null) {
                ItemStack fragile = new ItemStack(GestaltResonance.SOUL_VESSEL_FRAGILE.get());
                if (!player.getInventory().add(fragile)) {
                    player.drop(fragile, false);
                }
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ---- Store / Restore gestalt (sneaking + use in air) ----

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            // Return success on client to swing arm; server does the real work
            return InteractionResultHolder.success(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        // Cooldown check
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        long gameTime = level.getGameTime();
        if (gameTime - state.getLastVesselSwapGameTime() < SWAP_COOLDOWN_TICKS) {
            return InteractionResultHolder.pass(stack);
        }

        if (hasStoredGestalt(stack)) {
            return tryRestore(serverPlayer, state, stack, hand, gameTime);
        } else {
            return tryStore(serverPlayer, state, stack, hand, gameTime);
        }
    }

    private InteractionResultHolder<ItemStack> tryStore(ServerPlayer player, PlayerGestaltState state,
                                                         ItemStack stack, InteractionHand hand, long gameTime) {
        // Must be awakened with a type set
        if (!state.isAwakened() || state.getAwakenedGestaltType().isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }

        StoredGestaltData data = StoredGestaltData.of(
                state.getAwakenedGestaltType(), state.getGestaltLevel(), state.getGestaltXp());

        GestaltResonance.LOGGER.debug("[GestaltVessel] STORE BEFORE: awakened={} awakenedGestaltType={} gestaltId={}",
                state.isAwakened(), state.getAwakenedGestaltType(), state.getGestaltId());

        // Clear player gestalt state (level/xp are stored in the vessel data above)
        state.setAwakened(false);
        state.setDormant(false);
        state.setAwakenedGestaltType("");
        state.setPendingGestaltType("");
        state.setConsumedXpPoints(0);
        state.setSummoned(false);
        state.setGestaltId(PlayerGestaltState.NONE);
        state.setGestaltLevel(1);
        state.setGestaltXp(0);
        state.setLastVesselSwapGameTime(gameTime);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncToTracking(player);

        // Handle stack size > 1: split off one vessel and write data to it
        if (stack.getCount() > 1) {
            stack.shrink(1);
            ItemStack boundVessel = new ItemStack(this, 1);
            writeStoredGestalt(boundVessel, data);
            if (!player.getInventory().add(boundVessel)) {
                player.drop(boundVessel, false);
            }
            player.setItemInHand(hand, stack);
        } else {
            writeStoredGestalt(stack, data);
            player.setItemInHand(hand, stack);
        }

        GestaltResonance.LOGGER.debug("[GestaltVessel] STORE AFTER: awakened={} awakenedGestaltType={} gestaltId={}",
                state.isAwakened(), state.getAwakenedGestaltType(), state.getGestaltId());

        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private InteractionResultHolder<ItemStack> tryRestore(ServerPlayer player, PlayerGestaltState state,
                                                           ItemStack stack, InteractionHand hand, long gameTime) {
        // Must have no active gestalt
        if (state.isAwakened() || state.isDormant()
                || !state.getAwakenedGestaltType().isEmpty()
                || !state.getPendingGestaltType().isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }

        StoredGestaltData data = readStoredGestalt(stack);
        if (data == null) {
            return InteractionResultHolder.pass(stack);
        }

        // Restore gestalt to player
        state.setAwakened(true);
        state.setAwakenedGestaltType(data.gestaltType());
        state.setGestaltId(net.minecraft.resources.ResourceLocation.parse(data.gestaltType()));
        state.setGestaltLevel(data.gestaltLevel());
        state.setGestaltXp(data.gestaltXp());
        state.setLastVesselSwapGameTime(gameTime);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncToTracking(player);
        GestaltNetworking.syncGestaltXpToPlayer(player);

        // Clear stored data from vessel
        clearStoredGestalt(stack);
        player.setItemInHand(hand, stack);

        GestaltResonance.LOGGER.debug("[GestaltVessel] RESTORE: player={} awakenedGestaltType={} gestaltId={}",
                player.getName().getString(), state.getAwakenedGestaltType(), state.getGestaltId());

        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    // ---- Display name ----

    @Override
    public Component getName(ItemStack stack) {
        if (hasStoredGestalt(stack)) {
            return Component.translatable("item.gestaltresonance.soul_vessel_filled");
        }
        return super.getName(stack);
    }

    // ---- Tooltip ----

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        StoredGestaltData data = readStoredGestalt(stack);
        if (data != null) {
            tooltipComponents.add(Component.literal("Stored Gestalt: " + data.gestaltType()));
            tooltipComponents.add(Component.literal("Level: " + data.gestaltLevel()));
        }
    }

    // ---- DataComponent helpers ----

    public static boolean hasStoredGestalt(ItemStack stack) {
        return stack.has(GestaltDataComponents.STORED_GESTALT.get());
    }

    public static void writeStoredGestalt(ItemStack stack, StoredGestaltData data) {
        stack.set(GestaltDataComponents.STORED_GESTALT.get(), data);
    }

    public static StoredGestaltData readStoredGestalt(ItemStack stack) {
        return stack.get(GestaltDataComponents.STORED_GESTALT.get());
    }

    public static void clearStoredGestalt(ItemStack stack) {
        stack.remove(GestaltDataComponents.STORED_GESTALT.get());
    }
}
