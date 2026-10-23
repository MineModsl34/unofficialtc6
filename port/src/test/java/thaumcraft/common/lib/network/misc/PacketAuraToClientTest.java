package thaumcraft.common.lib.network.misc;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;
import thaumcraft.common.world.aura.AuraChunk;

class PacketAuraToClientTest {
    @Test
    void independentOriginalWireLayoutIsSignedShortFloatFloat() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            PacketAuraToClient.STREAM_CODEC.encode(bytes, new PacketAuraToClient((short) 0x1234, 1F, -2F));
            assertEquals(10, bytes.readableBytes());
            byte[] actual = new byte[10];
            bytes.readBytes(actual);
            assertArrayEquals(new byte[]{0x12, 0x34, 0x3F, (byte) 0x80, 0, 0, (byte) 0xC0, 0, 0, 0}, actual);
        } finally { bytes.release(); }
    }

    @Test
    void independentDecodePreservesNegativeBaseAndFractionalAuraWithoutClamping() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            bytes.writeShort(-3).writeFloat(12.25F).writeFloat(7.5F);
            assertEquals(new PacketAuraToClient((short) -3, 12.25F, 7.5F), PacketAuraToClient.STREAM_CODEC.decode(bytes));
            assertFalse(bytes.isReadable());
        } finally { bytes.release(); }
    }

    @Test
    void snapshotDoesNotRetainMutableAura() {
        AuraChunk aura = new AuraChunk((short) 100, 10F, 2F);
        var snapshot = new PacketAuraToClient(aura);
        aura.setBase((short) 200);
        aura.setVis(20F);
        aura.setFlux(4F);
        assertEquals(new PacketAuraToClient((short) 100, 10F, 2F), snapshot);
    }

    @Test
    void rejectsTruncationAndTrailingBytesBeforeReading() {
        for (int length : new int[]{0, 1, 2, 9, 11, 128}) {
            ByteBuf bytes = Unpooled.buffer();
            try {
                bytes.writeZero(length);
                assertThrows(DecoderException.class, () -> PacketAuraToClient.STREAM_CODEC.decode(bytes));
                assertEquals(0, bytes.readerIndex());
            } finally { bytes.release(); }
        }
    }

    @Test
    void heapAndDirectBuffersRoundTripSignedAndFloatBoundaries() {
        for (ByteBuf bytes : new ByteBuf[]{Unpooled.buffer(), Unpooled.directBuffer()}) {
            try {
                for (var packet : new PacketAuraToClient[]{new PacketAuraToClient(Short.MIN_VALUE, -0F, Float.POSITIVE_INFINITY),
                    new PacketAuraToClient(Short.MAX_VALUE, Float.MIN_VALUE, Float.MAX_VALUE),
                    new PacketAuraToClient((short) 0, Float.NaN, -3F)}) {
                    bytes.clear();
                    PacketAuraToClient.STREAM_CODEC.encode(bytes, packet);
                    assertEquals(packet, PacketAuraToClient.STREAM_CODEC.decode(bytes));
                }
            } finally { bytes.release(); }
        }
    }
}
