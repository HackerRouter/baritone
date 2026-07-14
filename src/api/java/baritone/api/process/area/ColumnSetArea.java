/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.api.process.area;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * An exact set of XZ block columns, useful for disconnected areas and areas with holes.
 * Y coordinates in the supplied positions are ignored.
 */
public final class ColumnSetArea implements IColumnarArea {

    private final Set<Long> columns;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    public ColumnSetArea(Collection<BlockPos> columns, int minY, int maxY) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("At least one XZ column is required");
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("minY must not exceed maxY");
        }
        this.columns = new HashSet<>();
        int foundMinX = Integer.MAX_VALUE;
        int foundMaxX = Integer.MIN_VALUE;
        int foundMinZ = Integer.MAX_VALUE;
        int foundMaxZ = Integer.MIN_VALUE;
        for (BlockPos column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("Columns cannot contain null");
            }
            this.columns.add(pack(column.getX(), column.getZ()));
            foundMinX = Math.min(foundMinX, column.getX());
            foundMaxX = Math.max(foundMaxX, column.getX());
            foundMinZ = Math.min(foundMinZ, column.getZ());
            foundMaxZ = Math.max(foundMaxZ, column.getZ());
        }
        this.minX = foundMinX;
        this.maxX = foundMaxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = foundMinZ;
        this.maxZ = foundMaxZ;
    }

    @Override public int minX() { return minX; }
    @Override public int maxX() { return maxX; }
    @Override public int minY() { return minY; }
    @Override public int maxY() { return maxY; }
    @Override public int minZ() { return minZ; }
    @Override public int maxZ() { return maxZ; }

    @Override
    public boolean containsXZ(int x, int z) {
        return columns.contains(pack(x, z));
    }

    @Override
    public long estimatedBlockCount() {
        return (long) columns.size() * ((long) maxY - minY + 1L);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
