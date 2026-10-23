package thaumcraft.common.blocks.essentia;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.blocks.ILabelable;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.tiles.essentia.EssentiaRegistries;
import thaumcraft.common.tiles.essentia.TileJarFillable;
import thaumcraft.common.tiles.essentia.TileJarFillableVoid;

public class BlockJar extends BaseEntityBlock implements ILabelable {
    public static final MapCodec<BlockJar> CODEC = simpleCodec(BlockJar::new);
    private static final VoxelShape SHAPE = box(3,0,3,13,12,13);
    public BlockJar(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.is(BlocksTC.jarVoid) ? new TileJarFillableVoid(pos,state) : new TileJarFillable(pos,state); }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type == EssentiaRegistries.JAR_VOID) return (world,pos,block,tile) -> ((TileJarFillableVoid)tile).update();
        return createTickerHelper(type,EssentiaRegistries.JAR,(world,pos,block,tile) -> tile.update());
    }
    private static int placedFacing(LivingEntity player) { return switch (net.minecraft.util.Mth.floor(player.getYRot() * 4F / 360F + .5F) & 3) { case 0 -> 2; case 1 -> 5; case 2 -> 3; default -> 4; }; }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity player, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof TileJarFillable jar) { jar.facing = placedFacing(player); if (stack.getItem() instanceof BlockJarItem item) { jar.setAspects(item.getAspects(stack)); jar.aspectFilter = item.getFilter(stack); } jar.changedAndSync(); }
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack jarStack = new ItemStack(this);
        java.util.ArrayList<ItemStack> drops = new java.util.ArrayList<>(); drops.add(jarStack);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TileJarFillable jar) {
            if (jarStack.getItem() instanceof BlockJarItem item) item.setAspects(jarStack,jar.getAspects());
            if (jar.aspectFilter != null) jarStack.set(TCDataComponents.ASPECT_FILTER,jar.aspectFilter.getTag());
            if (jar.blocked && ItemsTC.jarBrace != null) drops.add(new ItemStack(ItemsTC.jarBrace));
        }
        return drops;
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) { return level.getBlockEntity(pos) instanceof TileJarFillable jar ? (int)(jar.amount / 250F * 14F) + (jar.amount > 0 ? 1 : 0) : 0; }
    private static boolean activate(Level level, BlockPos pos, Player player, InteractionHand hand, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof TileJarFillable jar)) return false;
        if (level.isClientSide) return true;
        ItemStack held = player.getItemInHand(hand);
        if (!jar.blocked && held.is(ItemsTC.jarBrace)) { jar.blocked = true; held.shrink(1); level.playSound(null,pos,thaumcraft.common.lib.SoundsTC.key,net.minecraft.sounds.SoundSource.BLOCKS,1F,1F); }
        else if (player.isShiftKeyDown() && jar.aspectFilter != null && side.get3DDataValue() == jar.facing) {
            jar.aspectFilter = null; level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,pos.getX() + .5 + side.getStepX() / 3F,pos.getY() + .5,pos.getZ() + .5 + side.getStepZ() / 3F,new ItemStack(ItemsTC.label)));
            level.playSound(null,pos,thaumcraft.common.lib.SoundsTC.page,net.minecraft.sounds.SoundSource.BLOCKS,1F,1F);
        } else if (player.isShiftKeyDown() && held.isEmpty()) { AuraHelper.polluteAura(level,pos,jar.amount,true); jar.amount = 0; if (jar.aspectFilter == null) jar.aspect = null;
            level.playSound(null,pos,thaumcraft.common.lib.SoundsTC.jar,net.minecraft.sounds.SoundSource.BLOCKS,.4F,1F); level.playSound(null,pos,net.minecraft.sounds.SoundEvents.BOTTLE_FILL,net.minecraft.sounds.SoundSource.BLOCKS,.5F,1F);
        }
        else return true;
        jar.changedAndSync(); player.inventoryMenu.broadcastChanges(); return true;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) { return activate(level,pos,player,hand,hit.getDirection()) ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) { return activate(level,pos,player,InteractionHand.MAIN_HAND,hit.getDirection()) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS; }
    @Override public boolean applyLabel(Player player, BlockPos pos, Direction side, ItemStack label) {
        if (!(player.level().getBlockEntity(pos) instanceof TileJarFillable jar) || jar.aspectFilter != null) return false;
        var tags = label.getItem() instanceof IEssentiaContainerItem container ? container.getAspects(label) : null;
        Aspect tag = jar.amount > 0 ? jar.aspect : tags == null || tags.size() == 0 ? null : tags.getAspects()[0];
        if (tag == null) return false;
        jar.aspect = tag; jar.aspectFilter = tag; jar.facing = placedFacing(player); jar.changedAndSync(); player.level().playSound(null,pos,thaumcraft.common.lib.SoundsTC.jar,net.minecraft.sounds.SoundSource.BLOCKS,.4F,1F); return true;
    }
}
