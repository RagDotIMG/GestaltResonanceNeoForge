package net.ragdot.gestaltresonance.common;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Set;

/**
 * Central registry of all hunger/exhaustion costs for Gestalt actions.
 * Tune every drain number here; no other file should define raw exhaustion values.
 * Keep the order of entires consistent with the already established order, with new gestalten added at the end.
 */
public final class GestaltCosts {

    // ─────────────────────────────────────────────────────────────────────────
    // General gestalt
    // ─────────────────────────────────────────────────────────────────────────

    /** Exhaustion applied every {@link #SUMMON_DRAIN_INTERVAL} ticks while a Gestalt is summoned. */
    public static final float SUMMON_DRAIN = 0.5f;

    /** How often (in ticks) the summon drain fires. 200 ticks = 10 seconds. */
    public static final int SUMMON_DRAIN_INTERVAL = 200;

    /** Exhaustion applied each time the guard absorbs incoming damage. */
    public static final float GUARD_ACTIVATION = 1.5f;

    /** Food level (inclusive) at or below which the gestalt force-unsummons. */
    public static final int CRASH_HUNGER_THRESHOLD = 6;

    /** Ticks the gestalt stays locked out after a hunger crash before it can be re-summoned. */
    public static final int CRASH_COOLDOWN_TICKS = 660;

    /** Accumulated absorbed damage required to trigger a guard break. */
    public static final float GUARD_BREAK_DAMAGE_THRESHOLD = 13f;

    /** Maximum damage that can pass through the guard in a single hit (base, before durability scaling). */
    public static final float GUARD_MAX_THROUGH_BASE = 25f;
    /** Damage subtracted from the cap per point of durability stat. */
    public static final float GUARD_MAX_THROUGH_PER_DUR = 2f;

    /** Ticks the guard ability is locked out after a guard break. */
    public static final int GUARD_BREAK_COOLDOWN_TICKS = 280;

    /** Ticks between each guard damage decay pulse (1 point per interval). */
    public static final int GUARD_DECAY_INTERVAL = 6;

    // ── Passive fall break (any summoned fall, not just after throw) ──

    /** Raw fall distance (blocks) below which the passive fall break does not activate. */
    public static final float FALL_BREAK_MIN_DISTANCE = 3.0f;

    /** Flat blocks subtracted from raw fall distance during a passive fall break. */
    public static final float FALL_BREAK_DISTANCE_REDUCTION = 3.5f;

    /** Remaining-damage multiplier after distance reduction during a passive fall break. */
    public static final float FALL_BREAK_DAMAGE_MULTIPLIER = 0.7f;

    /** Raw fall distance at or above which the gestalt crashes while breaking the fall. */
    public static final float FALL_BREAK_CRASH_DISTANCE = 23.0f;

    // ── Hit chain ──

    /** Cooldown (ticks) applied after the full 3-hit chain completes. Shared with charged strike cooldowns. */
    public static final int CHAIN_COOLDOWN_TICKS = 45;

    public static final float  HIT_1_DAMAGE_MULTIPLIER = 0.6f;
    public static final float  HIT_3_DAMAGE_MULTIPLIER = 1.4f;

    public static final double HIT_1_KNOCKBACK = 0.2;
    public static final double HIT_2_KNOCKBACK = 0.2;
    public static final double HIT_3_KNOCKBACK = 0.3;

    // ── Charged strike ──

    /** Ticks the player must hold left-click before the charge is releasable as a fire (otherwise it aborts). */
    public static final int CHARGED_STRIKE_WINDUP_TICKS = 20;

    /** Cooldown applied after a successful hit. Shared with the standard hit chain. */
    public static final int CHARGED_STRIKE_HIT_COOLDOWN_TICKS = 50;

    /** Cooldown applied when the target dies while the gestalt is mid-travel. */
    public static final int CHARGED_STRIKE_DEATH_WHIFF_COOLDOWN_TICKS = 10;

    /** Cooldown applied when the player cancels travel via a fresh right-click (transitions to guard). */
    public static final int CHARGED_STRIKE_CANCEL_COOLDOWN_TICKS = 30;

    /** Hunger exhaustion applied on a successful hit. */
    public static final float CHARGED_STRIKE_EXHAUSTION = 4.0f;

    /** Damage multiplier applied on top of the standard hit-chain damage formula. */
    public static final float CHARGED_STRIKE_DAMAGE_MULTIPLIER = 2.3f;

