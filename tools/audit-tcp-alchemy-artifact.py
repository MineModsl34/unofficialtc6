"""Seal the final alchemy JAR, native cohort chain, and preserved FIRSTSTEPS/JEI bytes."""
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

BASELINE_SHA256 = "c8ab48de23354cd22c202d202a91c33df164291c7971495d4a96fca5326cf82a"
HOOK = "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler.class"
# Set only after reviewing javap's actual guarded post-context recorder addition.
REVIEWED_HOOK_SHA256 = "7605ebd04d002970a66aa30d83a34bd768ef8c42af5da9da08e892a69c1ced9e"
# A two-line guarded read-only observer added after native context/remainder validation,
# immediately before unchanged spendVis. Source and javap reviewed against baseline.
ARCANE_OBSERVER = "thaumcraft/common/container/ContainerArcaneWorkbench.class"
REVIEWED_ARCANE_OBSERVER_SHA256 = "b59ef8a055fed1287250835a3fcf2d2bf852e0f684fa835ec96bf98b88b0a4b4"
BASELINE_ARCANE_SOURCE_SHA256 = "fd7845d6eb980f9964b4452f2c0b4ee4c568e82500805488695572194e94b191"
ARCANE_OBSERVER_SOURCE = (
    b"            if (selected.arcane != null && Boolean.getBoolean(thaumcraft.common.lib.validation.TcpAlchemyAuditIO.ENABLE_PROPERTY))\n"
    b"                thaumcraft.common.lib.validation.TcpAlchemyAudit.observeArcanePaymentBefore((ServerPlayer)player, this, made, selected.cost);\n"
)
DEBUG_ONLY = {
    "thaumcraft/common/container/ContainerArcaneWorkbench$Selection.class": 2,
    "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler$Attempts.class": 3,
    "thaumcraft/common/lib/network/playerdata/ServerResearchRequestHandler$OwnerKey.class": 3,
    "thaumcraft/common/lib/research/LegacyResearchCraftingGameTests$Probe.class": 32,
    "thaumcraft/gametest/BiomeGameTests$1.class": 8,
    "thaumcraft/gametest/BiomeGameTests$2.class": 8,
    "thaumcraft/gametest/ResearchTableGameTests$CancelCraftingResearch.class": 5,
    "thaumcraft/gametest/ResearchTableGameTests$CraftProbe.class": 5,
}
# Explicit source-proven BlockItem identities and the obsolete nitor descriptor bridge.
# Exact compiled bytes are pinned after source and javap review; no wildcard permission
# to change the accepted progression, receipt recorder, catalog, resources or JEI code.
REVIEWED_COMPATIBILITY = {
    "thaumcraft/common/items/consumables/ItemPhial.class": "88e3d94c05a6318f636cf432c64c116522b1aa35e9dc221a259269ce955611be",
    "thaumcraft/common/lib/research/LegacyScanIdentity.class": "9e5bc4e8fa5c3d71af839998e3ad62d219512182813d080a99cd76b9d774eb34",
    "thaumcraft/common/lib/research/ResearchItemResolver.class": "bb9af24e6008a15d245dd0c48d0f36ba3ab499758b30056063f36f8a671b9719",
    "thaumcraft/common/lib/research/LegacyResearchCraftingGameTests.class": "5af7463274a25de1ebd42e641f826ade1fd4bd35b4f6fbbddaf6c5698c564615",
    "thaumcraft/gametest/ResearchItemResolverGameTests.class": "26be7c5300c32c3556e723f198fda0a3e5a9ad9a013b48e9ce6d0db3abb3fa83",
    "thaumcraft/gametest/BiomeGameTests.class": "122b441a0a17fc2da0be6434818e38edfe67ff86c663c1d789a066f67ed83af9",
    "thaumcraft/gametest/ResearchTableGameTests.class": "5476f9f90115482fb776f3d0106037b38de8432c819a1260b5eb684c8b257740",
    "thaumcraft/gametest/ScribingRecipeGameTests.class": "5245ead0d94f60ca9cb84db76bcf472fec7b44a7c3e4ff0d39dbce5143401fe1",
}
REVIEWED_ADDITIONS = {
    "thaumcraft/gametest/PhialCraftingGameTests.class": "399aa419f08fc4365ebff80637a37426950561bb4616d5fdcdc31e5d12d355c2",
}
REVIEWED_SOURCES = {
    "thaumcraft/common/container/ContainerArcaneWorkbench.java": "eaad77d56d7354b8f35a4cabb86216e1582ea0c77ce8a83eaa5539795d888f98",
    "thaumcraft/common/items/consumables/ItemPhial.java": "4be18e0a9caffe8539f5dbdad4a59e9ce312252c9304af606015e47fba781a00",
    "thaumcraft/gametest/PhialCraftingGameTests.java": "a9ae64390d140f7e01345fadcb1f877232c0d2b2599f655cffa47035fe727145",
    "thaumcraft/common/lib/research/LegacyScanIdentity.java": "96afbf03a210c8cee54ed693d4ba5f9d19f6091363faa6f6a5431adf0c79b653",
    "thaumcraft/common/lib/research/ResearchItemResolver.java": "910fce10e0b2db984fd3865bc647952476c64ef89ae40eb22bcacb5cee711943",
    "thaumcraft/common/lib/research/LegacyResearchCraftingGameTests.java": "d379ccb89f47293552bf5ac039c1d134168017e54c21a0be2f1d30e7561a01d6",
    "thaumcraft/gametest/ResearchItemResolverGameTests.java": "062d26420674184871b1f5f4cd251571faaec15bcfbf4736ccc7db061274a393",
    "thaumcraft/gametest/BiomeGameTests.java": "c18824a80338a6b532ee9ffe290e7ccfe599b86b124d0af595c1f55ce4810a53",
    "thaumcraft/gametest/ResearchTableGameTests.java": "d37d1f97f55103a1d52eb3314bd4e78cfe6cb4adf95c4d7d27b3fae25b050fdc",
    "thaumcraft/gametest/ScribingRecipeGameTests.java": "cb9c408ca9b3d3ec5afb35169e7a6aa3ba73b7c3c72ba0fdc56f0ccf0c85e9dc",
}
ADDITIONS = re.compile(
    r"(?:thaumcraft/common/lib/validation/TcpAlchemy(?:Audit(?:IO)?|Scenario)"
    r"|thaumcraft/client/lib/Client(?:TcpAlchemyAudit|AlchemyResearchDriver))(?:\$[^/]+)?\.class"
)


