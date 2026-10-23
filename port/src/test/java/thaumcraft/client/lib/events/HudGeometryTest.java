package thaumcraft.client.lib.events;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Registry-free checks of actual HUD arithmetic, not rendered visual acceptance. */
public class HudGeometryTest {
    @Test void auraUses525ClampAndSharedOverflowNormalization(){var bars=HudGeometry.aura(525,525,525);assertEquals(.5F,bars.base());assertEquals(.5F,bars.vis());assertEquals(.5F,bars.flux());var small=HudGeometry.aura(250,100,50);assertEquals(250/525F,small.base());assertEquals(100/525F,small.vis());assertEquals(50/525F,small.flux());}
    @Test void auraDoesNotNormalizeWhenSumIsExactlyOne(){var bars=HudGeometry.aura(525,525,0);assertEquals(1,bars.base());assertEquals(1,bars.vis());assertEquals(0,bars.flux());assertEquals(0,HudGeometry.aura(-10,0,0).base());}
    @Test void sourceZeroAndFullChargeGrayscaleAreIdenticalForInlayAndStabilizer(){assertEquals(0xff4c4c4c,HudGeometry.chargeColor(0));assertEquals(0xff888888,HudGeometry.chargeColor(1));assertEquals(0xffffffff,HudGeometry.chargeColor(15));}
    @Test void shiftInvertsConfiguredTooltipTags(){assertFalse(HudGeometry.showTags(false,false));assertTrue(HudGeometry.showTags(true,false));assertTrue(HudGeometry.showTags(false,true));assertFalse(HudGeometry.showTags(true,true));}
    @Test void tagScaleHasSourceAsymptoteAndLimit(){assertEquals(.031F,HudGeometry.tagScale(0,.3F));assertEquals(.3F,HudGeometry.tagScale(.3F,.3F));assertEquals(.301F,HudGeometry.tagScale(.3F,.5F),.000001F);}
    @Test void knowledgeBeginsAtZeroSizeAndEndsAtSourceBook(){var start=HudGeometry.knowledge(50,50,0,150,100);assertEquals(0,start.size());assertEquals(150,start.xDistance());assertEquals(100,start.yDistance());var end=HudGeometry.knowledge(0,50,0,150,100);assertEquals(0,end.size());assertEquals(0,end.xDistance(),.000001F);assertEquals(0,end.yDistance(),.000001F);}
    @Test void knowledgeUsesTheExactPoint66Branch(){int max=50,progress=20;float partial=.25F,q=(progress-partial)/(max*.66F),m=(float)Math.sin(q*Math.PI-Math.PI/2)*.5F+.5F;var pose=HudGeometry.knowledge(progress,max,partial,140,90);assertEquals(16*q,pose.size());assertEquals(90*m,pose.yDistance());assertEquals(140*(float)Math.sin(m*Math.PI*.5),pose.xDistance());}
}
