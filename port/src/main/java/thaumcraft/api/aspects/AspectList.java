package thaumcraft.api.aspects;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * A mutable set of aspects. If the list is stored in an item's Data Component,
 * {@link #copy()} it before changing it: components must stay immutable.
 */
public class AspectList {
    public LinkedHashMap<Aspect, Integer> aspects = new LinkedHashMap<>();

    public static final Codec<AspectList> CODEC = Codec.unboundedMap(Aspect.CODEC, Codec.INT)
        .xmap(AspectList::fromMap, list -> list.aspects);
    public static final StreamCodec<ByteBuf, AspectList> STREAM_CODEC = ByteBufCodecs
        .map(LinkedHashMap<Aspect, Integer>::new, Aspect.STREAM_CODEC, ByteBufCodecs.VAR_INT)
        .map(AspectList::fromMap, list -> list.aspects);

    public AspectList(ItemStack stack) {
        try {
            AspectList temp = AspectHelper.getObjectAspects(stack);
            if (temp != null) {
                for (Aspect tag : temp.getAspects()) {
                    this.add(tag, temp.getAmount(tag));
                }
            }
        } catch (Exception var7) {
        }
    }

    public AspectList() {
    }

    private static AspectList fromMap(Map<Aspect, Integer> map) {
        AspectList out = new AspectList();
        out.aspects.putAll(map);
        return out;
    }

    public AspectList copy() {
        AspectList out = new AspectList();

        for (Aspect a : this.getAspects()) {
            out.add(a, this.getAmount(a));
        }

        return out;
    }

    public int size() {
        return this.aspects.size();
    }

    public int visSize() {
        int q = 0;

        for (Aspect as : this.aspects.keySet()) {
            q += this.getAmount(as);
        }

        return q;
    }

    public Aspect[] getAspects() {
        return this.aspects.keySet().toArray(new Aspect[0]);
    }

    public Aspect[] getAspectsSortedByName() {
        try {
            Aspect[] out = this.aspects.keySet().toArray(new Aspect[0]);
            boolean change = false;

            do {
                change = false;

                for (int a = 0; a < out.length - 1; a++) {
                    Aspect e1 = out[a];
                    Aspect e2 = out[a + 1];
                    if (e1 != null && e2 != null && e1.getTag().compareTo(e2.getTag()) > 0) {
                        out[a] = e2;
                        out[a + 1] = e1;
                        change = true;
                        break;
                    }
                }
            } while (change);

            return out;
        } catch (Exception e) {
            return this.getAspects();
        }
    }

    public Aspect[] getAspectsSortedByAmount() {
        try {
            Aspect[] out = this.aspects.keySet().toArray(new Aspect[0]);
            boolean change = false;

            do {
                change = false;

                for (int a = 0; a < out.length - 1; a++) {
                    int e1 = this.getAmount(out[a]);
                    int e2 = this.getAmount(out[a + 1]);
                    if (e1 > 0 && e2 > 0 && e2 > e1) {
                        Aspect ea = out[a];
                        Aspect eb = out[a + 1];
                        out[a] = eb;
                        out[a + 1] = ea;
                        change = true;
                        break;
                    }
                }
            } while (change);

            return out;
        } catch (Exception e) {
            return this.getAspects();
        }
    }

    public int getAmount(Aspect key) {
        return this.aspects.get(key) == null ? 0 : this.aspects.get(key);
    }

    public boolean reduce(Aspect key, int amount) {
        if (this.getAmount(key) >= amount) {
            int am = this.getAmount(key) - amount;
            this.aspects.put(key, am);
            return true;
        } else {
            return false;
        }
    }

    public AspectList remove(Aspect key, int amount) {
        int am = this.getAmount(key) - amount;
        if (am <= 0) {
            this.aspects.remove(key);
        } else {
            this.aspects.put(key, am);
        }

        return this;
    }

    public AspectList remove(Aspect key) {
        this.aspects.remove(key);
        return this;
    }

    public AspectList add(Aspect aspect, int amount) {
        if (this.aspects.containsKey(aspect)) {
            int oldamount = this.aspects.get(aspect);
            amount += oldamount;
        }

        this.aspects.put(aspect, amount);
        return this;
    }

    public AspectList merge(Aspect aspect, int amount) {
        if (this.aspects.containsKey(aspect)) {
            int oldamount = this.aspects.get(aspect);
            if (amount < oldamount) {
                amount = oldamount;
            }
        }

        this.aspects.put(aspect, amount);
        return this;
    }

    public AspectList add(AspectList in) {
        for (Aspect a : in.getAspects()) {
            this.add(a, in.getAmount(a));
        }

        return this;
    }

    public AspectList remove(AspectList in) {
        for (Aspect a : in.getAspects()) {
            this.remove(a, in.getAmount(a));
        }

        return this;
    }

    public AspectList merge(AspectList in) {
        for (Aspect a : in.getAspects()) {
            this.merge(a, in.getAmount(a));
        }

        return this;
    }

    public void readFromNBT(CompoundTag nbttagcompound) {
        this.readFromNBT(nbttagcompound, "Aspects");
    }

    public void readFromNBT(CompoundTag nbttagcompound, String label) {
        this.aspects.clear();
        ListTag tlist = nbttagcompound.getList(label, Tag.TAG_COMPOUND);

        for (int j = 0; j < tlist.size(); j++) {
            CompoundTag rs = tlist.getCompound(j);
            // In 1.12.2 an unknown aspect (for example, from a removed addon) was added as a null key; skip it.
            Aspect aspect = rs.contains("key") ? Aspect.getAspect(rs.getString("key")) : null;
            if (aspect != null) {
                this.add(aspect, rs.getInt("amount"));
            }
        }
    }

    public void writeToNBT(CompoundTag nbttagcompound) {
        this.writeToNBT(nbttagcompound, "Aspects");
    }

    public void writeToNBT(CompoundTag nbttagcompound, String label) {
        ListTag tlist = new ListTag();
        nbttagcompound.put(label, tlist);

        for (Aspect aspect : this.getAspects()) {
            if (aspect != null) {
                CompoundTag f = new CompoundTag();
                f.putString("key", aspect.getTag());
                f.putInt("amount", this.getAmount(aspect));
                tlist.add(f);
            }
        }
    }

    // Needed for Data Components: item components are compared through equals/hashCode.
    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof AspectList other && this.aspects.equals(other.aspects);
    }

    @Override
    public int hashCode() {
        return this.aspects.hashCode();
    }

    @Override
    public String toString() {
        return "AspectList" + this.aspects;
    }
}
