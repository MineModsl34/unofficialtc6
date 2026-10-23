package thaumcraft.api.research;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import thaumcraft.api.capabilities.IPlayerKnowledge;

/** TC6's cancellable pre-mutation events; immutable arguments cannot change the requested award. */
public class ResearchEvent extends Event implements ICancellableEvent {
    private final Player player;

    public ResearchEvent(Player player) { this.player = player; }

    public Player getPlayer() { return player; }

    public static class Knowledge extends ResearchEvent {
        private final IPlayerKnowledge.EnumKnowledgeType type;
        private final ResearchCategory category;
        private final int amount;

        public Knowledge(Player player, IPlayerKnowledge.EnumKnowledgeType type, ResearchCategory category, int amount) {
            super(player);
            this.type = type;
            this.category = category;
            this.amount = amount;
        }

        public IPlayerKnowledge.EnumKnowledgeType getType() { return type; }
        public ResearchCategory getCategory() { return category; }
        public int getAmount() { return amount; }
    }

    public static class Research extends ResearchEvent {
        private final String researchKey;

        public Research(Player player, String researchKey) {
            super(player);
            this.researchKey = researchKey;
        }

        public String getResearchKey() { return researchKey; }
    }
}
