"""Read-only final JAR/unit/TCP acceptance and preservation of the accepted JEI baseline."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

BASELINE_SHA256 = "130b26565e79cc483129a94669dc21a6814e05f315129600f9c295e9261c7888"
CHANGES = {
    "thaumcraft/common/lib/research/catalog/validation/ResearchProgressNetworkAudit$Run.class",
    "thaumcraft/common/lib/research/catalog/validation/ResearchStageFourNetworkAudit$Run.class",
    "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler.class",
}
DEBUG_ONLY = {
    "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler$Attempts.class",
    "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler$OwnerKey.class",
}
# The three hooks and two unchanged executable nested classes were reviewed and
# accepted by bootstrap04. Extending opt-in scenario classes cannot broaden them.
REVIEWED_HOOK_SHA256 = {
    "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler.class":
        "28135f652740deb821db76bc0681b5bb3217e9415c47e8f6bce04ec0bbaf9038",
    "thaumcraft/common/lib/research/catalog/validation/ResearchProgressNetworkAudit$Run.class":
        "af61217f541311d911447ff35b6e817c272596210c88d8d525da9aec26e6c4cc",
    "thaumcraft/common/lib/research/catalog/validation/ResearchStageFourNetworkAudit$Run.class":
        "021a5258a5dc7e2125bdb9cc4b886b2bc19d920b3fbb26d76fb80566cde23600",
    "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler$Attempts.class":
        "ea8ba9f14f19addd24d6935f0a1b5403a7595a7517159f5dd3233528e496649a",
    "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler$OwnerKey.class":
        "8435ab20958c664c132868bddfc0831a8c74882876268af11ef552771e84971e",
}
ADDITIONS = re.compile(
    r"(?:thaumcraft/common/lib/validation/TcpBootstrap(?:Audit(?:IO)?|ReloadAudit)"
    r"|thaumcraft/client/lib/ClientTcpBootstrap(?:Audit|ReloadAudit))(?:\$[^/]+)?\.class"
)
IDENTITY = ("marker", "schemaVersion", "purpose", "networkProtocolVersion", "runId", "token", "port")
SOURCE_IDENTITY = ("sourceRunId", "sourceToken", "sourcePort", "sourceServerReportSha256",
                   "sourceRuntimeSha256", "worldManifestSha256")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def evidence(path):
    data = path.read_bytes()
    return {"path": str(path.resolve()), "bytes": len(data), "sha256": sha(data)}


def document(path):
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    require(isinstance(value, dict), f"Expected JSON object: {path}")
    return value


def native_processes(runtime):
    require(runtime.get("status") == "completed" and runtime.get("buildExitCode") == 0
            and runtime.get("serverReady") is True, "Actual native runner did not complete")
    processes = runtime.get("processes", [])
    require(len(processes) == 2 and {p.get("role") for p in processes} == {"server", "client"}
            and len({p.get("pid") for p in processes}) == 2
            and all(isinstance(p.get("pid"), int) and p["pid"] > 0
                    and p.get("exitCode") == 0 and p.get("forcedStop") is False for p in processes),
            "Actual native server/client engines did not exit0 independently")
    return processes


def preserved_evidence(cohort, verification, screenshots):
    reports, logs, images = (verification.get(key, []) for key in ("reports", "logs", "screenshots"))
    require(len(reports) == 2 and len(logs) == 4 and len(images) == screenshots,
            "Missing sealed native reports/logs/screenshots")
    require({Path(row["path"]).resolve() for row in reports}
            == {(cohort / role / f"{role}-report.json").resolve() for role in ("server", "client")},
            "Sealed acceptance must reference both exact role reports")
    require({Path(row["path"]).resolve() for row in logs}
            == {(cohort / f"{role}.{stream}.log").resolve() for role in ("server", "client")
                for stream in ("stdout", "stderr")}, "Sealed acceptance must reference all four exact native logs")
    for row in reports + logs + images:
        path = Path(row["path"])
        require(path.resolve().is_relative_to(cohort.resolve()) and sha(path.read_bytes()) == row["sha256"],
                f"Preserved TCP evidence changed: {path}")
        if "bytes" in row:
            require(path.stat().st_size == row["bytes"], f"Preserved TCP evidence size changed: {path}")
    if "savedPlayer" in verification:
        row = verification["savedPlayer"]
        path = Path(row["path"])
        expected = cohort / "server/tcp-bootstrap-audit/playerdata" / (verification["ownerUuid"] + ".dat")
        require(path.resolve() == expected.resolve() and path.stat().st_size == row["bytes"]
                and sha(path.read_bytes()) == row["sha256"], "Sealed actual native player save changed")


def complete_firststeps(server, client, owner):
    for flag in ("finalFirststepsComplete", "reconnectProven", "actualOldConnectionClosed"):
        require(server.get(flag) is True, f"Missing actual full native server proof: {flag}")
    require(server.get("finalFirststepsStage") == 4 and server.get("ownerEpoch") == 2
            and server.get("actualContextualRequestCount") == 3 and server.get("nativeFirstStepsResearchEvents") == 3,
            "Full FIRSTSTEPS must complete on the second actual TCP owner after three contextual requests")
    require(client.get("firststepsStage") == 4 and client.get("reconnectProven") is True
            and client.get("connectionEpoch") == 2 and client.get("thaumometerTaken") is True
            and client.get("thaumometerOrFullFirststepsTested") is True,
            "Client did not accept crafted thaumometer and full research on its new TCP connection")
    require(client.get("clientInventoryStackWrites") is False and client.get("clientKnowledgeGrants") is False,
            "Client acceptance may not insert material/output stacks or grant knowledge")
    require(server.get("auraFixtures") is False and server.get("thaumometerCraftEvents") == 1
            and server.get("craftVisPaid") == 20 and server.get("craftVisBefore", -1) - server.get("craftVisAfter", -1) == 20
            and abs(server.get("craftAuraFloatBefore", -1) - server.get("craftAuraFloatAfter", -1) - 20) < 0.0001,
            "Actual thaumometer did not pay exactly20 naturally generated local aura")
    require(server.get("thaumometerUseEvents") == 7 and server.get("scanResearchEvents") == 7
            and server.get("completedScanCount") == 7, "Seven actual targeted native scan events are missing")
    receipts = server.get("nativeScanReceipts", [])
    blocks = ("stone", "dirt", "grass_block", "sand", "gravel", "oak_log", "poppy")
    keys = ("stone", "dirt", "grass", "sand", "gravel", "log", "red_flower")
    require(len(receipts) == 7 and len(client.get("nativeScans", [])) == 7, "Seven sealed server/client scan receipts required")
    before = 0
    for index, row in enumerate(receipts):
        after = row.get("basicsRawAfter")
        require(row.get("scanIndex") == index and row.get("block") == "minecraft:" + blocks[index]
                and row.get("researchKey") == "!minecraft:" + keys[index] + "0" and row.get("position") == [4, 65, index]
                and row.get("actualTCP") is True and row.get("actualItemUseCount") == index + 1
                and row.get("basicsRawBefore") == before and isinstance(after, int) and after >= before
                and row.get("basicsRawAwarded") == after - before, "Actual natural scan target/award sequence differs")
        before = after
    require(before >= 16 and server.get("scansCompleteBasicsRaw") == before
            and server.get("observationPaymentBefore") == before and server.get("observationPaymentEvents") == 1
            and server.get("finalBasicsRaw") == before - 16 and client.get("basicsRawBeforeCompletion") == before
            and client.get("basicsRawAfterCompletion") == before - 16,
            "Full research must spend exactly16 raw observations earned by the seven native scans")
    requests = server.get("actualContextualRequests", [])
    require(len(requests) == 3, "Three native contextual research receipts required")
    for row, expected, final in zip(requests, (-1, 1, 2), (1, 2, 4)):
        require(row.get("researchKey") == "FIRSTSTEPS" and row.get("expectedStage") == expected
                and row.get("resultingStage") == final and row.get("result") == "APPLIED"
                and row.get("actualTCP") is True and row.get("mainThread") is True and row.get("ownerUuid") == owner,
                "Native source research transition sequence differs")
    require(requests[-1].get("rawObservationsPaid") == 16 and requests[-1].get("basicsRawBefore") == before
            and requests[-1].get("basicsRawAfter") == before - 16, "Paid source completion receipt differs")
    baseline = server.get("persistenceBaseline", {})
    require(baseline.get("ownerUuid") == owner and baseline.get("firststepsStage") == 4
            and baseline.get("basicsRaw") == before - 16 and baseline.get("bookshelfAir") is True
            and baseline.get("bookshelfBlock") == "minecraft:air" and baseline.get("workbenchBlock") == "thaumcraft:arcane_workbench",
            "Missing exact saved completed owner/world baseline")
    for name in ("knowledgeNbt", "warpNbt", "inventoryNbt", "workbenchNbt"):
        require(isinstance(baseline.get(name), str) and baseline[name], f"Missing native persistence NBT: {name}")
    for name in ("selectedSlot", "totalExperience", "experienceLevel", "experienceProgress"):
        require(isinstance(baseline.get(name), (int, float)), f"Missing native persistence field: {name}")
    require(baseline["knowledgeNbt"] == server.get("savedKnowledgeNbt") and baseline["warpNbt"] == server.get("savedWarpNbt"),
            "Final saved knowledge/warp differs from completed reconnect baseline")
    return baseline


def restart_acceptance(reload_cohort, cohort, generation_runtime, generation_server, generation_verification):
    verification_path = reload_cohort / "verification.json"
    verification = document(verification_path)
    runtime = document(reload_cohort / "runtime.json")
    server = document(reload_cohort / "server/server-report.json")
    client = document(reload_cohort / "client/client-report.json")
    require(verification.get("passed") is True and verification.get("runId") == reload_cohort.name
            and verification.get("auditPhase") == "reload" and verification.get("goal") == "firststeps"
            and verification.get("networkProtocolVersion") == "6", "Expected sealed separate new-JVM FIRSTSTEPS restart cohort")
    expected = {"sourceRunId": cohort.name, "sourceToken": generation_verification["token"],
                "sourcePort": generation_verification["port"],
                "sourceServerReportSha256": sha((cohort / "server/server-report.json").read_bytes()),
                "sourceRuntimeSha256": sha((cohort / "runtime.json").read_bytes()),
                "worldManifestSha256": sha((reload_cohort / "preparation-inputs/world-copy-manifest.json").read_bytes())}
    for key, value in expected.items():
        require(verification.get(key) == value, f"New-JVM cohort source provenance differs: {key}")
    require(verification.get("token") != generation_verification.get("token"), "New-JVM cohort must have a fresh isolation token")
    for value in (runtime, server, client):
        require(all(value.get(key) == verification.get(key) for key in IDENTITY + SOURCE_IDENTITY),
                "Restart evidence identity/source provenance differs")
        require(value.get("auditPhase") == "reload" and value.get("goal") == "firststeps", "Wrong restart identity phase/goal")
    processes = native_processes(runtime)
    source_processes = native_processes(generation_runtime)
    require(verification.get("processes") == processes and verification.get("sourceProcesses") == source_processes,
            "Independent restart verification must seal both exact source/new-JVM process records")
    require(not {p["pid"] for p in processes} & {p["pid"] for p in source_processes},
            "Restart cohort did not launch distinct new native engine process identities")
    source_end = datetime.fromisoformat(generation_runtime["endedAtUtc"].replace("Z", "+00:00"))
    require(all(datetime.fromisoformat(p["startedAtUtc"].replace("Z", "+00:00")) > source_end for p in processes),
            "Restart engines must start after both generation engines completed")
    for value in (server, client):
        require(value.get("passed") is True and value.get("status") == "completed"
                and value.get("ownerUuid") == generation_server.get("ownerUuid")
                and value.get("actualTCP") is True and value.get("firststepsStage") == 4
                and value.get("researchRequests") == 0 and value.get("loadedWithoutGrants") is True,
                "Native restart must accept the saved actual owner without grants/requests")
    for flag in ("dedicatedServer", "noArenaOrMaterialsWritten", "noKnowledgeResearchFixtures",
                 "newJvmRestartProven", "saveFlushed", "diskNbtVerified"):
        require(server.get(flag) is True, f"Missing actual restart server proof: {flag}")
    require(server.get("persistenceBaseline") == generation_server.get("persistenceBaseline"),
            "Restart must compare the exact final saved generation owner baseline")
    require(client.get("clientInventoryStackWrites") is False and client.get("clientKnowledgeGrants") is False
            and client.get("acceptedCurrentConnectionOwner") is True and client.get("stableNativeTicks", 0) >= 40,
            "Restart client must accept stable native owner state from its new actual connection")
    connection = client.get("connection", {})
    require(connection.get("memory") is False and connection.get("integratedServer") is False
            and connection.get("remoteHost") == "127.0.0.1" and connection.get("remotePort") == verification["port"]
            and connection.get("ownerUuid") == generation_server.get("ownerUuid") and connection.get("connected") is True,
            "Restart client was not a separate live loopback TCP engine")
    for flag in ("newJvmRestartProven", "fullKnowledgeWarpInventoryXpVerified", "clientOnlyPopupExcluded"):
        require(verification.get(flag) is True, f"Missing independent complete restart verification: {flag}")
    world_copy = verification.get("worldCopy", {})
    require(world_copy.get("sourceUnchangedAfterReload") is True
            and world_copy.get("manifestSha256") == expected["worldManifestSha256"]
            and world_copy.get("fileCount", 0) > 0 and world_copy.get("bytes", 0) > 0,
            "Restart must use a verified separate copy and preserve the stopped generation world")
    preserved_evidence(reload_cohort, verification, 0)
    player_rows = verification.get("nativeSavedPlayers", [])
    owner = generation_server["ownerUuid"]
    expected_players = {(base / "server/tcp-bootstrap-audit/playerdata" / (owner + ".dat")).resolve()
                        for base in (cohort, reload_cohort)}
    require(len(player_rows) == 2 and {Path(row["path"]).resolve() for row in player_rows} == expected_players,
            "Restart must seal independently read generation/reload native player saves")
    for row in player_rows:
        path = Path(row["path"])
        require(path.stat().st_size == row["bytes"] and sha(path.read_bytes()) == row["sha256"],
                "Independently verified generation/reload native player save changed")
    return ({**evidence(verification_path), "runId": reload_cohort.name, "protocol": "6",
             "serverExitCode": 0, "clientExitCode": 0, "finalFirststepsStage": 4,
             "newJvmRestartProven": True, "fullKnowledgeWarpInventoryXpVerified": True,
             "sourceRunId": cohort.name, "worldCopy": world_copy, "processes": processes,
             "nativeSavedPlayers": player_rows},
            [evidence(reload_cohort / "runtime.json"), evidence(reload_cohort / "preparation-inputs/world-copy-manifest.json"),
             *[evidence(Path(row["path"])) for row in verification["reports"] + verification["logs"] + player_rows]])


def verify(args):
    repo = Path(__file__).resolve().parent.parent
    build = repo / "port/build"
    jar = args.jar or build / "libs/thaumcraft-6.1.0-port.jar"
    baseline = args.baseline or build / "jei-review/jei-20261009-review-06/accepted-jei.jar"
    cohort = args.tcp_root or build / "tcp-bootstrap/tcp-20261009-bootstrap-04"
    require(cohort.resolve().parent == (build / "tcp-bootstrap").resolve()
            and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", cohort.name), "Expected an immediate private TCP cohort")
    build_log = args.build_log or build / "tcp-bootstrap-final-build.log"
    require("BUILD SUCCESSFUL" in build_log.read_text(encoding="utf-8-sig", errors="replace"),
            "Final unit/JAR build did not complete successfully")
    require(sha(baseline.read_bytes()) == BASELINE_SHA256, "Accepted JEI baseline hash differs")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = []
    for path in sorted((build / "test-results/test").glob("TEST-*.xml")):
        root = ET.parse(path).getroot()
        require(root.tag == "testsuite", f"Unknown unit XML format: {path}")
        row = {"name": root.get("name"), **evidence(path)}
        for field in totals:
            row[field] = int(root.get(field, "0"))
            totals[field] += row[field]
        suites.append(row)
    require(totals == dict(tests=1134, failures=0, errors=0, skipped=2),
            f"Unexpected complete unit counts: {totals}")
    changed, added, debug_only = [], [], []
    javap = args.javap or repo.parent / "toolchain/jdk21/bin/javap.exe"
    with zipfile.ZipFile(jar) as current, zipfile.ZipFile(baseline) as accepted:
        current_names, previous_names = current.namelist(), accepted.namelist()
        require(len(current_names) == len(set(current_names)) and len(previous_names) == len(set(previous_names)),
                "JAR/baseline contains duplicate ZIP entries")
        checked_classes = checked_resources = 0
        for relative, suffix in (("classes/java/main", ".class"), ("resources/main", None)):
            directory = build / relative
            require(directory.is_dir(), f"Current compiled output is missing: {directory}")
            for path in sorted(directory.rglob("*")):
                if not path.is_file() or suffix and path.suffix != suffix:
                    continue
                name = path.relative_to(directory).as_posix()
                require(name in current.NameToInfo and current.read(name) == path.read_bytes(),
                        f"Final JAR differs from current compiled/resource file: {name}")
                if suffix:
                    checked_classes += 1
                else:
                    checked_resources += 1
        require(checked_classes > 1000 and checked_resources > 1000, "Incomplete class/resource comparison")
        previous_files = {item.filename for item in accepted.infolist() if not item.is_dir()}
        current_files = {item.filename for item in current.infolist() if not item.is_dir()}
        require(previous_files <= current_files, "Accepted JEI JAR entries were removed")
        for name, reviewed_sha in REVIEWED_HOOK_SHA256.items():
            require(name in current_files and sha(current.read(name)) == reviewed_sha,
                    f"Previously reviewed production/audit hook bytes changed: {name}")
        for name in sorted(previous_files):
            before, after = accepted.read(name), current.read(name)
            if before != after:
                require(name in CHANGES | DEBUG_ONLY, f"Protected accepted JEI entry changed: {name}")
                if name in DEBUG_ONLY:
                    class_name = name.removesuffix(".class").replace("/", ".")
                    def dump(path, options):
                        return subprocess.check_output([str(javap), *options, "-classpath", str(path), class_name])
                    old_code, new_code = dump(baseline, ["-p", "-c", "-s"]), dump(jar, ["-p", "-c", "-s"])
                    require(old_code == new_code, f"Reviewed debug-only class executable/signature dump changed: {name}")
                    old_debug = dump(baseline, ["-p", "-c", "-s", "-l"]).decode("utf-8")
                    new_debug = dump(jar, ["-p", "-c", "-s", "-l"]).decode("utf-8")
                    pattern = re.compile(r"(?m)^(\s*line )(\d+)(: \d+)\r?$")
                    old_lines = [int(match[1]) for match in pattern.findall(old_debug)]
                    new_lines = [int(match[1]) for match in pattern.findall(new_debug)]
                    require(old_lines and len(old_lines) == len(new_lines)
                            and all(new - old == 3 for old, new in zip(old_lines, new_lines))
                            and pattern.sub(r"\1#\3", old_debug) == pattern.sub(r"\1#\3", new_debug),
                            f"Reviewed nested class changed beyond uniform +3 LineNumberTable shift: {name}")
                    debug_only.append(dict(entry=name, baselineSha256=sha(before), finalSha256=sha(after),
                        executableAndSignaturesDumpSha256=sha(old_code), identicalJavapOptions=["-p", "-c", "-s"],
                        baselineDebugDumpSha256=sha(old_debug.encode()), finalDebugDumpSha256=sha(new_debug.encode()),
                        onlyLineNumberTableShift=3, shiftedLineEntries=len(old_lines)))
                changed.append(dict(entry=name, baselineSha256=sha(before), finalSha256=sha(after)))
        for name in sorted(current_files - previous_files):
            require(ADDITIONS.fullmatch(name) is not None, f"Unexpected added JAR entry: {name}")
            added.append(dict(entry=name, bytes=len(current.read(name)), sha256=sha(current.read(name))))
        new_dirs = set(current_names) - set(previous_names) - current_files
        require(new_dirs <= {"thaumcraft/common/lib/validation/"}, f"Unexpected added ZIP directories: {new_dirs}")
        require(len(added) >= 4, "New native TCP audit classes are missing")
        protected_jei = [name for name in previous_files if name.startswith("thaumcraft/client/jei/")]
        require(protected_jei and all(accepted.read(name) == current.read(name) for name in protected_jei),
                "Accepted JEI class family changed")
        if args.reload_run_id:
            require("thaumcraft/common/lib/validation/TcpBootstrapReloadAudit.class" in current_files
                    and "thaumcraft/client/lib/ClientTcpBootstrapReloadAudit.class" in current_files,
                    "Separate native restart audit class families are missing")

    verification_path = cohort / "verification.json"
    verification = document(verification_path)
    runtime = document(cohort / "runtime.json")
    server = document(cohort / "server/server-report.json")
    client = document(cohort / "client/client-report.json")
    full = server.get("finalFirststepsStage") == 4
    require(args.goal is None or args.goal == ("firststeps" if full else "bootstrap"), "Requested artifact goal differs from native cohort")
    require(verification.get("runId") == cohort.name and verification.get("passed") is True
            and verification.get("networkProtocolVersion") == "6", "Expected completed actual protocol6 native TCP verification")
    if full:
        require(verification.get("auditPhase") == "generate" and verification.get("goal") == "firststeps"
                and verification.get("finalFirststepsStage") == 4, "Full native research requires a sealed fresh generation cohort")
    else:
        require(cohort.name == "tcp-20261009-bootstrap-04", "Legacy stage2 artifact acceptance requires completed bootstrap04")
    processes = native_processes(runtime)
    require(verification.get("processes") == processes, "Generation verification must seal exact native process records")
    for value in (runtime, server, client):
        require(all(value.get(key) == verification.get(key) for key in IDENTITY), "Actual TCP evidence identity differs")
        if full:
            require(value.get("auditPhase") == "generate" and value.get("goal") == "firststeps", "Wrong full native generation phase/goal")
    for value in (server, client):
        require(value.get("passed") is True and value.get("status") == "completed"
                and value.get("ownerUuid") == verification.get("ownerUuid"), "Actual native acceptance report failed")
    for flag in ("actualTCP", "dedicatedServer", "noKnowledgeResearchFixtures", "controlledWake", "diskNbtVerified", "saveFlushed"):
        require(server.get(flag) is True, f"Missing actual native server proof: {flag}")
    require(server.get("nativeRecipeCraftCount") == 2 and client.get("actualTCP") is True,
            "Actual native bootstrap proof is incomplete")
    if full:
        complete_firststeps(server, client, verification["ownerUuid"])
    else:
        require(server.get("finalFirststepsStage") == 2 and server.get("actualContextualRequestCount") == 2
                and client.get("firststepsStage") == 2, "Legacy native stage2 proof is incomplete")
    preserved_evidence(cohort, verification, 7 if full else 3)
    report = {
        "schema": 1, "stage": 12, "passed": True, "status": "PASS",
        "observedAt": datetime.now(timezone.utc).isoformat(),
        "projectRoot": str(repo),
        "scope": "Accepted JEI artifact preserved; native dedicated TCP bootstrap through FIRSTSTEPS "
                 + ("stage4 completion, paid thaumometer, seven block scans and second TCP connection" if full else "stage2"),
        "limits": verification.get("limits"),
        "unitTotals": totals, "unitPassed": totals["tests"] - totals["skipped"], "suites": suites,
        "finalJar": {**evidence(jar), "duplicateEntries": 0,
                     "matchingCompiledClasses": checked_classes, "matchingProcessedResources": checked_resources},
        "acceptedJeiBaseline": {**evidence(baseline), "protectedDifferences": 0,
                                "protectedEntries": len(previous_files) - len(CHANGES | DEBUG_ONLY),
                                "identicalJeiClassFamilyEntries": len(protected_jei),
                                "allBaselineResourceEntriesUnchanged": True,
                                "allowedChangedClasses": sorted(CHANGES), "changedClasses": changed, "addedClasses": added,
                                "reviewedHookBytesUnchanged": True,
                                "reviewedHookSha256": REVIEWED_HOOK_SHA256,
                                "reviewedDebugOnlyClasses": sorted(DEBUG_ONLY), "debugOnlyChanges": debug_only,
                                "debugOnlyProofTool": evidence(javap)},
        "tcpAcceptance": {**evidence(verification_path), "runId": cohort.name, "protocol": "6",
                          "serverExitCode": 0, "clientExitCode": 0, "checks": verification["checks"],
                          "actualContextualRequests": server["actualContextualRequests"],
                          "diskNbtVerified": True, "finalFirststepsStage": 4 if full else 2,
                          "savedPlayer": verification["savedPlayer"],
                          **({"reconnectProven": True, "ownerEpoch": 2, "nativeScanCount": 7, "thaumometerVisPaid": 20,
                              "basicsRawBeforeCompletion": server["scansCompleteBasicsRaw"], "basicsRawAfterCompletion": server["finalBasicsRaw"],
                              "rawObservationsPaid": 16, "persistenceBaseline": server["persistenceBaseline"]} if full else {})},
        "evidence": [evidence(build_log), evidence(cohort / "runtime.json"),
                     evidence(Path(verification["savedPlayer"]["path"])),
                     *[evidence(Path(row["path"])) for row in verification["reports"] + verification["logs"]]],
    }
    if args.reload_run_id:
        require(full and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", args.reload_run_id) is not None
                and args.reload_run_id != cohort.name, "Separate restart requires a completed full research generation cohort and distinct valid runId")
        reload_cohort = build / "tcp-bootstrap" / args.reload_run_id
        require(reload_cohort.resolve().parent == (build / "tcp-bootstrap").resolve(), "Restart escaped the private TCP cohort base")
        reload_report, reload_evidence = restart_acceptance(reload_cohort, cohort, runtime, server, verification)
        report["tcpRestartAcceptance"] = reload_report
        report["evidence"].extend(reload_evidence)
        report["scope"] += "; saved full owner and world reloaded without grants in distinct new server/client JVMs"
        report["limits"] = [limit for limit in verification.get("limits", [])
                            if not any(term in limit.lower() for term in ("new jvm", "new-jvm", "restart", "reload"))]
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("jar", "baseline", "tcp-root", "build-log", "output", "javap"):
        parser.add_argument("--" + name, type=Path)
    parser.add_argument("--goal", choices=("bootstrap", "firststeps"), help="Optional explicit native acceptance goal; default detects sealed cohort")
    parser.add_argument("--reload-run-id", help="Separate completed restart cohort under port/build/tcp-bootstrap")
    args = parser.parse_args()
    try:
        report = verify(args)
        output = args.output or Path(__file__).resolve().parent.parent / "reference/validation/phase12-tcp-bootstrap-final-current.json"
        with output.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print(json.dumps({"passed": True, "output": str(output.resolve()), "jarSha256": report["finalJar"]["sha256"],
                          "unitTotals": report["unitTotals"], "changedClasses": len(report["acceptedJeiBaseline"]["changedClasses"]),
                          "addedClasses": len(report["acceptedJeiBaseline"]["addedClasses"]),
                          "finalFirststepsStage": report["tcpAcceptance"]["finalFirststepsStage"],
                          "newJvmRestartProven": "tcpRestartAcceptance" in report}))
        return 0
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, ET.ParseError, subprocess.CalledProcessError) as failure:
        print(f"TCP/bootstrap final artifact verification failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
