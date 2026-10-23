"""Independently check one private dedicated-server/native-client bootstrap cohort.

Usage: python tools/audit-tcp-bootstrap.py --root port/build/tcp-bootstrap/<run-id>
The report checks both the historical stage-2 cohort and the full FIRSTSTEPS
cohort with native scans and a second TCP connection. A new-JVM reload has its
own verifier; resource gathering and ordinary sleep remain outside this scope.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import struct
import sys
import uuid
import zlib

MARKER = "TC_PORT_TCP_BOOTSTRAP_AUDIT_V1"
COMMON = ("marker", "schemaVersion", "purpose", "networkProtocolVersion", "runId", "token", "port")
OPTIONAL_IDENTITY = ("auditPhase", "goal", "sourceRunId", "sourceToken")
PNG_NAMES = (
    "01-thaumonomicon-browser.png",
    "02-native-arcane-workbench.png",
    "03-firststeps-workbench-requirement.png",
)
FULL_PNG_NAMES = PNG_NAMES + (
    "04-native-thaumometer-preview.png",
    "05-native-thaumometer-final-scan.png",
    "06-firststeps-thaumometer-observation-requirements.png",
    "07-firststeps-completed.png",
)
SCAN_BLOCKS = ("minecraft:stone", "minecraft:dirt", "minecraft:grass_block", "minecraft:sand",
               "minecraft:gravel", "minecraft:oak_log", "minecraft:poppy")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def unlinked(path: Path) -> Path:
    path = Path(os.path.abspath(path))
    for part in (path, *path.parents):
        try:
            attributes = part.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(attributes.st_mode), f"Linked path: {part}")
        require(not getattr(attributes, "st_file_attributes", 0) & 0x400, f"Reparse path: {part}")
    return path


def private_file(root: Path, path: Path, limit: int) -> bytes:
    path = unlinked(path)
    require(path.is_relative_to(root), f"Evidence escaped cohort: {path}")
    require(path.is_file() and path.stat().st_size <= limit, f"Missing/unbounded evidence: {path}")
    return path.read_bytes()


def document(root: Path, path: Path, limit: int = 2 * 1024 * 1024) -> dict:
    value = json.loads(private_file(root, path, limit).decode("utf-8-sig"))
    require(isinstance(value, dict), f"Expected JSON object: {path}")
    return value


def journal(root: Path, path: Path) -> list[dict]:
    data = private_file(root, path, 32 * 1024 * 1024)
    require(data.endswith(b"\n"), f"Truncated evidence journal: {path}")
    rows = [json.loads(line.decode("utf-8")) for line in data.splitlines() if line.strip()]
    require(rows and all(isinstance(row, dict) for row in rows), f"Empty/invalid journal: {path}")
    return rows


def identity(value: dict, marker: dict, role: str | None = None) -> None:
    for key in COMMON + OPTIONAL_IDENTITY:
        require(value.get(key) == marker.get(key), f"Cohort identity mismatch: {key}")
    if role is not None:
        require(value.get("role") == role, f"Role mismatch: {role}")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def png(root: Path, path: Path) -> dict:
    data = private_file(root, path, 16 * 1024 * 1024)
    require(len(data) > 1024 and data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {path}")
    offset, dimensions, image_data, ended = 8, None, False, False
    while offset < len(data):
        require(offset + 12 <= len(data), f"Truncated PNG chunk: {path}")
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4 : offset + 8]
        require(offset + 12 + length <= len(data), f"Invalid PNG length: {path}")
        payload = data[offset + 8 : offset + 8 + length]
        checksum = struct.unpack_from(">I", data, offset + 8 + length)[0]
        require(zlib.crc32(kind + payload) & 0xFFFFFFFF == checksum, f"PNG CRC mismatch: {path}")
        if kind == b"IHDR":
            require(dimensions is None and length == 13, f"Invalid PNG header: {path}")
            dimensions = struct.unpack_from(">II", payload)
        if kind == b"IDAT":
            image_data = True
        offset += 12 + length
        if kind == b"IEND":
            require(length == 0 and offset == len(data), f"Trailing PNG bytes: {path}")
            ended = True
            break
    require(dimensions is not None and image_data and ended, f"Incomplete PNG: {path}")
    require(320 <= dimensions[0] <= 8192 and 240 <= dimensions[1] <= 8192, f"Invalid native screenshot size: {path}")
    return {"path": str(path), "width": dimensions[0], "height": dimensions[1], "bytes": len(data), "sha256": sha256(data)}


def verify(root: Path) -> dict:
    root = unlinked(root)
    expected_base = unlinked(Path(__file__).resolve().parent.parent / "port" / "build" / "tcp-bootstrap")
    require(root.parent == expected_base and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", root.name) is not None,
            "Expected an immediate private port/build/tcp-bootstrap cohort")
    server_marker = document(root, root / "server" / ".tcp-bootstrap-audit.json", 4096)
    client_marker = document(root, root / "client" / ".tcp-bootstrap-audit.json", 4096)
    require(server_marker.get("marker") == MARKER and server_marker.get("schemaVersion") == 1,
            "Unknown isolation marker/schema")
    require(server_marker.get("purpose") == "native-bootstrap" and server_marker.get("networkProtocolVersion") == "6",
            "Expected current protocol 6 native bootstrap")
    require(server_marker.get("runId") == root.name and isinstance(server_marker.get("port"), int)
            and 1024 <= server_marker["port"] <= 65535, "Invalid run ID/port")
    uuid.UUID(server_marker["token"])
    full = server_marker.get("goal") == "firststeps"
    require(server_marker.get("auditPhase", "generate") == "generate", "Use the separate verifier for reload cohorts")
    require(server_marker.get("goal") in (None, "firststeps"), "Unknown bootstrap goal")
    final_stage, request_count = (4, 3) if full else (2, 2)
    png_names = FULL_PNG_NAMES if full else PNG_NAMES
    identity(server_marker, server_marker, "server")
    identity(client_marker, server_marker, "client")
    preparation = document(root, root / "preparation.json")
    identity(preparation, server_marker)
    for relative, key in (("preparation-inputs/eula.txt", "eulaSha256"), ("preparation-inputs/options.txt", "optionsSha256")):
        require(sha256(private_file(root, root / relative, 1024 * 1024)) == preparation.get(key),
                f"Copied existing preference/EULA bytes differ: {relative}")
    require(sha256(private_file(root, root / "server" / "eula.txt", 4096)) == preparation.get("eulaSha256"),
            "Runtime server EULA differs from the existing accepted source")
    current_options = private_file(root, root / "client" / "options.txt", 1024 * 1024)
    require("pauseOnLostFocus:false" in current_options.decode("utf-8-sig").splitlines(),
            "Private client lost-focus option changed")
    properties = private_file(root, root / "server" / "server.properties", 8192).decode("utf-8-sig")
    for line in ("level-name=tcp-bootstrap-audit", "server-ip=127.0.0.1", f"server-port={server_marker['port']}",
                 "online-mode=false", "gamemode=survival", "difficulty=peaceful", "spawn-protection=0",
                 "view-distance=4", "simulation-distance=4"):
        require(line in properties.splitlines(), f"Server isolation property missing: {line}")
    runtime = document(root, root / "runtime.json")
    identity(runtime, server_marker)
    require(runtime.get("status") == "completed" and runtime.get("buildExitCode") == 0 and runtime.get("serverReady") is True,
            "Runner did not complete prepared actual engines")
    processes = runtime.get("processes")
    require(isinstance(processes, list) and len(processes) == 2 and {p.get("role") for p in processes} == {"server", "client"},
            "Expected exactly one owned server and one owned client JVM")
    require(len({p.get("pid") for p in processes}) == 2, "Server/client process identities coincide")
    launch_evidence = []
    for process in processes:
        role = process["role"]
        require(isinstance(process.get("pid"), int) and process["pid"] > 0 and process.get("exitCode") == 0
                and process.get("forcedStop") is False, f"Native {role} failed or required forced stop")
        name = "tcpBootstrapAudit" + role.capitalize()
        files = root / "launch" / role
        for suffix, key in (("RunVmArgs.txt", "vmArgsSha256"), ("RunProgramArgs.txt", "programArgsSha256"),
                            ("RunClasspath.txt", "classpathArgsSha256")):
            path = files / (name + suffix)
            require(sha256(private_file(root, path, 1024 * 1024)) == process.get(key), f"Recorded native launch args changed: {path}")
        vm = private_file(root, files / (name + "RunVmArgs.txt"), 1024 * 1024).decode("utf-8-sig")
        require("-XX:ActiveProcessorCount=2" in vm and ("-Xmx1024m" if role == "server" else "-Xmx768m") in vm,
                f"Unbounded {role} JVM arguments")
        require("net.neoforged.devlaunch.Main" in process.get("arguments", "") and str(files) in process.get("arguments", ""),
                f"Missing generated private native launch path: {role}")
        for field, expected in (("stdoutFile", root / f"{role}.stdout.log"), ("stderrFile", root / f"{role}.stderr.log")):
            require(Path(process[field]) == expected, f"Unexpected process log path: {role}")
            data = private_file(root, expected, 32 * 1024 * 1024)
            launch_evidence.append({"path": str(expected), "bytes": len(data), "sha256": sha256(data)})
    require("Done (" in (root / "server.stdout.log").read_text(encoding="utf-8", errors="replace"), "No dedicated server ready output")

    server = document(root, root / "server" / "server-report.json")
    client = document(root, root / "client" / "client-report.json")
    for role, report in (("server", server), ("client", client)):
        identity(report, server_marker, role)
        require(report.get("passed") is True and report.get("status") == "completed", f"Native {role} scenario did not pass")
    digest = bytearray(hashlib.md5(b"OfflinePlayer:TC_Bootstrap").digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    owner = str(uuid.UUID(bytes=bytes(digest)))
    require(server.get("ownerUuid") == owner and client.get("ownerUuid") == owner, "Expected matching fresh native offline owner")
    for flag in ("actualTCP", "dedicatedServer", "noKnowledgeResearchFixtures", "controlledWake", "controlledArenaAndMaterials",
                 "diskNbtVerified", "saveFlushed"):
        require(server.get(flag) is True, f"Server bootstrap proof missing: {flag}")
    for key, expected in (("nativeRecipeCraftCount", 2), ("bookCraftEvents", 1), ("workbenchCraftEvents", 1),
                          ("finalFirststepsStage", final_stage), ("gotDream", True), ("gotBook", True),
                          ("workbenchBlock", "thaumcraft:arcane_workbench"), ("actualContextualRequestCount", request_count)):
        require(server.get(key) == expected, f"Server production observation mismatch: {key}")
    for flag in ("survivalPlaythroughProven", "resourceGatheringProven", "ordinarySleepOrWalkingProven", "reloadReconnectProven"):
        require(server.get(flag) is False, f"Unsupported acceptance claim: {flag}")
    checks = server.get("checks", [])
    check_names = {check.get("name") for check in checks if check.get("passed") is True}
    expected_checks = {"fresh-native-owner", "actual-crystal-collision-pickup", "controlled-native-wake",
                       "native-dust-result-takes-and-remainders", "native-delayed-bookshelf-conversion",
                       "native-special-book-collision-pickup", "native-firststeps-start", "native-paid-workbench-conversion",
                       "native-firststeps-craft-receipt-accepted", "actual-client-native-gui-and-payload-acceptance"}
    if full:
        expected_checks.update({"native-paid-thaumometer-arcane-craft", "native-seven-targeted-block-scans",
                                "native-paid-firststeps-completion", "native-second-tcp-owner-reconnect"})
    require(expected_checks <= check_names and all(check.get("passed") is True for check in checks), "Missing native server milestone checks")
    requests = server.get("actualContextualRequests", [])
    require(len(requests) == request_count, "Expected the exact real contextual research request count")
    for request, before, after in zip(requests, (-1, 1, 2), (1, 2, 4)):
        require(request.get("researchKey") == "FIRSTSTEPS" and request.get("expectedStage") == before
                and request.get("resultingStage") == after and request.get("result") == "APPLIED"
                and request.get("actualTCP") is True and request.get("mainThread") is True and request.get("ownerUuid") == owner,
                "Contextual request was not accepted by real TCP owner/main-thread handler")
        require(isinstance(request.get("catalogDigest"), str) and len(request["catalogDigest"]) >= 32, "Missing authoritative catalog digest")
    server_events = journal(root, root / "server" / "server-events.jsonl")
    require(server_events == server.get("nativeEvents"), "Server terminal transcript differs from its native event journal")
    crafted = [row["details"] for row in server_events if row.get("name") == "native-item-crafted-event"]
    expected_crafts = ["thaumcraft:salis_mundus", "thaumcraft:salis_mundus", "thaumcraft:thaumonomicon", "thaumcraft:arcane_workbench"]
    if full:
        expected_crafts.append("thaumcraft:thaumometer")
    require([row.get("item") for row in crafted] == expected_crafts,
            "Native crafting/conversion event order differs")
    require(all(row.get("count") == 1 for row in crafted) and all("CraftingMenu" in row.get("actualMenuType", "") for row in crafted[:2]),
            "Dust was not taken from the real vanilla crafting menu")
    server_state = journal(root, root / "server" / "server-state.json")[-1]
    identity(server_state, server_marker, "server")
    require(server_state.get("firststepsStage") == final_stage and server_state.get("dustCount") == 0 and server_state.get("crystalCount") == 0,
            "Final native server state/payment mismatch")

    connection = client.get("connection", {})
    require(client.get("actualTCP") is True and connection.get("memory") is False and connection.get("integratedServer") is False
            and connection.get("remoteHost") == "127.0.0.1" and connection.get("remotePort") == server_marker["port"]
            and connection.get("ownerUuid") == owner and connection.get("connected") is True, "Missing separate live TCP client proof")
    require(client.get("firststepsStage") == final_stage and client.get("dustTakes") == 2, "Client accepted stage/takes mismatch")
    for flag in ("clientInventoryStackWrites", "clientKnowledgeGrants", "fullSurvivalAcquisition"):
        require(client.get(flag) is False, f"Unexpected direct mutation/unsupported client claim: {flag}")
    require(client.get("thaumometerOrFullFirststepsTested") is full, "Full FIRSTSTEPS scope differs from isolation goal")
    actions = client.get("actions", [])
    require(actions, "Missing client native actions")
    for action in actions:
        identity(action, server_marker, "client")
    def named(name: str) -> list[dict]:
        return [action for action in actions if action.get("action") == name]
    for action_name in ("actual-tcp-play-owner", "native-crafting-table-open", "bookshelf-dust-use", "native-book-use",
                        "browser-FIRSTSTEPS-start", "workbench-dust-use", "native-workbench-open", "native-book-reopen",
                        "page-FIRSTSTEPS-advance"):
        require(len(named(action_name)) == 1, f"Expected one native client action: {action_name}")
    require(len(named("native-salis-result-take")) == 2 and len(named("native-screenshot")) == len(png_names), "Missing exact native takes/screenshots")
    clicks = named("vanilla-menu-click")
    require(len(clicks) >= 8 and all(row.get("detail", {}).get("nativeC2S") is True for row in clicks), "No real inventory C2S clicks")
    page_actions = [("browser-FIRSTSTEPS-start", -1), ("page-FIRSTSTEPS-advance", 1)]
    if full:
        page_actions.append(("page-FIRSTSTEPS-complete", 2))
    for index, (action_name, expected) in enumerate(page_actions):
        detail = named(action_name)[0]["detail"]
        require(detail.get("expectedStage") == expected and detail.get("nativeScreenClick") is True
                and detail.get("catalogDigest") == requests[index]["catalogDigest"],
                "Native research screen action does not match authoritative contextual request")
    snapshots = client.get("acceptedOwnerSnapshots", [])
    require(len(snapshots) >= 3 and all(row.get("actualAcceptedS2C") is True for row in snapshots), "Missing accepted owner S2C snapshots")
    require({-1, 1, 2, final_stage} <= {row.get("firststepsStage") for row in snapshots}, "Research owner stage transitions missing")
    require(snapshots[-1].get("firststepsStage") == final_stage and snapshots[-1].get("gotDream") is True and snapshots[-1].get("gotBook") is True,
            "Final client acquisition/research owner snapshot mismatch")
    client_events = journal(root, root / "client" / "client-events.jsonl")
    require(client_events[:len(actions)] == actions, "Client terminal actions differ from native event journal")
    client_state = journal(root, root / "client" / "client-state.json")[-1]
    identity(client_state, server_marker, "client")
    screenshots = client.get("screenshots", [])
    require(len(screenshots) == len(png_names) and {Path(path).name for path in screenshots} == set(png_names), "Native screenshot names/count differ")
    screenshot_evidence = []
    for path in sorted(map(Path, screenshots)):
        require(unlinked(path).parent == root / "client" / "screenshots", "Screenshot escaped the private client")
        screenshot_evidence.append(png(root, path))
    require(len({row["sha256"] for row in screenshot_evidence}) == len(png_names), "Native GUI screenshot frames coincide")
    player_file = root / "server" / "tcp-bootstrap-audit" / "playerdata" / (owner + ".dat")
    player_data = private_file(root, player_file, 8 * 1024 * 1024)
    require(len(player_data) == server.get("diskPlayerBytes") and len(player_data) > 64, "Saved native player evidence missing")
    require(isinstance(server.get("savedKnowledgeNbt"), str) and "FIRSTSTEPS" in server["savedKnowledgeNbt"]
            and "!gotdream" in server["savedKnowledgeNbt"] and "!gotthaumonomicon" in server["savedKnowledgeNbt"],
            "Saved acquisition/research evidence missing")
    full_evidence = {}
    if full:
        for key, expected in (("finalFirststepsComplete", True), ("thaumometerCraftEvents", 1), ("thaumometerUseEvents", 7),
                              ("scanResearchEvents", 7), ("completedScanCount", 7), ("observationPaymentEvents", 1),
                              ("craftVisPaid", 20), ("auraFixtures", False), ("reconnectProven", True),
                              ("ownerEpoch", 2), ("actualOldConnectionClosed", True)):
            require(server.get(key) == expected, f"Full server proof missing: {key}")
        require(server["craftVisBefore"] - server["craftVisAfter"] == 20
                and abs(server["craftAuraFloatBefore"] - server["craftAuraFloatAfter"] - 20) < .0001,
                "Native thaumometer did not pay exactly 20 natural vis")
        raw_before, raw_after = server["observationPaymentBefore"], server["finalBasicsRaw"]
        require(raw_before >= 16 and server["scansCompleteBasicsRaw"] == raw_before and raw_after == raw_before - 16,
                "Native FIRSTSTEPS did not debit exactly 16 earned raw observations")
        require(requests[2].get("rawObservationsPaid") == 16 and requests[2].get("basicsRawBefore") == raw_before
                and requests[2].get("basicsRawAfter") == raw_after, "Contextual handler payment receipt differs")
        require(server_state.get("thaumometerCount") == 1 and server_state.get("firststepsComplete") is True
                and server_state.get("basicsRaw") == raw_after and server_state.get("ownerEpoch") == 2,
                "Reconnected terminal owner does not have the fully paid research/items")
        receipts, scans = server.get("nativeScanReceipts", []), client.get("nativeScans", [])
        require(len(receipts) == len(scans) == 7, "Exactly seven source scan receipts/S2C acknowledgements required")
        require(len(named("native-thaumometer-block-use")) == 7 and len(named("accepted-native-scan-owner-snapshot")) == 7,
                "Exactly seven native uses and accepted snapshots required")
        previous = None
        for index, (receipt, scan, use_action) in enumerate(zip(receipts, scans, named("native-thaumometer-block-use"))):
            use = use_action["detail"]
            require(receipt.get("scanIndex") == scan.get("index") == use.get("index") == index
                    and receipt.get("block") == use.get("block") == SCAN_BLOCKS[index]
                    and receipt.get("position") == [4, 65, index]
                    and receipt.get("researchKey") == scan.get("scanKey") == use.get("scanKey"),
                    "Native client/server scan target or generic marker differs")
            before, after = receipt["basicsRawBefore"], receipt["basicsRawAfter"]
            require(receipt.get("actualTCP") is True and scan.get("actualAcceptedS2C") is True
                    and use.get("nativeC2S") is True and use.get("clientTargetPayload") is False
                    and use.get("readOnlyClientRayMatches") is True and use.get("normalMovementSettleTicks", 0) >= 8,
                    "Scan did not use the native TCP ray/use/S2C path")
            require(scan.get("basicsRawBefore") == use.get("basicsRawBefore") == before
                    and scan.get("basicsRawAfter") == after and scan.get("rawGain") == after - before and after > before,
                    "Source knowledge award and client accepted observation differ")
            require(previous is None or before == previous, "Knowledge changed outside the seven scan awards")
            previous = after
        require(previous == raw_before and len({r["researchKey"] for r in receipts}) == 7,
                "Scan identities repeat or final earned raw count differs")
        for action_name in ("native-thaumometer-workbench-open", "native-thaumometer-result-take",
                            "native-thaumometer-paid-output-received", "native-full-firststeps-book-reopen",
                            "page-FIRSTSTEPS-complete", "accepted-native-full-firststeps-completion", "native-reconnect-ready",
                            "native-tcp-disconnect", "native-tcp-disconnect-facades-cleared", "native-tcp-reconnect-dispatched",
                            "actual-tcp-reconnect-owner-restored"):
            require(len(named(action_name)) == 1, f"Missing unique full native action: {action_name}")
        require(client.get("thaumometerTaken") is True and client.get("acceptedScans") == 7
                and client.get("basicsRawBeforeCompletion") == raw_before and client.get("basicsRawAfterCompletion") == raw_after
                and client.get("reconnectProven") is True and client.get("connectionEpoch") == 2,
                "Client full paid/reconnected owner proof differs")
        restored = named("actual-tcp-reconnect-owner-restored")[0]["detail"]
        for key in ("newConnectionIdentity", "newLocalPlayerIdentity", "strictComplete", "sevenGenericMarkers",
                    "fullInventoryComponentsMatch", "knowledgeCanonicalMatch", "warpMatch", "selectedSlotAndExactXpMatch",
                    "onlyPopupExcluded", "actualAcceptedS2C"):
            require(restored.get(key) is True, f"Native reconnect proof missing: {key}")
        cleared = named("native-tcp-disconnect-facades-cleared")[0]["detail"]
        require(cleared.get("disconnectedTitleTicks", 0) >= 20
                and all(cleared.get(key) is True for key in ("researchOwnerCleared", "researchCatalogCleared",
                                                            "aspectSnapshotCleared", "jeiRecipeSnapshotCleared")),
                "Connection-owned facades were not cleared before native reconnect")
        baseline = server.get("persistenceBaseline", {})
        require(baseline.get("ownerUuid") == owner and baseline.get("firststepsStage") == 4
                and baseline.get("basicsRaw") == raw_after and baseline.get("bookshelfAir") is True
                and baseline.get("bookshelfBlock") == "minecraft:air" and baseline.get("workbenchBlock") == "thaumcraft:arcane_workbench",
                "Stable source persistence baseline missing")
        for key in ("knowledgeNbt", "warpNbt", "inventoryNbt", "workbenchNbt"):
            require(isinstance(baseline.get(key), str) and baseline[key], f"Full persistence baseline missing: {key}")
        require(baseline["knowledgeNbt"] == server["savedKnowledgeNbt"] and baseline["warpNbt"] == server["savedWarpNbt"],
                "Disk owner attachments changed after stable reconnect baseline")
        require(client.get("inventoryNbt") == baseline["inventoryNbt"] and client.get("warpNbt") == baseline["warpNbt"]
                and all(client.get(key) == baseline.get(key) for key in ("selectedSlot", "totalExperience", "experienceLevel", "experienceProgress")),
                "Reconnected client inventory/warp/XP differ from server baseline")
        full_evidence = {"finalFirststepsComplete": True, "completedScanCount": 7, "nativeScanReceipts": receipts,
                         "craftVisPaid": 20, "rawObservationsPaid": 16, "basicsRawBefore": raw_before, "basicsRawAfter": raw_after,
                         "reconnectProven": True, "ownerEpoch": 2, "persistenceBaseline": baseline}
    return {
        **{key: server_marker[key] for key in COMMON + OPTIONAL_IDENTITY if key in server_marker}, "passed": True, "ownerUuid": owner,
        "scope": "Real dedicated loopback TCP, supplied materials and controlled wake, native bootstrap to FIRSTSTEPS stage " + str(final_stage)
                 + (", native paid thaumometer/scans and second TCP connection" if full else ""),
        "limits": ["No resource-gathering/survival playthrough", "No ordinary sleep/walking"]
                  + (["New-JVM reload requires its separate cohort"] if full else ["No thaumometer or full FIRSTSTEPS", "No reload/reconnect"]),
        "checks": len(checks), "serverEvents": len(server_events), "clientActions": len(actions),
        "actualContextualRequests": requests, "nativeRecipeCraftCount": 2, "finalFirststepsStage": final_stage,
        **full_evidence,
        "acceptedOwnerSnapshots": snapshots, "screenshots": screenshot_evidence,
        "options": {"copiedSourceSha256": preparation["optionsSha256"], "runtimeSha256": sha256(current_options)},
        "savedPlayer": {"path": str(player_file), "bytes": len(player_data), "sha256": sha256(player_data)},
        "processes": processes, "logs": launch_evidence,
        "reports": [{"path": str(root / role / f"{role}-report.json"), "sha256": sha256((root / role / f"{role}-report.json").read_bytes())}
                    for role in ("server", "client")],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        report = verify(args.root)
        if args.output:
            output = unlinked(args.output)
            require(output.parent == unlinked(args.root), "Verification output must be directly within the private cohort")
            with output.open("x", encoding="utf-8", newline="\n") as stream:
                json.dump(report, stream, ensure_ascii=False, indent=2)
                stream.write("\n")
        print(json.dumps({"passed": True, "runId": report["runId"], "finalFirststepsStage": report["finalFirststepsStage"],
                          "nativeRecipeCraftCount": 2, "actualContextualRequestCount": len(report["actualContextualRequests"]),
                          "screenshots": len(report["screenshots"]), "reconnectProven": report.get("reconnectProven", False)}))
        return 0
    except (OSError, ValueError, KeyError, TypeError) as failure:
        print(f"TCP bootstrap verification failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