    /** Fixed knockback strength for a charged-strike hit. */
    public static final double CHARGED_STRIKE_KNOCKBACK = 1.6;

    /** Travel speed in blocks/tick by SPD stat (index 1..5). Index 5 is sentinel = instant. */
    public static final double[] CHARGED_STRIKE_TRAVEL_SPEED_BY_SPD =
            { 0.0, 0.20, 0.45, 0.60, 0.75, -1.0 };

    /** Tick within HIT_3 at which the charged-strike damage lands; mirrors GestaltAttackEvents.DAMAGE_TICK. */
    public static final int CHARGED_STRIKE_DAMAGE_TICK = 3;

    /** Total HIT_3 lifetime for a charged strike (damage tick + animation length). The gestalt
     *  snaps back to IDLE at the end so it doesn't linger on the target. */
    public static final int CHARGED_STRIKE_HIT3_DURATION_TICKS = 13;

    // ── Mining range ──────────────────────────────────────────────────────────

    /** Eye-to-block-center reach for gestalt mining: 3.5 + gestalt's RNG stat. */
    public static double mineRangeFor(PlayerGestaltState state) {
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        return 3.5 + (stats != null ? stats.range() : 0);
    }

    // ── Soul projection ──────────────────────────────────────────────────────

    /** Cooldown after a CLEAN exit (right-click own body double). */
    public static final int SOUL_PROJECTION_COOLDOWN_CLEAN_TICKS = 200;

    /** Cooldown after a non-clean exit (EMERGENCY / FORCED / CRASH). */
    public static final int SOUL_PROJECTION_COOLDOWN_HARD_TICKS = 800;

    /** Per-second hunger exhaustion applied while projecting (every 20 ticks). */
    public static final float SOUL_PROJECTION_HUNGER_DRAIN_PER_SECOND = 1.0f;

    /** Player flight speed while projecting. Half of vanilla creative fly speed (0.05). */
    public static final float SOUL_PROJECTION_FLY_SPEED = 0.025f;

    /** Vanilla default creative fly speed; restored on exit. */
    public static final float DEFAULT_FLY_SPEED = 0.05f;

    /** Fixed damage dealt on EMERGENCY exit; clamped above 0 HP server-side. */
    public static final float SOUL_PROJECTION_EMERGENCY_DAMAGE = 3.0f;

    /** Multiplier applied to the body double's incoming damage on FORCED exit. */
    public static final float SOUL_PROJECTION_FORCED_DAMAGE_MULTIPLIER = 3.0f;

    /** Damage dealt to the player on hunger CRASH exit. */
    public static final float SOUL_PROJECTION_CRASH_DAMAGE = 5.0f;

    /**
     * Max soul projection range in blocks: 2 + RNG, with a +1 bonus at RNG 5.
     * AmenBreak (RNG 2) → 4 blocks; max stat (RNG 5) → 8 blocks.
     */
    public static double soulProjectionRangeFor(PlayerGestaltState state) {
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int range = (stats != null) ? stats.range() : 0;
        return 4 + range + (range == 5 ? 1 : 0) + (range == 6 ? 2 : 0);
    }

    // ── Resonance system ─────────────────────────────────────────────────────

    /** Points per resonance/dissonance bar segment. */
    public static final int RESONANCE_SEGMENT_SIZE = 25;

    /** Dissonance cap (negative bound) indexed by RES stat 1-5 (index 0 unused). */
    public static final int[] RESONANCE_BAR_DISSONANCE = { 0, 25, 50, 50, 75, 75 };

    /** Resonance cap (positive bound) indexed by RES stat 1-5 (index 0 unused). */
    public static final int[] RESONANCE_BAR_RESONANCE  = { 0, 75, 75, 50, 50, 25 };

    /** Resonance tier multipliers indexed by tier 1–3 (index 0 unused). */
    public static final float[] RESONANCE_TIER_MULTIPLIER = { 0f, 1.0f, 1.2f, 1.4f };

