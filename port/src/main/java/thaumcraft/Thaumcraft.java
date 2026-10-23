package thaumcraft;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.slf4j.Logger;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.config.ConfigResearch;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.InternalMethodHandler;
import thaumcraft.proxies.CommonProxy;
import thaumcraft.proxies.IProxy;
import thaumcraft.common.world.biomes.TCBiomeRegion;
import thaumcraft.common.lib.research.catalog.ResearchCatalogRuntime;

@Mod(Thaumcraft.MODID)
public class Thaumcraft {
    public static final String MODID = "thaumcraft";
    public static final String MODNAME = "Thaumcraft";
    public static final String VERSION = "6.1.BETA26";
    /** Replaced by ClientProxy in {@link ThaumcraftClient}. */
    public static IProxy proxy = new CommonProxy();
    public static final Logger log = LogUtils.getLogger();

    public Thaumcraft(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ModConfig.CLIENT_SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ModConfig.SERVER_SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_SPEC);
        ThaumcraftApi.internalMethods = new InternalMethodHandler();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ConfigBlocks::initMisc);
        event.enqueueWork(thaumcraft.common.golems.GolemProperties::initialize);
        event.enqueueWork(thaumcraft.common.lib.crafting.DustTriggers::initialize);
        event.enqueueWork(TCBiomeRegion::register);
        event.enqueueWork(ConfigResearch::initCategories);
        event.enqueueWork(() -> ConfigResearch.registerResourceLocations(ThaumcraftApi::registerResearchLocation));
    }

    private void loadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(ModConfig::postInitMisc);
        event.enqueueWork(ConfigResearch::initScannables);
        // All mods' common setup work has finished. Reload worker threads consume this immutable
        // snapshot, never the public mutable TC6 maps. Active-server reloads capture on its thread.
        event.enqueueWork(ResearchCatalogRuntime::captureSetupSnapshot);
    }
}
