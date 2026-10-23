"""Independent native TCP caster progression, paid focus/cast/pouch and stopped-world persistence verifier."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import sys
import uuid
import zlib

sys.dont_write_bytecode = True
REPO = Path(__file__).resolve().parent.parent
PARSER_PINS = {
    "tools/audit-tcp-alchemy.py": "bfa3d1310b81d079e59159a63ae552e92d92d872cae147c526935ee2b2a1b07c",
    "tools/audit-tcp-bootstrap-reload.py": "29b8dfcbcfb0f99f70f96ce319ed0574bfc06fbfe3e0429fd83a380d97bc5695",
}
for _name, _expected in PARSER_PINS.items():
    _path = REPO / _name
    if not _path.is_file() or _path.stat().st_size > 4 * 1024 * 1024 or hashlib.sha256(_path.read_bytes()).hexdigest() != _expected:
        raise ValueError(f"Reviewed native caster parser changed before import: {_name}")
_spec = importlib.util.spec_from_file_location("tc_native_caster_parsers", REPO / "tools/audit-tcp-alchemy.py")
native = importlib.util.module_from_spec(_spec)
sys.modules[_spec.name] = native
_spec.loader.exec_module(native)
nbt, require, unlinked, file, sha = native.nbt, native.require, native.unlinked, native.file, native.sha
document, identity, journal = native.document, native.identity, native.journal
IDENTITY, SOURCE_FILES = native.IDENTITY, native.SOURCE_FILES

MARKER = "TC_PORT_TCP_CASTERS_AUDIT_V1"
BASELINE_SHA = "0e4ca3ba79fb1d00c0101107197089832c06685810398cd202093d62b0371aa1"
BASELINE_JAR = "port/build/jei-review/caster-20261010-visual-02/accepted-caster-textures.jar"
SOURCE_GENERATE = "alchemy-20261009-reload-02"
WORLD_NAME = "tcp-caster-audit"
RESEARCH = {
    "UNLOCKAUROMANCY": ((-1, 1), (1, 2), (2, 4)),
    "BASEAUROMANCY": ((-1, 1), (1, 2), (2, 4)),
    "FOCUSPOUCH": ((-1, 1), (1, 3)),
    "UNLOCKARTIFICE": ((-1, 1), (1, 3)),
    "BASEARTIFICE": ((-1, 2),),
}
FINAL_STAGES = {"UNLOCKAUROMANCY": 4, "BASEAUROMANCY": 4, "FOCUSPOUCH": 3, "UNLOCKARTIFICE": 3, "BASEARTIFICE": 2}
RESEARCH_COSTS = {("FOCUSPOUCH", 1): {"O_AUROMANCY": 16, "O_ARTIFICE": 16},
                  ("UNLOCKARTIFICE", 1): {"O_ARTIFICE": 16}}
ARCANE_COSTS = {
    "thaumcraft:thaumometer": (20, [1, 1, 1, 1, 1, 1]),
    "thaumcraft:vis_resonator": (50, [1, 0, 1, 0, 0, 0]),
    "thaumcraft:caster_basic": (100, [1, 1, 1, 1, 1, 1]),
    "thaumcraft:wand_workbench": (100, [0, 0, 1, 1, 0, 0]),
    "thaumcraft:focus_pouch": (25, [0, 0, 0, 0, 0, 0]),
    "thaumcraft:goggles": (50, [0, 0, 0, 0, 0, 0]),
}
# Scenario/ledger details are explicitly reviewed before this can accept a native cohort.
REVIEWED_GAMEPLAY_CONTRACT = True
REVIEWED_ROOF_POSITIONS = {(x, 69, z) for center in (268, 272) for x in range(center - 1, center + 2) for z in range(15, 18)}
REVIEWED_NEW_POSITIONS = {(264, 65, 16), (264, 66, 16), (258, 65, 36), (258, 4, 32), (258, 165, 32)} | REVIEWED_ROOF_POSITIONS
CRAFT_COUNTS = {"thaumcraft:vis_resonator": 2, "thaumcraft:caster_basic": 1, "thaumcraft:thaumometer": 2,
                "thaumcraft:focus_1": 1, "thaumcraft:wand_workbench": 1, "thaumcraft:girdle_mundane": 1,
                "thaumcraft:focus_pouch": 1, "thaumcraft:goggles": 1}
# Source item translation-key/metadata hashes are candidate identities. Only
# identities declared by a research stage's required_craft become learned keys.
CRAFT_REFERENCES = {"thaumcraft:vis_resonator": 303529330, "thaumcraft:caster_basic": -222878209,
                    "thaumcraft:focus_1": -1210339066, "thaumcraft:wand_workbench": 1235445097,
                    "thaumcraft:girdle_mundane": 1339991308, "thaumcraft:focus_pouch": 1774648592,
                    "thaumcraft:thaumometer": 1777627067, "thaumcraft:goggles": 125190606}
RECORDED_CRAFT_ITEMS = {"thaumcraft:vis_resonator", "thaumcraft:caster_basic", "thaumcraft:focus_1",
                        "thaumcraft:wand_workbench", "thaumcraft:thaumometer"}
CRAFT_CATALOG_PINS = {
    "reference/tc6/src/assets/thaumcraft/research/basics.json": "22addef24e9b0d0e0f4b2570d2da2e4ee9393876a341f592cfc2f32586a9d1aa",
    "reference/tc6/src/assets/thaumcraft/research/auromancy.json": "654e16833b2b8715af74adf3a26b5bc68d874f559c38a56b170628f8a75da604",
}
RESEARCH_SEQUENCE = tuple((key, before, after) for key, transitions in RESEARCH.items() for before, after in transitions)
FOCUS_NAME = "TCP Touch Fire"
ACTION_IDS = (
    "store-inherited-crystals", "scan-real-piston", "research-unlockauromancy--1", "native-deep-discovery",
    "native-high-discovery", "research-unlockauromancy-1", "craft-native-resonator-0", "craft-native-resonator-1",
    "craft-native-caster", "research-unlockauromancy-2", "research-baseauromancy--1", "dissolve-focus-vitreus",
    "dissolve-focus-praecantatio", "dissolve-focus-auram", "crucible-native-basic-focus", "pickup-native-basic-focus",
    "research-baseauromancy-1", "craft-native-focal-manipulator", "place-native-focal-manipulator",
    "native-fire-discovery", "research-baseauromancy-2", "research-focuspouch--1", "research-focuspouch-1",
    "research-unlockartifice--1", "research-unlockartifice-1", "research-baseartifice--1", "craft-native-girdle",
    "craft-native-focus-pouch", "craft-native-thaumometer-0", "craft-native-thaumometer-1", "craft-native-goggles",
    "gui-native-focal-draft", "gui-native-focal-craft", "gui-native-focal-pickup", "native-pouch-insert",
    "native-focus-select", "native-cast-without-goggles", "native-focus-remove", "native-pouch-extract",
    "native-pouch-reinsert", "native-focus-reselect", "native-equip-goggles", "native-cast-with-goggles", "save-native-casters",
)
LEDGER_EVENTS = {
    "nativeResearchRequests": "actual-applied-contextual-research", "nativeCraftReceipts": "native-server-craft-receipt",
    "nativeScanReceipts": "native-server-ray-scan-receipt", "nativeKnowledgeReceipts": "native-knowledge-event",
    "nativeDiscoveryReceipts": "native-discovery", "cruciblePaymentReceipts": "native-crucible-payment",
    "arcanePaymentReceipts": "native-arcane-payment", "nativeFocalRequests": "native-focal-request",
    "focalAuraPaymentReceipts": "native-focal-aura-payment", "castPaymentReceipts": "native-cast-payment",
    "nativeCastEffectReceipts": "native-cast-effect", "nativeFocusSwitchReceipts": "native-focus-switch",
    "nativePouchReceipts": "native-pouch-transfer", "nativeStorageReceipts": "native-material-storage",
}


def evidence(root, path, maximum=4 * 1024 * 1024):
    data = file(root, path, maximum)
    return {"path": str(path), "bytes": len(data), "sha256": sha(data)}


def same_identity(value, marker, role=None):
    identity(value, marker, role)


def f32(value):
    return struct.unpack(">f", struct.pack(">f", value))[0]


def float_bits(value):
    return struct.unpack(">i", struct.pack(">f", value))[0]


def raw_knowledge(value):
    return native.knowledge_map({"knowledge": nbt.snbt(value) if isinstance(value, str) else value})


def snapshot_xp(value):
    require(type(value.get("experienceLevel")) is int and type(value.get("totalExperience")) is int
            and type(value.get("experienceProgress")) in (int, float)
            and 0 <= value["experienceLevel"] <= 32767 and value["totalExperience"] >= 0
            and 0 <= value["experienceProgress"] < 1, "Invalid native source XP snapshot")
    return value["experienceLevel"], value["totalExperience"], f32(value["experienceProgress"])


def xp_needed(level):
    return 112 + (level - 30) * 9 if level >= 30 else 37 + (level - 15) * 5 if level >= 15 else 7 + level * 2


def add_source_research_xp(original, count):
    level, total, progress = snapshot_xp(original)
    for _ in range(count):
        progress = f32(progress + f32(5 / xp_needed(level)))
        total += 5
        while progress >= 1:
            progress = f32(f32(progress - 1) * xp_needed(level))
            level += 1
            progress = f32(progress / xp_needed(level))
    return level, total, progress


def stack(item, count=1, aspect=None):
    result = {"id": nbt.Tag(8, item if ":" in item else "minecraft:" + item), "count": nbt.Tag(3, count)}
    if aspect:
        result["components"] = nbt.Tag(10, {"thaumcraft:aspects": nbt.Tag(10, {aspect: nbt.Tag(3, 1)})})
    return nbt.Tag(10, result)


def inventory_slots(value, limit=41):
    tag = nbt.snbt(value) if isinstance(value, str) else value
    require(tag.kind == 9 and len(tag.value) <= limit, "Actual native slot snapshot is not a bounded typed inventory")
    result = {}
    for child in tag.value:
        row = dict(nbt.compound(child))
        position = nbt.scalar(row.pop("Slot"), 1)
        # Player armor/offhand use vanilla signed byte slots100..103/-106.
        require(position not in result and (0 <= position < limit or limit == 41 and position in (100, 101, 102, 103, -106)),
                "Actual native slot snapshot has duplicate/out-of-range slots")
        require(type(nbt.scalar(row["count"], 3)) is int and 0 < nbt.scalar(row["count"], 3) <= 99
                and nbt.scalar(row["id"], 8) != "minecraft:air", "Native stack contains invalid actual amount/item")
        result[position] = nbt.Tag(10, row)
    return result


def stack_bag(values):
    result = Counter()
    for value in values:
        row = dict(nbt.compound(value)); amount = nbt.scalar(row.pop("count"), 3)
        require(0 < amount <= 99 and "Slot" not in row, "Native stack bag has unreviewed slot/count")
        result[native.freeze(nbt.Tag(10, row))] += amount
    return result


def recipe_matrix(action):
    matrices = {
        "craft-native-caster": {0: stack("iron_ingot"), 1: stack("iron_ingot"), 2: stack("iron_ingot"),
            3: stack("leather"), 4: stack("thaumcraft:vis_resonator"), 5: stack("leather"), 6: stack("leather"),
            7: stack("thaumcraft:thaumometer"), 8: stack("leather")},
        "craft-native-focal-manipulator": {0: stack("thaumcraft:plate_iron"), 1: stack("thaumcraft:slab_arcane_stone"),
            2: stack("thaumcraft:plate_iron"), 3: stack("thaumcraft:stone_arcane"), 4: stack("thaumcraft:vis_resonator"),
            5: stack("thaumcraft:stone_arcane"), 6: stack("gold_ingot"), 7: stack("thaumcraft:table_stone"), 8: stack("gold_ingot")},
        "craft-native-girdle": {1: stack("leather"), 3: stack("leather"), 5: stack("leather"), 7: stack("thaumcraft:ingot_brass")},
        "craft-native-focus-pouch": {i: stack("gold_ingot" if i == 1 else "thaumcraft:girdle_mundane" if i == 4 else "leather") for i in range(9)},
        "craft-native-goggles": {0: stack("leather"), 1: stack("thaumcraft:ingot_brass"), 2: stack("leather"),
            3: stack("leather"), 5: stack("leather"), 6: stack("thaumcraft:thaumometer"), 7: stack("thaumcraft:ingot_brass"), 8: stack("thaumcraft:thaumometer")},
    }
    if action in ("craft-native-resonator-0", "craft-native-resonator-1"):
        matrix = {0: stack("thaumcraft:plate_iron"), 1: stack("quartz")}; primal = [1, 0, 1, 0, 0, 0]
    elif action in ("craft-native-thaumometer-0", "craft-native-thaumometer-1"):
        matrix = {i: stack("glass_pane" if i == 4 else "gold_ingot") for i in (1, 3, 4, 5, 7)}; primal = [1] * 6
    else:
        require(action in matrices, "Unreviewed actual source crafting action")
        matrix = matrices[action]; primal = [1] * 6 if action == "craft-native-caster" else [0, 0, 1, 1, 0, 0] if action == "craft-native-focal-manipulator" else [0] * 6
    return {**matrix, **{9 + i: stack("thaumcraft:crystal_essence", n, aspect) for i, (n, aspect) in enumerate(zip(primal,
                ("aer", "ignis", "aqua", "terra", "ordo", "perditio"))) if n}}


def java_hash(value):
    result = 0
    for character in value:
        result = (result * 31 + ord(character)) & 0xffffffff
    return result if result < 0x80000000 else result - 0x100000000


# Source FocusNode.settings is a16-bucket HashMap, with no collision between these keys.
SETTING_ORDER = tuple(sorted(("power", "duration"), key=lambda value: ((java_hash(value) & 0xffffffff) ^ ((java_hash(value) & 0xffffffff) >> 16)) & 15))
SORT_HASH = java_hash("ROOTthaumcraft.TOUCHthaumcraft.FIRE" + "".join("1" if key == "power" else "0" for key in SETTING_ORDER))
SOURCE_FOCUS_KEY = FOCUS_NAME + str(SORT_HASH)
SOURCE_FIRE_COLOR = (0xff000000 | 16734721) - 0x100000000


def focus_stack(value, allow_cache=True):
    """Validate the complete authored package; only source-derived cache values are optional."""
    data = nbt.compound(nbt.snbt(value) if isinstance(value, str) else value)
    require(set(data) == {"id", "count", "components"} and data["id"] == nbt.Tag(8, "thaumcraft:focus_1")
            and data["count"] == nbt.Tag(3, 1), "Authored focus is not the exact native basic singleton")
    components = nbt.compound(data["components"])
    require(set(components) == {"minecraft:custom_data", "minecraft:custom_name"}, "Authored focus contains unreviewed data components")
    name = nbt.scalar(components["minecraft:custom_name"], 8)
    require(json.loads(name) in (FOCUS_NAME, {"text": FOCUS_NAME}), "Authored native focus custom name differs")
    custom = nbt.compound(components["minecraft:custom_data"])
    require(set(custom) <= {"package", "srt", "color"} and "package" in custom, "Authored focus has unreviewed custom fields")
    if "srt" in custom:
        require(allow_cache and custom["srt"] == nbt.Tag(3, SORT_HASH), "Native focus sorting cache differs from actual source HashMap/string hash")
    if "color" in custom:
        require(allow_cache and custom["color"] == nbt.Tag(3, SOURCE_FIRE_COLOR), "Native focus color cache differs from the only actual source Fire effect")
    expected = nbt.snbt("{index:0,power:1.0f,complexity:4,nodes:[{type:'MEDIUM',key:'ROOT'},"
                        "{type:'MEDIUM',key:'thaumcraft.TOUCH'},{type:'EFFECT',key:'thaumcraft.FIRE','setting.power':1,'setting.duration':0}]}")
    require(custom["package"] == expected, "Full authored executable focus package differs or persisted runtime caster/index/UUID state")
    normalized = dict(components)
    normalized["minecraft:custom_data"] = nbt.Tag(10, {"package": expected})
    return nbt.Tag(10, {"id": data["id"], "count": data["count"], "components": nbt.Tag(10, normalized)})


def pouch_contents(value):
    data = nbt.compound(nbt.snbt(value) if isinstance(value, str) else value)
    require(nbt.scalar(data["id"], 8) == "thaumcraft:focus_pouch" and data["count"] == nbt.Tag(3, 1), "Native source pouch singleton differs")
    components = nbt.compound(data.get("components", nbt.Tag(10, {})))
    require(set(components) <= {"minecraft:container"}, "Native focus pouch has unreviewed component fields")
    contents = components.get("minecraft:container", nbt.Tag(9, ()))
    require(contents.kind == 9 and len(contents.value) <= 18, "Native source pouch is not bounded18-slot contents")
    result = {}
    for child in contents.value:
        row = nbt.compound(child)
        require(set(row) == {"slot", "item"}, "Native pouch saved entry schema differs")
        slot = nbt.scalar(row["slot"], 3)
        require(type(slot) is int and 0 <= slot < 18 and slot not in result, "Native pouch contains invalid/duplicate slots")
        focus_stack(row["item"]); result[slot] = row["item"]
    return result


def caster_focus(value):
    data = nbt.compound(nbt.snbt(value) if isinstance(value, str) else value)
    require(data["id"] == nbt.Tag(8, "thaumcraft:caster_basic") and data["count"] == nbt.Tag(3, 1), "Actual held caster is not a registered native basic singleton")
    components = nbt.compound(data.get("components", nbt.Tag(10, {})))
    require(set(components) <= {"thaumcraft:caster_focus"}, "Actual source basic caster has unreviewed picked/package/component storage")
    focus = components.get("thaumcraft:caster_focus")
    if focus is not None:
        focus_stack(focus)
    return focus


def flattened_inventory(value, normalize_focus_cache=False):
    result = []
    for item in inventory_slots(value).values():
        data = dict(nbt.compound(item)); item_id = nbt.scalar(data["id"], 8)
        if item_id == "thaumcraft:focus_pouch":
            result.extend(pouch_contents(item).values()); data.pop("components", None)
        elif item_id == "thaumcraft:caster_basic":
            focus = caster_focus(item)
            if focus is not None:
                result.append(focus)
            data.pop("components", None)
        result.append(nbt.Tag(10, data))
    if normalize_focus_cache:
        result = [focus_stack(item) if nbt.scalar(nbt.compound(item)["id"], 8) == "thaumcraft:focus_1"
                  and "components" in nbt.compound(item) else item for item in result]
    return stack_bag(result)


def combined_chest(baseline):
    owned = {tuple(row["position"]): row for row in baseline["worldTiles"]}
    items = []
    for half, position in enumerate(((260, 65, 12), (261, 65, 12))):
        require(owned[position]["block"] == "minecraft:chest", "Owned inherited native double chest is absent")
        data = nbt.compound(nbt.snbt(owned[position]["nbt"]))
        require(data["id"] == nbt.Tag(8, "minecraft:chest") and "LootTable" not in data, "Native chest is not physical saved normal storage")
        for child in data["Items"].value:
            item = dict(nbt.compound(child)); item["Slot"] = nbt.Tag(1, nbt.scalar(item["Slot"], 1) + half * 27); items.append(nbt.Tag(10, item))
    result = nbt.Tag(10, {"Size": nbt.Tag(3, 54), "Items": nbt.Tag(9, tuple(items))})
    native.chest_snapshot(str_tag(result))
    return result


def str_tag(value):
    """Only helpers needing textual SNBT use this finite typed serializer."""
    if value.kind == 10:
        return "{" + ",".join(json.dumps(key) + ":" + str_tag(child) for key, child in value.value.items()) + "}"
    if value.kind == 9:
        return "[" + ",".join(str_tag(child) for child in value.value) + "]"
    if value.kind == 8:
        return json.dumps(value.value)
    if value.kind in (1, 2, 3, 4, 5, 6):
        return str(value.value) + {1: "b", 2: "s", 3: "", 4: "L", 5: "f", 6: "d"}[value.kind]
    if value.kind in (7, 11, 12):
        prefix, suffix = {7: ("B", "b"), 11: ("I", ""), 12: ("L", "L")}[value.kind]
        return "[" + prefix + ";" + ",".join(str(child) + suffix for child in value.value) + "]"
    raise ValueError("Unreviewed native SNBT output type")


def aura_debit(before, after, expected, center, allowed_offsets):
    coverage = 1 if allowed_offsets == {(0, 0)} else 9
    require(isinstance(before, list) and isinstance(after, list) and len(before) == len(after) == coverage,
            "Missing complete source caster/charged native aura chunk observations")
    paid = 0.0
    for index, (left, right) in enumerate(zip(before, after)):
        position = center if coverage == 1 else (center[0] - 1 + index // 3, center[1] - 1 + index % 3)
        values = []
        for row in (left, right):
            require((row.get("chunkX"), row.get("chunkZ")) == position and row.get("actuallyLoaded") is True
                    and type(row.get("base")) is int and 0 <= row["base"] <= 500, "Actual native aura chunk coordinate/base/loading differs")
            for field in ("vis", "flux"):
                require(type(row.get(field)) in (int, float) and 0 <= row[field] <= 32767
                        and type(row.get(field + "Bits")) is int and float_bits(row[field]) == row[field + "Bits"],
                        "Native aura summary differs from actual IEEE-754 float bits")
            values.append(f32(row["vis"]))
        require(left["base"] == right["base"] and left["fluxBits"] == right["fluxBits"] and values[0] >= values[1],
                "Native focus/cast created aura or changed source base/flux")
        difference = values[0] - values[1]
        require(difference == 0 or (position[0] - center[0], position[1] - center[1]) in allowed_offsets,
                "Native source aura debit escaped the charged/caster-area chunk set")
        paid += difference
    require(abs(paid - expected) <= 1e-4, "Independently reconstructed native aura debit differs from source actual paid cost")
    return paid


def verify_research(server, client, state, original):
    rows = server.get("nativeResearchRequests")
    require(isinstance(rows, list) and len(rows) == 11, "Exact source caster contextual research request count differs")
    sequence = []
    for index, row in enumerate(rows):
        require(row.get("status") == "APPLIED" and row.get("actualContextualTCP") is True and row.get("mainThread") is True
                and re.fullmatch(r"[0-9a-f]{64}", row.get("catalogDigest", "")), "Native caster research request lacks actual applied TCP/catalog context")
        sequence.append((row["researchKey"], row["storedStageBefore"], row["storedStageAfter"]))
        require(row["expectedStage"] == row["storedStageBefore"], "Native caster research expected/source stage differs")
        require(snapshot_xp(row_owner(row, "ownerBefore")) == add_source_research_xp(original, index)
                and snapshot_xp(row_owner(row, "ownerAfter")) == add_source_research_xp(original, index + 1),
                "Native successful source research did not add exactly five real XP points with vanilla float/level carries")
        before, after = raw_knowledge(row["knowledgeBeforeNbt"])[1], raw_knowledge(row["knowledgeAfterNbt"])[1]
        cost = RESEARCH_COSTS.get((row["researchKey"], row["storedStageBefore"]), {})
        require(all(before.get(key, 0) - after.get(key, 0) == cost.get(key, 0) for key in set(before) | set(after) | set(cost)),
                "Native caster research did not pay exactly its source observation costs")
        require(native.inventory_bag(row["inventoryBeforeNbt"]) == native.inventory_bag(row["inventoryAfterNbt"]),
                "Native caster research consumed unrequired item stacks")
        for prefix, source_type in (("O_", "OBSERVATION"), ("T_", "THEORY")):
            for category in native.POOLS:
                require(row["knowledgeBefore"][source_type + ";" + category] == before.get(prefix + category, 0)
                        and row["knowledgeAfter"][source_type + ";" + category] == after.get(prefix + category, 0),
                        "Native caster research pool summary differs from typed knowledge NBT")
    require(tuple(sequence) == RESEARCH_SEQUENCE, "Actual caster progression differs from source stage gates/final-page skips")
    driver = client.get("researchReport", {})
    receipts = driver.get("researchReceipts")
    require(driver.get("knowledgeOrResearchFixtures") is False and driver.get("forcedRandomSeed") is False
            and driver.get("nativeResearchGui") is True and driver.get("theoryReceipts") == []
            and isinstance(receipts, list) and len(receipts) == 11
            and tuple((row.get("researchKey"), row.get("storedStageBefore"), row.get("storedStageAfter")) for row in receipts) == RESEARCH_SEQUENCE
            and all(row.get("actualAcceptedOwnerS2C") is True for row in receipts), "Genuine native book research/owner receipts differ")
    require(driver.get("events") == client_events(client["actions"], "native-research-driver"),
            "Sealed native research driver events differ from the actual client journal")
    final_research, final_pools = native.knowledge_map(state)
    source_research, source_pools = native.knowledge_map(original)
    require(all(final_research.get(key, {}).get("stage") == stage for key, stage in FINAL_STAGES.items())
            and all(key in final_research for key in ("m_deepdown", "m_uphigh", "f_onfire", "!machina", "!minecraft:piston0")),
            "Independent saved caster research/discovery/scan progression is incomplete")
    require(all(final_research.get(key) == row for key, row in source_research.items()), "Native caster progression changed preexisting accepted source research")
    events = server.get("nativeKnowledgeReceipts")
    require(isinstance(events, list) and 3 <= len(events) <= 64, "Missing bounded signed actual caster knowledge events")
    expected_pools, debits = dict(source_pools), Counter()
    for row in events:
        require(row.get("type") == "OBSERVATION" and row.get("category") in native.POOLS
                and type(row.get("amount")) is int and -16 <= row["amount"] <= 4096,
                "Source caster route earned or paid unrequired theory/unknown knowledge")
        key = "O_" + row["category"]
        require(row["beforeRaw"] == expected_pools.get(key, 0) and row["afterRaw"] == row["beforeRaw"] + row["amount"]
                and row["afterRaw"] >= 0, "Native signed caster knowledge event chain differs from real source")
        expected_pools[key] = row["afterRaw"]
        if row["amount"] < 0:
            debits[key] -= row["amount"]
    require(debits == Counter({"O_AUROMANCY": 16, "O_ARTIFICE": 32})
            and all(final_pools.get(key, 0) == expected_pools.get(key, 0) for key in set(final_pools) | set(expected_pools)),
            "Independently saved full knowledge pools differ from exact signed native scan/research payments")
    require(server.get("nativeTheoryReceipts", []) == [], "Caster route acquired unrequired native theory")
    return {"nativeResearchRequests": 11, "sourceResearchXpPoints": 55, "rawObservationPaid": dict(debits)}


def verify_crafting(server):
    for relative, expected in CRAFT_CATALOG_PINS.items():
        require(sha(file(REPO, REPO / relative)) == expected, "Reviewed source required-craft catalog changed")
    rows = server.get("nativeCraftReceipts")
    require(isinstance(rows, list) and len(rows) == 10, "Exact native caster item-crafted receipt set differs")
    actual = Counter()
    markers = set()
    for row in rows:
        require(row.get("actualTCP") is True and row.get("mainThread") is True and row.get("count") == 1
                and isinstance(row.get("actionId"), str), "Caster craft receipt lacks genuine source main-thread TCP event")
        actual[row["item"]] += 1
        marker = row.get("craftMarker", "")
        require(row["item"] in CRAFT_REFERENCES and marker == "[#]" + str(CRAFT_REFERENCES[row["item"]]),
                "Native caster craft candidate differs from its exact source translation-key/metadata identity")
        recorded = row["item"] in RECORDED_CRAFT_ITEMS
        require((marker in raw_knowledge(row["knowledgeAfterNbt"])[0]) is recorded,
                "Native craft learned-key presence differs from the source declared required-craft identities")
        if recorded:
            markers.add(marker)
    require(actual == Counter(CRAFT_COUNTS), "Actual native caster output counts differ from finite source action path")
    crucible = server.get("cruciblePaymentReceipts")
    require(isinstance(crucible, list) and len(crucible) == 1, "Native basic-focus crucible payment missing/duplicated")
    row = crucible[0]
    require(row.get("item") == "thaumcraft:focus_1" and row.get("actionId") == "crucible-native-basic-focus"
            and row.get("actualTCP") is True and row.get("mainThread") is True
            and row.get("actualClientS2CAcknowledged") is True, "Basic focus lacks native paid production and received action proof")
    before, after = (native.typed_aspects(row[name]) for name in ("aspectsBeforeNbt", "aspectsAfterNbt"))
    cost = {"vitreus": 20, "praecantatio": 10, "auram": 5}
    require(cost == {"vitreus": 20, "praecantatio": 10, "auram": 5}
            and all(before.get(key, 0) - after.get(key, 0) == cost.get(key, 0) for key in set(before) | set(after) | set(cost))
            and row["waterBefore"] - row["waterAfter"] == 50, "Actual native basic-focus aspect/water conservation differs")
    arcane = server.get("arcanePaymentReceipts")
    require(isinstance(arcane, list) and len(arcane) == 8, "Missing exact eight native caster arcane payments")
    counts, vis_total, crystals_total = Counter(), 0, [0] * 6
    for row in arcane:
        require(row.get("item") in ARCANE_COSTS and row.get("actualTCP") is True and row.get("mainThread") is True
                and row.get("chargedWorkbench") is True and row.get("nativeBeforePaymentObserved") is True
                and row.get("nativePaidRecipeCommitted") is True, "Arcane caster output lacks actual charged native payment")
        vis, crystals = ARCANE_COSTS[row["item"]]
        require(row["cost"] == vis and abs(row["visPaid"] - vis) <= 1e-4 and row["visBefore"] - row["visAfter"] == vis,
                "Actual source caster arcane vis/crystal debit differs")
        require(type(row["menuId"]) is int and row["menuId"] > 0, "Native caster arcane source menu context differs")
        require(nbt.compound(nbt.snbt(row["outputNbt"])) == {"id": nbt.Tag(8, row["item"]), "count": nbt.Tag(3, 1)},
                "Native caster arcane output differs from exact registered source item")
        actual_paid = aura_debit(row["auraChunksBefore"], row["auraChunksAfter"], vis, (16, 0), {(x, z) for x in range(-1, 2) for z in range(-1, 2)})
        require(abs(actual_paid - row["visPaid"]) <= 1e-4, "Native caster arcane paid float summary differs from source chunks")
        matrix_before = inventory_slots(nbt.compound(nbt.snbt(row["matrixBeforeNbt"]))["Items"], 15)
        matrix_after = inventory_slots(nbt.compound(nbt.snbt(row["matrixAfterNbt"]))["Items"], 15)
        require(not matrix_after, "Native paid single-take recipe left unreviewed matrix remainders")
        expected = recipe_matrix(row["actionId"])
        require(matrix_before == expected, "Actual native pre-payment matrix differs from every exact source ingredient/crystal slot")
        require([sum(nbt.scalar(nbt.compound(stack)["count"], 3) for slot, stack in matrix_before.items() if slot == 9 + i)
                 for i in range(6)] == crystals, "Independently reconstructed actual primal crystal payments differ")
        for field in ("Before", "After"):
            aggregate = 0
            for chunk in row["auraChunks" + field]:
                aggregate = int(f32(aggregate + chunk["vis"]))
            require(aggregate == row["vis" + field], "Actual charged caster workbench integer aggregate differs")
        counts[row["item"]] += 1
        vis_total += vis
        crystals_total = [left + right for left, right in zip(crystals_total, crystals)]
    require(counts == Counter({key: CRAFT_COUNTS[key] for key in ARCANE_COSTS}) and vis_total == 415
            and crystals_total == [5, 3, 6, 4, 3, 3], "Finite source caster arcane total/cardinality differs")
    return {"nativeCraftReceipts": 10, "sourceCraftMarkers": sorted(markers), "nativeArcanePayments": 8,
            "arcaneVisPaid": vis_total, "arcanePrimalCrystalsPaid": crystals_total,
            "crucibleAspectsPaid": cost}


def provenance(root, marker):
    phase = marker["auditPhase"]
    source = unlinked(REPO / "port/build" / ("tcp-alchemy" if phase == "generate" else "tcp-casters") / marker["sourceRunId"])
    prepared = document(root, root / "preparation.json")
    same_identity(prepared, marker)
    require(unlinked(Path(prepared["sourceRoot"])) == source, "Caster preparation source escaped the reviewed private base")
    inputs, copied = root / "preparation-inputs", {}
    for name, source_name, key in SOURCE_FILES:
        data = file(root, inputs / name)
        require(sha(data) == marker[key] and data == file(source, source / source_name), "Copied/original native caster source evidence changed")
        copied[name] = document(root, inputs / name)
    for name, row in copied.items():
        for key, expected in (("marker", marker["sourceMarker"]), ("purpose", marker["sourcePurpose"]),
                              ("goal", marker["sourceGoal"]), ("auditPhase", marker["sourceAuditPhase"]),
                              ("schemaVersion", 1), ("networkProtocolVersion", "6"), ("runId", marker["sourceRunId"]),
                              ("token", marker["sourceToken"]), ("port", marker["sourcePort"])):
            require(row.get(key) == expected, f"Copied native source identity differs: {name}/{key}")
    server, client, runtime = copied["source-server-report.json"], copied["source-client-report.json"], copied["source-runtime.json"]
    require(server.get("role") == "server" and client.get("role") == "client" and all(row.get("passed") is True for row in (server, client, copied["source-verification.json"]))
            and server.get("status") == client.get("status") == "completed"
            and server.get("saveFlushed") is True and server.get("diskNbtVerified") is True
            and marker["sourceToken"] != marker["token"], "Source lacks accepted stopped native/disk caster evidence")
    if phase == "generate":
        require(marker["sourceKind"] == "alchemy" and marker["sourceRunId"] == SOURCE_GENERATE
                and marker["sourceMarker"] == "TC_PORT_TCP_ALCHEMY_AUDIT_V1" and marker["sourcePurpose"] == "native-alchemy"
                and marker["sourceGoal"] == "alchemy" and marker["sourceAuditPhase"] == "reload"
                and server.get("alchemyComplete") is True and server.get("newJvmReloadProven") is True,
                "Native caster generation is not based on the accepted alchemy reload owner")
        # This readonly alchemy source has no reconnect of its own. Its preceding phase
        # supplied the accepted reconnect; no boolean is invented on the readonly source.
    else:
        require(marker["sourceKind"] == "casters" and marker["sourceMarker"] == MARKER and marker["sourcePurpose"] == "native-casters"
                and marker["sourceGoal"] == "casters" and marker["sourceAuditPhase"] == "generate"
                and server.get("castersComplete") is True and server.get("reconnectProven") is True,
                "Readonly caster reload lacks an accepted actual reconnected generation source")
    manifest_data = file(root, inputs / "world-copy-manifest.json")
    require(sha(manifest_data) == marker["worldManifestSha256"], "Caster stopped-world manifest hash differs")
    manifest = document(root, inputs / "world-copy-manifest.json")
    source_world = source / "server" / ("tcp-alchemy-audit" if phase == "generate" else WORLD_NAME)
    world = root / "server" / WORLD_NAME
    require(manifest.get("sourceRunId") == marker["sourceRunId"] and manifest.get("sourceToken") == marker["sourceToken"]
            and unlinked(Path(manifest["sourceRoot"])) == source and unlinked(Path(manifest["sourceWorld"])) == source_world
            and unlinked(Path(manifest["copiedWorld"])) == world and manifest.get("sourceStopped") is True
            and manifest.get("sourceUnchangedDuringCopy") is True and manifest.get("copiedBytesExact") is True, "Caster source copy lacks exact stopped-world provenance")
    rows = manifest.get("files")
    require(isinstance(rows, list) and 0 < len(rows) <= 10000 and len(rows) == manifest.get("fileCount"), "Unbounded/incomplete caster source manifest")
    names, total = set(), 0
    for row in rows:
        relative = Path(row["path"])
        require(not relative.is_absolute() and ".." not in relative.parts and row["path"] not in names, "Unsafe/duplicate caster copied world file")
        names.add(row["path"])
        data = file(source, source_world / relative, 64 * 1024 * 1024)
        require(len(data) == row["bytes"] and sha(data) == row["sha256"], "Stopped native caster source world changed after copy")
        total += len(data)
    actual_names = set()
    for path in source_world.rglob("*"):
        path = unlinked(path)
        if path.is_file():
            actual_names.add(path.relative_to(source_world).as_posix())
    require(actual_names == names and total == manifest.get("totalBytes") and total <= 512 * 1024 * 1024
            and "level.dat" in names and f"playerdata/{nbt.owner_uuid()}.dat" in names, "Caster source manifest omits actual saved world/player files")
    for relative, key in (("eula.txt", "eulaSha256"), ("options.txt", "optionsSha256")):
        require(sha(file(root, inputs / relative)) == prepared[key], "Copied native caster preference/accepted EULA bytes changed")
    require(file(root, inputs / "eula.txt", 4096) == file(root, root / "server/eula.txt", 4096)
            and re.search(r"(?m)^\s*eula\s*=\s*true\s*$", file(root, root / "server/eula.txt", 4096).decode("utf-8-sig")), "Previously accepted private EULA changed")
    require("pauseOnLostFocus:false" in file(root, root / "client/options.txt", 1024 * 1024).decode("utf-8-sig").splitlines(), "Native caster focus preference changed")
    properties = file(root, root / "server/server.properties", 8192).decode("utf-8-sig").splitlines()
    require(all(value in properties for value in (f"level-name={WORLD_NAME}", "server-ip=127.0.0.1", f"server-port={marker['port']}", "online-mode=false", "gamemode=survival")), "Private native caster server properties changed")
    return source, source_world, server, client, runtime, world, {"fileCount": len(rows), "bytes": total, "manifest": evidence(root, inputs / "world-copy-manifest.json")}


def processes(root, runtime, marker):
    identities, _ = native.processes(runtime, root)
    logs = []
    for row in runtime["processes"]:
        role = row["role"]
        require(unlinked(Path(row["javaExecutable"])) == unlinked(REPO.parent / "toolchain/jdk21/bin/java.exe"), "Native caster used an unreviewed executable")
        launch, name = root / "launch" / role, "tcpCasterAudit" + role.capitalize()
        for suffix, key in (("RunVmArgs.txt", "vmArgsSha256"), ("RunProgramArgs.txt", "programArgsSha256"), ("RunClasspath.txt", "classpathArgsSha256")):
            require(sha(file(root, launch / (name + suffix), 1024 * 1024)) == row[key], "Actual immutable native caster launch arguments changed")
        vm = file(root, launch / (name + "RunVmArgs.txt"), 1024 * 1024).decode("utf-8-sig")
        require("-Dthaumcraft.tcpCasterAudit=true" in vm and "-XX:ActiveProcessorCount=2" in vm
                and ("-Xmx1024m" if role == "server" else "-Xmx768m") in vm,
                "Wrong-purpose/unbounded actual native caster process")
        require("net.neoforged.devlaunch.Main" in row.get("arguments", "") and str(launch) in row["arguments"], "Actual native caster process did not use its private reviewed launch")
        for stream in ("stdout", "stderr"):
            path = root / f"{role}.{stream}.log"
            require(unlinked(Path(row[stream + "File"])) == path, "Native caster process log escaped its cohort")
            logs.append(evidence(root, path, 32 * 1024 * 1024))
    require("Done (" in file(root, root / "server.stdout.log", 32 * 1024 * 1024).decode("utf-8", "replace")
            and "Stopping server" in file(root, root / "server.stdout.log", 32 * 1024 * 1024).decode("utf-8", "replace"), "Actual dedicated caster server did not become ready and stop normally")
    return identities, logs


def verify_source_loaded(server, source_server, original):
    nbt.compare_snapshot(server["sourceLoadedPlayer"], original)
    expected = {tuple(row["position"]): row for row in source_server["persistenceBaseline"]["worldTiles"]}
    rows = server.get("sourceWorldLoadedTiles")
    require(isinstance(rows, list) and len(rows) == len(expected) and len({tuple(row["position"]) for row in rows}) == len(rows), "Caster phase omitted full before-fixtures/before-first-tick source world observations")
    for row in rows:
        key = tuple(row["position"])
        require(key in expected and row.get("beforeFirstChunkTick") is True
                and all(row.get(name) == value for name, value in expected[key].items()), "Loaded source caster world identity/NBT differs before first tick")
        if "nbt" in row:
            require(native.normalized_tile(row["actualLoadedNbt"]) == native.normalized_tile(row["nbt"]), "Actual source-loaded native machine NBT differs before first tick")


def verify_world(root, world, baseline, source_server, phase):
    rows = baseline.get("worldTiles")
    old = {tuple(row["position"]) for row in source_server["persistenceBaseline"]["worldTiles"]}
    expected = old | REVIEWED_NEW_POSITIONS if phase == "generate" else old
    require(isinstance(rows, list) and len(rows) == len(expected) == 52 and len({tuple(row["position"]) for row in rows}) == len(rows)
            and {tuple(row["position"]) for row in rows} == expected, "Caster final owned-machine set differs from all old and reviewed new targets")
    reader, result = native.WorldReader(root, world), []
    for row in rows:
        position = row["position"]
        require(isinstance(position, list) and len(position) == 3 and all(type(value) is int for value in position), "Invalid native caster owned position")
        actual = reader.block(position)
        require(actual == row["block"], "Independently saved native caster block differs")
        record = {"position": position, "block": actual}
        if "nbt" in row:
            actual_nbt, expected_nbt = reader.tile(position), nbt.snbt(row["nbt"])
            require(native.normalized_tile(actual_nbt) == native.normalized_tile(expected_nbt) if tuple(position) == (274, 67, 0)
                    else actual_nbt == expected_nbt, "Typed independently read saved caster machine differs")
            record["nbtSha256"] = sha(row["nbt"].encode("utf-8"))
        else:
            reader.no_tile(position)
        result.append(record)
    return result


def row_owner(row, field):
    value = row[field]
    require(isinstance(value, dict) and value.get("ownerUuid") == str(nbt.owner_uuid()) and value.get("firststepsStage") == 4,
            "Native mechanic snapshot is not the exact inherited server owner")
    inventory_slots(value["inventoryNbt"]); raw_knowledge(value["knowledgeNbt"]); nbt.compound(nbt.snbt(value["warpNbt"]))
    snapshot_xp(value)
    return value


def client_events(rows, name):
    return [row["detail"] for row in rows if row.get("action") == name]


def server_events(rows, name):
    return [row["details"] for row in rows if row.get("event") == name]


def prove_journals(server, client, server_rows, client_rows):
    for key, event in LEDGER_EVENTS.items():
        ledger = server.get(key)
        require(isinstance(ledger, list) and len(ledger) <= 4096 and ledger == server_events(server_rows, event),
                "Sealed actual caster ledger differs from append-only source events: " + key)
        for row in ledger:
            require(row.get("actionId") in ACTION_IDS and row.get("actionIndex") == ACTION_IDS.index(row["actionId"])
                    and row.get("actualTCP") is True and row.get("mainThread") is True, "Native caster ledger escaped the finite actual TCP main-thread action context")
    prepared = server_events(server_rows, "native-reviewed-action-prepared")
    done = server_events(server_rows, "native-reviewed-action-completed")
    require(tuple(row.get("actionId") for row in prepared) == ACTION_IDS
            and tuple(row.get("actionId") for row in done) == ACTION_IDS[:-1], "Server finite native action graph skips/repeats actual operations")
    for index, row in enumerate(prepared):
        require(row["actionIndex"] == index and row["actualTCP"] is True and row["mainThread"] is True, "Native prepared action index/context differs")
    for index, row in enumerate(done):
        require(row["actionIndex"] == index and row.get("actualClientS2CAcknowledged") is True
                and row["before"] == prepared[index]["before"], "Native committed action differs from its reviewed before/S2C snapshot")
    received = client_events(client_rows, "reviewed-native-action")
    require(tuple(row.get("id") for row in received) == ACTION_IDS
            and all(row.get("index") == index for index, row in enumerate(received)), "Actual client omitted/repeated a finite native caster action")
    completed = [row for row in client_rows if row.get("action") == "native-action-completed"]
    require(len(completed) == len(ACTION_IDS) and tuple(row["actionIndex"] for row in completed) == tuple(range(len(ACTION_IDS))),
            "Actual client did not finish every native caster action")
    require(server["fixtures"] == server_events(server_rows, "fixture"), "Declared native caster fixtures differ from append-only actual source writes")
    screenshots = client_events(client_rows, "native-screenshot")
    require([row.get("path") for row in screenshots] == client["screenshots"], "Sealed screenshots differ from genuine completed native framebuffer callbacks")
    return prepared, done


def verify_discovery(server, client_rows):
    rows = server["nativeDiscoveryReceipts"]
    require(len(rows) == 3 and [row["researchKey"] for row in rows] == ["m_deepdown", "m_uphigh", "f_onfire"]
            and [row["position"] for row in rows] == [[258, 5, 32], [258, 166, 32], [258, 65, 36]],
            "Native source deep/high/fire gates differ from the real periodic/damage route")
    for row in rows:
        before, after = (row_owner(row, field) for field in ("ownerBefore", "ownerAfter"))
        before_keys, after_keys = raw_knowledge(before["knowledgeNbt"])[0], raw_knowledge(after["knowledgeNbt"])[0]
        require(row["researchKey"] not in before_keys and row["researchKey"] in after_keys
                and native.inventory_bag(before["inventoryNbt"]) == native.inventory_bag(after["inventoryNbt"])
                and snapshot_xp(before) == snapshot_xp(after), "Source discoveries were pregranted or changed actual inventory/XP")
        stage = "BASEAUROMANCY" if row["researchKey"] == "f_onfire" else "UNLOCKAUROMANCY"
        require(before_keys[stage]["stage"] == (2 if stage == "BASEAUROMANCY" else 1), "Native discovery bypassed its source-known stage gate")
    received = client_events(client_rows, "native-periodic-or-fire-marker-received")
    require([row.get("marker") for row in received] == ["m_deepdown", "m_uphigh", "f_onfire"]
            and all(row.get("actualAcceptedOwnerS2C") is True for row in received), "Native discovery markers lack accepted actual owner S2C")
    scans = server["nativeScanReceipts"]
    require(len(scans) == 1 and scans[0]["researchKey"] == "!minecraft:piston0" and scans[0]["heldItem"] == "thaumcraft:thaumometer",
            "Actual native piston scan target/source marker differs")
    sent = client_events(client_rows, "native-caster-scan-dispatched")
    require(len(sent) == 1 and sent[0].get("marker") == "!machina" and sent[0].get("itemEntity") is True
            and sent[0].get("readOnlyClientRayMatches") is True and sent[0].get("clientTargetPayload") is False
            and sent[0].get("normalMovementSettleTicks", 0) >= 8, "Piston scan was not aimed through the real native source ray")
    require(client_events(client_rows, "native-caster-scan-received") == [{"marker": "!machina", "actualAcceptedS2C": True}],
            "Native actual piston aspect scan lacks received authoritative knowledge")
    return {"nativePeriodicDiscoveries": 2, "nativeFireDiscovery": True, "nativePistonScan": True}


def verify_stationary_scan_fixture(server_rows, phase):
    rows = server_events(server_rows, "native-stationary-scan-fixture")
    require(len(rows) == (1 if phase == "generate" else 0),
            "Native stationary piston fixture count differs from the reviewed generate/readonly phases")
    if not rows:
        return {}
    row = rows[0]
    require(row.get("actionId") == "scan-real-piston" and row.get("actionIndex") == 1
            and row.get("actualTCP") is True and row.get("mainThread") is True
            and row.get("noGravity") is True and type(row.get("pickupDelay")) is int and row["pickupDelay"] == 32767,
            "Stationary native scan fixture lacks real source context/gravity/pickup state")
    entity = nbt.snbt(row["entityNbt"])
    data = nbt.compound(entity)
    position = nbt.Tag(9, tuple(nbt.Tag(6, value) for value in (262.5, 66.2, 20.5)))
    motion = nbt.Tag(9, tuple(nbt.Tag(6, 0.0) for _ in range(3)))
    require(nbt.snbt(row["positionNbt"]) == data.get("Pos") == position
            and nbt.snbt(row["motionNbt"]) == data.get("Motion") == motion
            and data.get("NoGravity") == nbt.Tag(1, 1) and data.get("PickupDelay") == nbt.Tag(2, 32767)
            and data.get("Item") == stack("minecraft:piston")
            and str(uuid.UUID(row["uuid"])) == row["uuid"] == entity_uuid(entity),
            "Actual typed piston fixture UUID/item/position/motion differs from its native saved entity observation")
    return {"nativeStationaryPistonFixtureVerified": True}


def graph(value):
    data = nbt.compound(nbt.snbt(value) if isinstance(value, str) else value)
    rows = data["nodes"]
    require(rows.kind == 9 and len(rows.value) == 3, "Native GUI graph is not exactly the source three-node focus")
    result = []
    for index, value in enumerate(rows.value):
        row = nbt.compound(value)
        expected = {"id": nbt.Tag(3, index), "parent": nbt.Tag(3, index - 1), "x": nbt.Tag(3, 0), "y": nbt.Tag(3, index),
                    "children": nbt.Tag(11, (index + 1,) if index < 2 else ()),
                    "key": nbt.Tag(8, ("ROOT", "thaumcraft.TOUCH", "thaumcraft.FIRE")[index])}
        if index == 2:
            expected.update({"setting.power": nbt.Tag(3, 1), "setting.duration": nbt.Tag(3, 0)})
        require(all(row.get(key) == item for key, item in expected.items()) and set(row) <= set(expected) | {"target", "trajectory", "complexity"},
                "Native source focus graph identity/edges/default settings/position differs")
        for field in ("target", "trajectory"):
            if field in row:
                require(row[field].kind == 1 and row[field].value in (0, 1), "Native GUI derived supply flag has an invalid typed value")
        if "complexity" in row:
            require(row["complexity"] == nbt.Tag(5, 1.0), "Native source graph complexity multiplier differs")
        result.append(row)
    return result


def verify_focal(server, client, original):
    rows = server["nativeFocalRequests"]
    require(len(rows) == 2 and [row["actionId"] for row in rows] == ["gui-native-focal-draft", "gui-native-focal-craft"],
            "Native focus editor omitted/duplicated its draft and genuine craft")
    all_xp = add_source_research_xp(original, 11)
    for index, row in enumerate(rows):
        graph(row["graphNbt"])
        require(row.get("accepted") is True and row.get("actualContextualTCP") is True and row["craft"] is bool(index)
                and row["position"] == [264, 65, 16] and row["name"] == FOCUS_NAME and type(row["menuId"]) is int and row["menuId"] > 0
                and type(row["session"]) is int and -(1 << 63) <= row["session"] < (1 << 63) and row["session"] != 0
                and type(row["sequence"]) is int and row["sequence"] > 0
                and row["expectedRevision"] == row["revisionBefore"] and row["revisionAfter"] > row["revisionBefore"],
                "Actual accepted native focus editor menu/session/revision/sequence context differs")
        before, after = (row_owner(row, field) for field in ("ownerBefore", "ownerAfter"))
        require(snapshot_xp(before) == all_xp, "Native focus was not paid with the exact11source research XP awards")
        before_tile, after_tile = (nbt.compound(nbt.snbt(row[field])) for field in ("focalBeforeNbt", "focalAfterNbt"))
        require(before_tile["focus"] == stack("thaumcraft:focus_1") and after_tile["focus"] == before_tile["focus"],
                "Source GUI focus input was authored early or changed during draft/payment")
        graph(nbt.Tag(10, after_tile))
        require(after_tile["focusName"] == nbt.Tag(8, FOCUS_NAME), "Native accepted focus constructor name differs")
        if index == 0:
            require(snapshot_xp(after) == all_xp and native.inventory_bag(before["inventoryNbt"]) == native.inventory_bag(after["inventoryNbt"])
                    and after_tile["vis"] == nbt.Tag(5, 0.0) and after_tile["initialVis"] == nbt.Tag(5, 0.0), "Native draft wrote cost/effects/items")
        else:
            require(snapshot_xp(after) == (all_xp[0] - 2, all_xp[1], all_xp[2]) and row.get("xpLevelsPaid") == 2
                    and row.get("complexity") == 4 and row.get("initialVis") == 43
                    and after_tile["vis"] == nbt.Tag(5, 43.0) and after_tile["initialVis"] == nbt.Tag(5, 43.0),
                    "Actual source focus complexity/vis/XP levels debit differs")
            before_bag, after_bag = (native.inventory_bag(value["inventoryNbt"]) for value in (before, after))
            required = stack_bag([stack("thaumcraft:crystal_essence", 1, aspect) for aspect in ("aversio", "ignis")])
            require(before_bag == after_bag + required and nbt.compound(nbt.snbt(row["crystalsPaidNbt"])) == {"aversio": nbt.Tag(3, 1), "ignis": nbt.Tag(3, 1)},
                    "Source focus constructor did not consume exactly the two real native node crystals")
    require(rows[0]["session"] == rows[1]["session"] and rows[0]["menuId"] == rows[1]["menuId"]
            and rows[1]["sequence"] == rows[0]["sequence"] + 1, "Native GUI craft changed its active draft session/sequence")
    payments = server["focalAuraPaymentReceipts"]
    require(3 <= len(payments) <= 256, "Source focal constructor lacks bounded natural processing tick payments")
    remaining = 43.0
    for row in payments:
        require(row["actionId"] == "gui-native-focal-craft" and f32(row["visBefore"]) == remaining
                and f32(row["request"]) == min(20, remaining) and 0 <= row["consumed"] <= row["request"],
                "Actual source focal processing tick requested an unreviewed cost")
        aura_debit(row["auraChunksBefore"], row["auraChunksAfter"], row["consumed"], (16, 1), {(x, z) for x in range(-1, 2) for z in range(-1, 2)})
        remaining = max(0, f32(remaining - f32(row["consumed"])))
        require(f32(row["visAfter"]) == remaining, "Native focal source remaining vis is not its actual conserved payment chain")
    require(remaining == 0 and abs(sum(row["consumed"] for row in payments) - 43) <= 1e-4, "Native focus output became executable before paying all43vis")
    report = client["focusReport"]
    require(report.get("nativeGraphGui") is True and report.get("directClientFocusPackageWrite") is False,
            "Focus driver bypassed its genuine GUI graph")
    events = report["events"]
    require(isinstance(events, list) and 8 <= len(events) <= 64 and all(type(row.get("driverTick")) is int and isinstance(row.get("data"), dict) for row in events),
            "Actual focus GUI native actions are absent/unbounded")
    require(events == client_events(client["actions"], "native-focus-driver"), "Sealed native focus GUI events differ from actual client journal")
    palette = [row["data"] for row in events if row["action"] == "native-focus-palette-click"]
    require([row["elementKey"] for row in palette] == ["thaumcraft.TOUCH", "thaumcraft.FIRE"]
            and all(row.get("nativePaletteClick") is True for row in palette), "Focus graph was not built by native received palette clicks")
    required_events = ("native-focus-name-typed", "native-focus-draft-save-button", "native-focus-draft-received", "native-focus-craft-button",
                       "native-focus-crafting-received", "native-constructed-focus-received")
    selected = [row for row in events if row["action"] in required_events]
    require(tuple(row["action"] for row in selected) == required_events, "Native focus GUI omitted an actual draft/craft/job/result S2C proof")
    complete = selected[-1]["data"]
    require(complete.get("actualFocusSlotS2C") is True and complete["xpLevelBefore"] - complete["xpLevelAfter"] == 2,
            "Native constructed focus result lacks actual slot/XP S2C")
    authored = focus_stack(complete["focusSlotNbt"], False)
    return {"sourceFocusComplexity": 4, "sourceFocusVisPaid": 43, "sourceFocusXpLevelsPaid": 2,
            "sourceFocusNodeCrystalsPaid": {"aversio": 1, "ignis": 1}, "actualNativeGuiConstructed": True}, authored


def verify_storage(server, client, source_server, client_rows):
    rows, received = server["nativeStorageReceipts"], client["nativeStorageReceipts"]
    require(len(rows) == len(received) == 1, "Exact inherited-material native storage transaction missing/duplicated")
    row, accepted = rows[0], received[0]
    fields = ("transactionId", "actionIndex", "theoryCategory", "purpose", "position", "inventoryBeforeNbt", "chestBeforeNbt",
              "inventoryAfterNbt", "chestAfterNbt", "ordinaryEmptyBefore", "ordinaryEmptyAfter", "nativeQuickMoveCount", "nativeClicks")
    require(all(row.get(field) == accepted.get(field) for field in fields) and row["transactionId"] == "caster-storage-1"
            and row["actionIndex"] == 0 and row["purpose"] == "casters" and row["theoryCategory"] == "" and row["position"] == [260, 65, 12]
            and row.get("cursorEmpty") is True and row.get("nativeChestMenu54") is True and row.get("actualServerAccepted") is True
            and accepted.get("actualServerAccepted") is True and accepted.get("actualNativeChestMenu") is True
            and accepted.get("phase") == "await-server" and type(row["menuId"]) is int and row["menuId"] > 0
            and row["menuId"] == accepted["actualNativeMenuId"], "Native inherited storage lacks matching source menu, received full typed state and acceptance")
    chest_before, chest_after = (nbt.snbt(row[field]) for field in ("chestBeforeNbt", "chestAfterNbt"))
    require(chest_before == combined_chest(source_server["persistenceBaseline"]), "Native material storage overwrote preexisting accepted chest slots/components")
    before, after = (native.inventory_bag(row[field]) for field in ("inventoryBeforeNbt", "inventoryAfterNbt"))
    cb, ca = (native.chest_snapshot(row[field]) for field in ("chestBeforeNbt", "chestAfterNbt"))
    moved = before - after
    require(moved and before == after + moved and ca == cb + moved and before + cb == after + ca
            and all(dict(key[1]).get("id") == (8, "thaumcraft:crystal_essence") for key in moved)
            and not any(dict(key[1]).get("id") == (8, "thaumcraft:crystal_essence") for key in after),
            "Native source storage did not preserve/move every exact inherited crystal and every other full typed item")
    require(native.ordinary_empty(row["inventoryBeforeNbt"]) == row["ordinaryEmptyBefore"]
            and native.ordinary_empty(row["inventoryAfterNbt"]) == row["ordinaryEmptyAfter"] >= 8
            and row["ordinaryEmptyAfter"] > row["ordinaryEmptyBefore"], "Native material storage capacity summary differs from actual owner slots")
    clicks = row["nativeClicks"]
    require(isinstance(clicks, list) and 1 <= len(clicks) <= 36 and row["nativeQuickMoveCount"] == len(clicks)
            and clicks == client_events(client_rows, "native-caster-storage-quick-move"), "Native source storage clicks differ from actual native client journal")
    clicked = []
    for click in clicks:
        value = nbt.snbt(click["stackBeforeNbt"])
        require(click.get("transactionId") == row["transactionId"] and click["menuId"] == row["menuId"]
                and type(click["slot"]) is int and 54 <= click["slot"] < 90 and click.get("button") == 0
                and click.get("clickType") == "QUICK_MOVE" and click.get("nativeC2S") is True
                and nbt.compound(value)["id"] == nbt.Tag(8, "thaumcraft:crystal_essence"), "Native storage click escaped real crystal-only source54slot menu")
        clicked.append(value)
    require(stack_bag(clicked) == moved and accepted in client_events(client_rows, "native-caster-storage-completed"),
            "Actual native storage clicks do not account for every unit or lack server-accepted S2C completion")
    require(chest_after == combined_chest(server["persistenceBaseline"]), "Independently saved inherited54slot chest differs from actual last native transaction")
    return {"nativeMaterialStorageTransactions": 1, "nativeInheritedCrystalUnitsStored": sum(moved.values()),
            "previouslyStoredCrystalUnitsPreserved": sum(cb.values()), "savedChestItemUnits": sum(ca.values())}


def verify_pouch_switches(server, client, client_rows):
    rows = server["nativePouchReceipts"]
    require([row["actionId"] for row in rows] == ["native-pouch-insert", "native-pouch-extract", "native-pouch-reinsert"],
            "Source pouch route omitted native insert/remove/reinsert menu actions")
    for index, row in enumerate(rows):
        received = row["clientReceipt"]
        require(type(row["menuId"]) is int and row["menuId"] > 0 and row["actualNativeMenuClosed"] is True
                and row["extract"] is (index == 1) and received in client["casterReceipts"]
                and received in client_events(client_rows, "native-pouch-transfer-received")
                and received.get("actualNativePouchS2C") is True and received.get("nativeC2S") is True
                and received.get("clickType") == "QUICK_MOVE" and received.get("button") == 0
                and received.get("menuId") == row["menuId"] and received.get("actionId") == row["actionId"]
                and received.get("actionIndex") == row["actionIndex"], "Actual native18slot pouch menu transaction lacks matching C2S and received owner/pouch state")
        require(type(received["slot"]) is int and (0 <= received["slot"] < 18 if index == 1 else 18 <= received["slot"] < 54),
                "Native pouch clicked an unreviewed inventory/source slot")
        for field in ("inventoryBeforeNbt", "inventoryAfterNbt", "pouchBeforeNbt", "pouchAfterNbt"):
            require(nbt.snbt(row[field]) == nbt.snbt(received[field]), "Actual typed client/server pouch snapshots differ")
        require(flattened_inventory(row["inventoryBeforeNbt"]) == flattened_inventory(row["inventoryAfterNbt"]),
                "Native pouch GUI created/deleted/mutated any exact item, focus package or component")
        before, after = (pouch_contents(row[field]) for field in ("pouchBeforeNbt", "pouchAfterNbt"))
        require(len(before) == (1 if index == 1 else 0) and len(after) == (0 if index == 1 else 1),
                "Native pouch did not move the actual one constructed focus in the requested direction")
        for field, contents in (("contentsBeforeNbt", before), ("contentsAfterNbt", after)):
            data = nbt.compound(nbt.snbt(row[field]))
            require(set(data) == {"Size", "Items"} and data["Size"] == nbt.Tag(3, 18)
                    and inventory_slots(data["Items"], 18) == contents, "Actual native pouch menu18slots differ from its actual stored component")
        for field, pouch_field in (("inventoryBeforeNbt", "pouchBeforeNbt"), ("inventoryAfterNbt", "pouchAfterNbt")):
            inventory = inventory_slots(row[field])
            require(inventory.get(row["pouchSlot"]) == nbt.snbt(row[pouch_field]), "Actual native owning pouch item differs from protected server menu slot")
    switches = server["nativeFocusSwitchReceipts"]
    require([row["actionId"] for row in switches] == ["native-focus-select", "native-focus-remove", "native-focus-reselect"],
            "Native source select/remove/reselect payload route differs")
    sent = client_events(client_rows, "native-source-focus-payload-dispatched")
    accepted = client_events(client_rows, "native-source-focus-transfer-received")
    require(len(sent) == len(accepted) == 3, "Native caster source focus payload/S2C receipt set differs")
    for index, row in enumerate(switches):
        remove = index == 1
        before, after = (row_owner(row, field) for field in ("ownerBefore", "ownerAfter"))
        require(row["requestedKey"] == ("REMOVE" if remove else SOURCE_FOCUS_KEY)
                and before["selectedSlot"] == after["selectedSlot"] == 0
                and inventory_slots(before["inventoryNbt"])[0] == nbt.snbt(row["heldBeforeNbt"])
                and inventory_slots(after["inventoryNbt"])[0] == nbt.snbt(row["heldAfterNbt"]),
                "Actual source caster selection key/held owner/component identity differs")
        old, fresh = (caster_focus(row[field]) for field in ("heldBeforeNbt", "heldAfterNbt"))
        require((old is not None) is remove and (fresh is None) is remove, "Native source focus component was not moved in the requested direction")
        before_pouch, after_pouch = (pouch_contents(row[field]) for field in ("pouchBeforeNbt", "pouchAfterNbt"))
        require(len(before_pouch) == (0 if remove else 1) and len(after_pouch) == (1 if remove else 0),
                "Native source selection/removal did not preserve focus through the actual crafted pouch")
        require(flattened_inventory(before["inventoryNbt"], True) == flattened_inventory(after["inventoryNbt"], True)
                and snapshot_xp(before) == snapshot_xp(after) and nbt.snbt(before["warpNbt"]) == nbt.snbt(after["warpNbt"]),
                "Native source focus switching changed any item/package/XP/warp beyond its exact derived sorting cache")
        for received, is_after in ((sent[index], False), (accepted[index], True)):
            require(received.get("actionId") == row["actionId"] and received.get("actionIndex") == row["actionIndex"]
                    and received.get("focusKey") == row["requestedKey"] and received.get("remove") is remove
                    and received.get("nativeC2SPayload") == "thaumcraft:caster_action" and received.get("physicalFKeyClaimed") is False
                    and nbt.snbt(received["inventoryBeforeNbt"]) == nbt.snbt(before["inventoryNbt"]),
                    "Native source focus C2S payload differs from the actual server before/ownership/context")
            if is_after:
                require(received.get("actualNativeFocusS2C") is True and nbt.snbt(received["inventoryAfterNbt"]) == nbt.snbt(after["inventoryNbt"])
                        and received in client["casterReceipts"], "Native source focus transfer lacks exact server-owned received inventory component")
    return {"nativePouchSlots": 18, "nativePouchTransactions": 3, "nativeSourceFocusPayloads": 3,
            "physicalFKeyClaimed": False, "sourceFocusSortingKey": SOURCE_FOCUS_KEY}


def verify_casts(server, client, client_rows):
    payments, effects = server["castPaymentReceipts"], server["nativeCastEffectReceipts"]
    action_ids = ["native-cast-without-goggles", "native-cast-with-goggles"]
    require([row["actionId"] for row in payments] == action_ids and [row["actionId"] for row in effects] == action_ids,
            "Two actual native paid basic casts/Fire target effects are missing or repeated")
    native_received = client_events(client_rows, "native-caster-hit-and-aura-received")
    require(len(native_received) == 2 and len({row["targetUuid"] for row in effects}) == 2, "Native actual casts did not use two separate physical source targets")
    paid = []
    for index, (row, effect, received) in enumerate(zip(payments, effects, native_received)):
        before, after = (row_owner(row, field) for field in ("ownerBefore", "ownerAfter"))
        modifier, cost = f32(1 if index == 0 else .95), f32(f32(.8) * f32(1 if index == 0 else .95))
        require(f32(row["requestedVis"]) == f32(.8) and f32(row["modifier"]) == modifier and f32(row["effectiveCost"]) == cost
                and row.get("crafting") is False and row.get("simulate") is False and row.get("area") == 0 and row.get("success") is True,
                "Actual source native caster consumed an unreviewed amount/discount/area or simulation")
        position = row["position"]
        require(position == ([265, 65, 16] if index == 0 else [269, 65, 16]),
                "Actual native cast owner position differs from its normal aimed source range")
        dry = row["targetDryBeforeCast"]
        roof = [{"position": [x, 69, z], "block": "minecraft:stone"}
                for x in range((268 if index == 0 else 272) - 1, (268 if index == 0 else 272) + 2)
                for z in range(15, 18)]
        require(set(dry) == {"worldRaining", "targetInWaterRainOrBubble", "canSeeSky", "targetPosition", "roofBlocks"}
                and type(dry["worldRaining"]) is bool and dry["targetInWaterRainOrBubble"] is False and dry["canSeeSky"] is False
                and dry["targetPosition"] == [268 if index == 0 else 272, 65, 16] and dry["roofBlocks"] == roof,
                "Actual native cast target was not dry beneath the exact nine observed physical stone roof blocks")
        actual = aura_debit(row["auraChunksBefore"], row["auraChunksAfter"], cost, (position[0] >> 4, position[2] >> 4), {(0, 0)})
        require(abs(row["actualVisPaid"] - actual) <= 1e-4, "Native actual caster vis summary differs from independently reconstructed float chunks")
        before_held, after_held = (nbt.snbt(row[field]) for field in ("heldBeforeNbt", "heldAfterNbt"))
        require(before_held == after_held and caster_focus(before_held) is not None
                and nbt.snbt(before["inventoryNbt"]) == nbt.snbt(after["inventoryNbt"])
                and snapshot_xp(before) == snapshot_xp(after), "Native actual cast mutated persisted focus package/runtime UUID/owner items or XP")
        slots = inventory_slots(before["inventoryNbt"])
        require((slots.get(103) == stack("thaumcraft:goggles")) is bool(index), "Source cast discount does not correspond to actual native crafted helmet equipment")
        require(effect.get("sourceType") == "minecraft:fireball" and effect.get("ownerUuid") == str(nbt.owner_uuid())
                and effect["healthBefore"] == 10 and effect["actualFinalDamage"] == 4 and effect["healthAfter"] == 6
                and type(effect["remainingFireTicks"]) is int and 0 < effect["remainingFireTicks"] <= 20
                and effect.get("actualIgnitionObserved") is True, "Actual source Touch/Fire did not damage4/ignite1sec the physical default10HP cow")
        require(received.get("actionId") == row["actionId"] and received.get("actionIndex") == row["actionIndex"]
                and received.get("targetUuid") == effect["targetUuid"] and received.get("readOnlyNativeRayMatches") is True
                and received.get("onFireS2C") is True and received.get("actualEntityDataS2C") is True
                and received.get("actualFreshAuraS2C") is True and f32(received["expectedSourceVis"]) == cost
                and received["healthBefore"] == 10 and 0 < received["healthAfter"] <= 6 and received in client["casterReceipts"],
                "Actual aimed native use-item cast lacks received real target damage/fire and fresh aura S2C")
        paid.append(cost)
    equipped = client_events(client_rows, "native-goggles-equipped")
    require(len(equipped) == 1 and equipped[0].get("helmetSlot") == 5 and equipped[0].get("actualNativeEquipmentS2C") is True
            and f32(equipped[0]["discount"]) == f32(.05) and nbt.snbt(equipped[0]["helmetNbt"]) == stack("thaumcraft:goggles"),
            "Native five-percent goggles discount lacks actual armor slot take/S2C proof")
    return {"nativeSpellCasts": 2, "nativeCasterVisPaid": paid, "sourceGogglesDiscount": .05,
            "sourceDamagePerCast": 4, "sourceIgnitionSeconds": 1, "separateActualCowTargets": 2,
            "nativeDryTargetsUnderPhysicalRoofs": True}


def reviewed_arena():
    result = {(264, 64, 16): "minecraft:stone", (264, 66, 16): "thaumcraft:arcane_workbench_charger",
              (258, 64, 36): "minecraft:netherrack", (258, 65, 36): "minecraft:fire"}
    for y in (5, 166):
        for x in range(257, 260):
            for z in range(31, 34):
                result[(x, y - 1, z)] = "minecraft:stone"
                for dy in range(3):
                    result[(x, y + dy, z)] = "minecraft:air"
    result.update({position: "minecraft:stone" for position in REVIEWED_ROOF_POSITIONS})
    return result


def verify_fixtures_inventory(server, state, original, source_server, world_reader, authored):
    fixtures = server["fixtures"]
    require(isinstance(fixtures, list) and 123 <= len(fixtures) <= 512, "Native caster tangible fixture set is absent/unbounded")
    physical, teleports, material, targets, scans = {}, {}, [], [], []
    forbidden = set(CRAFT_COUNTS)
    source_matrices = Counter()
    for action_id in ACTION_IDS:
        if action_id.startswith("craft-native-"):
            source_matrices += stack_bag(recipe_matrix(action_id).values())
    source_matrices += stack_bag([stack("thaumcraft:crystal_essence", n, aspect) for aspect, n in
                                (("vitreus", 20), ("praecantatio", 10), ("auram", 5), ("ordo", 1), ("aversio", 1), ("ignis", 1))])
    for row in fixtures:
        name = row.get("name")
        if name == "physical-caster-arena":
            position = tuple(row["position"])
            require(position not in physical and position in reviewed_arena() and row["block"] == reviewed_arena()[position],
                    "Declared native caster arena wrote an unreviewed/duplicate source world block")
            physical[position] = row["block"]
        elif name == "owner-native-arena-teleport":
            action = row.get("actionId")
            require(action in ACTION_IDS and action not in teleports and all(type(row.get(key)) in (int, float) for key in ("x", "y", "z")),
                    "Declared caster teleport escaped the finite action graph")
            teleports[action] = row
        elif name == "tangible-recipe-material":
            value = nbt.snbt(row["stackNbt"]); data = nbt.compound(value)
            require(row.get("actionId") in ACTION_IDS and row.get("actionIndex") == ACTION_IDS.index(row["actionId"])
                    and row.get("actualTCP") is True and row.get("mainThread") is True and row["item"] not in forbidden
                    and data["id"] == nbt.Tag(8, row["item"]) and data["count"] == nbt.Tag(3, row["count"])
                    and 0 < row["count"] <= 64, "Native material fixture supplied paid outputs or unreviewed typed amounts/components")
            material.append(value)
        elif name == "physical-native-cow-target":
            data = nbt.compound(nbt.snbt(row["nbt"]))
            require(row.get("actionId") in ("native-cast-without-goggles", "native-cast-with-goggles") and row.get("type") == "minecraft:cow"
                    and row.get("health") == 10 and data["Health"] == nbt.Tag(5, 10.0) and data["NoAI"] == nbt.Tag(1, 1)
                    and data["PersistenceRequired"] == nbt.Tag(1, 1), "Native physical target changed its default source health/type/AI fixture declaration")
            targets.append(row)
        elif name == "native-piston-scan-target":
            require(row.get("actionId") == "scan-real-piston" and row.get("item") == "minecraft:piston" and row.get("count") == 1
                    and nbt.snbt(row["stackNbt"]) == stack("piston"), "Actual native scan fixture is not the single reviewed real piston entity")
            scans.append(row)
        else:
            raise ValueError("Unreviewed native caster fixture kind: " + str(name))
    require(physical == reviewed_arena() and len(physical) == 94 and set(teleports) == set(ACTION_IDS) and len(targets) == 2 and len(scans) == 1,
            "Native physical/material fixture declaration omitted its actual finite arena/teleports/targets")
    for position, block in physical.items():
        # Fire age varies naturally; this verifies the actual stable source block identity.
        require(world_reader.block(position) == block, "Independently saved declared native caster arena block differs")
    for action, position in (("native-deep-discovery", (258.5, 5, 32.5)), ("native-high-discovery", (258.5, 166, 32.5)),
                             ("native-fire-discovery", (258.5, 65, 36.5)), ("native-cast-without-goggles", (265.5, 65, 16.5)),
                             ("native-cast-with-goggles", (269.5, 65, 16.5))):
        require(tuple(teleports[action][key] for key in ("x", "y", "z")) == position, "Native source discovery/cast fixture teleport escaped its exact source range")
    given = stack_bag(material)
    require(all(count <= source_matrices.get(key, 0) for key, count in given.items()), "Material fixture count/component exceeds the entire finite real recipe budget")
    outputs = []
    for row in server["nativeCraftReceipts"]:
        value = nbt.snbt(row["outputNbt"])
        require(value == stack(row["item"]), "Actual native crafted output contains unreviewed authored/cache/aspect components")
        outputs.append(value)
        if row["actionId"].startswith("craft-native-"):
            matrix = nbt.snbt(row["sourceMatrixNbt"])
            require(matrix.kind == 9, "Native source recipe event inventory is not typed")
            actual = {}
            for child in matrix.value:
                item = dict(nbt.compound(child)); slot = nbt.scalar(item.pop("slot"), 3)
                require(slot not in actual and 0 <= slot < 15, "Native actual source recipe event matrix contains duplicate/unreviewed slots")
                actual[slot] = nbt.Tag(10, item)
            require(actual == recipe_matrix(row["actionId"]), "Source ItemCraftedEvent recipe inventory differs from exact finite native matrix")
    starting = flattened_inventory(original["inventory"]) + native.chest_snapshot(str_tag(combined_chest(source_server["persistenceBaseline"])))
    ending = flattened_inventory(state["inventory"], True) + native.chest_snapshot(str_tag(combined_chest(server["persistenceBaseline"])))
    inputs = source_matrices + stack_bag([stack("thaumcraft:wand_workbench"), stack("thaumcraft:focus_1")])
    produced = stack_bag(outputs) + stack_bag([authored])
    require(starting + given + produced == ending + inputs,
            "Full actual player/chest/components inventory does not conserve accepted source + declared materials + genuine crafted outputs - every native recipe/crystal/construction/place payment")
    final_slots = inventory_slots(state["inventory"])
    require(final_slots.get(103) == stack("thaumcraft:goggles") and state["selectedSlot"] == 0
            and caster_focus(final_slots[0]) is not None and len([item for item in final_slots.values() if nbt.compound(item)["id"] == nbt.Tag(8, "thaumcraft:caster_basic")]) == 2,
            "Independently saved final owner lost inherited/native caster or paid goggles/focus equipment")
    final_pouches = [item for item in final_slots.values() if nbt.compound(item)["id"] == nbt.Tag(8, "thaumcraft:focus_pouch")]
    require(len(final_pouches) == 1 and not pouch_contents(final_pouches[0]), "Independently saved final owner pouch contains a duplicate/missing constructed focus")
    tiles = {tuple(row["position"]): row for row in server["persistenceBaseline"]["worldTiles"]}
    constructor = nbt.compound(nbt.snbt(tiles[(264, 65, 16)]["nbt"]))
    require(tiles[(264, 65, 16)]["block"] == "thaumcraft:wand_workbench" and "focus" not in constructor
            and not constructor["nodes"].value and constructor["vis"] == nbt.Tag(5, 0.0) and constructor["initialVis"] == nbt.Tag(5, 0.0),
            "Independently saved real constructor retains input/output/job rather than native picked-up completed focus")
    return {"declaredPhysicalArenaWrites": len(physical), "declaredRecipeMaterialStacks": len(material),
            "declaredTargetEntities": len(targets), "fullNativeInventoryComponentConservation": True}


def entity_uuid(data):
    value = nbt.scalar(nbt.compound(data)["UUID"], 11)
    require(len(value) == 4, "Actual native saved target UUID is not four typed ints")
    return str(uuid.UUID(bytes=b"".join(struct.pack(">I", number & 0xffffffff) for number in value)))


def entity_chunk(root, world, x, z):
    """Entity Anvil chunks have Position int[2], rather than terrain xPos/zPos."""
    path = world / "entities" / f"r.{x >> 5}.{z >> 5}.mca"
    data = file(root, path, 64 * 1024 * 1024)
    require(len(data) >= 8192 and len(data) % 4096 == 0, "Invalid native entity Anvil sectors")
    index = (x & 31) + (z & 31) * 32
    location = struct.unpack_from(">I", data, index * 4)[0]; offset, sectors = location >> 8, location & 255
    require(offset >= 2 and sectors > 0 and (offset + sectors) * 4096 <= len(data), "Missing/out-of-range native target entity chunk sector")
    start = offset * 4096; length = struct.unpack_from(">I", data, start)[0]
    require(1 < length <= sectors * 4096 - 4 and not data[start + 4] & 128, "Invalid/external native entity chunk length")
    value = nbt.compound(native.decompress_chunk(data[start + 5:start + 4 + length], data[start + 4]))
    require(value["Position"] == nbt.Tag(11, (x, z)) and value["Entities"].kind == 9 and len(value["Entities"].value) <= 1024,
            "Native saved entity chunk identity/count differs")
    return value["Entities"].value


ENTITY_STABLE = ("UUID", "Health", "NoAI", "Pos", "PersistenceRequired")


def verify_native_entity_availability(server, phase, server_rows, expected):
    targets = {row["uuid"]: row for row in expected}

    def same_stable(row, prior):
        fresh, original = (nbt.compound(nbt.snbt(value["nbt"])) for value in (row, prior))
        require(row["uuid"] == prior["uuid"] == entity_uuid(nbt.Tag(10, fresh)) and row["type"] == prior["type"] == "minecraft:cow"
                and fresh.get("Health") == nbt.Tag(5, row["health"])
                and all(fresh.get(field) == original[field] for field in ENTITY_STABLE),
                "Native target availability/reconnect changed its actual stable typed UUID/health/AI/position")

    availability = server.get("nativeFinalEntityAvailability")
    require(isinstance(availability, dict) and server_events(server_rows, "native-final-target-entities-observed") == [availability]
            and availability.get("actualTCP") is True and availability.get("mainThread") is True
            and availability.get("ownerEpoch") == (2 if phase == "generate" else 1)
            and type(availability.get("stableTicks")) is int and 40 <= availability["stableTicks"] < 600,
            "Native final targets lack bounded forty-tick genuine loaded/ticking/alive observations")
    observed = availability.get("entities")
    require(isinstance(observed, list) and len(observed) == 2 and {row["uuid"] for row in observed} == set(targets),
            "Native final availability omitted a genuine saved target")
    for row in observed:
        require(all(row.get(field) is True for field in ("actualLookupLoaded", "actualEntityChunkLoaded", "actualEntityTicking", "alive")),
                "Native final target is not the actual current loaded, ticking and alive entity")
        same_stable(row, targets[row["uuid"]])

    loaded = server.get("nativeReconnectEntities")
    require(isinstance(loaded, list) and 0 <= len(loaded) <= 2
            and loaded == server_events(server_rows, "native-reconnect-target-entity-loaded")
            and len({row["uuid"] for row in loaded}) == len(loaded),
            "Native reconnect entity replacements differ from actual bounded append-only load events")
    baseline = server.get("reconnectEntityBaseline")
    if phase == "reload":
        require(baseline == [] and loaded == [] and server_events(server_rows, "native-reconnect-baseline-sealed") == [],
                "Readonly new JVM fabricated a generation reconnect entity receipt")
        return
    seals = server_events(server_rows, "native-reconnect-baseline-sealed")
    require(len(seals) == 1 and isinstance(baseline, list) and len(baseline) == 2 and seals[0].get("worldEntities") == baseline
            and {row["uuid"] for row in baseline} == set(targets),
            "Actual pre-disconnect reconnect baseline omitted its two genuine target snapshots")
    for row in baseline:
        same_stable(row, targets[row["uuid"]])
    for row in loaded:
        require(row["uuid"] in targets and all(row.get(field) is True for field in
                ("loadedFromDisk", "beforeFirstEntityTick", "naturalReconnect", "replacedOriginalObject"))
                and type(row.get("previousObjectRemoved")) is bool,
                "Reconnect target replacement was not a genuine before-first-tick natural disk load")
        same_stable(row, targets[row["uuid"]])


def verify_entities(root, world, server, source_server, phase, server_rows):
    expected = server["persistenceBaseline"].get("worldEntities")
    require(isinstance(expected, list) and len(expected) == 2 and len({row["uuid"] for row in expected}) == 2,
            "Native saved target entity ledger omits the actual two separate source casts")
    result, chunks = [], {}
    for row in expected:
        reported = nbt.snbt(row["nbt"]); data = nbt.compound(reported)
        require(row["type"] == "minecraft:cow" and row["uuid"] == entity_uuid(reported) and 0 < row["health"] <= 6
                and data["Health"] == nbt.Tag(5, row["health"]) and data["NoAI"] == nbt.Tag(1, 1)
                and data["PersistenceRequired"] == nbt.Tag(1, 1) and data["Pos"].kind == 9 and len(data["Pos"].value) == 3
                and all(tag.kind == 6 for tag in data["Pos"].value), "Actual saved native target type/UUID/health/AI/position differs")
        x, _, z = (tag.value for tag in data["Pos"].value); key = int(x // 16), int(z // 16)
        require(key in ((16, 1), (17, 1)), "Actual native target moved outside finite source arena chunks")
        if key not in chunks:
            chunks[key] = entity_chunk(root, world, *key)
        found = [tag for tag in chunks[key] if entity_uuid(tag) == row["uuid"]]
        require(len(found) == 1, "Independently saved native target entity is missing/duplicated")
        saved = nbt.compound(found[0])
        require(saved["id"] == nbt.Tag(8, row["type"]) and all(saved.get(field) == data[field] for field in ENTITY_STABLE),
                "Independently saved actual target stable typed identity/health/AI/position differs from final native observer")
        result.append({"uuid": row["uuid"], "type": row["type"], "health": row["health"], "stableTypedFields": list(ENTITY_STABLE),
                       "diskNbtSha256": sha(str_tag(found[0]).encode()), "nativeReportNbtSha256": sha(row["nbt"].encode())})
    if phase == "generate":
        actual = {row["targetUuid"] for row in server["nativeCastEffectReceipts"]}
        require(actual == {row["uuid"] for row in expected}, "Actual source cast target UUIDs differ from independently persisted physical entities")
        require(server.get("sourceLoadedEntities") == [], "Generation reported nonexisting inherited caster targets")
    else:
        source = {row["uuid"]: row for row in source_server["persistenceBaseline"]["worldEntities"]}
        loaded = server.get("sourceLoadedEntities")
        require(isinstance(loaded, list) and len(loaded) == 2 and {row["uuid"] for row in loaded} == set(source)
                and loaded == server_events(server_rows, "natural-source-entity-loaded"), "Readonly caster new JVM lacks actual before-first-tick target loading observations")
        for row in [*expected, *loaded]:
            fresh, original = (nbt.compound(nbt.snbt(value["nbt"])) for value in (row, source[row["uuid"]]))
            require(all(fresh.get(field) == original[field] for field in ENTITY_STABLE), "Readonly native target changed stable source typed identity/health/position")
        require(all(row.get("loadedFromDisk") is True and row.get("beforeFirstEntityTick") is True for row in loaded),
                "Readonly actual target observation was fabricated after native entity ticks")
    verify_native_entity_availability(server, phase, server_rows, expected)
    return result


def verify_generation(server, client, state, original, source_server, root, world, server_rows, client_rows):
    require(REVIEWED_GAMEPLAY_CONTRACT, "Native caster gameplay ledger contract has not been completely reviewed")
    prove_journals(server, client, server_rows, client_rows)
    require(server.get("castersComplete") is True and server.get("reconnectProven") is True and server.get("ownerEpoch") == 2
            and server.get("actualOldConnectionClosed") is True and client.get("reconnectProven") is True
            and client.get("connectionEpoch") == 2 and client.get("clientFocusPackageWrites") is False
            and client.get("physicalFKeyClaimed") is False, "Native caster generation lacks actual gameplay/component ownership and second TCP epoch")
    mechanics = verify_research(server, client, state, original)
    mechanics.update(verify_crafting(server)); mechanics.update(verify_discovery(server, client_rows))
    mechanics.update(verify_stationary_scan_fixture(server_rows, "generate"))
    focal, authored = verify_focal(server, client, original); mechanics.update(focal)
    mechanics.update(verify_storage(server, client, source_server, client_rows))
    mechanics.update(verify_pouch_switches(server, client, client_rows)); mechanics.update(verify_casts(server, client, client_rows))
    mechanics.update(verify_fixtures_inventory(server, state, original, source_server, native.WorldReader(root, world), authored))
    final_xp = add_source_research_xp(original, 11)
    require(snapshot_xp(state) == (final_xp[0] - 2, final_xp[1], final_xp[2]) and state["warp"] == original["warp"],
            "Independent saved caster XP/warp differs beyond55source research points and2paid constructor levels")
    source_research = native.knowledge_map(original)[0]
    final_research = native.knowledge_map(state)[0]
    allowed_keys = set(source_research) | set(FINAL_STAGES) | {"m_deepdown", "m_uphigh", "f_onfire", "!machina", "!minecraft:piston0"} | set(mechanics["sourceCraftMarkers"])
    require(set(final_research) == allowed_keys, "Independently saved full caster research contains unearned/fixture keys beyond source native progression/scan/craft events")
    fire = server_events(server_rows, "native-owner-fire-damage-accepted")
    require(fire and len(fire) <= 64 and all(row["actionId"] == "native-fire-discovery" and row["sourceType"] in ("minecraft:in_fire", "minecraft:on_fire")
                and 0 < row["amount"] <= 1 for row in fire), "Native source fire discovery lacks actual accepted living damage event")
    disconnected = client_events(client_rows, "native-tcp-disconnect")
    dispatched = client_events(client_rows, "native-tcp-reconnect-dispatched")
    restored = client_events(client_rows, "actual-tcp-reconnect-owner-restored")
    require(len(disconnected) == len(dispatched) == len(restored) == 1 and dispatched[0].get("nativeConnectScreen") is True
            and dispatched[0].get("allOwnerFacadesCleared") is True and all(restored[0].get(key) is True for key in
                    ("newConnectionIdentity", "newLocalPlayerIdentity", "fullInventoryKnowledgeWarpXpMatch", "onlyPopupExcluded")),
            "Actual native caster TCP disconnect/connect/S2C owner restore is incomplete")
    rebound = server_events(server_rows, "native-second-owner-tcp-reconnect")
    require(len(rebound) == 1, "Dedicated server did not observe the genuine replacement TCP owner")
    nbt.compare_snapshot(rebound[0], state)
    sealed = server_events(server_rows, "native-reconnect-baseline-sealed")
    require(len(sealed) == 1, "Native reconnect lacked its actual pre-disconnect owner/entities baseline")
    nbt.compare_snapshot(sealed[0], state)
    nbt.compare_snapshot(client["receivedServerBaseline"], state)
    mechanics.update({"sourceResearchXpPoints": 55, "savedTotalExperience": state["totalExperience"],
                      "savedExperienceLevel": state["experienceLevel"], "savedExperienceProgress": state["experienceProgress"],
                      "actualTcpReconnectEpoch": 2, "finiteNativeActions": len(ACTION_IDS)})
    return mechanics


def verify_reload_world(server, client, source_server, client_rows):
    source, final = source_server["persistenceBaseline"], server["persistenceBaseline"]
    source_owner = {key: value for key, value in source.items() if key not in ("worldTiles", "worldEntities")}
    final_owner = {key: value for key, value in final.items() if key not in ("worldTiles", "worldEntities")}
    require(source_owner == final_owner, "Readonly native caster reload changed full saved owner fields")
    expected = {tuple(row["position"]): row for row in source["worldTiles"]}
    for row in final["worldTiles"]:
        prior = expected[tuple(row["position"])]
        require(row["block"] == prior["block"] and ("nbt" in row) == ("nbt" in prior), "Readonly caster world block/BE identity changed")
        if "nbt" in row:
            old, new = (dict(nbt.compound(native.normalized_tile(value["nbt"]))) for value in (prior, row))
            if row["block"] == "thaumcraft:smelter_basic":
                previous, current = nbt.scalar(old.pop("BurnTime"), 2), nbt.scalar(new.pop("BurnTime"), 2)
                require(0 <= current <= previous, "Readonly native smelter counter increased")
            require(old == new, "Readonly native caster private machine fields changed beyond source-only old matrix save byte/decreasing spent fuel")
    observations = client.get("reloadedWorldObservations")
    require(isinstance(observations, list) and len(observations) == len(expected) == 52
            and len({tuple(row["position"]) for row in observations}) == len(expected), "Readonly native client omitted actual reloaded world target observations")
    for row in observations:
        prior = expected[tuple(row["position"])]
        require(row.get("actualNativeChunkS2C") is True and row["block"] == prior["block"], "Readonly caster block lacks actual native Chunk S2C")
        if "nbt" not in prior:
            continue
        require(row.get("actualPublicBlockEntityS2C") is True and row.get("privateDiskFieldsComparedOnServer") is True,
                "Readonly caster tile lacks actual public BE update/independent private saved fields")
        visible, disk = (nbt.compound(nbt.snbt(value)) for value in (row["publicSyncNbt"], prior["nbt"]))
        for key, value in visible.items():
            if key == "BurnTime" and prior["block"] == "thaumcraft:smelter_basic":
                require(value.kind == 2 and row.get("nativeBurnCounterMayAdvance") is True, "Unreviewed readonly public fuel counter exception")
            elif key == "renderNote":
                require(value == nbt.Tag(1, int("note" in disk)), "Readonly public native research-note display differs")
            elif key == "Aspects":
                require(native.typed_aspects(nbt.Tag(10, {key: value})) == native.typed_aspects(nbt.Tag(10, {key: disk[key]})),
                        "Readonly native public aspects differ from full source disk state")
            else:
                require(value == disk.get(key), "Readonly actual native BE public field differs from real copied save")
    complete = [row for row in client_rows if row.get("action") == "native-world-reload-observed"]
    loaded = [row for row in client_rows if row.get("action") == "server-phase" and row["detail"].get("phase") == "loaded"]
    require(len(complete) == len(loaded) == 1 and complete[0]["clientTick"] - loaded[0]["clientTick"] >= 40,
            "Native readonly new-JVM owner/world did not remain stable for40actual client ticks")


def verify_reload(server, client, state, original, source_server, server_rows, client_rows):
    verify_stationary_scan_fixture(server_rows, "reload")
    require(server.get("castersComplete") is True and server.get("newJvmReloadProven") is True
            and server.get("loadedWithoutGrants") is True and server.get("fixtures") == []
            and client.get("loadedWithoutGrants") is True and client.get("zeroGameplayActions") is True, "Readonly new-JVM caster phase wrote fixtures or gameplay state")
    require(original["inventory"] == state["inventory"] and original["selectedSlot"] == state["selectedSlot"]
            and nbt.canonical_knowledge(original["knowledge"]) == nbt.canonical_knowledge(state["knowledge"])
            and original["warp"] == state["warp"] and original["totalExperience"] == state["totalExperience"]
            and original["experienceLevel"] == state["experienceLevel"]
            and struct.pack(">f", original["experienceProgress"]) == struct.pack(">f", state["experienceProgress"]), "Readonly native caster new JVM changed full saved owner")
    verify_reload_world(server, client, source_server, client_rows)
    allowed = {"client-initialized", "server-phase", "actual-tcp-play-owner", "accepted-owner-snapshot", "native-screenshot", "native-world-reload-observed", "native-source-owner-observed"}
    require(all(row.get("action") in allowed for row in client_rows), "Readonly native caster phase dispatched gameplay actions")
    require(all(server.get(key) == [] and server_events(server_rows, event) == [] for key, event in LEDGER_EVENTS.items())
            and client.get("casterReceipts") == [] and client.get("nativeStorageReceipts") == []
            and client["focusReport"]["events"] == [] and client["researchReport"]["researchReceipts"] == []
            and client["researchReport"]["theoryReceipts"] == [], "Readonly caster new JVM executed a native gameplay receipt/GUI/card/recipe/payment")
    require(REVIEWED_GAMEPLAY_CONTRACT, "Native caster readonly zero-ledger contract has not been completely reviewed")
    return {"newJvmReloadProven": True, "fullNativeOwnerAndTilesPreserved": True}


def verify(root):
    root = unlinked(root)
    require(root.parent == unlinked(REPO / "port/build/tcp-casters") and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", root.name), "Expected immediate private native caster cohort")
    marker = document(root, root / "server/.tcp-casters-audit.json", 16384)
    require(marker.get("marker") == MARKER and type(marker.get("schemaVersion")) is int and marker["schemaVersion"] == 1
            and marker.get("purpose") == "native-casters" and marker.get("networkProtocolVersion") == "6"
            and marker.get("goal") == "casters" and marker.get("auditPhase") in ("generate", "reload")
            and marker.get("runId") == root.name and type(marker.get("port")) is int and 1024 <= marker["port"] <= 65535,
            "Invalid native caster isolation identity")
    require(str(uuid.UUID(marker["token"])) == marker["token"] and str(uuid.UUID(marker["sourceToken"])) == marker["sourceToken"]
            and type(marker.get("sourcePort")) is int and 1024 <= marker["sourcePort"] <= 65535
            and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", marker["sourceRunId"]) and marker["sourceRunId"] != marker["runId"], "Invalid distinct canonical native caster source identity")
    for key in ("sourceServerReportSha256", "sourceClientReportSha256", "sourceRuntimeSha256", "sourceVerificationSha256", "worldManifestSha256"):
        require(re.fullmatch(r"[0-9a-f]{64}", marker[key]), "Invalid immutable native caster source hash")
    same_identity(marker, marker, "server")
    same_identity(document(root, root / "client/.tcp-casters-audit.json", 16384), marker, "client")
    source, source_world, source_server, source_client, source_runtime, world, copied = provenance(root, marker)
    runtime = document(root, root / "runtime.json")
    same_identity(runtime, marker)
    source_processes, _ = native.processes(source_runtime, source)
    actual_processes, logs = processes(root, runtime, marker)
    require(actual_processes.isdisjoint(source_processes), "Native caster phase reused an accepted source JVM identity")
    server, client = (document(root, root / role / f"{role}-report.json") for role in ("server", "client"))
    for role, report in (("server", server), ("client", client)):
        same_identity(report, marker, role)
        owner_id = report["persistenceBaseline"].get("ownerUuid") if role == "server" else report.get("ownerUuid")
        require(report.get("passed") is True and report.get("status") == "completed" and report.get("actualTCP") is True
                and owner_id == str(nbt.owner_uuid()), "Native caster role lacks actual completed PLAY TCP owner")
    require(server.get("dedicatedServer") is True and server.get("saveFlushed") is True and server.get("diskNbtVerified") is True
            and server.get("noKnowledgeResearchFixtures") is True and server.get("noAspectOrRecipeEffectFixtures") is True
            and server.get("noFocusPackageFixtures") is True and server.get("noXpAuraDamageSetters") is True
            and server.get("rngNotReplaced") is True, "Native caster production/source save provenance missing")
    require(client.get("clientInventoryStackWrites") is False and client.get("clientWorldWrites") is False
            and client.get("clientKnowledgeGrants") is False and client.get("fullSurvivalAcquisition") is False, "Native caster client used direct state writes")
    connection = client["connection"]
    require(connection.get("memory") is False and connection.get("integratedServer") is False and connection.get("connected") is True
            and connection.get("remoteHost") == "127.0.0.1" and connection.get("remotePort") == marker["port"]
            and connection.get("ownerUuid") == str(nbt.owner_uuid()), "Missing genuine loopback PLAY TCP caster client")
    journals, transcripts = [], {}
    for role in ("server", "client"):
        rows = journal(root, root / role / f"{role}-events.jsonl", marker, role)
        transcripts[role] = rows
        if role == "client":
            actions = client.get("actions")
            require(isinstance(actions, list) and rows[:len(actions)] == actions and all(type(row.get("clientTick")) is int and isinstance(row.get("detail"), dict) for row in rows), "Native caster client actions differ from append-only journal")
        final = journal(root, root / role / f"{role}-state.json", marker, role)[-1]
        require(final.get("status") == "completed", "Native caster terminal progress state incomplete")
        journals.append({"role": role, "rows": len(rows), **evidence(root, root / role / f"{role}-events.jsonl", 32 * 1024 * 1024)})
    screenshots = client.get("screenshots")
    require(isinstance(screenshots, list) and 1 <= len(screenshots) <= 128 and len(set(screenshots)) == len(screenshots), "Missing bounded genuine native caster screenshots")
    images = []
    for name in screenshots:
        path = unlinked(Path(name))
        require(path.parent == root / "client/screenshots" and re.fullmatch(r"[a-z0-9_.-]+\.png", path.name), "Native caster screenshot escaped cohort")
        images.append(native.png(root, path))
    require(len({row["sha256"] for row in images}) == len(images), "Native caster actual frames coincide")
    player_path = world / f"playerdata/{nbt.owner_uuid()}.dat"
    state = nbt.disk_state(nbt.read_nbt(file(root, player_path, 1024 * 1024)))
    original = nbt.disk_state(nbt.read_nbt(file(source, source_world / f"playerdata/{nbt.owner_uuid()}.dat", 1024 * 1024)))
    baseline = server["persistenceBaseline"]
    nbt.compare_snapshot(baseline, state)
    nbt.compare_snapshot(client["loadedPlayer"], state, True)
    verify_source_loaded(server, source_server, original)
    initial_owner = client_events(transcripts["client"], "native-source-owner-observed")
    require(len(initial_owner) == 1 and initial_owner[0].get("actualAcceptedS2C") is True
            and client.get("sourceOwnerObserved") is True, "Native client did not receive the exact saved source owner before any fixture")
    nbt.compare_snapshot(initial_owner[0], original, True)
    world_evidence = verify_world(root, world, baseline, source_server, marker["auditPhase"])
    require(server.get("worldBaseline", {}).get("tiles") == baseline["worldTiles"], "Native caster saved world baseline aliases differ")
    require([row.get("sequence") for row in transcripts["server"]] == list(range(1, len(transcripts["server"]) + 1))
            and all(type(row.get("serverTick")) is int and isinstance(row.get("details"), dict) for row in transcripts["server"]),
            "Actual native caster source append-only sequence/tick/details differ")
    require(server_events(transcripts["server"], "natural-source-chunk-loaded") == server["sourceWorldLoadedTiles"],
            "Actual source-loaded inherited world differs from append-only natural chunk events")
    mechanics = verify_generation(server, client, state, original, source_server, root, world, transcripts["server"], transcripts["client"]) if marker["auditPhase"] == "generate" else verify_reload(server, client, state, original, source_server, transcripts["server"], transcripts["client"])
    entities = verify_entities(root, world, server, source_server, marker["auditPhase"], transcripts["server"])
    baseline_jar = REPO / BASELINE_JAR
    require(sha(file(REPO, baseline_jar, 64 * 1024 * 1024)) == BASELINE_SHA, "Immutable accepted caster textures/storage JAR changed")
    helpers = []
    for relative, expected in PARSER_PINS.items():
        path = REPO / relative
        require(sha(file(REPO, path)) == expected, "Reviewed native caster parser changed after import")
        helpers.append(evidence(REPO, path))
    saved_player = evidence(root, player_path, 1024 * 1024)
    return {**{key: marker[key] for key in IDENTITY}, "passed": True, "ownerUuid": str(nbt.owner_uuid()),
            "scope": "Dedicated native TCP auromancy progression, paid first Touch/Fire focus, casting, pouch/equipment and exact stopped-world persistence",
            "limits": ["Explicit tangible arena/material/target fixtures", "No full resource-gathering playthrough", "No other focus mediums/equipment tiers claimed"],
            "typedPlayerNbtVerified": True, "typedNativeAnvilTilesVerified": True, "sourceWorldUnchanged": True,
            "worldCopy": copied, "mechanics": mechanics, "checks": mechanics, "nativeSavedPlayer": saved_player,
            "nativeWorldTiles": world_evidence, "nativePersistedTargets": entities, "nativeScreenshots": images, "nativeJournals": journals,
            "reports": [evidence(root, root / role / f"{role}-report.json") for role in ("server", "client")],
            "screenshots": images, "savedPlayer": saved_player, "processes": runtime["processes"],
            "sourceProcesses": source_runtime["processes"], "logs": logs, "verifier": evidence(REPO, Path(__file__)),
            "reviewedNativeParsers": helpers, "reviewedSourceCraftCatalogs": [evidence(REPO, REPO / path) for path in CRAFT_CATALOG_PINS],
            "acceptedCasterTexturesJar": evidence(REPO, baseline_jar, 64 * 1024 * 1024),
            "observedAt": datetime.now(timezone.utc).isoformat()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.root)
        if args.output:
            output = unlinked(args.output)
            require(output.parent == unlinked(args.root), "Independent caster verification output must remain in its exact private cohort")
            with output.open("x", encoding="utf-8", newline="\n") as stream:
                json.dump(result, stream, ensure_ascii=False, indent=2)
                stream.write("\n")
        print(json.dumps({"passed": True, "runId": result["runId"], "auditPhase": result["auditPhase"], "nativeWorldTiles": len(result["nativeWorldTiles"]), "mechanics": result["mechanics"]}))
        return 0
    except (OSError, EOFError, ValueError, KeyError, TypeError, IndexError, OverflowError, struct.error, zlib.error) as failure:
        print(f"Native TCP caster verification failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
