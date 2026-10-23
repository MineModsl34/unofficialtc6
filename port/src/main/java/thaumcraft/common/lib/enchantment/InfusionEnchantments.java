package thaumcraft.common.lib.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Immutable, bounded replacement for the original ordered infench [{id:short,lvl:short}] list.
 * Preserves duplicate entries and raw signed short levels; lookup/update uses the FIRST id as TC6.
 */
public record InfusionEnchantments(List<Entry> entries) {
    public static final int MAX_ENTRIES = 256;
    public static final InfusionEnchantments EMPTY = new InfusionEnchantments(List.of());
    public record Entry(int id, int level) {
        public Entry {
            if (id < 0 || id >= EnumInfusionEnchantment.values().length || level < Short.MIN_VALUE || level > Short.MAX_VALUE)
                throw new IllegalArgumentException("Invalid infusion enchantment entry");
        }
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 12).fieldOf("id").forGetter(Entry::id),
            Codec.intRange(Short.MIN_VALUE, Short.MAX_VALUE).fieldOf("lvl").forGetter(Entry::level)
        ).apply(instance, Entry::new));
    }
    public InfusionEnchantments { if (entries == null || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many infusion enchantments"); entries = List.copyOf(entries); }
    public static final Codec<InfusionEnchantments> CODEC = Entry.CODEC.listOf(0, MAX_ENTRIES).xmap(InfusionEnchantments::new, InfusionEnchantments::entries);
    public static final StreamCodec<ByteBuf, InfusionEnchantments> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
        FriendlyByteBuf byteBuf = new FriendlyByteBuf(buffer);
        byteBuf.writeVarInt(value.entries.size());
        for (Entry entry : value.entries) { byteBuf.writeByte(entry.id); byteBuf.writeShort(entry.level); }
    }, buffer -> {
        FriendlyByteBuf byteBuf = new FriendlyByteBuf(buffer);
        int count = byteBuf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid infusion enchantment count");
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) entries.add(new Entry(byteBuf.readUnsignedByte(), byteBuf.readShort()));
        return new InfusionEnchantments(entries);
    });
    public int level(EnumInfusionEnchantment enchantment) {
        for (Entry entry : entries) if (entry.id == enchantment.ordinal()) return entry.level;
        return 0;
    }
    public InfusionEnchantments add(EnumInfusionEnchantment enchantment, int level) {
        if (level > enchantment.maxLevel || level < Short.MIN_VALUE) return this;
        List<Entry> updated = new ArrayList<>(entries);
        for (int index = 0; index < updated.size(); index++) if (updated.get(index).id == enchantment.ordinal()) {
            if (level <= updated.get(index).level) return this;
            updated.set(index, new Entry(enchantment.ordinal(), level)); return new InfusionEnchantments(updated);
        }
        if (updated.size() >= MAX_ENTRIES) return this;
        updated.add(new Entry(enchantment.ordinal(), level)); return new InfusionEnchantments(updated);
    }
}
