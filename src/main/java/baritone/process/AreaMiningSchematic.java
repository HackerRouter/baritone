/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.process;

import baritone.api.process.area.AreaMiningLiquidPolicy;
import baritone.api.process.area.AreaMiningOptions;
import baritone.api.process.area.IColumnarArea;
import baritone.api.schematic.AbstractSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

final class AreaMiningSchematic extends AbstractSchematic {

    private final IColumnarArea area;
    private final AreaMiningOptions options;
    private final BlockPos origin;

    AreaMiningSchematic(IColumnarArea area, AreaMiningOptions options) {
        super(width(area, options), height(area, options), length(area, options));
        this.area = area;
        this.options = options;
        final boolean seal = options.liquidPolicy() == AreaMiningLiquidPolicy.SEAL_BOUNDARY;
        if (seal && (area.minX() == Integer.MIN_VALUE || area.minZ() == Integer.MIN_VALUE
                || area.maxX() == Integer.MAX_VALUE || area.maxY() == Integer.MAX_VALUE || area.maxZ() == Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Area boundary cannot be expanded for liquid sealing");
        }
        this.origin = new BlockPos(area.minX() - (seal ? 1 : 0), area.minY(), area.minZ() - (seal ? 1 : 0));
    }

    BlockPos origin() {
        return origin;
    }

    @Override
    public boolean inSchematic(int x, int y, int z, BlockState currentState) {
        if (!super.inSchematic(x, y, z, currentState)) {
            return false;
        }
        final int worldX = x + origin.getX();
        final int worldY = y + origin.getY();
        final int worldZ = z + origin.getZ();
        if (area.contains(worldX, worldY, worldZ)) {
            return options.liquidPolicy() != AreaMiningLiquidPolicy.AVOID
                    || currentState == null
                    || currentState.getFluidState().isEmpty();
        }
        return options.liquidPolicy() == AreaMiningLiquidPolicy.SEAL_BOUNDARY
                && currentState != null
                && !currentState.getFluidState().isEmpty()
                && touchesSideOrTopBoundary(worldX, worldY, worldZ);
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        final int worldX = x + origin.getX();
        final int worldY = y + origin.getY();
        final int worldZ = z + origin.getZ();
        if (area.contains(worldX, worldY, worldZ)) {
            if (current != null && !current.getFluidState().isEmpty()
                    && options.liquidPolicy() != AreaMiningLiquidPolicy.AVOID) {
                return sealingState(approxPlaceable);
            }
            return Blocks.AIR.defaultBlockState();
        }
        return sealingState(approxPlaceable);
    }

    private BlockState sealingState(List<BlockState> approxPlaceable) {
        for (BlockState state : approxPlaceable) {
            if (options.sealingBlocks().contains(state.getBlock())) {
                return state;
            }
        }
        return options.sealingBlocks().get(0).defaultBlockState();
    }

    private boolean touchesSideOrTopBoundary(int x, int y, int z) {
        if (y == area.maxY() + 1 && area.containsXZ(x, z)) {
            return true;
        }
        if (y < area.minY() || y > area.maxY()) {
            return false;
        }
        return area.containsXZ(x + 1, z)
                || area.containsXZ(x - 1, z)
                || area.containsXZ(x, z + 1)
                || area.containsXZ(x, z - 1);
    }

    private static int width(IColumnarArea area, AreaMiningOptions options) {
        return checkedDimension((long) area.maxX() - area.minX() + 1L + (options.liquidPolicy() == AreaMiningLiquidPolicy.SEAL_BOUNDARY ? 2L : 0L));
    }

    private static int height(IColumnarArea area, AreaMiningOptions options) {
        return checkedDimension((long) area.maxY() - area.minY() + 1L + (options.liquidPolicy() == AreaMiningLiquidPolicy.SEAL_BOUNDARY ? 1L : 0L));
    }

    private static int length(IColumnarArea area, AreaMiningOptions options) {
        return checkedDimension((long) area.maxZ() - area.minZ() + 1L + (options.liquidPolicy() == AreaMiningLiquidPolicy.SEAL_BOUNDARY ? 2L : 0L));
    }

    private static int checkedDimension(long value) {
        if (value <= 0L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Area dimension is outside the supported integer range: " + value);
        }
        return (int) value;
    }
}
