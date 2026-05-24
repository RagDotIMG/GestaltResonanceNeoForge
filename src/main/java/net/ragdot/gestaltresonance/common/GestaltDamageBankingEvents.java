package net.ragdot.gestaltresonance.common;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.ragdot.gestaltresonance.common.entity.PhaseMineEntity;

/**
 * Intercepts incoming damage for entities currently in a Phase Mine's marking phase,
 * banks the damage into the mine, and zeroes the event so the entity takes no direct hit.
 * The banked total is released as bonus damage on the final drag-back explosion.
 */
public class GestaltDamageBankingEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity target = event.getEntity();
        PhaseMineEntity mine = PhaseMineEntity.getMarkingMine(target.getUUID());
        if (mine == null) return;
        int state = mine.getState();
        // Bank damage during marking phase and during dragback (intermediate explosions).
        // Skip banking only when the mine is actively releasing the final explosion.
        if (state != PhaseMineEntity.STATE_MARKING && state != PhaseMineEntity.STATE_DRAGBACK) return;
        if (mine.isReleasingFinalDamage()) return;
        mine.bankDamage(event.getAmount());
        event.setAmount(0f);
    }
}
