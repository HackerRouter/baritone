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

import java.util.List;

/**
 * An immutable polygonal XZ footprint extruded through an inclusive Y range.
 * Block centers are tested against the polygon. Vertex Y coordinates are ignored.
 */
public final class PolygonColumnarArea implements IColumnarArea {

    private final int[] x;
    private final int[] z;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    public PolygonColumnarArea(List<BlockPos> vertices, int minY, int maxY) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("A polygon requires at least three vertices");
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("minY must not exceed maxY");
        }
        this.x = new int[vertices.size()];
        this.z = new int[vertices.size()];
        int foundMinX = Integer.MAX_VALUE;
        int foundMaxX = Integer.MIN_VALUE;
        int foundMinZ = Integer.MAX_VALUE;
        int foundMaxZ = Integer.MIN_VALUE;
        for (int i = 0; i < vertices.size(); i++) {
            BlockPos vertex = vertices.get(i);
            if (vertex == null) {
                throw new IllegalArgumentException("Polygon vertices cannot be null");
            }
            this.x[i] = vertex.getX();
            this.z[i] = vertex.getZ();
            foundMinX = Math.min(foundMinX, this.x[i]);
            foundMaxX = Math.max(foundMaxX, this.x[i]);
            foundMinZ = Math.min(foundMinZ, this.z[i]);
            foundMaxZ = Math.max(foundMaxZ, this.z[i]);
        }
        this.minX = foundMinX;
        this.maxX = foundMaxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = foundMinZ;
        this.maxZ = foundMaxZ;
    }

    @Override
    public int minX() {
        return minX;
    }

    @Override
    public int maxX() {
        return maxX;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public int minZ() {
        return minZ;
    }

    @Override
    public int maxZ() {
        return maxZ;
    }

    @Override
    public boolean containsXZ(int blockX, int blockZ) {
        if (blockX < minX || blockX > maxX || blockZ < minZ || blockZ > maxZ) {
            return false;
        }
        final double px = blockX + 0.5D;
        final double pz = blockZ + 0.5D;
        boolean inside = false;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            if (onSegment(px, pz, x[j], z[j], x[i], z[i])) {
                return true;
            }
            if ((z[i] > pz) != (z[j] > pz)
                    && px < (double) (x[j] - x[i]) * (pz - z[i]) / (double) (z[j] - z[i]) + x[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean onSegment(double px, double pz, int ax, int az, int bx, int bz) {
        double cross = (px - ax) * (bz - az) - (pz - az) * (bx - ax);
        if (Math.abs(cross) > 1.0E-9D) {
            return false;
        }
        return px >= Math.min(ax, bx) && px <= Math.max(ax, bx)
                && pz >= Math.min(az, bz) && pz <= Math.max(az, bz);
    }
}
