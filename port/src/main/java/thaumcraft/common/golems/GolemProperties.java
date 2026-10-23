package thaumcraft.common.golems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.api.golems.parts.GolemAddon;
import thaumcraft.api.golems.parts.GolemArm;
import thaumcraft.api.golems.parts.GolemHead;
import thaumcraft.api.golems.parts.GolemLeg;
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.api.golems.parts.PartModel;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.golems.parts.PartModelBreakers;
import thaumcraft.api.golems.parts.PartModelClaws;
import thaumcraft.api.golems.parts.PartModelDarts;
import thaumcraft.api.golems.parts.PartModelHauler;
import thaumcraft.api.golems.parts.PartModelWheel;
import thaumcraft.common.golems.parts.GolemArmDart;
import thaumcraft.common.golems.parts.GolemLegLevitator;
import thaumcraft.common.golems.parts.GolemLegWheels;

public class GolemProperties implements IGolemProperties {
    private long data = 0L;
    private static boolean initialized;
    public static final com.mojang.serialization.Codec<GolemProperties> CODEC = com.mojang.serialization.Codec.LONG.comapFlatMap(value -> {
        try { return com.mojang.serialization.DataResult.success(fromLong(value)); }
        catch (IllegalArgumentException invalid) { return com.mojang.serialization.DataResult.error(invalid::getMessage); }
    }, GolemProperties::toLong);
    public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf,GolemProperties> STREAM_CODEC =
        net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.map(GolemProperties::fromLong,GolemProperties::toLong);
    private Set<EnumGolemTrait> traitCache = null;

    @Override
    public Set<EnumGolemTrait> getTraits() {
        if (this.traitCache == null) {
            this.traitCache = java.util.EnumSet.noneOf(EnumGolemTrait.class);

            for (EnumGolemTrait trait : this.getMaterial().traits) {
                this.addTraitSmart(trait);
            }

            for (EnumGolemTrait trait : this.getHead().traits) {
                this.addTraitSmart(trait);
            }

            for (EnumGolemTrait trait : this.getArms().traits) {
                this.addTraitSmart(trait);
            }

            for (EnumGolemTrait trait : this.getLegs().traits) {
                this.addTraitSmart(trait);
            }

            for (EnumGolemTrait trait : this.getAddon().traits) {
                this.addTraitSmart(trait);
            }
        }

        return this.traitCache;
    }

    private void addTraitSmart(EnumGolemTrait trait) {
        GolemPropertyRules.addTrait(this.traitCache,trait,value -> value.opposite);
    }

    @Override
    public boolean hasTrait(EnumGolemTrait trait) {
        return this.getTraits().contains(trait);
    }

    @Override
    public void setMaterial(GolemMaterial mat) {
        this.data = GolemPropertyRules.putByte(this.data, 0, mat.id);
        this.traitCache = null;
    }

    @Override
    public GolemMaterial getMaterial() {
        initialize();
        return GolemMaterial.getMaterials()[GolemPropertyRules.byteAt(this.data, 0)];
    }

    @Override
    public void setHead(GolemHead mat) {
        this.data = GolemPropertyRules.putByte(this.data, 1, mat.id);
        this.traitCache = null;
    }

    @Override
    public GolemHead getHead() {
        initialize();
        return GolemHead.getHeads()[GolemPropertyRules.byteAt(this.data, 1)];
    }

    @Override
    public void setArms(GolemArm mat) {
        this.data = GolemPropertyRules.putByte(this.data, 2, mat.id);
        this.traitCache = null;
    }

    @Override
    public GolemArm getArms() {
        initialize();
        return GolemArm.getArms()[GolemPropertyRules.byteAt(this.data, 2)];
    }

    @Override
    public void setLegs(GolemLeg mat) {
        this.data = GolemPropertyRules.putByte(this.data, 3, mat.id);
        this.traitCache = null;
    }

    @Override
    public GolemLeg getLegs() {
        initialize();
        return GolemLeg.getLegs()[GolemPropertyRules.byteAt(this.data, 3)];
    }

    @Override
    public void setAddon(GolemAddon mat) {
        this.data = GolemPropertyRules.putByte(this.data, 4, mat.id);
        this.traitCache = null;
    }

    @Override
    public GolemAddon getAddon() {
        initialize();
        return GolemAddon.getAddons()[GolemPropertyRules.byteAt(this.data, 4)];
    }

    @Override
    public void setRank(int rank) {
        if (rank < 0 || rank > 10) throw new IllegalArgumentException("Golem rank 0..10");
        this.data = GolemPropertyRules.putByte(this.data, 5, rank);
    }

