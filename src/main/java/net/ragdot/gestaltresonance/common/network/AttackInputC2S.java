package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: player pressed the attack key while gestalt is summoned. */
public record AttackInputC2S() implements CustomPacketPayload {

    public static final Type<AttackInputC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "attack_input"));

    public static final StreamCodec<ByteBuf, AttackInputC2S> STREAM_CODEC =
            StreamCodec.unit(new AttackInputC2S());

    @Override
    public Type<AttackInputC2S> type() {
        return TYPE;
    }
}
