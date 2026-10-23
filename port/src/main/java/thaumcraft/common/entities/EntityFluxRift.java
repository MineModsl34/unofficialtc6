package thaumcraft.common.entities;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.capabilities.*;
import thaumcraft.api.casters.*;
import thaumcraft.api.entities.IFluxRift;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeedPrime;
import thaumcraft.common.items.casters.foci.FocusEffectFlux;
import thaumcraft.common.items.casters.foci.FocusMediumCloud;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.utils.EntityUtils;
import thaumcraft.common.lib.warp.WarpGameplay;
import thaumcraft.common.tiles.crafting.Phase10RiftRegistries;

/** Actual stationary source world rift, not the eldritch projectile with a similar name. */
public final class EntityFluxRift extends Entity implements IFluxRift {
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(EntityFluxRift.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(EntityFluxRift.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> STABILITY = SynchedEntityData.defineId(EntityFluxRift.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> COLLAPSE = SynchedEntityData.defineId(EntityFluxRift.class,EntityDataSerializers.BOOLEAN);
    public final ArrayList<Vec3> points = new ArrayList<>();
    public final ArrayList<Float> pointsWidth = new ArrayList<>();
    private int maxSize, lastSize = -1, lastSeed;
    private float shapeWidth=2,shapeHeight=2;
    public EntityFluxRift(EntityType<? extends EntityFluxRift> type,Level level) { super(type,level); setNoGravity(true); rebuild(); }
    public EntityFluxRift(Level level) { this(Phase10RiftRegistries.FLUX_RIFT,level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(SEED,0); builder.define(SIZE,5); builder.define(STABILITY,0F); builder.define(COLLAPSE,false); }
    public int getRiftSeed() { return entityData.get(SEED); }
    public void setRiftSeed(int seed) { entityData.set(SEED,seed); rebuild(); }
    public int getRiftSize() { return entityData.get(SIZE); }
    public void setRiftSize(int size) { entityData.set(SIZE,FluxRiftRules.boundedSize(size)); rebuild(); }
    public float getRiftStability() { return entityData.get(STABILITY); }
    public void setRiftStability(float value) { entityData.set(STABILITY,FluxRiftRules.stability(value)); }
    public void addStability() { setRiftStability(getRiftStability() + .125F); }
    public boolean getCollapse() { return entityData.get(COLLAPSE); }
    @Override public void setCollapse(boolean collapse) { if (collapse && !getCollapse()) maxSize = getRiftSize(); entityData.set(COLLAPSE,collapse); }
    public EnumStability getStability() { return EnumStability.values()[FluxRiftRules.category(getRiftStability())]; }
    private void rebuild() {
        if (points == null || entityData == null) return;
        var shape = FluxRiftGeometry.create(getRiftSeed(),getRiftSize()); points.clear(); pointsWidth.clear();
        for (var point : shape.points()) points.add(new Vec3(point.x(),point.y(),point.z())); pointsWidth.addAll(shape.widths());
        lastSize = getRiftSize(); lastSeed = getRiftSeed(); updateBounds();
    }
    private void updateBounds() {
        if (points == null || points.isEmpty()) return;
        double x0 = Double.POSITIVE_INFINITY,y0 = x0,z0 = x0,x1 = Double.NEGATIVE_INFINITY,y1 = x1,z1 = x1;
        for (var p : points) { x0=Math.min(x0,p.x);y0=Math.min(y0,p.y);z0=Math.min(z0,p.z);x1=Math.max(x1,p.x);y1=Math.max(y1,p.y);z1=Math.max(z1,p.z); }
        shapeWidth=(float)Math.max(x1-x0,z1-z0);shapeHeight=(float)(y1-y0);fixupDimensions();
        setBoundingBox(new AABB(getX()+x0,getY()+y0,getZ()+z0,getX()+x1,getY()+y1,getZ()+z1));
    }
    @Override public EntityDimensions getDimensions(Pose pose){return points==null?super.getDimensions(pose):EntityDimensions.scalable(shapeWidth,shapeHeight);}
    @Override public void setPos(double x,double y,double z) { super.setPos(x,y,z); updateBounds(); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (key == SIZE || key == SEED) rebuild(); }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(getRiftSize()/100F + .2); }
    @Override public void move(MoverType type,Vec3 delta) {} // Source rifts cannot move.
    @Override public boolean isOnFire() { return false; }
    @Override public boolean displayFireAnimation() { return false; }
    @Override public void tick() {
        super.tick(); clearFire(); if (lastSize != getRiftSize() || lastSeed != getRiftSeed()) rebuild();
        if (!(level() instanceof ServerLevel server)) { RiftPresentationHooks.ambient.accept(this); return; }
        if (getRiftSeed() == 0) setRiftSeed(random.nextInt());
        if (points.size() > 1) {
            int index = random.nextInt(points.size()-1); Vec3 start = points.get(index).add(position()),end=points.get(index+1).add(position());
            var hit = server.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if (hit.getType() == HitResult.Type.BLOCK && server.hasChunkAt(hit.getBlockPos())) {
                var pos = hit.getBlockPos(); var state = server.getBlockState(pos);
                if (!state.isAir() && state.getDestroySpeed(server,pos) >= 0 && !state.getCollisionShape(server,pos).isEmpty()) { server.levelEvent(2001,pos,Block.getId(state)); server.removeBlock(pos,false); }
            }
            for (var entity : EntityUtils.getEntitiesInRange(server,start.x,start.y,start.z,this,Entity.class,.5)) {
                if (entity.isAlive() && !(entity instanceof Player player && player.isCreative())) { entity.hurt(damageSources().fellOutOfWorld(),2); if (entity instanceof ItemEntity) entity.discard(); }
            }
        }
        if (points.size() < 3 && !getCollapse()) setCollapse(true);
        if (getCollapse()) {
            setRiftSize(getRiftSize()-1); if (random.nextBoolean()) AuraHelper.addVis(server,blockPosition(),1); else AuraHelper.polluteAura(server,blockPosition(),1,false);
            if (random.nextInt(10)==0) server.explode(this,getX()+random.nextGaussian()*2,getY()+random.nextGaussian()*2,getZ()+random.nextGaussian()*2,random.nextFloat()/2,Level.ExplosionInteraction.NONE);
            if (getRiftSize() <= 1) { completeCollapse(server); return; }
        }
        if (tickCount % 120 == 0) setRiftStability(getRiftStability()-.2F);
        if (tickCount % 600 == getId() % 600) {
            double cost = FluxRiftRules.growthCost(getRiftSize());
            if (AuraHelper.getFlux(server,blockPosition()) >= cost && getRiftSize() < 100 && getStability()!=EnumStability.VERY_STABLE) { AuraHelper.drainFlux(server,blockPosition(),(float)cost,false); setRiftSize(getRiftSize()+1); }
            if (getRiftStability()<0 && random.nextInt(1000)<Math.abs(getRiftStability())+getRiftSize()) executeRiftEvent(server);
        }
        if (isAlive() && tickCount%300==0) playSound(SoundsTC.evilportal,(float)(.15F+random.nextGaussian()*.066),(float)(.75+random.nextGaussian()*.1));
    }
    public static void createRift(ServerLevel world,BlockPos origin) {
        BlockPos pos = origin.offset(world.random.nextInt(16),0,world.random.nextInt(16)); if (!world.hasChunkAt(pos)) return;
        BlockPos surface = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,pos);
        if (!world.dimensionType().hasSkyLight()) {
            surface = new BlockPos(pos.getX(),Math.max(10,world.getMinBuildHeight()),pos.getZ());
            while (!world.isEmptyBlock(surface)) { surface=surface.above(world.random.nextInt(5)+1); if (surface.getY()>world.getMaxBuildHeight()-5) return; }
        }
        if (surface.getY() >= world.getMaxBuildHeight()-4 || !EntityUtils.getEntitiesInRange(world,surface,null,EntityFluxRift.class,32).isEmpty()) return;
        var rift = new EntityFluxRift(world); rift.setRiftSeed(world.random.nextInt()); rift.moveTo(surface.getX()+.5,surface.getY()+.5,surface.getZ()+.5,world.random.nextInt(360),0);
        double size = Math.sqrt(AuraHelper.getFlux(world,surface)*3F);
        if (size <= 5 || !world.addFreshEntity(rift)) return;
        rift.setRiftSize((int)size); AuraHelper.drainFlux(world,surface,(float)size,false);
        for (var player : world.getEntitiesOfClass(Player.class,new AABB(surface).inflate(32))) {
            if (!ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("f_toomuchflux")) { player.displayClientMessage(Component.translatable("tc.fluxevent.3").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE,net.minecraft.ChatFormatting.ITALIC),true); ResearchManager.completeResearch(player,"f_toomuchflux"); }
        }
    }
    private void executeRiftEvent(ServerLevel server) {
        int event = FluxRiftRules.event(Math.random()); if (event==1 && TaintHelper.isNearTaintSeed(server,blockPosition())) return;
        boolean occurred = false;
        switch (event) {
            case 0 -> { var wisp = Mob8NaturalRegistries.WISP.create(server); if (wisp!=null) { wisp.moveTo(getX()+random.nextGaussian()*5,getY()+random.nextGaussian()*5,getZ()+random.nextGaussian()*5,0,0); if(server.random.nextInt(5)==0)wisp.setWispType(Aspect.FLUX.getTag()); var p=wisp.blockPosition();var r=wisp.getRandom();boolean dark=server.getBrightness(LightLayer.SKY,p)<=r.nextInt(32)&&(server.isThundering()?server.getMaxLocalRawBrightness(p,10):server.getMaxLocalRawBrightness(p))<=r.nextInt(8);if(server.getDifficulty()!=Difficulty.PEACEFUL&&dark&&server.getBlockState(p.below()).isValidSpawn(server,p.below(),wisp.getType())&&wisp.checkSpawnObstruction(server)&&server.addFreshEntity(wisp))occurred=true; } }
            case 1 -> { var seed = new EntityTaintSeedPrime(server); seed.moveTo((int)(getX()+random.nextGaussian()*5)+.5,(int)(getY()+random.nextGaussian()*5),(int)(getZ()+random.nextGaussian()*5)+.5,server.random.nextInt(360),0); if(seed.checkSpawnRules(server,MobSpawnType.EVENT)&&server.addFreshEntity(seed)){occurred=true;seed.boost=getRiftSize();AuraHelper.polluteAura(server,blockPosition(),getRiftSize()/2,true);discard();} }
            case 2 -> { for(var living:server.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(16))){occurred=true;if(living instanceof Player player)player.displayClientMessage(Component.translatable("tc.fluxevent.2").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE,net.minecraft.ChatFormatting.ITALIC),true);var effect=new MobEffectInstance(WarpEffects.INFECTIOUS_VIS_EXHAUST,3000,2);effect.getCures().clear();living.addEffect(effect);} }
            case 3 -> { var target=server.getNearestPlayer(this,16); if(target!=null){var pack=new FocusPackage(target);var root=new FocusMediumRoot();root.setupFromCasterToTarget(target,target,.5);pack.addNode(root);var cloud=new FocusMediumCloud();cloud.initialize();cloud.getSetting("radius").setValue(1+random.nextInt(3));int min=Math.min(getRiftSize()/2,30),max=Math.min(getRiftSize(),120);cloud.getSetting("duration").setValue(min+random.nextInt(max-min+1));pack.addNode(cloud);pack.addNode(new FocusEffectFlux());FocusEngine.castFocusPackage(target,pack,true);} }
            case 4 -> setCollapse(true);
        }
        // Source cloud/collapse deliberately do not set didit; they do not buy stability.
        if(occurred)setRiftStability(getRiftStability()+FluxRiftRules.EVENT_COSTS[event]);
    }
    private void completeCollapse(ServerLevel server) {
        int count=FluxRiftRules.collapseSeeds(maxSize);
        if(random.nextInt(100)<count){var pearl=new ItemStack(ItemsTC.primordialPearl);pearl.setDamageValue(4+random.nextInt(4));spawnAtLocation(pearl);}
        for(int i=0;i<count;i++)spawnAtLocation(new ItemStack(ItemsTC.voidSeed));
        server.broadcastEntityEvent(this,(byte)40);
        var targets=EntityUtils.getEntitiesInRange(server,getX(),getY(),getZ(),this,LivingEntity.class,32);int category=FluxRiftRules.category(getRiftStability());
        if(category==3)for(var target:targets){int duration=FluxRiftRules.collapseExposure(distanceToSqr(target),120);if(duration>0)target.addEffect(new MobEffectInstance(WarpEffects.FLUX_TAINT,duration*20));}
        if(category>=2)for(var target:targets){int duration=FluxRiftRules.collapseExposure(distanceToSqr(target),300);if(duration>0)target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,duration*20));}
        if(category>=1)for(var target:targets)if(target instanceof Player player){int warp=FluxRiftRules.collapseExposure(distanceToSqr(player),25);if(warp>0){WarpGameplay.addWarpToPlayer(player,warp,IPlayerWarp.EnumWarpType.NORMAL);WarpGameplay.addWarpToPlayer(player,warp,IPlayerWarp.EnumWarpType.TEMPORARY);}}
        discard();
    }
    @Override public void handleEntityEvent(byte event){if(event==40)RiftPresentationHooks.collapse.accept(this);else super.handleEntityEvent(event);}
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.putInt("MaxSize",maxSize);tag.putInt("RiftSize",getRiftSize());tag.putInt("RiftSeed",getRiftSeed());tag.putFloat("Stability",getRiftStability());tag.putBoolean("collapse",getCollapse());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){setRiftSeed(tag.getInt("RiftSeed"));setRiftSize(tag.getInt("RiftSize"));setRiftStability(tag.getFloat("Stability"));entityData.set(COLLAPSE,tag.getBoolean("collapse"));maxSize=FluxRiftRules.boundedSize(tag.getInt("MaxSize"));rebuild();}
    public enum EnumStability { VERY_STABLE,STABLE,UNSTABLE,VERY_UNSTABLE }
}
