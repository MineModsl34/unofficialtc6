"""Copies TC6 JSON/OBJ block models that are not worth describing in datagen (complex elements, OBJ).

Changes while copying:
- texture paths: "<ns>:blocks/x" -> "<ns>:block/x", "blocks/x" -> "block/x"
- parent "thaumcraft:x" (1.12 looked it up in models/block) -> "thaumcraft:block/x"
- .mtl: Kd = 1 (otherwise the NeoForge OBJ loader darkens it), map_Kd -> #texture from the model

The model list is MODELS below; extended as blocks are ported.
Usage: python tools/import_models.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "reference" / "tc6" / "src" / "assets" / "thaumcraft" / "models" / "block"
DST = ROOT / "port" / "src" / "main" / "resources" / "assets" / "thaumcraft" / "models" / "block"

MODELS = [
    "arcane_stone", "ancient_stone", "eldritch_stone",
    "candle", "table_wood", "table_stone",
    "paving_stone_travel", "paving_stone_barrier",
    "pillar.obj",
    "bellows", "brain_box", "infernal_furnace", "smelter_aux", "smelter_vent", "thaumatorium.obj",
]

# Fields that did not exist in 1.12 (render_type was set by the block's getRenderLayer)
EXTRA = {
    "candle": {"render_type": "minecraft:cutout"},
    "bellows": {"render_type": "minecraft:cutout"},
    "brain_box": {"render_type": "minecraft:cutout"},
    "infernal_furnace": {"render_type": "minecraft:cutout", "textures": {
        "side": "thaumcraft:block/infernal_furnace", "particle": "thaumcraft:block/infernal_furnace",
        "core": "minecraft:block/lava_still", "fire": "minecraft:block/fire_0"}},
    "smelter_aux": {"render_type": "minecraft:cutout"},
    "smelter_vent": {"render_type": "minecraft:cutout"},
}


def fix_texture(value: str) -> str:
    if value.startswith("#"):
        return value
    return re.sub(r"(^|:)blocks/", r"\1block/", value)


def convert_json(src: Path, dst: Path) -> None:
    model = json.loads(src.read_text(encoding="utf-8"))
    parent = model.get("parent")
    if parent and parent.startswith("thaumcraft:") and "/" not in parent:
        model["parent"] = "thaumcraft:block/" + parent.split(":", 1)[1]
    if "textures" in model:
        model["textures"] = {k: fix_texture(v) for k, v in model["textures"].items()}
    model.update(EXTRA.get(src.stem, {}))
    dst.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8", newline="\n")


def convert_mtl(src: Path, dst: Path) -> None:
    out = []
    for line in src.read_text(encoding="utf-8").splitlines():
        t = line.strip().split()
        if not t:
            continue
        if t[0] == "newmtl":
            out.append(line.strip())
        elif t[0] == "Kd":
            out.append("Kd 1.0 1.0 1.0")
        elif t[0] == "map_Kd":
            out.append("map_Kd #texture")
        elif t[0] in ("d", "Ns"):
            out.append(line.strip())
    dst.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    DST.mkdir(parents=True, exist_ok=True)
    for name in MODELS:
        if name.endswith(".obj"):
            obj = SRC / name
            (DST / name).write_bytes(obj.read_bytes())
            mtl = obj.with_suffix(".mtl")
            convert_mtl(mtl, DST / mtl.name)
            print("obj ", name)
        else:
            convert_json(SRC / f"{name}.json", DST / f"{name}.json")
            print("json", name)


if __name__ == "__main__":
    main()
