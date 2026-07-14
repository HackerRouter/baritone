package baritone.api.process.area;

import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PolygonColumnarAreaTest {

    @Test
    public void extrudesPolygonThroughInclusiveYRange() {
        PolygonColumnarArea area = new PolygonColumnarArea(Arrays.asList(
                new BlockPos(0, 99, 0),
                new BlockPos(3, 99, 0),
                new BlockPos(3, 99, 3),
                new BlockPos(0, 99, 3)
        ), -5, 7);

        assertTrue(area.contains(0, -5, 0));
        assertTrue(area.contains(2, 7, 2));
        assertFalse(area.contains(3, 0, 2));
        assertFalse(area.contains(2, -6, 2));
        assertFalse(area.contains(2, 8, 2));
    }

    @Test
    public void supportsConcaveFootprints() {
        PolygonColumnarArea area = new PolygonColumnarArea(Arrays.asList(
                new BlockPos(0, 0, 0),
                new BlockPos(4, 0, 0),
                new BlockPos(4, 0, 1),
                new BlockPos(1, 0, 1),
                new BlockPos(1, 0, 4),
                new BlockPos(0, 0, 4)
        ), 0, 0);

        assertTrue(area.containsXZ(0, 3));
        assertTrue(area.containsXZ(3, 0));
        assertFalse(area.containsXZ(2, 2));
    }

    @Test
    public void exactColumnSetSupportsHolesAndDisconnectedColumns() {
        ColumnSetArea area = new ColumnSetArea(Arrays.asList(
                new BlockPos(0, 100, 0),
                new BlockPos(2, -100, 0),
                new BlockPos(20, 0, 20)
        ), 4, 6);

        assertTrue(area.contains(0, 4, 0));
        assertTrue(area.contains(20, 6, 20));
        assertFalse(area.containsXZ(1, 0));
        assertEquals(9L, area.estimatedBlockCount());
    }
}
