package thaumcraft.common.lib.utils;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Source crop extension hooks: native BlockState replaces obsolete translation-key + metadata strings. */
public final class CropUtils {
    public static final Set<BlockState> clickableCrops = new HashSet<>(), standardCrops = new HashSet<>(), stackedCrops = new HashSet<>(), lampBlacklist = new HashSet<>();
    static { addStandardCrop(Blocks.MELON,32767); addStandardCrop(Blocks.PUMPKIN,32767); addStackedCrop(Blocks.SUGAR_CANE,32767); addStackedCrop(Blocks.CACTUS,32767); addStandardCrop(Blocks.NETHER_WART,3); }
    private CropUtils() {}
    private static void add(Set<BlockState> target,Block block,int age) { if (age == 32767) { target.addAll(block.getStateDefinition().getPossibleStates()); return; } for (BlockState state : block.getStateDefinition().getPossibleStates()) for (var property : state.getProperties()) if (property instanceof IntegerProperty number && number.getName().equals("age") && state.getValue(number) == age) target.add(state); if (block instanceof CropBlock && age != 7) add(target,block,7); }
    public static void addStandardCrop(Block block,int grownMeta) { add(standardCrops,block,grownMeta); }
    public static void addStandardCrop(ItemStack stack,int grownMeta) { if (stack.getItem() instanceof net.minecraft.world.item.BlockItem item) addStandardCrop(item.getBlock(),grownMeta); }
    public static void addStandardCrop(BlockState grown) { standardCrops.add(grown); }
    public static void addClickableCrop(ItemStack stack,int grownMeta) { if (stack.getItem() instanceof net.minecraft.world.item.BlockItem item) add(clickableCrops,item.getBlock(),grownMeta); }
    public static void addClickableCrop(BlockState grown) { clickableCrops.add(grown); }
    public static void addStackedCrop(Block block,int grownMeta) { add(stackedCrops,block,grownMeta); }
    public static void addStackedCrop(ItemStack stack,int grownMeta) { if (stack.getItem() instanceof net.minecraft.world.item.BlockItem item) addStackedCrop(item.getBlock(),grownMeta); }
    public static void addStackedCrop(BlockState grown) { stackedCrops.add(grown); }
    public static void blacklistLamp(ItemStack stack,int meta) { if (stack.getItem() instanceof net.minecraft.world.item.BlockItem item) add(lampBlacklist,item.getBlock(),meta); }
    public static void blacklistLamp(BlockState state) { lampBlacklist.add(state); }
    public static boolean doesLampGrow(Level level,BlockPos pos) { return !lampBlacklist.contains(level.getBlockState(pos)); }
    public static boolean isGrownCrop(Level level,BlockPos pos) { BlockState state = level.getBlockState(pos); if (state.isAir()) return false; boolean registered = standardCrops.contains(state) || clickableCrops.contains(state) || stackedCrops.contains(state); return state.getBlock() instanceof BonemealableBlock plant && !(state.getBlock() instanceof StemBlock) && !plant.isValidBonemealTarget(level,pos,state) || state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && !registered || standardCrops.contains(state) || clickableCrops.contains(state) || stackedCrops.contains(state) && level.getBlockState(pos.below()).is(state.getBlock()); }
}
