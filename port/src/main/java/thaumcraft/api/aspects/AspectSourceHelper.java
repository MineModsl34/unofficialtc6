package thaumcraft.api.aspects;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

public class AspectSourceHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    static Method drainEssentia;
    static Method findEssentia;

    public static boolean drainEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range) {
        try {
            if (drainEssentia == null) {
                Class<?> fake = Class.forName("thaumcraft.common.lib.events.EssentiaHandler");
                drainEssentia = fake.getMethod("drainEssentia", BlockEntity.class, Aspect.class, Direction.class, int.class);
            }

            return (Boolean)drainEssentia.invoke(null, tile, aspect, direction, range);
        } catch (Exception ex) {
            LOGGER.warn("[Thaumcraft API] Could not invoke thaumcraft.common.lib.events.EssentiaHandler method drainEssentia");
            return false;
        }
    }

    public static boolean findEssentia(BlockEntity tile, Aspect aspect, Direction direction, int range) {
        try {
            if (findEssentia == null) {
                Class<?> fake = Class.forName("thaumcraft.common.lib.events.EssentiaHandler");
                findEssentia = fake.getMethod("findEssentia", BlockEntity.class, Aspect.class, Direction.class, int.class);
            }

            return (Boolean)findEssentia.invoke(null, tile, aspect, direction, range);
        } catch (Exception ex) {
            LOGGER.warn("[Thaumcraft API] Could not invoke thaumcraft.common.lib.events.EssentiaHandler method findEssentia");
            return false;
        }
    }
}
