package net.ragdot.gestaltresonance.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.player.LocalPlayer;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltThrowEvents;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.client.SoulProjectionClientHandler;
import net.ragdot.gestaltresonance.common.SoulProjectionExitType;
import net.ragdot.gestaltresonance.common.network.PowerActivateC2S;
import net.ragdot.gestaltresonance.common.network.SoulProjectionActivateC2S;
import net.ragdot.gestaltresonance.common.network.SoulProjectionExitC2S;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;
import net.ragdot.gestaltresonance.common.network.StartChannelXpC2S;
import net.ragdot.gestaltresonance.common.network.StartGuardC2S;
import net.ragdot.gestaltresonance.common.network.StopChannelXpC2S;
import net.ragdot.gestaltresonance.common.network.StopGuardC2S;
import net.ragdot.gestaltresonance.common.network.ThrowInputC2S;
import net.ragdot.gestaltresonance.common.network.ToggleSummonC2S;
import org.lwjgl.glfw.GLFW;

public class GestaltKeybinds {

    public static final String CATEGORY = "key.categories.gestaltresonance";

    public static final Lazy<KeyMapping> SUMMON_TOGGLE = Lazy.of(() -> new KeyMapping(
            "key.gestaltresonance.summon_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            CATEGORY
    ));

    public static final Lazy<KeyMapping> POWER_1 = Lazy.of(() -> new KeyMapping(
            "key.gestaltresonance.power_1",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z,
            CATEGORY
    ));

    public static final Lazy<KeyMapping> POWER_2 = Lazy.of(() -> new KeyMapping(
            "key.gestaltresonance.power_2",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X,
            CATEGORY
    ));

    public static final Lazy<KeyMapping> POWER_3 = Lazy.of(() -> new KeyMapping(
            "key.gestaltresonance.power_3",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C,
            CATEGORY
    ));

    // True after sending StartGuardC2S; prevents spam before TriggerGuardS2C arrives.
    private static boolean guardInitiated = false;

