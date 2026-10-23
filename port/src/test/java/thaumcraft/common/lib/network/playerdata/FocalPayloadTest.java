package thaumcraft.common.lib.network.playerdata;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class FocalPayloadTest {
    @Test void minimalStateRoundTripsAndRejectsTrailingBytes() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            var packet = new PacketFocalState(1,BlockPos.ZERO,1,0,new CompoundTag(),false);
            PacketFocalState.STREAM_CODEC.encode(buffer,packet);
            assertEquals(31,buffer.readableBytes());
            assertEquals(packet,PacketFocalState.STREAM_CODEC.decode(buffer));
            buffer.clear(); PacketFocalState.STREAM_CODEC.encode(buffer,packet); buffer.writeByte(1);
            assertThrows(RuntimeException.class,() -> PacketFocalState.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void draftIsDefensivelyCopiedBothDirections() {
        CompoundTag body = new CompoundTag(); body.putString("key","ROOT"); var p = new PacketFocusEdit(1,BlockPos.ZERO,1,0,1,false,"Focus",body);
        body.putString("key","FIRE"); p.graph().putString("key","AIR"); assertEquals("ROOT",p.graph().getString("key"));
    }
    @Test void invalidSessionSequenceRevisionNameRejected() {
        CompoundTag body = new CompoundTag();
        assertThrows(IllegalArgumentException.class,() -> new PacketFocusEdit(1,BlockPos.ZERO,0,0,1,true,"",body));
        assertThrows(IllegalArgumentException.class,() -> new PacketFocusEdit(1,BlockPos.ZERO,1,-1,1,true,"",body));
        assertThrows(IllegalArgumentException.class,() -> new PacketFocusEdit(1,BlockPos.ZERO,1,0,0,true,"",body));
        assertThrows(IllegalArgumentException.class,() -> new PacketFocusEdit(1,BlockPos.ZERO,1,0,1,true,"a".repeat(51),body));
        assertThrows(IllegalArgumentException.class,() -> new PacketFocusEdit(1,BlockPos.ZERO,1,0,1,true,"a\nb",body));
    }
    @Test void stateIsNotMutableAndReportsRejectedRequest() {
        CompoundTag view = new CompoundTag(); view.putFloat("vis",20); var p = new PacketFocalState(1,BlockPos.ZERO,1,0,view,true);
        view.putFloat("vis",0); p.view().putFloat("vis",0); assertEquals(20,p.view().getFloat("vis")); assertTrue(p.rejected());
    }
    @Test void signedLegacyNodeLimitIsBounded() {
        CompoundTag body = new CompoundTag(); ListTag nodes = new ListTag(); for (int i = 0; i < 128; i++) nodes.add(new CompoundTag()); body.put("nodes",nodes);
        assertThrows(IllegalArgumentException.class,() -> new PacketFocusEdit(1,BlockPos.ZERO,1,0,1,true,"",body));
    }
}
