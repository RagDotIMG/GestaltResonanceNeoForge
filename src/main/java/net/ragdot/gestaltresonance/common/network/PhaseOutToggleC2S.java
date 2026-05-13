package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: toggle Phase Out armed state. Sent when X is pressed while guarding. */
public record PhaseOutToggleC2S() implements CustomPacketPayload {

    public static final Type<PhaseOutToggleC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "phase_out_toggle"));

    public static final StreamCodec<ByteBuf, PhaseOutToggleC2S> STREAM_CODEC =
            StreamCodec.unit(new PhaseOutToggleC2S());

    @Override
    public Type<PhaseOutToggleC2S> type() {
        return TYPE;
    }
}
