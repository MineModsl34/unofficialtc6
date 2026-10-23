package thaumcraft.common.tiles.misc;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.items.casters.CasterRegistries;
import thaumcraft.common.items.casters.FocusBlockActions;
/** Source 3x3 aperture plus one depth segment per tick, restoring each remembered state independently. */
public final class TileHole extends BlockEntity {
    public BlockState oldblock=Blocks.AIR.defaultBlockState();public short countdown,countdownmax=120;public byte count;public Direction direction;
    public TileHole(BlockPos pos,BlockState state){super(CasterRegistries.HOLE,pos,state);}
    public static boolean createHole(Level level,BlockPos pos,Direction direction,byte count,int duration){if(level.isClientSide||!level.hasChunkAt(pos))return false;var state=level.getBlockState(pos);if(level.getBlockEntity(pos)!=null||FocusBlockActions.holeBlacklisted(state)||state.is(Blocks.BEDROCK)||state.is(BlocksTC.hole)||!(state.isAir()||!state.canBeReplaced())||state.getDestroySpeed(level,pos)==-1)return false;if(!level.setBlock(pos,BlocksTC.hole.defaultBlockState(),3))return false;if(level.getBlockEntity(pos) instanceof TileHole hole){hole.oldblock=state;hole.count=count;hole.direction=direction;hole.countdownmax=(short)duration;hole.setChanged();level.sendBlockUpdated(pos,hole.getBlockState(),hole.getBlockState(),3);return true;}level.setBlock(pos,state,3);return false;}
    public static void tick(Level level,BlockPos pos,BlockState state,TileHole hole){if(level.isClientSide){for(int i=0;i<2;i++)hole.sparkles();return;}if(hole.countdown==0&&hole.count>1&&hole.direction!=null){for(int a=0;a<9;a++)if(a/3!=1||a%3!=1){int x=-1+a/3,y=-1+a%3;var side=switch(hole.direction.getAxis()){case Y->pos.offset(x,0,y);case Z->pos.offset(x,y,0);case X->pos.offset(0,x,y);};createHole(level,side,null,(byte)1,hole.countdownmax);}if(!createHole(level,pos.relative(hole.direction.getOpposite()),hole.direction,(byte)(hole.count-1),hole.countdownmax))hole.count=0;}hole.countdown++;if(hole.countdown%20==0)hole.setChanged();if(hole.countdown>=hole.countdownmax)level.setBlock(pos,hole.oldblock,3);}
    private void sparkles(){for(Direction d1:Direction.values()){var next=worldPosition.relative(d1);var b1=level.getBlockState(next);if(b1.is(BlocksTC.hole)||b1.isSolidRender(level,next))continue;for(Direction d2:Direction.values())if(d1.getAxis()!=d2.getAxis()&&(level.getBlockState(worldPosition.relative(d2)).isSolidRender(level,worldPosition.relative(d2))||level.getBlockState(next.relative(d2)).isSolidRender(level,next.relative(d2)))){double sx=.5*d1.getStepX(),sy=.5*d1.getStepY(),sz=.5*d1.getStepZ();if(sx==0)sx=.5*d2.getStepX();if(sy==0)sy=.5*d2.getStepY();if(sz==0)sz=.5*d2.getStepZ();sx=sx==0?level.random.nextFloat():sx+.5;sy=sy==0?level.random.nextFloat():sy+.5;sz=sz==0?level.random.nextFloat():sz+.5;thaumcraft.common.lib.fx.SourceFX.local(level,"sparkle_hole",new net.minecraft.world.phys.Vec3(worldPosition.getX()+sx,worldPosition.getY()+sy,worldPosition.getZ()+sz),net.minecraft.world.phys.Vec3.ZERO,0,0);}}}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.put("remembered_state",NbtUtils.writeBlockState(oldblock));tag.putShort("countdown",countdown);tag.putShort("countdownmax",countdownmax);tag.putByte("count",count);tag.putByte("direction",direction==null?-1:(byte)direction.ordinal());}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);oldblock=NbtUtils.readBlockState(lookup.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag.getCompound("remembered_state"));countdown=tag.getShort("countdown");countdownmax=tag.contains("countdownmax")?tag.getShort("countdownmax"):120;count=tag.getByte("count");int d=tag.getByte("direction");direction=d>=0&&d<6?Direction.from3DDataValue(d):null;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){return saveWithoutMetadata(lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
