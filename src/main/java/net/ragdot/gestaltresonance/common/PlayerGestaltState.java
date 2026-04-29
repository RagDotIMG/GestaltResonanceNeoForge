package net.ragdot.gestaltresonance.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

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
            Codec.INT.optionalFieldOf("gestaltXp", 0).forGetter(s -> s.gestaltXp)
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

    // --- Guard state (transient, not serialized) ---
    private float guardDamageAccumulated = 0f;
    private long guardCooldownUntilTick = 0L;

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
        this(false, NONE, false, false, "", "", 0, DEFAULT_TARGET_XP, 1, 0);
    }

    public PlayerGestaltState(boolean summoned, ResourceLocation gestaltId) {
        this(summoned, gestaltId, false, false, "", "", 0, DEFAULT_TARGET_XP, 1, 0);
    }

    public PlayerGestaltState(boolean summoned, ResourceLocation gestaltId,
                              boolean dormant, boolean awakened,
                              String pendingGestaltType, String awakenedGestaltType,
                              int consumedXpPoints, int targetXpPoints) {
        this(summoned, gestaltId, dormant, awakened, pendingGestaltType, awakenedGestaltType,
                consumedXpPoints, targetXpPoints, 1, 0);
    }

    public PlayerGestaltState(boolean summoned, ResourceLocation gestaltId,
                              boolean dormant, boolean awakened,
                              String pendingGestaltType, String awakenedGestaltType,
                              int consumedXpPoints, int targetXpPoints,
                              int gestaltLevel, int gestaltXp) {
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
        // Cancel ledge grab and return to idle when desummoning
        if (!summoned) {
            clearLedgeGrab();
        }
        // Resummon always starts from idle
        if (summoned) {
            currentAction = GestaltAction.IDLE;
        }
    }

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
                consumedXpPoints, targetXpPoints, gestaltLevel, gestaltXp);
        c.currentAction = this.currentAction;
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
