package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.SoulProjectionExitType;
import net.ragdot.gestaltresonance.common.network.SoulProjectionYankS2C;

public class SoulProjectionClientHandler {

    private static int heartbeatCounter = 0;

    private static float shakeStrength = 0f;
    private static float shakeStrengthO = 0f;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        shakeStrengthO = shakeStrength;
        shakeStrength = Math.max(0f, shakeStrength - 0.05f);

        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isSoulProjecting()) {
            heartbeatCounter++;
            if (heartbeatCounter >= 40) {
                if (mc.level != null) {
                    mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.5f, 1.0f, false);
                }
                heartbeatCounter = 0;
            }
        } else {
            heartbeatCounter = 0;
        }
    }

    public static void triggerActivationShake() {
        shakeStrength = 3.0f;
    }

    public static void onYank(SoulProjectionYankS2C packet) {
        SoulProjectionExitType exitType = SoulProjectionExitType.fromByte(packet.exitType());
        // CLEAN = light shake, no sound; everything else = heavy + sonic boom
        shakeStrength = exitType == SoulProjectionExitType.CLEAN ? 2.0f : 6.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && exitType != SoulProjectionExitType.CLEAN) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 0.6f, 0.8f, false);
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeStrength <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        float partialTick = (float) event.getPartialTick();
        float s = shakeStrengthO + (shakeStrength - shakeStrengthO) * partialTick;
        double time = (mc.level != null ? mc.level.getGameTime() : 0L) + partialTick;
        event.setPitch((float) (event.getPitch() + Math.sin(time * 3.0) * s));
        event.setYaw((float) (event.getYaw() + Math.cos(time * 2.5) * s * 0.5f));
    }
}
