package thaumcraft.client.renderers.models.entity;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstructGeometryTest {
    @Test void basicHasExactlyTwentySourceCubesAndFourteenCrossbowChildren(){var model=SourceConstructGeometry.load("crossbow");assertEquals(64,model.width());assertEquals(32,model.height());assertEquals(20,model.parts().size());assertEquals(14,model.parts().stream().filter(p->"crossbow".equals(p.parent())).count());var body=model.parts().stream().filter(p->p.name().equals("crossbow")).findFirst().orElseThrow();assertArrayEquals(new float[]{-2,0,-7,4,2,14},body.box());assertArrayEquals(new float[]{0,10,0},body.pivot());assertEquals(28,body.u());assertEquals(14,body.v());}
    @Test void boreHasElevenCubesFiveBaseChildrenAndOriginalTipRotation(){var model=SourceConstructGeometry.load("arcane_bore");assertEquals(11,model.parts().size());assertEquals(5,model.parts().stream().filter(p->"base".equals(p.parent())).count());var tip=model.parts().stream().filter(p->p.name().equals("tip")).findFirst().orElseThrow();assertArrayEquals(new float[]{0,-3,-6},tip.pivot());assertEquals(-1.570796F,tip.rotation()[0]);}
    @Test void sourceMirrorAssignmentAfterAddBoxDoesNotRetroactivelyMirrorUvs(){for(var name:new String[]{"crossbow","arcane_bore"})for(var part:SourceConstructGeometry.load(name).parts())assertFalse(part.mirror(),name+":"+part.name());}
    @Test void advancedUsesAllEightActualObjGroupsAnd834OriginalTriangles(){var mesh=ConstructObjMesh.load();assertEquals(Set.of("bow1","bow2","mech","box","shield","legs","loader","brain"),mesh.groups());assertEquals(834,mesh.groups().stream().mapToInt(g->mesh.faces(g).size()).sum());for(var group:mesh.groups())for(var face:mesh.faces(group)){assertEquals(3,face.vertices().size());assertTrue(Float.isFinite(face.nx()));assertTrue(Float.isFinite(face.ny()));assertTrue(Float.isFinite(face.nz()));}}
    @Test void wavefrontRetainsFlippedVAndSourceUvInset() throws Exception {var mesh=ConstructObjMesh.read(new java.io.StringReader("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\ng test\nf 1/1 2/2 3/3\n"));var face=mesh.faces("test").getFirst();assertEquals(.0005F,face.vertices().getFirst().u());assertEquals(.9995F,face.vertices().getFirst().v());assertEquals(0,face.nx());assertEquals(0,face.ny());assertEquals(1,face.nz());}
}
