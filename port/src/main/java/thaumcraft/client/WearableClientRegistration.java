package thaumcraft.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
import thaumcraft.Thaumcraft;
import thaumcraft.common.items.armor.*;
import thaumcraft.client.renderers.models.gear.*;
import thaumcraft.common.items.armor.SourceCultistArmorGeometry.Kind;

@EventBusSubscriber(modid=Thaumcraft.MODID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class WearableClientRegistration {
    private WearableClientRegistration(){}
    public static ModelLayerLocation fortressLayer(boolean inner){return new ModelLayerLocation(ResourceLocation.parse("thaumcraft:fortress_armor"),inner?"inner":"outer");}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){for(boolean inner:new boolean[]{false,true})event.registerLayerDefinition(fortressLayer(inner),()->ModelFortressArmor.createLayer(inner));}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){
        for(String family:new String[]{"fortress","void_robe","crimson_praetor"})event.registerItem(new Extension(family),WearableRegistries.item(family+"_helm"),WearableRegistries.item(family+"_chest"),WearableRegistries.item(family+"_legs"));
        event.registerItem(new IClientItemExtensions(){@Override public int getDefaultDyeColor(ItemStack stack){return 0xFF000000|((ItemRobeArmor)stack.getItem()).getColor(stack);}},WearableRegistries.item("cloth_chest"),WearableRegistries.item("cloth_legs"),WearableRegistries.item("cloth_boots"));
    }
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event){event.register((stack,index)->index==0&&stack.getItem() instanceof ItemRobeArmor robe?0xFF000000|robe.getColor(stack):0xFFFFFFFF,WearableRegistries.item("cloth_chest"),WearableRegistries.item("cloth_legs"),WearableRegistries.item("cloth_boots"),WearableRegistries.item("void_robe_helm"),WearableRegistries.item("void_robe_chest"),WearableRegistries.item("void_robe_legs"));}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{ItemProperties.register(WearableRegistries.item("verdant_charm"),ResourceLocation.withDefaultNamespace("type"),(stack,world,entity,seed)->WearableTags.verdantType(stack));for(String name:new String[]{"goggles","curiosity_band"})top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(WearableRegistries.item(name),()->new CurioHeadRenderer(name));});}
    @SubscribeEvent public static void additionalModels(ModelEvent.RegisterAdditional event){for(String name:new String[]{"goggles","curiosity_band"})event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ResourceLocation.parse("thaumcraft:item/"+name+"_worn")));}
    @SubscribeEvent public static void hud(RegisterGuiLayersEvent event){event.wrapLayer(net.neoforged.neoforge.client.gui.VanillaGuiLayers.ARMOR_LEVEL,original->(graphics,tracker)->{if(!thaumcraft.client.lib.WearableClientHooks.renderSourceArmor(graphics))original.render(graphics,tracker);});}
    private static final class Extension implements IClientItemExtensions {
        final String family;Object owner;ModelCultistArmor inner,outer;
        Extension(String family){this.family=family;}
        @Override public int getDefaultDyeColor(ItemStack stack){return stack.getItem() instanceof ItemRobeArmor robe?0xFF000000|robe.getColor(stack):0xFFFFFFFF;}
        ModelCultistArmor bake(boolean inner){var models=Minecraft.getInstance().getEntityModels();return family.equals("fortress")?new ModelFortressArmor(models.bakeLayer(fortressLayer(inner))):family.equals("void_robe")?new ModelRobe(models.bakeLayer(CultistArmorClientRegistration.layer(Kind.ROBE,inner))):new ModelLeaderArmor(models.bakeLayer(CultistArmorClientRegistration.layer(Kind.LEADER,inner)));}
        @Override public Model getGenericArmorModel(LivingEntity wearer,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){var models=Minecraft.getInstance().getEntityModels();if(owner!=models){owner=models;inner=bake(true);outer=bake(false);}var model=CustomArmorHelper.inner(slot)?inner:outer;CustomArmorHelper.prepare(model,wearer,slot,0);return model;}
        @Override public void setupModelAnimations(LivingEntity wearer,ItemStack stack,EquipmentSlot slot,Model model,float swing,float amount,float partial,float age,float yaw,float pitch){if(model instanceof ModelCultistArmor armor){CustomArmorHelper.prepare(armor,wearer,slot,partial);armor.setupAnim(wearer,swing,amount,age,yaw,pitch);}}
    }
}
