package thaumcraft.client.renderers.models.entity;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
/** Native ModelPart construction preserves each old ModelRenderer's own texture dimensions. */
public final class SourceBossGeometry {
    public record Rig(ModelPart root,Map<String,ModelPart> parts){}
    public static final class Builder {
        private final Map<String,ModelPart> parts=new LinkedHashMap<>();private final Map<String,Map<String,ModelPart>> children=new HashMap<>();private final Set<String> nested=new HashSet<>();
        public void part(String name,int u,int v,float x,float y,float z,float width,float height,float depth,float pivotX,float pivotY,float pivotZ,float rotX,float rotY,float rotZ,boolean mirror,int textureWidth,int textureHeight){Map<String,ModelPart> child=new LinkedHashMap<>();children.put(name,child);ModelPart part=new ModelPart(List.of(new ModelPart.Cube(u,v,x,y,z,width,height,depth,0,0,0,mirror,textureWidth,textureHeight,EnumSet.allOf(Direction.class))),child);part.setPos(pivotX,pivotY,pivotZ);part.setRotation(rotX,rotY,rotZ);parts.put(name,part);}
        public void empty(String name){Map<String,ModelPart> child=new LinkedHashMap<>();children.put(name,child);parts.put(name,new ModelPart(List.of(),child));}
        public void child(String parent,String child){children.get(parent).put(child,parts.get(child));nested.add(child);}
        public Rig build(){Map<String,ModelPart> roots=new LinkedHashMap<>();parts.forEach((name,part)->{if(!nested.contains(name))roots.put(name,part);});return new Rig(new ModelPart(List.of(),roots),Map.copyOf(parts));}
    }
}
