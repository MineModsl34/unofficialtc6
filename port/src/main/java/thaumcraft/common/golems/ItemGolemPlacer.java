package thaumcraft.common.golems;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.ISealDisplayer;
import thaumcraft.api.golems.parts.GolemArm;
import thaumcraft.api.golems.parts.GolemHead;
import thaumcraft.api.golems.parts.GolemMaterial;

/** Source built-golem item; actual properties, rank XP, ownership and native spawn lifecycle. */
public final class ItemGolemPlacer extends Item implements ISealDisplayer {
    public ItemGolemPlacer() { super(new Properties()); }
    public List<ItemStack> creativeStacks() {
        GolemProperties.initialize(); var result = new ArrayList<ItemStack>();
        for (int[] config : new int[][]{{0,0,0},{0,1,1},{1,1,2},{4,1,3}}) {
            var props = new GolemProperties(); props.setMaterial(GolemMaterial.getMaterials()[config[0]]);
            props.setHead(GolemHead.getHeads()[config[1]]); props.setArms(GolemArm.getArms()[config[2]]);
            result.add(GolemItemData.write(new ItemStack(this),props,0));
        }
        return List.copyOf(result);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag) {
        try {
            if (GolemItemData.tag(stack).contains("props")) {
                var props = GolemItemData.properties(stack);
                if (props.hasTrait(EnumGolemTrait.SMART)) {
                    var text = Component.translatable("golem.rank").append(" " + props.getRank()).withStyle(ChatFormatting.GOLD);
                    if (props.getRank() < 10) text.append(Component.literal(" (" + GolemItemData.experience(stack) + "/" + (props.getRank() + 1) * (props.getRank() + 1) * 1000 + ")").withStyle(ChatFormatting.DARK_GREEN));
                    tooltip.add(text);
                }
                tooltip.add(Component.translatable("golem.material." + props.getMaterial().key.toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GREEN));
                for (var trait : props.getTraits()) tooltip.add(Component.literal("-").append(Component.translatable("golem.trait." + trait.name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.BLUE));
            }
        } catch (IllegalArgumentException invalid) { tooltip.add(Component.translatable("thaumcraft.golem.invalid_properties").withStyle(ChatFormatting.RED)); }
        super.appendHoverText(stack,context,tooltip,flag);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context) {
        var world = context.getLevel(); var player = context.getPlayer();
        if (player == null || !world.getBlockState(context.getClickedPos()).isSolid()) return InteractionResult.FAIL;
        if (world.isClientSide) return InteractionResult.PASS;
        var pos = context.getClickedPos().relative(context.getClickedFace());
        if (!(world instanceof ServerLevel server) || !world.hasChunkAt(pos) || !player.mayUseItemAt(pos,context.getClickedFace(),stack) || !world.mayInteract(player,pos)) return InteractionResult.FAIL;
        final GolemProperties props; try { props = GolemItemData.properties(stack); } catch (IllegalArgumentException invalid) { return InteractionResult.FAIL; }
        var golem = new EntityThaumcraftGolem(world); golem.moveTo(pos.getX() + .5,pos.getY(),pos.getZ() + .5,0,0);
        golem.setOwned(true); golem.setValidSpawn(); golem.setOwnerId(player.getUUID()); golem.setProperties(props); golem.rankXp = GolemItemData.experience(stack);
        golem.finalizeSpawn(server,server.getCurrentDifficultyAt(pos),MobSpawnType.SPAWN_EGG,null);
        // Source ignored world obstruction. Native permission/entity collision must not consume or duplicate an item.
        if (!world.noCollision(golem) || !world.addFreshEntity(golem)) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
