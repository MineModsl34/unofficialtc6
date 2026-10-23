package thaumcraft.client.renderers.models.entity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact source Wavefront groups, flat normals, flipped V and .0005 UV seam offset. */
public final class ConstructObjMesh {
    public record Vertex(float x,float y,float z,float u,float v){}
    public record Face(List<Vertex> vertices,float nx,float ny,float nz){}
    private final Map<String,List<Face>> groups;
    private ConstructObjMesh(Map<String,List<Face>> groups){this.groups=groups;}
    public Set<String> groups(){return groups.keySet();}public List<Face> faces(String group){return groups.getOrDefault(group,List.of());}
    public static ConstructObjMesh load(){try(var stream=ConstructObjMesh.class.getResourceAsStream("/assets/thaumcraft/models/obj/crossbow_advanced.obj")){if(stream==null)throw new IOException("Missing original turret OBJ");return read(new InputStreamReader(stream,StandardCharsets.UTF_8));}catch(IOException error){throw new IllegalStateException(error);}}
    public static ConstructObjMesh read(Reader input)throws IOException{
        var vertices=new ArrayList<float[]>();var tex=new ArrayList<float[]>();var groups=new LinkedHashMap<String,List<Face>>();String current="Default";int lines=0,faces=0;
        try(var reader=new BufferedReader(input)){String line;while((line=reader.readLine())!=null){if(++lines>40000)throw new IOException("OBJ line bound");line=line.strip();if(line.isEmpty() || line.startsWith("#"))continue;var parts=line.split("\\s+");switch(parts[0]){
            case "v"->{if(vertices.size()>=16384 || parts.length<4)throw new IOException("OBJ vertex bound");vertices.add(new float[]{value(parts[1]),value(parts[2]),value(parts[3])});}
            case "vt"->{if(tex.size()>=16384 || parts.length<3)throw new IOException("OBJ texture bound");tex.add(new float[]{value(parts[1]),1F-value(parts[2])});}
            case "g","o"->{if(parts.length!=2 || groups.size()>64)throw new IOException("OBJ group");current=parts[1];groups.computeIfAbsent(current,key->new ArrayList<>());}
            case "f"->{if(++faces>16384 || parts.length<4 || parts.length>5)throw new IOException("OBJ face bound");var xyz=new ArrayList<float[]>();var uv=new ArrayList<float[]>();float u=0,v=0;
                for(int i=1;i<parts.length;i++){var fields=parts[i].split("/");var point=vertices.get(index(fields[0],vertices.size()));var coord=fields.length>1&&!fields[1].isEmpty()?tex.get(index(fields[1],tex.size())):new float[]{0,0};xyz.add(point);uv.add(coord);u+=coord[0];v+=coord[1];}u/=xyz.size();v/=xyz.size();
                double ax=xyz.get(1)[0]-xyz.get(0)[0],ay=xyz.get(1)[1]-xyz.get(0)[1],az=xyz.get(1)[2]-xyz.get(0)[2],bx=xyz.get(2)[0]-xyz.get(0)[0],by=xyz.get(2)[1]-xyz.get(0)[1],bz=xyz.get(2)[2]-xyz.get(0)[2];double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,length=Math.sqrt(nx*nx+ny*ny+nz*nz);if(length<1E-4){nx=ny=nz=0;}else{nx/=length;ny/=length;nz/=length;}
                var faceVertices=new ArrayList<Vertex>();for(int i=0;i<xyz.size();i++){var point=xyz.get(i);var coord=uv.get(i);faceVertices.add(new Vertex(point[0],point[1],point[2],coord[0]+(coord[0]>u?-.0005F:.0005F),coord[1]+(coord[1]>v?-.0005F:.0005F)));}groups.computeIfAbsent(current,key->new ArrayList<>()).add(new Face(List.copyOf(faceVertices),(float)nx,(float)ny,(float)nz));
            }
            default->{} // Source mtllib/usemtl/smoothing/vn do not affect flat-face renderer.
        }}}
        var immutable=new LinkedHashMap<String,List<Face>>();groups.forEach((key,value)->immutable.put(key,List.copyOf(value)));return new ConstructObjMesh(Collections.unmodifiableMap(immutable));
    }
    private static int index(String value,int count)throws IOException{int parsed=Integer.parseInt(value);int index=parsed<0?count+parsed:parsed-1;if(index<0 || index>=count)throw new IOException("OBJ index outside source data");return index;}
    private static float value(String value)throws IOException{float result=Float.parseFloat(value);if(!Float.isFinite(result))throw new IOException("Nonfinite OBJ number");return result;}
}