    @Override
    public int getRank() {
        return GolemPropertyRules.byteAt(this.data, 5);
    }

    public static GolemProperties fromLong(long d) {
        initialize();
        if (!isValid(d)) throw new IllegalArgumentException("Invalid packed golem catalog indices/rank");
        GolemProperties out = new GolemProperties();
        out.data = d;
        return out;
    }

    @Override
    public long toLong() {
        return this.data;
    }

    @Override
    public ItemStack[] generateComponents() {
        ArrayList<ItemStack> comps = new ArrayList<>();
        ItemStack base = this.getMaterial().componentBase;
        ItemStack mech = this.getMaterial().componentMechanism;
        addToList(comps, base, 2);
        addToList(comps, mech, 1);
        addToListFromComps(comps, this.getArms().components, this.getMaterial());
        addToListFromComps(comps, this.getLegs().components, this.getMaterial());
        addToListFromComps(comps, this.getHead().components, this.getMaterial());
        addToListFromComps(comps, this.getAddon().components, this.getMaterial());
        return comps.toArray(new ItemStack[0]);
    }

    private static void addToListFromComps(ArrayList<ItemStack> comps, Object[] objs, GolemMaterial mat) {
        for (Object o : objs) {
            if (o instanceof ItemStack) {
                addToList(comps, (ItemStack)o, 1);
            } else if (o instanceof String) {
                String s = (String)o;
                if (s.equalsIgnoreCase("base")) {
                    addToList(comps, mat.componentBase, 1);
                } else if (s.equalsIgnoreCase("mech")) {
                    addToList(comps, mat.componentMechanism, 1);
                }
            }
        }
    }

    private static void addToList(ArrayList<ItemStack> comps, ItemStack newItem, int mult) {
        for (ItemStack stack : comps) {
            if (ItemStack.isSameItemSameComponents(stack, newItem)) {
                stack.grow(newItem.getCount() * mult);
                return;
            }
        }

        ItemStack stack = newItem.copy();
        stack.setCount(stack.getCount() * mult);
        comps.add(stack);
    }

    public static boolean isValid(long packed) {
        initialize(); return GolemPropertyRules.valid(packed,GolemMaterial.getMaterials().length,GolemHead.getHeads().length,GolemArm.getArms().length,GolemLeg.getLegs().length,GolemAddon.getAddons().length);
    }

