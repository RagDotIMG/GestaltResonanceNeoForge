package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Server-to-client: remove a single afterimage by id (used by Phase Court dragback). */
public record DiscardAfterimageS2C(int id) implements CustomPacketPayload {

    public static final Type<DiscardAfterimageS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "discard_afterimage"));

    public static final StreamCodec<ByteBuf, DiscardAfterimageS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DiscardAfterimageS2C::id,
                    DiscardAfterimageS2C::new
            );

    @Override
    public Type<DiscardAfterimageS2C> type() { return TYPE; }
}
