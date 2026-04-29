package net.ragdot.gestaltresonance.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Data attachment for LivingEntity tracking whether it has been "seeded"
 * with a Soul Vessel (Fragile). The seedTypeKey stores the entity type
 * registry key (e.g. "minecraft:zombie") so the resulting gestalt
 * reflects the mob type.
 */
public class MobSeededData {

    public static final Codec<MobSeededData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.fieldOf("seeded").forGetter(d -> d.seeded),
            Codec.STRING.fieldOf("seedTypeKey").forGetter(d -> d.seedTypeKey)
    ).apply(inst, MobSeededData::new));

    private boolean seeded;
    private String seedTypeKey;

    public MobSeededData() {
        this(false, "");
    }

    public MobSeededData(boolean seeded, String seedTypeKey) {
        this.seeded = seeded;
        this.seedTypeKey = seedTypeKey;
    }

    public boolean isSeeded() { return seeded; }
    public void setSeeded(boolean seeded) { this.seeded = seeded; }
    public String getSeedTypeKey() { return seedTypeKey; }
    public void setSeedTypeKey(String seedTypeKey) { this.seedTypeKey = seedTypeKey; }

    public MobSeededData copy() {
        return new MobSeededData(seeded, seedTypeKey);
    }
}
