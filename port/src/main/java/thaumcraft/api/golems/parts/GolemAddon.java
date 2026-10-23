package thaumcraft.api.golems.parts;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import thaumcraft.api.golems.EnumGolemTrait;

public class GolemAddon {
    protected static GolemAddon[] addons = new GolemAddon[1];
    public byte id;
    public String key;
    public String[] research;
    public ResourceLocation icon;
    public Object[] components;
    public EnumGolemTrait[] traits;
    public GolemAddon.IAddonFunction function;
    public PartModel model;
    private static int lastID = 0;

    public GolemAddon(String key, String[] research, ResourceLocation icon, PartModel model, Object[] comp, EnumGolemTrait[] tags) {
        this.key = key;
        this.research = research;
        this.icon = icon;
        this.components = comp;
        this.traits = tags;
        this.model = model;
        this.function = null;
    }

    public GolemAddon(
        String key, String[] research, ResourceLocation icon, PartModel model, Object[] comp, GolemAddon.IAddonFunction function, EnumGolemTrait[] tags
    ) {
        this(key, research, icon, model, comp, tags);
        this.function = function;
    }

    public static void register(GolemAddon thing) {
        if (lastID > 127) throw new IllegalStateException("Golem catalog signed-byte ID bound");
        java.util.Objects.requireNonNull(thing, "Golem part");
        thing.id = (byte)lastID++;
        if (thing.id >= addons.length) {
            GolemAddon[] temp = new GolemAddon[thing.id + 1];
            System.arraycopy(addons, 0, temp, 0, addons.length);
            addons = temp;
        }

        addons[thing.id] = thing;
    }

    public String getLocalizedName() {
        return Component.translatable("golem.addon." + this.key.toLowerCase()).getString();
    }

    public String getLocalizedDescription() {
        return Component.translatable("golem.addon.text." + this.key.toLowerCase()).getString();
    }

    public static GolemAddon[] getAddons() {
        return addons;
    }

    public interface IAddonFunction extends IGenericFunction {
    }
}
