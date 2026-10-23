package thaumcraft.common.lib.capabilities.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

/** Pure fixture geometry only: these checks do not claim real portal/credits/network acceptance. */
class PlayerDataNetworkAuditFixtureTest {
    @Test
    void remoteEndPlatformIsOutsideDragonPlayerScanArenaAndOriginPodium() {
        BlockPos center = PlayerDataNetworkAudit.END_PLATFORM_CENTER;
        assertEquals(new BlockPos(1024, 80, 1024), center);
        for (int dx = -3; dx <= 4; dx++) for (int dz = -3; dz <= 3; dz++) {
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            assertTrue((long)x * x + (long)z * z > 192L * 192L);
            assertTrue(Math.floorDiv(x, 16) > 8 && Math.floorDiv(z, 16) > 8);
            assertTrue(x > 4 && z > 4);
        }
        assertEquals(center.above(), PlayerDataNetworkAudit.END_PORTAL_POSITION);
    }

    @Test
    void centeredStandingPlayerOverlapsPortalButRecordedOriginFailureDoesNot() {
        BlockPos portal = PlayerDataNetworkAudit.END_PORTAL_POSITION;
        // EndPortalBlock.SHAPE is Block.box(0,6,0,16,12,16), not a full block.
        AABB shape = new AABB(portal.getX(), portal.getY() + 6.0 / 16.0, portal.getZ(),
            portal.getX() + 1, portal.getY() + 12.0 / 16.0, portal.getZ() + 1);
        AABB standing = new AABB(portal.getX() + 0.2, portal.getY(), portal.getZ() + 0.2,
            portal.getX() + 0.8, portal.getY() + 1.8, portal.getZ() + 0.8);
        assertTrue(standing.intersects(shape));
        double failedX = 1.4997197379614304;
        double failedZ = 0.08557145160759871;
        AABB recordedFailure = new AABB(failedX - 0.3, 80, failedZ - 0.3, failedX + 0.3, 81.8, failedZ + 0.3);
        assertFalse(recordedFailure.intersects(new AABB(0, 81 + 6.0 / 16.0, 0, 1, 81 + 12.0 / 16.0, 1)));
    }
}
