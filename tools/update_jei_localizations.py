"""Keep JEI strings in both imported languages and the persistent language import owner."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRANSLATIONS = {
    "thaumcraft.jei.aspect_amount": ("Amount: %s", "Количество: %s"),
    "thaumcraft.jei.aspect_combinations": ("Aspect combinations", "Сочетания аспектов"),
    "thaumcraft.jei.item_aspects": ("Item aspects", "Аспекты предметов"),
    "thaumcraft.jei.aspects_per_item": ("Aspect amounts per one item.", "Количество аспектов в одном предмете."),
    "thaumcraft.jei.transfer.cursor": ("Empty the cursor before transferring a recipe", "Освободите курсор перед переносом рецепта"),
    "thaumcraft.jei.transfer.pending": ("A recipe transfer is already in progress", "Перенос рецепта уже выполняется"),
    "thaumcraft.jei.transfer.research": ("Required research is not unlocked", "Не открыто необходимое исследование"),
    "thaumcraft.jei.transfer.missing": ("Missing ingredients or vis crystals", "Не хватает ингредиентов или кристаллов вис"),
    "thaumcraft.jei.transfer.space": ("Not enough inventory space to return workbench items", "Недостаточно места в инвентаре для предметов с верстака"),
    "thaumcraft.jei.transfer.unsupported": ("This recipe cannot be transferred safely", "Этот рецепт нельзя перенести"),
    "thaumcraft.jei.infusion_enchantment": ("%s — level %s/%s", "%s — уровень %s/%s"),
    "thaumcraft.jei.enchantment_cost_note": ("Costs include the displayed item's existing infusion enchantments.", "Затраты учитывают уже имеющиеся инфузионные зачарования показанного предмета."),
    "thaumcraft.jei.enchantment_warp_note": ("Existing infusion enchantments can add Warp during crafting.", "Имеющиеся инфузионные зачарования могут добавить варп при создании предмета."),
    "thaumcraft.jei.runic_upgrade": ("Runic shield: %s → %s", "Рунический щит: %s → %s"),
    "thaumcraft.jei.preserves_central": ("Preserves the central item's other properties.", "Сохраняет остальные свойства центрального предмета."),
    "thaumcraft.jei.item_modification": ("Item modification", "Модификация предмета"),
    "thaumcraft.jei.modification": ("Modification: %s", "Модификация: %s"),
}

def main():
    additions_path = ROOT / "tools/lang_port_additions.json"
    additions = json.loads(additions_path.read_text(encoding="utf-8"))
    for index, locale in enumerate(("en_us", "ru_ru")):
        path = ROOT / f"port/src/main/resources/assets/thaumcraft/lang/{locale}.json"
        language = json.loads(path.read_text(encoding="utf-8"))
        updates = {key: values[index] for key, values in TRANSLATIONS.items()}
        language.update(updates)
        additions.setdefault(locale, {}).update(updates)
        path.write_text(json.dumps(language, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    additions_path.write_text(json.dumps(additions, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
