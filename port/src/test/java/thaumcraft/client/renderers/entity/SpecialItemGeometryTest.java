package thaumcraft.client.renderers.entity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class SpecialItemGeometryTest {
    @Test void fiveFastAndTenFancyFansRemainSource(){assertEquals(5,SpecialItemGeometry.rays(false));assertEquals(10,SpecialItemGeometry.rays(true));}
    @Test void glowGrowthCapsAtAgeTen(){assertEquals(0,SpecialItemGeometry.growth(0));assertEquals(1/300F,SpecialItemGeometry.growth(1),.00000001F);assertEquals(1/30F,SpecialItemGeometry.growth(10));assertEquals(SpecialItemGeometry.growth(10),SpecialItemGeometry.growth(1000));}
}
