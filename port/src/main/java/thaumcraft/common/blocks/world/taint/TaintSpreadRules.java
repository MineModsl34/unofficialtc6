package thaumcraft.common.blocks.world.taint;

/** Pure source predicates shared by taint mechanics; all source taint-material members are equivalent here. */
public final class TaintSpreadRules {
    private TaintSpreadRules() {}
    public static boolean isNonTaintSupport(boolean air, boolean taintMaterial, boolean solidFace) {
        return !air && !taintMaterial && solidFace;
    }
}
