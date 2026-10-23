package thaumcraft.api.golems.parts;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import thaumcraft.api.golems.EnumGolemTrait;

public class GolemMaterial {
    protected static GolemMaterial[] materials = new GolemMaterial[1];
    public byte id;
    public String key;
    public String[] research;
    public ResourceLocation texture;
    public int itemColor;
    public int healthMod;
    public int armor;
    public int damage;
    public ItemStack componentBase;
    public ItemStack componentMechanism;
    public EnumGolemTrait[] traits;
    private static int lastID = 0;

    public GolemMaterial(
        String key,
        String[] research,
        ResourceLocation texture,
        int itemColor,
        int hp,
        int armor,
        int damage,
        ItemStack compb,
        ItemStack compm,
        EnumGolemTrait[] tags
    ) {
        this.key = key;
        this.research = research;
        this.texture = texture;
        this.itemColor = itemColor;
        this.componentBase = compb;
        this.componentMechanism = compm;
        this.healthMod = hp;
        this.armor = armor;
        this.traits = tags;
        this.damage = damage;
    }

    public static void register(GolemMaterial thing) {
        if (lastID > 127) throw new IllegalStateException("Golem catalog signed-byte ID bound");
        java.util.Objects.requireNonNull(thing, "Golem part");
        thing.id = (byte)lastID++;
        if (thing.id >= materials.length) {
            GolemMaterial[] temp = new GolemMaterial[thing.id + 1];
            System.arraycopy(materials, 0, temp, 0, materials.length);
            materials = temp;
        }

        materials[thing.id] = thing;
    }

    public String getLocalizedName() {
        return Component.translatable("golem.material." + this.key.toLowerCase()).getString();
    }

    public String getLocalizedDescription() {
        return Component.translatable("golem.material.text." + this.key.toLowerCase()).getString();
    }

    public static GolemMaterial[] getMaterials() {
        return materials;
    }
}
