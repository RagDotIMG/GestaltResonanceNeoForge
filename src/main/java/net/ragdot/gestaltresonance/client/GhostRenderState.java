package net.ragdot.gestaltresonance.client;

import net.ragdot.gestaltresonance.common.GestaltCosts;

/**
 * Shared render-frame state for ghost/translucency effects.
 * Set and cleared by {@code LivingEntityRendererTranslucencyMixin} for the duration
 * of each {@code LivingEntityRenderer.render()} call; read by
 * {@code EntityModelGhostMixin} to apply the same alpha to all render layers.
 */
public final class GhostRenderState {

    private GhostRenderState() {}

    /** True while rendering a ghost/translucent entity. */
    public static final ThreadLocal<Boolean> projecting = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 0 = none, 1 = soul projection, 2 = phase out, 3 = phase court, 4 = float play, 5 = time phase. */
    public static final ThreadLocal<Integer> translucencyMode = ThreadLocal.withInitial(() -> 0);
    /** Non-null while rendering a SpawnIllusionEntity; packed ARGB tint. */
    public static final ThreadLocal<Integer> illusionArgb = ThreadLocal.withInitial(() -> null);
    /** Alpha (0–255) for Float Play Spot Late; -1 when inactive. */
    public static final ThreadLocal<Integer> floatPlayAlpha = ThreadLocal.withInitial(() -> -1);

    public static final int ALPHA_PROJECTION  = 0x4C << 24;
    public static final int ALPHA_PHASE_OUT   = 0x0D << 24;
    public static final int ALPHA_PHASE_COURT = GestaltCosts.PHASE_COURT_GHOST_ALPHA << 24;

    /** Returns the packed ARGB color with alpha applied, or -1 if no ghost effect is active. */
    public static int resolveAlpha() {
        Integer illusion = illusionArgb.get();
        if (illusion != null) return illusion;
        if (projecting.get() == Boolean.TRUE) {
            return switch (translucencyMode.get()) {
                case 2  -> ALPHA_PHASE_OUT;
                case 3  -> ALPHA_PHASE_COURT;
                case 4  -> floatPlayAlpha.get() << 24;
                default -> ALPHA_PROJECTION;
            };
        }
        return -1;
    }

    public static void clear() {
        projecting.remove();
        translucencyMode.remove();
        illusionArgb.remove();
        floatPlayAlpha.remove();
    }
}
