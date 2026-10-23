package thaumcraft.api;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Literal original ByteBuffer oracle, no world, registry or game runtime. */
class GolemBytePackingTest {
    @Test void everySignedByteAndIndexMatchTheOriginalBigEndianByteBuffer() {
        for (long data : new long[]{0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x0123456789abcdefL}) {
            for (int index = 0; index < 8; index++) {
                assertEquals(ByteBuffer.allocate(8).putLong(0, data).get(index), ThaumcraftApiHelper.getByteInLong(data, index));
                for (int value = -128; value < 128; value++) {
                    long expected = ByteBuffer.allocate(8).putLong(0, data).put(index, (byte)value).getLong(0);
                    assertEquals(expected, ThaumcraftApiHelper.setByteInLong(data, (byte)value, index));
                }
            }
        }
    }
    @Test void materialIsTheHighByteAndRankPreservesTheReservedLowSixteenBits() {
        long data = 0x000000000000abcdL;
        data = ThaumcraftApiHelper.setByteInLong(data, (byte)5, 0);
        data = ThaumcraftApiHelper.setByteInLong(data, (byte)7, 5);
        assertEquals(0x050000000007abcdL, data);
        assertEquals(0xabcdL, data & 65535L);
    }
    @Test void invalidIndicesRejectInsteadOfWrappingTheJavaShiftDistance() {
        for (int index : new int[]{-1, 8, 100}) {
            assertThrows(IndexOutOfBoundsException.class, () -> ThaumcraftApiHelper.getByteInLong(0, index));
            assertThrows(IndexOutOfBoundsException.class, () -> ThaumcraftApiHelper.setByteInLong(0, (byte)1, index));
        }
    }
}
