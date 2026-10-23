package thaumcraft.api.golems.parts;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import thaumcraft.api.golems.EnumGolemTrait;

public class GolemLeg {
    protected static GolemLeg[] legs = new GolemLeg[1];
    public byte id;
    public String key;
    public String[] research;
    public ResourceLocation icon;
    public Object[] components;
    public EnumGolemTrait[] traits;
    public GolemLeg.ILegFunction function;
    public PartModel model;
    private static int lastID = 0;

    public GolemLeg(String key, String[] research, ResourceLocation icon, PartModel model, Object[] comp, EnumGolemTrait[] tags) {
        this.key = key;
        this.research = research;
        this.icon = icon;
        this.components = comp;
        this.traits = tags;
        this.model = model;
        this.function = null;
    }

    public GolemLeg(String key, String[] research, ResourceLocation icon, PartModel model, Object[] comp, GolemLeg.ILegFunction function, EnumGolemTrait[] tags) {
        this(key, research, icon, model, comp, tags);
        this.function = function;
    }

    public static void register(GolemLeg thing) {
        if (lastID > 127) throw new IllegalStateException("Golem catalog signed-byte ID bound");
        java.util.Objects.requireNonNull(thing, "Golem part");
        thing.id = (byte)lastID++;
        if (thing.id >= legs.length) {
            GolemLeg[] temp = new GolemLeg[thing.id + 1];
            System.arraycopy(legs, 0, temp, 0, legs.length);
            legs = temp;
        }

        legs[thing.id] = thing;
    }

    public String getLocalizedName() {
        return Component.translatable("golem.leg." + this.key.toLowerCase()).getString();
    }

    public String getLocalizedDescription() {
        return Component.translatable("golem.leg.text." + this.key.toLowerCase()).getString();
    }

    public static GolemLeg[] getLegs() {
        return legs;
    }

    public interface ILegFunction extends IGenericFunction {
    }
}
