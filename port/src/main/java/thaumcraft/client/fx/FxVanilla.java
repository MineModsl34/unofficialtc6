package thaumcraft.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** TC6 dispatcher effects that genuinely used Minecraft item/block fragments; not a replacement for custom TC particles. */
final class FxVanilla {
    enum Kind { ITEM_CRACK, BLOCK_CRACK }
    static void spawn(ClientLevel level,Kind kind,double x,double y,double z,double vx,double vy,double vz,int[] sourceIds) {
        if(level==null||sourceIds.length==0)return;
        if(kind==Kind.ITEM_CRACK)level.addParticle(new ItemParticleOption(ParticleTypes.ITEM,new ItemStack(Item.byId(sourceIds[0]))),x,y,z,vx,vy,vz);
        else level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,Block.stateById(sourceIds[0])),x,y,z,vx,vy,vz);
    }
}
