package thaumcraft.api.golems.parts;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemAPI;

public class GolemArm {
    protected static GolemArm[] arms = new GolemArm[1];
    public byte id;
    public String key;
    public String[] research;
    public ResourceLocation icon;
    public Object[] components;
    public EnumGolemTrait[] traits;
    public GolemArm.IArmFunction function;
    public PartModel model;
    private static int lastID = 0;

    public GolemArm(String key, String[] research, ResourceLocation icon, PartModel model, Object[] comp, EnumGolemTrait[] tags) {
        this.key = key;
        this.research = research;
        this.icon = icon;
        this.components = comp;
        this.traits = tags;
        this.model = model;
        this.function = null;
    }

    public GolemArm(String key, String[] research, ResourceLocation icon, PartModel model, Object[] comp, GolemArm.IArmFunction function, EnumGolemTrait[] tags) {
        this(key, research, icon, model, comp, tags);
        this.function = function;
    }

    public static void register(GolemArm thing) {
        if (lastID > 127) throw new IllegalStateException("Golem catalog signed-byte ID bound");
        java.util.Objects.requireNonNull(thing, "Golem part");
        thing.id = (byte)lastID++;
        if (thing.id >= arms.length) {
            GolemArm[] temp = new GolemArm[thing.id + 1];
            System.arraycopy(arms, 0, temp, 0, arms.length);
            arms = temp;
        }

        arms[thing.id] = thing;
    }

    public String getLocalizedName() {
        return Component.translatable("golem.arm." + this.key.toLowerCase()).getString();
    }

    public String getLocalizedDescription() {
        return Component.translatable("golem.arm.text." + this.key.toLowerCase()).getString();
    }

    public static GolemArm[] getArms() {
        return arms;
    }

    public interface IArmFunction extends IGenericFunction {
        void onMeleeAttack(IGolemAPI var1, Entity var2);

        void onRangedAttack(IGolemAPI var1, LivingEntity var2, float var3);

        Goal getRangedAttackAI(RangedAttackMob var1);
    }
}
