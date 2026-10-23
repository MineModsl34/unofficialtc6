package thaumcraft.common.entities;
import java.util.function.Consumer;
/** Logical-side safe bridge: client installs real presentation without server loading client classes. */
public final class RiftPresentationHooks {
    public static Consumer<EntityFluxRift> ambient = rift -> {};
    public static Consumer<EntityFluxRift> collapse = rift -> {};
    public static java.util.function.BiConsumer<thaumcraft.common.tiles.crafting.TileVoidSiphon,Integer> siphon = (tile,seed) -> {};
    private RiftPresentationHooks() {}
}
