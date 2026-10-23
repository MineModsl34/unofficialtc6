"""Converts TC6 language files (.lang, 1.12.2) to JSON for 1.21.1.

Keys are renamed by general rules (auto_key) and individually through tools/lang_remap.json
({"old.key": "new.key"}; a null value removes the key). Other keys are kept as they are.

Usage: python tools/convert_lang.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "reference" / "tc6" / "src" / "assets" / "thaumcraft" / "lang"
DST = ROOT / "port" / "src" / "main" / "resources" / "assets" / "thaumcraft" / "lang"
REMAP = ROOT / "tools" / "lang_remap.json"
ADDITIONS = ROOT / "tools" / "lang_port_additions.json"


def parse_lang(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        # In .lang a line break is written as \n; in the JSON file it is the same sequence after json.dumps
        # Vanilla 1.12 Locale normalizes numeric printf directives to string placeholders.
        # Native TranslatableContents accepts %s, not %d/%f (source boss names use %d for a string).
        value = re.sub(r"%(\d+\$)?[\d.]*[df]", lambda match: "%" + (match.group(1) or "") + "s", value)
        entries[key.strip()] = value.replace("\\n", "\n")
    return entries


COLOR_FIXES = {"_lightblue": "_light_blue", "_silver": "_light_gray"}


def auto_key(key: str) -> str:
    """General rules 1.12 -> 1.21: tile.X.name -> block.thaumcraft.X, item.X.Y.name -> item.thaumcraft.X_Y."""
    for prefix, kind in (("tile.", "block"), ("item.", "item")):
        if key.startswith(prefix) and key.endswith(".name"):
            name = key[len(prefix):-len(".name")].replace(".", "_")
            for old, new in COLOR_FIXES.items():
                if name.endswith(old) and name.split("_")[0] in ("candle", "banner", "nitor"):
                    name = name[: -len(old)] + new
            return f"{kind}.thaumcraft.{name}"
    return key


def main() -> None:
    remap: dict[str, str | None] = json.loads(REMAP.read_text(encoding="utf-8")) if REMAP.exists() else {}
    additions = json.loads(ADDITIONS.read_text(encoding="utf-8")) if ADDITIONS.exists() else {}
    DST.mkdir(parents=True, exist_ok=True)
    english = parse_lang(SRC / "en_us.lang")
    for path in sorted(SRC.glob("*.lang")):
        entries = parse_lang(path)
        out: dict[str, str] = {}
        for key, value in entries.items():
            new_key = remap[key] if key in remap else auto_key(key)
            if new_key is not None:
                out[new_key] = value
        name = path.stem.lower()  # nl_NL.lang -> nl_nl.json
        # Explicit port additions/flattened names survive repeated imports. Missing locales
        # deliberately use Minecraft's English fallback, not guessed translations.
        out.update(additions.get(name, {}))
        (DST / f"{name}.json").write_text(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        missing = len(set(english) - set(entries))
        print(f"{path.name:12} -> {name}.json  {len(out):5} keys  (missing vs en_us: {missing})")


if __name__ == "__main__":
    main()
