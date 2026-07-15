package baritone.process;

import baritone.api.process.area.AreaMiningLiquidPolicy;
import baritone.api.process.area.AreaMiningOptions;
import baritone.api.process.area.IColumnarArea;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AreaMiningSchematicTest {

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final IColumnarArea AREA = new IColumnarArea() {
        @Override public int minX() { return 10; }
        @Override public int maxX() { return 11; }
        @Override public int minY() { return 5; }
        @Override public int maxY() { return 6; }
        @Override public int minZ() { return 20; }
        @Override public int maxZ() { return 21; }
        @Override public boolean containsXZ(int x, int z) {
            return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
        }
    };

    @Test
    public void sealPolicyAddsDynamicSideAndTopShell() {
        AreaMiningSchematic schematic = new AreaMiningSchematic(
                AREA, new AreaMiningOptions(AreaMiningLiquidPolicy.SEAL_BOUNDARY));

        assertEquals(4, schematic.widthX());
        assertEquals(3, schematic.heightY());
        assertEquals(4, schematic.lengthZ());
        assertTrue(schematic.inSchematic(1, 0, 1, Blocks.STONE.defaultBlockState()));
        assertTrue(schematic.inSchematic(0, 0, 1, Blocks.WATER.defaultBlockState()));
        assertTrue(schematic.inSchematic(1, 2, 1, Blocks.WATER.defaultBlockState()));
        assertFalse(schematic.inSchematic(0, 0, 1, Blocks.STONE.defaultBlockState()));
        assertEquals(Blocks.AIR, schematic.desiredState(1, 0, 1, Blocks.STONE.defaultBlockState(), Collections.emptyList()).getBlock());
        assertEquals(Blocks.NETHERRACK, schematic.desiredState(0, 0, 1, Blocks.WATER.defaultBlockState(), Collections.emptyList()).getBlock());
        assertEquals(Blocks.NETHERRACK, schematic.desiredState(1, 0, 1, Blocks.WATER.defaultBlockState(), Collections.emptyList()).getBlock());
    }

    @Test
    public void avoidPolicyExcludesLiquidCells() {
        AreaMiningSchematic schematic = new AreaMiningSchematic(
                AREA, new AreaMiningOptions(AreaMiningLiquidPolicy.AVOID));

        assertTrue(schematic.inSchematic(0, 0, 0, Blocks.STONE.defaultBlockState()));
        assertFalse(schematic.inSchematic(0, 0, 0, Blocks.WATER.defaultBlockState()));
    }

    @Test
    public void disallowedBlocksAreExcludedFromAreaMining() {
        AreaMiningSchematic schematic = new AreaMiningSchematic(
                AREA, new AreaMiningOptions(AreaMiningLiquidPolicy.SEAL_BOUNDARY), List.of(Blocks.STONE));

        assertFalse(schematic.inSchematic(1, 0, 1, Blocks.STONE.defaultBlockState()));
        assertTrue(schematic.inSchematic(1, 0, 1, Blocks.DIRT.defaultBlockState()));
    }

    @Test
    public void emptySealingListSkipsLiquidsButKeepsOrdinaryBlocks() {
        AreaMiningSchematic schematic = new AreaMiningSchematic(
                AREA, new AreaMiningOptions(AreaMiningLiquidPolicy.SEAL_BOUNDARY, List.of(), 64L));

        assertFalse(schematic.inSchematic(1, 0, 1, Blocks.WATER.defaultBlockState()));
        assertTrue(schematic.inSchematic(1, 0, 1, Blocks.STONE.defaultBlockState()));
        assertEquals(64L, new AreaMiningOptions(AreaMiningLiquidPolicy.AVOID, List.of(), 64L).blockLimit());
    }

    @Test
    public void multiLevelSideBoundaryUsesVerticallyAlignedInteriorApproaches() {
        for (int y = AREA.minY(); y <= AREA.maxY(); y++) {
            List<BetterBlockPos> approaches = BuilderProcess.areaInteriorNeighbors(AREA, new BetterBlockPos(9, y, 20));
            assertEquals(List.of(new BetterBlockPos(10, y, 20)), approaches);
        }
    }

    @Test
    public void topBoundaryDoesNotHaveSameLevelInteriorApproaches() {
        assertTrue(BuilderProcess.areaInteriorNeighbors(AREA, new BetterBlockPos(10, 7, 20)).isEmpty());
    }
}
