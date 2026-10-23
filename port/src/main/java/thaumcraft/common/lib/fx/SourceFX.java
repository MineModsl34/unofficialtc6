package thaumcraft.common.lib.fx;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.common.lib.network.fx.PacketSourceFX;

/** Sided presentation boundary: common/server code never resolves a client particle class.
 * S2C messages describe effects only; no target, payment or damage authority flows back. */
public final class SourceFX {
    private static Consumer<PacketSourceFX> client = ignored -> {};
    private static Consumer<Player> scan = ignored -> {};
    private SourceFX() {}
    public static void install(Consumer<PacketSourceFX> effects, Consumer<Player> scanRunes) {
        client = Objects.requireNonNull(effects); scan = Objects.requireNonNull(scanRunes);
    }
    public static void receive(PacketSourceFX packet) { client.accept(packet); }
    public static void scanRunes(Player player) { if (player.level().isClientSide) scan.accept(player); }
    public static void emit(Level level, PacketSourceFX packet) {
        emit(level,packet,32);
    }
    public static void emit(Level level,PacketSourceFX packet,double radius) {
        emit(level,packet,radius,new Vec3(packet.x(),packet.y(),packet.z()));
    }
    /** Source audience centre can differ from payload coordinates (Break uses hitVec/block centre). */
    public static void emit(Level level,PacketSourceFX packet,double radius,Vec3 audience) {
        if (level.isClientSide) client.accept(packet);
        else if (level instanceof ServerLevel server)
            PacketDistributor.sendToPlayersNear(server, null, audience.x, audience.y, audience.z, Math.max(1,Math.min(64,radius)), packet);
    }
    public static void shield(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.damagesource.DamageSource damage){
        int target=damage.getEntity()==null?-1:damage.getEntity().getId();
        if(damage.is(net.minecraft.world.damagesource.DamageTypes.FALL))target=-2;
        if(damage.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK))target=-3;
        emit(entity.level(),event(PacketSourceFX.Kind.SHIELD,entity.position(),Vec3.ZERO,entity.getId(),target,0,-1,""));
    }
    public static void pollute(Level level, BlockPos pos, float amount) {
        int count = (byte)(amount > 0 && amount < 1 ? 1 : amount); // Source signed-byte wrap is deliberate.
        emit(level, event(PacketSourceFX.Kind.POLLUTION, Vec3.atLowerCornerOf(pos), Vec3.ZERO, 0, count, 0, -1, ""));
    }
    public static void bamf(Level level, Vec3 pos, int color, boolean sound, boolean flair, Direction face) {
        emit(level, event(PacketSourceFX.Kind.BAMF, pos, Vec3.ZERO, color, 0,
            (sound ? 1 : 0) | (flair ? 2 : 0), face == null ? -1 : face.ordinal(), ""));
    }
    public static void essentia(Level level, BlockPos target, BlockPos source, int color, int extend) {
        // TC6 wire uses signed byte offsets and a signed short extension.
        BlockPos end = target.offset((byte)(source.getX()-target.getX()), (byte)(source.getY()-target.getY()), (byte)(source.getZ()-target.getZ()));
        emit(level, event(PacketSourceFX.Kind.ESSENTIA, Vec3.atLowerCornerOf(target), Vec3.atLowerCornerOf(end), color, (short)extend, 0, -1, ""));
    }
    public static void infusion(Level level, BlockPos matrix, BlockPos source, int color) {
        emit(level, event(PacketSourceFX.Kind.INFUSION, Vec3.atLowerCornerOf(matrix), Vec3.atLowerCornerOf(source), color, 0, 0, -1, ""));
    }
    public static void arc(Level level, BlockPos source, Vec3 target, int color) {
        emit(level, event(PacketSourceFX.Kind.ARC, Vec3.atCenterOf(source), floats(target), color, 0, 0, -1, ""));
    }
    public static void focus(Level level, String key, Vec3 pos, Vec3 velocity, PacketSourceFX.Kind kind) {
        // Legacy focus packets send coordinates and motion as floats.
        emit(level, event(kind, kind==PacketSourceFX.Kind.FOCUS_PARTICLE?pos:floats(pos), kind==PacketSourceFX.Kind.FOCUS_PARTICLE?velocity:floats(velocity), 0, 0, 0, -1, key));
    }
    /** Client-local source animation keys; this does not send animation ticks over the network. */
    public static void local(Level level, String key, Vec3 pos, Vec3 motion, int color, int extra) {
        if (level.isClientSide) client.accept(event(PacketSourceFX.Kind.LOCAL, pos, motion, color, extra, 0, -1, key));
    }
    public static PacketSourceFX event(PacketSourceFX.Kind kind, Vec3 pos, Vec3 motion, int color, int extra, int flags, int face, String key) {
        return new PacketSourceFX(kind, pos.x, pos.y, pos.z, motion.x, motion.y, motion.z, color, extra, flags, face, key);
    }
    private static Vec3 floats(Vec3 v) { return new Vec3((float)v.x, (float)v.y, (float)v.z); }
}
