package thaumcraft.common.golems.tasks;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.seals.SealHandler;

/** Logical-server task queues. The original distance/priority ordering and expiry callbacks are retained. */
public class TaskHandler {
    static final int TASK_LIMIT = 10000;
    public static final ConcurrentHashMap<ResourceKey<Level>, ConcurrentHashMap<Integer, Task>> tasks = new ConcurrentHashMap<>();

    public static void addTask(ResourceKey<Level> dimension, Task ticket) {
        ConcurrentHashMap<Integer, Task> queue = getTasks(dimension);
        // Source checks BEFORE insertion, so the retained upper bound is 10001.
        if (queue.size() > TASK_LIMIT) {
            var iterator = queue.values().iterator();
            if (iterator.hasNext()) queue.remove(iterator.next().getId());
        }
        queue.put(ticket.getId(), ticket);
    }
    public static Task getTask(ResourceKey<Level> dimension, int id) { return getTasks(dimension).get(id); }
    public static ConcurrentHashMap<Integer, Task> getTasks(ResourceKey<Level> dimension) {
        return tasks.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>());
    }
    public static ArrayList<Task> getBlockTasksSorted(ResourceKey<Level> dimension, UUID uuid, Entity golem) {
        return sorted(dimension, uuid, golem, (byte) 0);
    }
    public static ArrayList<Task> getEntityTasksSorted(ResourceKey<Level> dimension, UUID uuid, Entity golem) {
        return sorted(dimension, uuid, golem, (byte) 1);
    }
    private static ArrayList<Task> sorted(ResourceKey<Level> dimension, UUID uuid, Entity golem, byte type) {
        ArrayList<Task> output = new ArrayList<>();
        for (Task ticket : getTasks(dimension).values()) {
            if (ticket.isReserved() || ticket.getType() != type
                    || uuid != null && ticket.getGolemUUID() != null && !uuid.equals(ticket.getGolemUUID())) continue;
            if (type == 1 && (ticket.getEntity() == null || !ticket.getEntity().isAlive())) {
                ticket.setSuspended(true);
                continue;
            }
            double score = score(ticket, golem);
            int index = 0;
            while (index < output.size() && score >= score(output.get(index), golem)) index++;
            output.add(index, ticket);
        }
        return output;
    }
    private static double score(Task ticket, Entity golem) {
        return TaskRules.selectionScore(ticket.getPos().distToCenterSqr(golem.position()), ticket.getPriority());
    }
    public static void completeTask(Task task, EntityThaumcraftGolem golem) {
        if (task.isCompleted() || task.isSuspended()) return;
        ISealEntity seal = SealHandler.getSealEntity(golem.level().dimension(), task.getSealPos());
        task.setCompletion(seal == null || seal.getSeal().onTaskCompletion(golem.level(), golem, task));
    }
    public static void clearSuspendedOrExpiredTasks(Level world) {
        if (world.isClientSide) return;
        ConcurrentHashMap<Integer, Task> retained = new ConcurrentHashMap<>();
        for (Task ticket : getTasks(world.dimension()).values()) {
            if (!ticket.isSuspended() && ticket.getLifespan() > 0) {
                ticket.setLifespan((short) (ticket.getLifespan() - 1));
                retained.put(ticket.getId(), ticket);
            } else {
                ISealEntity seal = SealHandler.getSealEntity(world.dimension(), ticket.getSealPos());
                if (seal != null) seal.getSeal().onTaskSuspension(world, ticket);
            }
        }
        tasks.put(world.dimension(), retained);
    }
}
