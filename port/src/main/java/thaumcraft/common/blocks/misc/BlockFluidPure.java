package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.potions.WarpEffects;

/** Source single-use Warp Ward bath; never removes the player's actual permanent warp. */
public final class BlockFluidPure extends LiquidBlock{
    public BlockFluidPure(FlowingFluid fluid,Properties properties){super(fluid,properties);}
    /** Source display cadence/sound and actual three-frame bath particle. */
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random){
        if(random.nextInt(10)==0)thaumcraft.common.lib.fx.SourceFX.local(level,"bath",new net.minecraft.world.phys.Vec3(pos.getX()+random.nextFloat(),pos.getY()+state.getFluidState().getAmount()/8D,pos.getZ()+random.nextFloat()),net.minecraft.world.phys.Vec3.ZERO,0,0);
        if(random.nextInt(50)==0)level.playLocalSound(pos.getX()+random.nextFloat(),pos.getY()+.5,pos.getZ()+random.nextFloat(),net.minecraft.sounds.SoundEvents.LAVA_POP,net.minecraft.sounds.SoundSource.BLOCKS,.1F+random.nextFloat()*.1F,.9F+random.nextFloat()*.15F,false);
    }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        double slowdown=1-state.getFluidState().getAmount()/16D;
        var motion=entity.getDeltaMovement();entity.setDeltaMovement(motion.x*slowdown,motion.y,motion.z*slowdown);
        if(!level.isClientSide && state.getFluidState().isSource() && entity instanceof Player player && !player.hasEffect(WarpEffects.WARP_WARD)){
            int warp=ThaumcraftCapabilities.getWarp(player).get(EnumWarpType.PERMANENT);
            int divisor=Math.max(1,(int)Math.sqrt(Math.max(0,warp)));
            player.addEffect(new MobEffectInstance(WarpEffects.WARP_WARD,Math.min(32000,200000/divisor),0,true,true));
            level.removeBlock(pos,false);
        }
    }
}