    // Resonance gains (base values before tier multiplier)
    public static final int GAIN_COMBO_HIT_BASE              = 1;
    public static final int GAIN_COMBO_HIT2_BONUS            = 3;
    public static final int GAIN_COMBO_HIT3_BONUS            = 4;
    public static final int GAIN_CHARGED_STRIKE_HIT          = 10;
    public static final int GAIN_KILL                        = 5;
    public static final int GAIN_OVERKILL_HOSTILE            = 4;
    public static final int GAIN_FALL_BREAK_KILL             = 7;
    public static final int GAIN_MULTI_KILL                  = 5;
    public static final int GAIN_MULTI_KILL_WINDOW           = 20;
    public static final int GAIN_CHAIN_FINISHER_LOW_HP       = 7;
    /** Health (half-hearts * 2) below which the chain finisher bonus activates: 4 hearts = 8 health. */
    public static final float GAIN_CHAIN_FINISHER_HP_THRESHOLD = 8.0f;
    public static final int GAIN_PARRY                       = 7;
    public static final int GAIN_PARRY_WINDOW_MIN            = 1;
    public static final int GAIN_PARRY_WINDOW_MAX            = 7;
    public static final int GAIN_XP_CHANNEL                  = 2;
    public static final int GAIN_XP_CHANNEL_THRESHOLD        = 5;

    // Dissonance losses (flat — no multiplier ever)
    public static final int   LOSS_GUARD_ABSORB              = 2;
    public static final int   LOSS_DAMAGE_WHILE_SUMMONED     = 4;
    public static final int   LOSS_HIT_MID_COMBO             = 4;
    public static final int   LOSS_HIT_CHARGED_TRAVEL        = 7;
    public static final int   LOSS_CHARGED_WHIFF             = 2;
    public static final int   LOSS_NEAR_DEATH                = 7;
    /** Health (half-hearts * 2) at or below which LOSS_NEAR_DEATH triggers: 3 hearts = 6 health. */
    public static final float LOSS_NEAR_DEATH_THRESHOLD      = 6.0f;

    // Decay
    public static final int DECAY_SUMMONED_HOSTILE_INTERVAL   = 23;
    public static final int DECAY_SUMMONED_NO_HOSTILE_INTERVAL = 18;
    public static final int DECAY_UNSUMMONED_INTERVAL         = 5;
    public static final int DECAY_UNSUMMONED_RATE             = 2;
    public static final int DECAY_HOSTILE_DETECTION_RADIUS    = 20;
    /** Ticks after last hostile contact before switching to the faster no-hostile decay rate. */
    public static final int DECAY_HOSTILE_GRACE_TICKS         = 300;

    /** Fraction of dissonance cap at which desperate struggle activates (80%). */
    public static final float DESPERATE_STRUGGLE_THRESHOLD        = 0.8f;
    /** Damage multiplier applied to gestalt hits during desperate struggle (tunable per-gestalt). */
    public static final float DESPERATE_STRUGGLE_DAMAGE_MULTIPLIER = 1.5f;

    // ── Resonance helpers ────────────────────────────────────────────────────

    public static int maxDissonance(int res) {
        return RESONANCE_BAR_DISSONANCE[Math.max(1, Math.min(5, res))];
    }

    public static int maxResonance(int res) {
        return RESONANCE_BAR_RESONANCE[Math.max(1, Math.min(5, res))];
    }

    /**
     * Tier multiplier for resonance gains based on which segment the bar is currently in.
     * Only meaningful when resonanceValue > 0; returns 1.0 otherwise.
     */
    public static float getTierMultiplier(int resonanceValue, int maxRes) {
        if (resonanceValue <= 0 || maxRes <= 0) return 1.0f;
        int totalSegments = maxRes / RESONANCE_SEGMENT_SIZE;
        if (totalSegments == 0) return 1.0f;
        int segment = Math.min((resonanceValue - 1) / RESONANCE_SEGMENT_SIZE, totalSegments - 1);
        int distFromTop = totalSegments - 1 - segment;
        return switch (distFromTop) {
            case 0  -> RESONANCE_TIER_MULTIPLIER[3];
            case 1  -> RESONANCE_TIER_MULTIPLIER[2];
            default -> RESONANCE_TIER_MULTIPLIER[1];
        };
    }

    // ── Float Play: Spot Late passive ─────────────────────────────────────────

    /** Hunger at which Spot Late effects are maximised (matches crash threshold). */
    public static final int FLOAT_PLAY_HUNGER_MIN = 6;

    /** Hunger at which Spot Late effects begin to apply (full hunger = no effect). */
    public static final int FLOAT_PLAY_HUNGER_MAX = 20;

