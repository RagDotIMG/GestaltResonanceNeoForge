package net.ragdot.gestaltresonance.common.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * One-shot goal that walks a Mob sequentially through a list of recorded waypoints.
 * Used by Time Phase's Phase Court takeover: hostile mobs replay their catch-up-phase path
 * after being teleported back to their tick-0 position.
 *
 * Each waypoint is allotted a maximum of MAX_TICKS_PER_WAYPOINT ticks. If the mob arrives
 * within REACH_DISTANCE earlier, it advances immediately. If pathfinding fails (e.g. the
 * recorded position is unreachable), the timeout still advances the goal so it doesn't
 * stall. We re-issue moveTo every few ticks while pathfinding is idle to recover from
 * transient failures.
 */
public class WalkRecordedPathGoal extends Goal {

    private static final double REACH_DISTANCE = 2.0;
    private static final int MAX_TICKS_PER_WAYPOINT = 40;
    private static final int REISSUE_INTERVAL = 10;
    private static final double WALK_SPEED = 1.4;

    private final Mob mob;
    private final Vec3[] waypoints;
    private int index = 0;
    private int ticksOnCurrentWaypoint = 0;

    public WalkRecordedPathGoal(Mob mob, Vec3[] waypoints) {
        this.mob = mob;
        this.waypoints = waypoints;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() { return currentWaypoint() != null; }

    @Override
    public boolean canContinueToUse() { return currentWaypoint() != null; }

    @Override
    public void start() {
        ticksOnCurrentWaypoint = 0;
        issueMove();
    }

    @Override
    public void tick() {
        Vec3 wp = currentWaypoint();
        if (wp == null) return;
        ticksOnCurrentWaypoint++;

        if (mob.position().distanceTo(wp) <= REACH_DISTANCE) {
            advance();
            return;
        }
        if (ticksOnCurrentWaypoint >= MAX_TICKS_PER_WAYPOINT) {
            advance();
            return;
        }
        if (mob.getNavigation().isDone() && ticksOnCurrentWaypoint % REISSUE_INTERVAL == 0) {
            issueMove();
        }
    }

    private Vec3 currentWaypoint() {
        while (index < waypoints.length && waypoints[index] == null) index++;
        return index < waypoints.length ? waypoints[index] : null;
    }

    private void advance() {
        index++;
        ticksOnCurrentWaypoint = 0;
        issueMove();
    }

    private void issueMove() {
        Vec3 wp = currentWaypoint();
        if (wp != null) mob.getNavigation().moveTo(wp.x, wp.y, wp.z, WALK_SPEED);
    }
}
