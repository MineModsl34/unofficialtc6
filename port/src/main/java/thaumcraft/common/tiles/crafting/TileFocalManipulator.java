package thaumcraft.common.tiles.crafting;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.*;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.container.ContainerFocalManipulator;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.world.aura.AuraHandler;

/** BETA26 focus constructor, with validated graph and preflight-before-debit payment. */
public final class TileFocalManipulator extends BlockEntity implements Container,MenuProvider {
    private ItemStack focus = ItemStack.EMPTY;
    public final HashMap<Integer,FocusElementNode> data = new HashMap<>();
    public String focusName = "";
    public float vis;
    private float initialVis;
    private int ticks;
    private long revision;
    public TileFocalManipulator(BlockPos pos,BlockState state) { super(FocalRegistries.TYPE,pos,state); }
    public long revision() { return revision; }
    public float initialVis() { return initialVis; }
    public boolean busy() { return vis > 0; }
    public static List<FocusElementNode> readNodes(CompoundTag tag) {
        ListTag list = tag.getList("nodes",Tag.TAG_COMPOUND);
        if (list.size() < 2 || list.size() > FocusGraphRules.MAX_NODES) throw new IllegalArgumentException("Focus graph size");
        ArrayList<FocusElementNode> nodes = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) { FocusElementNode node = new FocusElementNode(); node.deserialize(list.getCompound(i)); nodes.add(node); }
        return nodes;
    }
    public static CompoundTag writeNodes(Collection<FocusElementNode> nodes) {
        CompoundTag tag = new CompoundTag(); ListTag list = new ListTag(); nodes.stream().sorted(Comparator.comparingInt(node -> node.id)).forEach(node -> list.add(node.serialize())); tag.put("nodes",list); return tag;
    }
    private int validate(Player player,List<FocusElementNode> nodes,boolean complete) {
        if (!(focus.getItem() instanceof ItemFocus item)) throw new IllegalArgumentException("Missing focus item");
        int complexity = FocusGraphRules.validate(nodes.stream().map(FocusElementNode::rule).toList(),item.getMaxComplexity(),complete);
        for (FocusElementNode row : nodes) if (row.node != null) {
            String research = row.node.getResearch();
            if (research != null && !research.isEmpty() && !ThaumcraftCapabilities.knowsResearchStrict(player,research)) throw new IllegalArgumentException("Unknown focus research");
            for (String key : row.node.getSettingList()) {
                NodeSetting setting = row.node.getSetting(key); String gate = setting.getResearch();
                if (gate != null && !gate.isEmpty() && !ThaumcraftCapabilities.knowsResearchStrict(player,gate)
                    && setting.getValue() != setting.getType().getValue(setting.getType().getDefault())) throw new IllegalArgumentException("Unknown setting research");
            }
        }
        return complexity;
    }
    public boolean apply(Player player,CompoundTag graph,String name,boolean craft) {
        if (level == null || level.isClientSide || busy() || !stillValid(player) || name == null || name.length() > 50
            || name.chars().anyMatch(Character::isISOControl)) return false;
        final List<FocusElementNode> rows; final int complexity;
        try { rows = readNodes(graph); complexity = validate(player,rows,craft); }
        catch (IllegalArgumentException malformed) { return false; }
        if (!craft) { data.clear(); rows.forEach(row -> data.put(row.id,row)); focusName = name; changedAndSync(); return true; }
        AspectList crystals = new AspectList();
        for (FocusElementNode row : rows) if (row.node != null && row.node.getAspect() != null) crystals.add(row.node.getAspect(),1);
        if (crystals.size() == 0) return false;
        var cost = FocusGraphRules.costs(complexity,((ItemFocus)focus.getItem()).getMaxComplexity());
        if (!player.isCreative() && player.experienceLevel < cost.experience()) return false;
        // Source consumes crystals even in creative. Only XP is bypassed by creative mode.
        Map<Integer,Integer> debit = new LinkedHashMap<>();
        for (Aspect aspect : crystals.getAspects()) {
            int remaining = crystals.getAmount(aspect);
            for (int slot = 0; slot < player.getInventory().items.size() && remaining > 0; slot++) {
                ItemStack held = player.getInventory().items.get(slot); AspectList contents = held.get(TCDataComponents.ASPECTS);
                if (!held.is(ItemsTC.crystalEssence) || contents == null || contents.size() != 1 || contents.getAmount(aspect) != 1) continue;
                int take = Math.min(remaining,held.getCount() - debit.getOrDefault(slot,0));
                if (take > 0) { debit.merge(slot,take,Integer::sum); remaining -= take; }
            }
            if (remaining > 0) return false;
        }
        // No callbacks/re-entry between preflight and all debits; item/XP payment happens once.
        debit.forEach((slot,amount) -> player.getInventory().items.get(slot).shrink(amount));
        if (!player.isCreative()) player.giveExperienceLevels(-cost.experience()); player.getInventory().setChanged();
        data.clear(); rows.forEach(row -> data.put(row.id,row)); focusName = name; vis = initialVis = cost.vis();
        changedAndSync(); level.playSound(null,worldPosition,SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.BLOCKS,1,1); return true;
    }
    public float spendAura(float request) {
        if (!level.getBlockState(worldPosition.above()).is(BlocksTC.arcaneWorkbenchCharger)) return AuraHandler.drainVis(level,worldPosition,request,false);
        float remaining = request,part = request / 9;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { remaining -= AuraHandler.drainVis(level,worldPosition.offset(x * 16,0,z * 16),Math.min(part,remaining),false); if (remaining <= 0) return request - remaining; }
        return request - remaining;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,TileFocalManipulator tile) {
        if (level.isClientSide || ++tile.ticks % 20 != 0 || !tile.busy()) return;
        if (!(tile.focus.getItem() instanceof ItemFocus item)) { tile.vis = 0; tile.changedAndSync(); return; }
        // Validate persisted executable graph before continuing after reload, without needing owner online.
        try { FocusGraphRules.validate(tile.data.values().stream().map(FocusElementNode::rule).toList(),item.getMaxComplexity(),true); }
        catch (IllegalArgumentException malformed) { tile.vis = 0; tile.data.clear(); tile.changedAndSync(); return; }
        if (Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))
            thaumcraft.common.lib.validation.TcpCasterAudit.observeFocalAuraBefore(tile,Math.min(20,tile.vis));
        float consumed = tile.spendAura(Math.min(20,tile.vis));
        if (Boolean.getBoolean(thaumcraft.common.lib.validation.TcpCasterAuditIO.ENABLE_PROPERTY))
            thaumcraft.common.lib.validation.TcpCasterAudit.observeFocalAuraAfter(tile,Math.min(20,tile.vis),consumed);
        if (consumed <= 0) return; tile.vis = Math.max(0,tile.vis - consumed);
        if (tile.vis == 0) tile.endCraft(); tile.changedAndSync();
    }
    private void traverse(FocusPackage pack,FocusElementNode row) {
        pack.addNode(row.node);
        if (row.children.length == 1) traverse(pack,data.get(row.children[0]));
        else if (row.children.length == 2 && row.node instanceof FocusModSplit split) {
            split.getSplitPackages().clear();
            for (int child : row.children) { FocusPackage branch = new FocusPackage(); traverse(branch,data.get(child)); split.getSplitPackages().add(branch); }
        }
    }
    public void endCraft() {
        if (!(focus.getItem() instanceof ItemFocus item)) return;
        int complexity = FocusGraphRules.validate(data.values().stream().map(FocusElementNode::rule).toList(),item.getMaxComplexity(),true);
        FocusPackage pack = new FocusPackage(); pack.setComplexity(complexity); traverse(pack,data.get(0));
        ItemFocus.setPackage(focus,pack);
        if (!focusName.isBlank()) focus.set(DataComponents.CUSTOM_NAME,Component.literal(focusName)); else focus.remove(DataComponents.CUSTOM_NAME);
        data.clear(); vis = initialVis = 0; level.playSound(null,worldPosition,SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.BLOCKS,1,1.5F);
    }
    public void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); }
    @Override public void setChanged() { super.setChanged(); if (revision < Long.MAX_VALUE) revision++; }
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return focus.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? focus : ItemStack.EMPTY; }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return slot == 0 && !busy() && stack.getItem() instanceof ItemFocus; }
    @Override public ItemStack removeItem(int slot,int amount) { if (slot != 0 || busy()) return ItemStack.EMPTY; ItemStack result = focus.split(amount); if (!result.isEmpty()) { data.clear(); changedAndSync(); } return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { if (slot != 0 || busy()) return ItemStack.EMPTY; ItemStack result = focus; focus = ItemStack.EMPTY; data.clear(); return result; }
    @Override public void setItem(int slot,ItemStack stack) { if (slot != 0 || busy()) return; focus = stack; stack.limitSize(1); data.clear(); focusName = stack.isEmpty() || !stack.has(DataComponents.CUSTOM_NAME) ? "" : stack.getHoverName().getString().substring(0,Math.min(50,stack.getHoverName().getString().length())); changedAndSync(); }
    @Override public void clearContent() { focus = ItemStack.EMPTY; data.clear(); vis = initialVis = 0; changedAndSync(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.thaumcraft.wand_workbench"); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new ContainerFocalManipulator(id,inventory,this); }
    public CompoundTag view(Player player) {
        CompoundTag tag = writeNodes(data.values()); tag.putString("focusName",focusName); tag.putFloat("vis",vis); tag.putFloat("initialVis",initialVis);
        ListTag known = new ListTag();
        FocusEngine.elements.keySet().stream().sorted().forEach(key -> { IFocusElement element = FocusEngine.getElement(key);
            if (element instanceof FocusNode node && (node.getResearch() == null || node.getResearch().isEmpty() || ThaumcraftCapabilities.knowsResearchStrict(player,node.getResearch()))) {
                CompoundTag row = new CompoundTag(); row.putString("key",key); ListTag settings = new ListTag();
                for (String setting : node.getSettingList()) { String gate = node.getSetting(setting).getResearch(); if (gate == null || gate.isEmpty() || ThaumcraftCapabilities.knowsResearchStrict(player,gate)) settings.add(StringTag.valueOf(setting)); }
                row.put("settings",settings); known.add(row);
            }
        }); tag.put("known",known); return tag;
    }
    private void write(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.merge(writeNodes(data.values())); if (!focus.isEmpty()) tag.put("focus",focus.save(lookup)); tag.putString("focusName",focusName); tag.putFloat("vis",vis); tag.putFloat("initialVis",initialVis);
    }
    private void read(CompoundTag tag,HolderLookup.Provider lookup) {
        focus = ItemStack.parseOptional(lookup,tag.getCompound("focus")); focus.limitSize(1); focusName = tag.getString("focusName"); if (focusName.length() > 50) focusName = "";
        data.clear(); if (!tag.getList("nodes",Tag.TAG_COMPOUND).isEmpty()) try { for (var row : readNodes(tag)) data.put(row.id,row); } catch (IllegalArgumentException invalid) { data.clear(); }
        vis = tag.getFloat("vis"); initialVis = tag.getFloat("initialVis");
        float maximumVis = focus.getItem() instanceof ItemFocus item
            ? FocusGraphRules.costs(item.getMaxComplexity(),item.getMaxComplexity()).vis() : 0;
        if (!Float.isFinite(vis) || vis < 0 || vis > maximumVis || !Float.isFinite(initialVis) || initialVis < vis || initialVis > maximumVis || data.isEmpty()) vis = initialVis = 0;
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup) { super.saveAdditional(tag,lookup); write(tag,lookup); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup) { super.loadAdditional(tag,lookup); read(tag,lookup); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { CompoundTag tag = new CompoundTag(); write(tag,lookup); return tag; }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup) { read(tag,lookup); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
