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

    /** Plays on guard break and fall break impact. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_HEAVY_IMPACT =
            register("gestalt.heavy_impact");

    /** Plays when a gestalt action fails (summoning blocked, power denied, etc.). */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_FAIL =
            register("gestalt.fail");

    /** Plays when the gestalt gains a level. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_LEVELUP =
            register("gestalt.levelup");

    /** Plays when Amen Break's Phase Out (2G) activates. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_AB_2G =
            register("gestalt.ab_2g");

    /** Plays when each hit of the gestalt attack chain starts. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_HIT =
            register("gestalt.hit");

    /** Plays when a gestalt explosion detonates. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_EXPLOSION =
            register("gestalt.explosion");

    /** Plays when the player attaches to a wall slide. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GESTALT_WALLSLIDE =
            register("gestalt.wallslide");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () ->
                SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, name)));
    }

    private GestaltSounds() {}
}
