package thaumcraft.common.lib.network.misc;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;

class PacketKnowledgeGainTest {
    @Test
    void sourceShapeIsByteOrdinalAndOpaqueCategoryWithoutPlayerData() {
        var packet = new PacketKnowledgeGain((byte) EnumKnowledgeType.OBSERVATION.ordinal(), "MiXeD категории");
        assertEquals("thaumcraft:knowledge_gain", packet.type().id().toString());
        assertEquals(EnumKnowledgeType.OBSERVATION, packet.knowledgeType());
        var buffer = Unpooled.buffer();
        try {
            PacketKnowledgeGain.STREAM_CODEC.encode(buffer, packet);
            assertEquals(1, buffer.getByte(0));
            assertEquals(packet, PacketKnowledgeGain.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test
    void nullCategoryUsesSourceEmptyStringAndBothTypesRoundtrip() {
        for (var type : EnumKnowledgeType.values()) {
            var packet = new PacketKnowledgeGain((byte) type.ordinal(), null);
            assertEquals("", packet.categoryKey());
            var buffer = Unpooled.buffer();
            try {
                PacketKnowledgeGain.STREAM_CODEC.encode(buffer, packet);
                assertEquals(packet, PacketKnowledgeGain.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test
    void impossibleOrdinalIsRejectedInsteadOfClientArrayIndexFailure() {
        assertThrows(IllegalArgumentException.class, () -> new PacketKnowledgeGain((byte) -1, "BASICS"));
        assertThrows(IllegalArgumentException.class, () -> new PacketKnowledgeGain((byte) 2, "BASICS"));
        var buffer = Unpooled.buffer();
        try {
            buffer.writeByte(127).writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> PacketKnowledgeGain.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test
    void categoryWireBoundRejectsOversizedOpaqueKeysWithoutTruncation() {
        String max = "A".repeat(PacketKnowledgeGain.MAX_CATEGORY_CHARACTERS);
        var packet = new PacketKnowledgeGain((byte) 0, max);
        var buffer = Unpooled.buffer();
        try {
            PacketKnowledgeGain.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, PacketKnowledgeGain.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new PacketKnowledgeGain((byte) 0, max + "B"));
    }
}
