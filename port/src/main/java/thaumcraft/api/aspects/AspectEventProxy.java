package thaumcraft.api.aspects;

import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.internal.CommonInternals;

/**
 * In 1.12.2 the string overloads took an Ore Dictionary name ("ingotIron", "ore*").
 * In 1.21 this is an item tag id ("c:ingots/iron", "c:ores/*"). Tags are available only after
 * datapacks load, so register aspects by tag no earlier than TagsUpdatedEvent.
 */
public class AspectEventProxy {
    public void registerObjectTag(ItemStack item, AspectList aspects) {
        if (aspects == null) {
            aspects = new AspectList();
        }

        try {
            int id = CommonInternals.generateUniqueItemstackId(item);
            CommonInternals.objectTags.put(id, aspects);
            CommonInternals.objectTagStacks.put(id, item.copyWithCount(1));
            CommonInternals.generatedTags.remove(id);
        } catch (Exception var4) {
        }
    }

    public void registerObjectTag(String itemTag, AspectList aspects) {
        if (aspects == null) {
            aspects = new AspectList();
        }

        for (ItemStack ore : ThaumcraftApiHelper.getOresWithWildCards(itemTag)) {
            try {
                this.registerObjectTag(ore.copyWithCount(1), aspects.copy());
            } catch (Exception var7) {
            }
        }
    }

    public void registerObjectTag(TagKey<Item> itemTag, AspectList aspects) {
        this.registerObjectTag(itemTag.location().toString(), aspects);
    }

    /**
     * TC6 had a check {@code if (!ThaumcraftApi.exists(item))} here, with a merge branch for already
     * registered items. But because of a bug exists() always returned false, and the merge branch
     * (itself buggy: a merge with its own values) never ran.
     * Keep TC6's actual behaviour so that aspect values match the original.
     */
    public void registerComplexObjectTag(ItemStack item, AspectList aspects) {
        AspectList tmp = AspectHelper.generateTags(item);
        if (tmp != null && tmp.size() > 0) {
            for (Aspect tag : tmp.getAspects()) {
                aspects.add(tag, tmp.getAmount(tag));
            }
        }

        this.registerObjectTag(item, aspects);
    }

    public void registerComplexObjectTag(String itemTag, AspectList aspects) {
        if (aspects == null) {
            aspects = new AspectList();
        }

        List<ItemStack> ores = ThaumcraftApiHelper.getOresWithWildCards(itemTag);
        for (ItemStack ore : ores) {
            try {
                this.registerComplexObjectTag(ore.copyWithCount(1), aspects.copy());
            } catch (Exception var7) {
            }
        }
    }

    public void registerComplexObjectTag(TagKey<Item> itemTag, AspectList aspects) {
        this.registerComplexObjectTag(itemTag.location().toString(), aspects);
    }
}
