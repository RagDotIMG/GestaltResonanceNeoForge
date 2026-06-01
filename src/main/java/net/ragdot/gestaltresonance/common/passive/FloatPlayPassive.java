package net.ragdot.gestaltresonance.common.passive;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltCosts;

/**
 * Float Play passive: Spot Late.
 * Scales mob visibility, render opacity, gravity, and fall damage reduction
 * inversely with hunger — effects maximise at 6 hunger, absent at full hunger.
 *
 * Gravity is applied via an Attributes.GRAVITY transient modifier.
 * Render opacity is handled client-side by LivingEntityRendererTranslucencyMixin.
 * Mob visibility is handled by PlayerVisibilityMixin.
 * Fall distance reduction is handled by FloatPlayPassiveEvents.
 */
public class FloatPlayPassive implements GestaltPassive {

    private static final ResourceLocation GRAVITY_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "float_play_gravity");

    @Override
    public void tick(ServerPlayer player) {
        float t = GestaltCosts.spotLateScale(player.getFoodData().getFoodLevel());
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;
        if (t <= 0f) {
            gravity.removeModifier(GRAVITY_MODIFIER_ID);
        } else {
            double reduction = -t * GestaltCosts.FLOAT_PLAY_GRAVITY_REDUCTION;
            gravity.addOrUpdateTransientModifier(new AttributeModifier(
                    GRAVITY_MODIFIER_ID, reduction, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void onActivate(ServerPlayer player) {
        tick(player);
    }

    @Override
    public void onDeactivate(ServerPlayer player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) gravity.removeModifier(GRAVITY_MODIFIER_ID);
    }
}
