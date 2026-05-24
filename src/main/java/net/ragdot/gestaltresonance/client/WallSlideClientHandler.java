package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.GestaltThrowEvents;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.ThrowInputC2S;

import javax.annotation.Nullable;

/**
 * Client-side wall slide input handler.
 *
 * While the server reports WALL_SLIDE:
 *   - Freeze all horizontal movement input so the player stays against the wall.
 *   - Detect a fresh SPACE press and fire the throw (sneak is already held).
 *
 * The server drives all physics; this handler only suppresses conflicting client input
 * and bridges the "SPACE while airborne" case that won't trigger LivingJumpEvent.
 */
public class WallSlideClientHandler {

    private static boolean wasSpaceDown = false;
    private static boolean wasSliding = false;
    @Nullable private static SoundInstance wallSlideSoundInstance = null;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean isSliding = state.isWallSliding();
        boolean spaceDown = mc.options.keyJump.isDown();

        if (isSliding && !wasSliding) {
            wallSlideSoundInstance = new SimpleSoundInstance(
                    GestaltSounds.GESTALT_WALLSLIDE.get(),
                    SoundSource.PLAYERS,
                    1.0f, 1.0f,
                    RandomSource.create(),
                    player.getX(), player.getY(), player.getZ()
            );
            mc.getSoundManager().play(wallSlideSoundInstance);
        } else if (!isSliding && wasSliding && wallSlideSoundInstance != null) {
            mc.getSoundManager().stop(wallSlideSoundInstance);
            wallSlideSoundInstance = null;
        }
        wasSliding = isSliding;

        if (isSliding) {
            // Freeze horizontal input — server controls position
            player.input.leftImpulse = 0;
            player.input.forwardImpulse = 0;
            player.input.up = false;
            player.input.down = false;
            player.input.left = false;
            player.input.right = false;
            player.input.jumping = false;

            // Fresh SPACE press while wall sliding → throw
            if (spaceDown && !wasSpaceDown) {
                Vec3 velocity = GestaltThrowEvents.computeThrowVelocity(player, state);
                player.setDeltaMovement(velocity);
                state.setThrowOrigin(player.getX(), player.getY(), player.getZ(), player.yBodyRot);
                state.setAction(GestaltAction.THROW);
                state.setThrowFallProtection(true);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                PacketDistributor.sendToServer(new ThrowInputC2S());
            }

            wasSpaceDown = spaceDown;
            return;
        }

        wasSpaceDown = spaceDown;
    }
}