    /** Minimum mob-visibility multiplier at minimum hunger (full hunger = 1.0). */
    public static final double FLOAT_PLAY_MIN_VISIBILITY = 0.3;

    /** Minimum render opacity (0–1) at minimum hunger (full hunger = 1.0). */
    public static final float FLOAT_PLAY_MIN_OPACITY = 0.5f;

    /** Gravity ADD_VALUE reduction applied at minimum hunger (base gravity is 0.08). */
    public static final double FLOAT_PLAY_GRAVITY_REDUCTION = 0.04;

    /** Fall distance reduction at minimum hunger, giving flat HP reduction of equal amount. */
    public static final float FLOAT_PLAY_FALL_DISTANCE_REDUCTION = 4.0f;

    /**
     * Returns the Spot Late interpolation factor: 0.0 at full hunger, 1.0 at minimum hunger.
     * Values below {@link #FLOAT_PLAY_HUNGER_MIN} are clamped to 1.0.
     */
    public static float spotLateScale(int food) {
        int range = FLOAT_PLAY_HUNGER_MAX - FLOAT_PLAY_HUNGER_MIN;
        return Math.min(1f, Math.max(0f, (float)(FLOAT_PLAY_HUNGER_MAX - food) / range));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Amen Break powers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Minimum gestalt level required to use each power/modifier combination.
     * Row = power slot (0=POWER_1, 1=POWER_2, 2=POWER_3).
     * Col = modifier (0=B/NONE, 1=S/SNEAK, 2=G/GUARD).
     */
    public static final int[][] POWER_LEVELS = {
            {0, 2, 3},
            {5, 7, 8},
            {10, 12, 13}
    };

    // ── Amen Break Jungle Bomber (Power 1B) ──────────────────────────────────

    /** Max concurrent PopVines per player: 4 + floor((level-1)/3). Level 1 → 4, level 13 → 8. */
    public static int popVineCap(int gestaltLevel) { return 4 + (gestaltLevel - 1) / 3; }

    /** Max concurrent PopDrip vines per player: 4 + floor((level-1)/3). */
    public static int popDripCap(int gestaltLevel) { return 4 + (gestaltLevel - 1) / 3; }

    /** Max concurrent PopPads per player: 4 + floor((level-1)/3). */
    public static int popPadCap(int gestaltLevel) { return 4 + (gestaltLevel - 1) / 3; }

    /** Gestalt XP cost paid at activation. */
    public static final int POWER_1B_XP_COST = 1;

    /** Player exhaustion paid at activation. */
    public static final float POWER_1B_EXHAUSTION = 1.0f;

    /** Cooldown applied at activation. */
    public static final int POWER_1B_COOLDOWN_TICKS = 10;

    /** Base explosion radius for pop blocks — scaled by gestalt level. */
    public static final float POWER_1B_EXPLOSION_BASE_RADIUS = 2.0f;

    /** Base explosion damage for pop blocks — scaled by gestalt level. */
    public static final float POWER_1B_EXPLOSION_BASE_DAMAGE = 4.0f;

    /** Distance (blocks) within which a PopSprout triggers on a nearby mob. */
    public static final double POP_SPROUT_TRIGGER_DISTANCE = 2.5;

    /** Max fire blocks spread when a PopPod hits lava. */
    public static final int LAVA_FIRE_SPREAD_COUNT = 4;

    // ── PopDrip scan ─────────────────────────────────────────────────────────

    /** Ticks between hostile mob scans on an active PopDrip END block. */
    public static final int POPDRIP_SCAN_INTERVAL = 60;

    /** Base number of DripDrop projectiles before the vine despawns. */
    public static final int POPDRIP_BASE_DROPS = 3;

    /** Gestalt levels required per additional drop (+1 drop every N levels). */
    public static final int POPDRIP_BONUS_LEVEL_INTERVAL = 3;

    /** Base explosion radius for a DripDrop impact. */
    public static final float POPDRIP_EXPLOSION_BASE_RADIUS = 2.0f;

    /** Base explosion damage for a DripDrop impact. */
    public static final float POPDRIP_EXPLOSION_BASE_DAMAGE = 2.5f;

    /** Total drops available = 3 + floor(level / 3). Level 1–2 → 3, level 15 → 8. */
    public static int popdripMaxDrops(int gestaltLevel) {
        return POPDRIP_BASE_DROPS + (gestaltLevel / POPDRIP_BONUS_LEVEL_INTERVAL);
    }

    // ── Amen Break Twisted Firestarter (Power 1S) ────────────────────────────

    /** Gestalt XP cost paid at activation. Refused if insufficient. */
    public static final int POWER_1S_XP_COST = 40;

    /** Reduced cost when a catalyst item is held at activation. */
    public static final int POWER_1S_REDUCED_XP_COST = 10;

    /** Items consumed on activation to trigger the reduced-cost path. */
    public static final Set<Item> POWER_1S_CATALYSTS = Set.of(
            Items.FLINT,
            Items.COAL,
            Items.GUNPOWDER
    );

    /** Held item consumed by any tier-3 power (3B/3G/3S) to waive the normal resonance/XP cost. */
    public static final Set<Item> POWER_3_CATALYSTS = Set.of(
            Items.ENDER_PEARL,
            Items.ENDER_EYE
    );

    /** Ticks the primed block entity stays active before detonating. */
    public static final int POWER_1S_FUSE_TICKS = 80;

    /** Explosion power (radius) of the normal primed block — matches vanilla TNT. */
    public static final float POWER_1S_EXPLOSION_POWER = 4.0f;

    /** Explosion power (radius) of the Break Core primed block (Phase Court variant). */
    public static final float POWER_1S_BREAK_CORE_EXPLOSION_POWER = 5.0f;

    /** Base entity damage for the Break Core 1S custom explosion. */
    public static final float POWER_1S_EXPLOSION_BASE_DAMAGE = 8.0f;

    /** Cooldown applied at activation. */
    public static final int POWER_1S_COOLDOWN = 60;

    // ── Amen Break Queen Killer (Power 1G) ───────────────────────────────────

    /** Length of the windup animation before the hit lands. */
    public static final int POWER_1G_ANIMATION_TICKS = 40;

    /** Multiplier applied to the standard hit damage formula on a successful Power 1G hit. */
    public static final float POWER_1G_DAMAGE_MULTIPLIER = 2.0f;

    /** Ticks between remote detonation activation and the explosion firing (with mob shake). */
    public static final int POWER_1G_DETONATION_DELAY = 20;

    /** Cooldown applied at activation; prevents overlapping marks. */
    public static final int POWER_1G_COOLDOWN_TICKS = 60;

    /** Gestalt XP cost paid at activation. Refused if insufficient. */
    public static final int POWER_1G_XP_COST = 10;

    /** Player exhaustion paid at activation. */
    public static final float POWER_1G_EXHAUSTION = 2.0f;

    /** Base explosion radius — scaled by gestalt level via {@code GestaltExplosionUtil.scaledRadius}. */
    public static final float POWER_1G_EXPLOSION_BASE_RADIUS = 1.5f;

    /** Base explosion damage — scaled by gestalt level via {@code GestaltExplosionUtil.scaledDamage}. */
    public static final float POWER_1G_EXPLOSION_BASE_DAMAGE = 5.6f;

    // ── Amen Break P3T (Power 2B) ─────────────────────────────────────────────

    public static final int   ILLUSION_SPAWN_COST            = 15;
    public static final int   ILLUSION_TELEPORT_COST         = 5;
    public static final int   ILLUSION_LIFETIME              = 200;
    public static final int   ILLUSION_FADE_START            = 150;
    public static final int   ILLUSION_FADE_DURATION         = 40;
    public static final float ILLUSION_BASE_OPACITY          = 0.75f;
    public static final float ILLUSION_FADE_OPACITY          = 0.2f;
    public static final int   ILLUSION_COOLDOWN              = 600;
    public static final int   ILLUSION_TELEPORT_GHOST_TICKS  = 5;
    public static final int   ILLUSION_POPSPROUT_SCAN_RADIUS = 16;
    public static final float ILLUSION_EXPLOSION_BASE_RADIUS = 2.5f;
    public static final float ILLUSION_EXPLOSION_BASE_DAMAGE = 4.0f;

    // ── Amen Break What bombs at Midnight (Power 2S) ─────────────────────────

    /** Gestalt XP cost paid at activation. */
    public static final int POWER_2S_XP_COST = 20;

    /** Raytrace reach for mine placement (blocks). */
    public static final double POWER_2S_RANGE = 3.5;

    /** Gestalt level at which a second WbaM Mine becomes available. */
    public static final int POWER_2S_EXTRA_MINE_LEVEL = 12;

    /** Max concurrent WbaM Mines based on current gestalt level. */
    public static int phaseMineLimit(int gestaltLevel) {
        return gestaltLevel >= POWER_2S_EXTRA_MINE_LEVEL ? 2 : 1;
    }

    // ── What bombs at Midnight behavior ──────────────────────────────────────

    /** Ticks the marking phase lasts before drag-back begins. */
    public static final int PHASE_MINE_MARK_DURATION = 80;

    /** Ticks between each position snapshot during marking (snapshots at tick 0/20/40/60). */
    public static final int PHASE_MINE_SNAPSHOT_INTERVAL = 20;

    /** Total position snapshots taken during marking. */
    public static final int PHASE_MINE_SNAPSHOT_COUNT = 4;

    /** Speed (blocks/tick) at which the marked entity is dragged toward each snapshot. */
    public static final double PHASE_MINE_DRAGBACK_SPEED = 0.4;

    /** Distance (blocks) within which a snapshot waypoint is considered reached. */
    public static final double PHASE_MINE_CONTACT_RADIUS = 0.75;

    /** Damage multiplier applied to each afterimage explosion relative to the scaled base. */
    public static final float PHASE_MINE_EXPLOSION_DAMAGE_MULTIPLIER = 0.5f;

    /** Base explosion radius for each afterimage contact, before level scaling. */
    public static final float PHASE_MINE_EXPLOSION_BASE_RADIUS = 2.5f;

    /** Base explosion damage for each afterimage contact, before level scaling and multiplier. */
    public static final float PHASE_MINE_EXPLOSION_BASE_DAMAGE = 4.0f;

    // ── Amen Break Rave Step (Power 2G) ──────────────────────────────────────

    /** Total resonance + gestaltXP cost to trigger Phase Out. */
    public static final int PHASE_OUT_COST_TOTAL = 10;

    /** Duration of the ghost window in ticks (3 seconds). */
    public static final int PHASE_OUT_GHOST_TICKS = 60;

    /** Cooldown after the ghost window ends, in ticks. */
    public static final int PHASE_OUT_COOLDOWN_TICKS = 1200;

    // ── Amen Break Banned Forever (Power 3B) ─────────────────────────────────

    /** Gestalt XP cost paid at activation (spawn path). */
    public static final int PHASE_BLOSSOM_XP_COST = 30;

    /** Cooldown applied after dismissing the blossom. */
    public static final int PHASE_BLOSSOM_COOLDOWN_TICKS = 40;

    /** Raycast range for placing the blossom. */
    public static final double PHASE_BLOSSOM_PLACE_RANGE = 3.5;

    // ── Amen Break Futurama (Power 3S) ───────────────────────────────────────

    /** Resonance cost paid at activation. */
    public static final int    TIME_PHASE_RESONANCE_COST         = 25;

    /** Duration of the ghost window in ticks (10 seconds). */
    public static final int    TIME_PHASE_GHOST_TICKS            = 200;

    /** Ticks at which catch-up ends and prediction phase begins. */
    public static final int    TIME_PHASE_OBSERVATION_TICKS      = 80;

    /** Cooldown in ticks applied at activation (not window end). */
    public static final int    TIME_PHASE_COOLDOWN_TICKS         = 2000;

    /** Ticks between entity position snapshots (and afterimage spawns). */
    public static final int    TIME_PHASE_SNAPSHOT_INTERVAL      = 20;

    /** Maximum number of entities that can be tracked simultaneously. */
    public static final int    TIME_PHASE_MAX_ENTITIES           = 12;

    /** Radius (blocks) scanned for entities at activation. */
    public static final double TIME_PHASE_SCAN_RADIUS            = 24.0;

    /** ADD_MULTIPLIED_TOTAL speed modifier applied to each tracked entity. */
    public static final double TIME_PHASE_ENTITY_SLOW            = -0.05;

    /** ADD_MULTIPLIED_BASE speed modifier applied to the player during the window. */
    public static final double TIME_PHASE_CATCHUP_SLOW           = -0.9;

    /** Blocks ahead the body double walks in its initial facing direction when no PopSprout is in range. */
    public static final double TIME_PHASE_BODY_DOUBLE_WALK_DIST  = 12.0;

    /** XZ random spread (blocks) added to each entity's computed destination on Time Skip. */
    public static final double TIME_PHASE_DESTINATION_RADIUS     = 8.0;

    /** Base explosion radius when releasing banked damage at window end. */
    public static final float  TIME_PHASE_EXPLOSION_BASE_RADIUS  = 2.0f;

    /** Base explosion damage when releasing banked damage at window end. */
    public static final float  TIME_PHASE_EXPLOSION_BASE_DAMAGE  = 6.0f;

    /** Max range (blocks) of the look-ray used to pick the body double's destination at tick 80. */
    public static final double TIME_PHASE_BODY_DOUBLE_MAX_RANGE  = 35.0;

    /** Max blocks to scan downward from a mid-air ray hit to find a solid block. */
    public static final int    TIME_PHASE_BODY_DOUBLE_DROP_SCAN  = 35;

    // ── Amen Break Court of the Purple Prince (Power 3G) ─────────────────────

    /** Resonance cost paid at activation. */
    public static final int PHASE_COURT_RESONANCE_COST           = 50;
    /** Reduced resonance cost when activating Phase Court from an active Time Phase. */
    public static final int PHASE_COURT_FROM_TIME_PHASE_RESONANCE_COST = 35;

    /** Duration of the ghost window in ticks (8 seconds). */
    public static final int PHASE_COURT_GHOST_TICKS              = 160;

    /** Cooldown in ticks applied at activation (not window end). */
    public static final int PHASE_COURT_COOLDOWN_TICKS           = 2400;

    /** Number of position snapshots taken during Break Core 1B recording phase. */
    public static final int PHASE_COURT_SNAPSHOT_COUNT           = 8;

    /** Ticks between each snapshot during recording (8 snapshots × 20 ticks = 160 ticks). */
    public static final int PHASE_COURT_SNAPSHOT_INTERVAL        = 20;

    /** Total recording phase duration in ticks. */
    public static final int PHASE_COURT_RECORD_DURATION          = 160;

    /** Speed (blocks/tick) at which the marked entity is dragged toward each snapshot. */
    public static final double PHASE_COURT_DRAGBACK_SPEED        = 0.4;

    /** Distance within which a dragback waypoint is considered reached. */
    public static final double PHASE_COURT_CONTACT_RADIUS        = 0.75;

    /** Base explosion radius for afterimage contacts during dragback. */
    public static final float PHASE_COURT_EXPLOSION_BASE_RADIUS  = 2.5f;

    /** Base explosion damage for afterimage contacts during dragback. */
    public static final float PHASE_COURT_EXPLOSION_BASE_DAMAGE  = 5.0f;

    /** Damage multiplier for each non-final afterimage explosion. */
    public static final float PHASE_COURT_EXPLOSION_DAMAGE_MULT  = 0.5f;

    /** Damage multiplier on the initial Break Core 1G hit (on top of POWER_1G_DAMAGE_MULTIPLIER). */
    public static final float PHASE_COURT_1G_HIT_MULT             = 10.0f;

    /** Damage multiplier applied to the final (tick-46) post-hit explosion relative to scaled base. */
    public static final float PHASE_COURT_1G_FINAL_EXPLOSION_MULT = 10.0f;

    /** Damage multiplier applied to all Break Core 1B dragback explosions. */
    public static final float PHASE_COURT_1B_DRAGBACK_MULT        = 10.0f;

    /** Ticks player + target are frozen after Break Core 1G hits. */
    public static final int PHASE_COURT_1G_PLAYER_LOCK_TICKS     = 20;

    /** Ticks target is frozen total (player lock ends at 20; target stays 20 more). */
    public static final int PHASE_COURT_1G_TARGET_LOCK_TICKS     = 40;

    /** Alpha byte (0–255) for Phase Court ghost window rendering (~5%). Change here updates both body and gestalt. */
    public static final int PHASE_COURT_GHOST_ALPHA = 0x0D;

    // ─────────────────────────────────────────────────────────────────────────
    // Spillways
    // ─────────────────────────────────────────────────────────────────────────

    // ── Spillways Tears for Fears (Power 1B — seeking water tear) ────────────

    /** Cooldown applied at activation, in ticks. */
    public static final int TEARS_FOR_FEARS_COOLDOWN_TICKS = 5;

    /** Gestalt XP cost paid at activation. */
    public static final int TEARS_FOR_FEARS_XP_COST = 1;

    /** Player exhaustion paid at activation. */
    public static final float TEARS_FOR_FEARS_EXHAUSTION = 1.0f;

    /** Forward offset from eye position used for spawn (blocks). */
    public static final double TEARS_FOR_FEARS_SPAWN_OFFSET = 2.0;

    /** Max ray distance for destination selection (blocks). */
    public static final double TEARS_FOR_FEARS_DEST_RANGE = 45.0;

    /** Travel speed at spawn (blocks/tick). */
    public static final float TEARS_FOR_FEARS_BASE_SPEED = 0.05f;

    /** Travel speed at max lifetime — speed scales linearly between these two values. */
    public static final float TEARS_FOR_FEARS_MAX_SPEED = 0.8f;

    /** Flat speed multiplier applied when in water or it is raining at the entity's position. */
    public static final float TEARS_FOR_FEARS_WATER_SPEED_MULT = 1.5f;

    /** AABB inflate radius for entity and fire-block target scans (blocks). */
    public static final double TEARS_FOR_FEARS_SCAN_RADIUS = 8.0;

    /** Maximum entity lifetime in ticks before auto-discard. */
    public static final int TEARS_FOR_FEARS_MAX_LIFETIME = 400;

    /** Base simultaneous bubble cap per player. */
    public static final int TEARS_FOR_FEARS_BASE_CAP = 3;
    /** One extra bubble per this many gestalt levels above zero. */
    public static final int TEARS_FOR_FEARS_CAP_PER_LEVELS = 3;
    public static int tearsMaxCount(int gestaltLevel) {
        return TEARS_FOR_FEARS_BASE_CAP + gestaltLevel / TEARS_FOR_FEARS_CAP_PER_LEVELS;
    }

    /** Consecutive horizontal-collision ticks required to trigger stuck-discard. */
    public static final int TEARS_FOR_FEARS_STUCK_TICKS = 20;

    /** Base heal applied to passive entities on contact. */
    public static final float TEARS_FOR_FEARS_PASSIVE_HEAL_BASE = 5.0f;

    /** Additional heal per urgency level for passive contact. */
    public static final float TEARS_FOR_FEARS_PASSIVE_HEAL_PER_URGENCY = 3.0f;

    /** Drown-effect duration for hostile contact: (4 + urgency) seconds in ticks. */
    public static int tearsDrownDurationTicks(int urgency) { return (4 + urgency) * 20; }

    /** Water-breathing effect duration for passive contact: (6 + urgency) seconds in ticks. */
    public static int tearsWaterBreathingTicks(int urgency) { return (6 + urgency) * 20; }

    /** Slowness amplifier for hostile contact (only applied when urgency > 1). */
    public static int tearsSlownessAmplifier(int urgency) { return urgency - 1; }

    /** Slowness duration for hostile contact: (4 + urgency) seconds in ticks. */
    public static int tearsSlownessDurationTicks(int urgency) { return (4 + urgency) * 20; }

    /** Extended lifetime (ticks) for a Tears for Fears bubble that has been locked in place. */
    public static final int TEARS_FOR_FEARS_LOCKED_MAX_LIFETIME = 3000;
    /** Raycast range (blocks) for locking a Tears for Fears bubble via re-activation. */
    public static final double TEARS_FOR_FEARS_LOCK_RANGE = 8.0;

    // ── Spillways Lachryma (Power 2B — water take/place) ─────────────────────

    /** Gestalt XP cost paid when placing water. Taking water is free. */
    public static final int SPILLWAYS_LACHRYMA_XP_COST = 5;

    /** Cooldown applied at activation, in ticks. */
    public static final int SPILLWAYS_LACHRYMA_COOLDOWN_TICKS = 10;

    /** Raytrace reach for water manipulation (blocks). */
    public static final int SPILLWAYS_LACHRYMA_RANGE = 7;

    // ── Light levels ──────────────────────────────────────────────────────────

    /** Light level emitted by a summoned Spillways player via minecraft:light block. */
    public static final int SPILLWAYS_LIGHT_LEVEL = 7;
    /** Light level emitted by each Tears for Fears bubble via minecraft:light block. */
    public static final int TEARS_LIGHT_LEVEL = 10;

    private GestaltCosts() {}
}
