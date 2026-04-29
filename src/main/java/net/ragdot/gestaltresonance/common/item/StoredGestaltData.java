package net.ragdot.gestaltresonance.common.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Immutable record stored as a DataComponent on a Soul Vessel ItemStack.
 * Holds the gestalt type key and optional level/xp when a player stores
 * their awakened gestalt into the vessel.
 *
 * Component key: gestaltresonance:stored_gestalt
 */
public record StoredGestaltData(String gestaltType, int gestaltLevel, int gestaltXp) {

    public static final Codec<StoredGestaltData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("gestaltType").forGetter(StoredGestaltData::gestaltType),
            Codec.INT.fieldOf("gestaltLevel").forGetter(StoredGestaltData::gestaltLevel),
            Codec.INT.fieldOf("gestaltXp").forGetter(StoredGestaltData::gestaltXp)
    ).apply(inst, StoredGestaltData::new));

    public static StoredGestaltData of(String type, int level, int xp) {
        return new StoredGestaltData(type, level, xp);
    }
}
