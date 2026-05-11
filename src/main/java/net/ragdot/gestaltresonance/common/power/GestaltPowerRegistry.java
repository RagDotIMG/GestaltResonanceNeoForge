package net.ragdot.gestaltresonance.common.power;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;

/**
 * Static dispatch table for gestalt powers. Each power registers an activator at class-init
 * time. {@link #tryActivate} looks up the (gestaltId, slot, modifier) triple and invokes the
 * matching activator if one exists; otherwise it's a silent no-op.
 *
 * The registry is intentionally minimal — no interface hierarchy, no power lifecycle hooks.
 * Each power class owns its own state, ticks, and event handlers. Registration is only how
 * the activation packet is routed.
 */
public final class GestaltPowerRegistry {

    private static final Map<GestaltPowerKey, Consumer<ServerPlayer>> ACTIVATORS = new HashMap<>();

    private GestaltPowerRegistry() {}

    /** Register an activator for the given key. Last registration wins. */
    public static void register(GestaltPowerKey key, Consumer<ServerPlayer> activator) {
        ACTIVATORS.put(key, activator);
    }

    /** Returns true if a power was found and dispatched. */
    public static boolean tryActivate(ServerPlayer player, GestaltPowerKey key) {
        Consumer<ServerPlayer> activator = ACTIVATORS.get(key);
        if (activator == null) return false;
        activator.accept(player);
        return true;
    }
}
