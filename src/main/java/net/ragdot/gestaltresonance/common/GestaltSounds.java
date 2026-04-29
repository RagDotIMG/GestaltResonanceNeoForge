package net.ragdot.gestaltresonance.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ragdot.gestaltresonance.GestaltResonance;

public final class GestaltSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, GestaltResonance.MODID);

    /** Plays when a mob is successfully seeded with a fragile soul vessel. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_BIRTH =
            register("gestalt.birth");

    /** Plays when the gestalt is force-unsummoned due to low hunger. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_DISSOLVE =
            register("gestalt.dissolve");

    /** Plays when the player manually summons their gestalt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_SUMMON =
            register("gestalt.summon");

    /** Plays when the player manually dismisses their gestalt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_DISMISS =
            register("gestalt.dismiss");

    /** Plays when the dormant gestalt fully awakens after consuming XP. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_AWAKEN =
            register("gestalt.awaken");

    /** Plays when a guard is broken. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_GUARDBREAK =
            register("gestalt.guardbreak");

    /** Plays on gestalt punch/strike. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_PUNCH =
            register("gestalt.punch");

    /** Plays when the gestalt gains a level. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_LEVELUP =
            register("gestalt.levelup");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () ->
                SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, name)));
    }

    private GestaltSounds() {}
}
