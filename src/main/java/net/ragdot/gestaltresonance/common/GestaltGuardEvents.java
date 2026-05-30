package net.ragdot.gestaltresonance.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.GestaltResonanceEvents;

/**
 * Server-side guard event hooks.
 *
 * Guard activation is driven by StartGuardC2S (sent by the client when it detects
 * a low-priority right-click). This class:
 *  - swallows any server-side interaction events that arrive while guarding
 *    (safety net for edge cases where the client packet races ahead)
 *  - applies damage reduction while guarding, accumulates absorbed damage,
 *    and triggers a guard break when the tracker or hunger threshold is exceeded
 */
public class GestaltGuardEvents {

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;
        GestaltAction action = state.getAction();
        if (action == GestaltAction.HIT_1 || action == GestaltAction.HIT_2 || action == GestaltAction.HIT_3
                || action == GestaltAction.CHARGED_STRIKE_WINDUP
                || action == GestaltAction.CHARGED_STRIKE_TRAVEL) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isThrowable(event.getItemStack())) return;
        cancelIfGuarding(event, event.getEntity());
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfGuarding(event, event.getEntity());
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfGuarding(event, event.getEntity());
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned() || !state.isGuarding()) return;
        // Guard only blocks entity attacks and projectiles — never fall, fire, lava, drowning, etc.
        if (event.getSource().getEntity() == null) return;

        // Guard break: hunger too low to sustain the guard
        if (player.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) {
            triggerGuardBreak(player, state, event.getSource());
            return; // guard overwhelmed — full damage passes through
        }

        // 35% base + 1% per gestalt level + 10% per durability stat
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int durability = (stats != null) ? stats.durability() : 0;
        float multiplier = Math.max(0.0f, 0.65f - state.getGestaltLevel() * 0.01f - durability * 0.10f);
        float absorbed = event.getAmount() * (1.0f - multiplier);

        // Guard break: accumulated absorbed damage exceeded the threshold
        if (state.getGuardDamageAccumulated() + absorbed >= GestaltCosts.GUARD_BREAK_DAMAGE_THRESHOLD) {
            triggerGuardBreak(player, state, event.getSource());
            return; // guard overwhelmed — full damage passes through
        }

        // Parry: guard activated within window → full absorption, resonance reward
        long now = player.getServer().getTickCount();
        long ticksSinceGuard = now - state.getGuardActivatedTick();
        boolean isParry = state.getGuardActivatedTick() >= 0
                && ticksSinceGuard >= GestaltCosts.GAIN_PARRY_WINDOW_MIN
                && ticksSinceGuard <= GestaltCosts.GAIN_PARRY_WINDOW_MAX;

        if (isParry) {
            event.setAmount(0f);
            player.causeFoodExhaustion(GestaltCosts.GUARD_ACTIVATION);
            GestaltResonanceEvents.applyResonance(player, GestaltCosts.GAIN_PARRY);
            return;
        }

        // Normal guard: apply reduction, accumulate, drain hunger
        event.setAmount(event.getAmount() * multiplier);
        state.addGuardDamageAccumulated(absorbed);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        player.causeFoodExhaustion(GestaltCosts.GUARD_ACTIVATION);
        GestaltResonanceEvents.applyResonance(player, -GestaltCosts.LOSS_GUARD_ABSORB);
    }

    private static void triggerGuardBreak(ServerPlayer player, PlayerGestaltState state, DamageSource source) {
        state.clearGuard();
        state.resetGuardDamageAccumulated();
        state.setGuardCooldownUntilTick(
                player.getServer().getTickCount() + GestaltCosts.GUARD_BREAK_COOLDOWN_TICKS);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        player.playNotifySound(GestaltSounds.GESTALT_HEAVY_IMPACT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        GestaltResonanceEvents.applyResonance(player, -15);

        // Knock player back away from the hit source
        Entity sourceEntity = source.getEntity();
        if (sourceEntity != null) {
            player.knockback(1.0,
                    sourceEntity.getX() - player.getX(),
                    sourceEntity.getZ() - player.getZ());
        }

        GestaltNetworking.syncGuardToTracking(player, false);
    }

    private static void cancelIfGuarding(net.neoforged.bus.api.ICancellableEvent event, Player player) {
        if (event.isCanceled()) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;
        GestaltAction action = state.getAction();
        if (state.isGuarding()
                || action == GestaltAction.CHARGED_STRIKE_WINDUP) {
            event.setCanceled(true);
        }
    }

    /** Items that should bypass the guard block and be throwable while guarding. */
    private static boolean isThrowable(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof SnowballItem        // snowball
            || item instanceof ExperienceBottleItem // bottle o' enchanting
            || item == Items.EGG
            || item == Items.ENDER_PEARL
            || item == Items.FIRE_CHARGE
            || item == Items.SPLASH_POTION
            || item == Items.LINGERING_POTION;
    }
}