    /** Root commonSetup hook after actual item/block registration, before add-on catalog additions. */
    public static synchronized void initialize() {
        if (initialized) return;
        if (ItemsTC.mechanismSimple == null || ItemsTC.mindClockwork == null || ItemsTC.moduleVision == null || BlocksTC.levitator == null)
            throw new IllegalStateException("Golem catalog requires real registered components");
        GolemMaterial.register(
            new GolemMaterial(
                "WOOD",
                new String[]{"MATSTUDWOOD"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_wood.png"),
                5059370,
                6,
                2,
                1,
                new ItemStack(BlocksTC.plankGreatwood),
                new ItemStack(ItemsTC.mechanismSimple),
                new EnumGolemTrait[]{EnumGolemTrait.LIGHT}
            )
        );
        GolemMaterial.register(
            new GolemMaterial(
                "IRON",
                new String[]{"MATSTUDIRON"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_iron.png"),
                16777215,
                20,
                8,
                3,
                new ItemStack(ItemsTC.plateIron),
                new ItemStack(ItemsTC.mechanismSimple),
                new EnumGolemTrait[]{EnumGolemTrait.HEAVY, EnumGolemTrait.FIREPROOF, EnumGolemTrait.BLASTPROOF}
            )
        );
        GolemMaterial.register(
            new GolemMaterial(
                "CLAY",
                new String[]{"MATSTUDCLAY"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_clay.png"),
                13071447,
                10,
                4,
                2,
                new ItemStack(Blocks.TERRACOTTA),
                new ItemStack(ItemsTC.mechanismSimple),
                new EnumGolemTrait[]{EnumGolemTrait.FIREPROOF}
            )
        );
        GolemMaterial.register(
            new GolemMaterial(
                "BRASS",
                new String[]{"MATSTUDBRASS"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_brass.png"),
                15638812,
                16,
                6,
                3,
                new ItemStack(ItemsTC.plateBrass),
                new ItemStack(ItemsTC.mechanismSimple),
                new EnumGolemTrait[]{EnumGolemTrait.LIGHT}
            )
        );
        GolemMaterial.register(
            new GolemMaterial(
                "THAUMIUM",
                new String[]{"MATSTUDTHAUMIUM"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_thaumium.png"),
                5257074,
                24,
                10,
                4,
                new ItemStack(ItemsTC.plateThaumium),
                new ItemStack(ItemsTC.mechanismSimple),
                new EnumGolemTrait[]{EnumGolemTrait.HEAVY, EnumGolemTrait.FIREPROOF, EnumGolemTrait.BLASTPROOF}
            )
        );
        GolemMaterial.register(
            new GolemMaterial(
                "VOID",
                new String[]{"MATSTUDVOID"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_void.png"),
                1445161,
                20,
                6,
                4,
                new ItemStack(ItemsTC.plateVoid),
                new ItemStack(ItemsTC.mechanismSimple),
                new EnumGolemTrait[]{EnumGolemTrait.REPAIR}
            )
        );
        GolemHead.register(
            new GolemHead(
                "BASIC",
                new String[]{"MINDCLOCKWORK"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_basic.png"),
                new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_basic.obj"), null, PartModel.EnumAttachPoint.HEAD),
                new Object[]{new ItemStack(ItemsTC.mindClockwork)},
                new EnumGolemTrait[0]
            )
        );
        GolemHead.register(
            new GolemHead(
                "SMART",
                new String[]{"MINDBIOTHAUMIC"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_smart.png"),
                new PartModel(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_smart.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_head_other.png"),
                    PartModel.EnumAttachPoint.HEAD
                ),
                new Object[]{new ItemStack(ItemsTC.mindBiothaumic)},
                new EnumGolemTrait[]{EnumGolemTrait.SMART, EnumGolemTrait.FRAGILE}
            )
        );
        GolemHead.register(
            new GolemHead(
                "SMART_ARMORED",
                new String[]{"MINDBIOTHAUMIC", "GOLEMCOMBATADV"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_smartarmor.png"),
                new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_smart_armor.obj"), null, PartModel.EnumAttachPoint.HEAD),
                new Object[]{new ItemStack(ItemsTC.mindBiothaumic), new ItemStack(ItemsTC.plateBrass), "base", new ItemStack(Blocks.WHITE_WOOL)},
                new EnumGolemTrait[]{EnumGolemTrait.SMART}
            )
        );
        GolemHead.register(
            new GolemHead(
                "SCOUT",
                new String[]{"GOLEMVISION"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_scout.png"),
                new PartModel(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_scout.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_head_other.png"),
                    PartModel.EnumAttachPoint.HEAD
                ),
                new Object[]{new ItemStack(ItemsTC.mindClockwork), new ItemStack(ItemsTC.moduleVision)},
                new EnumGolemTrait[]{EnumGolemTrait.SCOUT, EnumGolemTrait.FRAGILE}
            )
        );
        GolemHead.register(
            new GolemHead(
                "SMART_SCOUT",
                new String[]{"GOLEMVISION", "MINDBIOTHAUMIC"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_smartscout.png"),
                new PartModel(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_scout_smart.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_head_other.png"),
                    PartModel.EnumAttachPoint.HEAD
                ),
                new Object[]{new ItemStack(ItemsTC.mindBiothaumic), new ItemStack(ItemsTC.moduleVision)},
                new EnumGolemTrait[]{EnumGolemTrait.SCOUT, EnumGolemTrait.SMART, EnumGolemTrait.FRAGILE}
            )
        );
        GolemArm.register(
            new GolemArm(
                "BASIC",
                new String[]{"MINDCLOCKWORK"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_basic.png"),
                new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_basic.obj"), null, PartModel.EnumAttachPoint.ARMS),
                new Object[0],
                new EnumGolemTrait[0]
            )
        );
        GolemArm.register(
            new GolemArm(
                "FINE",
                new String[]{"MATSTUDBRASS"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_fine.png"),
                new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_fine.obj"), null, PartModel.EnumAttachPoint.ARMS),
                new Object[]{new ItemStack(ItemsTC.mechanismSimple), "base"},
                new EnumGolemTrait[]{EnumGolemTrait.DEFT, EnumGolemTrait.FRAGILE}
            )
        );
        GolemArm.register(
            new GolemArm(
                "CLAWS",
                new String[]{"GOLEMCOMBATADV"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_claws.png"),
                new PartModelClaws(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_claws.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_arms_claws.png"),
                    PartModel.EnumAttachPoint.ARMS
                ),
                new Object[]{new ItemStack(ItemsTC.moduleAggression), new ItemStack(Items.SHEARS, 2), "base"},
                new EnumGolemTrait[]{EnumGolemTrait.FIGHTER, EnumGolemTrait.CLUMSY, EnumGolemTrait.BRUTAL}
            )
        );
        GolemArm.register(
            new GolemArm(
                "BREAKERS",
                new String[]{"GOLEMBREAKER"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_breakers.png"),
                new PartModelBreakers(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_breakers.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_arms_breakers.png"),
                    PartModel.EnumAttachPoint.ARMS
                ),
                new Object[]{new ItemStack(Items.DIAMOND, 2), "base", new ItemStack(Blocks.PISTON, 2)},
                new EnumGolemTrait[]{EnumGolemTrait.BREAKER, EnumGolemTrait.CLUMSY, EnumGolemTrait.BRUTAL}
            )
        );
        GolemArm.register(
            new GolemArm(
                "DARTS",
                new String[]{"GOLEMCOMBATADV"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_darts.png"),
                new PartModelDarts(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_darter.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_arms_darter.png"),
                    PartModel.EnumAttachPoint.ARMS
                ),
                new Object[]{new ItemStack(ItemsTC.moduleAggression), new ItemStack(Blocks.DISPENSER, 2), new ItemStack(Items.ARROW, 32), "mech"},
                new GolemArmDart(),
                new EnumGolemTrait[]{EnumGolemTrait.FIGHTER, EnumGolemTrait.CLUMSY, EnumGolemTrait.RANGED, EnumGolemTrait.FRAGILE}
            )
        );
        GolemLeg.register(
            new GolemLeg(
                "WALKER",
                new String[]{"MINDCLOCKWORK"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/legs_walker.png"),
                new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_legs_walker.obj"), null, PartModel.EnumAttachPoint.LEGS),
                new Object[]{"base", "mech"},
                new EnumGolemTrait[0]
            )
        );
        GolemLeg.register(
            new GolemLeg(
                "ROLLER",
                new String[]{"MINDCLOCKWORK"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/legs_roller.png"),
                new PartModelWheel(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_legs_wheel.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_legs_wheel.png"),
                    PartModel.EnumAttachPoint.BODY
                ),
                new Object[]{new ItemStack(Items.BOWL, 2), new ItemStack(Items.LEATHER), "mech"},
                new GolemLegWheels(),
                new EnumGolemTrait[]{EnumGolemTrait.WHEELED}
            )
        );
        GolemLeg.register(
            new GolemLeg(
                "CLIMBER",
                new String[]{"GOLEMCLIMBER"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/legs_climber.png"),
                new PartModel(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_legs_climber.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/block/base_metal.png"),
                    PartModel.EnumAttachPoint.LEGS
                ),
                new Object[]{new ItemStack(Items.FLINT, 4), "base", "mech", "mech"},
                new EnumGolemTrait[]{EnumGolemTrait.CLIMBER}
            )
        );
        GolemLeg.register(
            new GolemLeg(
                "FLYER",
                new String[]{"GOLEMFLYER"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/legs_flyer.png"),
                new PartModel(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_legs_floater.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_legs_floater.png"),
                    PartModel.EnumAttachPoint.BODY
                ),
                new Object[]{new ItemStack(BlocksTC.levitator), new ItemStack(ItemsTC.plateBrass, 4), new ItemStack(Items.SLIME_BALL), "mech"},
                new GolemLegLevitator(),
                new EnumGolemTrait[]{EnumGolemTrait.FLYER, EnumGolemTrait.FRAGILE}
            )
        );
        GolemAddon.register(
            new GolemAddon(
                "NONE",
                new String[]{"MINDCLOCKWORK"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/block/blank.png"),
                null,
                new Object[0],
                new EnumGolemTrait[0]
            )
        );
        GolemAddon.register(
            new GolemAddon(
                "ARMORED",
                new String[]{"GOLEMCOMBATADV"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/addon_armored.png"),
                new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_armor.obj"), null, PartModel.EnumAttachPoint.BODY),
                new Object[]{"base", "base", "base", "base"},
                new EnumGolemTrait[]{EnumGolemTrait.ARMORED, EnumGolemTrait.HEAVY}
            )
        );
        GolemAddon.register(
            new GolemAddon(
                "FIGHTER",
                new String[]{"SEALGUARD"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/addon_fighter.png"),
                null,
                new Object[]{new ItemStack(ItemsTC.moduleAggression), "mech"},
                new EnumGolemTrait[]{EnumGolemTrait.FIGHTER}
            )
        );
        GolemAddon.register(
            new GolemAddon(
                "HAULER",
                new String[]{"MINDCLOCKWORK"},
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/addon_hauler.png"),
                new PartModelHauler(
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_hauler.obj"),
                    ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_hauler.png"),
                    PartModel.EnumAttachPoint.BODY
                ),
                new Object[]{new ItemStack(Items.LEATHER), new ItemStack(Blocks.CHEST)},
                new EnumGolemTrait[]{EnumGolemTrait.HAULER}
            )
        );
        initialized = true;
    }
}
