package net.ragdot.gestaltresonance.common;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Registers mob effects used by the gestalt system.
 * DORMANT_GESTALT is a cosmetic-only effect shown as a status icon
 * while the player's gestalt is in the dormant (XP-draining) phase.
 * INVASIVE_SOUL is a one-time debuff applied when a gestalt first becomes dormant.
 */
public class GestaltMobEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, GestaltResonance.MODID);

    public static final Holder<MobEffect> DORMANT_GESTALT = MOB_EFFECTS.register(
            "dormant_gestalt",
            () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x6A0DAD) {
                // Pure cosmetic effect — no gameplay modifications
            }
    );

    public static final Holder<MobEffect> INVASIVE_SOUL = MOB_EFFECTS.register(
            "invasive_soul",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8B0000) {
                @Override
                public boolean applyEffectTick(LivingEntity entity, int amplifier) {
                    // Nausea is client-driven, but we reapply weakness each tick to ensure
                    // it stays in sync with this effect's remaining duration.
                    return true;
                }

                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return true;
                }

                @Override
                public void onEffectStarted(LivingEntity entity, int amplifier) {
                    entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, INVASIVE_SOUL_DURATION, 0, false, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, INVASIVE_SOUL_DURATION, 0, false, false, false));
                }
            }
    );

    /** Duration for Invasive Soul: 40 seconds. */
    private static final int INVASIVE_SOUL_DURATION = 800;

    /**
     * Ensures the dormant effect is present on the player if they are dormant,
     * and removed if they are not. Uses -1 (infinite) duration so no timer is shown.
     * Call from server tick or login.
     */
    public static void syncDormantEffect(Player player) {
        if (player.level().isClientSide()) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean hasDormantEffect = player.hasEffect(DORMANT_GESTALT);

        if (state.isDormant() && !hasDormantEffect) {
            player.addEffect(new MobEffectInstance(DORMANT_GESTALT, -1, 0, true, false, true));
        } else if (!state.isDormant() && hasDormantEffect) {
            player.removeEffect(DORMANT_GESTALT);
        }
    }

    /**
     * Applies the one-time Invasive Soul debuff when a gestalt first becomes dormant.
     * The effect itself applies weakness and nausea internally via onEffectStarted.
     */
    public static void applyInvasiveSoul(Player player) {
        if (player.level().isClientSide()) return;

        player.addEffect(new MobEffectInstance(INVASIVE_SOUL, INVASIVE_SOUL_DURATION, 0, false, true, true));
    }
}
