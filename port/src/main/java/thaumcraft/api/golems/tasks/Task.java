package thaumcraft.api.golems.tasks;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.ProvisionRequest;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.common.golems.tasks.TaskRules;

/** BETA26 task lifecycle, including the original short lifespan arithmetic. */
public class Task {
    private UUID golemUUID;
    private final int id;
    private final byte type;
    private final SealPos sealPos;
    private final BlockPos pos;
    private final Entity entity;
    private boolean reserved;
    private boolean suspended;
    private boolean completed;
    private int data;
    private ProvisionRequest linkedProvision;
    private short lifespan = 300;
    private byte priority;

    public Task(SealPos sealPos, BlockPos pos) {
        this.sealPos = sealPos;
        this.pos = pos.immutable();
        entity = null;
        id = (System.currentTimeMillis() + (sealPos == null ? "/BNPOS/" : "/B/" + sealPos.face + "/" + sealPos.pos + "/") + pos).hashCode();
        type = 0;
    }
    public Task(SealPos sealPos, Entity entity) {
        this.sealPos = sealPos;
        this.entity = entity;
        pos = null;
        id = (System.currentTimeMillis() + (sealPos == null ? "/ENPOS/" : "/E/" + sealPos.face + "/" + sealPos.pos + "/") + entity.getId()).hashCode();
        type = 1;
    }

    public byte getPriority() { return priority; }
    public void setPriority(byte priority) { this.priority = priority; }
    public boolean isCompleted() { return completed; }
    public void setCompletion(boolean fulfilled) { completed = fulfilled; lifespan = TaskRules.completionLifespan(lifespan); }
    public UUID getGolemUUID() { return golemUUID; }
    public void setGolemUUID(UUID golemUUID) { this.golemUUID = golemUUID; }
    public BlockPos getPos() { return type == 1 ? entity.blockPosition() : pos; }
    public byte getType() { return type; }
    public Entity getEntity() { return entity; }
    public int getId() { return id; }
    public boolean isReserved() { return reserved; }
    public void setReserved(boolean reserved) { this.reserved = reserved; lifespan = TaskRules.reservationLifespan(lifespan); }
    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { setLinkedProvision(null); this.suspended = suspended; }
    public SealPos getSealPos() { return sealPos; }
    public long getLifespan() { return lifespan; }
    public void setLifespan(short lifespan) { this.lifespan = lifespan; }
    public int getData() { return data; }
    public void setData(int data) { this.data = data; }
    public ProvisionRequest getLinkedProvision() { return linkedProvision; }
    public void setLinkedProvision(ProvisionRequest linkedProvision) { this.linkedProvision = linkedProvision; }

    public boolean canGolemPerformTask(IGolemAPI golem) {
        ISealEntity seal = GolemHelper.getSealEntity(golem.getGolemWorld().dimension(), sealPos);
        return seal == null || (golem.getGolemColor() <= 0 || seal.getColor() <= 0 || golem.getGolemColor() == seal.getColor())
                && seal.getSeal().canGolemPerformTask(golem, this);
    }
    @Override public boolean equals(Object other) { return other instanceof Task task && task.id == id; }
    @Override public int hashCode() { return id; }
}
