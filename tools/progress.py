"""Porting progress: which TC6 classes already exist in the port (by package path and file name).

A port file marked TODO(port) counts as partially ported.
Usage: python tools/progress.py [--missing <package>]   for example: --missing api.research
"""
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TC6 = ROOT / "reference" / "tc6" / "src" / "thaumcraft"
PORT = ROOT / "port" / "src" / "main" / "java" / "thaumcraft"


def package_of(rel: Path) -> str:
    parts = rel.parent.parts
    return ".".join(parts[:2]) if parts else "(root)"


def lines(path: Path) -> int:
    return sum(1 for _ in path.open(encoding="utf-8", errors="replace"))


def main() -> None:
    stats = defaultdict(lambda: [0, 0, 0, 0, 0])  # files, lines, done, partial, done_lines
    missing = defaultdict(list)
    for src in sorted(TC6.rglob("*.java")):
        rel = src.relative_to(TC6)
        pkg = package_of(rel)
        n = lines(src)
        s = stats[pkg]
        s[0] += 1
        s[1] += n
        dst = PORT / rel
        if dst.exists():
            if "TODO(port)" in dst.read_text(encoding="utf-8"):
                s[3] += 1
            else:
                s[2] += 1
            s[4] += n
        else:
            missing[pkg].append(str(rel).replace("\\", "/"))

    if len(sys.argv) == 3 and sys.argv[1] == "--missing":
        for path in missing.get(sys.argv[2], []):
            print(path)
        return

    total = [0, 0, 0, 0, 0]
    print(f"{'package':24} {'files':>6} {'done':>5} {'part':>5} {'lines TC6':>10} {'covered':>8}")
    for pkg, s in sorted(stats.items()):
        print(f"{pkg:24} {s[0]:6} {s[2]:5} {s[3]:5} {s[1]:10} {100 * s[4] / s[1]:7.1f}%")
        total = [a + b for a, b in zip(total, s)]
    print(f"{'TOTAL':24} {total[0]:6} {total[2]:5} {total[3]:5} {total[1]:10} {100 * total[4] / total[1]:7.1f}%")


if __name__ == "__main__":
    main()
