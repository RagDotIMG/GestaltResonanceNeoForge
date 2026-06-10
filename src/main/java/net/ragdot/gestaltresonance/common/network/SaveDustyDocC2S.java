package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.WritableBookContent;
import net.ragdot.gestaltresonance.GestaltResonance;

import java.util.List;
import java.util.Optional;

public record SaveDustyDocC2S(int slot, List<String> pages, Optional<String> title)
        implements CustomPacketPayload {

    public static final Type<SaveDustyDocC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "save_dusty_doc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveDustyDocC2S> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SaveDustyDocC2S::slot,
                    ByteBufCodecs.stringUtf8(WritableBookContent.PAGE_EDIT_LENGTH)
                            .apply(ByteBufCodecs.list(WritableBookContent.MAX_PAGES)),
                    SaveDustyDocC2S::pages,
                    ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(128)),
                    SaveDustyDocC2S::title,
                    SaveDustyDocC2S::new
            );

    @Override
    public Type<SaveDustyDocC2S> type() { return TYPE; }
}
