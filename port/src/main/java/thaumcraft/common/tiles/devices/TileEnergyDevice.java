package thaumcraft.common.tiles.devices;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.RechargeHelper;

/** TileVisGenerator, TileRechargePedestal and TileLevitator source algorithms (no invented rates). */
public final class TileEnergyDevice extends DeviceTile implements IEnergyStorage,thaumcraft.api.aspects.IAspectContainer {
    public static final int[] RANGES = {4,8,16,32};
    public int energy, vis, range = 1, rangeActual;
    public TileEnergyDevice(BlockPos pos,BlockState state) { super(pos,state,((thaumcraft.common.blocks.devices.BlockWorldDevice)state.getBlock()).kind == thaumcraft.common.blocks.devices.BlockWorldDevice.Kind.RECHARGE ? 1 : 0); }
    @Override public void update() {
        if (level == null) return;
        switch (kind()) {
            case GENERATOR -> generator();
            case RECHARGE -> { if (!level.isClientSide && counter++ % 10 == 0 && RechargeHelper.rechargeItem(level,getItem(0),worldPosition,null,5) > 0) { changedAndSync(); var primals = thaumcraft.api.aspects.Aspect.getPrimalAspects(); level.blockEvent(worldPosition,getBlockState().getBlock(),5,primals.get(level.random.nextInt(primals.size())).getColor()); } }
            case LEVITATOR -> levitator();
            default -> {}
        }
    }
    private void generator() {
        if (level.isClientSide || !enabled()) return;
        if (energy == 0) { energy = (int)(AuraHelper.drainVis(level,worldPosition,1,false) * 1000); changedAndSync(); }
        var target = level.getCapability(Capabilities.EnergyStorage.BLOCK,worldPosition.relative(facing()),facing().getOpposite());
        if (target != null && target.canReceive()) { int accepted = Math.max(0,Math.min(Math.min(energy,20),target.receiveEnergy(Math.min(energy,20),false))); energy -= accepted; if (accepted > 0) { setChanged(); if (energy == 0) changedAndSync(); } }
    }
    private void levitator() {
        Direction face = facing(); int size = RANGES[range];
        if (rangeActual > size) rangeActual = 0;
        int sample = 1 + Math.floorMod(counter,size);
        BlockPos check = worldPosition.relative(face,sample);
        if (level.getBlockState(check).isSolidRender(level,check)) { if (sample < rangeActual) rangeActual = sample; counter = -1; }
        else if (sample > rangeActual) rangeActual = sample;
        counter++;
        if (!level.isClientSide && vis < 10) { vis = (int)(vis + AuraHelper.drainVis(level,worldPosition,1,false) * 1200); changedAndSync(); }
        if (rangeActual <= 0 || vis <= 0 || !enabled()) return;
        AABB box = new AABB(worldPosition).expandTowards(face.getStepX() * rangeActual,face.getStepY() * rangeActual,face.getStepZ() * rangeActual);
        boolean lifted = false;
        for (Entity entity : level.getEntities((Entity)null,box)) {
            if (!(entity instanceof ItemEntity) && !entity.isPushable() && !(entity instanceof AbstractHorse)) continue;
            Vec3 motion = entity.getDeltaMovement();
            if (!entity.isShiftKeyDown() || face != Direction.UP) {
                motion = motion.add(face.getStepX() * .1F,face.getStepY() * .1F,face.getStepZ() * .1F);
                if (face.getAxis().isHorizontal() && !entity.onGround()) motion = new Vec3(motion.x,(motion.y < 0 ? motion.y * .9F : motion.y) + .08F,motion.z);
                motion = new Vec3(net.minecraft.util.Mth.clamp(motion.x,-.35F,.35F),net.minecraft.util.Mth.clamp(motion.y,-.35F,.35F),net.minecraft.util.Mth.clamp(motion.z,-.35F,.35F));
            } else if (motion.y < 0) motion = new Vec3(motion.x,motion.y * .9F,motion.z);
            entity.setDeltaMovement(motion); entity.fallDistance = 0; vis -= size * 2; lifted = true;
            if(level.isClientSide&&level.random.nextFloat()<.1F)thaumcraft.common.lib.fx.SourceFX.local(level,"levitator",new Vec3(
                (float)(entity.getX()+(level.random.nextFloat()-level.random.nextFloat())*entity.getBbWidth()),(float)(entity.getY()+level.random.nextFloat()*entity.getBbHeight()),(float)(entity.getZ()+(level.random.nextFloat()-level.random.nextFloat())*entity.getBbWidth())),
                new Vec3((level.random.nextFloat()-level.random.nextFloat())*.01,(level.random.nextFloat()-level.random.nextFloat())*.01,(level.random.nextFloat()-level.random.nextFloat())*.01),0,0);
            if (vis <= 0) break;
        }
        if (!level.isClientSide && lifted && counter % 20 == 0) setChanged();
        if(level.isClientSide&&level.random.nextFloat()<.1F)thaumcraft.common.lib.fx.SourceFX.local(level,"levitator",new Vec3(worldPosition.getX()+.25F+level.random.nextFloat()*.5F,worldPosition.getY()+.25F+level.random.nextFloat()*.5F,worldPosition.getZ()+.25F+level.random.nextFloat()*.5F),new Vec3(face.getStepX()/50D,face.getStepY()/50D,face.getStepZ()/50D),0,0);
    }
    public void increaseRange(Player player) { if (!level.isClientSide) { range = (range + 1) % 4; changedAndSync(); player.displayClientMessage(Component.translatable("tc.levitator",RANGES[range],RANGES[range] * 2),true); } rangeActual = 0; }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return slot == 0 && stack.getItem() instanceof IRechargable; }
    @Override public int receiveEnergy(int amount,boolean simulate) { return 0; }
    /** Source intentionally pushes energy; a neighbor cannot pull via extractEnergy. */
    @Override public int extractEnergy(int amount,boolean simulate) { return 0; }
    @Override public int getEnergyStored() { return energy; }
    @Override public int getMaxEnergyStored() { return 1000; }
    @Override public boolean canExtract() { return true; }
    @Override public boolean canReceive() { return false; }
    @Override public thaumcraft.api.aspects.AspectList getAspects() { return getItem(0).getItem() instanceof IRechargable ? new thaumcraft.api.aspects.AspectList().add(thaumcraft.api.aspects.Aspect.ENERGY,RechargeHelper.getCharge(getItem(0))) : null; }
    @Override public void setAspects(thaumcraft.api.aspects.AspectList aspects) {}
    @Override public int addToContainer(thaumcraft.api.aspects.Aspect aspect,int amount) { return 0; }
    @Override public boolean takeFromContainer(thaumcraft.api.aspects.Aspect aspect,int amount) { return false; }
    @Override public boolean takeFromContainer(thaumcraft.api.aspects.AspectList aspects) { return false; }
    @Override public boolean doesContainerContainAmount(thaumcraft.api.aspects.Aspect aspect,int amount) { return false; }
    @Override public boolean doesContainerContain(thaumcraft.api.aspects.AspectList aspects) { return false; }
    @Override public int containerContains(thaumcraft.api.aspects.Aspect aspect) { return 0; }
    @Override public boolean doesContainerAccept(thaumcraft.api.aspects.Aspect aspect) { return true; }
    @Override protected void writeDevice(CompoundTag tag,HolderLookup.Provider registries) { tag.putInt("energy",energy); tag.putInt("vis",vis); tag.putByte("range",(byte)range); }
    @Override protected void readDevice(CompoundTag tag,HolderLookup.Provider registries) { energy = Math.max(0,Math.min(1000,tag.getInt("energy"))); vis = tag.getInt("vis"); range = tag.contains("range") ? Math.floorMod(tag.getByte("range"),4) : 1; }
    @Override public boolean triggerEvent(int id,int param){if(id==5){if(level!=null&&level.isClientSide)thaumcraft.common.lib.fx.SourceFX.local(level,"recharge",Vec3.atLowerCornerOf(worldPosition),Vec3.ZERO,0,param);return true;}return super.triggerEvent(id,param);}
}
