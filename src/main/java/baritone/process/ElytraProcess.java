/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.process;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.event.events.*;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.process.elytra.ElytraBehavior;
import baritone.process.elytra.NetherPathfinderContext;
import baritone.process.elytra.NullElytraProcess;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ElytraProcess extends BaritoneProcessHelper implements IBaritoneProcess, IElytraProcess, AbstractGameEventListener {
    private static final int GROUND_TAKEOFF_MAX_ATTEMPTS = 3;
    private static final int GROUND_TAKEOFF_PATH_TIMEOUT = 600;
    private static final int GROUND_TAKEOFF_JUMP_TIMEOUT = 20;
    private static final int GROUND_TAKEOFF_DEPLOY_TIMEOUT = 30;
    private static final int GROUND_TAKEOFF_BOOST_TICKS = 12;
    private static final double GROUND_TAKEOFF_RELEASE_HEIGHT = 7.0;
    private static final float[] GROUND_TAKEOFF_PITCHES = {-35.0F, -45.0F, -55.0F, -65.0F, -75.0F, -85.0F};
    private static final float[] GROUND_TAKEOFF_YAW_OFFSETS = {0.0F, -25.0F, 25.0F, -50.0F, 50.0F, -90.0F, 90.0F};
    public State state;
    private boolean goingToLandingSpot;
    private BetterBlockPos landingSpot;
    private boolean reachedGoal; // this basically just prevents potential notification spam
    private ElytraBehavior behavior;
    private BetterBlockPos requestedDestination;
    private boolean predictingTerrain;
    private Rotation groundTakeoffRotation;
    private int groundTakeoffTicks;
    private int groundTakeoffAttempts;
    private boolean groundTakeoffFireworkUsed;
    private double groundTakeoffStartY;

    @Override
    public void onLostControl() {
        this.state = State.START_FLYING; // TODO: null state?
        this.goingToLandingSpot = false;
        this.landingSpot = null;
        this.reachedGoal = false;
        this.groundTakeoffRotation = null;
        this.groundTakeoffTicks = 0;
        this.groundTakeoffAttempts = 0;
        this.groundTakeoffFireworkUsed = false;
        this.requestedDestination = null;
        destroyBehaviorAsync();
    }

    private ElytraProcess(Baritone baritone) {
        super(baritone);
        baritone.getGameEventHandler().registerEventListener(this);
    }

    public static IElytraProcess create(final Baritone baritone) {
        return NetherPathfinderContext.isSupported()
                ? new ElytraProcess(baritone)
                : new NullElytraProcess(baritone);
    }

    @Override
    public boolean isActive() {
        return this.behavior != null;
    }

    @Override
    public void resetState() {
        BlockPos destination = this.currentDestination();
        this.onLostControl();
        if (destination != null) {
            this.pathTo(destination);
            this.repackChunks();
        }
    }

    private static final String GROUND_TAKEOFF_FAILURE_MSG = "Ground takeoff failed after three attempts.";

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        final long seedSetting = Baritone.settings().elytraNetherSeed.value;
        if (ctx.world().dimension() == Level.NETHER && seedSetting != this.behavior.context.getSeed()) {
            logDirect("Nether seed changed, recalculating path");
            this.resetState();
        }
        if (predictingTerrain != Baritone.settings().elytraPredictTerrain.value) {
            logDirect("elytraPredictTerrain setting changed, recalculating path");
            predictingTerrain = Baritone.settings().elytraPredictTerrain.value;
            this.resetState();
        }

        this.behavior.onTick();

        if (calcFailed) {
            onLostControl();
            logDirect(GROUND_TAKEOFF_FAILURE_MSG);
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        boolean safetyLanding = false;
        if (ctx.player().isFallFlying() && shouldLandForSafety()) {
            if (Baritone.settings().elytraAllowEmergencyLand.value) {
                logDirect("Emergency landing - almost out of elytra durability or fireworks");
                safetyLanding = true;
            } else {
                logDirect("almost out of elytra durability or fireworks, but I'm going to continue since elytraAllowEmergencyLand is false");
            }
        }
        if (ctx.player().isFallFlying() && this.state != State.LANDING && (this.behavior.pathManager.isComplete() || safetyLanding)) {
            final BetterBlockPos last = this.behavior.pathManager.path.getLast();
            if (last != null && (ctx.player().position().distanceToSqr(last.getCenter()) < (48 * 48) || safetyLanding) && (!goingToLandingSpot || (safetyLanding && this.landingSpot == null))) {
                logDirect("Path complete, picking a nearby safe landing spot...");
                BetterBlockPos landingTarget = safetyLanding ? ctx.playerFeet() : requestedDestination;
                BetterBlockPos landingSpot = findSafeLandingSpot(landingTarget);
                if (landingSpot != null) {
                    this.pathTo0(landingSpot, true);
                    this.landingSpot = landingSpot;
                } else {
                    logDirect("No safe landing point was found within the configured radius.");
                    this.onLostControl();
                    return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                this.goingToLandingSpot = true;
            }

            if (last != null && ctx.player().position().distanceToSqr(last.getCenter()) < 1) {
                if (Baritone.settings().notificationOnPathComplete.value && !reachedGoal) {
                    logNotification("Pathing complete", false);
                }
                if (Baritone.settings().disconnectOnArrival.value && !reachedGoal) {
                    // don't be active when the user logs back in
                    this.onLostControl();
                    if (ctx.world() instanceof ClientLevel clientLevel) {
                        clientLevel.disconnect(Component.literal("[Baritone] Arrived at goal!"));
                    }
                    return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                reachedGoal = true;

                // we are goingToLandingSpot and we are in the last node of the path
                if (this.goingToLandingSpot) {
                    this.state = State.LANDING;
                    logDirect("Above the landing spot, landing...");
                }
            }
        }

        if (this.state == State.LANDING) {
            final BetterBlockPos endPos = this.landingSpot != null ? this.landingSpot : behavior.pathManager.path.getLast();
            if (ctx.player().isFallFlying() && endPos != null) {
                Vec3 from = ctx.player().position();
                Vec3 to = new Vec3(((double) endPos.x) + 0.5, from.y, ((double) endPos.z) + 0.5);
                Rotation rotation = RotationUtils.calcRotationFromVec3d(from, to, ctx.playerRotations());
                baritone.getLookBehavior().updateTarget(new Rotation(rotation.getYaw(), 0), false); // this will be overwritten, probably, by behavior tick

                if (ctx.player().position().y < endPos.y - LANDING_COLUMN_HEIGHT) {
                    logDirect("bad landing spot, trying again...");
                    landingSpotIsBad(endPos);
                }
            }
        }

        if (isGroundTakeoffActive()) {
            return tickGroundTakeoff();
        }

        if (ctx.player().isFallFlying()) {
            behavior.landingMode = this.state == State.LANDING;
            baritone.getInputOverrideHandler().clearAllKeys();
            behavior.tick();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        } else if (this.state == State.LANDING) {
            if (ctx.playerMotion().multiply(1, 0, 1).length() > 0.001) {
                logDirect("Landed, but still moving, waiting for velocity to die down... ");
                baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            logDirect("Done :)");
            baritone.getInputOverrideHandler().clearAllKeys();
            this.onLostControl();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (this.state == State.START_FLYING) {
            if (!isSafeToCancel) {
                // owned
                baritone.getPathingBehavior().secretInternalSegmentCancel();
            }
            baritone.getInputOverrideHandler().clearAllKeys();
            // TODO 1.21.5: replace `ctx.player().getDeltaMovement().y < -0.377` with `ctx.player().fallDistance > 1.0f`
            if (ctx.player().getDeltaMovement().y < -0.377) {
                baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            }
        }
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    public boolean isGroundTakeoffActive() {
        return this.state == State.GROUND_PREPARE
                || this.state == State.GROUND_JUMP
                || this.state == State.GROUND_DEPLOY
                || this.state == State.GROUND_BOOST;
    }

    private PathingCommand tickGroundTakeoff() {
        baritone.getInputOverrideHandler().clearAllKeys();
        if (ctx.player().isFallFlying()
                && this.state != State.GROUND_DEPLOY
                && this.state != State.GROUND_BOOST) {
            this.state = State.FLYING;
            behavior.tick();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        if (this.state == State.GROUND_PREPARE) {
            this.groundTakeoffTicks++;
            if (shouldLandForSafety() || fireworkQuantity() <= Baritone.settings().elytraMinFireworksBeforeLanding.value + 1) {
                logDirect("Ground takeoff cancelled because elytra durability or fireworks are too low for launch and continued flight.");
                onLostControl();
                return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
            if (!ctx.player().onGround()) {
                return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
            if (behavior.pathManager.getPath().isEmpty()) {
                if (this.groundTakeoffTicks > GROUND_TAKEOFF_PATH_TIMEOUT) {
                    logDirect("Ground takeoff timed out while waiting for an elytra path.");
                    onLostControl();
                    return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            if (this.groundTakeoffRotation == null) {
                this.groundTakeoffRotation = findGroundTakeoffRotation();
                if (this.groundTakeoffRotation == null) {
                    logDirect("No collision-free ground takeoff trajectory was found.");
                    onLostControl();
                    return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                logDirect(String.format("Ground takeoff trajectory selected: yaw %.1f, pitch %.1f", this.groundTakeoffRotation.getYaw(), this.groundTakeoffRotation.getPitch()));
                this.groundTakeoffTicks = 0;
            }
            baritone.getLookBehavior().updateTarget(this.groundTakeoffRotation, true);
            if (isGroundTakeoffRotationReady()) {
                this.state = State.GROUND_JUMP;
                this.groundTakeoffTicks = 0;
                this.groundTakeoffAttempts++;
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        baritone.getLookBehavior().updateTarget(this.groundTakeoffRotation, true);
        this.groundTakeoffTicks++;

        if (this.state == State.GROUND_JUMP) {
            if (!ctx.player().onGround()) {
                this.state = State.GROUND_DEPLOY;
                this.groundTakeoffTicks = 0;
            } else {
                baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                if (this.groundTakeoffTicks > GROUND_TAKEOFF_JUMP_TIMEOUT) {
                    return retryGroundTakeoff();
                }
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        if (this.state == State.GROUND_DEPLOY) {
            if (ctx.player().isFallFlying()) {
                this.state = State.GROUND_BOOST;
                this.groundTakeoffTicks = 0;
                this.groundTakeoffStartY = ctx.player().position().y;
                this.groundTakeoffFireworkUsed = false;
            } else if (ctx.player().onGround() && this.groundTakeoffTicks > 2) {
                return retryGroundTakeoff();
            } else if (ctx.player().getDeltaMovement().y < 0.0) {
                baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            }
            if (this.groundTakeoffTicks > GROUND_TAKEOFF_DEPLOY_TIMEOUT) {
                return retryGroundTakeoff();
            }
        }

        if (this.state == State.GROUND_BOOST) {
            if (!ctx.player().isFallFlying()) {
                return retryGroundTakeoff();
            }
            if (!this.groundTakeoffFireworkUsed) {
                if (!behavior.useGroundTakeoffFirework()) {
                    logDirect("Ground takeoff could not select a firework.");
                    onLostControl();
                    return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                this.groundTakeoffFireworkUsed = true;
            }
            if (this.groundTakeoffTicks >= GROUND_TAKEOFF_BOOST_TICKS
                    || ctx.player().position().y - this.groundTakeoffStartY >= GROUND_TAKEOFF_RELEASE_HEIGHT) {
                this.state = State.FLYING;
                behavior.tick();
            }
        }
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    private boolean isGroundTakeoffRotationReady() {
        Rotation current = ctx.playerRotations();
        float yawDifference = Math.abs(Rotation.normalizeYaw(current.getYaw() - this.groundTakeoffRotation.getYaw()));
        float pitchDifference = Math.abs(current.getPitch() - this.groundTakeoffRotation.getPitch());
        return yawDifference <= 2.0F && pitchDifference <= 2.0F;
    }

    private PathingCommand retryGroundTakeoff() {
        if (this.groundTakeoffAttempts >= GROUND_TAKEOFF_MAX_ATTEMPTS) {
            logDirect(GROUND_TAKEOFF_FAILURE_MSG);
            onLostControl();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        this.state = State.GROUND_PREPARE;
        this.groundTakeoffRotation = null;
        this.groundTakeoffTicks = 0;
        this.groundTakeoffFireworkUsed = false;
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    private Rotation findGroundTakeoffRotation() {
        Vec3 start = ctx.player().position();
        Vec3 target = behavior.pathManager.getPath().stream()
                .map(BetterBlockPos::getCenter)
                .filter(pos -> pos.distanceToSqr(start) >= 24.0 * 24.0)
                .findFirst()
                .orElse(behavior.destination.getCenter());
        float baseYaw = RotationUtils.calcRotationFromVec3d(start, target, ctx.playerRotations()).getYaw();
        Rotation best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (float pitch : GROUND_TAKEOFF_PITCHES) {
            for (float yawOffset : GROUND_TAKEOFF_YAW_OFFSETS) {
                Rotation rotation = new Rotation(baseYaw + yawOffset, pitch);
                Vec3 end = simulateGroundTakeoff(rotation);
                if (end == null) {
                    continue;
                }
                double score = end.distanceToSqr(target) + Math.abs(yawOffset) * 6.0;
                if (score < bestScore) {
                    bestScore = score;
                    best = rotation;
                }
            }
        }
        return best;
    }

    private Vec3 simulateGroundTakeoff(Rotation rotation) {
        AABB initialBox = ctx.player().getBoundingBox();
        Vec3 offset = Vec3.ZERO;
        Vec3 velocity = new Vec3(0.0, 0.42, 0.0);
        for (int tick = 0; tick < 7; tick++) {
            offset = offset.add(velocity);
            if (!ctx.world().noCollision(ctx.player(), initialBox.move(offset))) {
                return null;
            }
            velocity = new Vec3(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
        }
        double yaw = Math.toRadians(rotation.getYaw());
        double pitch = Math.toRadians(rotation.getPitch());
        Vec3 direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        for (int tick = 0; tick < 24; tick++) {
            velocity = velocity.add(
                    direction.x * 0.1 + (direction.x * 1.5 - velocity.x) * 0.5,
                    direction.y * 0.1 + (direction.y * 1.5 - velocity.y) * 0.5,
                    direction.z * 0.1 + (direction.z * 1.5 - velocity.z) * 0.5
            );
            velocity = new Vec3(velocity.x * 0.99, (velocity.y - 0.02) * 0.98, velocity.z * 0.99);
            offset = offset.add(velocity);
            if (!ctx.world().noCollision(ctx.player(), initialBox.move(offset))) {
                return null;
            }
        }
        return ctx.player().position().add(offset);
    }

    public void landingSpotIsBad(BetterBlockPos endPos) {
        badLandingSpots.add(endPos);
        goingToLandingSpot = false;
        this.landingSpot = null;
        this.state = State.FLYING;
    }

    private void destroyBehaviorAsync() {
        ElytraBehavior behavior = this.behavior;
        if (behavior != null) {
            this.behavior = null;
            Baritone.getExecutor().execute(behavior::destroy);
        }
    }

    @Override
    public double priority() {
        return 0; // higher priority than CustomGoalProcess
    }

    @Override
    public String displayName0() {
        return "Elytra - " + this.state.description;
    }

    @Override
    public void repackChunks() {
        if (this.behavior != null) {
            this.behavior.repackChunks();
        }
    }

    @Override
    public BlockPos currentDestination() {
        return this.requestedDestination;
    }

    @Override
    public void pathTo(BlockPos destination) {
        this.pathTo0(destination, false);
    }

    private void pathTo0(BlockPos destination, boolean appendDestination) {
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        BetterBlockPos requested = appendDestination && this.requestedDestination != null
                ? this.requestedDestination
                : new BetterBlockPos(destination);
        this.onLostControl();
        this.requestedDestination = requested;
        this.predictingTerrain = Baritone.settings().elytraPredictTerrain.value;
        this.behavior = new ElytraBehavior(this.baritone, this, destination, appendDestination);
        this.state = ctx.player().isFallFlying()
                ? State.FLYING
                : Baritone.settings().elytraAutoJump.value && ctx.player().onGround() ? State.GROUND_PREPARE : State.START_FLYING;
        if (ctx.world() != null) {
            this.behavior.repackChunks();
        }
        this.behavior.pathTo();
    }

    @Override
    public void pathTo(Goal iGoal) {
        final int x;
        final int y;
        final int z;
        if (iGoal instanceof GoalXZ) {
            GoalXZ goal = (GoalXZ) iGoal;
            x = goal.getX();
            final int minY = ctx.world().dimensionType().minY();
            final int maxY = minY + ctx.world().dimensionType().height() - 1;
            y = Math.max(minY, Math.min(maxY, ctx.playerFeet().getY()));
            z = goal.getZ();
        } else if (iGoal instanceof GoalBlock) {
            GoalBlock goal = (GoalBlock) iGoal;
            x = goal.x;
            y = goal.y;
            z = goal.z;
        } else {
            throw new IllegalArgumentException("The goal must be a GoalXZ or GoalBlock");
        }
        final int minY = ctx.world().dimensionType().minY();
        final int maxYExclusive = minY + ctx.world().dimensionType().height();
        if (y < minY || y >= maxYExclusive) {
            throw new IllegalArgumentException("The y of the goal is not between " + minY + " and " + (maxYExclusive - 1));
        }
        this.pathTo(new BlockPos(x, y, z));
    }

    private boolean shouldLandForSafety() {
        ItemStack chest = ctx.player().getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA || chest.getMaxDamage() - chest.getDamageValue() < Baritone.settings().elytraMinimumDurability.value) {
            // elytrabehavior replaces when durability <= minimumDurability, so if durability < minimumDurability then we can reasonably assume that the elytra will soon be broken without replacement
            return true;
        }

        return fireworkQuantity() <= Baritone.settings().elytraMinFireworksBeforeLanding.value;
    }

    private int fireworkQuantity() {
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int qty = 0;
        for (int i = 0; i < 36; i++) {
            if (ElytraBehavior.isFireworks(inv.get(i))) {
                qty += inv.get(i).getCount();
            }
        }
        return qty;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public boolean isSafeToCancel() {
        return !this.isActive() || !(this.state == State.FLYING
                || this.state == State.START_FLYING
                || this.state == State.GROUND_JUMP
                || this.state == State.GROUND_DEPLOY
                || this.state == State.GROUND_BOOST);
    }

    public enum State {
        GROUND_PREPARE("Calculating ground takeoff"),
        GROUND_JUMP("Jumping for ground takeoff"),
        GROUND_DEPLOY("Deploying elytra"),
        GROUND_BOOST("Boosting from ground"),
        START_FLYING("Begin flying"),
        FLYING("Flying"),
        LANDING("Landing");

        public final String description;

        State(String desc) {
            this.description = desc;
        }
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        if (this.behavior != null) this.behavior.onRenderPass(event);
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        if (event.getWorld() != null && event.getState() == EventState.POST) {
            // Exiting the world, just destroy
            destroyBehaviorAsync();
        }
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        if (this.behavior != null) this.behavior.onChunkEvent(event);
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (this.behavior != null) this.behavior.onBlockChange(event);
    }

    @Override
    public void onReceivePacket(PacketEvent event) {
        if (this.behavior != null) this.behavior.onReceivePacket(event);
    }

    @Override
    public void onPostTick(TickEvent event) {
        IBaritoneProcess procThisTick = baritone.getPathingControlManager().mostRecentInControl().orElse(null);
        if (this.behavior != null && procThisTick == this) this.behavior.onPostTick(event);
    }

    private boolean isInBounds(BlockPos pos) {
        final int minY = ctx.world().dimensionType().minY();
        return pos.getY() >= minY && pos.getY() < minY + ctx.world().dimensionType().height();
    }

    private boolean isSafeBlock(BlockPos pos) {
        final BlockState state = ctx.world().getBlockState(pos);
        final Block block = state.getBlock();
        if (ctx.world().dimension() == Level.NETHER) {
            return block == Blocks.NETHERRACK || block == Blocks.GRAVEL || (block == Blocks.NETHER_BRICKS && Baritone.settings().elytraAllowLandOnNetherFortress.value);
        }
        return state.getFluidState().isEmpty()
                && state.isFaceSturdy(ctx.world(), pos, Direction.UP)
                && block != Blocks.MAGMA_BLOCK
                && block != Blocks.CACTUS
                && block != Blocks.CAMPFIRE
                && block != Blocks.SOUL_CAMPFIRE;
    }

    private boolean isAtEdge(BlockPos pos) {
        return !isSafeBlock(pos.north())
                || !isSafeBlock(pos.south())
                || !isSafeBlock(pos.east())
                || !isSafeBlock(pos.west())
                // corners
                || !isSafeBlock(pos.north().west())
                || !isSafeBlock(pos.north().east())
                || !isSafeBlock(pos.south().west())
                || !isSafeBlock(pos.south().east());
    }

    private boolean isColumnAir(BlockPos landingSpot, int minHeight) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos(landingSpot.getX(), landingSpot.getY(), landingSpot.getZ());
        final int maxY = mut.getY() + minHeight;
        for (int y = mut.getY() + 1; y <= maxY; y++) {
            mut.set(mut.getX(), y, mut.getZ());
            if (!ctx.world().isLoaded(mut) || !(ctx.world().getBlockState(mut).getBlock() instanceof AirBlock)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAirBubble(BlockPos pos) {
        final int radius = 4; // Half of the full width, rounded down, as we're counting blocks in each direction from the center
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mut.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (!ctx.world().isLoaded(mut) || !(ctx.world().getBlockState(mut).getBlock() instanceof AirBlock)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static final int LANDING_COLUMN_HEIGHT = 15;
    private Set<BetterBlockPos> badLandingSpots = new HashSet<>();

    private BetterBlockPos findSafeLandingSpot(BetterBlockPos target) {
        int radius = Math.max(0, Baritone.settings().elytraLandingSearchRadius.value);
        Map<Integer, List<BlockPos>> columnsByDistance = new TreeMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared <= radius * radius) {
                    columnsByDistance.computeIfAbsent(distanceSquared, ignored -> new ArrayList<>())
                            .add(new BlockPos(target.x + dx, target.y, target.z + dz));
                }
            }
        }
        for (List<BlockPos> columns : columnsByDistance.values()) {
            BetterBlockPos best = null;
            int bestYDifference = Integer.MAX_VALUE;
            for (BlockPos column : columns) {
                BetterBlockPos candidate = findClosestSafeLandingInColumn(column.getX(), column.getZ(), target.y);
                if (candidate == null) {
                    continue;
                }
                int finalFeetY = candidate.y - LANDING_COLUMN_HEIGHT + 1;
                int yDifference = Math.abs(finalFeetY - target.y);
                if (yDifference < bestYDifference) {
                    bestYDifference = yDifference;
                    best = candidate;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private BetterBlockPos findClosestSafeLandingInColumn(int x, int z, int targetFeetY) {
        int minFeetY = ctx.world().dimensionType().minY() + 1;
        int maxFeetY = ctx.world().dimensionType().minY() + ctx.world().dimensionType().height() - LANDING_COLUMN_HEIGHT;
        int clampedTargetY = Math.max(minFeetY, Math.min(maxFeetY, targetFeetY));
        int maxDifference = Math.max(clampedTargetY - minFeetY, maxFeetY - clampedTargetY);
        for (int difference = 0; difference <= maxDifference; difference++) {
            BetterBlockPos lower = safeLandingApproachAt(x, clampedTargetY - difference, z);
            if (lower != null) {
                return lower;
            }
            if (difference > 0) {
                BetterBlockPos upper = safeLandingApproachAt(x, clampedTargetY + difference, z);
                if (upper != null) {
                    return upper;
                }
            }
        }
        return null;
    }

    private BetterBlockPos safeLandingApproachAt(int x, int feetY, int z) {
        BlockPos floor = new BlockPos(x, feetY - 1, z);
        BetterBlockPos approach = new BetterBlockPos(x, feetY - 1 + LANDING_COLUMN_HEIGHT, z);
        if (!isInBounds(floor) || !isInBounds(approach) || !ctx.world().isLoaded(floor)) {
            return null;
        }
        if (!isSafeBlock(floor) || isAtEdge(floor)) {
            return null;
        }
        if (!isColumnAir(floor, LANDING_COLUMN_HEIGHT) || !hasAirBubble(approach) || badLandingSpots.contains(approach)) {
            return null;
        }
        return approach;
    }
}