def require(condition, detail):
    if not condition:
        raise ValueError(detail)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def evidence(path):
    data = path.read_bytes()
    return {"path": str(path.resolve()), "bytes": len(data), "sha256": sha(data)}


def document(path):
    require(path.is_file() and path.stat().st_size <= 4 * 1024 * 1024, f"Missing/unbounded JSON: {path}")
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    require(isinstance(value, dict), f"Expected object: {path}")
    return value


def verify(args):
    repo = Path(__file__).resolve().parent.parent
    build = repo / "port/build"
    baseline = repo / "port/build/tcp-bootstrap/tcp-20261009-firststeps-01/accepted-firststeps.jar"
    jar = args.jar.resolve()
    require(sha(baseline.read_bytes()) == BASELINE_SHA256, "Accepted FIRSTSTEPS baseline changed")
    original_phial = repo / "reference/tc6/src/thaumcraft/common/items/consumables/ItemPhial.java"
    require(sha(original_phial.read_bytes()) == "4061a4f2768d3c5536209e66b3f58d65c07b44f891d8df5b95be121294890879",
            "Original ItemPhial.onCreated source proof changed")
    reviewed_sources = []
    for name, expected in REVIEWED_SOURCES.items():
        path = repo / "port/src/main/java" / name
        require(sha(path.read_bytes()) == expected, f"Reviewed compatibility source changed: {name}")
        reviewed_sources.append(evidence(path))
    arcane_source = (repo / "port/src/main/java/thaumcraft/common/container/ContainerArcaneWorkbench.java").read_bytes()
    require(arcane_source.count(ARCANE_OBSERVER_SOURCE) == 1
            and sha(arcane_source.replace(ARCANE_OBSERVER_SOURCE, b"", 1)) == BASELINE_ARCANE_SOURCE_SHA256,
            "Arcane production source changed beyond the exact reviewed default-off observer")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = sorted((build / "test-results/test").glob("TEST-*.xml"))
    require(len(suites) >= 199, "Missing complete final unit suite")
    for path in suites:
        root = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(root.attrib.get(key, 0))
    require(totals["tests"] >= 1134 and totals["failures"] == totals["errors"] == 0
            and totals["skipped"] == 2, "Final complete tests did not pass with the two reviewed opt-in skips")
    log = args.build_log.resolve()
    require(log.is_relative_to(build.resolve()) and log.is_file()
            and "BUILD SUCCESSFUL" in log.read_text(encoding="utf-8-sig"), "Missing successful final build log")
    game_test_log = args.game_test_log.resolve()
    require(game_test_log.is_relative_to(build.resolve()) and game_test_log.is_file()
            and game_test_log.stat().st_size <= 32 * 1024 * 1024, "Missing/unbounded isolated GameTest log")
    game_test_text = game_test_log.read_text(encoding="utf-8-sig")
    require("330 GAME TESTS COMPLETE" in game_test_text and "All 330 required tests passed" in game_test_text
            and "BUILD SUCCESSFUL" in game_test_text and "required tests failed" not in game_test_text,
            "Actual full native regression suite did not pass")
    javap = repo.parent / "toolchain/jdk21/bin/javap.exe"
    changed, added, debug = [], [], []
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(jar) as new:
        require(len(old.namelist()) == len(set(old.namelist())) and len(new.namelist()) == len(set(new.namelist())),
                "Duplicate ZIP entries in final/baseline JAR")
        before = {row.filename for row in old.infolist() if not row.is_dir()}
        after = {row.filename for row in new.infolist() if not row.is_dir()}
        require(before <= after, "Accepted FIRSTSTEPS entries removed")
        require(sha(new.read(HOOK)) == REVIEWED_HOOK_SHA256, "Actual contextual observer hook differs from reviewed bytes")
        require(sha(new.read(ARCANE_OBSERVER)) == REVIEWED_ARCANE_OBSERVER_SHA256,
                "Actual default-off before-payment observer differs from reviewed bytes")
        for name, expected in REVIEWED_COMPATIBILITY.items():
            require(name in before and sha(new.read(name)) == expected, f"Reviewed compatibility bytecode changed: {name}")
        classes = resources = 0
        for relative, suffix in (("classes/java/main", ".class"), ("resources/main", None)):
            directory = build / relative
            require(directory.is_dir(), "Missing current compiled/processed output")
            for path in sorted(directory.rglob("*")):
                if not path.is_file() or suffix and path.suffix != suffix:
                    continue
                name = path.relative_to(directory).as_posix()
                require(name in after and path.read_bytes() == new.read(name), f"Final JAR differs from current output: {name}")
                if suffix:
                    classes += 1
                else:
                    resources += 1
        require(classes >= 1722 and resources >= 2863, "Final classes/resources incomplete")
        for name in sorted(before):
            source, current = old.read(name), new.read(name)
            if source == current:
                continue
            require(name in (HOOK, ARCANE_OBSERVER) or name in DEBUG_ONLY or name in REVIEWED_COMPATIBILITY,
                    f"Protected accepted FIRSTSTEPS entry changed: {name}")
            row = dict(entry=name, baselineSha256=sha(source), finalSha256=sha(current))
            if name in DEBUG_ONLY:
                target = name.removesuffix(".class").replace("/", ".")
                def dump(path, options):
                    return subprocess.check_output([str(javap), *options, "-classpath", str(path), target])
                old_code, new_code = dump(baseline, ["-p", "-c", "-s"]), dump(jar, ["-p", "-c", "-s"])
                require(old_code == new_code, f"Executable/signature changes in reviewed debug-only class: {name}")
                old_debug, new_debug = (dump(path, ["-p", "-c", "-s", "-l"]).decode("utf-8") for path in (baseline, jar))
                pattern = re.compile(r"(?m)^(\s*line )(\d+)(: \d+)\r?$")
                old_lines = [int(match[1]) for match in pattern.findall(old_debug)]
                new_lines = [int(match[1]) for match in pattern.findall(new_debug)]
                require(old_lines and len(old_lines) == len(new_lines)
                        and all(b - a == DEBUG_ONLY[name] for a, b in zip(old_lines, new_lines))
                        and pattern.sub(r"\1#\3", old_debug) == pattern.sub(r"\1#\3", new_debug),
                        "Nested class differs beyond its reviewed debug line shift")
                row.update(executableAndSignaturesSha256=sha(old_code), onlyLineNumberTableShift=DEBUG_ONLY[name])
                debug.append(row.copy())
            changed.append(row)
        for name in sorted(after - before):
            require(ADDITIONS.fullmatch(name) or name in REVIEWED_ADDITIONS, f"Unexpected new JAR entry: {name}")
            if name in REVIEWED_ADDITIONS:
                require(sha(new.read(name)) == REVIEWED_ADDITIONS[name], f"Reviewed native regression changed: {name}")
            added.append(dict(entry=name, bytes=len(new.read(name)), sha256=sha(new.read(name))))
        require(len(added) >= 6, "Missing new native alchemy audit families")
        jei = [name for name in before if name.startswith("thaumcraft/client/jei/")]
        require(len(jei) >= 41 and all(old.read(name) == new.read(name) for name in jei), "Accepted JEI family changed")
    chain = []
    previous = "tcp-20261009-firststeps-01"
    for phase, run_id in zip(("generate", "resume", "reload"), (args.generate, args.resume, args.reload)):
        require(re.fullmatch(r"[A-Za-z0-9_-]{1,80}", run_id), "Invalid private cohort ID")
        root = build / "tcp-alchemy" / run_id
        verified = document(root / "verification.json")
        require(verified.get("passed") is True and verified.get("auditPhase") == phase
                and verified.get("runId") == run_id and verified.get("goal") == "alchemy"
                and verified.get("sourceRunId") == previous, "Sealed alchemy chain identity/provenance mismatch")
        for group in ("reports", "logs", "screenshots"):
            rows = verified.get(group, [])
            require(rows and len(rows) <= 512, f"Missing/unbounded sealed {group}")
            for row in rows:
                path = Path(row["path"]).resolve()
                require(path.is_relative_to(root.resolve())
                        and all(row.get(key) == value for key, value in evidence(path).items()),
                        f"Sealed {group} changed: {path}")
        saved = verified["savedPlayer"]
        require(evidence(Path(saved["path"])) == saved, "Sealed actual saved player bytes changed")
        runtime = document(root / "runtime.json")
        processes = runtime.get("processes", [])
        require(runtime.get("status") == "completed" and runtime.get("serverReady") is True
                and runtime.get("buildExitCode") == 0 and len(processes) == 2
                and {p["role"] for p in processes} == {"server", "client"}
                and all(p["exitCode"] == 0 and p["forcedStop"] is False for p in processes), "Native engines failed or were forced")
        require(verified.get("processes") == processes, "Native process evidence changed")
        chain.append(dict(phase=phase, runId=run_id, verification=evidence(root / "verification.json"),
                          runtime=evidence(root / "runtime.json"), savedPlayer=saved, processes=processes))
        previous = run_id
    return dict(schema=1, stage=12, passed=True, status="PASS", observedAt=datetime.now(timezone.utc).isoformat(),
                scope="Native TCP crucible, research table theories, smelting, essentia transfer, paid infusion restart and complete owner reload",
                finalJar={**evidence(jar), "duplicateEntries": 0, "matchingCompiledClasses": classes, "matchingProcessedResources": resources},
                acceptedFirststepsBaseline={**evidence(baseline), "protectedDifferences": 0,
                    "allBaselineResourcesUnchanged": True, "identicalJeiClassFamilyEntries": len(jei),
                    "changedClasses": changed, "debugOnlyChanges": debug, "addedClasses": added,
                    "reviewedContextualObserverSha256": REVIEWED_HOOK_SHA256,
                    "reviewedArcaneBeforePaymentObserverSha256": REVIEWED_ARCANE_OBSERVER_SHA256,
                    "arcaneSourceIdenticalAfterRemovingExactObserver": True,
                    "reviewedCompatibilitySources": reviewed_sources,
                    "sourceCraftReceiptCompatibility": {
                        "source": "TC6 BETA26 ConfigBlocks:328-366 and BlockTC constructors",
                        "obsoleteNitorDescriptor": "nitor metadata 0..15 maps to the real colored BlockItem; original lightblue/silver names retained",
                        "nativeRequiredMarkers": {"crucible": "[#]408125240", "nitor_yellow": "[#]1800551424",
                            "research_table": "[#]1543993555", "smelter_basic": "[#]2042497286", "infusion_matrix": "[#]1192670574"}},
                    "emptyPhialCraftingCompatibility": {"originalSource": evidence(original_phial),
                        "restoredCallback": "ItemPhial overrides the generic crafting callback; no random aspect creation on empty phials",
                        "actualResultSlotAndFilledComponentsRegression": "PhialCraftingGameTests"}},
                unitTotals=totals, unitPassed=totals["tests"] - totals["skipped"], suites=len(suites),
                nativeCohortChain=chain, buildLog=evidence(log),
                nativeGameTests={"requiredPassed": 330, "failed": 0, "log": evidence(game_test_log),
                    "scope": "All registered thaumcraft GameTests, including actual alchemy craft bridge and corrected source-backed older assertions"},
                limits=["Controlled material/arena fixtures; full survival gathering is not tested.",
                        "Broader multiplayer, equipment, golems, mobs and complete visual parity remain separate work."])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("jar", "build-log", "game-test-log", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    for name in ("generate", "resume", "reload"):
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args()
    try:
        result = verify(args)
        with args.output.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print(json.dumps({"passed": True, "jarSha256": result["finalJar"]["sha256"], "output": str(args.output.resolve()),
                          "unitTotals": result["unitTotals"], "nativePhases": 3}))
        return 0
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"Alchemy artifact verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
