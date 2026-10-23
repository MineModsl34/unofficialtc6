package thaumcraft.api.golems.seals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.Objects;
/** Dimension belongs to the owning world index; source position and face identify one seal. */
public final class SealPos {
    public final BlockPos pos; public final Direction face;
    public SealPos(BlockPos pos,Direction face){this.pos=Objects.requireNonNull(pos).immutable();this.face=Objects.requireNonNull(face);}
    @Override public int hashCode(){int i=31*(face.ordinal()+1)+pos.getX();i=31*i+pos.getY();return 31*i+pos.getZ();}
    @Override public boolean equals(Object other){return other instanceof SealPos s && pos.equals(s.pos) && face==s.face;}
}
