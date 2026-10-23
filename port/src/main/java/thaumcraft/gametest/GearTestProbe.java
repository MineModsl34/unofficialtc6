package thaumcraft.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.IRechargable;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Dedicated test-server fixture only: absent from normal dev, datagen, and production registries. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class GearTestProbe {
    public static final ResourceLocation ID = ResourceLocation.parse("thaumcraft:gametest_recharge_probe");
    static Item item;

    private GearTestProbe() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!GameTestHooks.isGametestServer() || !Boolean.getBoolean("thaumcraft.gearTestProbe")) {
            return;
        }
        event.register(Registries.ITEM, registry -> {
            item = new RechargeProbe();
            registry.register(ID, item);
        });
    }

    private static final class RechargeProbe extends Item implements IRechargable, ICurioItem {
        private RechargeProbe() {
            super(new Properties().stacksTo(1));
        }

        @Override
        public int getMaxCharge(ItemStack stack, LivingEntity wearer) {
            return 10;
        }

        @Override
        public EnumChargeDisplay showInHud(ItemStack stack, LivingEntity wearer) {
            return EnumChargeDisplay.NORMAL;
        }
    }
}
