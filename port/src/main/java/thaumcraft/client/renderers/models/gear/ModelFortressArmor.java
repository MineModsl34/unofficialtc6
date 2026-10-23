package thaumcraft.client.renderers.models.gear;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.*;
import thaumcraft.common.items.armor.*;
/** Actual 66 source parts; set ornaments, goggles and three masks follow the worn source tags. */
public final class ModelFortressArmor extends ModelCultistArmor {
    public ModelFortressArmor(ModelPart root){super(root,SourceCultistArmorGeometry.Kind.LEADER);}
    public static LayerDefinition createLayer(boolean inner){return ModelCultistArmor.createLayer(SourceCultistArmorGeometry.loadPath("/assets/thaumcraft/armor_geometry/fortress.json"),inner);}
    @Override public void setupAnim(LivingEntity wearer,float swing,float amount,float age,float yaw,float pitch){
        super.setupAnim(wearer,swing,amount,age,yaw,pitch);int set=0;for(var slot:new EquipmentSlot[]{EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD})if(wearer.getItemBySlot(slot).getItem() instanceof ItemFortressArmor)set++;
        var helm=wearer.getItemBySlot(EquipmentSlot.HEAD);boolean fortress=helm.getItem() instanceof ItemFortressArmor;show(head,"Goggles",fortress&&WearableTags.has(helm,"goggles"));for(int mask=0;mask<3;mask++)show(head,"Mask"+mask,fortress&&WearableTags.has(helm,"mask")&&WearableTags.integer(helm,"mask")==mask);
        show(body,"Scroll",set>=3);show(body,"Book",set>=2);
        for(String part:new String[]{"OrnamentL","OrnamentL2","OrnamentR","OrnamentR2","Gemornament","Gem"})show(head,part,set>=3);
        for(String part:new String[]{"flapL","flapR"})show(head,part,set>=2);
        for(var arm:new ModelPart[]{leftArm,rightArm})for(String part:arm==leftArm?new String[]{"ShoulderplateLtop","ShoulderplateL1","ShoulderplateL2","ShoulderplateL3"}:new String[]{"ShoulderplateRtop","ShoulderplateR1","ShoulderplateR2","ShoulderplateR3"})show(arm,part,part.endsWith("2")||part.endsWith("3")?set>=3:set>=2);
        show(rightLeg,"SidepanelR2",set>=2);show(leftLeg,"SidepanelL2",set>=2);show(rightLeg,"SidepanelR3",set>=3);show(leftLeg,"SidepanelL3",set>=3);
    }
    private static void show(ModelPart parent,String name,boolean visible){if(parent.hasChild(name))parent.getChild(name).visible=visible;}
}
