package net.ragdot.gestaltresonance.common.entity;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

public class BodyDoubleHitEvent extends Event {

    private final UUID ownerUuid;
    private final DamageSource source;
    private final float amount;
    private final Level level;

    public BodyDoubleHitEvent(UUID ownerUuid, DamageSource source, float amount, Level level) {
        this.ownerUuid = ownerUuid;
        this.source = source;
        this.amount = amount;
        this.level = level;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
    public MinecraftServer getServer() { return level.getServer(); }
}
