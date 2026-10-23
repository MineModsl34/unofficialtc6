package thaumcraft.common.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.lib.SoundsTC;

/** Exact falling-taint simulation, explicit block state sync and original unsigned-byte Time save quirk. */
public final class EntityFallingTaint extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK=SynchedEntityData.defineId(EntityFallingTaint.class,EntityDataSerializers.BLOCK_STATE);
    private BlockPos oldPos=BlockPos.ZERO;
    public int fallTime;
    public EntityFallingTaint(EntityType<? extends EntityFallingTaint> type,Level level){super(type,level);blocksBuilding=true;}
    public EntityFallingTaint(Level level,double x,double y,double z,BlockState block,BlockPos old) {
        this(Mob8MiscRegistries.FALLING_TAINT,level);setPos(x,y,z);entityData.set(BLOCK,block);oldPos=old.immutable();setDeltaMovement(0,0,0);
    }
    public BlockState getBlock(){return entityData.get(BLOCK);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(BLOCK,Blocks.AIR.defaultBlockState());}
    @Override public boolean isPickable(){return !isRemoved();}
    @Override protected MovementEmission getMovementEmission(){return MovementEmission.NONE;}
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override public void tick() {
        super.tick();BlockState block=getBlock();if(block.isAir()){discard();return;}
        xo=getX();yo=getY();zo=getZ();fallTime++;setDeltaMovement(getDeltaMovement().add(0,-.04F,0));move(MoverType.SELF,getDeltaMovement());setDeltaMovement(getDeltaMovement().scale(.98F));
        BlockPos pos=blockPosition();
        if(!level().isClientSide) {
            if(fallTime==1) {if(!level().hasChunkAt(oldPos)||level().getBlockState(oldPos)!=block){discard();return;}level().removeBlock(oldPos,false);}
            if(!level().hasChunkAt(pos)){discard();return;}
            // Source compares IBlockState to Block here: that flux-goo shortcut never succeeds.
            if(!onGround()) {
                if(fallTime>100&&(pos.getY()<level().getMinBuildHeight()||pos.getY()>=level().getMaxBuildHeight())||fallTime>600)discard();
            } else {
                setDeltaMovement(getDeltaMovement().multiply(.7,-.5,.7));var at=level().getBlockState(pos);
                if(!at.is(Blocks.PISTON)&&!at.is(Blocks.MOVING_PISTON)&&!at.is(Blocks.PISTON_HEAD)) {
                    playSound(SoundsTC.gore,.5F,((random.nextFloat()-random.nextFloat())*.2F+1)*.8F);discard();
                    boolean place=at.is(BlocksTC.taintFibre)||at.is(BlocksTC.fluxGoo)||at.canBeReplaced();
                    if(place&&!thaumcraft.common.blocks.world.taint.BlockTaint.canFallBelow(level(),pos.below())&&block.canSurvive(level(),pos))level().setBlockAndUpdate(pos,block);
                }
            }
        } else if(onGround()||fallTime==1)thaumcraft.common.lib.fx.SourceFX.local(level(),"taint_land",this.position(),net.minecraft.world.phys.Vec3.ZERO,this.getId(),0);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.put("BlockState",NbtUtils.writeBlockState(getBlock()));tag.putByte("Time",(byte)fallTime);tag.putLong("Old",oldPos.asLong());tag.putFloat("FallHurtAmount",2);tag.putInt("FallHurtMax",40);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){entityData.set(BLOCK,NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK),tag.getCompound("BlockState")));fallTime=tag.getByte("Time")&255;oldPos=BlockPos.of(tag.getLong("Old"));}
}
