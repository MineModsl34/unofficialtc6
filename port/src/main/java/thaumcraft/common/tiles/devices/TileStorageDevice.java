package thaumcraft.common.tiles.devices;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import thaumcraft.common.blocks.devices.BlockWorldDevice;

/** Hungry chest deliberately never merges; brain jar conserves native merged-orb Count as well as Value. */
public final class TileStorageDevice extends DeviceTile {
    public int xp, eatDelay, users;
    public float lidAngle, prevLidAngle, rota, rotb;
    private long soundDelay = System.currentTimeMillis() + 1500;
    private float targetRotation;
    public TileStorageDevice(BlockPos pos,BlockState state) { super(pos,state,((BlockWorldDevice)state.getBlock()).kind == BlockWorldDevice.Kind.CHEST ? 27 : 0); }
    @Override public void update() {
        if (level == null) return;
        if (kind() == BlockWorldDevice.Kind.CHEST) { chest(); return; }
        if (xp > 2000) xp = 2000;
        ExperienceOrb orb = xp < 2000 ? level.getEntitiesOfClass(ExperienceOrb.class,new AABB(worldPosition).inflate(8)).stream().min(java.util.Comparator.comparingDouble(other -> other.distanceToSqr(worldPosition.getCenter()))).orElse(null) : null;
        if (xp < 2000 && eatDelay == 0) {
            if (orb != null) { Vec3 delta = worldPosition.getCenter().subtract(orb.position()).scale(1 / 25.0); double distance = delta.length(), factor = 1 - distance; factor *= factor; if (distance > 1.0E-8 && factor > 0) orb.setDeltaMovement(orb.getDeltaMovement().add(delta.x / distance * factor * .3,delta.y / distance * factor * .5,delta.z / distance * factor * .3)); }
        }
        if (level.isClientSide) { brainClient(orb); if (eatDelay > 0) eatDelay--; return; }
        if (eatDelay > 0) { eatDelay--; return; }
        if (xp < 2000) for (ExperienceOrb eaten : level.getEntitiesOfClass(ExperienceOrb.class,new AABB(worldPosition).inflate(.1))) {
            CompoundTag nativeOrb = new CompoundTag(); eaten.saveWithoutId(nativeOrb);
            xp += eaten.getValue() * Math.max(1,nativeOrb.getInt("Count")); eaten.discard(); level.playSound(null,worldPosition,SoundEvents.GENERIC_EAT,SoundSource.BLOCKS,.1F,1 + (level.random.nextFloat() - level.random.nextFloat()) * .2F); changedAndSync();
        }
    }
    private void brainClient(ExperienceOrb orb) {
        rotb = rota; Player player = level.getNearestPlayer(worldPosition.getX() + .5,worldPosition.getY() + .5,worldPosition.getZ() + .5,6,false);
        net.minecraft.world.entity.Entity target = orb == null ? player : orb;
        if (target != null) targetRotation = (float)Math.atan2(target.getZ() - worldPosition.getZ() - .5,target.getX() - worldPosition.getX() - .5); else targetRotation += .01F;
        while (rota >= Math.PI) rota -= (float)Math.PI * 2; while (rota < -Math.PI) rota += (float)Math.PI * 2; while (targetRotation >= Math.PI) targetRotation -= (float)Math.PI * 2; while (targetRotation < -Math.PI) targetRotation += (float)Math.PI * 2;
        float delta = targetRotation - rota; while (delta >= Math.PI) delta -= (float)Math.PI * 2; while (delta < -Math.PI) delta += (float)Math.PI * 2; rota += delta * .04F;
        long now = System.currentTimeMillis(); if (orb == null && player != null && now >= soundDelay) { level.playLocalSound(worldPosition.getX() + .5,worldPosition.getY() + .5,worldPosition.getZ() + .5,thaumcraft.common.lib.SoundsTC.brain,SoundSource.AMBIENT,.15F,.8F + level.random.nextFloat() * .4F,false); soundDelay = now + 5000 + level.random.nextInt(25000); }
    }
    private void chest() {
        prevLidAngle = lidAngle;
        if (!level.isClientSide && ++counter % 200 == 0) { users = 0; for (Player player : level.getEntitiesOfClass(Player.class,new AABB(worldPosition).inflate(5))) if (player.containerMenu instanceof ChestMenu menu && menu.getContainer() == this) users++; level.blockEvent(worldPosition,getBlockState().getBlock(),1,users); }
        if (users > 0 && lidAngle == 0) level.playSound(null,worldPosition,SoundEvents.CHEST_OPEN,SoundSource.BLOCKS,.5F,level.random.nextFloat() * .1F + .9F);
        lidAngle = net.minecraft.util.Mth.clamp(lidAngle + (users > 0 ? .1F : -.1F),0,1);
        if (lidAngle < .5F && prevLidAngle >= .5F) level.playSound(null,worldPosition,SoundEvents.CHEST_CLOSE,SoundSource.BLOCKS,.5F,level.random.nextFloat() * .1F + .9F);
    }
    public void swallow(ItemEntity entity) {
        if (level.isClientSide || !entity.isAlive()) return;
        ItemStack before = entity.getItem(), leftover = ItemHandlerHelper.insertItemStacked(new InvWrapper(this),before,false);
        if (leftover.getCount() != before.getCount()) { level.playSound(null,worldPosition,SoundEvents.GENERIC_EAT,SoundSource.BLOCKS,.25F,1 + (level.random.nextFloat() - level.random.nextFloat()) * .2F); if (leftover.isEmpty()) entity.discard(); else entity.setItem(leftover); }
        setChanged();
    }
    @Override protected void inventoryChanged() { if (kind() == BlockWorldDevice.Kind.CHEST) setChanged(); else super.inventoryChanged(); }
    public void releaseXp() { eatDelay = 40; if (!(level instanceof ServerLevel server)) return; int amount = level.random.nextInt(Math.min(xp + 1,64)); if (amount > 0) { xp -= amount; ExperienceOrb.award(server,worldPosition.getCenter(),amount); changedAndSync(); } }
    @Override public void startOpen(Player player) { if (!player.isSpectator() && kind() == BlockWorldDevice.Kind.CHEST) { users++; level.blockEvent(worldPosition,getBlockState().getBlock(),1,users); level.updateNeighborsAt(worldPosition,getBlockState().getBlock()); } }
    @Override public void stopOpen(Player player) { if (!player.isSpectator() && kind() == BlockWorldDevice.Kind.CHEST) { users--; level.blockEvent(worldPosition,getBlockState().getBlock(),1,users); level.updateNeighborsAt(worldPosition,getBlockState().getBlock()); level.updateNeighborsAt(worldPosition.below(),getBlockState().getBlock()); } }
    @Override public boolean triggerEvent(int id,int param) { if (kind() == BlockWorldDevice.Kind.CHEST && id == 1) { users = param; return true; } return super.triggerEvent(id,param); }
    @Override protected void writeDevice(CompoundTag tag,HolderLookup.Provider registries) { tag.putInt("XP",xp); }
    @Override protected void readDevice(CompoundTag tag,HolderLookup.Provider registries) { xp = Math.max(0,tag.getInt("XP")); }
}
