package thaumcraft.api.aspects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class AspectTest {
    @Test
    void registryMatchesThaumcraft6() {
        assertEquals(37, Aspect.aspects.size());
        assertEquals(6, Aspect.getPrimalAspects().size());
        assertEquals(31, Aspect.getCompoundAspects().size());
        assertEquals("Praecantatio", Aspect.MAGIC.getName());
        assertEquals("thaumcraft:textures/aspects/ignis.png", Aspect.FIRE.getImage().toString());
    }

    @Test
    void combinationIsOrderIndependent() {
        assertSame(Aspect.MIND, AspectHelper.getCombinationResult(Aspect.FIRE, Aspect.SOUL));
        assertSame(Aspect.MIND, AspectHelper.getCombinationResult(Aspect.SOUL, Aspect.FIRE));
    }

    @Test
    void reduceToPrimalsExpandsRecursively() {
        // cognitio = ignis + spiritus; spiritus = victus + mortuus; victus = terra + aqua; mortuus = aqua + perditio
        AspectList primals = AspectHelper.reduceToPrimals(new AspectList().add(Aspect.MIND, 3));
        assertEquals(3, primals.getAmount(Aspect.FIRE));
        assertEquals(3, primals.getAmount(Aspect.EARTH));
        assertEquals(6, primals.getAmount(Aspect.WATER));
        assertEquals(3, primals.getAmount(Aspect.ENTROPY));
        assertEquals(4, primals.size());
    }

    @Test
    void cullTagsDropsCheapestAspects() {
        AspectList list = new AspectList();
        Aspect[] all = Aspect.aspects.values().toArray(new Aspect[0]);
        for (int i = 0; i < 10; i++) {
            list.add(all[i], i + 1);
        }

        AspectList culled = AspectHelper.cullTags(list);
        assertEquals(7, culled.size());
        assertEquals(0, culled.getAmount(all[0]));
    }

    @Test
    void sortingMatchesOriginal() {
        AspectList list = new AspectList().add(Aspect.WATER, 1).add(Aspect.AIR, 5).add(Aspect.FIRE, 3);
        assertArrayEquals(new Aspect[]{Aspect.AIR, Aspect.WATER, Aspect.FIRE}, list.getAspectsSortedByName());
        assertArrayEquals(new Aspect[]{Aspect.AIR, Aspect.FIRE, Aspect.WATER}, list.getAspectsSortedByAmount());
    }

    @Test
    void nbtRoundTripUsesThaumcraft6Format() {
        AspectList list = new AspectList().add(Aspect.MAGIC, 8).add(Aspect.FIRE, 2);
        CompoundTag tag = new CompoundTag();
        list.writeToNBT(tag);

        ListTag raw = tag.getList("Aspects", Tag.TAG_COMPOUND);
        assertEquals("praecantatio", raw.getCompound(0).getString("key"));
        assertEquals(8, raw.getCompound(0).getInt("amount"));

        // An unknown aspect (a removed addon) must be skipped rather than become a null key
        CompoundTag unknown = new CompoundTag();
        unknown.putString("key", "nonexistent");
        unknown.putInt("amount", 5);
        raw.add(unknown);

        AspectList read = new AspectList();
        read.readFromNBT(tag);
        assertEquals(list, read);
    }

    @Test
    void codecRoundTrip() {
        AspectList list = new AspectList().add(Aspect.ELDRITCH, 4).add(Aspect.AIR, 1);
        Tag encoded = AspectList.CODEC.encodeStart(NbtOps.INSTANCE, list).getOrThrow();
        AspectList decoded = AspectList.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(list, decoded);
    }

    @Test
    void streamCodecRoundTripKeepsOrder() {
        AspectList list = new AspectList().add(Aspect.VOID, 7).add(Aspect.LIGHT, 2).add(Aspect.MAN, 9);
        ByteBuf buf = Unpooled.buffer();
        AspectList.STREAM_CODEC.encode(buf, list);
        AspectList decoded = AspectList.STREAM_CODEC.decode(buf);
        assertEquals(list, decoded);
        assertArrayEquals(list.getAspects(), decoded.getAspects());
    }
}
