"""Seal the reviewed default-off TCP caster block and its independent native generate/reload evidence."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
REPO = Path(__file__).resolve().parent.parent
PARSER_PINS = {
    "tools/audit-tcp-casters.py": "4d10b0ea0f65524e271b828edfc85baf3fe59d5962909e4efd354d8c271f1457",
    "tools/audit-caster-textures.py": "0307e901f1e98aa8fa293416ac765f9e5dcb6f0a830f8c208c0acb2a6923d1b1",
    "tools/audit-tcp-alchemy.py": "bfa3d1310b81d079e59159a63ae552e92d92d872cae147c526935ee2b2a1b07c",
    "tools/audit-tcp-bootstrap-reload.py": "29b8dfcbcfb0f99f70f96ce319ed0574bfc06fbfe3e0429fd83a380d97bc5695",
}
for name, expected in PARSER_PINS.items():
    path = REPO / name
    if not path.is_file() or path.stat().st_size > 4 * 1024 * 1024 or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise ValueError("Reviewed independent caster parser changed before import: " + name)


def import_checked(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


gameplay = import_checked("tc_casters_artifact_native", REPO / "tools/audit-tcp-casters.py")
texture = import_checked("tc_casters_artifact_reference", REPO / "tools/audit-caster-textures.py")
require, unlinked, bounded, sha = texture.require, texture.unlinked, texture.bounded, texture.sha
evidence, document = texture.evidence, texture.document

BASELINE = REPO / gameplay.BASELINE_JAR
BASELINE_SHA = gameplay.BASELINE_SHA
ACCEPTED_TEXTURE_REPORT = REPO / "reference/validation/phase12-caster-textures-final-current.json"
ACCEPTED_TEXTURE_REPORT_SHA = "cac4a317325631d3915ef68752433098fea4c49bba62329da553bbfbbed84a23"
REVIEWED_SNAPSHOT = REPO / "port/build/tcp-casters-class-review-01/reviewed-snapshot08.json"
REVIEWED_SNAPSHOT_SHA = "e041d01f421d57715d858673a970c9635b1b317201c846c69a31f00df1d79d5d"
require(sha(bounded(REVIEWED_SNAPSHOT)) == REVIEWED_SNAPSHOT_SHA, "Root-reviewed native caster source/compiled snapshot changed")
REVIEW = document(REVIEWED_SNAPSHOT)
require(set(REVIEW["classPins"]) == set(REVIEW["added"]) | set(REVIEW["changedExisting"])
        and len(REVIEW["added"]) == 14 and len(REVIEW["changedExisting"]) == 12 and len(REVIEW["sourcePins"]) == 17,
        "Pinned root native caster class/source review is incomplete")
PRIVATE_ENTRIES = set(REVIEW["added"]) | {"thaumcraft/client/lib/ClientAlchemyResearchDriver.class"}
REVIEWED_PRIVATE_CLASSES = {name: value for name, value in REVIEW["classPins"].items() if name in PRIVATE_ENTRIES}
REVIEWED_PRODUCTION_CLASSES = {name: value for name, value in REVIEW["classPins"].items() if name not in PRIVATE_ENTRIES}
REVIEWED_SOURCES = REVIEW["sourcePins"]
REQUIRED_NEW_CLASSES = 14
REQUIRED_CHANGED_CLASSES = 12
REQUIRED_GAME_TESTS = 332
SEMANTIC_PAIRS = ("ItemCaster$1", "ContainerArcaneWorkbench$Selection", "ServerResearchRequestHandler$Attempts", "ServerResearchRequestHandler$OwnerKey")


def verify_sources():
    require(REVIEWED_PRIVATE_CLASSES and REVIEWED_PRODUCTION_CLASSES and REVIEWED_SOURCES,
            "Current TCP caster source and compiled-byte pins have not been explicitly reviewed")
    require(sha(bounded(BASELINE, 64 * 1024 * 1024)) == BASELINE_SHA, "Immutable accepted caster texture/storage JAR changed")
    require(sha(bounded(ACCEPTED_TEXTURE_REPORT)) == ACCEPTED_TEXTURE_REPORT_SHA, "Accepted caster texture/storage final report changed")
    accepted = document(ACCEPTED_TEXTURE_REPORT)
    require(accepted.get("passed") is True and accepted.get("status") == "PASS"
            and accepted["preservedJar"] == evidence(BASELINE, 64 * 1024 * 1024), "Accepted caster texture/storage report is not bound to the exact immutable baseline")
    records = []
    for name, expected in {**PARSER_PINS, **REVIEWED_SOURCES}.items():
        path = unlinked(REPO / name)
        require(path.is_relative_to(REPO) and sha(bounded(path)) == expected, "Reviewed TCP caster source changed: " + name)
        records.append(evidence(path))
    return records


def verify_jar(jar, generate):
    normal = unlinked(REPO / "port/build/libs/thaumcraft-6.1.0-port.jar")
    require(jar == normal, "Final native caster JAR must be the current normal compiled/processed artifact")
    require(sha(bounded(jar, 64 * 1024 * 1024)) == REVIEW["jarSha256"] and jar.stat().st_size == REVIEW["jarBytes"],
            "Final native caster JAR differs from root's exact completed byte review")
    preserved = generate / "accepted-casters.jar"
    require(bounded(preserved, 64 * 1024 * 1024) == bounded(jar, 64 * 1024 * 1024),
            "Immutable private native caster JAR copy differs from the current actual build")
    classes_pins = {**REVIEWED_PRIVATE_CLASSES, **REVIEWED_PRODUCTION_CLASSES}
    require(not set(REVIEWED_PRIVATE_CLASSES) & set(REVIEWED_PRODUCTION_CLASSES), "Reviewed native private/production class sets overlap")
    changed, added, compiled_records, resource_records = [], [], [], []
    with zipfile.ZipFile(BASELINE) as previous, zipfile.ZipFile(jar) as current:
        before, after = texture.zip_names(previous), texture.zip_names(current)
        require(before <= after, "Current native caster artifact removed accepted baseline entries")
        for entry, expected in classes_pins.items():
            require(entry in after and entry.endswith(".class") and sha(current.read(entry)) == expected,
                    "Reviewed current TCP caster bytecode changed: " + entry)
        for entry in sorted(before | after):
            old, new = previous.read(entry) if entry in before else None, current.read(entry) if entry in after else None
            if old == new:
                continue
            require(entry in classes_pins, "Protected accepted caster texture/storage class/resource changed: " + entry)
            row = {"entry": entry, "beforeSha256": sha(old) if old is not None else None, "afterSha256": sha(new)}
            (added if old is None else changed).append(row)
        require(len(added) == REQUIRED_NEW_CLASSES and len(changed) == REQUIRED_CHANGED_CLASSES
                and {row["entry"] for row in added} == set(REVIEW["added"])
                and {row["entry"] for row in changed} == set(REVIEW["changedExisting"])
                and set(after - before) == {row["entry"] for row in added}, "Exact reviewed current native caster added/changed entry set differs")
        protected_resources = {entry for entry in before if not entry.endswith(".class")}
        require({entry for entry in after if not entry.endswith(".class")} == protected_resources
                and all(previous.read(entry) == current.read(entry) for entry in protected_resources),
                "Any accepted original PNG/model/research/recipe/resource/metadata byte changed")
        jei = {entry for entry in before if entry.startswith("thaumcraft/client/jei/") and entry.endswith(".class")}
        require(len(jei) == 41 and {entry for entry in after if entry.startswith("thaumcraft/client/jei/") and entry.endswith(".class")} == jei
                and all(previous.read(entry) == current.read(entry) for entry in jei), "Any of the41accepted JEI class bytes changed")
        for relative, suffix, records in (("classes/java/main", ".class", compiled_records), ("resources/main", None, resource_records)):
            directory = unlinked(REPO / "port/build" / relative)
            require(directory.is_dir(), "Actual current compiled/processed native caster directory is missing")
            for path in sorted(directory.rglob("*")):
                path = unlinked(path)
                if not path.is_file() or suffix and path.suffix != suffix:
                    continue
                entry = path.relative_to(directory).as_posix()
                data = bounded(path, 64 * 1024 * 1024)
                require(entry in after and data == current.read(entry), "Final TCP caster JAR differs from actual compiled/processed output: " + entry)
                records.append({"entry": entry, "bytes": len(data), "sha256": sha(data)})
        require({row["entry"] for row in compiled_records} == {entry for entry in after if entry.endswith(".class")}
                and len(compiled_records) == 1753 and len(resource_records) == 2863,
                "Final current native caster compiled/resource completeness differs")
    semantic = []
    for name in SEMANTIC_PAIRS:
        directory = REPO / "port/build/tcp-casters-class-review-01"
        before, after = directory / f"baseline-{name}.txt", directory / f"current-{name}.txt"
        require(bounded(before) == bounded(after) and sha(bounded(before)) == REVIEW["semanticPairs"][name],
                "Reviewed nested class executable/signature javap output differs: " + name)
        semantic.append({"class": name, "baselineJavap": evidence(before), "currentJavap": evidence(after),
                         "executableAndSignaturesIdentical": True, "format": "javap -c -p -s"})
    return {"finalJar": evidence(jar, 64 * 1024 * 1024), "preservedJar": evidence(preserved, 64 * 1024 * 1024),
            "acceptedCasterTexturesJar": evidence(BASELINE, 64 * 1024 * 1024), "acceptedCasterTexturesReport": evidence(ACCEPTED_TEXTURE_REPORT),
            "changedEntries": changed, "addedEntries": added, "identicalJeiClasses": 41,
            "identicalAcceptedResources": len(protected_resources), "matchingCompiledClasses": len(compiled_records),
            "matchingProcessedResources": len(resource_records), "compiledEntries": compiled_records, "resourceEntries": resource_records,
            "privateAuditEntries": [row for row in [*added, *changed] if row["entry"] in REVIEWED_PRIVATE_CLASSES],
            "productionObserverAndColorEntries": [row for row in changed if row["entry"] in REVIEWED_PRODUCTION_CLASSES],
            "reviewedPrivateClasses": REVIEWED_PRIVATE_CLASSES, "reviewedProductionClasses": REVIEWED_PRODUCTION_CLASSES,
            "nestedClassSemanticReviews": semantic}


def verify_regression(build_log):
    require(build_log == unlinked(REPO / REVIEW["buildLog"]) and sha(bounded(build_log, 32 * 1024 * 1024)) == REVIEW["buildLogSha256"],
            "Complete native caster regression build log differs from root's exact successful review")
    result = texture.verify_units(REPO, build_log)
    require(result["unitTotals"] == {"tests": 1134, "failures": 0, "errors": 0, "skipped": 2}
            and result["unitPassed"] == 1132 and len(result["unitSuites"]) == 199
            and result["gameTests"]["requiredPassed"] == REQUIRED_GAME_TESTS,
            "Exact existing complete native caster unit/GameTest regression totals differ")
    return result


def verify_cohort(root, phase):
    require(root.parent == unlinked(REPO / "port/build/tcp-casters") and re.fullmatch(r"[A-Za-z0-9_-]{1,80}", root.name),
            "Final native caster phase escaped the exact private root")
    actual = gameplay.verify(root)  # Reads real stopped native files; no engine or report mutation.
    sealed = document(root / "verification.json")
    require(actual["passed"] is True and actual["auditPhase"] == phase and sealed.get("passed") is True
            and sealed.get("auditPhase") == phase and {key: value for key, value in actual.items() if key != "observedAt"}
                == {key: value for key, value in sealed.items() if key != "observedAt"},
            "Sealed native caster independent verification no longer matches actual exact stopped evidence")
    require(actual["verifier"]["sha256"] == PARSER_PINS["tools/audit-tcp-casters.py"], "Native caster cohort used a different independent verifier")
    records = [evidence(root / "verification.json"), evidence(root / "runtime.json"), evidence(root / "preparation.json"),
               actual["worldCopy"]["manifest"], *actual["reports"], *actual["screenshots"], actual["savedPlayer"],
               *actual["logs"], *[{key: row[key] for key in ("path", "bytes", "sha256")} for row in actual["nativeJournals"]]]
    for record in records:
        require(evidence(unlinked(Path(record["path"])), 64 * 1024 * 1024) == {key: record[key] for key in ("path", "bytes", "sha256")},
                "Sealed final actual native caster file bytes changed")
    return actual, {"phase": phase, "runId": actual["runId"], "independentVerification": evidence(root / "verification.json"),
                    "sealedFiles": records, "processes": actual["processes"], "sourceProcesses": actual["sourceProcesses"],
                    "mechanics": actual["mechanics"], "savedPlayer": actual["savedPlayer"],
                    "nativeWorldTiles": actual["nativeWorldTiles"], "nativePersistedTargets": actual["nativePersistedTargets"],
                    "nativeScreenshots": actual["screenshots"]}


def verify(args):
    generate, reload, jar, build_log = (unlinked(path) for path in (args.generate, args.reload, args.jar, args.build_log))
    require(generate != reload, "Native caster final phases must use distinct fresh private cohorts")
    sources = verify_sources()
    result = verify_jar(jar, generate)
    result.update(verify_regression(build_log))
    generation, first = verify_cohort(generate, "generate")
    restored, second = verify_cohort(reload, "reload")
    require(restored["sourceRunId"] == generation["runId"] and restored["sourceToken"] == generation["token"]
            and restored["sourceServerReportSha256"] == next(row["sha256"] for row in generation["reports"] if Path(row["path"]).name == "server-report.json")
            and generation["sourceRunId"] == gameplay.SOURCE_GENERATE,
            "Final native caster graph is not accepted alchemy reload → genuine paid caster/reconnect → distinct new-JVM readonly reload")
    for name, expected in PARSER_PINS.items():
        require(sha(bounded(REPO / name)) == expected, "Reviewed native parsers changed while sealing actual caster evidence")
    return {"schema": 1, "stage": 12, "passed": True, "status": "PASS", "observedAt": datetime.now(timezone.utc).isoformat(),
            "scope": "Native dedicated TCP auromancy gates, paid GUI Touch/Fire focus, first casts and crafted pouch/goggles, reconnect and exact new-JVM stopped-save reload",
            "limits": ["Explicit tangible materials, physical arena and two default10HP cow fixtures", "No full resource-gathering playthrough",
                       "Source focus selection/removal C2S; no physical F-key claim", "Basic Touch/Fire only; no other mediums or equipment tiers",
                       "Target stable typed UUID/health/AI/position verified; ordinary ticking counters are not claimed immutable"],
            **result, "reviewedSources": sources, "nativePhases": [first, second], "independentNativeGameplayAccepted": True,
            "sourceAlchemyWorldUnchanged": True, "nativeNormalProcesses": 4, "verifier": evidence(Path(__file__)),
            "nativeGameplayVerifier": evidence(REPO / "tools/audit-tcp-casters.py"), "reviewedCompiledSourceSnapshot": evidence(REVIEWED_SNAPSHOT)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", type=Path, required=True)
    parser.add_argument("--reload", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--build-log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = verify(args)
        output = unlinked(args.output)
        require(output == REPO / "reference/validation/phase12-tcp-casters-final-current.json", "Final native caster artifact must use the reviewed new validation path")
        with output.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2); stream.write("\n")
        print(json.dumps({"passed": True, "nativePhases": [row["runId"] for row in result["nativePhases"]],
                          "jarSha256": result["preservedJar"]["sha256"], "unitPassed": result["unitPassed"], "gameTests": REQUIRED_GAME_TESTS}))
        return 0
    except (OSError, EOFError, ValueError, KeyError, TypeError, IndexError, OverflowError, ET.ParseError, zipfile.BadZipFile) as failure:
        print("Native TCP caster artifact verification failed: " + str(failure), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
