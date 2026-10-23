package thaumcraft.common.items.casters;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import thaumcraft.api.casters.*;
/** All three original focus tiers share source package data and complexity-derived costs. */
public class ItemFocus extends Item {
    private final int maxComplexity;
    public ItemFocus(Properties properties,int complexity){super(properties.stacksTo(1).rarity(Rarity.RARE));maxComplexity=complexity;}
    public int getMaxComplexity(){return maxComplexity;}
    public static void setPackage(ItemStack stack,FocusPackage p){CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->{tag.put("package",p.serialize());tag.remove("color");tag.remove("srt");});}
    public static FocusPackage getPackage(ItemStack stack){if(stack==null||stack.isEmpty())return null;var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(!tag.contains("package"))return null;var p=new FocusPackage();try{p.deserialize(tag.getCompound("package"));}catch(IllegalArgumentException invalid){return null;}return p.nodes.isEmpty()||!Float.isFinite(p.getPower())||p.getPower()<=0||p.getComplexity()<0||stack.getItem() instanceof ItemFocus item&&p.getComplexity()>item.getMaxComplexity()?null:p;}
    public static float getVisCost(ItemStack stack){var p=getPackage(stack);return p==null?0:p.getComplexity()/5F;}
    public static int getActivationTime(ItemStack stack){var p=getPackage(stack);return p==null?0:Math.max(5,p.getComplexity()/5*(p.getComplexity()/4));}
    public static int getCooldown(ItemStack stack){return getActivationTime(stack);}
    public int getFocusColor(ItemStack stack){var p=getPackage(stack);if(p==null)return 0xffffff;var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(tag.contains("color"))return tag.getInt("color");int r=0,g=0,b=0;var effects=p.getFocusEffects();for(var effect:effects){int c=FocusEngine.getElementColor(effect.getKey());r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;}int color=effects.length==0?0:0xff000000|r/effects.length<<16|g/effects.length<<8|b/effects.length;CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putInt("color",color));return color;}
    public String getSortingHelper(ItemStack stack){var p=getPackage(stack);if(p==null)return null;var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();int sort=tag.getInt("srt");if(sort==0){sort=p.getSortingHelper();final int value=sort;CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putInt("srt",value));}return stack.getHoverName().getString()+sort;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){addFocusInformation(stack,tooltip);}
    public void addFocusInformation(ItemStack stack,List<Component> tooltip){var p=getPackage(stack);if(p==null)return;tooltip.add(Component.literal(new java.text.DecimalFormat("#######.#").format(getVisCost(stack))+" ").append(Component.translatable("item.Focus.cost1")));info(p,tooltip,0);}
    private void info(FocusPackage p,List<Component> tooltip,int depth){if(depth>32)return;for(var element:p.nodes)if(element instanceof FocusNode node&&!(node instanceof FocusMediumRoot)){var line=Component.literal("  ".repeat(depth)).append(Component.translatable(node.getUnlocalizedName())).withStyle(ChatFormatting.DARK_PURPLE);if(!node.getSettingList().isEmpty()){line.append(" [");boolean comma=false;for(var key:node.getSettingList()){var setting=node.getSetting(key);line.append((comma?", ":"")+setting.getLocalizedName()+" "+setting.getValueText());comma=true;}line.append("]");}tooltip.add(line);if(node instanceof FocusModSplit split)for(var child:split.getSplitPackages())info(child,tooltip,depth+1);}}
}
