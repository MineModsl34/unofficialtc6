package thaumcraft.common.items.tools;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import thaumcraft.api.items.IWarpingGear;

/** Source simultaneous pickaxe/shovel. Removed Material predicates have an explicit native bridge. */
public final class ItemPrimalCrusher extends DiggerItem implements IWarpingGear {
    private static final Set<String> TAINT=Set.of("taint_crust","taint_rock","taint_soil","taint_feature","taint_log","taint_fibre");
    private static final Set<String> WOOD_DEVICES=Set.of("bookshelf","crafting_table","chest","trapped_chest","ladder","jukebox","note_block","brown_mushroom_block","red_mushroom_block","mushroom_stem","barrel","composter","fletching_table","cartography_table","loom","smithing_table");
    public ItemPrimalCrusher(){super(EquipmentMaterials.PRIMAL,BlockTags.MINEABLE_WITH_PICKAXE,new Properties().attributes(DiggerItem.createAttributes(EquipmentMaterials.PRIMAL,3.5F,-2.8F)));}
    @Override public boolean isCorrectToolForDrops(ItemStack stack,BlockState state){
        var nativeId=BuiltInRegistries.BLOCK.getKey(state.getBlock());if(nativeId.getNamespace().equals("thaumcraft") && TAINT.contains(nativeId.getPath()))return true;
        if(EquipmentRules.crusherRejectsSourceWood(nativeId.getNamespace(),nativeId.getPath()))return false;
        if(state.getBlock() instanceof LeavesBlock || state.getBlock() instanceof BushBlock || state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS))return false;
        if(state.getTags().anyMatch(tag->tag.location().getNamespace().equals("minecraft") && tag.location().getPath().startsWith("wooden_")))return false;
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if(id.getNamespace().equals("minecraft") && (WOOD_DEVICES.contains(id.getPath()) || id.getPath().endsWith("_wood") || id.getPath().endsWith("_hyphae") || id.getPath().endsWith("_fence") || id.getPath().endsWith("_fence_gate")))return false;
        return true;
    }
    @Override public float getDestroySpeed(ItemStack stack,BlockState state){
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if(state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || id.getNamespace().equals("thaumcraft") && TAINT.contains(id.getPath()))return 8;
        return 1;
    }
    @Override public boolean canPerformAction(ItemStack stack,ItemAbility ability){return ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability) || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability);}
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){super.inventoryTick(stack,level,entity,slot,selected);EquipmentToolActions.repair(stack,entity);}
    @Override public int getWarp(ItemStack stack,Player player){return 2;}
}
