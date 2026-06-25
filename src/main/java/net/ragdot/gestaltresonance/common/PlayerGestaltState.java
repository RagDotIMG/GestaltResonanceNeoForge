package net.ragdot.gestaltresonance.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds the gestalt summoning state and ledge grab state for a player.
 * Persisted via data attachment and synced to tracking clients.
 */
public class PlayerGestaltState {

    public static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath("gestaltresonance", "none");

    // XP required to go from level N to N+1 (index = current level, so [1]=150 means L1→L2 costs 150)
    private static final int[] XP_PER_LEVEL = {
        0,     // index 0 unused
        150,   // level 1 → 2
        165,   // level 2 → 3
        180,   // level 3 → 4
        200,   // level 4 → 5
        220,   // level 5 → 6
        250,   // level 6 → 7
        285,   // level 7 → 8
        330,   // level 8 → 9
        380,   // level 9 → 10
        465,   // level 10 → 11
        565,   // level 11 → 12
        685,   // level 12 → 13
        835,   // level 13 → 14
        1020,  // level 14 → 15
        1270,  // level 15 (max — no further progression)
    };

    public static final int MAX_GESTALT_LEVEL = 15;

    // Only summoned + gestaltId + acquisition fields are persisted; ledge grab state is transient
    public static final Codec<PlayerGestaltState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.fieldOf("summoned").forGetter(s -> s.summoned),
            ResourceLocation.CODEC.fieldOf("gestaltId").forGetter(s -> s.gestaltId),
            Codec.BOOL.fieldOf("dormant").forGetter(s -> s.dormant),
            Codec.BOOL.fieldOf("awakened").forGetter(s -> s.awakened),
            Codec.STRING.fieldOf("pendingGestaltType").forGetter(s -> s.pendingGestaltType),
            Codec.STRING.fieldOf("awakenedGestaltType").forGetter(s -> s.awakenedGestaltType),
            Codec.INT.fieldOf("consumedXpPoints").forGetter(s -> s.consumedXpPoints),
            Codec.INT.fieldOf("targetXpPoints").forGetter(s -> s.targetXpPoints),
            Codec.INT.optionalFieldOf("gestaltLevel", 1).forGetter(s -> s.gestaltLevel),
            Codec.INT.optionalFieldOf("gestaltXp", 0).forGetter(s -> s.gestaltXp),
            ResourceLocation.CODEC.optionalFieldOf("selectedSkin", NONE).forGetter(s -> s.selectedSkin),
            Codec.list(ResourceLocation.CODEC).optionalFieldOf("unlockedSkins", List.of()).forGetter(s -> new ArrayList<>(s.unlockedSkins)),
            Codec.INT.optionalFieldOf("gestaltCrashCount", 0).forGetter(s -> s.gestaltCrashCount),
            Codec.INT.optionalFieldOf("resonanceValue", 0).forGetter(s -> s.resonanceValue)
    ).apply(inst, PlayerGestaltState::new));

    private boolean summoned;
    private ResourceLocation gestaltId;

    // --- Gestalt acquisition state ---
    private boolean dormant;
    private boolean awakened;
    private String pendingGestaltType;
    private String awakenedGestaltType;
    private int consumedXpPoints;
    private int targetXpPoints;

    // --- Gestalt leveling state ---
    private int gestaltLevel;
    private int gestaltXp;

    // --- Skin selection (persisted) ---
    /** {@link #NONE} means "use the default skin for the current gestalt". */
    private ResourceLocation selectedSkin = NONE;
    private Set<ResourceLocation> unlockedSkins = new HashSet<>();

    // --- Cumulative gestalt crash counter (persisted) ---
    /** Capped at the largest registered crash-count unlock threshold; further crashes do not increment. */
    private int gestaltCrashCount = 0;

    // --- Resonance (persisted) ---
    /** Clamped between -maxDissonance and +maxResonance; 0 = equilibrium. */
    private int resonanceValue = 0;

    // --- Resonance tracking (transient, not serialized) ---
    /** Last server tick at which a hostile mob was detected nearby; -1 = never detected. */
    private long lastHostileMobNearbyTick = -1L;
    /** Gestalt XP accumulated toward the next GAIN_XP_CHANNEL bonus during channeling. */
    private int xpChannelResonanceAccumulator = 0;
    /** Server tick when the most recent fall break landing occurred; -1 = none. */
    private long fallBreakTick = -1L;
    /** Server tick when guard was last activated; -1 = not set. Used for parry detection. */
    private long guardActivatedTick = -1L;

    // --- Vessel swap cooldown (transient, not serialized) ---
    private long lastVesselSwapGameTime;

    // --- Crash cooldown (transient, not serialized) ---
    // Stores the absolute game tick at which the cooldown expires (0 = no cooldown)
    private long crashUntilTick = 0;

    // --- Client-side summon/unsummon animation progress (not serialized) ---
    private float summonProgress = 0.0f;
    private float summonProgressO = 0.0f;
    // Set to true by TriggerGestaltCrashS2C; halves dismiss speed and amplifies VFX
    private boolean crashingOut = false;

    // --- Active action (transient, derived from the rest of the state) ---
    private GestaltAction currentAction = GestaltAction.IDLE;

    // --- Throw origin (transient, client-side only, not serialized) ---
    private double throwOriginX, throwOriginY, throwOriginZ;
    private float throwOriginYaw;

    // Server-side: armed when a throw fires, cleared on next on-ground tick.
    // Drives the LivingFallEvent damage reduction.
    private boolean throwFallProtection = false;

    // --- XP channeling (transient, server-authoritative) ---
    // Active flag synced to clients for feedback rendering.
    private boolean channelingXp = false;
    // Absolute server tick when the channel started (used for ramp lookup).
    private long channelStartTick = 0L;
    // Fractional drain accumulators — round to whole points before applying.
    private float channelPlayerXpAccumulator = 0f;
    private float channelGestaltXpAccumulator = 0f;

    // --- Guard state (transient, not serialized) ---
    private float guardDamageAccumulated = 0f;
    private long guardCooldownUntilTick = 0L;

    // --- Mining (transient, synced from local client to tracking clients for remote rendering) ---
    private boolean mining = false;

    // --- Charged strike (transient, synced when entering travel) ---
    private double chargedStrikeLaunchX, chargedStrikeLaunchY, chargedStrikeLaunchZ;
    private int chargedStrikeTargetEntityId = -1;
    private int chargedStrikeSpeedTier = 0;
    /** Server snapshot of total straight-line distance from launch to target at fire time. */
    private double chargedStrikeTargetDistance = 0.0;
    /** Server-tracked distance traveled per tick (and synced for client lerp). */
    private double chargedStrikeTraveled = 0.0;

    // --- Ghost state (transient, not serialized) ---
    private boolean ghostState = false;

    // --- Power activation state (transient, not serialized) ---
    /** Per-power cooldown: indexed by slot.ordinal() * 3 + modifier.ordinal() (3 slots × 3 modifiers = 9). */
    private final long[] perPowerCooldowns = new long[9];
    /** Server tick when the current power windup began; -1 = not winding up. */
    private long powerWindupStartTick = -1L;

    // --- Power 1G mark (transient, not serialized) ---
    private int markedEntityId = -1;
    private int markedEntityTicksRemaining = 0;
    @Nullable private Vec3 markedEntityLastPos;

    // --- Phase Out state (transient, not serialized) ---
    /** True when Phase Out is armed (toggled by X+Guard). Resets on death/logout. */
    private boolean phaseOutArmed = false;
    /** True during the 60-tick ghost window after Phase Out triggers. */
    private boolean phaseOutActive = false;
    /** Ticks remaining in the current Phase Out ghost window. */
    private int phaseOutTicksRemaining = 0;
    /** Server-side cooldown countdown. Decrements each tick when > 0. */
    private int phaseOutCooldownTicks = 0;

    // --- Moist Air state (transient, not serialized) ---
    /** True when Moist Air (Spillways 2G) is toggled on. Resets on death/logout. */
    private boolean moistAirActive = false;

    // --- Phase Court state (transient, not serialized) ---
    /** True during the 160-tick Phase Court ghost window. */
    private boolean phaseCourtActive = false;
    /** Ticks remaining in the current Phase Court window. */
    private int phaseCourtTicksRemaining = 0;
    /** Entity ID of the body double spawned at activation; -1 = none. */
    private int phaseCourtBodyDoubleId = -1;
    /** True once any Break Core ability is used during the current window. */
    private boolean breakCoreUsed = false;
    /** Entity ID of the Break Core 1B marked entity; -1 = none. */
    private int breakCoreMarkedEntityId = -1;
    /** Client-synced cooldown countdown (starts at activation). */
    private int phaseCourtCooldownTicks = 0;
    // Phase Court Break Core 1G post-hit sequence
    /** Counter driving the 1G freeze + explosion sequence; 0 = inactive. */
    private int phaseCourtPostHitTick = 0;
    /** Entity ID of the 1G target being frozen. */
    private int phaseCourtPostHitTargetId = -1;
    /** Locked world position of the 1G target during freeze. */
    @Nullable private Vec3 phaseCourtPostHitTargetPos = null;
    /** Locked world position of the player during the 1G freeze (ticks 1-10). */
    @Nullable private Vec3 phaseCourtPostHitPlayerPos = null;
    // Break Core 1B recording phase
    private boolean breakCoreRecording = false;
    private int breakCoreRecordTick = 0;
    private final Vec3[] breakCoreSnapshots = new Vec3[GestaltCosts.PHASE_COURT_SNAPSHOT_COUNT];
    private final int[] breakCoreAfterimageIds = new int[]{-1,-1,-1,-1,-1,-1,-1,-1};
    private int breakCoreSnapshotCount = 0;
    // Break Core 1B dragback phase
    private boolean breakCoreDragback = false;
    private int breakCoreDragbackIndex = -1;
    private float breakCoreBankedDamage = 0f;

    // --- Time Phase (3S) state (transient, not serialized) ---
    private boolean timePhaseActive = false;
    private int timePhaseTicksRemaining = 0;
    private int timePhaseBodyDoubleId = -1;
    private int timePhaseCooldownTicks = 0;
    private final int[] timePhaseTrackedIds = new int[GestaltCosts.TIME_PHASE_MAX_ENTITIES];
    private int timePhaseTrackedCount = 0;
    private final Vec3[][] timePhaseSnapshots = new Vec3[GestaltCosts.TIME_PHASE_MAX_ENTITIES][11];
    private final float[] timePhaseBankedDamage = new float[GestaltCosts.TIME_PHASE_MAX_ENTITIES];
    private final Vec3[] timePhaseDestinations = new Vec3[GestaltCosts.TIME_PHASE_MAX_ENTITIES];
    private int timePhaseRecordTick = 0;
    private boolean timePhasePredictionPhase = false;
    @Nullable private Vec3 timePhaseBodyDoubleDestination = null;
    private int timePhaseBodyDoubleAfterimageId = -1;
    private float timePhaseBodyDoubleBankedDamage = 0f;

    // --- Soul projection state (transient, not serialized) ---
    private boolean soulProjecting = false;
    private int bodyDoubleEntityId = -1;
    /** Server-authoritative anchor (body double spawn pos). Mirrored to the owning client for range prediction. */
    @Nullable private Vec3 soulProjectionAnchor;
    /** Server-authoritative max range in blocks. Mirrored to owning client for range prediction. */
    private float soulProjectionMaxRange = 0f;
    /** Server-side cooldown countdown after projection ends. Decrements each server tick when > 0. */
    private int soulProjectionCooldownTicks = 0;

    // --- Wall slide state (transient, not serialized) ---
    private boolean wallSliding = false;
    @Nullable private Direction wallSlideFace;
    private double wallSlideDistance = 0.0;
    private boolean canAttachToWall = true;

    // --- Ledge grab state (transient, not serialized) ---
    private boolean ledgeGrabbing;
    @Nullable private BlockPos ledgePos;
    @Nullable private Direction ledgeFace;
    @Nullable private Vec3 anchorPos;
    private int ticksGrabbing;
    // Ticks remaining for mantle magnet after release
    private int mantleTicks;
    @Nullable private Vec3 mantleTarget;

    /** Default XP points target for gestalt awakening (equivalent to 0->15 levels = 315 points). */
    public static final int DEFAULT_TARGET_XP = 315;

    public PlayerGestaltState() {
        this(false, NONE, false, false, "", "", 0, DEFAULT_TARGET_XP, 1, 0, NONE, List.of(), 0, 0);
    }

    public PlayerGestaltState(boolean summoned, ResourceLocation gestaltId) {
        this(summoned, gestaltId, false, false, "", "", 0, DEFAULT_TARGET_XP, 1, 0, NONE, List.of(), 0, 0);
    }

    public PlayerGestaltState(boolean summoned, ResourceLocation gestaltId,
                              boolean dormant, boolean awakened,
                              String pendingGestaltType, String awakenedGestaltType,
                              int consumedXpPoints, int targetXpPoints) {
        this(summoned, gestaltId, dormant, awakened, pendingGestaltType, awakenedGestaltType,
                consumedXpPoints, targetXpPoints, 1, 0, NONE, List.of(), 0, 0);
    }

    public PlayerGestaltState(boolean summoned, ResourceLocation gestaltId,
                              boolean dormant, boolean awakened,
                              String pendingGestaltType, String awakenedGestaltType,
                              int consumedXpPoints, int targetXpPoints,
                              int gestaltLevel, int gestaltXp) {
        this(summoned, gestaltId, dormant, awakened, pendingGestaltType, awakenedGestaltType,
                consumedXpPoints, targetXpPoints, gestaltLevel, gestaltXp, NONE, List.of(), 0, 0);
    }

    public PlayerGestaltState(boolean summoned, ResourceLocation gestaltId,
                              boolean dormant, boolean awakened,
                              String pendingGestaltType, String awakenedGestaltType,
                              int consumedXpPoints, int targetXpPoints,
                              int gestaltLevel, int gestaltXp,
                              ResourceLocation selectedSkin,
                              List<ResourceLocation> unlockedSkins,
                              int gestaltCrashCount,
                              int resonanceValue) {
        this.summoned = summoned;
        this.gestaltId = gestaltId;
        this.dormant = dormant;
        this.awakened = awakened;
        this.pendingGestaltType = pendingGestaltType;
        this.awakenedGestaltType = awakenedGestaltType;
        this.consumedXpPoints = consumedXpPoints;
        this.targetXpPoints = targetXpPoints;
        this.gestaltLevel = Math.max(1, Math.min(MAX_GESTALT_LEVEL, gestaltLevel));
        this.gestaltXp = Math.max(0, gestaltXp);
        this.selectedSkin = selectedSkin == null ? NONE : selectedSkin;
        this.unlockedSkins = new HashSet<>(unlockedSkins);
        this.gestaltCrashCount = Math.max(0, gestaltCrashCount);
        this.resonanceValue = resonanceValue;
        this.summonProgress = summoned ? 1.0f : 0.0f;
        this.summonProgressO = this.summonProgress;
        this.ledgeGrabbing = false;
        this.ledgePos = null;
        this.ledgeFace = null;
        this.anchorPos = null;
        this.ticksGrabbing = 0;
        this.mantleTicks = 0;
        this.mantleTarget = null;
    }

    public boolean isSummoned() {
        return summoned;
    }

    public void setSummoned(boolean summoned) {
        this.summoned = summoned;
    }

    public ResourceLocation getGestaltId() {
        return gestaltId;
    }

    public void setGestaltId(ResourceLocation gestaltId) {
        this.gestaltId = gestaltId;
    }

    /** Toggle summoned state. Only works if player has an awakened gestalt. */
    public void toggleSummon() {
        if (!awakened || gestaltId.equals(NONE)) {
            return;
        }
        summoned = !summoned;
        // Cancel ledge grab / wall slide and return to idle when desummoning
        if (!summoned) {
            clearLedgeGrab();
            clearWallSlide();
        }
        // Resummon always starts from idle
        if (summoned) {
            currentAction = GestaltAction.IDLE;
        }
    }

    // --- Throw origin accessors ---

    public void setThrowOrigin(double x, double y, double z, float yaw) {
        throwOriginX = x; throwOriginY = y; throwOriginZ = z; throwOriginYaw = yaw;
    }
    public double getThrowOriginX() { return throwOriginX; }
    public double getThrowOriginY() { return throwOriginY; }
    public double getThrowOriginZ() { return throwOriginZ; }
    public float getThrowOriginYaw() { return throwOriginYaw; }

    public boolean hasThrowFallProtection() { return throwFallProtection; }
    public void setThrowFallProtection(boolean v) { throwFallProtection = v; }

    // --- Mining accessors ---
    public boolean isMining() { return mining; }
    public void setMining(boolean v) { mining = v; }

    // --- Charged strike accessors ---

    public void setChargedStrikeLaunch(double x, double y, double z) {
        chargedStrikeLaunchX = x; chargedStrikeLaunchY = y; chargedStrikeLaunchZ = z;
    }
    public double getChargedStrikeLaunchX() { return chargedStrikeLaunchX; }
    public double getChargedStrikeLaunchY() { return chargedStrikeLaunchY; }
    public double getChargedStrikeLaunchZ() { return chargedStrikeLaunchZ; }

    public int getChargedStrikeTargetEntityId() { return chargedStrikeTargetEntityId; }
    public void setChargedStrikeTargetEntityId(int id) { chargedStrikeTargetEntityId = id; }

    public int getChargedStrikeSpeedTier() { return chargedStrikeSpeedTier; }
    public void setChargedStrikeSpeedTier(int tier) { chargedStrikeSpeedTier = tier; }

    public double getChargedStrikeTargetDistance() { return chargedStrikeTargetDistance; }
    public void setChargedStrikeTargetDistance(double d) { chargedStrikeTargetDistance = d; }

    public double getChargedStrikeTraveled() { return chargedStrikeTraveled; }
    public void setChargedStrikeTraveled(double d) { chargedStrikeTraveled = d; }

    public void clearChargedStrikeData() {
        chargedStrikeLaunchX = chargedStrikeLaunchY = chargedStrikeLaunchZ = 0.0;
        chargedStrikeTargetEntityId = -1;
        chargedStrikeSpeedTier = 0;
        chargedStrikeTargetDistance = 0.0;
        chargedStrikeTraveled = 0.0;
    }

    // --- Channeling accessors ---
    public boolean isChannelingXp() { return channelingXp; }
    public void setChannelingXp(boolean v) { channelingXp = v; }
    public long getChannelStartTick() { return channelStartTick; }
    public void setChannelStartTick(long t) { channelStartTick = t; }
    public float getChannelPlayerXpAccumulator() { return channelPlayerXpAccumulator; }
    public void setChannelPlayerXpAccumulator(float v) { channelPlayerXpAccumulator = v; }
    public float getChannelGestaltXpAccumulator() { return channelGestaltXpAccumulator; }
    public void setChannelGestaltXpAccumulator(float v) { channelGestaltXpAccumulator = v; }

    // --- Action accessors ---

    public GestaltAction getAction() { return currentAction; }
    public void setAction(GestaltAction action) { this.currentAction = action; }

    /** True when the gestalt is summoned and not performing any special action. */
    public boolean isIdle() { return summoned && currentAction == GestaltAction.IDLE; }

    // --- Guard state ---

    public boolean isGuarding() { return currentAction == GestaltAction.GUARD; }

    public void startGuard() {
        this.currentAction = GestaltAction.GUARD;
    }

    public void clearGuard() {
        this.currentAction = GestaltAction.IDLE;
    }

    public float getGuardDamageAccumulated() { return guardDamageAccumulated; }
    public void addGuardDamageAccumulated(float amount) {
        guardDamageAccumulated = Math.max(0f, guardDamageAccumulated + amount);
    }
    public void resetGuardDamageAccumulated() { guardDamageAccumulated = 0f; }

    public long getGuardCooldownUntilTick() { return guardCooldownUntilTick; }
    public void setGuardCooldownUntilTick(long tick) { guardCooldownUntilTick = tick; }
    public boolean hasGuardCooldown(long currentTick) { return currentTick < guardCooldownUntilTick; }

    // --- Ledge grab accessors ---

    public boolean isLedgeGrabbing() {
        return ledgeGrabbing;
    }

    public void startLedgeGrab(BlockPos pos, Direction face, Vec3 anchor) {
        this.ledgeGrabbing = true;
        this.currentAction = GestaltAction.LEDGE_GRAB;
        this.ledgePos = pos;
        this.ledgeFace = face;
        this.anchorPos = anchor;
        this.ticksGrabbing = 0;
        this.mantleTicks = 0;
        this.mantleTarget = null;
    }

    public void clearLedgeGrab() {
        this.ledgeGrabbing = false;
        this.currentAction = GestaltAction.IDLE;
        this.ledgePos = null;
        this.ledgeFace = null;
        this.anchorPos = null;
        this.ticksGrabbing = 0;
    }

    @Nullable public BlockPos getLedgePos() { return ledgePos; }
    @Nullable public Direction getLedgeFace() { return ledgeFace; }
    @Nullable public Vec3 getAnchorPos() { return anchorPos; }
    public void setAnchorPos(Vec3 anchor) { this.anchorPos = anchor; }
    public int getTicksGrabbing() { return ticksGrabbing; }
    public void setTicksGrabbing(int ticks) { this.ticksGrabbing = ticks; }

    // --- Ghost state accessors ---
    public boolean isGhostState() { return ghostState; }
    public void setGhostState(boolean v) { ghostState = v; }

    // --- Phase Out accessors ---
    public boolean isPhaseOutArmed() { return phaseOutArmed; }
    public void setPhaseOutArmed(boolean v) { phaseOutArmed = v; }
    public boolean isPhaseOutActive() { return phaseOutActive; }
    public void setPhaseOutActive(boolean v) { phaseOutActive = v; }
    public int getPhaseOutTicksRemaining() { return phaseOutTicksRemaining; }
    public void setPhaseOutTicksRemaining(int t) { phaseOutTicksRemaining = t; }
    public int getPhaseOutCooldownTicks() { return phaseOutCooldownTicks; }
    public void setPhaseOutCooldownTicks(int t) { phaseOutCooldownTicks = Math.max(0, t); }
    public boolean hasPhaseOutCooldown() { return phaseOutCooldownTicks > 0; }

    // --- Moist Air accessors ---
    public boolean isMoistAirActive() { return moistAirActive; }
    public void setMoistAirActive(boolean v) { moistAirActive = v; }

    // --- Phase Court accessors ---
    public boolean isPhaseCourtActive() { return phaseCourtActive; }
    public void setPhaseCourtActive(boolean v) { phaseCourtActive = v; }
    public int getPhaseCourtTicksRemaining() { return phaseCourtTicksRemaining; }
    public void setPhaseCourtTicksRemaining(int t) { phaseCourtTicksRemaining = t; }
    public int getPhaseCourtBodyDoubleId() { return phaseCourtBodyDoubleId; }
    public void setPhaseCourtBodyDoubleId(int id) { phaseCourtBodyDoubleId = id; }
    public boolean isBreakCoreUsed() { return breakCoreUsed; }
    public void setBreakCoreUsed(boolean v) { breakCoreUsed = v; }
    public int getBreakCoreMarkedEntityId() { return breakCoreMarkedEntityId; }
    public void setBreakCoreMarkedEntityId(int id) { breakCoreMarkedEntityId = id; }
    public int getPhaseCourtCooldownTicks() { return phaseCourtCooldownTicks; }
    public void setPhaseCourtCooldownTicks(int t) { phaseCourtCooldownTicks = Math.max(0, t); }
    public boolean hasPhaseCourtCooldown() { return phaseCourtCooldownTicks > 0; }
    // Phase Court 1G post-hit
    public int getPhaseCourtPostHitTick() { return phaseCourtPostHitTick; }
    public void setPhaseCourtPostHitTick(int t) { phaseCourtPostHitTick = t; }
    public int getPhaseCourtPostHitTargetId() { return phaseCourtPostHitTargetId; }
    public void setPhaseCourtPostHitTargetId(int id) { phaseCourtPostHitTargetId = id; }
    @Nullable public Vec3 getPhaseCourtPostHitTargetPos() { return phaseCourtPostHitTargetPos; }
    public void setPhaseCourtPostHitTargetPos(@Nullable Vec3 v) { phaseCourtPostHitTargetPos = v; }
    @Nullable public Vec3 getPhaseCourtPostHitPlayerPos() { return phaseCourtPostHitPlayerPos; }
    public void setPhaseCourtPostHitPlayerPos(@Nullable Vec3 v) { phaseCourtPostHitPlayerPos = v; }
    // Break Core 1B recording
    public boolean isBreakCoreRecording() { return breakCoreRecording; }
    public void setBreakCoreRecording(boolean v) { breakCoreRecording = v; }
    public int getBreakCoreRecordTick() { return breakCoreRecordTick; }
    public void setBreakCoreRecordTick(int t) { breakCoreRecordTick = t; }
    public Vec3[] getBreakCoreSnapshots() { return breakCoreSnapshots; }
    public int[] getBreakCoreAfterimageIds() { return breakCoreAfterimageIds; }
    public int getBreakCoreSnapshotCount() { return breakCoreSnapshotCount; }
    public void setBreakCoreSnapshotCount(int n) { breakCoreSnapshotCount = n; }
    // Break Core 1B dragback
    public boolean isBreakCoreDragback() { return breakCoreDragback; }
    public void setBreakCoreDragback(boolean v) { breakCoreDragback = v; }
    public int getBreakCoreDragbackIndex() { return breakCoreDragbackIndex; }
    public void setBreakCoreDragbackIndex(int i) { breakCoreDragbackIndex = i; }
    public float getBreakCoreBankedDamage() { return breakCoreBankedDamage; }
    public void setBreakCoreBankedDamage(float v) { breakCoreBankedDamage = v; }
    public void addBreakCoreBankedDamage(float v) { breakCoreBankedDamage += v; }
    public void clearBreakCoreState() {
        breakCoreMarkedEntityId = -1;
        breakCoreUsed = false;
        breakCoreRecording = false;
        breakCoreRecordTick = 0;
        breakCoreSnapshotCount = 0;
        for (int i = 0; i < breakCoreSnapshots.length; i++) breakCoreSnapshots[i] = null;
        for (int i = 0; i < breakCoreAfterimageIds.length; i++) breakCoreAfterimageIds[i] = -1;
        breakCoreDragback = false;
        breakCoreDragbackIndex = -1;
        breakCoreBankedDamage = 0f;
    }

    // --- Time Phase (3S) accessors ---
    public boolean isTimePhaseActive() { return timePhaseActive; }
    public void setTimePhaseActive(boolean v) { timePhaseActive = v; }
    public int getTimePhaseTicksRemaining() { return timePhaseTicksRemaining; }
    public void setTimePhaseTicksRemaining(int t) { timePhaseTicksRemaining = t; }
    public int getTimePhaseBodyDoubleId() { return timePhaseBodyDoubleId; }
    public void setTimePhaseBodyDoubleId(int id) { timePhaseBodyDoubleId = id; }
    public int getTimePhaseCooldownTicks() { return timePhaseCooldownTicks; }
    public void setTimePhaseCooldownTicks(int t) { timePhaseCooldownTicks = Math.max(0, t); }
    public boolean hasTimePhaseCooldown() { return timePhaseCooldownTicks > 0; }
    // Tracked entities
    public int[] getTimePhaseTrackedIds() { return timePhaseTrackedIds; }
    public int getTimePhaseTrackedCount() { return timePhaseTrackedCount; }
    public void setTimePhaseTrackedCount(int n) { timePhaseTrackedCount = n; }
    // Snapshots
    public Vec3[][] getTimePhaseSnapshots() { return timePhaseSnapshots; }
    // Banking
    public float[] getTimePhaseBankedDamage() { return timePhaseBankedDamage; }
    public void addTimePhaseBankedDamage(int index, float v) { timePhaseBankedDamage[index] += v; }
    // Destinations
    public Vec3[] getTimePhaseDestinations() { return timePhaseDestinations; }
    // Record tick
    public int getTimePhaseRecordTick() { return timePhaseRecordTick; }
    public void setTimePhaseRecordTick(int t) { timePhaseRecordTick = t; }
    // Prediction phase
    public boolean isTimePhasePredictionPhase() { return timePhasePredictionPhase; }
    public void setTimePhasePredictionPhase(boolean v) { timePhasePredictionPhase = v; }
    // Body double destination (set at prediction-phase transition)
    @Nullable public Vec3 getTimePhaseBodyDoubleDestination() { return timePhaseBodyDoubleDestination; }
    public void setTimePhaseBodyDoubleDestination(@Nullable Vec3 v) { timePhaseBodyDoubleDestination = v; }
    public int getTimePhaseBodyDoubleAfterimageId() { return timePhaseBodyDoubleAfterimageId; }
    public void setTimePhaseBodyDoubleAfterimageId(int id) { timePhaseBodyDoubleAfterimageId = id; }
    public float getTimePhaseBodyDoubleBankedDamage() { return timePhaseBodyDoubleBankedDamage; }
    public void setTimePhaseBodyDoubleBankedDamage(float v) { timePhaseBodyDoubleBankedDamage = v; }

    public void clearTimePhaseState() {
        timePhaseActive = false;
        timePhaseTicksRemaining = 0;
        timePhaseBodyDoubleId = -1;
        timePhaseTrackedCount = 0;
        timePhaseRecordTick = 0;
        timePhasePredictionPhase = false;
        timePhaseBodyDoubleDestination = null;
        timePhaseBodyDoubleAfterimageId = -1;
        timePhaseBodyDoubleBankedDamage = 0f;
        for (int i = 0; i < GestaltCosts.TIME_PHASE_MAX_ENTITIES; i++) {
            timePhaseTrackedIds[i] = -1;
            timePhaseBankedDamage[i] = 0f;
            timePhaseDestinations[i] = null;
            for (int j = 0; j < 11; j++) timePhaseSnapshots[i][j] = null;
        }
    }

    // --- Power state accessors ---
    public boolean hasPowerCooldown(GestaltPowerSlot slot, GestaltPowerModifier mod, long tick) {
        return tick < perPowerCooldowns[slot.ordinal() * 3 + mod.ordinal()];
    }
    public void setPowerCooldown(GestaltPowerSlot slot, GestaltPowerModifier mod, long until) {
        perPowerCooldowns[slot.ordinal() * 3 + mod.ordinal()] = until;
    }

    /** Clamps all gestalt power cooldowns to at most 20 ticks (1 second) for creative players. Returns true if any value changed. */
    public boolean clampAllCooldownsForCreative(long now) {
        long cap = now + 20;
        boolean changed = false;
        for (int i = 0; i < perPowerCooldowns.length; i++) {
            if (perPowerCooldowns[i] > cap) {
                perPowerCooldowns[i] = cap;
                changed = true;
            }
        }
        if (phaseCourtCooldownTicks > 20) {
            phaseCourtCooldownTicks = 20;
            changed = true;
        }
        if (phaseOutCooldownTicks > 20) {
            phaseOutCooldownTicks = 20;
            changed = true;
        }
        if (timePhaseCooldownTicks > 20) {
            timePhaseCooldownTicks = 20;
            changed = true;
        }
        return changed;
    }

    public long getPowerWindupStartTick() { return powerWindupStartTick; }
    public void setPowerWindupStartTick(long t) { powerWindupStartTick = t; }

    // --- Power 1G mark accessors ---
    public int getMarkedEntityId() { return markedEntityId; }
    public void setMarkedEntityId(int id) { markedEntityId = id; }
    public int getMarkedEntityTicksRemaining() { return markedEntityTicksRemaining; }
    public void setMarkedEntityTicksRemaining(int t) { markedEntityTicksRemaining = t; }
    @Nullable public Vec3 getMarkedEntityLastPos() { return markedEntityLastPos; }
    public void setMarkedEntityLastPos(@Nullable Vec3 v) { markedEntityLastPos = v; }
    public void clearMark() {
        markedEntityId = -1;
        markedEntityTicksRemaining = 0;
        markedEntityLastPos = null;
    }

    // --- Soul projection accessors ---
    public boolean isSoulProjecting() { return soulProjecting; }
    public void setSoulProjecting(boolean v) { soulProjecting = v; }
    public int getBodyDoubleEntityId() { return bodyDoubleEntityId; }
    public void setBodyDoubleEntityId(int id) { bodyDoubleEntityId = id; }
    @Nullable public Vec3 getSoulProjectionAnchor() { return soulProjectionAnchor; }
    public void setSoulProjectionAnchor(@Nullable Vec3 v) { soulProjectionAnchor = v; }
    public float getSoulProjectionMaxRange() { return soulProjectionMaxRange; }
    public void setSoulProjectionMaxRange(float v) { soulProjectionMaxRange = v; }
    public int getSoulProjectionCooldownTicks() { return soulProjectionCooldownTicks; }
    public void setSoulProjectionCooldownTicks(int t) { soulProjectionCooldownTicks = Math.max(0, t); }

    // --- Wall slide accessors ---

    public boolean isWallSliding() { return wallSliding; }

    public void startWallSlide(Direction face) {
        this.wallSliding = true;
        this.currentAction = GestaltAction.WALL_SLIDE;
        this.wallSlideFace = face;
        this.wallSlideDistance = 0.0;
    }

    public void clearWallSlide() {
        this.wallSliding = false;
        if (this.currentAction == GestaltAction.WALL_SLIDE) this.currentAction = GestaltAction.IDLE;
        this.wallSlideFace = null;
        this.wallSlideDistance = 0.0;
    }

    @Nullable public Direction getWallSlideFace() { return wallSlideFace; }
    public double getWallSlideDistance() { return wallSlideDistance; }
    public void setWallSlideDistance(double d) { wallSlideDistance = d; }
    public boolean canAttachToWall() { return canAttachToWall; }
    public void setCanAttachToWall(boolean v) { canAttachToWall = v; }

    public int getMantleTicks() { return mantleTicks; }
    public void setMantleTicks(int ticks) { this.mantleTicks = ticks; }
    @Nullable public Vec3 getMantleTarget() { return mantleTarget; }
    public void setMantleTarget(@Nullable Vec3 target) { this.mantleTarget = target; }

    // --- Summon progress (client-side animation) ---

    /** Call once per client tick to animate summon progress toward target. */
    public void tickSummonProgress() {
        summonProgressO = summonProgress;
        // Crash dismiss is half speed (0.05/tick = ~1 second); all other transitions use 0.10/tick (~0.5 second)
        float speed = (!summoned && crashingOut) ? 0.05f : 0.10f;
        if (summoned) {
            summonProgress = Math.min(1.0f, summonProgress + speed);
            if (summonProgress >= 1.0f) crashingOut = false;
        } else {
            summonProgress = Math.max(0.0f, summonProgress - speed);
            if (summonProgress <= 0.0f) crashingOut = false;
        }
    }

    public boolean isCrashingOut() { return crashingOut; }
    public void setCrashingOut(boolean crashingOut) { this.crashingOut = crashingOut; }

    /** Interpolated summon progress for rendering. 0 = hidden, 1 = fully visible. */
    public float getSummonProgress(float partialTick) {
        return summonProgressO + (summonProgress - summonProgressO) * partialTick;
    }

    public float getSummonProgress() {
        return summonProgress;
    }

    // --- Gestalt acquisition accessors ---

    public boolean isDormant() { return dormant; }
    public void setDormant(boolean dormant) { this.dormant = dormant; }
    public boolean isAwakened() { return awakened; }
    public void setAwakened(boolean awakened) { this.awakened = awakened; }
    public String getPendingGestaltType() { return pendingGestaltType; }
    public void setPendingGestaltType(String type) { this.pendingGestaltType = type; }
    public String getAwakenedGestaltType() { return awakenedGestaltType; }
    public void setAwakenedGestaltType(String type) { this.awakenedGestaltType = type; }
    public int getConsumedXpPoints() { return consumedXpPoints; }
    public void setConsumedXpPoints(int points) { this.consumedXpPoints = points; }
    public int getTargetXpPoints() { return targetXpPoints; }
    public void setTargetXpPoints(int points) { this.targetXpPoints = points; }

    public long getLastVesselSwapGameTime() { return lastVesselSwapGameTime; }
    public void setLastVesselSwapGameTime(long time) { this.lastVesselSwapGameTime = time; }

    public long getCrashUntilTick() { return crashUntilTick; }
    public void setCrashUntilTick(long tick) { this.crashUntilTick = tick; }
    public boolean hasCrashCooldown(long currentTick) { return currentTick < crashUntilTick; }

    // --- Gestalt leveling ---

    public int getGestaltLevel() { return gestaltLevel; }
    public void setGestaltLevel(int level) { this.gestaltLevel = Math.max(1, Math.min(MAX_GESTALT_LEVEL, level)); }
    public int getGestaltXp() { return gestaltXp; }
    public void setGestaltXp(int xp) { this.gestaltXp = Math.max(0, xp); }

    /** Total XP across all accumulated levels plus current within-level progress. */
    public int getTotalGestaltXp() {
        int total = gestaltXp;
        for (int lvl = 1; lvl < gestaltLevel; lvl++) {
            total += XP_PER_LEVEL[lvl];
        }
        return total;
    }

    /**
     * Deduct {@code amount} from the total gestalt XP pool, de-leveling if the current
     * within-level progress is insufficient. Returns {@code false} without modifying state
     * if the total pool is smaller than the requested amount.
     */
    public boolean spendGestaltXp(int amount) {
        if (getTotalGestaltXp() < amount) return false;
        int remaining = amount;
        while (remaining > 0) {
            if (gestaltXp >= remaining) {
                gestaltXp -= remaining;
                remaining = 0;
            } else {
                remaining -= gestaltXp;
                gestaltLevel = Math.max(1, gestaltLevel - 1);
                gestaltXp = XP_PER_LEVEL[gestaltLevel]; // full previous level's pool
            }
        }
        return true;
    }

    // --- Skin accessors ---
    /** Returns {@link #NONE} if no explicit skin has been selected (renderers should fall back to the gestalt's default). */
    public ResourceLocation getSelectedSkin() { return selectedSkin; }
    public void setSelectedSkin(ResourceLocation skin) { this.selectedSkin = skin == null ? NONE : skin; }
    public Set<ResourceLocation> getUnlockedSkins() { return unlockedSkins; }
    /** Adds the skin to the unlocked set. Returns {@code true} if it was newly added. */
    public boolean unlockSkin(ResourceLocation skin) { return unlockedSkins.add(skin); }
    public boolean isSkinUnlocked(ResourceLocation skin) { return unlockedSkins.contains(skin); }
    public void setUnlockedSkins(Set<ResourceLocation> skins) {
        this.unlockedSkins = (skins == null) ? new HashSet<>() : new HashSet<>(skins);
    }

    // --- Crash counter ---
    public int getGestaltCrashCount() { return gestaltCrashCount; }
    public void setGestaltCrashCount(int count) { this.gestaltCrashCount = Math.max(0, count); }
    public void incrementGestaltCrashCount() { this.gestaltCrashCount++; }

    // --- Resonance ---

    public int getResonanceValue() { return resonanceValue; }
    public void setResonanceValue(int value) { resonanceValue = value; }

    /** Decay the resonance value toward 0 by {@code amount} (always moves toward equilibrium). */
    public void decayResonance(int amount) {
        if (resonanceValue > 0) {
            resonanceValue = Math.max(0, resonanceValue - amount);
        } else if (resonanceValue < 0) {
            resonanceValue = Math.min(0, resonanceValue + amount);
        }
    }

    /**
     * Adds resonance with tier multiplier applied. Clamped to the positive cap.
     * @return the effective amount added after multiplier
     */
    public int addResonance(int baseAmount, GestaltStats stats) {
        if (baseAmount <= 0 || stats == null) return 0;
        int maxRes = GestaltCosts.maxResonance(stats.resonance());
        float mult = GestaltCosts.getTierMultiplier(resonanceValue, maxRes);
        int effective = Math.round(baseAmount * mult);
        if (effective <= 0) return 0;
        resonanceValue = Math.min(maxRes, resonanceValue + effective);
        return effective;
    }

    /**
     * Subtracts dissonance (flat, no multiplier). Clamped to the negative cap.
     * @return true if the dissonance cap was reached (caller should trigger crash)
     */
    public boolean addDissonance(int amount, GestaltStats stats) {
        if (amount <= 0 || stats == null) return false;
        int maxDis = GestaltCosts.maxDissonance(stats.resonance());
        resonanceValue = Math.max(-maxDis, resonanceValue - amount);
        return resonanceValue <= -maxDis;
    }

    /** True when the dissonance side exceeds 80% of the cap. */
    public boolean isDesperateStruggle(GestaltStats stats) {
        if (stats == null || resonanceValue >= 0) return false;
        int maxDis = GestaltCosts.maxDissonance(stats.resonance());
        return -resonanceValue >= (int)(maxDis * GestaltCosts.DESPERATE_STRUGGLE_THRESHOLD);
    }

    // --- Resonance transient tracking ---
    public long getLastHostileMobNearbyTick() { return lastHostileMobNearbyTick; }
    public void setLastHostileMobNearbyTick(long tick) { lastHostileMobNearbyTick = tick; }
    public long getFallBreakTick() { return fallBreakTick; }
    public void setFallBreakTick(long tick) { fallBreakTick = tick; }
    public long getGuardActivatedTick() { return guardActivatedTick; }
    public void setGuardActivatedTick(long tick) { guardActivatedTick = tick; }
    public int getXpChannelResonanceAccumulator() { return xpChannelResonanceAccumulator; }
    public void setXpChannelResonanceAccumulator(int v) { xpChannelResonanceAccumulator = v; }

    /** XP needed to advance from the given level to the next. Returns MAX_VALUE at max level. */
    public static int getXpForNextLevel(int level) {
        if (level <= 0 || level > MAX_GESTALT_LEVEL) return Integer.MAX_VALUE;
        return XP_PER_LEVEL[level];
    }

    /** Progress within the current level as a 0.0–1.0 float. Returns 0.0 at max level. */
    public float getGestaltXpProgress() {
        if (gestaltLevel >= MAX_GESTALT_LEVEL) return 0.0f;
        int needed = getXpForNextLevel(gestaltLevel);
        return needed > 0 ? (float) gestaltXp / needed : 0.0f;
    }

    /**
     * Adds XP to the gestalt, handling level-ups.
     * @return number of levels gained (0 if none)
     */
    public int addGestaltExperience(int xp) {
        if (gestaltLevel >= MAX_GESTALT_LEVEL || xp <= 0) return 0;
        gestaltXp += xp;
        int levelsGained = 0;
        while (gestaltLevel < MAX_GESTALT_LEVEL) {
            int needed = getXpForNextLevel(gestaltLevel);
            if (gestaltXp < needed) break;
            gestaltXp -= needed;
            gestaltLevel++;
            levelsGained++;
        }
        if (gestaltLevel >= MAX_GESTALT_LEVEL) gestaltXp = 0;
        return levelsGained;
    }

    public PlayerGestaltState copy() {
        PlayerGestaltState c = new PlayerGestaltState(summoned, gestaltId,
                dormant, awakened, pendingGestaltType, awakenedGestaltType,
                consumedXpPoints, targetXpPoints, gestaltLevel, gestaltXp,
                selectedSkin, new ArrayList<>(unlockedSkins), gestaltCrashCount, resonanceValue);
        c.currentAction = this.currentAction;
        c.ghostState = this.ghostState;
        c.phaseOutArmed = this.phaseOutArmed;
        c.phaseOutActive = this.phaseOutActive;
        c.phaseOutTicksRemaining = this.phaseOutTicksRemaining;
        c.phaseOutCooldownTicks = this.phaseOutCooldownTicks;
        System.arraycopy(this.perPowerCooldowns, 0, c.perPowerCooldowns, 0, 9);
        c.powerWindupStartTick = this.powerWindupStartTick;
        c.markedEntityId = this.markedEntityId;
        c.markedEntityTicksRemaining = this.markedEntityTicksRemaining;
        c.markedEntityLastPos = this.markedEntityLastPos;
        c.phaseCourtActive = this.phaseCourtActive;
        c.phaseCourtTicksRemaining = this.phaseCourtTicksRemaining;
        c.phaseCourtBodyDoubleId = this.phaseCourtBodyDoubleId;
        c.breakCoreUsed = this.breakCoreUsed;
        c.breakCoreMarkedEntityId = this.breakCoreMarkedEntityId;
        c.phaseCourtCooldownTicks = this.phaseCourtCooldownTicks;
        c.phaseCourtPostHitTick = this.phaseCourtPostHitTick;
        c.phaseCourtPostHitTargetId = this.phaseCourtPostHitTargetId;
        c.phaseCourtPostHitTargetPos = this.phaseCourtPostHitTargetPos;
        c.phaseCourtPostHitPlayerPos = this.phaseCourtPostHitPlayerPos;
        c.breakCoreRecording = this.breakCoreRecording;
        c.breakCoreRecordTick = this.breakCoreRecordTick;
        System.arraycopy(this.breakCoreSnapshots, 0, c.breakCoreSnapshots, 0, this.breakCoreSnapshots.length);
        System.arraycopy(this.breakCoreAfterimageIds, 0, c.breakCoreAfterimageIds, 0, this.breakCoreAfterimageIds.length);
        c.breakCoreSnapshotCount = this.breakCoreSnapshotCount;
        c.breakCoreDragback = this.breakCoreDragback;
        c.breakCoreDragbackIndex = this.breakCoreDragbackIndex;
        c.breakCoreBankedDamage = this.breakCoreBankedDamage;
        c.timePhaseActive = this.timePhaseActive;
        c.timePhaseTicksRemaining = this.timePhaseTicksRemaining;
        c.timePhaseBodyDoubleId = this.timePhaseBodyDoubleId;
        c.timePhaseCooldownTicks = this.timePhaseCooldownTicks;
        c.timePhaseTrackedCount = this.timePhaseTrackedCount;
        c.timePhaseRecordTick = this.timePhaseRecordTick;
        c.timePhasePredictionPhase = this.timePhasePredictionPhase;
        c.timePhaseBodyDoubleDestination = this.timePhaseBodyDoubleDestination;
        c.timePhaseBodyDoubleAfterimageId = this.timePhaseBodyDoubleAfterimageId;
        c.timePhaseBodyDoubleBankedDamage = this.timePhaseBodyDoubleBankedDamage;
        System.arraycopy(this.timePhaseTrackedIds, 0, c.timePhaseTrackedIds, 0, this.timePhaseTrackedIds.length);
        System.arraycopy(this.timePhaseBankedDamage, 0, c.timePhaseBankedDamage, 0, this.timePhaseBankedDamage.length);
        System.arraycopy(this.timePhaseDestinations, 0, c.timePhaseDestinations, 0, this.timePhaseDestinations.length);
        for (int i = 0; i < GestaltCosts.TIME_PHASE_MAX_ENTITIES; i++) {
            System.arraycopy(this.timePhaseSnapshots[i], 0, c.timePhaseSnapshots[i], 0, this.timePhaseSnapshots[i].length);
        }
        c.soulProjecting = this.soulProjecting;
        c.bodyDoubleEntityId = this.bodyDoubleEntityId;
        c.soulProjectionAnchor = this.soulProjectionAnchor;
        c.soulProjectionMaxRange = this.soulProjectionMaxRange;
        c.soulProjectionCooldownTicks = this.soulProjectionCooldownTicks;
        c.wallSliding = this.wallSliding;
        c.wallSlideFace = this.wallSlideFace;
        c.wallSlideDistance = this.wallSlideDistance;
        c.canAttachToWall = this.canAttachToWall;
        c.ledgeGrabbing = this.ledgeGrabbing;
        c.ledgePos = this.ledgePos;
        c.ledgeFace = this.ledgeFace;
        c.anchorPos = this.anchorPos;
        c.ticksGrabbing = this.ticksGrabbing;
        c.mantleTicks = this.mantleTicks;
        c.mantleTarget = this.mantleTarget;
        c.summonProgress = this.summonProgress;
        c.summonProgressO = this.summonProgressO;
        c.crashingOut = this.crashingOut;
        c.crashUntilTick = this.crashUntilTick;
        c.guardDamageAccumulated = this.guardDamageAccumulated;
        c.guardCooldownUntilTick = this.guardCooldownUntilTick;
        return c;
    }
}
