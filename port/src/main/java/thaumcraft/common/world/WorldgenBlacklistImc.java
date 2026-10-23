package thaumcraft.common.world;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import thaumcraft.Thaumcraft;

/** TC6 blacklist IMC names retained; numeric-ID payloads become namespace:path=mode. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class WorldgenBlacklistImc {
    public static final String DIMENSION_BLACKLIST = "dimensionBlacklist";
    public static final String BIOME_BLACKLIST = "biomeBlacklist";

    private WorldgenBlacklistImc() {
    }

    @SubscribeEvent
    public static void process(InterModProcessEvent event) {
        event.enqueueWork(() -> event.getIMCStream(WorldgenBlacklistImc::isBlacklistMethod)
            .forEach(message -> processMessage(message, entry -> {
                if (entry.dimension()) {
                    WorldgenBlacklists.registerDimension(entry.key(), entry.mode());
                } else {
                    WorldgenBlacklists.registerBiome(entry.key(), entry.mode());
                }
            })));
    }

    static boolean isBlacklistMethod(String method) {
        return DIMENSION_BLACKLIST.equals(method) || BIOME_BLACKLIST.equals(method);
    }

    /** Pure parser; unknown/deferred registry keys are allowed because datapack biomes load after IMC. */
    static Optional<Entry> parseMessage(String method, Object payload) {
        if (!isBlacklistMethod(method) || !WorldgenPolicy.isValidEntry(payload)) {
            return Optional.empty();
        }
        String text = (String) payload;
        int separator = text.indexOf('=');
        ResourceLocation key = ResourceLocation.parse(text.substring(0, separator).trim());
        int mode = Integer.parseInt(text.substring(separator + 1).trim());
        return Optional.of(new Entry(DIMENSION_BLACKLIST.equals(method), key, mode));
    }

    /** Failure is isolated per message; unsupported IMC never evaluates its supplier. */
    static boolean processMessage(InterModComms.IMCMessage message, Consumer<Entry> receiver) {
        if (!isBlacklistMethod(message.method())) {
            return false;
        }
        try {
            var entry = parseMessage(message.method(), message.messageSupplier().get());
            if (entry.isEmpty()) {
                Thaumcraft.log.warn("Ignoring invalid {} IMC from {}: expected namespace:path=mode string",
                    message.method(), message.senderModId());
                return false;
            }
            receiver.accept(entry.get());
            return true;
        } catch (Exception failure) {
            Thaumcraft.log.warn("Ignoring failed {} IMC from {}", message.method(), message.senderModId(), failure);
            return false;
        }
    }

    record Entry(boolean dimension, ResourceLocation key, int mode) {
    }
}
