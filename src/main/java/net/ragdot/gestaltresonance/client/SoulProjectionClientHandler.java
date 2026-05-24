package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.SoulProjectionExitType;
import net.ragdot.gestaltresonance.common.network.SoulProjectionYankS2C;

public class SoulProjectionClientHandler {

    private static int heartbeatCounter = 0;

    private static float shakeStrength = 0f;
    private static float shakeStrengthO = 0f;

    private static WhisperSound activeWhisper = null;

    private static class WhisperSound extends AbstractTickableSoundInstance {
        WhisperSound() {
            super(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.6f;
            this.pitch = 1.0f;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) { this.stop(); return; }
            PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isSoulProjecting()) this.stop();
        }
    }

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
            if (activeWhisper == null || activeWhisper.isStopped()) {
                activeWhisper = new WhisperSound();
                mc.getSoundManager().play(activeWhisper);
            }
        } else {
            heartbeatCounter = 0;
            activeWhisper = null; // let the tick() stop it naturally
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
