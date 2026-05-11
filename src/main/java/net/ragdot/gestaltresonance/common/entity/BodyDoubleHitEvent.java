package net.ragdot.gestaltresonance.common.entity;

import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;

public class BodyDoubleHitEvent extends Event {

    private final UUID ownerUuid;
    private final DamageSource source;
    private final float amount;

    public BodyDoubleHitEvent(UUID ownerUuid, DamageSource source, float amount) {
        this.ownerUuid = ownerUuid;
        this.source = source;
        this.amount = amount;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
}
