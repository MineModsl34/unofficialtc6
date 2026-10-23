package thaumcraft.common.entities;

import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.projectile.EntityAlumentum;

@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class AlumentumRegistration {
    public static EntityType<EntityAlumentum> TYPE;
    private AlumentumRegistration() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            TYPE = EntityType.Builder.<EntityAlumentum>of(EntityAlumentum::new, MobCategory.MISC).sized(.25F, .25F)
                .clientTrackingRange(4).updateInterval(20).build("thaumcraft:alumentum");
            helper.register(ResourceLocation.parse("thaumcraft:alumentum"), TYPE);
        });
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DispenserBlock.registerBehavior(ItemsTC.alumentum, new ProjectileDispenseBehavior(ItemsTC.alumentum)));
    }
}
