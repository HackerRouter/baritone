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

package baritone.process.elytra;

import baritone.Baritone;
import baritone.api.event.events.BlockChangeEvent;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import dev.babbaj.pathfinder.PathSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.SoftReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Brady
 */
public final class NetherPathfinderContext {

    // This lock must be held while there are active pointers to chunks in java,
    // but we just hold it for the entire tick so we don't have to think much about it.
    public final Object cullingLock = new Object();

    // Visible for access in BlockStateOctreeInterface
    final long context;
    private final long seed;
    private final int dimension;
    private final int minY;
    private final int height;
    private final ExecutorService executor;

    public NetherPathfinderContext(Level world, long seed) {
        this.dimension = dimensionKind(world);
        this.minY = world.dimensionType().minY();
        this.height = world.dimensionType().height();
        this.context = NetherPathfinder.newContext(seed, null, this.dimension, this.minY, this.height, true);
        this.seed = seed;
        this.executor = Executors.newSingleThreadExecutor();
    }

    private static int dimensionKind(Level world) {
        if (world.dimension() == Level.OVERWORLD) {
            return NetherPathfinder.DIMENSION_OVERWORLD;
        }
        if (world.dimension() == Level.NETHER) {
            return NetherPathfinder.DIMENSION_NETHER;
        }
        if (world.dimension() == Level.END) {
            return NetherPathfinder.DIMENSION_END;
        }
        return NetherPathfinder.DIMENSION_GENERIC;
    }

    public boolean hasChunk(ChunkPos pos) {
        return NetherPathfinder.hasChunkFromJava(this.context, pos.x(), pos.z());
    }

    public void queueCacheCulling(int chunkX, int chunkZ, int maxDistanceBlocks, BlockStateOctreeInterface boi) {
        this.executor.execute(() -> {
            synchronized (this.cullingLock) {
                boi.chunkPtr = 0L;
                NetherPathfinder.cullFarChunks(this.context, chunkX, chunkZ, maxDistanceBlocks);
            }
        });
    }

    public void queueForPacking(final LevelChunk chunkIn) {
        final SoftReference<LevelChunk> ref = new SoftReference<>(chunkIn);
        this.executor.execute(() -> {
            // TODO: Prioritize packing recent chunks and/or ones that the path goes through,
            //       and prune the oldest chunks per chunkPackerQueueMaxSize
            final LevelChunk chunk = ref.get();
            if (chunk != null) {
                synchronized (this.cullingLock) {
                    writeChunkData(chunk);
                }
            }
        });
    }

    public void queueBlockUpdate(BlockChangeEvent event) {
        this.executor.execute(() -> {
            synchronized (this.cullingLock) {
                ChunkPos chunkPos = event.getChunkPos();
                long ptr = NetherPathfinder.getChunk(this.context, chunkPos.x(), chunkPos.z());
                if (ptr == 0) return; // this shouldn't ever happen
                event.getBlocks().forEach(pair -> {
                    BlockPos pos = pair.first();
                    final int internalY = pos.getY() - this.minY;
                    if (internalY < 0 || internalY >= this.height) return;
                    Octree.setBlock(ptr, pos.getX() & 15, internalY, pos.getZ() & 15, !pair.second().isAir());
                });
            }
        });
    }

    public CompletableFuture<PathSegment> pathFindAsync(final BlockPos src, final BlockPos dst) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this.cullingLock) {
                final PathSegment segment = NetherPathfinder.pathFind(
                        this.context,
                        src.getX(), src.getY(), src.getZ(),
                        dst.getX(), dst.getY(), dst.getZ(),
                        true,
                        false,
                        10000,
                        this.dimension != NetherPathfinder.DIMENSION_NETHER || !Baritone.settings().elytraPredictTerrain.value,
                        1.0
                );
                if (segment == null) {
                    throw new PathCalculationException("Path calculation failed");
                }
                return segment;
            }
        }, this.executor);
    }

    /**
     * Performs a raytrace from the given start position to the given end position, returning {@code true} if there is
     * visibility between the two points.
     *
     * @param startX The start X coordinate
     * @param startY The start Y coordinate
     * @param startZ The start Z coordinate
     * @param endX   The end X coordinate
     * @param endY   The end Y coordinate
     * @param endZ   The end Z coordinate
     * @return {@code true} if there is visibility between the points
     */
    public boolean raytrace(final double startX, final double startY, final double startZ,
                            final double endX, final double endY, final double endZ) {
        synchronized (this.cullingLock) {
            return NetherPathfinder.isVisible(this.context, NetherPathfinder.CACHE_MISS_SOLID, startX, startY, startZ, endX, endY, endZ);
        }
    }

    /**
     * Performs a raytrace from the given start position to the given end position, returning {@code true} if there is
     * visibility between the two points.
     *
     * @param start The starting point
     * @param end   The ending point
     * @return {@code true} if there is visibility between the points
     */
    public boolean raytrace(final Vec3 start, final Vec3 end) {
        synchronized (this.cullingLock) {
            return NetherPathfinder.isVisible(this.context, NetherPathfinder.CACHE_MISS_SOLID, start.x, start.y, start.z, end.x, end.y, end.z);
        }
    }

    public boolean raytrace(final int count, final double[] src, final double[] dst, final int visibility) {
        synchronized (this.cullingLock) {
            switch (visibility) {
                case Visibility.ALL:
                    return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, false) == -1;
                case Visibility.NONE:
                    return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, true) == -1;
                case Visibility.ANY:
                    return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, true) != -1;
                default:
                    throw new IllegalArgumentException("lol");
            }
        }
    }

    public void raytrace(final int count, final double[] src, final double[] dst, final boolean[] hitsOut, final double[] hitPosOut) {
        synchronized (this.cullingLock) {
            NetherPathfinder.raytrace(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, hitsOut, hitPosOut);
        }
    }

    public void cancel() {
        NetherPathfinder.cancel(this.context);
    }

    public void destroy() {
        this.cancel();
        // Ignore anything that was queued up, just shutdown the executor
        this.executor.shutdownNow();

        try {
            while (!this.executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {}
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        NetherPathfinder.freeContext(this.context);
    }

    public long getSeed() {
        return this.seed;
    }

    int toInternalY(int worldY) {
        return worldY - this.minY;
    }

    boolean containsWorldY(int worldY) {
        final int internalY = toInternalY(worldY);
        return internalY >= 0 && internalY < this.height;
    }

    private void writeChunkData(LevelChunk chunk) {
        try {
            final boolean[] data = new boolean[16 * 16 * this.height];
            final LevelChunkSection[] sections = chunk.getSections();
            final int sectionCount = Math.min(sections.length, (this.height + 15) >> 4);
            for (int sectionY = 0; sectionY < sectionCount; sectionY++) {
                final LevelChunkSection section = sections[sectionY];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                final int baseY = sectionY << 4;
                for (int y = 0; y < 16 && baseY + y < this.height; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            final BlockState state = section.getBlockState(x, y, z);
                            data[((baseY + y) << 8) | (z << 4) | x] = !state.isAir();
                        }
                    }
                }
            }
            NetherPathfinder.insertChunkData(this.context, chunk.getPos().x(), chunk.getPos().z(), data);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static final class Visibility {

        public static final int ALL = 0;
        public static final int NONE = 1;
        public static final int ANY = 2;

        private Visibility() {}
    }

    public static boolean isSupported() {
        return NetherPathfinder.isThisSystemSupported();
    }
}
