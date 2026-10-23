package thaumcraft.common.items.casters;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.api.items.CasterStoredStack;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.network.misc.PacketAuraToClient;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;

/** Source gauntlet: caster interactions, typed focus/block storage, aura payment and the full focus engine. */
public class ItemCaster extends Item implements ICaster,thaumcraft.api.items.IArchitect {
    private static final int[][] CROSS = {{0, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    private final int area;
    public ItemCaster(Properties properties, int area) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON)); this.area = area;
    }
    @Override public float getConsumptionModifier(ItemStack stack, Player player, boolean crafting) {
        return CasterVisConsumption.modifier(ArcaneWorkbenchCosts.discount(player));
    }
    @Override public boolean consumeVis(ItemStack stack, Player player, float amount, boolean crafting, boolean simulate) {
        if (player == null || player.level().isClientSide || !player.level().getServer().isSameThread()) return false;
        Level level = player.level(); BlockPos center = player.blockPosition();
        if (Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))
            thaumcraft.common.lib.validation.TcpCasterAudit.observeCastBefore(stack,player,amount,crafting,simulate,area);
        boolean consumed = CasterVisConsumption.consume(amount * getConsumptionModifier(stack, player, crafting), area, simulate, new CasterVisConsumption.Aura() {
            private BlockPos position(int x, int z) { return center.offset(x * 16, 0, z * 16); }
            @Override public float available(int x, int z) { return AuraHandler.getVis(level, position(x, z)); }
            @Override public float drain(int x, int z, float request) { return AuraHandler.drainVis(level, position(x, z), request, false); }
        });
        if (Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))
            thaumcraft.common.lib.validation.TcpCasterAudit.observeCastAfter(stack,player,amount,crafting,simulate,area,consumed);
        return consumed;
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer(); if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel(); BlockPos pos = context.getClickedPos();
        if (level.getBlockState(pos).getBlock() instanceof IInteractWithCaster interactive
            && interactive.onCasterRightClick(level, stack, player, pos, context.getClickedFace(), context.getHand())) return InteractionResult.PASS;
        if (level.getBlockEntity(pos) instanceof IInteractWithCaster interactive
            && interactive.onCasterRightClick(level, stack, player, pos, context.getClickedFace(), context.getHand())) return InteractionResult.PASS;
        var state=level.getBlockState(pos);
        if(thaumcraft.api.casters.CasterTriggerRegistry.hasTrigger(state))return thaumcraft.api.casters.CasterTriggerRegistry.performTrigger(level,stack,player,pos,context.getClickedFace(),state)?InteractionResult.SUCCESS:InteractionResult.FAIL;
        var focus=ItemFocus.getPackage(getFocusStack(stack));
        if(focus!=null&&player.isShiftKeyDown()&&level.getBlockEntity(pos)==null&&focus.nodes.stream().anyMatch(node->node instanceof thaumcraft.api.casters.IFocusBlockPicker)){
            if(!level.isClientSide){var picked=state.getCloneItemStack(new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),context.getClickedFace(),pos,false),level,pos,player);storePickedBlock(stack,picked);return InteractionResult.SUCCESS;}player.swing(context.getHand());
        }
        return InteractionResult.PASS;
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity.tickCount % 10 == 0 && entity instanceof ServerPlayer player) {
            for (ItemStack held : player.getHandSlots()) if (!held.isEmpty() && held.getItem() instanceof ICaster) {
                updateAura(level, player); break;
            }
        }
    }
    private void updateAura(Level level, ServerPlayer player) {
        // Source HUD truncates before shifting; deliberately differs from floor-based payment at negative coordinates.
        int x = (int)player.getX() >> 4, z = (int)player.getZ() >> 4;
        float vis = 0, flux = 0; short base = 0;
        if (area == 2) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                AuraChunk chunk = AuraHandler.getAuraChunk(level, x + dx, z + dz);
                if (chunk != null) { vis += chunk.getVis(); flux += chunk.getFlux(); base += chunk.getBase(); }
            }
        } else {
            AuraChunk center = AuraHandler.getAuraChunk(level, x, z);
            if (center != null) {
                vis = center.getVis(); flux = center.getFlux(); base = center.getBase();
                if (area == 1) for (int index = 1; index < CROSS.length; index++) {
                    AuraChunk chunk = AuraHandler.getAuraChunk(level, x + CROSS[index][0], z + CROSS[index][1]);
                    if (chunk != null) { vis += chunk.getVis(); flux += chunk.getFlux(); base += chunk.getBase(); }
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new PacketAuraToClient(base, vis, flux));
    }
    @Override public ItemStack getFocusStack(ItemStack stack) {
        CasterStoredStack stored = stack.get(TCDataComponents.CASTER_FOCUS);
        return stored == null ? null : stored.copy();
    }
    @Override public ItemFocus getFocus(ItemStack stack) {
        ItemStack focus = getFocusStack(stack); return focus != null && focus.getItem() instanceof ItemFocus item ? item : null;
    }
    @Override public void setFocus(ItemStack stack, ItemStack focus) {
        if (focus == null || focus.isEmpty()) stack.remove(TCDataComponents.CASTER_FOCUS);
        else stack.set(TCDataComponents.CASTER_FOCUS, new CasterStoredStack(focus));
    }
    public void storePickedBlock(ItemStack stack,ItemStack picked){if(picked!=null&&!picked.isEmpty())stack.set(TCDataComponents.CASTER_PICKED,new CasterStoredStack(picked));}
    @Override public ItemStack getPickedBlock(ItemStack stack) { if(stack.isEmpty())return ItemStack.EMPTY;var pack=ItemFocus.getPackage(getFocusStack(stack));if(pack==null||pack.nodes.stream().noneMatch(node->node instanceof thaumcraft.api.casters.IFocusBlockPicker))return null;var picked=stack.get(TCDataComponents.CASTER_PICKED);if(picked!=null)return picked.copy();var tag=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();return !tag.contains("picked")||server==null?ItemStack.EMPTY:ItemStack.parseOptional(server.registryAccess(),tag.getCompound("picked")); }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,Player player,net.minecraft.world.InteractionHand hand){var stack=player.getItemInHand(hand);var focus=getFocusStack(stack);var core=ItemFocus.getPackage(focus);if(getFocus(stack)==null||core==null||core.nodes.isEmpty()||CasterManager.isOnCooldown(player))return net.minecraft.world.InteractionResultHolder.pass(stack);CasterManager.setCooldown(player,ItemFocus.getActivationTime(focus));if(player.isShiftKeyDown()&&core.nodes.stream().anyMatch(node->node instanceof thaumcraft.api.casters.IFocusBlockPicker))return net.minecraft.world.InteractionResultHolder.pass(stack);if(level.isClientSide)return net.minecraft.world.InteractionResultHolder.success(stack);if(!consumeVis(stack,player,ItemFocus.getVisCost(focus),false,false))return net.minecraft.world.InteractionResultHolder.fail(stack);thaumcraft.api.casters.FocusEngine.castFocusPackage(player,core);player.swing(hand,true);return net.minecraft.world.InteractionResultHolder.success(stack);}
    @Override public java.util.ArrayList<BlockPos> getArchitectBlocks(ItemStack stack,Level level,BlockPos pos,net.minecraft.core.Direction side,Player player){var p=ItemFocus.getPackage(getFocusStack(stack));if(p!=null)for(var node:p.nodes)if(node instanceof thaumcraft.api.items.IArchitect architect)return architect.getArchitectBlocks(stack,level,pos,side,player);return null;}
    @Override public boolean showAxis(ItemStack stack,Level level,Player player,net.minecraft.core.Direction side,thaumcraft.api.items.IArchitect.EnumAxis axis){var p=ItemFocus.getPackage(getFocusStack(stack));if(p!=null)for(var node:p.nodes)if(node instanceof thaumcraft.api.items.IArchitect architect)return architect.showAxis(stack,level,player,side,axis);return false;}
    @Override public net.minecraft.world.phys.HitResult getArchitectMOP(ItemStack stack,Level level,net.minecraft.world.entity.LivingEntity player){var p=ItemFocus.getPackage(getFocusStack(stack));if(p!=null&&thaumcraft.api.casters.FocusEngine.doesPackageContainElement(p,"thaumcraft.PLAN"))return ((thaumcraft.api.items.IArchitect)thaumcraft.api.casters.FocusEngine.getElement("thaumcraft.PLAN")).getArchitectMOP(getFocusStack(stack),level,player);return null;}
    @Override public boolean useBlockHighlight(ItemStack stack){return false;}
    @Override public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged){if(before.getItem()==this&&after.getItem()==this){var old=getFocus(before);var next=getFocus(after);String a=old==null?null:old.getSortingHelper(getFocusStack(before)),b=next==null?null:next.getSortingHelper(getFocusStack(after));return !java.util.Objects.equals(a,b);}return before.getItem()!=after.getItem();}
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,java.util.List<net.minecraft.network.chat.Component> tooltip,net.minecraft.world.item.TooltipFlag flag){var focus=getFocusStack(stack);var item=getFocus(stack);if(item!=null){tooltip.add(focus.getHoverName().copy().withStyle(net.minecraft.ChatFormatting.BOLD,net.minecraft.ChatFormatting.ITALIC,net.minecraft.ChatFormatting.GREEN));item.addFocusInformation(focus,tooltip);}}
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) { return 72000; }
}
