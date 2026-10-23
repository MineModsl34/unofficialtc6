"""Seal one original BASICS icon alias, two private waits, and genuine FIRSTSTEPS TCP.

This checker starts from the separately accepted caster artifact. It never reruns
that artifact's current-JAR comparison after this resource addition, overwrites
old evidence, creates a preserved JAR, launches a game, or manufactures HUD gains.
Root must first review the exact private class delta, preserve the new JAR, and
visually inspect its actual native final-scan frame. All gameplay stays unchanged.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import sys
import zipfile

sys.dont_write_bytecode = True
REPO = Path(__file__).resolve().parent.parent
CASTER_REPORT = REPO / "reference/validation/phase12-tcp-casters-final-current.json"
# Root's independently sealed caster acceptance, preserved before this resource check.
CASTER_REPORT_SHA256 = "7b75d8c60c4192c3ed8ce43e9a0fe5a8277888730abb26080bf6ed6937016dbb"
CASTER_BASELINE = REPO / "port/build/tcp-casters/casters-20261010-chain-07/accepted-casters.jar"
CASTER_BASELINE_SHA256 = "929a5e31509c5f95c6a6832c4ec819d826ac87fd97472e7979ec653feafaf7c0"
CASTER_BASELINE_BYTES = 17558222
CASTER_SNAPSHOT = REPO / "port/build/tcp-casters-class-review-01/reviewed-snapshot08.json"
CASTER_SNAPSHOT_SHA256 = "e041d01f421d57715d858673a970c9635b1b317201c846c69a31f00df1d79d5d"
ORIGINAL = REPO.parent / "mods 1.12.2/Thaumcraft-1.12.2-6.1.BETA26.jar"
ORIGINAL_SHA256 = "9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f"
ALIAS = "assets/thaumcraft/textures/items/thaumonomicon_cheat.png"
MODERN = "assets/thaumcraft/textures/item/thaumonomicon_cheat.png"
PNG_BYTES = 2314
PNG_SHA256 = "eecd7212229f38ed2382af8299c862e89b24b9ccd55edeb94a5cc7cfba69e048"
ICON = "thaumcraft:textures/items/thaumonomicon_cheat.png"
FINAL_SCAN = "05-native-thaumometer-final-scan.png"
FINAL_REPORT = REPO / "reference/validation/phase12-thaumometer-knowledge-assets-final-current.json"
PRIVATE_DIRECTORY = REPO / "port/build/thaumometer-assets-private-wait-review-01"
PRIVATE_REVIEW = PRIVATE_DIRECTORY / "reviewed-private-waits.json"
# Root's exact review of the completed central build, with raw class copies.
PRIVATE_REVIEW_SHA256 = "7355a960fd849b7be8fb78b62bec6f84773de0431ac629f3fcf2839f04b5adb2"
PRIVATE_CLASS = "thaumcraft/client/lib/ClientTcpBootstrapAudit$Run.class"
PRIVATE_CLASS_BEFORE_SHA256 = "118d620ea276e78d96c51b012e7ac7636460c477ce48392f0209df683e27df0b"
PRIVATE_CLASS_BEFORE_BYTES = 55193
PRIVATE_CLASS_AFTER_SHA256 = "9f31b4c2fa93e10a76ece3446aafa4371cd668502e6bc8c0f3cad3517071d8ee"
PRIVATE_CLASS_AFTER_BYTES = 55354
PRIVATE_SOURCE = REPO / "port/src/main/java/thaumcraft/client/lib/ClientTcpBootstrapAudit.java"
PRIVATE_SOURCE_BEFORE = PRIVATE_DIRECTORY / "ClientTcpBootstrapAudit.before.java"
PRIVATE_SOURCE_BEFORE_SHA256 = "8ff92f18fc0ac9329fcba8f7768012e4cefc3022d20da6b75bbdbd1bb541fb71"
PRIVATE_SOURCE_AFTER_SHA256 = "533f3c381dbeca670bfced151a6623554b4adeb145c04666b7f1da602945399b"
PRIVATE_INSERTIONS = (
    b'            if (sealed && "completed".equals(nextPhase)) return;\n',
    b'            if (count(stack -> stack.is(ItemsTC.salisMundus)) < 2) return;\n',
)
SOURCE_PINS = {
    "tools/audit-tcp-bootstrap.py": "a9c78cd4deba2c600e01d707a1af9123a3fa61fb1f980b90fe38beb77162da60",
    "tools/audit-tcp-bootstrap-reload.py": "29b8dfcbcfb0f99f70f96ce319ed0574bfc06fbfe3e0429fd83a380d97bc5695",
    "tools/run-tcp-bootstrap.ps1": "fcb97042ea6b1b821d7b06b87c6842efc89bc1a2ecdf21e058abbc36d80362b8",
    "tools/prepare-tcp-bootstrap.ps1": "2646a4414cf9740deb8bb65b4474d353341126fa93b020771790ba31fad8469c",
}
RAW_SEQUENCE = (0, 3, 5, 8, 11, 13, 17, 21)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def unlinked(path):
    path = Path(os.path.abspath(path))
    for part in (path, *path.parents):
        try:
            attributes = part.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(attributes.st_mode)
                and not getattr(attributes, "st_file_attributes", 0) & 0x400, "Linked/reparse evidence path: " + str(part))
    return path


def bounded(path, maximum=4 * 1024 * 1024):
    path = unlinked(path)
    require(path.is_file() and path.stat().st_size <= maximum, "Missing/unbounded evidence: " + str(path))
    data = path.read_bytes()
    require(len(data) <= maximum, "Evidence grew beyond its bound: " + str(path))
    return data


def evidence(path, maximum=4 * 1024 * 1024):
    path = unlinked(path)
    data = bounded(path, maximum)
    return {"path": str(path), "bytes": len(data), "sha256": sha(data)}


def document(path):
    value = json.loads(bounded(path).decode("utf-8-sig"))
    require(isinstance(value, dict), "Expected evidence JSON object: " + str(path))
    return value


def utc(value):
    require(isinstance(value, str), "Missing actual UTC observation")
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(result.utcoffset() is not None and result.utcoffset().total_seconds() == 0, "Observation time must be UTC")
    return result


def checked_import(name, relative):
    path = REPO / relative
    require(sha(bounded(path)) == SOURCE_PINS[relative], "Unchanged independent native helper changed: " + relative)
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def verify_parent():
    require(isinstance(CASTER_REPORT_SHA256, str) and re.fullmatch(r"[a-f0-9]{64}", CASTER_REPORT_SHA256),
            "Caster acceptance report SHA256 is not pinned; root must seal the caster block first")
    require(sha(bounded(CASTER_REPORT, 16 * 1024 * 1024)) == CASTER_REPORT_SHA256,
            "Immutable accepted caster final report changed")
    parent = json.loads(bounded(CASTER_REPORT, 16 * 1024 * 1024).decode("utf-8-sig"))
    require(isinstance(parent, dict) and parent.get("passed") is True and parent.get("status") == "PASS"
            and parent.get("independentNativeGameplayAccepted") is True and parent.get("nativeNormalProcesses") == 4
            and parent.get("matchingCompiledClasses") == 1753 and parent.get("matchingProcessedResources") == 2863,
            "Parent is not the fully accepted separate native caster block")
    baseline = evidence(CASTER_BASELINE, 64 * 1024 * 1024)
    require(baseline["bytes"] == CASTER_BASELINE_BYTES and baseline["sha256"] == CASTER_BASELINE_SHA256
            and parent.get("preservedJar") == baseline, "Immutable accepted GEN07 caster JAR differs")
    require(sha(bounded(CASTER_SNAPSHOT)) == CASTER_SNAPSHOT_SHA256
            and parent.get("reviewedCompiledSourceSnapshot") == evidence(CASTER_SNAPSHOT), "Caster source/byte review changed")
    snapshot = document(CASTER_SNAPSHOT)
    require(snapshot.get("jarSha256") == CASTER_BASELINE_SHA256 and snapshot.get("jarBytes") == CASTER_BASELINE_BYTES
            and isinstance(snapshot.get("sourcePins"), dict) and len(snapshot["sourcePins"]) == 17,
            "Exact reviewed caster source pins are incomplete")
    source_records = []
    for relative, expected in {**snapshot["sourcePins"], **SOURCE_PINS}.items():
        path = unlinked(REPO / relative)
        require(path.is_relative_to(REPO) and re.fullmatch(r"[a-f0-9]{64}", expected)
                and sha(bounded(path)) == expected, "Protected accepted source/helper changed: " + relative)
        source_records.append(evidence(path))
    phases = parent.get("nativePhases")
    require(isinstance(phases, list) and len(phases) == 2 and [row.get("phase") for row in phases] == ["generate", "reload"]
            and phases[0].get("runId") == CASTER_BASELINE.parent.name, "Parent native generate/reload acceptance is incomplete")
    protected = []
    for phase in phases:
        require(isinstance(phase.get("sealedFiles"), list) and phase["sealedFiles"], "Parent native phase lacks immutable files")
        root = unlinked(REPO / "port/build/tcp-casters" / phase["runId"])
        require(root.parent == unlinked(REPO / "port/build/tcp-casters"), "Parent phase escaped private caster base")
        for row in phase["sealedFiles"]:
            path = unlinked(Path(row["path"]))
            require(path.is_relative_to(root) and evidence(path, 64 * 1024 * 1024)
                    == {key: row[key] for key in ("path", "bytes", "sha256")}, "Protected accepted caster native file changed")
            protected.append({key: row[key] for key in ("path", "bytes", "sha256")})
    return parent, {"acceptanceReport": evidence(CASTER_REPORT, 16 * 1024 * 1024), "preservedJar": baseline,
                    "reviewedSnapshot": evidence(CASTER_SNAPSHOT), "unchangedReviewedSources": source_records,
                    "unchangedNativeFiles": protected}


def zip_names(archive):
    entries = archive.infolist()
    require(len(entries) <= 16384 and len(entries) == len({row.filename for row in entries}), "Duplicate/unbounded ZIP entries")
    require(all(row.file_size <= 64 * 1024 * 1024 and not row.flag_bits & 1 for row in entries)
            and sum(row.file_size for row in entries) <= 256 * 1024 * 1024, "Unbounded/encrypted ZIP contents")
    names = {row.filename for row in entries if not row.is_dir()}
    require(all(not name.startswith("/") and ".." not in name.split("/") and "\\" not in name for name in names),
            "Noncanonical ZIP entry path")
    return names


def reverse_private_waits(current):
    for insertion in PRIVATE_INSERTIONS:
        require(current.count(insertion) == 1, "Exact reviewed private synchronization wait is missing/duplicated")
        current = current.replace(insertion, b"", 1)
    return current


def verify_private_review():
    require(isinstance(PRIVATE_REVIEW_SHA256, str) and re.fullmatch(r"[a-f0-9]{64}", PRIVATE_REVIEW_SHA256)
            and isinstance(PRIVATE_CLASS_AFTER_SHA256, str) and re.fullmatch(r"[a-f0-9]{64}", PRIVATE_CLASS_AFTER_SHA256)
            and isinstance(PRIVATE_CLASS_AFTER_BYTES, int) and PRIVATE_CLASS_AFTER_BYTES > 0,
            "Private synchronization wait compiled review is not pinned; root must review its actual build first")
    require(sha(bounded(PRIVATE_REVIEW)) == PRIVATE_REVIEW_SHA256, "Root's exact private-wait compiled review changed")
    review = document(PRIVATE_REVIEW)
    before, current = bounded(PRIVATE_SOURCE_BEFORE), bounded(PRIVATE_SOURCE)
    require(len(before) == 57734 and sha(before) == PRIVATE_SOURCE_BEFORE_SHA256
            and len(current) == 57874 and sha(current) == PRIVATE_SOURCE_AFTER_SHA256
            and reverse_private_waits(current) == before,
            "Reverse of only the two reviewed waits does not restore the whole exact accepted controller source")
    require(review.get("schemaVersion") == 1 and review.get("purpose") == "private-bootstrap-sync-waits"
            and review.get("baselineJar") == evidence(CASTER_BASELINE, 64 * 1024 * 1024)
            and review.get("baselineSource") == evidence(PRIVATE_SOURCE_BEFORE)
            and review.get("currentSource") == evidence(PRIVATE_SOURCE), "Private source review identity differs")
    expected = {"entry": PRIVATE_CLASS, "beforeSha256": PRIVATE_CLASS_BEFORE_SHA256,
                "afterSha256": PRIVATE_CLASS_AFTER_SHA256, "beforeBytes": PRIVATE_CLASS_BEFORE_BYTES,
                "afterBytes": PRIVATE_CLASS_AFTER_BYTES}
    require(review.get("changedClass") == expected and review.get("allOtherBaselineEntriesUnchanged") is True,
            "Private byte review is not exactly the one authorized default-off controller class")
    return {"review": evidence(PRIVATE_REVIEW), "beforeSource": evidence(PRIVATE_SOURCE_BEFORE),
            "currentSource": evidence(PRIVATE_SOURCE), "changedClass": expected,
            "wholeSourceReverseVerified": True, "exactWaits": [line.decode("utf-8").strip() for line in PRIVATE_INSERTIONS],
            "reviewedJar": review["newJar"]}


def compare_archives(previous, current, original):
    before, after = zip_names(previous), zip_names(current)
    require(len(before) == 4617 and ALIAS not in before and after == before | {ALIAS},
            "Artifact must retain exactly all4617 prior entries and add only the original icon alias")
    require(len([name for name in before if name.endswith(".class")]) == 1753, "Accepted caster class set is incomplete")
    source = original.read(ALIAS)
    require(len(source) == PNG_BYTES and sha(source) == PNG_SHA256
            and current.read(ALIAS) == source and previous.read(MODERN) == source and current.read(MODERN) == source,
            "Alias differs from exact whole original BETA26 PNG or its accepted modern copy")
    unchanged, changed = [], []
    for name in sorted(before):
        prior, actual = previous.read(name), current.read(name)
        if name == PRIVATE_CLASS:
            require(len(prior) == PRIVATE_CLASS_BEFORE_BYTES and sha(prior) == PRIVATE_CLASS_BEFORE_SHA256
                    and isinstance(PRIVATE_CLASS_AFTER_SHA256, str) and len(actual) == PRIVATE_CLASS_AFTER_BYTES
                    and sha(actual) == PRIVATE_CLASS_AFTER_SHA256 and prior != actual,
                    "Actual authorized private controller byte delta differs from root's exact pins")
            changed.append({"entry": name, "beforeBytes": len(prior), "beforeSha256": sha(prior),
                            "bytes": len(actual), "sha256": sha(actual)})
        else:
            require(prior == actual, "Protected accepted gameplay/JEI/resource/metadata entry changed: " + name)
            unchanged.append({"entry": name, "bytes": len(actual), "sha256": sha(actual)})
    require(len(unchanged) == 4616 and len(changed) == 1, "Exact reviewed one-class private wait delta is incomplete")
    return before, after, unchanged, changed


def verify_jar(jar, root, private):
    require(jar == unlinked(REPO / "port/build/libs/thaumcraft-6.1.0-port.jar"), "Use the current normal built artifact")
    bounded(jar, 64 * 1024 * 1024)
    require(evidence(jar, 64 * 1024 * 1024) == private["reviewedJar"], "Normal JAR differs from root's exact completed private class/resource review")
    preserved = root / "preserved-thaumometer-assets.jar"
    require(bounded(preserved, 64 * 1024 * 1024) == bounded(jar, 64 * 1024 * 1024),
            "Root must first CreateNew-preserve the exact final icon artifact in this fresh native cohort")
    require(sha(bounded(ORIGINAL, 64 * 1024 * 1024)) == ORIGINAL_SHA256, "Original BETA26 source JAR changed")
    require(bounded(REPO / "port/src/main/resources" / ALIAS) == bounded(REPO / "port/src/main/resources" / MODERN)
            and sha(bounded(REPO / "port/src/main/resources" / ALIAS)) == PNG_SHA256,
            "Source icon alias differs from the unchanged original modern source PNG")
    with zipfile.ZipFile(CASTER_BASELINE) as previous, zipfile.ZipFile(jar) as current, zipfile.ZipFile(ORIGINAL) as original:
        before, after, unchanged, changed = compare_archives(previous, current, original)
        outputs = []
        for relative, suffix, expected_count in (("classes/java/main", ".class", 1753), ("resources/main", None, 2864)):
            directory = unlinked(REPO / "port/build" / relative)
            require(directory.is_dir(), "Current actual native compiled/resource directory missing")
            rows = []
            for path in sorted(directory.rglob("*")):
                path = unlinked(path)
                require(path.is_relative_to(directory), "Native output escaped its protected directory")
                if not path.is_file() or suffix is not None and path.suffix != suffix:
                    continue
                entry = path.relative_to(directory).as_posix()
                data = bounded(path, 64 * 1024 * 1024)
                require(entry in after and current.read(entry) == data, "Native runtime output differs from final JAR: " + entry)
                rows.append({"entry": entry, **evidence(path, 64 * 1024 * 1024)})
            require(len(rows) == expected_count, "Compiled/processed runtime output completeness differs: " + relative)
            if suffix:
                require({row["entry"] for row in rows} == {name for name in after if name.endswith(".class")},
                        "Native compiled class set differs from exact immutable JAR")
            else:
                require({row["entry"] for row in rows} == {name for name in after if not name.endswith(".class")} - {"META-INF/MANIFEST.MF"},
                        "Native processed resource set differs from final artifact")
            outputs.append(rows)
    return {"finalJar": evidence(jar, 64 * 1024 * 1024), "preservedJar": evidence(preserved, 64 * 1024 * 1024),
            "originalJar": evidence(ORIGINAL, 64 * 1024 * 1024), "alias": {"entry": ALIAS, "bytes": PNG_BYTES, "sha256": PNG_SHA256},
            "identicalAcceptedEntries": len(unchanged), "identicalAcceptedClasses": 1752,
            "identicalAcceptedNonClassEntries": len(before) - 1753, "addedEntries": [ALIAS], "changedEntries": changed,
            "unchangedEntries": unchanged, "compiledEntries": outputs[0], "resourceEntries": outputs[1],
            "matchingCompiledClasses": 1753, "matchingProcessedResources": 2864}


def one(rows, name):
    matches = [row for row in rows if row.get("action") == name]
    require(len(matches) == 1, "Missing unique genuine native action: " + name)
    return matches[0]


def verify_native(root, parent, native, nbt):
    result = native.verify(root)
    sealed = document(root / "verification.json")
    require(result == sealed and result.get("passed") is True and result.get("goal") == "firststeps"
            and result.get("auditPhase") == "generate" and result.get("finalFirststepsStage") == 4,
            "Actual unchanged full-FIRSTSTEPS verifier differs from the stopped independent verification")
    runtime, server, client = (document(root / path) for path in ("runtime.json", "server/server-report.json", "client/client-report.json"))
    require(utc(runtime["startedAtUtc"]) > utc(parent["observedAt"]), "Native icon cohort must start after separate caster acceptance")
    options_evidence = []
    for relative in ("preparation-inputs/options.txt", "client/options.txt"):
        path = root / relative
        lines = bounded(path, 1024 * 1024).decode("utf-8-sig").splitlines()
        for key in ("resourcePacks", "incompatibleResourcePacks"):
            values = [line.split(":", 1)[1] for line in lines if line.startswith(key + ":")]
            require(len(values) == 1 and json.loads(values[0]) == [], "Native client has active resource-pack overrides: " + relative)
        options_evidence.append(evidence(path, 1024 * 1024))
    scans = server["nativeScanReceipts"]
    require(len(scans) == 7 and all(row["basicsRawBefore"] == RAW_SEQUENCE[index]
            and row["basicsRawAfter"] == RAW_SEQUENCE[index + 1] for index, row in enumerate(scans))
            and server.get("scansCompleteBasicsRaw") == 21 and server.get("observationPaymentBefore") == 21
            and server.get("finalBasicsRaw") == 5, "Native original seven-scan BASICS 13→17→21 and16raw payment differs")
    require(server.get("noKnowledgeResearchFixtures") is True and server.get("auraFixtures") is False
            and server.get("craftVisPaid") == 20 and server.get("reconnectProven") is True and client.get("connectionEpoch") == 2,
            "Real native craft/payment/TCP scope differs")
    actions = native.journal(root, root / "client/client-events.jsonl")
    # The unchanged controller writes its report before waiting for the matching
    # server terminal, then appends exactly this last honest shutdown receipt.
    require(len(actions) == len(client["actions"]) + 1 and actions[:-1] == client["actions"]
            and actions[-1].get("action") == "matching-server-terminal" and actions[-1].get("phase") == "finish"
            and actions[-1].get("detail") == {}, "Native client report/journal and matching-terminal boundary differ")
    for row in actions:
        native.identity(row, server, "client")
    take, received = one(actions, "native-thaumometer-result-take"), one(actions, "native-thaumometer-paid-output-received")
    require(take["detail"].get("requiredVis") == 20 and take["detail"].get("nativeC2S") is True
            and received["detail"].get("allFifteenInputsEmpty") is True
            and received["detail"].get("actualNativeMenuSynchronization") is True
            and received["detail"]["availableVisBefore"] - received["detail"]["availableVisAfter"] == 20,
            "Actual thaumometer C2S/result/full-menu S2C payment differs")
    clicks = [row for row in actions if row.get("action") == "vanilla-menu-click" and row.get("phase") == "thaumometer"]
    for slot in range(10, 16):
        require(len([row for row in clicks if row["detail"].get("menuId") == take["detail"]["menuId"]
                    and row["detail"].get("slot") == slot and row["detail"].get("button") == 1
                    and row["detail"].get("clickType") == "PICKUP" and row["detail"].get("nativeC2S") is True]) == 1,
                "Missing genuine native insertion for one of six primal crystal slots")
    player_path = Path(result["savedPlayer"]["path"])
    state = nbt.disk_state(nbt.read_nbt(bounded(player_path, 8 * 1024 * 1024)))
    nbt.compare_snapshot(server["persistenceBaseline"], state)
    nbt.compare_snapshot({**client, "ownerUuid": client["ownerUuid"], "firststepsStage": 4}, state, True)
    require(nbt.snbt(server["savedKnowledgeNbt"]) == state["knowledge"] and nbt.snbt(server["savedWarpNbt"]) == state["warp"],
            "Actual typed final saved player attachments differ from native server snapshot")
    baseline = server["persistenceBaseline"]
    knowledge = nbt.canonical_knowledge(state["knowledge"])
    require({row["key"]: row["amount"] for row in knowledge["knowledge"]}.get("O_BASICS") == 5
            and baseline["basicsRaw"] == 5, "Actual saved typed BASICS observations differ")
    logs, launch = [], []
    missing = ("Failed to load texture: " + ICON, "FileNotFoundException: " + ICON)
    for role in ("server", "client"):
        for relative in (f"{role}.stdout.log", f"{role}.stderr.log", f"{role}/logs/latest.log"):
            path = root / relative
            text = bounded(path, 32 * 1024 * 1024).decode("utf-8", "replace")
            require(all(signature not in text for signature in missing), "Exact BASICS icon missing-resource error remains: " + relative)
            logs.append(evidence(path, 32 * 1024 * 1024))
        process = next(row for row in runtime["processes"] if row["role"] == role)
        folders = "-Dfml.modFolders=thaumcraft%%" + str(REPO / "port/build/classes/java/main") + ";thaumcraft%%" + str(REPO / "port/build/resources/main")
        require(process["arguments"].count(folders) == 1, "Native process did not use the exact verified current class/resource directories")
        name = "tcpBootstrapAudit" + role.capitalize()
        path = root / "launch" / role / (name + "RunVmArgs.txt")
        vm = bounded(path).decode("utf-8-sig")
        require("-Dthaumcraft.tcpBootstrapAudit=true" in vm and "-Dthaumcraft.tcpBootstrapReloadAudit=false" in vm
                and not re.search(r"-Dthaumcraft\.(?:tcpCasterAudit|tcpAlchemyAudit|jeiReview|casterVisualReview|scanningReview)=true", vm),
                "Native process enabled an unrelated private/synthetic scenario")
        for suffix in ("RunVmArgs.txt", "RunProgramArgs.txt", "RunClasspath.txt", "LegacyClasspath.txt", "Log4j2.xml"):
            launch.append(evidence(root / "launch" / role / (name + suffix)))
    screenshot = root / "client/screenshots" / FINAL_SCAN
    frame = [row for row in actions if row.get("action") == "native-screenshot" and row.get("detail", {}).get("path") == str(screenshot)]
    crossing = [row for row in actions if row.get("action") == "accepted-native-scan-owner-snapshot" and row["detail"].get("index") == 5]
    require(len(frame) == len(crossing) == 1 and 0 <= frame[0]["clientTick"] - crossing[0]["clientTick"] < 40,
            "Actual final-scan screenshot callback is outside the minimum native BASICS feedback lifetime")
    files = [evidence(root / "verification.json"), evidence(root / "runtime.json"), evidence(root / "preparation.json"),
             evidence(root / "server/server-report.json"), evidence(root / "client/client-report.json"),
             evidence(root / "client/client-events.jsonl", 32 * 1024 * 1024), evidence(root / "server/server-events.jsonl", 32 * 1024 * 1024),
             evidence(player_path, 8 * 1024 * 1024), *options_evidence, *logs, *launch]
    return {"runId": result["runId"], "token": result["token"], "port": result["port"], "rawSequence": list(RAW_SEQUENCE),
            "crossingAcceptedS2C": crossing[0], "nativeFrameCallback": frame[0], "nativeScreenshots": result["screenshots"],
            "nativeScanReceipts": scans, "craftVisPaid": 20, "sixNativePrimalInputSlots": list(range(10, 16)),
            "nativeCraftTake": take, "nativePaidFullMenuS2C": received, "rawObservationsPaid": 16,
            "reconnectProven": True, "connectionEpoch": 2, "savedTypedPlayer": result["savedPlayer"],
            "savedTypedPlayerSnapshotMatches": True, "persistenceBaseline": baseline, "processes": runtime["processes"],
            "nativeNormalProcesses": 2, "exactMissingTextureSignatureAbsent": True,
            "noClientResourcePackOverrides": True, "clientOptions": options_evidence, "sealedFiles": files}


def verify_root_review(path, root, native):
    path = unlinked(path)
    require(path == root / "hud-review.json", "Root's actual frame-review receipt must be new private hud-review.json")
    review = document(path)
    frame = evidence(root / "client/screenshots" / FINAL_SCAN, 16 * 1024 * 1024)
    require(review.get("schemaVersion") == 1 and review.get("purpose") == "actual-root-native-hud-review"
            and review.get("runId") == native["runId"] and review.get("token") == native["token"]
            and review.get("screenshot") == frame and review.get("reviewer") == "root"
            and isinstance(review.get("notes"), str) and 1 <= len(review["notes"].strip()) <= 4096,
            "Root agent visual review is not bound to the actual exact native final-scan frame")
    for key in ("observedBasicsIcon", "missingPlaceholderAbsent", "thaumometerAndScanEffectsVisible"):
        require(review.get(key) is True, "Actual frame review did not confirm: " + key)
    require(utc(review["observedAtUtc"]) >= utc(native["nativeFrameCallback"]["observedUtc"]), "Root visual review predates actual native screenshot")
    return {"receipt": evidence(path), "actualFrame": frame, "observations": review,
            "method": "Root agent's actual visual frame review, byte-bound to the Screenshot.grab callback; no human-review or automatic pixel-classification claim"}


def verify(args):
    root, jar = (unlinked(path) for path in (args.root, args.jar))
    require(root.parent == unlinked(REPO / "port/build/tcp-bootstrap") and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", root.name),
            "Use one fresh full-FIRSTSTEPS private generation cohort")
    parent, accepted = verify_parent()
    private = verify_private_review()
    artifact = verify_jar(jar, root, private)
    native = checked_import("tc_thaumometer_assets_bootstrap", "tools/audit-tcp-bootstrap.py")
    nbt = checked_import("tc_thaumometer_assets_nbt", "tools/audit-tcp-bootstrap-reload.py")
    run = verify_native(root, parent, native, nbt)
    view = verify_root_review(args.hud_review, root, run)
    for relative, expected in SOURCE_PINS.items():
        require(sha(bounded(REPO / relative)) == expected, "Pinned unchanged native helper changed while sealing")
    require(sha(bounded(CASTER_REPORT, 16 * 1024 * 1024)) == CASTER_REPORT_SHA256
            and sha(bounded(CASTER_BASELINE, 64 * 1024 * 1024)) == CASTER_BASELINE_SHA256,
            "Immutable accepted caster baseline changed while sealing this separate resource check")
    return {"schema": 1, "stage": 12, "passed": True, "status": "PASS", "observedAt": datetime.now(timezone.utc).isoformat(),
            "scope": "Original BASICS knowledge HUD icon alias and two reviewed private-controller synchronization waits; paid native Thaumometer/seven genuine scans/TCP reconnect",
            "limits": ["Explicit existing bootstrap material/arena fixtures and controlled wake; no survival acquisition claim",
                       "Actual root-reviewed native PNG; no automatic pixel classification or separate gain-packet receipt claim",
                       "Other block-model missing-resource warnings are outside this single confirmed HUD alias"],
            "acceptedCasterBlock": accepted, **artifact, "reviewedPrivateSynchronizationWaits": private,
            "nativeBootstrap": run, "nativeHudReview": view,
            "unchangedGameplayAndJei": True, "onlyOriginalIconAliasAdded": True, "originalCategoryDescriptorUnchanged": True,
            "nativeGameplayVerifier": evidence(REPO / "tools/audit-tcp-bootstrap.py"),
            "typedNbtVerifier": evidence(REPO / "tools/audit-tcp-bootstrap-reload.py"), "verifier": evidence(Path(__file__))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--hud-review", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        output = unlinked(args.output)
        require(output == unlinked(FINAL_REPORT) and not output.exists(), "Final report must use its new dedicated CreateNew validation path")
        result = verify(args)
        with output.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print(json.dumps({"passed": True, "nativeRunId": result["nativeBootstrap"]["runId"],
                          "jarSha256": result["preservedJar"]["sha256"], "identicalClasses": 1752, "reviewedPrivateClassChanges": 1,
                          "addedAsset": ALIAS, "nativeRawSequence": list(RAW_SEQUENCE), "reconnectProven": True}))
        return 0
    except (OSError, EOFError, ValueError, KeyError, TypeError, IndexError, OverflowError, zipfile.BadZipFile) as failure:
        print("Thaumometer knowledge icon verification failed: " + str(failure), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
