package thaumcraft.client.renderers.models.entity;

import java.util.*;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public final class ConstructLayers {
    private ConstructLayers(){}
    public static LayerDefinition source(String name){var source=SourceConstructGeometry.load(name);var mesh=new MeshDefinition();var pending=new ArrayList<>(source.parts());Map<String,PartDefinition> parents=new HashMap<>();
        while(!pending.isEmpty()){int old=pending.size();for(var iterator=pending.iterator();iterator.hasNext();){var part=iterator.next();if(part.parent()!=null && !parents.containsKey(part.parent()))continue;var parent=part.parent()==null?mesh.getRoot():parents.get(part.parent());var b=part.box();var p=part.pivot();var r=part.rotation();parents.put(part.name(),parent.addOrReplaceChild(part.name(),CubeListBuilder.create().texOffs(part.u(),part.v()).mirror(part.mirror()).addBox(b[0],b[1],b[2],b[3],b[4],b[5]),PartPose.offsetAndRotation(p[0],p[1],p[2],r[0],r[1],r[2])));iterator.remove();}if(pending.size()==old)throw new IllegalArgumentException("Cyclic source model hierarchy");}return LayerDefinition.create(mesh,source.width(),source.height());}
    /** Source advanced geometry is an OBJ, so these native parts are transform nodes only. */
    public static LayerDefinition advanced(){var mesh=new MeshDefinition();for(String group:List.of("legs","mech","box","shield","brain","loader","bow1","bow2"))mesh.getRoot().addOrReplaceChild(group,CubeListBuilder.create(),PartPose.ZERO);return LayerDefinition.create(mesh,64,32);}
}
