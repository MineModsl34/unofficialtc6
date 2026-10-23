package thaumcraft.client.renderers.models.entity;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure source-data reader; coordinates and UVs are copied, not replaced with vanilla models. */
public final class SourceConstructGeometry {
    public record Part(String name,String parent,int u,int v,float[] box,float[] pivot,float[] rotation,boolean mirror){}
    public record Model(int width,int height,List<Part> parts){}
    private SourceConstructGeometry(){}
    public static Model load(String name){try(var stream=SourceConstructGeometry.class.getResourceAsStream("/assets/thaumcraft/construct_geometry/constructs.json")){if(stream==null)throw new IOException("Missing source construct geometry");return read(new InputStreamReader(stream,StandardCharsets.UTF_8),name);}catch(IOException error){throw new IllegalStateException(error);}}
    public static Model read(Reader reader,String name){
        var root=JsonParser.parseReader(reader).getAsJsonObject();if(root.get("format").getAsInt()!=1)throw new IllegalArgumentException("Construct geometry format");
        for(var row:root.getAsJsonArray("models")){var model=row.getAsJsonObject();if(!model.get("name").getAsString().equals(name))continue;
            var texture=model.getAsJsonArray("texture");var parts=new ArrayList<Part>();var names=new HashSet<String>();
            for(var value:model.getAsJsonArray("parts")){var part=value.getAsJsonObject();String id=part.get("name").getAsString(),parent=part.get("parent").isJsonNull()?null:part.get("parent").getAsString();if(!names.add(id) || parts.size()>=64)throw new IllegalArgumentException("Duplicate/oversized geometry");var uv=part.getAsJsonArray("uv");parts.add(new Part(id,parent,uv.get(0).getAsInt(),uv.get(1).getAsInt(),floats(part,"box",6),floats(part,"pivot",3),floats(part,"rotation",3),part.get("mirror").getAsBoolean()));}
            for(var part:parts)if(part.parent()!=null && !names.contains(part.parent()))throw new IllegalArgumentException("Missing parent");return new Model(texture.get(0).getAsInt(),texture.get(1).getAsInt(),List.copyOf(parts));
        }throw new IllegalArgumentException("Unknown construct geometry: "+name);
    }
    private static float[] floats(JsonObject object,String name,int count){var values=object.getAsJsonArray(name);if(values.size()!=count)throw new IllegalArgumentException("Geometry vector length");float[] result=new float[count];for(int i=0;i<count;i++){result[i]=values.get(i).getAsFloat();if(!Float.isFinite(result[i]))throw new IllegalArgumentException("Nonfinite geometry");}return result;}
}
