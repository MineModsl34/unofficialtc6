package thaumcraft.client.fx;

/** Pure source keyframe/atlas and engine-budget rules, shared by production and bounded tests. */
public final class FxAnimationRules {
    private FxAnimationRules() {}
    public static float[] frames(int duration,float[] keys) {
        if(keys==null||keys.length==0)keys=new float[]{1};
        if(duration<=0)return new float[]{keys[0]};
        float[] frames=new float[duration+1];float increment=(keys.length-1)/(float)duration,position=0;
        for(int age=0;age<=duration;age++) {
            int index=Math.min(keys.length-1,(int)Math.floor(position));
            float difference=index<keys.length-1?keys[index+1]-keys[index]:0;
            frames[age]=keys[index]+difference*(position-index);position+=increment;
        }
        return frames;
    }
    public static int textureFrame(int age,int duration,int start,int count,int increment,boolean loop,int[] finalFrames) {
        count=Math.max(1,count);increment=Math.max(1,increment);
        int frame=loop?start+age/increment%count:(int)(start+Math.min(count*(age/(float)Math.max(1,duration)),count-1));
        if(finalFrames!=null&&finalFrames.length>0&&age>duration-finalFrames.length)frame=finalFrames[Math.max(0,Math.min(finalFrames.length-1,duration-age))];
        return Math.max(0,frame);
    }
    public static int particleLimit(int setting) { return setting==2?500:setting==1?1000:2000; }
    public static boolean admit(int randomThree,int setting,int fps) { return randomThree>=setting+(fps<30?1:0); }
}
