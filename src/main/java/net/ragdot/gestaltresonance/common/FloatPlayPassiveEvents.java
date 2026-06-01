package net.ragdot.gestaltresonance.common;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

public class FloatPlayPassiveEvents {

    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned() || !GestaltIds.FLOAT_PLAY.equals(state.getGestaltId())) return;
        float t = GestaltCosts.spotLateScale(player.getFoodData().getFoodLevel());
        if (t <= 0f) return;
        float reduction = t * GestaltCosts.FLOAT_PLAY_FALL_DISTANCE_REDUCTION;
        event.setDistance(Math.max(0f, event.getDistance() - reduction));
    }

    @SubscribeEvent
    public void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned() || !GestaltIds.FLOAT_PLAY.equals(state.getGestaltId())) return;
        float t = GestaltCosts.spotLateScale(player.getFoodData().getFoodLevel());
        if (t <= 0f) return;
        double multiplier = 1.0 - t * (1.0 - GestaltCosts.FLOAT_PLAY_MIN_VISIBILITY);
        event.modifyVisibility(multiplier);
    }
}
