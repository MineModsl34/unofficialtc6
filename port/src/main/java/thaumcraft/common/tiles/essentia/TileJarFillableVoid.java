package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;

public class TileJarFillableVoid extends TileJarFillable {
    public TileJarFillableVoid(BlockPos pos, BlockState state) { super(EssentiaRegistries.JAR_VOID,pos,state); }
    @Override public int addToContainer(Aspect tag, int quantity) {
        if (quantity == 0) return 0;
        int original = amount;
        if (tag == aspect || amount == 0) {
            aspect = tag; amount += quantity; quantity = 0;
            if (amount > CAPACITY) { if (level != null && !level.isClientSide && level.random.nextInt(CAPACITY) == 0) AuraHelper.polluteAura(level,worldPosition,1,true); amount = CAPACITY; }
        }
        if (original < CAPACITY) changedAndSync(); return quantity;
    }
    @Override public int getMinimumSuction() { return aspectFilter != null ? 48 : 32; }
    @Override public int getSuctionAmount(Direction side) { return aspectFilter != null && amount < CAPACITY ? 48 : 32; }
    @Override public void update() { if (level != null && !level.isClientSide && ++count % 5 == 0) fillJar(); }
}
