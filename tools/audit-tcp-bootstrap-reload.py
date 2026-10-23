"""Independently verify new-JVM TCP reload, stopped-world provenance, and real saved NBT."""
import argparse
from collections import namedtuple
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import stat
import struct
import sys
import uuid

MARKER = "TC_PORT_TCP_BOOTSTRAP_AUDIT_V1"
COMMON = ("marker", "schemaVersion", "purpose", "networkProtocolVersion", "runId", "token", "port", "auditPhase", "goal")
SOURCE = ("sourceRunId", "sourceToken", "sourcePort", "sourceServerReportSha256", "sourceRuntimeSha256", "worldManifestSha256")
Tag = namedtuple("Tag", "kind value")


def require(condition, detail):
    if not condition:
        raise ValueError(detail)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def unlinked(path):
    path = path.absolute()
    require(path == Path(str(path.resolve())), f"Noncanonical path: {path}")
    for part in (path, *path.parents):
        try:
            entry = part.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(entry.st_mode) and not getattr(entry, "st_file_attributes", 0) & 0x400,
                f"Linked/reparse path: {part}")
    return path


def file(root, path, limit=4 * 1024 * 1024):
    path = unlinked(path)
    require(path.is_relative_to(root) and path.is_file() and path.stat().st_size <= limit, f"Missing/unbounded private evidence: {path}")
    data = path.read_bytes()
    require(len(data) <= limit, f"Evidence grew beyond bound: {path}")
    return data


def document(root, path):
    value = json.loads(file(root, path).decode("utf-8-sig"))
    require(isinstance(value, dict), f"Expected JSON object: {path}")
    return value


def identity(value, marker, role=None):
    for key in (*COMMON, *SOURCE):
        require(value.get(key) == marker.get(key), f"Reload identity mismatch: {key}")
    if role:
        require(value.get("role") == role, f"Unexpected reload role: {role}")


def source_identity(value, marker, role=None):
    for key, expected in (("marker", MARKER), ("schemaVersion", 1), ("purpose", "native-bootstrap"),
                          ("networkProtocolVersion", "6"), ("auditPhase", "generate"), ("goal", "firststeps"),
                          ("runId", marker["sourceRunId"]), ("token", marker["sourceToken"]), ("port", marker["sourcePort"])):
        require(value.get(key) == expected, f"Generation source identity mismatch: {key}")
    if role:
        require(value.get("role") == role, f"Unexpected source role: {role}")


