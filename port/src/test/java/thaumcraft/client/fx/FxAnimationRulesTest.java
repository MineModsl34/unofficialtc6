package thaumcraft.client.fx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FxAnimationRulesTest {
    @Test void sourceConstantRandomRangesDoNotConsumeRandomness() {
        var random=net.minecraft.util.RandomSource.create(2306L);
        var control=net.minecraft.util.RandomSource.create(2306L);
        assertEquals(255,FxMath.getInt(random,255,255));
        assertEquals(9,FxMath.getInt(random,9,3));
        assertEquals(control.nextLong(),random.nextLong());
        assertEquals(189+control.nextInt(67),FxMath.getInt(random,189,255));
        assertEquals(control.nextLong(),random.nextLong());
    }
    @Test void sourceUniformKeyframesIncludeBothEndpointsAndPlateaus() {
        assertArrayEquals(new float[]{0,.5F,1,1,1,.5F,0},FxAnimationRules.frames(6,new float[]{0,1,1,0}),.00001F);
        assertArrayEquals(new float[]{.75F,.75F,.75F,.75F},FxAnimationRules.frames(3,new float[]{.75F}),.00001F);
    }
    @Test void sourceLoopUsesTickIncrementAndDoesNotStretchToLifetime() {
        assertEquals(320,FxAnimationRules.textureFrame(0,80,320,16,2,true,null));
        assertEquals(321,FxAnimationRules.textureFrame(3,80,320,16,2,true,null));
        assertEquals(320,FxAnimationRules.textureFrame(32,80,320,16,2,true,null));
    }
    @Test void sourceNonLoopAndReversedFinalFramesAreDistinct() {
        assertEquals(208,FxAnimationRules.textureFrame(0,31,208,31,1,false,null));
        assertEquals(238,FxAnimationRules.textureFrame(31,31,208,31,1,false,null));
        assertEquals(64,FxAnimationRules.textureFrame(7,10,64,1,1,false,new int[]{65,66,67}));
        assertEquals(67,FxAnimationRules.textureFrame(8,10,64,1,1,false,new int[]{65,66,67}));
        assertEquals(66,FxAnimationRules.textureFrame(9,10,64,1,1,false,new int[]{65,66,67}));
        assertEquals(65,FxAnimationRules.textureFrame(10,10,64,1,1,false,new int[]{65,66,67}));
    }
    @Test void sourceParticleSettingAndSubThirtyFpsThinningPreserveBoundaries() {
        assertEquals(2000,FxAnimationRules.particleLimit(0));assertEquals(1000,FxAnimationRules.particleLimit(1));assertEquals(500,FxAnimationRules.particleLimit(2));
        assertTrue(FxAnimationRules.admit(0,0,30));assertFalse(FxAnimationRules.admit(0,0,29));
        assertTrue(FxAnimationRules.admit(2,2,30));assertFalse(FxAnimationRules.admit(2,2,29));
    }
    @Test void nativeInvalidLifecycleGuardsKeepTheAtlasFinite() {
        assertArrayEquals(new float[]{1},FxAnimationRules.frames(0,null));
        assertEquals(0,FxAnimationRules.textureFrame(0,0,-1,0,0,true,null));
        assertEquals(65,FxAnimationRules.textureFrame(20,10,64,1,1,false,new int[]{65,66}));
    }
}
