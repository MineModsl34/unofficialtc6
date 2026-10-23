package thaumcraft.client.renderers.models.gear;
import net.minecraft.client.model.geom.ModelPart;
import thaumcraft.common.items.armor.SourceCultistArmorGeometry.Kind;
/** Preserved source mesh, not implicit registration of future Praetor items/entities. */
public final class ModelLeaderArmor extends ModelCultistArmor {
    public ModelLeaderArmor(ModelPart root) { super(root, Kind.LEADER); }
}
