package thaumcraft.common.lib.network.misc;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

class PacketArcaneRecipeTransferResultTest {
    @Test void receiptCarriesExactRequestMenuStateAndAuthoritativeOutcome() {
        for (boolean accepted : new boolean[] {false, true}) {
            var request = new PacketArcaneRecipeTransferResult(5, 255, 32767, accepted);
            var buffer = Unpooled.buffer();
            try {
                PacketArcaneRecipeTransferResult.STREAM_CODEC.encode(buffer, request);
                assertEquals(8, buffer.readableBytes()); assertEquals(request, PacketArcaneRecipeTransferResult.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
    @Test void exactBodyIdentityAndBooleanBoundsAreEnforced() {
        for (int badSize : new int[] {0, 7, 9, 512}) {
            var buffer = Unpooled.buffer();
            try { buffer.writeZero(badSize); assertThrows(DecoderException.class, () -> PacketArcaneRecipeTransferResult.STREAM_CODEC.decode(buffer)); }
            finally { buffer.release(); }
        }
        for (int field : new int[] {0, 1, 2}) {
            var buffer = Unpooled.buffer();
            try {
                PacketArcaneRecipeTransferResult.STREAM_CODEC.encode(buffer, new PacketArcaneRecipeTransferResult(1, 2, 3, true));
                if (field == 0) buffer.setInt(0, 0);
                if (field == 1) buffer.setShort(5, 65535);
                if (field == 2) buffer.setByte(7, 2);
                assertThrows(DecoderException.class, () -> PacketArcaneRecipeTransferResult.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
}
