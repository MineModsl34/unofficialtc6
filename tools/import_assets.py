"""Copies TC6 assets into the port with the 1.21.1 folder layout.

- textures/blocks -> textures/block, textures/items -> textures/item (other texture folders unchanged)
- sounds and sounds.json as they are (the format did not change)
- models, blockstates, loot and recipes are NOT copied: datagen generates them

Usage: python tools/import_assets.py
"""
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "reference" / "tc6" / "src" / "assets" / "thaumcraft"
DST = ROOT / "port" / "src" / "main" / "resources" / "assets" / "thaumcraft"

TEXTURE_DIRS = {"blocks": "block", "items": "item"}


def copy_tree(src: Path, dst: Path) -> int:
    count = 0
    for f in src.rglob("*"):
        if f.is_file():
            target = dst / f.relative_to(src)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, target)
            count += 1
    return count


def main() -> None:
    for sub in sorted((SRC / "textures").iterdir()):
        if sub.is_dir():
            name = TEXTURE_DIRS.get(sub.name, sub.name)
            n = copy_tree(sub, DST / "textures" / name)
            print(f"textures/{sub.name:10} -> textures/{name:10} {n:4} files")
    n = copy_tree(SRC / "sounds", DST / "sounds")
    shutil.copy2(SRC / "sounds.json", DST / "sounds.json")
    print(f"sounds{'':15} {n:4} files + sounds.json")


if __name__ == "__main__":
    main()
