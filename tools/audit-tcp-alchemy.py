"""Independent native TCP alchemy, stopped-world chain, typed player/chunk NBT verifier."""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import struct
import sys
import uuid
import zlib

sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("tc_native_saved_nbt", Path(__file__).with_name("audit-tcp-bootstrap-reload.py"))
nbt = importlib.util.module_from_spec(_spec)
sys.modules[_spec.name] = nbt
_spec.loader.exec_module(nbt)
require, unlinked, file, sha = nbt.require, nbt.unlinked, nbt.file, nbt.sha

MARKER = "TC_PORT_TCP_ALCHEMY_AUDIT_V1"
IDENTITY = ("marker", "schemaVersion", "purpose", "networkProtocolVersion", "runId", "token", "port",
            "auditPhase", "goal", "sourceKind", "sourceRunId", "sourceToken", "sourcePort", "sourceMarker",
            "sourcePurpose", "sourceGoal", "sourceAuditPhase", "sourceServerReportSha256", "sourceClientReportSha256",
            "sourceRuntimeSha256", "sourceVerificationSha256", "worldManifestSha256")
SOURCE_FILES = (("source-server-report.json", "server/server-report.json", "sourceServerReportSha256"),
                ("source-client-report.json", "client/client-report.json", "sourceClientReportSha256"),
                ("source-runtime.json", "runtime.json", "sourceRuntimeSha256"),
                ("source-verification.json", "verification.json", "sourceVerificationSha256"))
RESEARCH = (("UNLOCKALCHEMY", -1, 1), ("UNLOCKALCHEMY", 1, 2), ("UNLOCKALCHEMY", 2, 3), ("UNLOCKALCHEMY", 3, 4),
            ("BASEALCHEMY", -1, 2), ("ALUMENTUM", -1, 1), ("ALUMENTUM", 1, 3),
            ("METALLURGY", -1, 1), ("METALLURGY", 1, 2), ("THEORYRESEARCH", -1, 1), ("THEORYRESEARCH", 1, 3),
            ("ESSENTIASMELTER", -1, 1), ("ESSENTIASMELTER", 1, 2), ("ESSENTIASMELTER", 2, 3), ("ESSENTIASMELTER", 3, 5),
            ("UNLOCKINFUSION", -1, 1), ("UNLOCKINFUSION", 1, 3), ("BASEINFUSION", -1, 2),
            ("INFUSION", -1, 1), ("INFUSION", 1, 2), ("INFUSION", 2, 4), ("CRYSTALFARMER", -1, 1), ("CRYSTALFARMER", 1, 3))
FINAL_STAGES = {"FIRSTSTEPS": 4, "UNLOCKALCHEMY": 4, "BASEALCHEMY": 2, "ALUMENTUM": 3, "METALLURGY": 2,
                "THEORYRESEARCH": 3, "ESSENTIASMELTER": 5, "WARDEDJARS": 2, "UNLOCKINFUSION": 3,
                "BASEINFUSION": 2, "INFUSION": 4, "CRYSTALFARMER": 3, "ORE": 2}
POOLS = ("BASICS", "ALCHEMY", "INFUSION", "AUROMANCY", "ARTIFICE", "GOLEMANCY", "ELDRITCH")
COSTS = {("UNLOCKALCHEMY", 1): {"O_ALCHEMY": 16, "O_BASICS": 16},
         ("ALUMENTUM", 1): {"O_BASICS": 16}, ("METALLURGY", 1): {"O_ALCHEMY": 16},
         ("ESSENTIASMELTER", 3): {"O_ALCHEMY": 16, "T_ALCHEMY": 32},
         ("UNLOCKINFUSION", 1): {"O_INFUSION": 16},
         ("INFUSION", 1): {"O_INFUSION": 16, "T_INFUSION": 32},
         ("CRYSTALFARMER", 1): {"O_AUROMANCY": 16, "O_BASICS": 16}}
INFUSION_COST = {"aer": 10, "vitreus": 10, "vinculum": 5}


def strict_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON evidence key")
        result[key] = value
    return result


