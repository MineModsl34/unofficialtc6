"""Creates an empty 3x3x3 structure template (air) for GameTest: data/thaumcraft/structure/empty.nbt."""
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "port" / "src" / "main" / "resources" / "data" / "thaumcraft" / "structure" / "empty.nbt"
DATA_VERSION = 3955  # 1.21.1


def name(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag_int(key: str, v: int) -> bytes:
    return b"\x03" + name(key) + struct.pack(">i", v)


def tag_string(key: str, v: str) -> bytes:
    return b"\x08" + name(key) + name(v)


def tag_list(key: str, elem_type: int, payloads: list[bytes]) -> bytes:
    return b"\x09" + name(key) + bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def int_list_payload(values: list[int]) -> bytes:
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_payload(*tags: bytes) -> bytes:
    return b"".join(tags) + b"\x00"


def main() -> None:
    root = b"\x0a" + name("") + compound_payload(
        tag_int("DataVersion", DATA_VERSION),
        b"\x09" + name("size") + int_list_payload([3, 3, 3]),
        tag_list("palette", 10, [compound_payload(tag_string("Name", "minecraft:air"))]),
        tag_list("blocks", 10, []),
        tag_list("entities", 10, []),
    )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(root))
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
