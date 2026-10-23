package thaumcraft.common.lib.network.misc;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** No Minecraft bootstrap/level: packet shape and defensive authority boundaries only. */
class ThaumatoriumPayloadTest {
    @Test void selectionRequiresMenuSessionRevisionAndMonotonicSequence() {
        ResourceLocation recipe = ResourceLocation.parse("thaumcraft:nitor");
        assertThrows(IllegalArgumentException.class,() -> new PacketSelectThaumotoriumRecipeToServer(1,BlockPos.ZERO,0,0,1,recipe));
        assertThrows(IllegalArgumentException.class,() -> new PacketSelectThaumotoriumRecipeToServer(1,BlockPos.ZERO,1,-1,1,recipe));
        assertThrows(IllegalArgumentException.class,() -> new PacketSelectThaumotoriumRecipeToServer(1,BlockPos.ZERO,1,0,0,recipe));
        assertEquals(recipe,new PacketSelectThaumotoriumRecipeToServer(1,BlockPos.ZERO,1,0,1,recipe).recipe());
    }
    @Test void displayStateDoesNotExposeMutableServerNbt() {
        CompoundTag supplied = new CompoundTag(); supplied.putInt("maxrec",1);
        PacketThaumatoriumState state = new PacketThaumatoriumState(1,BlockPos.ZERO,1,0,supplied);
        supplied.putInt("maxrec",9); CompoundTag view = state.view(); view.putInt("maxrec",17);
        assertEquals(1,state.view().getInt("maxrec"));
        assertThrows(IllegalArgumentException.class,() -> new PacketThaumatoriumState(1,BlockPos.ZERO,0,0,supplied));
    }
}
