package thaumcraft.common.blocks.basic;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.common.tiles.misc.TileBanner;

/** Source banners: sixteen dyes and the uncoloured cult variant, including phial aspect decoration. */
public final class BlockBannerTC extends BaseEntityBlock {
    public final DyeColor dye;
    private static final MapCodec<BlockBannerTC> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
        DyeColor.CODEC.optionalFieldOf("dye").forGetter(block->Optional.ofNullable(block.dye)),propertiesCodec())
        .apply(instance,(dye,properties)->new BlockBannerTC(dye.orElse(null),properties)));
    public BlockBannerTC(){this(null);}
    public BlockBannerTC(DyeColor dye){this(dye,Properties.of().mapColor(dye==null?MapColor.COLOR_RED:dye.getMapColor()).strength(1).sound(SoundType.WOOD).noCollission().noOcclusion());}
    private BlockBannerTC(DyeColor dye,Properties properties){super(properties);this.dye=dye;}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TileBanner(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        if(!(level.getBlockEntity(pos) instanceof TileBanner))return Shapes.block();
        if(level.getBlockEntity(pos) instanceof TileBanner banner&&banner.getWall())return switch(banner.getBannerFacing()) {
            case 0->Shapes.box(0,-1,0,1,1,.25);case 4->Shapes.box(.75,-1,0,1,1,1);
            case 8->Shapes.box(0,-1,.75,1,1,1);case 12->Shapes.box(0,-1,0,.25,1,1);default->Shapes.block();};
        return Shapes.box(.33F,0,.33F,.66F,2,.66F);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){
        return List.of(decorated(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)));
    }
    private ItemStack decorated(BlockEntity entity) {
        ItemStack stack=new ItemStack(this);
        if(entity instanceof TileBanner tile&&(dye!=null||tile.getAspect()!=null)) {
            var tag=new CompoundTag();if(tile.getAspect()!=null)tag.putString("aspect",tile.getAspect().getTag());
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        }
        return stack;
    }
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){return decorated(level.getBlockEntity(pos));}
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        decorate(level,pos,player,held);return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        decorate(level,pos,player,player.getMainHandItem());return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private void decorate(Level level,BlockPos pos,Player player,ItemStack held) {
        if(level.isClientSide||dye==null||!(level.getBlockEntity(pos) instanceof TileBanner tile)
            ||!player.isShiftKeyDown()&&!(held.getItem() instanceof thaumcraft.common.items.consumables.ItemPhial))return;
        if(player.isShiftKeyDown())tile.setAspect(null);
        else if(held.getItem() instanceof thaumcraft.common.items.consumables.ItemPhial phial) {
            var aspects=phial.getAspects(held);
            if(aspects!=null&&aspects.size()>0){tile.setAspect(aspects.getAspects()[0]);held.shrink(1);} // Source consumes in creative too.
        }
        tile.setChanged();level.sendBlockUpdated(pos,tile.getBlockState(),tile.getBlockState(),3);
        level.playSound(null,pos,SoundEvents.WOOL_HIT,SoundSource.BLOCKS,1,1);
    }
}
