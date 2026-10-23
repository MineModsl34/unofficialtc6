package thaumcraft.common.lib.network.misc;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PacketArcaneRecipeTransferTest {
    private static PacketArcaneRecipeTransfer request() {
        return new PacketArcaneRecipeTransfer(12, 234, 891, ResourceLocation.parse("thaumcraft:arcane_test"), true);
    }
    @Test void roundtripCarriesOnlyMenuStateRealRecipeIdAndBatchFlag() {
        var buffer = Unpooled.buffer();
        try {
            PacketArcaneRecipeTransfer.STREAM_CODEC.encode(buffer, request());
            assertEquals(request(), PacketArcaneRecipeTransfer.STREAM_CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void trailingAndTruncatedBodiesAreRejected() {
        var full = Unpooled.buffer();
        try {
            PacketArcaneRecipeTransfer.STREAM_CODEC.encode(full, request());
            int size = full.readableBytes();
            for (int length = 0; length < size; length++) {
                var shortBody = full.copy(0, length);
                try { assertThrows(DecoderException.class, () -> PacketArcaneRecipeTransfer.STREAM_CODEC.decode(shortBody)); }
                finally { shortBody.release(); }
            }
            full.writeByte(1); assertThrows(DecoderException.class, () -> PacketArcaneRecipeTransfer.STREAM_CODEC.decode(full));
        } finally { full.release(); }
    }
    @Test void oversizedBodyAndNonBooleanFlagAndOutOfRangeStateAreRejected() {
        for (int corruption : new int[] {0, 1, 2}) {
            var buffer = Unpooled.buffer();
            try {
                PacketArcaneRecipeTransfer.STREAM_CODEC.encode(buffer, request());
                if (corruption == 0) buffer.writeZero(PacketArcaneRecipeTransfer.MAX_BYTES + 1);
                if (corruption == 1) buffer.setByte(3, 2);
                if (corruption == 2) buffer.setShort(1, 65535);
                assertThrows(DecoderException.class, () -> PacketArcaneRecipeTransfer.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
    @Test void untrustedIdsAndConstructorBoundsCannotSelectArbitrarySlotsOrCounts() {
        assertThrows(IllegalArgumentException.class, () -> new PacketArcaneRecipeTransfer(-1, 0, 1, request().recipeId(), false));
        assertThrows(IllegalArgumentException.class, () -> new PacketArcaneRecipeTransfer(256, 0, 1, request().recipeId(), false));
        assertThrows(IllegalArgumentException.class, () -> new PacketArcaneRecipeTransfer(0, 32768, 1, request().recipeId(), false));
        assertThrows(IllegalArgumentException.class, () -> new PacketArcaneRecipeTransfer(0, 0, 0, request().recipeId(), false));
        assertThrows(IllegalArgumentException.class, () -> new PacketArcaneRecipeTransfer(0, 0, 1, ResourceLocation.fromNamespaceAndPath("thaumcraft", "a".repeat(256)), false));
        var buffer = Unpooled.buffer();
        try {
            PacketArcaneRecipeTransfer.STREAM_CODEC.encode(buffer, request()); buffer.setByte(10, '!');
            assertThrows(DecoderException.class, () -> PacketArcaneRecipeTransfer.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
