package thaumcraft.common.lib.events;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.common.config.ConfigAspects;
import thaumcraft.common.config.ConfigTagAspects;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.network.misc.PacketSyncObjectTags;

/**
 * Not in TC6 (there ConfigAspects.postInit was called in FMLPostInitializationEvent).
 * In 1.21 tags and recipes appear only once datapacks load, so the aspect table is built at server
 * start and after /reload, and then sent to clients.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class AspectEvents {
    private AspectEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        rebuild(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ThaumcraftCraftingManager.setServer(null);
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            // /reload: recipes and tags may have changed
            rebuild(event.getPlayerList().getServer());
        }
        PacketSyncObjectTags packet = PacketSyncObjectTags.fromServerTable();
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, packet));
    }

    private static void dropEmpty() {
        CommonInternals.objectTags.entrySet().removeIf(entry -> {
            boolean empty = entry.getValue() == null || entry.getValue().size() == 0;
            if (empty) {
                CommonInternals.objectTagStacks.remove(entry.getKey());
                CommonInternals.generatedTags.remove(entry.getKey());
            }
            return empty;
        });
    }

    private static void rebuild(MinecraftServer server) {
        long start = System.currentTimeMillis();
        ThaumcraftCraftingManager.setServer(server);
        CommonInternals.generatedTags.clear();
        ConfigAspects.postInit();
        thaumcraft.common.lib.crafting.CrucibleRecipes.postAspects();
        // Values generated during ConfigAspects may have been computed before their ingredients were registered
        // (for example, the lapis block before lapis): drop them and recompute from the full table
        for (Integer id : CommonInternals.generatedTags) {
            CommonInternals.objectTags.remove(id);
            CommonInternals.objectTagStacks.remove(id);
        }
        CommonInternals.generatedTags.clear();
        int explicit = CommonInternals.objectTags.size();
        // Precompute the aspects of every item so the client receives the full table
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                ThaumcraftCraftingManager.getObjectTags(new ItemStack(item));
            }
        }
        // Not in TC6: a second pass for items still empty (mostly other mods' items): tag rules,
        // recipes of any type, fallback values. Values from the first pass stay as they are.
        dropEmpty();
        ThaumcraftCraftingManager.setExtendedDerivation(true);
        ConfigTagAspects.fill();
        NeoForge.EVENT_BUS.post(new thaumcraft.api.aspects.AspectGapEvent());
        // failed derivations cached before the addons' values are recomputed
        dropEmpty();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                ThaumcraftCraftingManager.getObjectTags(new ItemStack(item));
            }
        }
        Thaumcraft.log.info("Aspect table built in {} ms: {} registered, {} total entries",
            System.currentTimeMillis() - start, explicit, CommonInternals.objectTags.size());
    }
}