class NbtReader:
    """All twelve binary tag types; gzip, recursion, allocation, and byte counts are bounded."""
    def __init__(self, data):
        self.data, self.position, self.tags = data, 0, 0

    def take(self, length):
        require(0 <= length <= len(self.data) - self.position, "Truncated/invalid NBT allocation")
        result = self.data[self.position:self.position + length]
        self.position += length
        return result

    def number(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def text(self):
        data = self.take(self.number(">H"))
        decoded = data.replace(b"\xc0\x80", b"\0").decode("utf-8", "surrogatepass")
        return decoded.encode("utf-16-le", "surrogatepass").decode("utf-16-le")

    def length(self):
        length = self.number(">i")
        require(0 <= length <= 262144, "Unbounded NBT array/list")
        return length

    def read(self, kind, depth=0):
        self.tags += 1
        require(depth <= 128 and self.tags <= 262144 and 1 <= kind <= 12, "Unbounded/unknown NBT tag")
        formats = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if kind in formats:
            return Tag(kind, self.number(formats[kind]))
        if kind == 7:
            return Tag(kind, tuple(struct.unpack(">b", bytes((value,)))[0] for value in self.take(self.length())))
        if kind == 8:
            return Tag(kind, self.text())
        if kind == 9:
            child, count = self.number(">B"), self.length()
            require(0 <= child <= 12 and (count == 0 or child != 0), "Invalid NBT list type")
            return Tag(kind, tuple(self.read(child, depth + 1) for _ in range(count)))
        if kind == 10:
            result = {}
            while True:
                child = self.number(">B")
                if child == 0:
                    break
                key = self.text()
                require(key not in result, "Duplicate NBT compound key")
                result[key] = self.read(child, depth + 1)
            return Tag(kind, result)
        fmt = ">i" if kind == 11 else ">q"
        return Tag(kind, tuple(self.number(fmt) for _ in range(self.length())))


def read_nbt(data):
    with gzip.GzipFile(fileobj=io.BytesIO(data)) as stream:
        raw = stream.read(8 * 1024 * 1024 + 1)
    require(len(raw) <= 8 * 1024 * 1024, "Unbounded decompressed saved player NBT")
    reader = NbtReader(raw)
    require(reader.number(">B") == 10, "Expected root NBT compound")
    reader.text()
    value = reader.read(10)
    require(reader.position == len(raw), "Trailing binary NBT bytes")
    return value


class SnbtReader:
    """Parse Minecraft's native StringTagVisitor output while retaining exact numeric tag types."""
    def __init__(self, text):
        require(isinstance(text, str) and len(text) <= 1024 * 1024, "Unbounded native SNBT snapshot")
        self.text, self.pos, self.tags = text, 0, 0

    def skip(self):
        while self.pos < len(self.text) and self.text[self.pos].isspace():
            self.pos += 1

    def expect(self, character):
        self.skip()
        require(self.pos < len(self.text) and self.text[self.pos] == character, f"Expected SNBT {character}")
        self.pos += 1

    def token(self):
        self.skip()
        require(self.pos < len(self.text), "Truncated SNBT token")
        quote = self.text[self.pos]
        if quote in "\"'":
            self.pos += 1
            result = []
            while self.pos < len(self.text):
                character = self.text[self.pos]
                self.pos += 1
                if character == quote:
                    return "".join(result), True
                if character == "\\":
                    require(self.pos < len(self.text), "Truncated SNBT string escape")
                    character = self.text[self.pos]
                    self.pos += 1
                    require(character in (quote, "\\"), "Invalid native SNBT escape")
                result.append(character)
            raise ValueError("Unclosed SNBT string")
        match = re.match(r"[A-Za-z0-9_.+\-]+", self.text[self.pos:])
        require(match is not None, "Invalid SNBT bare token")
        self.pos += len(match[0])
        return match[0], False

    def read(self, depth=0):
        self.tags += 1
        require(depth <= 128 and self.tags <= 262144, "Unbounded SNBT structure")
        self.skip()
        require(self.pos < len(self.text), "Truncated SNBT value")
        character = self.text[self.pos]
        if character == "{":
            self.pos += 1
            result = {}
            self.skip()
            while self.pos < len(self.text) and self.text[self.pos] != "}":
                key, _ = self.token()
                require(key not in result, "Duplicate SNBT key")
                self.expect(":")
                result[key] = self.read(depth + 1)
                self.skip()
                if self.text[self.pos] != "}":
                    self.expect(",")
            self.expect("}")
            return Tag(10, result)
        if character == "[":
            self.pos += 1
            self.skip()
            array_kind = None
            if self.pos + 1 < len(self.text) and self.text[self.pos].upper() in "BIL" and self.text[self.pos + 1] == ";":
                array_kind = {"B": 7, "I": 11, "L": 12}[self.text[self.pos].upper()]
                self.pos += 2
            result = []
            self.skip()
            while self.pos < len(self.text) and self.text[self.pos] != "]":
                result.append(self.read(depth + 1))
                self.skip()
                if self.text[self.pos] != "]":
                    self.expect(",")
            self.expect("]")
            if array_kind is not None:
                required = {7: 1, 11: 3, 12: 4}[array_kind]
                require(all(value.kind == required for value in result), "SNBT array numeric types differ")
                return Tag(array_kind, tuple(value.value for value in result))
            require(not result or len({value.kind for value in result}) == 1, "Heterogeneous native SNBT list")
            return Tag(9, tuple(result))
        token, quoted = self.token()
        if quoted:
            return Tag(8, token)
        if token.lower() in ("true", "false"):
            return Tag(1, int(token.lower() == "true"))
        numeric = re.fullmatch(r"([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)([bBsSlLfFdD]?)", token)
        if numeric:
            number, suffix = numeric.groups()
            kind = {"b": 1, "s": 2, "l": 4, "f": 5, "d": 6}.get(suffix.lower(), 6 if any(c in number for c in ".eE") else 3)
            value = float(number) if kind in (5, 6) else int(number)
            if kind == 5:
                value = struct.unpack(">f", struct.pack(">f", value))[0]
            return Tag(kind, value)
        return Tag(8, token)


def snbt(text):
    reader = SnbtReader(text)
    value = reader.read()
    reader.skip()
    require(reader.pos == len(text), "Trailing SNBT bytes")
    return value


def compound(tag):
    require(tag.kind == 10, "Expected NBT compound")
    return tag.value


def scalar(tag, kind=None):
    if kind is not None:
        require(tag.kind == kind, f"Expected numeric/string NBT type {kind}")
    return tag.value


def canonical_knowledge(tag, remove_popup=False):
    data = compound(tag)
    result = {"research": [], "knowledge": []}
    for name in result:
        require(data[name].kind == 9, f"Expected knowledge {name} list")
        keys = set()
        for child in data[name].value:
            entry = compound(child)
            key = scalar(entry["key"], 8)
            require(key not in keys, f"Duplicate knowledge {name} key")
            keys.add(key)
            row = {"key": key}
            if name == "research":
                if "stage" in entry:
                    row["stage"] = scalar(entry["stage"], 3)
                if "flags" in entry:
                    flags = sorted(flag for flag in scalar(entry["flags"], 8).split(",") if flag and not (remove_popup and flag == "POPUP"))
                    if flags:
                        row["flags"] = flags
            else:
                row["amount"] = scalar(entry["amount"], 3)
            result[name].append(row)
        result[name].sort(key=lambda row: row["key"])
    require(set(data) == {"research", "knowledge"}, "Unknown full knowledge fields")
    return result


def owner_uuid():
    digest = bytearray(hashlib.md5(b"OfflinePlayer:TC_Bootstrap").digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return uuid.UUID(bytes=bytes(digest))


def disk_state(tag):
    data = compound(tag)
    actual_uuid = scalar(data["UUID"], 11)
    require(len(actual_uuid) == 4 and uuid.UUID(bytes=b"".join(struct.pack(">I", value & 0xFFFFFFFF) for value in actual_uuid)) == owner_uuid(),
            "Saved player UUID differs from exact native offline owner")
    attachments = compound(data["neoforge:attachments"])
    return {"knowledge": attachments["thaumcraft:knowledge"], "warp": attachments["thaumcraft:warp"],
            "inventory": data["Inventory"], "selectedSlot": scalar(data["SelectedItemSlot"], 3),
            "totalExperience": scalar(data["XpTotal"], 3), "experienceLevel": scalar(data["XpLevel"], 3),
            "experienceProgress": scalar(data["XpP"], 5)}


def compare_snapshot(snapshot, state, client=False):
    require(snapshot.get("ownerUuid") == str(owner_uuid()) and snapshot.get("firststepsStage") == 4, "Live loaded owner/stage differs")
    require(canonical_knowledge(snbt(snapshot["knowledgeNbt"])) == canonical_knowledge(state["knowledge"], client),
            "Live full knowledge differs from independently read disk data (only client POPUP may be removed)")
    require(snbt(snapshot["warpNbt"]) == state["warp"] and snbt(snapshot["inventoryNbt"]) == state["inventory"],
            "Live warp/inventory components differ from independently read native NBT")
    for key in ("selectedSlot", "totalExperience", "experienceLevel"):
        require(snapshot.get(key) == state[key], f"Live native {key} differs")
    require(struct.pack(">f", snapshot["experienceProgress"]) == struct.pack(">f", state["experienceProgress"]), "Live exact XP float differs")


def verify(root):
    root = unlinked(root)
    base = unlinked(Path(__file__).resolve().parent.parent / "port/build/tcp-bootstrap")
    require(root.parent == base and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", root.name), "Expected immediate private reload cohort")
    marker = document(root, root / "server/.tcp-bootstrap-audit.json")
    require(marker.get("marker") == MARKER and marker.get("schemaVersion") == 1 and marker.get("purpose") == "native-bootstrap"
            and marker.get("networkProtocolVersion") == "6" and marker.get("auditPhase") == "reload" and marker.get("goal") == "firststeps"
            and marker.get("runId") == root.name and isinstance(marker.get("port"), int) and 1024 <= marker["port"] <= 65535,
            "Unknown reload identity/protocol/phase")
    uuid.UUID(marker["token"])
    uuid.UUID(marker["sourceToken"])
    require(re.fullmatch(r"[A-Za-z0-9_-]{1,80}", marker["sourceRunId"]) and marker["sourceRunId"] != root.name
            and marker["sourceToken"] != marker["token"] and 1024 <= marker["sourcePort"] <= 65535, "Reload must have distinct fresh identity")
    identity(marker, marker, "server")
    identity(document(root, root / "client/.tcp-bootstrap-audit.json"), marker, "client")
    preparation = document(root, root / "preparation.json")
    identity(preparation, marker)
    source = unlinked(base / marker["sourceRunId"])
    require(Path(preparation["sourceRoot"]) == source, "Source private root differs")
    inputs = root / "preparation-inputs"
    for relative, key in (("generation-server-report.json", "sourceServerReportSha256"),
                          ("generation-runtime.json", "sourceRuntimeSha256"), ("world-copy-manifest.json", "worldManifestSha256")):
        require(sha(file(root, inputs / relative)) == marker[key], f"Copied provenance document hash differs: {relative}")
    source_server = document(root, inputs / "generation-server-report.json")
    source_runtime = document(root, inputs / "generation-runtime.json")
    source_client = document(root, inputs / "generation-client-report.json")
    for value, role in ((source_server, "server"), (source_client, "client"), (source_runtime, None)):
        source_identity(value, marker, role)
    for copied, original in (("generation-server-report.json", "server/server-report.json"),
                             ("generation-runtime.json", "runtime.json"), ("generation-client-report.json", "client/client-report.json")):
        require(file(root, inputs / copied) == file(source, source / original), "Original stopped generation evidence changed")
    require(source_server.get("passed") is True and source_server.get("saveFlushed") is True and source_server.get("diskNbtVerified") is True
            and source_server.get("finalFirststepsStage") == 4 and source_server.get("finalFirststepsComplete") is True
            and source_server.get("reconnectProven") is True and source_server.get("ownerEpoch") == 2
            and source_client.get("passed") is True and source_client.get("firststepsStage") == 4,
            "Generation did not prove completed FIRSTSTEPS and actual native reconnect")
    source_verification = document(source, source / "verification.json")
    source_identity(source_verification, marker)
    require(source_verification.get("passed") is True, "Generation independent acceptance did not pass")
    manifest = document(root, inputs / "world-copy-manifest.json")
    source_world, copied_world = source / "server/tcp-bootstrap-audit", root / "server/tcp-bootstrap-audit"
    require(manifest.get("sourceRunId") == marker["sourceRunId"] and manifest.get("sourceToken") == marker["sourceToken"]
            and Path(manifest["sourceRoot"]) == source and Path(manifest["sourceWorld"]) == source_world
            and Path(manifest["copiedWorld"]) == copied_world and manifest.get("sourceStopped") is True
            and manifest.get("sourceUnchangedDuringCopy") is True and manifest.get("copiedBytesExact") is True,
            "Missing exact stopped-world copy provenance")
    files = manifest.get("files")
    require(isinstance(files, list) and 0 < len(files) <= 10000 and len(files) == manifest.get("fileCount"), "Unbounded/empty world manifest")
    names, total = set(), 0
    for entry in files:
        relative = Path(entry["path"])
        require(not relative.is_absolute() and ".." not in relative.parts and entry["path"] not in names, "Unsafe/duplicate copied world path")
        names.add(entry["path"])
        data = file(source, source_world / relative, 64 * 1024 * 1024)
        require(len(data) == entry["bytes"] and sha(data) == entry["sha256"], "Generation world changed after its stopped exact copy")
        total += len(data)
    require(total == manifest.get("totalBytes") and total <= 512 * 1024 * 1024, "World manifest byte count differs")
    actual_names = set()
    for path in source_world.rglob("*"):
        unlinked(path)
        if path.is_file():
            actual_names.add(path.relative_to(source_world).as_posix())
    require(names == actual_names and "level.dat" in names and f"playerdata/{owner_uuid()}.dat" in names, "Stopped generation world manifest is incomplete")
    for relative, key in (("eula.txt", "eulaSha256"), ("options.txt", "optionsSha256")):
        require(sha(file(root, inputs / relative)) == preparation[key], "Copied existing EULA/preference bytes differ")
    require(file(root, inputs / "eula.txt", 4096) == file(root, root / "server/eula.txt", 4096), "Runtime EULA bytes differ")
    require(re.search(r"(?m)^\s*eula\s*=\s*true\s*$", file(root, root / "server/eula.txt", 4096).decode("utf-8-sig")), "Missing already accepted source EULA")
    require("pauseOnLostFocus:false" in file(root, root / "client/options.txt", 1024 * 1024).decode("utf-8-sig").splitlines(), "Runtime private options lost focus flag differs")
    properties = file(root, root / "server/server.properties", 8192).decode("utf-8-sig").splitlines()
    for expected in ("level-name=tcp-bootstrap-audit", "server-ip=127.0.0.1", f"server-port={marker['port']}", "online-mode=false", "gamemode=survival"):
        require(expected in properties, f"Lost private server property: {expected}")
    runtime = document(root, root / "runtime.json")
    identity(runtime, marker)
    require(runtime.get("status") == "completed" and runtime.get("buildExitCode") == 0 and runtime.get("serverReady") is True,
            "Actual new-JVM runner did not complete")
    process_sets = []
    logs = []
    for actual, context in ((source_runtime, source), (runtime, root)):
        processes = actual.get("processes")
        require(isinstance(processes, list) and len(processes) == 2 and {p.get("role") for p in processes} == {"server", "client"}, "Expected exactly one server and client JVM per phase")
        identities = set()
        for process in processes:
            role = process["role"]
            require(isinstance(process.get("pid"), int) and process["pid"] > 0 and process.get("exitCode") == 0 and process.get("forcedStop") is False,
                    "Actual owned JVM failed or required a forced stop")
            identities.add((process["pid"], process["startedAtUtc"]))
            if context == root:
                name = "tcpBootstrapAudit" + role.capitalize()
                launch = context / "launch" / role
                for suffix, key in (("RunVmArgs.txt", "vmArgsSha256"), ("RunProgramArgs.txt", "programArgsSha256"), ("RunClasspath.txt", "classpathArgsSha256")):
                    require(sha(file(context, launch / (name + suffix), 1024 * 1024)) == process[key], "Native generated launch args changed")
                vm = file(context, launch / (name + "RunVmArgs.txt"), 1024 * 1024).decode("utf-8-sig")
                require("-XX:ActiveProcessorCount=2" in vm and ("-Xmx1024m" if role == "server" else "-Xmx768m") in vm
                        and "-Dthaumcraft.tcpBootstrapReloadAudit=true" in vm, "Unbounded/wrong-purpose native reload JVM")
                require(str(launch) in process["arguments"] and "net.neoforged.devlaunch.Main" in process["arguments"], "Missing private native launch path")
                for stream in ("stdout", "stderr"):
                    path = context / f"{role}.{stream}.log"
                    require(Path(process[stream + "File"]) == path, "Unexpected process log path")
                    data = file(context, path, 32 * 1024 * 1024)
                    logs.append({"path": str(path), "bytes": len(data), "sha256": sha(data)})
        require(len(identities) == 2, "Client/server JVM identities coincide")
        process_sets.append(identities)
    require(process_sets[0].isdisjoint(process_sets[1]), "Reload reused a generation JVM identity")
    require("Done (" in file(root, root / "server.stdout.log", 32 * 1024 * 1024).decode("utf-8", "replace"), "No new dedicated server ready output")
    server = document(root, root / "server/server-report.json")
    client = document(root, root / "client/client-report.json")
    for role, report in (("server", server), ("client", client)):
        identity(report, marker, role)
        require(report.get("passed") is True and report.get("status") == "completed" and report.get("actualTCP") is True
                and report.get("loadedWithoutGrants") is True and report.get("ownerUuid") == str(owner_uuid())
                and report.get("firststepsStage") == 4 and report.get("researchRequests") == 0, "Native reload role acceptance incomplete")
    for flag in ("dedicatedServer", "noArenaOrMaterialsWritten", "noKnowledgeResearchFixtures", "newJvmRestartProven", "saveFlushed", "diskNbtVerified"):
        require(server.get(flag) is True, f"Server restart evidence missing: {flag}")
    require(client.get("clientInventoryStackWrites") is False and client.get("clientKnowledgeGrants") is False
            and client.get("acceptedCurrentConnectionOwner") is True and client.get("stableNativeTicks", 0) >= 40
            and isinstance(client.get("catalogDigest"), str) and len(client["catalogDigest"]) >= 32, "Missing actual native received owner proof")
    connection = client.get("connection", {})
    require(connection.get("memory") is False and connection.get("integratedServer") is False and connection.get("connected") is True
            and connection.get("remoteHost") == "127.0.0.1" and connection.get("remotePort") == marker["port"]
            and connection.get("ownerUuid") == str(owner_uuid()), "No live separate actual PLAY TCP client")
    source_player_path = source_world / f"playerdata/{owner_uuid()}.dat"
    reload_player_path = copied_world / f"playerdata/{owner_uuid()}.dat"
    original_bytes = file(source, source_player_path, 1024 * 1024)
    loaded_bytes = file(root, reload_player_path, 1024 * 1024)
    original = disk_state(read_nbt(original_bytes))
    loaded = disk_state(read_nbt(loaded_bytes))
    require(canonical_knowledge(original["knowledge"]) == canonical_knowledge(loaded["knowledge"]), "Full independently read saved knowledge changed across restart")
    for key in ("warp", "inventory", "selectedSlot", "totalExperience", "experienceLevel", "experienceProgress"):
        require(original[key] == loaded[key], f"Independent disk {key} changed across restart")
    baseline = source_server["persistenceBaseline"]
    require(server.get("persistenceBaseline") == baseline and baseline.get("workbenchBlock") == "thaumcraft:arcane_workbench"
            and baseline.get("bookshelfBlock") == "minecraft:air" and snbt(baseline["workbenchNbt"]).kind == 10,
            "Persisted native world/table baseline differs")
    compare_snapshot(baseline, original)
    compare_snapshot(server["loadedPlayer"], original)
    compare_snapshot(client["loadedPlayer"], original, True)
    research = {row["key"]: row for row in canonical_knowledge(original["knowledge"])["research"]}
    require(research.get("FIRSTSTEPS", {}).get("stage") == 4 and all(key in research for key in ("!gotcrystals", "!gotdream", "!gotthaumonomicon")), "Actual saved complete acquisition/research is missing")
    amounts = {row["key"]: row["amount"] for row in canonical_knowledge(original["knowledge"])["knowledge"]}
    require(amounts.get("O_BASICS", 0) == baseline["basicsRaw"], "Independently read saved BASICS category observations differ")
    scans = source_server.get("nativeScanReceipts", [])
    require(len(scans) == 7 and len({scan.get("researchKey") for scan in scans}) == 7
            and all(research.get(scan["researchKey"], {}).get("stage", 0) == 0 and scan["researchKey"] in research for scan in scans),
            "Seven actual native generic scan markers did not survive the disk restart")
    inventory = [compound(value) for value in original["inventory"].value]
    require(sum(scalar(item["count"]) for item in inventory if scalar(item["id"], 8) == "thaumcraft:thaumometer") == 1
            and sum(scalar(item["count"]) for item in inventory if scalar(item["id"], 8) == "thaumcraft:thaumonomicon") == 1,
            "Independently read genuine book/thaumometer inventory is missing")
    return {**{key: marker[key] for key in (*COMMON, *SOURCE)}, "passed": True, "ownerUuid": str(owner_uuid()),
            "scope": "New dedicated/client JVMs, exact stopped private-world copy, actual TCP login, full FIRSTSTEPS/native owner data preserved without grants",
            "limits": ["Supplied materials/controlled arena in referenced generation", "No resource-gathering playthrough", "No acceptance of later crucible/essentia/infusion mechanics"],
            "newJvmRestartProven": True, "finalFirststepsStage": 4, "fullKnowledgeWarpInventoryXpVerified": True,
            "clientOnlyPopupExcluded": True, "worldCopy": {"fileCount": len(files), "bytes": total, "manifestSha256": marker["worldManifestSha256"], "sourceUnchangedAfterReload": True},
            "nativeSavedPlayers": [{"path": str(path), "bytes": len(data), "sha256": sha(data)} for path, data in ((source_player_path, original_bytes), (reload_player_path, loaded_bytes))],
            "processes": runtime["processes"], "sourceProcesses": source_runtime["processes"], "logs": logs,
            "reports": [{"path": str(root / role / f"{role}-report.json"), "sha256": sha(file(root, root / role / f"{role}-report.json"))} for role in ("server", "client")]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        report = verify(args.root)
        if args.output:
            output = unlinked(args.output)
            require(output.parent == unlinked(args.root), "Verification output must be directly in the private reload root")
            with output.open("x", encoding="utf-8", newline="\n") as stream:
                json.dump(report, stream, ensure_ascii=False, indent=2)
                stream.write("\n")
        print(json.dumps({"passed": True, "runId": report["runId"], "sourceRunId": report["sourceRunId"],
                          "finalFirststepsStage": 4, "newJvmRestartProven": True, "worldCopyFiles": report["worldCopy"]["fileCount"]}))
        return 0
    except (OSError, EOFError, ValueError, KeyError, TypeError, IndexError, OverflowError, struct.error) as failure:
        print(f"TCP bootstrap reload verification failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
