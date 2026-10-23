package thaumcraft.common.blocks.misc;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.common.items.casters.FocusBlockActions;
import thaumcraft.common.lib.fx.SourceFX;
import net.minecraft.world.phys.Vec3;
/** Source sap/shock air effects, random-tick expiry and deferred entity effects. */
public final class BlockFocusEffect extends Block {
    public static final MapCodec<BlockFocusEffect> CODEC=simpleCodec(BlockFocusEffect::new);
    public BlockFocusEffect(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BlockFocusEffect> codec(){return CODEC;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){level.removeBlock(pos,false);}
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(level.isClientSide)return;
        if(state.is(thaumcraft.api.blocks.BlocksTC.effectShock)){
            if(entity instanceof LivingEntity living)FocusBlockActions.schedule(level,()->{
                living.hurt(level.damageSources().magic(),1);
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20,0,true,true));
            },0);
            if(level.random.nextInt(100)==0)level.removeBlock(pos,false);
        }else if(entity instanceof LivingEntity living&&!(entity instanceof IEldritchMob)&&!living.hasEffect(MobEffects.WITHER))FocusBlockActions.schedule(level,()->{living.addEffect(new MobEffectInstance(MobEffects.WITHER,40,0,true,true));living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,1,true,true));living.addEffect(new MobEffectInstance(MobEffects.HUNGER,40,1,true,true));},0);
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        float h=random.nextFloat()*.33F;
        double x=pos.getX()+level.random.nextFloat(),y=pos.getY()+.1515F+h/2F,z=pos.getZ()+level.random.nextFloat();
        boolean shock=state.is(thaumcraft.api.blocks.BlocksTC.effectShock);
        float red=shock?.65F+level.random.nextFloat()*.1F:.3F-level.random.nextFloat()*.1F;
        float blue=shock?1:.5F+level.random.nextFloat()*.2F;
        SourceFX.local(level,"block_spark",new Vec3(x,y,z),new Vec3(shock?1:0,blue,shock?.8F:1),Float.floatToRawIntBits(red),Float.floatToRawIntBits(3F+h*6F));
        if(random.nextInt(50)==0)level.playLocalSound(pos.getX(),pos.getY(),pos.getZ(),thaumcraft.common.lib.SoundsTC.jacobs,net.minecraft.sounds.SoundSource.AMBIENT,.25F,1+(random.nextFloat()-random.nextFloat())*.2F,false);
    }
}
