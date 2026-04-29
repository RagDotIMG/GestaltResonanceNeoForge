package net.ragdot.gestaltresonance.common;

import java.util.function.Supplier;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.ragdot.gestaltresonance.GestaltResonance;

public class GestaltAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, GestaltResonance.MODID);

    public static final Supplier<AttachmentType<PlayerGestaltState>> PLAYER_GESTALT_STATE =
            ATTACHMENT_TYPES.register("player_gestalt_state", () ->
                    AttachmentType.builder(PlayerGestaltState::new)
                            .serialize(PlayerGestaltState.CODEC)
                            .copyOnDeath()
                            .build()
            );

    public static final Supplier<AttachmentType<MobSeededData>> MOB_SEEDED_DATA =
            ATTACHMENT_TYPES.register("mob_seeded_data", () ->
                    AttachmentType.builder(MobSeededData::new)
                            .serialize(MobSeededData.CODEC)
                            .build()
            );
}
