package thaumcraft.common.entities;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.projectile.EntityBottleTaint;
import thaumcraft.common.entities.projectile.EntityCausalityCollapser;

public final class Mob8MiscRegistries {
    public static EntityType<EntityFallingTaint> FALLING_TAINT;
    public static EntityType<EntityBottleTaint> BOTTLE_TAINT;
    public static EntityType<EntityCausalityCollapser> CAUSALITY_COLLAPSER;
    private Mob8MiscRegistries() {}
    public static Map<String,EntityType<?>> createEntityTypes() {
        var out=new LinkedHashMap<String,EntityType<?>>();
        out.put("falling_taint",FALLING_TAINT=EntityType.Builder.<EntityFallingTaint>of(EntityFallingTaint::new,MobCategory.MISC).sized(.98F,.98F).clientTrackingRange(4).updateInterval(3).setShouldReceiveVelocityUpdates(true).build("thaumcraft:falling_taint"));
        out.put("bottle_taint",BOTTLE_TAINT=EntityType.Builder.<EntityBottleTaint>of(EntityBottleTaint::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(4).updateInterval(20).build("thaumcraft:bottle_taint"));
        out.put("causality_collapser",CAUSALITY_COLLAPSER=EntityType.Builder.<EntityCausalityCollapser>of(EntityCausalityCollapser::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(4).updateInterval(20).build("thaumcraft:causality_collapser"));
        return out;
    }
    public static Map<String,Item> createItems() {return Map.of(
        "bottle_taint",ItemsTC.bottleTaint=new thaumcraft.common.items.consumables.ItemBottleTaint(),
        "causality_collapser",ItemsTC.causalityCollapser=new thaumcraft.common.items.consumables.ItemCausalityCollapser());}
}