def json_value(data):
    return json.loads(data.decode("utf-8-sig"), object_pairs_hook=strict_pairs,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Nonfinite JSON evidence")))


def document(root, path, limit=4 * 1024 * 1024):
    value = json_value(file(root, path, limit))
    require(isinstance(value, dict), f"Expected bounded JSON document: {path}")
    return value


def identity(value, marker, role=None):
    for key in IDENTITY:
        require(key in marker and key in value and value[key] == marker[key], f"Alchemy identity differs: {key}")
    if role is not None:
        require(value.get("role") == role, f"Alchemy role differs: {role}")


def journal(root, path, marker, role):
    data = file(root, path, 32 * 1024 * 1024)
    require(data.endswith(b"\n"), f"Truncated native append-only journal: {path}")
    lines = data.splitlines()
    require(0 < len(lines) <= 131072, f"Empty/unbounded native journal: {path}")
    result = []
    for line in lines:
        require(0 < len(line) <= 256 * 1024, f"Unbounded native journal row: {path}")
        row = json_value(line)
        require(isinstance(row, dict), f"Invalid native journal row: {path}")
        identity(row, marker, role)
        result.append(row)
    return result


def png(root, path):
    data = file(root, path, 16 * 1024 * 1024)
    require(len(data) > 1024 and data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid native PNG: {path}")
    offset, dimensions, image_data, ended, channels = 8, None, bytearray(), False, None
    while offset < len(data):
        require(offset + 12 <= len(data), f"Truncated PNG chunk: {path}")
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        require(length <= 16 * 1024 * 1024 and offset + 12 + length <= len(data), "Unbounded PNG chunk")
        payload = data[offset + 8:offset + 8 + length]
        checksum = struct.unpack_from(">I", data, offset + 8 + length)[0]
        require(zlib.crc32(kind + payload) & 0xFFFFFFFF == checksum, f"Native screenshot CRC differs: {path}")
        if kind == b"IHDR":
            require(dimensions is None and length == 13, "Duplicate/invalid PNG header")
            dimensions = struct.unpack_from(">II", payload)
            require(payload[8] == 8 and payload[9] in (2, 6) and payload[10:] == b"\0\0\0", "Unexpected native PNG format")
            channels = 3 if payload[9] == 2 else 4
        if kind == b"IDAT":
            image_data.extend(payload)
        offset += length + 12
        if kind == b"IEND":
            require(length == 0 and offset == len(data), "PNG trailing bytes")
            ended = True
            break
    require(dimensions == (1280, 720) and image_data and ended, "Missing complete native1280x720 screenshot")
    decoder = zlib.decompressobj()
    expected = 720 * (1 + 1280 * channels)
    pixels = decoder.decompress(image_data, expected + 1)
    require(decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail and len(pixels) == expected
            and all(pixels[index * (1 + 1280 * channels)] <= 4 for index in range(720)), "Native screenshot pixels are not decodable")
    return {"path": str(path), "bytes": len(data), "sha256": sha(data), "width": dimensions[0], "height": dimensions[1]}


def unpack_nbt(raw):
    require(len(raw) <= 8 * 1024 * 1024, "Unbounded chunk NBT")
    reader = nbt.NbtReader(raw)
    require(reader.number(">B") == 10, "Expected chunk root compound")
    reader.text()
    value = reader.read(10)
    require(reader.position == len(raw), "Trailing chunk NBT bytes")
    return value


def decompress_chunk(payload, compression):
    if compression == 1:
        with gzip.GzipFile(fileobj=io.BytesIO(payload)) as stream:
            raw = stream.read(8 * 1024 * 1024 + 1)
    elif compression == 2:
        decoder = zlib.decompressobj()
        raw = decoder.decompress(payload, 8 * 1024 * 1024 + 1)
        require(decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail, "Truncated/unbounded zlib chunk")
    elif compression == 3:
        raw = payload
    else:
        raise ValueError(f"Unsupported native region compression: {compression}")
    return unpack_nbt(raw)


class WorldReader:
    """Read final owned block states and BEs from native Anvil sectors, retaining all NBT types."""
    def __init__(self, root, world):
        self.root, self.world, self.regions, self.chunks = root, world, {}, {}

    def chunk(self, x, z):
        key = x, z
        if key in self.chunks:
            return self.chunks[key]
        region = x >> 5, z >> 5
        if region not in self.regions:
            path = self.world / "region" / f"r.{region[0]}.{region[1]}.mca"
            self.regions[region] = file(self.root, path, 64 * 1024 * 1024)
        data = self.regions[region]
        require(len(data) >= 8192 and len(data) % 4096 == 0, "Invalid native Anvil sectors")
        index = (x & 31) + (z & 31) * 32
        location = struct.unpack_from(">I", data, index * 4)[0]
        offset, sectors = location >> 8, location & 255
        require(offset >= 2 and sectors > 0 and (offset + sectors) * 4096 <= len(data), "Missing/out-of-range native chunk sector")
        start = offset * 4096
        length = struct.unpack_from(">I", data, start)[0]
        require(1 < length <= sectors * 4096 - 4, "Invalid native chunk length")
        compression = data[start + 4]
        require(not compression & 128, "External Anvil chunk is outside reviewed bounded cohort")
        value = nbt.compound(decompress_chunk(data[start + 5:start + 4 + length], compression))
        require(nbt.scalar(value["xPos"], 3) == x and nbt.scalar(value["zPos"], 3) == z, "Anvil chunk coordinates differ")
        self.chunks[key] = value
        return value

    def block(self, position):
        x, y, z = position
        chunk = self.chunk(x >> 4, z >> 4)
        sections = chunk["sections"]
        require(sections.kind == 9, "Expected native chunk sections")
        section = next((nbt.compound(tag) for tag in sections.value if nbt.scalar(nbt.compound(tag)["Y"], 1) == y >> 4), None)
        require(section is not None, "Owned native section absent")
        states = nbt.compound(section["block_states"])
        palette = states["palette"]
        require(palette.kind == 9 and 0 < len(palette.value) <= 4096, "Invalid native block palette")
        index = 0
        if len(palette.value) > 1:
            packed = states["data"]
            require(packed.kind == 12, "Expected native packed blockstate longs")
            bits = max(4, (len(palette.value) - 1).bit_length())
            per_long = 64 // bits
            require(len(packed.value) == (4096 + per_long - 1) // per_long, "Native packed section length differs")
            block_index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
            index = (packed.value[block_index // per_long] & 0xFFFFFFFFFFFFFFFF) >> ((block_index % per_long) * bits) & ((1 << bits) - 1)
        require(index < len(palette.value), "Native blockstate palette index out of bounds")
        return nbt.scalar(nbt.compound(palette.value[index])["Name"], 8)

    def tile(self, position):
        x, y, z = position
        chunk = self.chunk(x >> 4, z >> 4)
        entities = chunk["block_entities"]
        require(entities.kind == 9, "Expected native saved block entities")
        matches = [tag for tag in entities.value if tuple(nbt.scalar(nbt.compound(tag)[key], 3) for key in ("x", "y", "z")) == tuple(position)]
        require(len(matches) == 1, "Missing/duplicate owned saved tile coordinates")
        return matches[0]

    def no_tile(self, position):
        chunk = self.chunk(position[0] >> 4, position[2] >> 4)
        require(not any(tuple(nbt.scalar(nbt.compound(tag)[key], 3) for key in ("x", "y", "z")) == tuple(position)
                        for tag in chunk["block_entities"].value), "Unexpected saved BE at an owned ordinary block")


def processes(runtime, root, marker=None):
    require(runtime.get("status") == "completed" and runtime.get("buildExitCode") == 0 and runtime.get("serverReady") is True,
            "Native JVM runner did not complete")
    values = runtime.get("processes")
    require(isinstance(values, list) and len(values) == 2 and {value.get("role") for value in values} == {"server", "client"},
            "Expected exactly one owned server/client JVM")
    identities, evidence = set(), []
    for process in values:
        role = process["role"]
        require(type(process.get("pid")) is int and process["pid"] > 0 and process.get("exitCode") == 0
                and process.get("forcedStop") is False and isinstance(process.get("startedAtUtc"), str),
                "Native JVM failed or needed forced stop")
        identities.add((process["pid"], process["startedAtUtc"]))
        if marker is None:
            continue
        launch = root / "launch" / role
        name = "tcpAlchemyAudit" + role.capitalize()
        for suffix, key in (("RunVmArgs.txt", "vmArgsSha256"), ("RunProgramArgs.txt", "programArgsSha256"),
                            ("RunClasspath.txt", "classpathArgsSha256")):
            require(sha(file(root, launch / (name + suffix), 1024 * 1024)) == process[key], "Native immutable launch arguments changed")
        vm = file(root, launch / (name + "RunVmArgs.txt"), 1024 * 1024).decode("utf-8-sig")
        require("-Dthaumcraft.tcpAlchemyAudit=true" in vm and "-XX:ActiveProcessorCount=2" in vm
                and ("-Xmx1024m" if role == "server" else "-Xmx768m") in vm,
                "Unbounded/wrong-purpose actual native JVM")
        require("net.neoforged.devlaunch.Main" in process.get("arguments", "") and str(launch) in process["arguments"],
                "Missing actual private generated NeoForge launch")
        for stream in ("stdout", "stderr"):
            path = root / f"{role}.{stream}.log"
            require(Path(process[stream + "File"]) == path, "Native process log escaped cohort")
            data = file(root, path, 32 * 1024 * 1024)
            evidence.append({"path": str(path), "bytes": len(data), "sha256": sha(data)})
    require(len(identities) == 2, "Native process identities coincide")
    return identities, evidence


def provenance(root, marker):
    phase = marker["auditPhase"]
    repo = Path(__file__).resolve().parent.parent
    source = unlinked(repo / "port/build" / ("tcp-bootstrap" if phase == "generate" else "tcp-alchemy") / marker["sourceRunId"])
    preparation = document(root, root / "preparation.json")
    identity(preparation, marker)
    require(Path(preparation["sourceRoot"]) == source, "Prepared source root differs")
    inputs = root / "preparation-inputs"
    source_documents = {}
    for copied, original, key in SOURCE_FILES:
        data = file(root, inputs / copied)
        require(sha(data) == marker[key] and data == file(source, source / original), "Copied/original source evidence hash differs")
        source_documents[copied] = document(root, inputs / copied)
    for name, value in source_documents.items():
        for key, expected in (("marker", marker["sourceMarker"]), ("purpose", marker["sourcePurpose"]),
                              ("goal", marker["sourceGoal"]), ("auditPhase", marker["sourceAuditPhase"]),
                              ("schemaVersion", 1), ("networkProtocolVersion", "6"), ("runId", marker["sourceRunId"]),
                              ("token", marker["sourceToken"]), ("port", marker["sourcePort"])):
            require(value.get(key) == expected, f"Source evidence identity differs: {name}/{key}")
    server, client = source_documents["source-server-report.json"], source_documents["source-client-report.json"]
    require(server.get("role") == "server" and client.get("role") == "client" and server.get("passed") is True
            and client.get("passed") is True and server.get("saveFlushed") is True and server.get("diskNbtVerified") is True
            and source_documents["source-verification.json"].get("passed") is True, "Source lacks accepted native/disk evidence")
    expected_source_phase = {"generate": "generate", "resume": "generate", "reload": "resume"}[phase]
    require(marker["sourceAuditPhase"] == expected_source_phase and marker["sourceToken"] != marker["token"], "Invalid fresh phase chain")
    if phase == "generate":
        require(marker["sourceKind"] == "bootstrap" and marker["sourceMarker"] == "TC_PORT_TCP_BOOTSTRAP_AUDIT_V1"
                and marker["sourceGoal"] == "firststeps" and marker["sourcePurpose"] == "native-bootstrap"
                and server.get("finalFirststepsStage") == 4 and server.get("finalFirststepsComplete") is True
                and server.get("reconnectProven") is True, "Generation source is not accepted complete FIRSTSTEPS")
    else:
        require(marker["sourceKind"] == "alchemy" and marker["sourceMarker"] == MARKER
                and marker["sourceGoal"] == "alchemy" and marker["sourcePurpose"] == "native-alchemy", "Invalid native alchemy source")
        if phase == "resume":
            require(server.get("checkpointedInfusion") is True, "Resume source lacks partial paid native job")
        else:
            require(server.get("alchemyComplete") is True and server.get("reconnectProven") is True, "Reload source lacks completed/reconnected alchemy")
    manifest_bytes = file(root, inputs / "world-copy-manifest.json")
    require(sha(manifest_bytes) == marker["worldManifestSha256"], "Copied world manifest hash differs")
    manifest = document(root, inputs / "world-copy-manifest.json")
    source_world = source / "server" / ("tcp-bootstrap-audit" if phase == "generate" else "tcp-alchemy-audit")
    world = root / "server/tcp-alchemy-audit"
    require(manifest.get("sourceRunId") == marker["sourceRunId"] and manifest.get("sourceToken") == marker["sourceToken"]
            and Path(manifest["sourceRoot"]) == source and Path(manifest["sourceWorld"]) == source_world
            and Path(manifest["copiedWorld"]) == world and manifest.get("sourceStopped") is True
            and manifest.get("sourceUnchangedDuringCopy") is True and manifest.get("copiedBytesExact") is True, "Invalid stopped native world copy")
    entries = manifest.get("files")
    require(isinstance(entries, list) and 0 < len(entries) <= 10000 and len(entries) == manifest.get("fileCount"), "Unbounded world file manifest")
    names, total = set(), 0
    for entry in entries:
        relative = Path(entry["path"])
        require(not relative.is_absolute() and ".." not in relative.parts and entry["path"] not in names, "Unsafe/duplicate source world file")
        names.add(entry["path"])
        data = file(source, source_world / relative, 64 * 1024 * 1024)
        require(len(data) == entry["bytes"] and sha(data) == entry["sha256"], "Stopped source world changed after copy")
        total += len(data)
    actual_names = set()
    for path in source_world.rglob("*"):
        unlinked(path)
        if path.is_file():
            actual_names.add(path.relative_to(source_world).as_posix())
    require(names == actual_names and total == manifest.get("totalBytes") and total <= 512 * 1024 * 1024
            and "level.dat" in names and f"playerdata/{nbt.owner_uuid()}.dat" in names, "Incomplete source world manifest")
    for relative, key in (("eula.txt", "eulaSha256"), ("options.txt", "optionsSha256")):
        require(sha(file(root, inputs / relative)) == preparation[key], "Copied existing preference/EULA hash differs")
    require(file(root, inputs / "eula.txt", 4096) == file(root, root / "server/eula.txt", 4096), "Runtime accepted EULA changed")
    require(re.search(r"(?m)^\s*eula\s*=\s*true\s*$", file(root, root / "server/eula.txt", 4096).decode("utf-8-sig")), "Missing already accepted EULA")
    require("pauseOnLostFocus:false" in file(root, root / "client/options.txt", 1024 * 1024).decode("utf-8-sig").splitlines(), "Native private focus preference differs")
    properties = file(root, root / "server/server.properties", 8192).decode("utf-8-sig").splitlines()
    for expected in ("level-name=tcp-alchemy-audit", "server-ip=127.0.0.1", f"server-port={marker['port']}", "online-mode=false", "gamemode=survival"):
        require(expected in properties, f"Lost private native server property: {expected}")
    return source, source_world, server, client, source_documents["source-runtime.json"], world, {"fileCount": len(entries), "bytes": total}


def knowledge_map(state):
    canonical = nbt.canonical_knowledge(state["knowledge"])
    return {row["key"]: row for row in canonical["research"]}, {row["key"]: row["amount"] for row in canonical["knowledge"]}


def typed_aspects(value):
    data = nbt.compound(nbt.snbt(value) if isinstance(value, str) else value)
    require("Aspects" in data and data["Aspects"].kind == 9, "Missing typed source aspect list")
    result = {}
    for child in data["Aspects"].value:
        row = nbt.compound(child)
        key, amount = nbt.scalar(row["key"], 8), nbt.scalar(row["amount"], 3)
        require(key not in result and 0 <= amount <= 4096, "Duplicate/invalid typed source aspect")
        result[key] = amount
    return result


def normalized_tile(value):
    tag = nbt.snbt(value) if isinstance(value, str) else value
    data = dict(nbt.compound(tag))
    if "recipein" in data:
        require(data["recipein"].kind == 9, "Recipe input list type differs")
        children = []
        for child in data["recipein"].value:
            row = dict(nbt.compound(child))
            if "item" in row:
                nbt.scalar(row.pop("item"), 1)  # Source saveAdditional count++; no other job field is ignored.
            children.append(nbt.Tag(10, row))
        data["recipein"] = nbt.Tag(9, children)
    return nbt.Tag(10, data)


def freeze(tag):
    if tag.kind == 10:
        value = tuple((key, freeze(value)) for key, value in sorted(tag.value.items()))
    elif tag.kind == 9:
        value = tuple(freeze(value) for value in tag.value)
    elif isinstance(tag.value, (list, tuple)):
        value = tuple(tag.value)
    else:
        value = tag.value
    return tag.kind, value


def inventory_bag(value):
    tag = nbt.snbt(value) if isinstance(value, str) else value
    require(tag.kind == 9, "Native inventory must be a typed list")
    result = Counter()
    for child in tag.value:
        row = dict(nbt.compound(child))
        nbt.scalar(row.pop("Slot"), 1)
        amount = nbt.scalar(row.pop("count"), 3)
        require(0 < amount <= 99 and nbt.scalar(row["id"], 8) != "minecraft:air", "Invalid native inventory stack")
        result[freeze(nbt.Tag(10, row))] += amount
    return result


def stack_identity(item, components=None):
    row = {"id": nbt.Tag(8, item)}
    if components:
        row["components"] = nbt.Tag(10, components)
    return freeze(nbt.Tag(10, row))


def chest_items(value, size):
    tag = nbt.snbt(value) if isinstance(value, str) else value
    require(tag.kind == 9 and len(tag.value) <= size, "Native chest items are not a bounded typed list")
    occupied = set()
    for child in tag.value:
        row = nbt.compound(child)
        slot = nbt.scalar(row["Slot"], 1)
        require(0 <= slot < size and slot not in occupied, "Native chest has a duplicate/out-of-range slot")
        occupied.add(slot)
        require(0 < nbt.scalar(row["count"], 3) <= 64, "Native chest stack violates its actual slot limit")
    return inventory_bag(tag)


def chest_snapshot(value):
    tag = nbt.compound(nbt.snbt(value))
    require(set(tag) == {"Size", "Items"} and nbt.scalar(tag["Size"], 3) == 54,
            "Native material storage is not the actual fifty-four-slot chest")
    return chest_items(tag["Items"], 54)


def ordinary_empty(value):
    tag = nbt.snbt(value)
    require(tag.kind == 9, "Native owner inventory is not a typed list")
    slots = [nbt.scalar(nbt.compound(child)["Slot"], 1) for child in tag.value]
    require(len(set(slots)) == len(slots), "Native owner snapshot has duplicate slots")
    return 36 - sum(0 <= slot < 36 for slot in slots)


def verify_native_storage(server, client, phase, source_server):
    rows, received = server.get("nativeStorageReceipts"), client.get("nativeStorageReceipts")
    require(isinstance(rows, list) and isinstance(received, list) and len(rows) == len(received) <= 128,
            "Native material storage receipts are missing/unbounded")
    if phase != "generate":
        require(rows == [] and received == [], "New JVM resume/reload executed material storage gameplay")
    fields = ("transactionId", "actionIndex", "theoryCategory", "position", "inventoryBeforeNbt", "chestBeforeNbt",
              "inventoryAfterNbt", "chestAfterNbt", "ordinaryEmptyBefore", "ordinaryEmptyAfter", "nativeQuickMoveCount")
    prior, prior_snapshot = Counter(), None
    for index, (row, accepted) in enumerate(zip(rows, received), 1):
        require(all(field in row and field in accepted and row[field] == accepted[field] for field in fields)
                and row["transactionId"] == f"theory-storage-{index}" and row["position"] == [260, 65, 12]
                and row["theoryCategory"] in ("ALCHEMY", "INFUSION") and type(row["actionIndex"]) is int,
                "Actual client/server native storage transaction identity/typed fields differ")
        require(all(row.get(key) is True for key in ("actualServerAccepted", "actualTCP", "mainThread",
                    "noteBoundaryNoActiveData", "cursorEmpty", "nativeChestMenu54"))
                and accepted.get("actualServerAccepted") is True and accepted.get("actualNativeChestMenu") is True
                and accepted.get("phase") == "await-server"
                and type(row.get("menuId")) is int and 0 < row["menuId"] == accepted.get("actualNativeMenuId"),
                "Native material storage lacks actual owner note boundary/C2S menu acceptance")
        before, after = (inventory_bag(row[name]) for name in ("inventoryBeforeNbt", "inventoryAfterNbt"))
        chest_before, chest_after = (chest_snapshot(row[name]) for name in ("chestBeforeNbt", "chestAfterNbt"))
        require(chest_before == prior, "Native storage first chest was not empty or later contents changed outside native transfers")
        require(prior_snapshot is None or nbt.snbt(row["chestBeforeNbt"]) == prior_snapshot,
                "Native storage changed exact chest slots/components between reviewed transactions")
        moved = before - after
        require(moved and before == after + moved and chest_after == chest_before + moved
                and before + chest_before == after + chest_after,
                "Native QUICK_MOVE deleted, created or mutated actual typed items/components")
        require(all(dict(key[1]).get("id") == (8, "thaumcraft:crystal_essence") for key in moved)
                and row.get("crystalsMoved") == sum(moved.values()), "Native storage transferred unreviewed items or misstated crystal counts")
        require(ordinary_empty(row["inventoryBeforeNbt"]) == row["ordinaryEmptyBefore"] < 8
                and ordinary_empty(row["inventoryAfterNbt"]) == row["ordinaryEmptyAfter"] >= 8,
                "Native storage did not restore actual ordinary owner inventory capacity")
        clicks = accepted.get("nativeClicks")
        require(type(row["nativeQuickMoveCount"]) is int and 0 < row["nativeQuickMoveCount"] <= 36
                and isinstance(clicks, list) and len(clicks) == row["nativeQuickMoveCount"]
                and row.get("nativeClicks") == clicks,
                "Native storage lacks bounded actual vanilla QUICK_MOVE clicks")
        expected_clicks = [event["detail"] for event in client["actions"]
                           if event.get("action") == "native-theory-storage-quick-move"
                           and event["detail"].get("transactionId") == row["transactionId"]]
        require(clicks == expected_clicks, "Sealed storage clicks differ from actual append-only native client journal")
        clicked = Counter()
        for click in clicks:
            stack = dict(nbt.compound(nbt.snbt(click["stackBeforeNbt"])))
            require(click.get("transactionId") == row["transactionId"] and click.get("menuId") == row["menuId"]
                    and type(click.get("slot")) is int and 54 <= click["slot"] < 90
                    and click.get("button") == 0 and click.get("clickType") == "QUICK_MOVE" and click.get("nativeC2S") is True
                    and nbt.scalar(stack["id"], 8) == "thaumcraft:crystal_essence" and nbt.scalar(stack["count"], 3) > 0,
                    "Native storage used an unreviewed menu/input/stack transaction")
            amount = nbt.scalar(stack.pop("count"), 3)
            require("Slot" not in stack, "Native click stack includes a fabricated inventory slot")
            clicked[freeze(nbt.Tag(10, stack))] += amount
        require(clicked == moved, "Actual native clicks do not account for every exact moved stack/component/unit")
        completed = [event["detail"] for event in client["actions"] if event.get("action") == "native-theory-storage-completed"]
        require(accepted in completed, "Native storage lacks final actual server-accepted client receipt")
        prior, prior_snapshot = chest_after, nbt.snbt(row["chestAfterNbt"])
    owned = {tuple(row["position"]): row for row in server["persistenceBaseline"]["worldTiles"]}
    saved, combined = Counter(), []
    for half, position in enumerate(((260, 65, 12), (261, 65, 12))):
        require(position in owned and owned[position].get("block") == "minecraft:chest" and "nbt" in owned[position],
                "Owned stopped-world ledger omits actual double-chest halves")
        data = nbt.compound(nbt.snbt(owned[position]["nbt"]))
        require(nbt.scalar(data["id"], 8) == "minecraft:chest" and "LootTable" not in data,
                "Native material storage is not a normal persisted physical chest")
        saved += chest_items(data["Items"], 27)
        for child in data["Items"].value:
            item = dict(nbt.compound(child))
            item["Slot"] = nbt.Tag(1, nbt.scalar(item["Slot"], 1) + half * 27)
            combined.append(nbt.Tag(10, item))
    if phase == "generate":
        require(saved == prior, "Independently saved native double-chest items differ from the last conserved transaction")
        actual_combined = nbt.Tag(10, {"Size": nbt.Tag(3, 54), "Items": nbt.Tag(9, tuple(combined))})
        require(prior_snapshot is None and not combined or actual_combined == prior_snapshot,
                "Independently saved native double-chest slots/components differ from exact last GUI receipt")
    else:
        source_owned = {tuple(row["position"]): row for row in source_server["persistenceBaseline"]["worldTiles"]}
        for position in ((260, 65, 12), (261, 65, 12)):
            require(position in source_owned and owned[position]["block"] == source_owned[position]["block"]
                    and nbt.snbt(owned[position]["nbt"]) == nbt.snbt(source_owned[position]["nbt"]),
                    "New native JVM changed full stopped-world material chest slots/items/components")
    return {"nativeMaterialStorageTransactions": len(rows), "savedChestItemUnits": sum(saved.values())}


def verify_checkpoint(checkpoint, baseline):
    require(checkpoint.get("actualNativeCasterStarted") is True and checkpoint.get("savedDiskJob") is True,
            "Checkpoint lacks native caster start and actual saved job")
    initial, remaining, paid = (typed_aspects(checkpoint[name]) for name in ("initialCosts", "remainingCosts", "paidCosts"))
    require(initial == INFUSION_COST and set(remaining) <= set(initial) and set(paid) == set(initial), "Native recipe initial/cost types differ")
    require(all(paid[key] == initial[key] - remaining.get(key, 0) for key in initial), "Partial native job aspect payment does not conserve costs")
    require(sum(paid.values()) == checkpoint.get("paidUnits") and sum(remaining.values()) == checkpoint.get("totalRemaining")
            and 0 < sum(paid.values()) < 25 and 0 < sum(remaining.values()) < 25, "Native job is not partly paid")
    saved = next(row for row in baseline["worldTiles"] if row["position"] == [274, 67, 0])
    job = nbt.snbt(checkpoint["jobNbt"])
    require(job == nbt.snbt(saved["nbt"]), "Checkpoint differs from independently verified saved matrix")
    data = nbt.compound(job)
    require(nbt.scalar(data["active"], 1) == 1 and nbt.scalar(data["crafting"], 1) == 1
            and nbt.scalar(data["recipeplayer"], 8) == "TC_Bootstrap" and nbt.scalar(data["recipetype"], 3) == 0
            and nbt.scalar(data["rotype"], 8) == "@" and typed_aspects(job) == remaining, "Saved paid job owner/state/costs differ")
    output = nbt.compound(data["recipeout"])
    require(nbt.scalar(output["id"], 8) == "thaumcraft:crystal_aer" and nbt.scalar(output["count"], 3) == 1,
            "Saved native job output differs from reviewed registered infusion recipe")
    central = nbt.compound(data["recipeinput"])
    require(nbt.scalar(central["id"], 8) == "thaumcraft:crystal_essence" and nbt.scalar(central["count"], 3) == 1
            and nbt.compound(nbt.compound(central["components"])["thaumcraft:aspects"]) == {"aer": nbt.Tag(3, 1)},
            "Saved native job central aspect component differs")
    ingredients = nbt.compound(normalized_tile(job))["recipein"]
    require(ingredients.kind == 9 and len(ingredients.value) == 2
            and {nbt.scalar(nbt.compound(row)["id"], 8) for row in ingredients.value} == {"minecraft:wheat_seeds", "thaumcraft:salis_mundus"}
            and all(nbt.scalar(nbt.compound(row)["count"], 3) == 1 for row in ingredients.value), "Saved native components differ")
    observation = checkpoint.get("costObservation")
    require(isinstance(observation, dict) and observation.get("recipeResearch") == "CRYSTALFARMER"
            and observation.get("nativeSourceRecipeClass") == "thaumcraft.api.crafting.InfusionRecipe"
            and observation.get("actionId") == "start-native-infusion" and observation.get("costMultiplier") == 1.0
            and type(observation.get("nativeMatrixCountBefore")) is int and observation["nativeMatrixCountBefore"] >= 0
            and all(observation.get(key) is True for key in ("actualTCP", "mainThread", "nativeServerInteractionObservedBeforeFirstPayment"))
            and typed_aspects(observation["initialCosts"]) == initial,
            "Actual before-payment source recipe context/costs differ from the saved native checkpoint")
    observed_center = nbt.snbt(observation["centralInputNbt"])
    observed_components = nbt.snbt(observation["componentsNbt"])
    require(observed_center == data["recipeinput"] and observed_components.kind == 9
            and [nbt.scalar(nbt.compound(row)["id"], 8) for row in observed_components.value]
                == ["minecraft:wheat_seeds", "thaumcraft:salis_mundus"]
            and Counter(freeze(row) for row in observed_components.value) == Counter(freeze(row) for row in ingredients.value),
            "Source costs were not observed on the exact actual typed native central/peripheral components")
    jars_before, jars_after = observation.get("jarAmountsBefore"), checkpoint.get("jarAmountsAfter")
    require(checkpoint.get("actualSourceCostAndJarPaymentBalanced") is True
            and isinstance(jars_before, dict) and isinstance(jars_after, dict)
            and set(jars_before) == set(jars_after) == set(initial), "Missing exact before/after native jar payment snapshots")
    owned = {tuple(row["position"]): row for row in baseline["worldTiles"]}
    for aspect, z in (("aer", 0), ("vitreus", 4), ("vinculum", 8)):
        saved_jar = nbt.compound(nbt.snbt(owned[(266, 65, z)]["nbt"]))
        require(type(jars_before[aspect]) is int and initial[aspect] <= jars_before[aspect] <= 250
                and type(jars_after[aspect]) is int and 0 <= jars_after[aspect] <= jars_before[aspect]
                and nbt.scalar(saved_jar["Aspect"], 8) == aspect
                and nbt.scalar(saved_jar["Amount"], 2) == jars_after[aspect]
                and jars_before[aspect] - jars_after[aspect] == paid[aspect],
                "Independently saved jar delta does not equal the exact actual source cost minus outstanding job cost")
    return paid, remaining


def verify_world(root, world, baseline):
    tiles = baseline.get("worldTiles")
    require(isinstance(tiles, list) and 10 <= len(tiles) <= 128, "Missing bounded complete owned-machine snapshots")
    reader, positions, evidence = WorldReader(root, world), set(), []
    for entry in tiles:
        position = entry.get("position")
        require(isinstance(position, list) and len(position) == 3 and all(type(value) is int for value in position)
                and 256 <= position[0] <= 288 and 60 <= position[1] <= 80 and -8 <= position[2] <= 32, "Owned-machine position outside private arena")
        key = tuple(position)
        require(key not in positions, "Duplicate owned native tile snapshot")
        positions.add(key)
        block = reader.block(position)
        require(block == entry["block"], "Independently read owned native block state differs")
        record = {"position": position, "block": block}
        if "nbt" in entry:
            actual = reader.tile(position)
            require(normalized_tile(actual) == normalized_tile(entry["nbt"]) if key == (274, 67, 0)
                    else actual == nbt.snbt(entry["nbt"]),
                    "Typed independently read native saved tile differs from final owned snapshot")
            record["nbtSha256"] = sha(entry["nbt"].encode("utf-8"))
        else:
            reader.no_tile(position)
        evidence.append(record)
    require({(258, 65, 4), (258, 65, 8), (258, 65, 12), (274, 67, 0), (274, 65, 0)} <= positions,
            "Owned-machine snapshot omits workbench/crucible/research/matrix/center")
    return evidence


def row_field(row, *names):
    found = [row[name] for name in names if name in row]
    require(found and all(value == found[0] for value in found), f"Missing/ambiguous native ledger field: {names}")
    return found[0]


def verify_production_payments(server):
    for row in server["nativeCraftReceipts"]:
        require(row.get("actualTCP") is True and row.get("mainThread") is True
                and type(row.get("count")) is int and 0 < row["count"] <= 8
                and isinstance(row.get("actionId"), str), "Native craft receipt lacks actual source main TCP context")
    expected = {"thaumcraft:nitor_yellow": {"potentia": 10, "ignis": 10, "lux": 10},
                "thaumcraft:alumentum": {"potentia": 10, "ignis": 10, "perditio": 5},
                "thaumcraft:ingot_brass": {"instrumentum": 5}}
    rows = server["cruciblePaymentReceipts"]
    require(len(rows) == 3 and {row["item"] for row in rows} == set(expected), "Native crucible output/payment set differs")
    for row in rows:
        before, after, cost = (typed_aspects(row[name]) for name in ("aspectsBeforeNbt", "aspectsAfterNbt", "costAspectsNbt"))
        require(cost == expected[row["item"]] and row.get("count") == 1 and row.get("actualTCP") is True
                and row.get("sourceEventBeforePayment") is True and row.get("postPaymentObserved") is True,
                "Native crucible event did not observe both sides of actual paid recipe")
        require(all(before.get(key, 0) - after.get(key, 0) == cost.get(key, 0) for key in set(before) | set(after) | set(cost))
                and type(row.get("waterBefore")) is int and 50 <= row["waterBefore"] <= 1000
                and row.get("waterAfter") == row["waterBefore"] - 50, "Native crucible aspect/water conservation differs")
    rows = server["arcanePaymentReceipts"]
    expected_arcane = {"thaumcraft:smelter_basic": (50, [0, 1, 0, 0, 0, 0]),
                       "thaumcraft:infusion_matrix": (150, [1, 1, 1, 1, 1, 1])}
    require(len(rows) == 2 and {row["item"] for row in rows} == set(expected_arcane), "Native arcane output/payment set differs")
    for row in rows:
        vis, crystals = expected_arcane[row["item"]]
        require(row.get("actualTCP") is True and row.get("visPaid") == vis and row.get("visBefore", 0) - row.get("visAfter", 0) == vis
                and row.get("crystalsPaid") == crystals and row.get("count") == 1,
                "Actual native arcane vis/crystal payment differs from source registered recipe")
        observed = row.get("beforePayment")
        require(row.get("chargedWorkbench") is True and row.get("nativeBeforePaymentObserved") is True
                and isinstance(observed, dict) and observed.get("item") == row["item"]
                and observed.get("actionId") == row["actionId"] and observed.get("visBefore") == row["visBefore"]
                and type(observed.get("menuId")) is int and observed["menuId"] > 0
                and observed.get("actualTCP") is True and observed.get("mainThread") is True
                and observed.get("auraChunksBefore") == row.get("auraChunksBefore"),
                "Native charged payment lacks its actual immediately preceding source menu/output observation")
        output = nbt.compound(nbt.snbt(observed["outputNbt"]))
        require(output == {"id": nbt.Tag(8, row["item"]), "count": nbt.Tag(3, 1)},
                "Native before-payment output is not the exact actual registered source block item")
        before_chunks, after_chunks = row.get("auraChunksBefore"), row.get("auraChunksAfter")
        require(isinstance(before_chunks, list) and isinstance(after_chunks, list)
                and len(before_chunks) == len(after_chunks) == 9, "Native charger lacks all nine actual aura chunk snapshots")
        before_total, after_total, actual_paid = 0, 0, 0.0
        for index, (before_chunk, after_chunk) in enumerate(zip(before_chunks, after_chunks)):
            expected_position = (15 + index // 3, -1 + index % 3)
            values = []
            for chunk in (before_chunk, after_chunk):
                require((chunk.get("chunkX"), chunk.get("chunkZ")) == expected_position and chunk.get("actuallyLoaded") is True
                        and type(chunk.get("base")) is int and 0 <= chunk["base"] <= 500,
                        "Native charged payment used an unreviewed/unloaded source aura chunk")
                native_values = []
                for field in ("vis", "flux"):
                    value, bits = chunk.get(field), chunk.get(field + "Bits")
                    require(type(value) in (int, float) and 0 <= value <= 32767 and type(bits) is int
                            and -2147483648 <= bits <= 2147483647
                            and struct.unpack(">i", struct.pack(">f", value))[0] == bits,
                            "Native source aura float summary differs from its actual IEEE-754 bits")
                    native_values.append(struct.unpack(">f", struct.pack(">i", bits))[0])
                values.append(native_values)
            require(before_chunk["base"] == after_chunk["base"] and before_chunk["fluxBits"] == after_chunk["fluxBits"]
                    and values[0][0] >= values[1][0], "Native charged recipe changed source base/flux or created vis")
            before_total = int(struct.unpack(">f", struct.pack(">f", before_total + values[0][0]))[0])
            after_total = int(struct.unpack(">f", struct.pack(">f", after_total + values[1][0]))[0])
            actual_paid += values[0][0] - values[1][0]
        require(before_total == row["visBefore"] and after_total == row["visAfter"]
                and type(row.get("visFloatPaid")) in (int, float) and abs(actual_paid - row["visFloatPaid"]) <= 1e-4
                and abs(actual_paid - vis) <= 1e-4,
                "Independently reconstructed native nine-chunk float/aggregate debit differs from the paid recipe")
    rows = server["nativePhialReceipts"]
    require(len(rows) == 3 and [row.get("direction") for row in rows] == ["extract", "insert", "extract"],
            "Actual native ten-unit phial conversion sequence differs")
    for row in rows:
        change = -10 if row["direction"] == "extract" else 10
        require(row.get("position") == [266, 65, 0] and row.get("aspect") == "aer"
                and row.get("nativeItemUseFirst") is True and row.get("actualInventoryAndBlockPaymentObserved") is True
                and row.get("transferredAmount") == 10 and row.get("expectedAfterAmount") == row.get("afterAmount")
                and row["afterAmount"] - row["beforeAmount"] == change, "Native phial did not pay exactly ten jar units")
        before, after = inventory_bag(row["inventoryBeforeNbt"]), inventory_bag(row["inventoryAfterNbt"])
        empty = stack_identity("thaumcraft:phial_empty")
        filled = stack_identity("thaumcraft:phial_filled", {"thaumcraft:aspects": nbt.Tag(10, {"aer": nbt.Tag(3, 10)})})
        input_item, output_item = (empty, filled) if change < 0 else (filled, empty)
        require(before + Counter({output_item: 1}) == after + Counter({input_item: 1}),
                "Actual native phial inventory components/count conservation differs")


def verify_pipeline(server, paid):
    rows = server["essentiaConservationRecords"]
    require(0 < len(rows) <= 104, "Missing bounded natural smelter conservation records")
    totals = {key: (0, 0) for key in INFUSION_COST}
    batches = {"aer": (64, 1), "vitreus": (8, 5), "vinculum": (32, 1)}
    for row in rows:
        aspect = row.get("aspect")
        require(aspect in totals and row.get("nativeFuelBurned") is True, "Natural smelting record lacks reviewed aspect/fuel")
        consumed, produced = totals[aspect]
        batch, units = batches[aspect]
        after_consumed, after_produced = row["inputConsumedAfter"], row["producedAfter"]
        require(row["inputConsumedBefore"] == consumed and row["producedBefore"] == produced
                and consumed < after_consumed <= batch and produced <= after_produced <= batch * units
                and row["naturalEfficiencyLoss"] == (after_consumed - consumed) * units - (after_produced - produced)
                and row["naturalEfficiencyLoss"] >= 0, "Actual smelter input/output/loss arithmetic differs")
        require(all(type(row.get(key)) is int and row[key] >= 0 for key in ("smelterAmount", "alembicAmount", "jarAmount", "tubeAmount"))
                and sum(row[key] for key in ("smelterAmount", "alembicAmount", "jarAmount", "tubeAmount")) == row["totalStored"] == after_produced,
                "Actual smelter/alembic/tube/jar units do not conserve production")
        totals[aspect] = after_consumed, after_produced
    tiles = {tuple(row["position"]): row for row in server["persistenceBaseline"]["worldTiles"]}
    for aspect, z in (("aer", 0), ("vitreus", 4), ("vinculum", 8)):
        consumed, produced = totals[aspect]
        require(consumed == batches[aspect][0], "Natural smelter did not consume its reviewed input batch")
        jar = nbt.compound(nbt.snbt(tiles[(266, 65, z)]["nbt"]))
        require(nbt.scalar(jar["Aspect"], 8) == aspect
                and nbt.scalar(jar["Amount"], 2) == produced - paid[aspect] - (10 if aspect == "aer" else 0),
                "Independently saved jar does not conserve smelting, paid phial research and partial infusion")
    return totals


def verify_server_journal(rows, server):
    require([row.get("sequence") for row in rows] == list(range(1, len(rows) + 1))
            and all(row.get("mainThread") is True and type(row.get("serverTick")) is int and isinstance(row.get("details"), dict) for row in rows),
            "Actual native server append-only journal sequence/context differs")
    require(all(rows[index]["serverTick"] <= rows[index + 1]["serverTick"] for index in range(len(rows) - 1)),
            "Native server ticks moved backwards")
    for field, event in (("nativeResearchRequests", "actual-applied-contextual-research"),
                         ("nativeCraftReceipts", "native-production-item-crafted"), ("nativeKnowledgeReceipts", "native-knowledge-event"),
                         ("arcanePaymentReceipts", "native-arcane-payment"), ("cruciblePaymentReceipts", "native-crucible-payment-observed"),
                         ("nativePhialReceipts", "native-ten-unit-phial-conversion-observed"),
                         ("nativeStorageReceipts", "native-theory-material-storage-conserved"),
                         ("essentiaConservationRecords", "native-essentia-conservation"),
                         ("sourceWorldLoadedTiles", "natural-source-chunk-loaded")):
        require(server.get(field) == [row["details"] for row in rows if row.get("name") == event],
                f"Sealed native production ledger differs from actual append-only journal: {field}")
    scans = [row["details"] for row in rows if row.get("name") == "native-server-ray-scan-receipt"]
    require(len(scans) == len(server.get("nativeScanReceipts", [])), "Native scan journal/receipt count differs")
    for before, after in zip(scans, server["nativeScanReceipts"]):
        require(all(after.get(key) == value for key, value in before.items()) and after.get("actualTCP") is True
                and after.get("mainThread") is True and "knowledgeAfterNbt" in after,
                "Native server ray scan receipt/after payment differs")
    starts = [row["details"] for row in rows if row.get("name") == "native-infusion-source-costs-observed-before-payment"]
    arcane_before = [row["details"] for row in rows if row.get("name") == "native-arcane-before-payment-observed"]
    require(arcane_before == [row["beforePayment"] for row in server.get("arcanePaymentReceipts", [])],
            "Native arcane before-payment observations differ from exact completed payment journal pairs")
    if server.get("auditPhase") == "generate":
        require(len(starts) == 1 and server.get("nativeInfusionStartObservation") == starts[0]
                and server.get("infusionCheckpoint", {}).get("costObservation") == starts[0],
                "Actual native before-payment observation differs from its sealed source checkpoint/journal")
    else:
        require(starts == [] and server.get("nativeInfusionStartObservation") is None,
                "New JVM resume/reload emitted an unreviewed source recipe start")


def verify_source_loaded(server, source_server, original, phase):
    nbt.compare_snapshot(server["sourceLoadedPlayer"], original)
    if phase == "generate":
        require(server.get("sourceWorldLoadedTiles") == [], "Generation falsely emitted new-JVM arena reload observations")
        return
    expected = {tuple(row["position"]): row for row in source_server["persistenceBaseline"]["worldTiles"]}
    rows = server.get("sourceWorldLoadedTiles")
    require(isinstance(rows, list) and len(rows) == len(expected) and len({tuple(row["position"]) for row in rows}) == len(rows),
            "New native JVM omitted/duplicated source chunk targets")
    for row in rows:
        key = tuple(row["position"])
        require(key in expected and row.get("beforeFirstChunkTick") is True
                and all(row.get(name) == value for name, value in expected[key].items()), "Native source tile provenance differs before first tick")
        if "nbt" in row:
            require(normalized_tile(row["actualLoadedNbt"]) == normalized_tile(row["nbt"]),
                    "Actual loaded typed native tile differs before first chunk tick")


def verify_reload_world(server, client, source_server):
    source = source_server["persistenceBaseline"]
    final = server["persistenceBaseline"]
    require({key: value for key, value in final.items() if key != "worldTiles"} == {key: value for key, value in source.items() if key != "worldTiles"},
            "Read-only reload changed full saved owner baseline")
    expected = {tuple(row["position"]): row for row in source["worldTiles"]}
    require(len(final["worldTiles"]) == len(expected), "Read-only reload changed owned world target count")
    for row in final["worldTiles"]:
        prior = expected[tuple(row["position"])]
        require(row["block"] == prior["block"] and ("nbt" in row) == ("nbt" in prior), "Read-only reload changed owned block/BE identity")
        if "nbt" not in row:
            continue
        before, after = dict(nbt.compound(normalized_tile(prior["nbt"]))), dict(nbt.compound(normalized_tile(row["nbt"])))
        if row["block"] == "thaumcraft:smelter_basic":
            old_burn, new_burn = nbt.scalar(before.pop("BurnTime"), 2), nbt.scalar(after.pop("BurnTime"), 2)
            require(0 <= new_burn <= old_burn, "Read-only native smelter fuel counter increased")
        require(before == after, "Read-only native world persistence changed outside source-only save byte/decreasing fuel counter")
    observations = client.get("reloadedWorldObservations")
    require(client.get("reloadedWorldObserved") is True and client.get("zeroGameplayActions") is True
            and isinstance(observations, list) and len(observations) == len(expected)
            and len({tuple(row["position"]) for row in observations}) == len(expected), "Native client did not observe every reloaded world target")
    for row in observations:
        prior = expected[tuple(row["position"])]
        require(row.get("actualNativeChunkS2C") is True and row["block"] == prior["block"], "Missing actual native reloaded chunk receipt")
        if "nbt" not in prior:
            continue
        require(row.get("actualPublicBlockEntityS2C") is True and row.get("privateDiskFieldsComparedOnServer") is True,
                "Reloaded machine lacks actual native public BE S2C")
        visible, disk = nbt.compound(nbt.snbt(row["publicSyncNbt"])), nbt.compound(nbt.snbt(prior["nbt"]))
        for key, tag in visible.items():
            if key == "BurnTime" and prior["block"] == "thaumcraft:smelter_basic":
                require(row.get("nativeBurnCounterMayAdvance") is True and tag.kind == 2, "Unreviewed public smelter counter exception")
            elif key == "renderNote":
                require(nbt.scalar(tag, 1) == int("note" in disk), "Actual reloaded table note render state differs")
            elif key == "Aspects":
                require(typed_aspects(nbt.Tag(10, {key: tag})) == typed_aspects(nbt.Tag(10, {key: disk[key]})),
                        "Actual reloaded public aspect pools differ")
            else:
                require(key in disk and tag == disk[key], "Actual native public BE fields differ from saved world")


def verify_resumed_job(server, client, source_server, original, state):
    resumed = server["resumedInfusion"]
    checkpoint = source_server["infusionCheckpoint"]
    paid, remaining = verify_checkpoint(checkpoint, source_server["persistenceBaseline"])
    require(resumed.get("loadBeforeFirstTick") is True and server.get("loadedJobObservedBeforeFirstTick") is True
            and normalized_tile(resumed["loadedJobNbt"]) == normalized_tile(checkpoint["jobNbt"])
            and resumed.get("outstandingAtLoad") == sum(remaining.values())
            and resumed.get("paidAfterReload") == sum(remaining.values())
            and resumed.get("outputItem") == "thaumcraft:crystal_aer" and resumed.get("outputCount") == 1
            and resumed.get("peripheralPedestalsEmpty") is True and resumed.get("completedByNativeTicks") is True,
            "New native JVM did not load/pay/complete its exact remaining source job")
    crafted = server.get("nativeCraftReceipts")
    require(isinstance(crafted, list) and len(crafted) == 1 and crafted[0].get("item") == "thaumcraft:crystal_aer"
            and crafted[0].get("count") == 1 and crafted[0].get("actualTCP") is True and crafted[0].get("mainThread") is True,
            "Resumed source infusion lacks exactly one actual production output craft event")
    require(inventory_bag(state["inventory"]) == inventory_bag(original["inventory"]) + Counter({stack_identity("thaumcraft:crystal_aer"): 1}),
            "Resumed native owner inventory differs beyond one real paid output")
    tiles = {tuple(row["position"]): row for row in server["persistenceBaseline"]["worldTiles"]}
    matrix = nbt.compound(nbt.snbt(tiles[(274, 67, 0)]["nbt"]))
    require(nbt.scalar(matrix["active"], 1) == 1 and nbt.scalar(matrix["crafting"], 1) == 0
            and sum(typed_aspects(nbt.Tag(10, matrix)).values()) == 0, "Saved resumed matrix has unfinished cost/state")
    for position in ((274, 65, 0), (271, 65, 0), (277, 65, 0)):
        require(not nbt.compound(nbt.snbt(tiles[position]["nbt"])).get("Items", nbt.Tag(9, [])).value,
                "Saved native output/component pedestal was not emptied")
    source_tiles = {tuple(row["position"]): row for row in source_server["persistenceBaseline"]["worldTiles"]}
    for aspect, z in (("aer", 0), ("vitreus", 4), ("vinculum", 8)):
        before, after = (nbt.compound(nbt.snbt(rows[(266, 65, z)]["nbt"])) for rows in (source_tiles, tiles))
        require(nbt.scalar(before["Amount"], 2) - nbt.scalar(after["Amount"], 2) == remaining.get(aspect, 0),
                "Saved native resumed jar did not pay exact outstanding source infusion cost")
    require(server.get("alchemyComplete") is True and server.get("reconnectProven") is True and server.get("ownerEpoch") == 2
            and server.get("actualOldConnectionClosed") is True and client.get("reconnectProven") is True
            and client.get("resumedOutputPickedUp") is True and client.get("connectionEpoch") == 2,
            "Actual native output pickup or fresh TCP owner reconnect missing")
    actions = {row["action"]: row["detail"] for row in client["actions"]}
    for name in ("native-resumed-infusion-output-remove", "native-resumed-infusion-output-pickup-received",
                 "native-tcp-disconnect", "native-tcp-reconnect-dispatched", "actual-tcp-reconnect-owner-restored"):
        require(name in actions, "Actual native resume/pickup/reconnect journal event missing")
    output = actions["native-resumed-infusion-output-pickup-received"]
    require(output.get("item") == "thaumcraft:crystal_aer" and output.get("actualOutputCount") == 1
            and all(output.get(key) is True for key in ("centerPedestalEmpty", "nativeInventoryS2C", "nativeBlockEntityS2C")),
            "Native client paid output lacks exact accepted inventory/block S2C")
    require(actions["native-tcp-reconnect-dispatched"].get("allOwnerFacadesCleared") is True
            and actions["actual-tcp-reconnect-owner-restored"].get("fullInventoryKnowledgeWarpXpMatch") is True,
            "Actual native reconnect did not clear/restore owner-bound state")


def verify_generation(server, client, state, original):
    requests = server.get("nativeResearchRequests")
    require(isinstance(requests, list) and len(requests) == len(RESEARCH), "Exact contextual native research request count differs")
    actual = []
    for row in requests:
        require(row_field(row, "status") == "APPLIED" and row.get("actualContextualTCP") is True
                and row.get("mainThread") is True and re.fullmatch(r"[0-9a-f]{64}", row.get("catalogDigest", "")),
                "Native contextual research provenance or source catalog missing")
        actual.append((row_field(row, "researchKey", "key"), row_field(row, "storedStageBefore", "expectedStage"),
                       row_field(row, "storedStageAfter", "stageAfter")))
        before = knowledge_map({"knowledge": nbt.snbt(row["knowledgeBeforeNbt"])})[1]
        after = knowledge_map({"knowledge": nbt.snbt(row["knowledgeAfterNbt"])})[1]
        cost = COSTS.get((row["researchKey"], row["expectedStage"]), {})
        for key in set(before) | set(after) | set(cost):
            require(before.get(key, 0) - after.get(key, 0) == cost.get(key, 0), "Native paid research pool debit differs")
        for prefix, source_type in (("O_", "OBSERVATION"), ("T_", "THEORY")):
            for category in ("BASICS", "ALCHEMY", "AUROMANCY", "INFUSION"):
                require(row["knowledgeBefore"][source_type + ";" + category] == before.get(prefix + category, 0)
                        and row["knowledgeAfter"][source_type + ";" + category] == after.get(prefix + category, 0),
                        "Native research pool summary differs from typed NBT")
        inventory_before, inventory_after = inventory_bag(row["inventoryBeforeNbt"]), inventory_bag(row["inventoryAfterNbt"])
        paid_items = Counter()
        aspects = ("aer", "ignis", "aqua", "terra", "ordo", "perditio") if (row["researchKey"], row["expectedStage"]) in (("ESSENTIASMELTER", 1), ("CRYSTALFARMER", 1)) else (
            ("vacuos", "lux", "motus", "gelum", "vitreus", "metallum", "victus", "mortuus", "potentia", "permutatio")
            if (row["researchKey"], row["expectedStage"]) == ("ESSENTIASMELTER", 2) else ())
        for aspect in aspects:
            paid_items[stack_identity("thaumcraft:crystal_essence", {"thaumcraft:aspects": nbt.Tag(10, {aspect: nbt.Tag(3, 1)})})] += 1
        if (row["researchKey"], row["expectedStage"]) == ("INFUSION", 1):
            paid_items[stack_identity("minecraft:stone")] = 1
            paid_items[stack_identity("minecraft:feather")] = 1
            paid_items[stack_identity("thaumcraft:phial_filled", {"thaumcraft:aspects": nbt.Tag(10, {"aer": nbt.Tag(3, 10)})})] = 1
        require(inventory_before == inventory_after + paid_items, "Native research did not consume exact typed source item requirements")
    require(tuple(actual) == RESEARCH, "Actual contextual paid progression differs from reviewed source graph")
    fixtures = server.get("fixtures")
    require(isinstance(fixtures, list) and 0 < len(fixtures) <= 8192, "Missing bounded explicit tangible-material fixtures")
    forbidden = {"thaumcraft:nitor_yellow", "thaumcraft:alumentum", "thaumcraft:infusion_matrix", "thaumcraft:smelter_basic",
                 "thaumcraft:ingot_brass", "thaumcraft:phial_filled", "thaumcraft:crystal_aer"}
    for row in fixtures:
        require(row.get("name") in ("empty-material-arena", "supplied-tangible-material", "rendered-native-item-scan-target",
                                   "ordinary-workbench-charger"),
                "Unreviewed native generation fixture kind")
        if row["name"] == "empty-material-arena":
            require(row.get("hardwareEssentiaInitiallyZero") is True and row.get("xOffset") == 256,
                    "Explicit native arena hardware was not empty")
            require(row.get("storageInitiallyEmpty") is True, "Declared native material storage did not start physically empty")
        elif row["name"] == "ordinary-workbench-charger":
            require(row.get("position") == [258, 66, 4] and row.get("block") == "thaumcraft:arcane_workbench_charger"
                    and row.get("chargedAuraChunks") == 9 and row.get("auraValuesWritten") is False
                    and row.get("nativeCraftingOfChargerProven") is False,
                    "Native charger fixture declaration invents aura values or source crafting")
        else:
            tag = nbt.compound(nbt.snbt(row["stackNbt"]))
            require(nbt.scalar(tag["id"], 8) == row["item"] and nbt.scalar(tag["count"], 3) == row["count"] > 0,
                    "Explicit tangible fixture summary differs from typed stack")
            if row["name"] == "supplied-tangible-material":
                require(row["item"] not in forbidden, "Native earned alchemy output was supplied as a fixture")
    chargers = [row for row in fixtures if row["name"] == "ordinary-workbench-charger"]
    require(len(chargers) == 1 and any(row.get("position") == [258, 66, 4]
                and row.get("block") == "thaumcraft:arcane_workbench_charger" for row in server["persistenceBaseline"]["worldTiles"]),
            "Actual independently saved owned world omits the single declared source workbench charger")
    research, pools = knowledge_map(state)
    for key, stage in FINAL_STAGES.items():
        require(research.get(key, {}).get("stage") == stage, f"Real saved progression incomplete: {key}")
    require(all(key in research for key in ("!ORECRYSTAL", "!auram", "!praecantatio")), "Required natural native scan markers absent")
    driver = client.get("researchReport", {})
    require(driver.get("knowledgeOrResearchFixtures") is False and driver.get("forcedRandomSeed") is False
            and driver.get("nativeTheoryGui") is True and driver.get("nativeResearchGui") is True,
            "Native research/theory GUI provenance missing")
    receipts = driver.get("researchReceipts")
    require(isinstance(receipts, list) and len(receipts) == len(RESEARCH)
            and tuple((row.get("researchKey"), row.get("storedStageBefore"), row.get("storedStageAfter")) for row in receipts) == RESEARCH
            and all(row.get("actualAcceptedOwnerS2C") is True for row in receipts), "Actual native client research owner receipts differ")
    theory = driver.get("theoryReceipts")
    require(isinstance(theory, list) and 0 < len(theory) <= 128 and driver.get("notesFinished") == len(theory)
            and 0 < driver.get("cardsSelected", 0) <= 2048 and driver.get("notesStarted", 0) <= 128,
            "Missing bounded naturally completed native theory notes")
    earned = {key: 0 for key in POOLS}
    for row in theory:
        require(row.get("actualAcceptedOwnerS2C") is True, "Theory award lacks actual owner S2C")
        awards, before, after = row["expectedAwards"], row["theoryBefore"], row["knowledgeAfter"]
        require(isinstance(awards, dict), "Invalid native note award pools")
        for key, amount in awards.items():
            require(key in earned and type(amount) is int and amount > 0 and after["T_" + key] - before[key] == amount,
                    "Native client exact theory award differs")
            earned[key] += amount
    require(earned["ALCHEMY"] >= 32 and earned["INFUSION"] >= 32, "Actual completed notes did not earn paid theory costs")
    source_pools = knowledge_map(original)[1]
    events = server.get("nativeKnowledgeReceipts")
    require(isinstance(events, list) and events, "Missing actual signed production knowledge events")
    expected_pools, server_earned = dict(source_pools), {key: 0 for key in POOLS}
    for row in events:
        require(row.get("type") in ("OBSERVATION", "THEORY") and row.get("category") in POOLS
                and type(row.get("amount")) is int and -64 <= row["amount"] <= 4096,
                "Unknown/unbounded signed native knowledge event")
        key = ("O_" if row["type"] == "OBSERVATION" else "T_") + row["category"]
        before = expected_pools.get(key, 0)
        require(before == row.get("beforeRaw") and row.get("afterRaw") == before + row["amount"]
                and row["afterRaw"] >= 0, "Native signed knowledge event sequence differs from real source pools")
        expected_pools[key] = row["afterRaw"]
        if row["type"] == "THEORY" and row["amount"] > 0:
            require(row.get("actualSourceNoteFinished") is True and row.get("awardedRaw") == row["amount"],
                    "Positive theory event lacks actual source completed note")
            note = nbt.compound(nbt.snbt(row["sourceNoteNbt"]))
            require(nbt.scalar(note["player"], 8) == "TC_Bootstrap" and nbt.scalar(note["inspiration"], 3) <= 0,
                    "Theory award note owner/inspiration differs")
            totals = [(nbt.scalar(nbt.compound(tag)["category"], 8), nbt.scalar(nbt.compound(tag)["total"], 3)) for tag in note["categoryTotals"].value]
            totals.sort(key=lambda entry: -entry[1])
            penalty = nbt.scalar(note["penaltyStart"], 3)
            prediction = {}
            for index, (category, percentage) in enumerate(totals):
                percent_float = struct.unpack(">f", struct.pack(">f", percentage / 100))[0]
                total = int(struct.unpack(">f", struct.pack(">f", percent_float * 32))[0] + .5)
                prediction[category] = int(max(1.0, total * .666666667)) if index > penalty else total
            require(prediction.get(row["category"]) == row["amount"], "Actual source theory note arithmetic differs from paid award")
            server_earned[row["category"]] += row["amount"]
    require(all(expected_pools.get(key, 0) == pools.get(key, 0) for key in set(expected_pools) | set(pools)),
            "Independent saved pools differ from source plus exact native signed events")
    require(server_earned == earned and server.get("nativeTheoryReceipts") == [row for row in events if row["type"] == "THEORY" and row["amount"] > 0],
            "Actual server completed-note awards differ from client owner S2C receipts")
    for name in ("nativeCraftReceipts", "nativeScanReceipts", "nativeTheoryReceipts", "nativePhialReceipts", "cruciblePaymentReceipts", "arcanePaymentReceipts", "essentiaConservationRecords"):
        require(isinstance(server.get(name), list) and server[name], f"Missing genuine native mechanics ledger: {name}")
    crafted = {row_field(row, "item", "outputItem") for row in server["nativeCraftReceipts"]}
    require({"thaumcraft:crucible", "thaumcraft:nitor_yellow", "thaumcraft:alumentum", "thaumcraft:ingot_brass",
             "thaumcraft:scribing_tools", "thaumcraft:research_table", "thaumcraft:smelter_basic", "thaumcraft:infusion_matrix"} <= crafted,
            "Required real crafting event receipts absent")
    requirements = {("UNLOCKALCHEMY", 2): ("thaumcraft:crucible",), ("UNLOCKALCHEMY", 3): ("thaumcraft:nitor_yellow",),
                    ("METALLURGY", 1): ("thaumcraft:ingot_brass",),
                    ("THEORYRESEARCH", 1): ("thaumcraft:scribing_tools", "thaumcraft:research_table"),
                    ("ESSENTIASMELTER", 3): ("thaumcraft:smelter_basic",), ("INFUSION", 2): ("thaumcraft:infusion_matrix",)}
    for row in requests:
        markers = knowledge_map({"knowledge": nbt.snbt(row["knowledgeBeforeNbt"])})[0]
        for item in requirements.get((row["researchKey"], row["expectedStage"]), ()):
            receipts = [craft for craft in server["nativeCraftReceipts"] if craft["item"] == item]
            require(len(receipts) == 1 and receipts[0].get("craftMarker") in markers,
                    "Paid native research lacks exact real prerequisite craft marker")
    verify_production_payments(server)
    checkpoint = server.get("infusionCheckpoint")
    require(server.get("checkpointedInfusion") is True and isinstance(checkpoint, dict) and client.get("checkpointObserved") is True,
            "Missing genuinely partial paid native infusion checkpoint")
    paid, remaining = verify_checkpoint(checkpoint, server["persistenceBaseline"])
    verify_pipeline(server, paid)
    return {"researchRequests": len(requests), "completedTheoryNotes": len(theory), "nativeTheoryAwards": earned,
            "partialPaidInfusionUnits": checkpoint["paidUnits"]}


def verify(root):
    root = unlinked(root)
    base = unlinked(Path(__file__).resolve().parent.parent / "port/build/tcp-alchemy")
    require(root.parent == base and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", root.name), "Expected immediate private alchemy cohort")
    marker = document(root, root / "server/.tcp-alchemy-audit.json", 16384)
    require(marker.get("marker") == MARKER and type(marker.get("schemaVersion")) is int and marker["schemaVersion"] == 1 and marker.get("purpose") == "native-alchemy"
            and marker.get("networkProtocolVersion") == "6" and marker.get("goal") == "alchemy"
            and marker.get("auditPhase") in ("generate", "resume", "reload") and marker.get("runId") == root.name
            and type(marker.get("port")) is int and 1024 <= marker["port"] <= 65535, "Invalid native alchemy isolation identity")
    require(str(uuid.UUID(marker["token"])) == marker["token"] and str(uuid.UUID(marker["sourceToken"])) == marker["sourceToken"]
            and type(marker.get("sourcePort")) is int and 1024 <= marker["sourcePort"] <= 65535, "Invalid canonical source/current native identity")
    require(re.fullmatch(r"[A-Za-z0-9_-]{1,80}", marker["sourceRunId"]) and marker["sourceRunId"] != marker["runId"], "Invalid distinct source run")
    for key in ("sourceServerReportSha256", "sourceClientReportSha256", "sourceRuntimeSha256", "sourceVerificationSha256", "worldManifestSha256"):
        require(re.fullmatch(r"[0-9a-f]{64}", marker[key]), "Invalid immutable preparation hash")
    identity(marker, marker, "server"); identity(document(root, root / "client/.tcp-alchemy-audit.json", 16384), marker, "client")
    source, source_world, source_server, source_client, source_runtime, world, copy_evidence = provenance(root, marker)
    runtime = document(root, root / "runtime.json"); identity(runtime, marker)
    source_processes, _ = processes(source_runtime, source)
    actual_processes, logs = processes(runtime, root, marker)
    require(actual_processes.isdisjoint(source_processes), "New phase reused a source JVM identity")
    require("Done (" in file(root, root / "server.stdout.log", 32 * 1024 * 1024).decode("utf-8", "replace"), "No actual dedicated ready output")
    server, client = (document(root, root / role / f"{role}-report.json") for role in ("server", "client"))
    for role, report in (("server", server), ("client", client)):
        identity(report, marker, role)
        require(report.get("passed") is True and report.get("status") == "completed" and report.get("actualTCP") is True
                and report.get("ownerUuid") == str(nbt.owner_uuid()), "Native alchemy role incomplete")
    require(server.get("saveFlushed") is True and server.get("diskNbtVerified") is True, "Actual server save/disk acceptance missing")
    require(server.get("dedicatedServer") is True and server.get("noKnowledgeResearchFixtures") is True
            and server.get("noAspectOrRecipeEffectFixtures") is True and server.get("rngNotOverridden") is True,
            "Production alchemy research/aspect/RNG provenance missing")
    require(client.get("clientInventoryStackWrites") is False and client.get("clientWorldWrites") is False
            and client.get("clientKnowledgeGrants") is False and client.get("fullSurvivalAcquisition") is False, "Client used unreviewed state writes")
    connection = client["connection"]
    require(connection.get("memory") is False and connection.get("integratedServer") is False and connection.get("connected") is True
            and connection.get("remoteHost") == "127.0.0.1" and connection.get("remotePort") == marker["port"]
            and connection.get("ownerUuid") == str(nbt.owner_uuid()), "Missing actual native PLAY TCP client")
    journal_evidence = []
    for role, report in (("server", server), ("client", client)):
        rows = journal(root, root / role / f"{role}-events.jsonl", marker, role)
        if role == "server":
            verify_server_journal(rows, server)
        else:
            transcript = report.get("actions")
            require(isinstance(transcript, list) and rows[:len(transcript)] == transcript
                    and all(type(row.get("clientTick")) is int and isinstance(row.get("detail"), dict) for row in rows),
                    "Native terminal client transcript differs from append-only journal")
        final = journal(root, root / role / f"{role}-state.json", marker, role)[-1]
        require(final.get("status") == "completed", "Native final progress state incomplete")
        journal_evidence.append({"role": role, "rows": len(rows), "sha256": sha(file(root, root / role / f"{role}-events.jsonl", 32 * 1024 * 1024))})
    screenshots = client.get("screenshots")
    require(isinstance(screenshots, list) and 1 <= len(screenshots) <= 128 and len(set(screenshots)) == len(screenshots), "Missing bounded native screenshots")
    images = []
    for value in screenshots:
        path = unlinked(Path(value))
        require(path.parent == root / "client/screenshots" and re.fullmatch(r"[a-z0-9_.-]+\.png", path.name), "Native screenshot escaped its cohort")
        images.append(png(root, path))
    require(len({image["sha256"] for image in images}) == len(images), "Native gameplay screenshot frames coincide")
    player_path = world / f"playerdata/{nbt.owner_uuid()}.dat"
    player_bytes = file(root, player_path, 1024 * 1024)
    state = nbt.disk_state(nbt.read_nbt(player_bytes))
    source_path = source_world / f"playerdata/{nbt.owner_uuid()}.dat"
    original = nbt.disk_state(nbt.read_nbt(file(source, source_path, 1024 * 1024)))
    baseline = server["persistenceBaseline"]
    nbt.compare_snapshot(baseline, state)
    nbt.compare_snapshot(client["loadedPlayer"], state, True)
    world_evidence = verify_world(root, world, baseline)
    if "worldBaseline" in server:
        require(server["worldBaseline"].get("tiles") == baseline["worldTiles"], "World persistence baseline aliases disagree")
    phase = marker["auditPhase"]
    verify_source_loaded(server, source_server, original, phase)
    if phase == "generate":
        mechanics = verify_generation(server, client, state, original)
    else:
        require(server.get("noArenaOrMaterialsWritten") is True and server.get("fixtures") == []
                and client.get("loadedWithoutGrants") is True, "New native JVM wrote generation fixtures")
        require(nbt.canonical_knowledge(original["knowledge"]) == nbt.canonical_knowledge(state["knowledge"])
                and original["warp"] == state["warp"] and original["totalExperience"] == state["totalExperience"]
                and original["experienceLevel"] == state["experienceLevel"]
                and struct.pack(">f", original["experienceProgress"]) == struct.pack(">f", state["experienceProgress"]),
                "Independent full saved knowledge/warp/XP changed after new JVM phase")
        for name in ("nativeResearchRequests", "nativeScanReceipts", "nativeTheoryReceipts", "nativeKnowledgeReceipts", "nativePhialReceipts", "cruciblePaymentReceipts", "arcanePaymentReceipts"):
            require(server.get(name) == [], "Resume/reload acquired research or emitted unreviewed native production actions")
        if phase == "reload":
            require(server.get("alchemyComplete") is True and server.get("newJvmReloadProven") is True
                    and server.get("nativeCraftReceipts") == [] and server.get("essentiaConservationRecords") == [],
                    "Read-only native reload emitted production actions")
            verify_reload_world(server, client, source_server)
            require(original["inventory"] == state["inventory"] and original["selectedSlot"] == state["selectedSlot"], "Read-only new JVM changed actual inventory")
            observations = {"client-initialized", "server-phase", "actual-tcp-play-owner", "accepted-owner-snapshot", "native-screenshot"}
            require(all(row.get("action") in observations for row in client["actions"])
                    and client["researchReport"].get("researchReceipts") == [] and client["researchReport"].get("theoryReceipts") == []
                    and client["researchReport"].get("events") == [], "Read-only reload executed native gameplay fixtures/actions")
            mechanics = {"newJvmReloadProven": True, "fullNativeOwnerAndTilesPreserved": True}
        else:
            verify_resumed_job(server, client, source_server, original, state)
            mechanics = {"newJvmResumptionProven": True, "nativeInfusionOutput": "thaumcraft:crystal_aer", "nativeReconnectProven": True}
    mechanics.update(verify_native_storage(server, client, phase, source_server))
    saved_player = {"path": str(player_path), "bytes": len(player_bytes), "sha256": sha(player_bytes)}
    reports = []
    for role in ("server", "client"):
        path = root / role / f"{role}-report.json"
        data = file(root, path)
        reports.append({"path": str(path), "bytes": len(data), "sha256": sha(data)})
    return {**{key: marker[key] for key in IDENTITY}, "passed": True, "ownerUuid": str(nbt.owner_uuid()),
            "scope": "Dedicated native TCP alchemy progression, paid mechanics, exact stopped-world/new-JVM persistence",
            "limits": ["Explicit tangible materials/arena/device fixtures", "No resource-gathering playthrough", "No full visual/GPU parity claim"],
            "typedPlayerNbtVerified": True, "typedNativeAnvilTilesVerified": True, "sourceWorldUnchanged": True,
            "worldCopy": copy_evidence, "mechanics": mechanics, "checks": mechanics, "nativeSavedPlayer": saved_player,
            "nativeWorldTiles": world_evidence, "nativeScreenshots": images, "nativeJournals": journal_evidence,
            "reports": reports, "screenshots": images, "savedPlayer": saved_player,
            "processes": runtime["processes"], "sourceProcesses": source_runtime["processes"], "logs": logs}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.root)
        if args.output:
            output = unlinked(args.output)
            require(output.parent == unlinked(args.root), "Independent output must remain in private native root")
            with output.open("x", encoding="utf-8", newline="\n") as stream:
                json.dump(result, stream, ensure_ascii=False, indent=2); stream.write("\n")
        print(json.dumps({"passed": True, "runId": result["runId"], "auditPhase": result["auditPhase"],
                          "nativeWorldTiles": len(result["nativeWorldTiles"]), "mechanics": result["mechanics"]}))
        return 0
    except (OSError, EOFError, ValueError, KeyError, TypeError, IndexError, OverflowError, struct.error, zlib.error) as failure:
        print(f"Native TCP alchemy verification failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
