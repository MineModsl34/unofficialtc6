package thaumcraft.common.lib.capabilities;

import static org.junit.jupiter.api.Assertions.*;
import static thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerWarp;

class PlayerWarpTest {
    @Test
    void enumOrderingAndEmptyFactoryMatchLegacyArrayLayout() {
        assertArrayEquals(new IPlayerWarp.EnumWarpType[]{PERMANENT, NORMAL, TEMPORARY},
            IPlayerWarp.EnumWarpType.values());
        PlayerWarp data = new PlayerWarp();
        assertArrayEquals(new int[]{0, 0, 0}, data.serializeNBT().getIntArray("warp"));
        assertEquals(0, data.getCounter());
        assertEquals(java.util.Set.of("warp", "counter"), data.serializeNBT().getAllKeys());
        assertTrue(data.serializeNBT().contains("warp", Tag.TAG_INT_ARRAY));
        assertTrue(data.serializeNBT().contains("counter", Tag.TAG_INT));
    }

    @Test
    void mutatorsClampOnlyTheirSelectedTypeAndAllowSignedAmounts() {
        PlayerWarp data = new PlayerWarp();
        data.set(PERMANENT, -1);
        assertEquals(0, data.get(PERMANENT));
        data.set(NORMAL, 600);
        assertEquals(500, data.get(NORMAL));
        data.set(TEMPORARY, 10);
        assertEquals(17, data.add(TEMPORARY, 7));
        assertEquals(12, data.add(TEMPORARY, -5));
        assertEquals(20, data.reduce(TEMPORARY, -8));
        assertEquals(0, data.reduce(TEMPORARY, 999));
        assertEquals(500, data.add(TEMPORARY, 999));
        assertEquals(0, data.add(TEMPORARY, -999));
        assertEquals(500, data.get(NORMAL));
        assertEquals(0, data.get(PERMANENT));
    }

    @Test
    void intOverflowIsPerformedBeforeClamping() {
        PlayerWarp data = new PlayerWarp();
        data.set(NORMAL, 500);
        assertEquals(0, data.add(NORMAL, Integer.MAX_VALUE));
        assertEquals(0, data.reduce(NORMAL, Integer.MIN_VALUE));
        data.deserializeNBT(nbt(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE, 0}, 7));
        assertEquals(500, data.add(PERMANENT, -1));
        assertEquals(0, data.reduce(NORMAL, -1));
        assertEquals(7, data.getCounter());
    }

    @Test
    void loadingDoesNotClampAndTruncatesLongArrays() {
        PlayerWarp data = new PlayerWarp();
        data.deserializeNBT(nbt(new int[]{-7, 501, Integer.MAX_VALUE, 88}, -9));
        assertEquals(-7, data.get(PERMANENT));
        assertEquals(501, data.get(NORMAL));
        assertEquals(Integer.MAX_VALUE, data.get(TEMPORARY));
        assertArrayEquals(new int[]{-7, 501, Integer.MAX_VALUE}, data.serializeNBT().getIntArray("warp"));
        assertEquals(-9, data.getCounter());
        data.set(NORMAL, 12);
        assertEquals(-7, data.get(PERMANENT));
        assertEquals(Integer.MAX_VALUE, data.get(TEMPORARY));
        assertEquals(12, data.get(NORMAL));
    }

    @Test
    void shortArraysPadAndNonnullEmptyLoadClears() {
        PlayerWarp data = new PlayerWarp();
        data.set(NORMAL, 100);
        data.set(TEMPORARY, 200);
        data.deserializeNBT(nbt(new int[]{21}, 3));
        assertArrayEquals(new int[]{21, 0, 0}, data.serializeNBT().getIntArray("warp"));
        data.deserializeNBT(new CompoundTag());
        assertArrayEquals(new int[]{0, 0, 0}, data.serializeNBT().getIntArray("warp"));
        assertEquals(0, data.getCounter());
        data.deserializeNBT(nbt(new int[0], 5));
        assertEquals(5, data.getCounter());
    }

    @Test
    void nullLoadDoesNotModifyData() {
        PlayerWarp data = new PlayerWarp();
        data.set(PERMANENT, 8);
        data.setCounter(41);
        data.deserializeNBT((CompoundTag) null);
        assertEquals(8, data.get(PERMANENT));
        assertEquals(41, data.getCounter());
    }

    @Test
    void counterIsIndependentAndUnclampedUntilClear() {
        PlayerWarp data = new PlayerWarp();
        data.setCounter(Integer.MIN_VALUE);
        data.add(NORMAL, 40);
        data.reduce(NORMAL, 1);
        data.set(TEMPORARY, 30);
        assertEquals(Integer.MIN_VALUE, data.getCounter());
        data.setCounter(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, data.serializeNBT().getInt("counter"));
        data.clear();
        assertEquals(0, data.getCounter());
        assertArrayEquals(new int[]{0, 0, 0}, data.serializeNBT().getIntArray("warp"));
    }

    @Test
    void deserializeCopiesValuesRatherThanAliasingIncomingArray() {
        int[] values = {1, 2, 3};
        CompoundTag incoming = nbt(values, 4);
        PlayerWarp data = new PlayerWarp();
        data.deserializeNBT(incoming);
        values[0] = 100;
        incoming.getIntArray("warp")[1] = 200;
        incoming.putInt("counter", 9);
        assertArrayEquals(new int[]{1, 2, 3}, data.serializeNBT().getIntArray("warp"));
        assertEquals(4, data.getCounter());
    }

    @Test
    void rawSerializedArrayIsLiveAsInOriginalButCopyIsStable() {
        PlayerWarp data = new PlayerWarp();
        data.set(PERMANENT, 10);
        data.setCounter(7);
        CompoundTag raw = data.serializeNBT();
        CompoundTag snapshot = raw.copy();
        data.set(PERMANENT, 20);
        data.setCounter(8);
        assertEquals(20, raw.getIntArray("warp")[0]);
        assertEquals(7, raw.getInt("counter")); // scalar was captured at serialization
        assertEquals(10, snapshot.getIntArray("warp")[0]);
        raw.getIntArray("warp")[2] = -99;
        assertEquals(-99, data.get(TEMPORARY));
        data.clear();
        assertEquals(-99, raw.getIntArray("warp")[2]); // clear replaces the model's array
        assertEquals(0, data.get(TEMPORARY));
    }

    @Test
    void registryProviderBridgeUsesSameLegacyFormat() {
        PlayerWarp data = new PlayerWarp();
        data.set(PERMANENT, 1);
        data.set(NORMAL, 2);
        data.set(TEMPORARY, 3);
        data.setCounter(4);
        PlayerWarp copy = new PlayerWarp();
        copy.deserializeNBT(null, data.serializeNBT(null));
        assertEquals(data.serializeNBT(), copy.serializeNBT());
        copy.set(PERMANENT, 50);
        assertEquals(1, data.get(PERMANENT));
    }

    private static CompoundTag nbt(int[] values, int counter) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("warp", values);
        tag.putInt("counter", counter);
        return tag;
    }
}
