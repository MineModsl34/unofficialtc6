package thaumcraft.common.tiles.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.blocks.basic.BannerRegistries;

/** Source facing/aspect/wall state, used by the genuine greater cult portal. */
public final class TileBanner extends BlockEntity {
    private byte facing;private Aspect aspect;private boolean wall;
    public TileBanner(BlockPos pos,BlockState state){super(BannerRegistries.BANNER,pos,state);}
    public byte getBannerFacing(){return facing;}
    public void setBannerFacing(byte value){facing=(byte)(value&15);sync();}
    public boolean getWall(){return wall;}
    public void setWall(boolean value){wall=value;sync();}
    public Aspect getAspect(){return aspect;}
    public int getColor(){return getBlockState().getBlock() instanceof thaumcraft.common.blocks.basic.BlockBannerTC banner&&banner.dye!=null?banner.dye.getTextureDiffuseColor():-1;}
    public void setAspect(Aspect value){aspect=value;sync();}
    private void sync(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putByte("facing",facing);tag.putString("aspect",aspect==null?"":aspect.getTag());tag.putBoolean("wall",wall);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);facing=(byte)(tag.getByte("facing")&15);aspect=Aspect.getAspect(tag.getString("aspect"));wall=tag.getBoolean("wall");}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){return saveWithoutMetadata(lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
