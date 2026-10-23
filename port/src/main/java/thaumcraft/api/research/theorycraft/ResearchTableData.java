package thaumcraft.api.research.theorycraft;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Source theory state, source NBT names and RNG calls. UUID is an additional server ownership guard. */
public final class ResearchTableData {
    public final TileResearchTable table;
    public String player = "";
    public UUID owner;
    public int inspiration, inspirationStart, bonusDraws, placedCards, aidsChosen, penaltyStart;
    public final ArrayList<Long> savedCards = new ArrayList<>();
    public final ArrayList<String> aidCards = new ArrayList<>();
    public final TreeMap<String, Integer> categoryTotals = new TreeMap<>();
    public final ArrayList<String> categoriesBlocked = new ArrayList<>();
    public final ArrayList<CardChoice> cardChoices = new ArrayList<>();
    public CardChoice lastDraw;
    public ResearchTableData(TileResearchTable table) { this.table = table; }
    public ResearchTableData(Player player, TileResearchTable table) { this(table); this.player = player.getName().getString(); owner = player.getUUID(); }
    public boolean isComplete() { return inspiration <= 0; }
    public boolean ownedBy(Player value) { return owner != null && owner.equals(value.getUUID()); }
    public boolean hasTotal(String category) { return categoryTotals.containsKey(category); }
    public int getTotal(String category) { return categoryTotals.getOrDefault(category, 0); }
    public void addTotal(String category, int amount) {
        int current = getTotal(category) + amount;
        if (current <= 0) categoryTotals.remove(category); else categoryTotals.put(category, current);
    }
    public void addInspiration(int amount) { inspiration += amount; if (inspiration > inspirationStart) inspiration = inspirationStart; }
    public void initialize(Player player, Set<String> aids) {
        inspirationStart = getAvailableInspiration(player);
        inspiration = inspirationStart - aids.size();
        for (String key : aids) {
            ITheorycraftAid aid = TheorycraftManager.aids.get(key);
            if (aid != null) for (String card : aid.getCardKeys()) aidCards.add(card);
        }
    }
    public ArrayList<String> getAvailableCategories(Player player) {
        ArrayList<String> result = new ArrayList<>();
        for (String key : ResearchCategories.researchCategories.keySet()) {
            ResearchCategory category = ResearchCategories.getResearchCategory(key);
            if (category != null && !categoriesBlocked.contains(key)
                && (category.researchKey == null || ThaumcraftCapabilities.knowsResearchStrict(player, category.researchKey))) result.add(key);
        }
        return result;
    }
    public static int getAvailableInspiration(Player player) {
        float result = 5.0F;
        for (String key : ThaumcraftCapabilities.getKnowledge(player).getResearchList()) {
            if (ThaumcraftCapabilities.knowsResearchStrict(player, key)) {
                ResearchEntry entry = ResearchCategories.getResearch(key);
                if (entry != null) {
                    if (entry.hasMeta(ResearchEntry.EnumResearchMeta.SPIKY)) result += .5F;
                    if (entry.hasMeta(ResearchEntry.EnumResearchMeta.HIDDEN)) result += .1F;
                }
            }
        }
        return Math.min(15, Math.round(result));
    }
    public void drawCards(int draw, Player player) {
        if (draw == 3) { if (bonusDraws > 0) bonusDraws--; else draw = 2; }
        cardChoices.clear();
        this.player = player.getName().getString();
        ArrayList<String> available = getAvailableCategories(player), drawn = new ArrayList<>();
        // Source never updates aidDrawn: more than one aid card can occur in the same draw.
        int failsafe = 0;
        while (draw > 0 && failsafe++ < 10000) {
            String key;
            TheorycraftCard card;
            boolean aid = !aidCards.isEmpty() && player.getRandom().nextFloat() <= .25;
            if (aid) {
                int index = player.getRandom().nextInt(aidCards.size());
                key = aidCards.get(index);
                card = generateCard(key, player);
                if (card == null || card.getInspirationCost() > inspiration || categoriesBlocked.contains(card.getResearchCategory()) || drawn.contains(key)) continue;
                aidCards.remove(index);
            } else {
                String[] keys = TheorycraftManager.cards.keySet().toArray(new String[0]);
                if (keys.length == 0) break;
                key = keys[player.getRandom().nextInt(keys.length)];
                card = generateCard(key, player);
                if (card == null || card.isAidOnly() || card.getInspirationCost() > inspiration
                    || (card.getResearchCategory() != null && !available.contains(card.getResearchCategory())) || drawn.contains(key)) continue;
            }
            drawn.add(key);
            cardChoices.add(new CardChoice(key, card, aid, false));
            draw--;
        }
    }
    private TheorycraftCard generateCard(String key, Player player) {
        var supplier = TheorycraftManager.cards.get(key);
        if (supplier == null) return null;
        try {
            TheorycraftCard card = supplier.get();
            card.bindRegistries(player.registryAccess());
            card.setSeed(player.getRandom().nextLong());
            return card.initialize(player, this) ? card : null;
        } catch (RuntimeException exception) { return null; }
    }
    public CompoundTag serialize(HolderLookup.Provider registries) {
        CompoundTag result = new CompoundTag();
        result.putString("player", player);
        if (owner != null) result.putUUID("owner", owner);
        result.putInt("inspiration", inspiration); result.putInt("inspirationStart", inspirationStart);
        result.putInt("placedCards", placedCards); result.putInt("bonusDraws", bonusDraws);
        result.putInt("aidsChosen", aidsChosen); result.putInt("penaltyStart", penaltyStart);
        ListTag saved = new ListTag();
        for (long value : savedCards) { CompoundTag entry = new CompoundTag(); entry.putLong("card", value); saved.add(entry); }
        result.put("savedCards", saved);
        result.put("categoriesBlocked", strings(categoriesBlocked, "category"));
        result.put("aidCards", strings(aidCards, "aidCard"));
        ListTag totals = new ListTag();
        categoryTotals.forEach((key, value) -> { CompoundTag entry = new CompoundTag(); entry.putString("category", key); entry.putInt("total", value); totals.add(entry); });
        result.put("categoryTotals", totals);
        ListTag choices = new ListTag();
        for (CardChoice choice : cardChoices) choices.add(serializeCardChoice(choice, registries));
        result.put("cardChoices", choices);
        if (lastDraw != null) result.put("lastDraw", serializeCardChoice(lastDraw, registries));
        return result;
    }
    private static ListTag strings(Iterable<String> values, String field) {
        ListTag result = new ListTag();
        for (String value : values) { CompoundTag entry = new CompoundTag(); entry.putString(field, value); result.add(entry); }
        return result;
    }
    public static CompoundTag serializeCardChoice(CardChoice choice, HolderLookup.Provider registries) {
        choice.card.bindRegistries(registries);
        CompoundTag tag = new CompoundTag();
        tag.putString("cardChoice", choice.key); tag.putBoolean("aid", choice.fromAid); tag.putBoolean("select", choice.selected);
        tag.put("cardNBT", choice.card.serialize()); return tag;
    }
    public void deserialize(CompoundTag tag, HolderLookup.Provider registries) {
        for (String list : new String[]{"savedCards", "categoriesBlocked", "categoryTotals", "aidCards"})
            if (tag.getList(list, 10).size() > 4096) throw new IllegalArgumentException("Oversized theory note " + list);
        if (tag.getList("cardChoices", 10).size() > 3 || tag.getString("player").length() > 64) throw new IllegalArgumentException("Invalid theory note");
        player = tag.getString("player"); owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        inspiration = tag.getInt("inspiration"); inspirationStart = tag.getInt("inspirationStart");
        placedCards = tag.getInt("placedCards"); bonusDraws = tag.getInt("bonusDraws");
        aidsChosen = tag.getInt("aidsChosen"); penaltyStart = tag.getInt("penaltyStart");
        savedCards.clear(); for (var value : tag.getList("savedCards", 10)) savedCards.add(((CompoundTag)value).getLong("card"));
        categoriesBlocked.clear(); for (var value : tag.getList("categoriesBlocked", 10)) categoriesBlocked.add(((CompoundTag)value).getString("category"));
        aidCards.clear(); for (var value : tag.getList("aidCards", 10)) aidCards.add(((CompoundTag)value).getString("aidCard"));
        categoryTotals.clear(); for (var value : tag.getList("categoryTotals", 10)) { CompoundTag entry = (CompoundTag)value; categoryTotals.put(entry.getString("category"), entry.getInt("total")); }
        cardChoices.clear(); for (var value : tag.getList("cardChoices", 10)) { CardChoice choice = deserializeCardChoice((CompoundTag)value, registries); if (choice != null) cardChoices.add(choice); }
        lastDraw = deserializeCardChoice(tag.getCompound("lastDraw"), registries);
    }
    public static CardChoice deserializeCardChoice(CompoundTag tag, HolderLookup.Provider registries) {
        var supplier = TheorycraftManager.cards.get(tag.getString("cardChoice"));
        if (supplier == null) return null;
        TheorycraftCard card = supplier.get(); card.bindRegistries(registries); card.deserialize(tag.getCompound("cardNBT"));
        return new CardChoice(tag.getString("cardChoice"), card, tag.getBoolean("aid"), tag.getBoolean("select"));
    }
    public static final class CardChoice {
        public final TheorycraftCard card; public final String key; public final boolean fromAid; public boolean selected;
        public CardChoice(String key, TheorycraftCard card, boolean fromAid, boolean selected) { this.key = key; this.card = card; this.fromAid = fromAid; this.selected = selected; }
        @Override public String toString() { return "key:" + key + " card:" + card.getSeed() + " fromAid:" + fromAid + " selected:" + selected; }
    }
}