    // Sneak+G chord state. We disambiguate tap (open management screen) from hold (XP channel)
    // using a 10-tick threshold. The 10 ticks are now client-side (windup happens here, not on server).
    private static final int CHANNEL_WINDUP_TICKS = 10;
    private static int sneakGHoldTicks = 0;
    private static boolean channelInitiated = false;

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SUMMON_TOGGLE.get());
        event.register(POWER_1.get());
        event.register(POWER_2.get());
        event.register(POWER_3.get());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean sneakHeld = mc.options.keyShift.isDown();
        boolean gHeld = SUMMON_TOGGLE.get().isDown();
        boolean chordHeld = sneakHeld && gHeld;
        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (chordHeld) {
            sneakGHoldTicks++;
            if (sneakGHoldTicks == CHANNEL_WINDUP_TICKS && !channelInitiated) {
                PacketDistributor.sendToServer(new StartChannelXpC2S());
                channelInitiated = true;
            }
        } else {
            // Chord released. Decide between channel-stop and tap-open.
            if (channelInitiated) {
                PacketDistributor.sendToServer(new StopChannelXpC2S());
                channelInitiated = false;
            } else if (sneakGHoldTicks > 0 && sneakGHoldTicks < CHANNEL_WINDUP_TICKS) {
                net.ragdot.gestaltresonance.client.gui.GestaltManagementScreen.openIfEligible();
            }
            sneakGHoldTicks = 0;
        }

        // Drain G clicks: priority order — exit projection > activate projection > toggle summon.
        while (SUMMON_TOGGLE.get().consumeClick()) {
            if (state.isSoulProjecting()) {
                PacketDistributor.sendToServer(new SoulProjectionExitC2S(SoulProjectionExitType.EMERGENCY.toByte()));
            } else if (state.isGuarding()) {
                PacketDistributor.sendToServer(new SoulProjectionActivateC2S());
            } else if (!sneakHeld) {
                PacketDistributor.sendToServer(new ToggleSummonC2S());
            }
        }
        while (POWER_1.get().consumeClick()) {
            PacketDistributor.sendToServer(new PowerActivateC2S(GestaltPowerSlot.POWER_1.toByte()));
        }
        while (POWER_2.get().consumeClick()) {
            mc.player.displayClientMessage(Component.literal("[GestaltResonance] Power 2 pressed"), false);
        }
        while (POWER_3.get().consumeClick()) {
            mc.player.displayClientMessage(Component.literal("[GestaltResonance] Power 3 pressed"), false);
        }

        // When right-click released: stop guard and reset the initiated flag
        if (!mc.options.keyUse.isDown()) {
            guardInitiated = false;
            if (state.isGuarding() && !state.isSoulProjecting()) {
                PacketDistributor.sendToServer(new StopGuardC2S());
            }
        }

        // Once the server confirms guard is active, clear the pending flag
        if (state.isGuarding()) {
            guardInitiated = false;
        }

        // Halt any in-progress block destruction while the gestalt owns left-click. Vanilla
        // continues an already-started destroy each tick from keyAttack.isDown(); cancelling the
        // initial press isn't enough if the user was already mining when the action started.
        GestaltAction currentAction = state.getAction();
        if (currentAction == GestaltAction.HIT_1
                || currentAction == GestaltAction.HIT_2
                || currentAction == GestaltAction.HIT_3
                || currentAction == GestaltAction.CHARGED_STRIKE_WINDUP
                || currentAction == GestaltAction.CHARGED_STRIKE_TRAVEL
                || currentAction == GestaltAction.POWER_1G_WINDUP) {
            if (mc.gameMode != null && mc.gameMode.isDestroying()) {
                mc.gameMode.stopDestroyBlock();
            }
        }

        SoulProjectionClientHandler.tick();
        WallSlideClientHandler.tick();
        LedgeGrabClientHandler.tick();
    }

    /**
     * Fires each time the USE key mapping is triggered (including when looking at nothing).
     * If the gestalt is summoned and the interaction is low-priority, cancel it and start guard.
     */
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;
        if (state.isSoulProjecting()) return;

        // Suppress vanilla attack (and the block-break it would start) while the gestalt is
        // in any combat action — chain hits, charged-strike windup, travel, or strike. The
        // chain advances via keyAttack.consumeClick() in GestaltAttackClientEvents, which is
        // independent of this event, so chain advancement keeps working.
        if (event.isAttack()) {
            GestaltAction action = state.getAction();
            if (action == GestaltAction.HIT_1
                    || action == GestaltAction.HIT_2
                    || action == GestaltAction.HIT_3
                    || action == GestaltAction.CHARGED_STRIKE_WINDUP
                    || action == GestaltAction.CHARGED_STRIKE_TRAVEL
                    || action == GestaltAction.POWER_1G_WINDUP) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
            return;
        }

        if (!event.isUseItem()) return;

        // Block guard initiation during power windups — the power has priority over guard.
        if (state.getAction() == GestaltAction.POWER_1G_WINDUP) return;

        // Already guarding or waiting for server confirmation: don't cancel here.
        // GestaltGuardEvents.cancelIfGuarding handles the server-side PlayerInteractEvent,
        // which correctly suppresses the arm swing. Canceling InteractionKeyMappingTriggered
        // here instead causes the arm-swing animation to play on repeat.
        if (state.isGuarding() || guardInitiated) return;

        // High-priority items always pass through to vanilla
        if (isHighPriorityItem(mc.player.getMainHandItem())) return;

        // High-priority targets pass through
        if (mc.hitResult instanceof EntityHitResult ehr && isHighPriorityEntity(ehr.getEntity())) return;
        if (mc.hitResult instanceof BlockHitResult bhr
                && mc.level != null
                && isInteractiveBlock(mc.level, bhr.getBlockPos())) return;

        // Low-priority interaction — cancel it and start guard
        event.setCanceled(true);
        guardInitiated = true;
        PacketDistributor.sendToServer(new StartGuardC2S());
    }

    // ── Priority helpers (client-side mirror of GestaltGuardEvents) ───────────

    private static boolean isHighPriorityItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (stack.has(DataComponents.FOOD)) return true;
        if (item instanceof PotionItem) return true;
        if (stack.is(Items.MILK_BUCKET)) return true;
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) return true;
        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) return true;
        if (item instanceof FishingRodItem) return true;
        if (item instanceof LeadItem || item instanceof NameTagItem) return true;
        if (item instanceof SpyglassItem || item instanceof ShearsItem) return true;
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) return true;
        if (stack.is(Items.MAP) || stack.is(Items.FILLED_MAP)) return true;
        return false;
    }

    private static boolean isHighPriorityEntity(Entity entity) {
        return entity instanceof Villager
                || entity instanceof WanderingTrader
                || entity instanceof AbstractHorse
                || entity instanceof Llama
                || entity instanceof ItemFrame
                || entity instanceof ArmorStand
                || entity instanceof Painting;
    }

    /**
     * Fires after vanilla {@code jumpFromGround} has already overwritten vy with JUMP_POWER (0.42).
     * If the player is sneaking and the gestalt is summoned + idle, replace the vanilla jump with
     * the throw velocity. Vanilla jump only sets vy, leaving horizontal velocity intact, but our
     * throw needs full control over all three components — so we compute and apply our own here,
     * then notify the server which will sync to other clients.
     */
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player) return;
        if (!mc.options.keyShift.isDown()) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned() || state.getAction() != GestaltAction.IDLE) return;

        Vec3 velocity = GestaltThrowEvents.computeThrowVelocity(player, state);
        player.setDeltaMovement(velocity);

        state.setThrowOrigin(player.getX(), player.getY(), player.getZ(), player.yBodyRot);
        state.setAction(GestaltAction.THROW);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        PacketDistributor.sendToServer(new ThrowInputC2S());
    }

    private static boolean isInteractiveBlock(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MenuProvider) return true;
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof EnderChestBlock) return true;
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)) return true;
        if (state.is(BlockTags.BEDS)) return true;
        if (block instanceof ButtonBlock || block instanceof LeverBlock) return true;
        return false;
    }
}
