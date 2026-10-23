package thaumcraft.api.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftApiHelper;

public class ScanEntity implements IScanThing {
    String research;
    Class<?> entityClass;
    ThaumcraftApi.EntityTagsNBT[] NBTData;
    boolean inheritedClasses;

    public ScanEntity(String research, Class<?> entityClass, boolean inheritedClasses) {
        this.research = research;
        this.entityClass = entityClass;
        this.inheritedClasses = inheritedClasses;
    }

    public ScanEntity(String research, Class<?> entityClass, boolean inheritedClasses, ThaumcraftApi.EntityTagsNBT... nbt) {
        this(research, entityClass, inheritedClasses);
        this.NBTData = nbt;
    }

    @Override
    public boolean checkThing(Player player, Object object) {
        if (object != null && (!inheritedClasses && entityClass == object.getClass()
            || inheritedClasses && entityClass.isInstance(object))) {
            if (NBTData != null && NBTData.length > 0) {
                CompoundTag tag = ((Entity) object).saveWithoutId(new CompoundTag());
                for (ThaumcraftApi.EntityTagsNBT nbt : NBTData) {
                    if (!tag.contains(nbt.name)
                        || !ThaumcraftApiHelper.getNBTDataFromId(tag, tag.getTagType(nbt.name), nbt.name).equals(nbt.value)) return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public String getResearchKey(Player player, Object object) { return research; }
}
