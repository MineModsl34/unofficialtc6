package thaumcraft.common.lib.enchantment;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class InfusionEnchantmentsTest {
    @Test void retainsAllThirteenSourceOrdinalIdsResearchAndMaximums() {
        assertEquals(List.of("COLLECTOR", "DESTRUCTIVE", "BURROWING", "SOUNDING", "REFINING", "ARCING", "ESSENCE", "VISBATTERY", "VISCHARGE", "SWIFT", "AGILE", "INFESTED", "LAMPLIGHT"),
            java.util.Arrays.stream(EnumInfusionEnchantment.values()).map(Enum::name).toList());
        assertEquals(4, EnumInfusionEnchantment.SOUNDING.maxLevel); assertEquals(5, EnumInfusionEnchantment.ESSENCE.maxLevel);
        assertEquals("?", EnumInfusionEnchantment.VISBATTERY.research);
        assertTrue(EnumInfusionEnchantment.LAMPLIGHT.toolClasses.contains("shovel"));
    }
    @Test void duplicateFirstEntryLookupAndOnlyFirstMonotonicUpgradeMatchSource() {
        var value = new InfusionEnchantments(List.of(new InfusionEnchantments.Entry(3, 1), new InfusionEnchantments.Entry(3, 4), new InfusionEnchantments.Entry(0, 0)));
        assertEquals(1, value.level(EnumInfusionEnchantment.SOUNDING));
        assertSame(value, value.add(EnumInfusionEnchantment.SOUNDING, 1));
        var changed = value.add(EnumInfusionEnchantment.SOUNDING, 2);
        assertEquals(2, changed.entries().get(0).level()); assertEquals(4, changed.entries().get(1).level());
        assertEquals(3, changed.entries().size()); assertEquals(1, value.entries().get(0).level());
        assertSame(changed, changed.add(EnumInfusionEnchantment.SOUNDING, 5));
    }
    @Test void boundedCodecsPreserveRawSignedShortLevelsAndRejectInvalidCountsAndIds() {
        var value = new InfusionEnchantments(List.of(new InfusionEnchantments.Entry(0, -12), new InfusionEnchantments.Entry(12, 1)));
        var encoded = InfusionEnchantments.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, InfusionEnchantments.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertTrue(InfusionEnchantments.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[{\"id\":13,\"lvl\":1}]")).error().isPresent());
        var buffer = Unpooled.buffer();
        try {
            InfusionEnchantments.STREAM_CODEC.encode(buffer, value); assertEquals(value, InfusionEnchantments.STREAM_CODEC.decode(buffer));
            buffer.clear(); new FriendlyByteBuf(buffer).writeVarInt(257);
            assertThrows(IllegalArgumentException.class, () -> InfusionEnchantments.STREAM_CODEC.decode(buffer));
            buffer.clear(); new FriendlyByteBuf(buffer).writeVarInt(1); buffer.writeByte(13).writeShort(1);
            assertThrows(IllegalArgumentException.class, () -> InfusionEnchantments.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new InfusionEnchantments.Entry(0, 32768));
    }
}
