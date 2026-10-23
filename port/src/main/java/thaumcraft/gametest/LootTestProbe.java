package thaumcraft.gametest;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.BlockLootTC;
import thaumcraft.common.lib.utils.LegacyLootRules.Rarity;

/** Registered only on the dedicated test server, before intrusive block holders are frozen. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class LootTestProbe {
    public static final ResourceLocation ID = ResourceLocation.parse("thaumcraft:gametest_loot_probe");
    static ProbeLootBlock block;

    private LootTestProbe() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterEvent event) {
        if (!GameTestHooks.isGametestServer() || !Boolean.getBoolean("thaumcraft.gearTestProbe")) {
            return;
        }
        event.register(Registries.BLOCK, registry -> {
            if (!(BlocksTC.lootCrateCommon instanceof BlockLootTC)) {
                throw new IllegalStateException("Loot probe must register after the actual TC crate");
            }
            block = new ProbeLootBlock();
            registry.register(ID, block);
        });
    }

    static final class ProbeLootBlock extends BlockLootTC {
        int contentsCalls;

        private ProbeLootBlock() {
            super(BlockBehaviour.Properties.of().lootFrom(() -> BlocksTC.lootCrateCommon), true, Rarity.COMMON);
        }

        @Override
        public synchronized List<ItemStack> generateContents(ServerLevel level) {
            contentsCalls++;
            throw new AssertionError("Silk must not invoke random crate contents");
        }
    }
}
