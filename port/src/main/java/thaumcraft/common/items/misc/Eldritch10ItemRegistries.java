package thaumcraft.common.items.misc;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.items.consumables.ItemSanitySoap;

/** The two remaining actual BETA26 item implementations, not invented Outer Lands equipment. */
public final class Eldritch10ItemRegistries {
    public static ItemSanitySoap SOAP;
    public static ItemCreativeFluxSponge SPONGE;
    private Eldritch10ItemRegistries() {}

    public static Map<String, Item> createItems() {
        Map<String, Item> items = new LinkedHashMap<>();
        items.put("sanity_soap", SOAP = new ItemSanitySoap());
        items.put("creative_flux_sponge", SPONGE = new ItemCreativeFluxSponge());
        ConfigItems.SIMPLE_ITEM_TEXTURES.put(SOAP, "sanity_soap");
        ConfigItems.SIMPLE_ITEM_TEXTURES.put(SPONGE, "flux_sponge");
        return items;
    }
}
