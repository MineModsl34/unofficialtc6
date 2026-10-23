package thaumcraft.common.world.aura;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The aura of one chunk. In TC6 it was stored in the chunk NBT (tag "Thaumcraft": base/vis/flux) and duplicated in AuraWorld;
 * here it is a chunk Data Attachment (thaumcraft:aura), and AuraWorld only indexes loaded chunks.
 */
public class AuraChunk {
    public static final Codec<AuraChunk> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.SHORT.fieldOf("base").forGetter(AuraChunk::getBase),
        Codec.FLOAT.fieldOf("vis").forGetter(AuraChunk::getVis),
        Codec.FLOAT.fieldOf("flux").forGetter(AuraChunk::getFlux)
    ).apply(i, AuraChunk::new));

    private short base;
    private float vis;
    private float flux;
    /** The "data set" flag: an empty attachment is created by default on getData and is not generated yet. */
    private boolean generated;
    LevelChunk chunk;
    ChunkPos loc;

    public AuraChunk() {
    }

    public AuraChunk(short base, float vis, float flux) {
        this.base = base;
        this.vis = vis;
        this.flux = flux;
        this.generated = true;
    }

    public boolean isGenerated() {
        return this.generated;
    }

    void set(short base, float vis, float flux) {
        this.base = base;
        this.vis = vis;
        this.flux = flux;
        this.generated = true;
        this.markDirty();
    }

    private void markDirty() {
        if (this.chunk != null) {
            this.chunk.setUnsaved(true);
        }
    }

    public short getBase() {
        return this.base;
    }

    public void setBase(short base) {
        this.base = base;
        this.markDirty();
    }

    public float getVis() {
        return this.vis;
    }

    public void setVis(float vis) {
        this.vis = Math.min(32766.0F, Math.max(0.0F, vis));
        this.markDirty();
    }

    public float getFlux() {
        return this.flux;
    }

    public void setFlux(float flux) {
        this.flux = Math.min(32766.0F, Math.max(0.0F, flux));
        this.markDirty();
    }

    public ChunkPos getLoc() {
        return this.loc;
    }
}
