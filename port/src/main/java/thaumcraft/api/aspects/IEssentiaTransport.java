package thaumcraft.api.aspects;

import net.minecraft.core.Direction;

public interface IEssentiaTransport {
    boolean isConnectable(Direction var1);

    boolean canInputFrom(Direction var1);

    boolean canOutputTo(Direction var1);

    void setSuction(Aspect var1, int var2);

    Aspect getSuctionType(Direction var1);

    int getSuctionAmount(Direction var1);

    int takeEssentia(Aspect var1, int var2, Direction var3);

    int addEssentia(Aspect var1, int var2, Direction var3);

    Aspect getEssentiaType(Direction var1);

    int getEssentiaAmount(Direction var1);

    int getMinimumSuction();
}
