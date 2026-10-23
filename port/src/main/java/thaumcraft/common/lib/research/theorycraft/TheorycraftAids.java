package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftManager;
import thaumcraft.common.lib.research.theorycraft.SourceTheorycraftCard.Kind;

/** Original 14 aid registrations, with late registry lookup rather than invented later-stage blocks. */
public final class TheorycraftAids {
    private TheorycraftAids() {}
    public static void register() {
        register("thaumcraft.api.research.theorycraft.AidBookshelf", "minecraft:bookshelf", false, false,
            Kind.BALANCE, Kind.NOTATION, Kind.NOTATION, Kind.STUDY, Kind.STUDY, Kind.STUDY);
        register("AidBrainInAJar", "thaumcraft:jar_brain", false, false, Kind.DARK_WHISPERS);
        register("AidGlyphedStone", "thaumcraft:stone_ancient_glyphed", true, false, Kind.GLYPHS);
        register("AidPortal$AidPortalEnd", "minecraft:end_portal", false, false, Kind.PORTAL);
        register("AidPortal$AidPortalNether", "minecraft:nether_portal", false, false, Kind.PORTAL);
        register("AidPortal$AidPortalCrimson", "thaumcraft:cultist_portal_lesser", false, true, Kind.PORTAL);
        register("AidBasicAlchemy", "thaumcraft:crucible", false, false, Kind.CONCENTRATE, Kind.REACTIONS, Kind.SYNTHESIS);
        register("AidBasicArtifice", "thaumcraft:arcane_workbench", false, false, Kind.CALIBRATE, Kind.TINKER, Kind.MIND_OVER_MATTER);
        register("AidBasicInfusion", "thaumcraft:infusion_matrix", false, false, Kind.MEASURE, Kind.CHANNEL, Kind.INFUSE);
        register("AidBasicAuromancy", "thaumcraft:wand_workbench", false, false, Kind.FOCUS, Kind.AWARENESS, Kind.SPELLBINDING);
        register("AidBasicGolemancy", "thaumcraft:golem_builder", false, false, Kind.SCULPTING, Kind.SCRIPTING, Kind.SYNERGY);
        register("AidBasicEldritch", "thaumcraft:eldritch", false, false, Kind.REALIZATION, Kind.REVELATION, Kind.TRUTH);
        register("AidEnchantmentTable", "minecraft:enchanting_table", true, false, Kind.ENCHANTMENT);
        register("AidBeacon", "minecraft:beacon", true, false, Kind.BEACON);
    }
    private static void register(String name, String id, boolean item, boolean entity, Kind... cards) {
        String key = name.startsWith("thaumcraft.") ? name : "thaumcraft.common.lib.research.theorycraft." + name;
        String[] keys = new String[cards.length]; for (int i = 0; i < cards.length; i++) keys[i] = cards[i].sourceKey();
        TheorycraftManager.registerAid(key, new Aid(ResourceLocation.parse(id), item, entity, keys));
    }
    public record Aid(ResourceLocation id, boolean item, boolean entity, String[] keys) implements ITheorycraftAid {
        public Aid { keys = keys.clone(); }
        @Override public String[] getCardKeys() { return keys.clone(); }
        @Override public Object getAidObject() {
            if (entity) return BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.get(id) : null;
            if (!BuiltInRegistries.BLOCK.containsKey(id)) return null;
            Block block = BuiltInRegistries.BLOCK.get(id); if (block == Blocks.AIR) return null;
            return item ? new ItemStack(block) : block;
        }
        @Override public String translationKey() { return "block." + id.getNamespace() + "." + id.getPath(); }
        public Component displayName() { Object object = getAidObject(); return object instanceof ItemStack stack ? stack.getHoverName() : object instanceof Block block ? block.getName() : Component.translatable("entity." + id.getNamespace() + "." + id.getPath()); }
        public boolean matchesEntity(Entity value) { return entity && BuiltInRegistries.ENTITY_TYPE.getKey(value.getType()).equals(id); }
    }
}
