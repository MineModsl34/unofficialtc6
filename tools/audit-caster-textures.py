"""Independently seal the source caster atlas fix and genuine private native rendering evidence."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import sys
import uuid
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
PARSER_PINS = {
    "tools/audit-tcp-alchemy.py": "bfa3d1310b81d079e59159a63ae552e92d92d872cae147c526935ee2b2a1b07c",
    "tools/audit-tcp-bootstrap-reload.py": "29b8dfcbcfb0f99f70f96ce319ed0574bfc06fbfe3e0429fd83a380d97bc5695",
}
for _parser_name, _parser_hash in PARSER_PINS.items():
    _parser_path = Path(__file__).resolve().parent.parent / _parser_name
    if not _parser_path.is_file() or _parser_path.stat().st_size > 4 * 1024 * 1024 or hashlib.sha256(_parser_path.read_bytes()).hexdigest() != _parser_hash:
        raise ValueError(f"Reviewed native parser changed before import: {_parser_name}")
_spec = importlib.util.spec_from_file_location("tc_caster_native_parsers", Path(__file__).with_name("audit-tcp-alchemy.py"))
native = importlib.util.module_from_spec(_spec)
sys.modules[_spec.name] = native
_spec.loader.exec_module(native)
nbt, require, unlinked, sha = native.nbt, native.require, native.unlinked, native.sha

BASELINE_SHA256 = "b9eb5b93f553b66f6be3812697deef5dbb5b7b87318952f3dd5d8094265a41c0"
ORIGINAL_SHA256 = "9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f"
PURPOSE = "thaumcraft-caster-texture-review"
OWNER_UUID = "2159e193-c786-3bf5-a6ab-d54a4492ea40"  # Native OfflinePlayer:TC_Jei_Review.
ATLAS = "assets/minecraft/atlases/blocks.json"
PRIVATE = re.compile(r"thaumcraft/client/lib/ClientJeiReviewScenario(?:\$[^/]+)?\.class")
# Exact pins from root's source/compiled-byte review of the private native probe.
# No family wildcard permits arbitrary changed executable bytes.
REVIEWED_PRIVATE_CLASSES = {
    "thaumcraft/client/lib/ClientJeiReviewScenario$ServerWork.class": "bde5152aadb79f55d3b7c5502d0fc15031a2eb45735069976ddaf9b09bfa34e7",
    "thaumcraft/client/lib/ClientJeiReviewScenario.class": "edfdf17158c74aae8ce72951ab5ab13e022026f0ac89c4d87d96fab12dd1fc99",
}
# Exact root-reviewed production fix and registered native regression bytes.
REVIEWED_PRODUCTION_CLASSES = {
    "thaumcraft/Registrar.class": "fc3f796a08cfecb55cfca7607077392274c5069cada4304bc25270a961e0c7c7",
    "thaumcraft/api/items/TCDataComponents.class": "b1bde6c4939ed7906a8acd86cff9290b63a9b79dd09a5725a3587a9c09dc529f",
    "thaumcraft/api/items/CasterStoredStack.class": "549b6266a5011377b9a3003ff6a9bbf9abd4f4b3234628505e9e3ee2377aa072",
    "thaumcraft/common/items/casters/ItemCaster.class": "f8c61365ae12a4c2a427ec456afa6c7510c1cb8dd994d0bd028ddf27cc01d417",
    "thaumcraft/common/items/casters/ItemCaster$1.class": "22ad8e6ccb5753993efbe8d8351869d7f3054fd863d90af44c0c458f46381d72",
    "thaumcraft/gametest/CasterStoredStackGameTests.class": "28e6161733c247dc3e3a465ac89edc21fd1ba269a7d1b9d7d0da8bd3fa30ef8f",
}
PRODUCTION_CLASS_SET = {
    "thaumcraft/Registrar.class",
    "thaumcraft/api/items/TCDataComponents.class",
    "thaumcraft/api/items/CasterStoredStack.class",
    "thaumcraft/common/items/casters/ItemCaster.class",
    "thaumcraft/common/items/casters/ItemCaster$1.class",
    "thaumcraft/gametest/CasterStoredStackGameTests.class",
}
REQUIRED_GAME_TESTS = 332
REVIEWED_SOURCES = {
    **PARSER_PINS,
    "port/src/main/java/thaumcraft/client/lib/ClientJeiReviewScenario.java": "cfa29c29b491f4ca8fc56f66d6b6f5ac60ba87b035f02fa5d73f202359d84504",
    "port/src/main/resources/assets/minecraft/atlases/blocks.json": "56fbb72cd6d9c53065c045010f76bc7084d1f01f0f87cba91a3ab8845dfb9253",
    "port/src/test/java/thaumcraft/datagen/BannerResourcesTest.java": "2c3087a041dcd6cee587101ff8539c929c5c37d4f3e7cb7a2139eb373caf2a96",
    "port/build.gradle": "9f388d83db7913208036537aab3e6e86a5ac2e109593a8807bd45e9ce979cdcf",
    "tools/run-jei-review.ps1": "820cddec428129bf52dd400ae7a8b47d4c0ac45197ffe7f4775df56eddf94a32",
    "port/src/main/java/thaumcraft/api/items/CasterStoredStack.java": "87b1fc67df83c9e4362d180c5abd96ba4bda9c49947a500acdedc3a6a1ae45da",
    "port/src/main/java/thaumcraft/gametest/CasterStoredStackGameTests.java": "32390f00c089c11a38af87e6e3a2ed9d1b77221bf25073d3af8d627800c00a61",
    "port/src/main/java/thaumcraft/api/items/TCDataComponents.java": "422800f92dd7bec67236a2359d5afe0f85204be8f27f45597028e510322d0533",
    "port/src/main/java/thaumcraft/Registrar.java": "2878fd69f75e9e9490bd2422f71305061cf289e2c3d4006b07cc7f29a91ca9f3",
    "port/src/main/java/thaumcraft/common/items/casters/ItemCaster.java": "6deb9d7e9b6a48d35e83690cf571738d9ff54e076ac1901f341fbed7f8ae026d",
}
TEXTURES = {
    "basic": "e686075c5b3c5b41946086021860c0310cdfddc3daefd07609a495474b7279c1",
    "advanced": "0a66d596a46042d29c926022207329fd8f8d1a8199057351f273110e52b0c32f",
}
MODELS = ("caster_basic", "caster_basic_model", "caster_basic_focus_model", "caster_gauntlet", "caster_gauntlet_focus")
NATIVE_STEPS = ("fresh-private-world-ready", "caster-owned-server-fixtures-received", "caster-two-source-atlas-sprites",
                "caster-native-empty-model", "caster-native-empty-screenshot", "caster-native-swap-dispatched",
                "caster-native-swap-server-and-s2c-received", "caster-native-focused-model",
                "caster-native-focused-screenshot", "caster-texture-visual-complete")
EXPECTED_ATLAS = {"sources": [
    {"type": "minecraft:single", "resource": name, "sprite": name}
    for name in ("thaumcraft:research/quill", "thaumcraft:entity/armor/caster_basic", "thaumcraft:entity/armor/caster_advanced")
]}


def bounded(path, maximum=4 * 1024 * 1024):
    path = unlinked(path)
    require(path.is_file() and path.stat().st_size <= maximum, f"Missing/unbounded caster evidence: {path}")
    return path.read_bytes()


def evidence(path, maximum=4 * 1024 * 1024):
    path = unlinked(path)
    data = bounded(path, maximum)
    return {"path": str(path), "bytes": len(data), "sha256": sha(data)}


def document(path):
    value = native.json_value(bounded(path))
    require(isinstance(value, dict), "Native caster JSON must be a bounded object")
    return value


def utc(value):
    require(isinstance(value, str), "Missing native UTC identity")
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(result.utcoffset() is not None and result.utcoffset().total_seconds() == 0, "Native process time must be UTC")
    return result


def zip_names(archive):
    entries = archive.infolist()
    require(len(entries) <= 16384 and len(entries) == len({row.filename for row in entries}), "Duplicate/unbounded JAR entries")
    require(all(row.file_size <= 64 * 1024 * 1024 and not row.flag_bits & 1 for row in entries)
            and sum(row.file_size for row in entries) <= 256 * 1024 * 1024, "Unbounded/encrypted source JAR contents")
    names = {row.filename for row in entries if not row.is_dir()}
    require(all(not name.startswith("/") and ".." not in name.split("/") and "\\" not in name for name in names), "Invalid native JAR path")
    return names


def verify_jar(repo, jar):
    build = repo / "port/build"
    baseline = build / "tcp-alchemy/alchemy-20261009-chain-08/accepted-alchemy.jar"
    original = repo.parent / "mods 1.12.2/Thaumcraft-1.12.2-6.1.BETA26.jar"
    require(sha(bounded(baseline, 64 * 1024 * 1024)) == BASELINE_SHA256, "Immutable accepted alchemy JAR changed")
    require(sha(bounded(original, 64 * 1024 * 1024)) == ORIGINAL_SHA256, "Original BETA26 source JAR changed")
    require(jar.parent == unlinked(build / "libs") and jar.suffix == ".jar", "Final caster JAR must be the current normal build artifact")
    bounded(jar, 64 * 1024 * 1024)
    require(REVIEWED_PRIVATE_CLASSES and REVIEWED_SOURCES and set(REVIEWED_PRODUCTION_CLASSES) == PRODUCTION_CLASS_SET,
            "Native private probe and immutable caster storage source/compiled pins have not been explicitly reviewed")
    source_evidence = []
    for name, expected in REVIEWED_SOURCES.items():
        path = repo / name
        require(sha(bounded(path)) == expected, f"Reviewed caster source changed: {name}")
        source_evidence.append(evidence(path))
    changed = []
    with zipfile.ZipFile(baseline) as previous, zipfile.ZipFile(jar) as current, zipfile.ZipFile(original) as source:
        before, after, source_names = zip_names(previous), zip_names(current), zip_names(source)
        require(before <= after, "Accepted alchemy entries were removed")
        private = {name for name in after if PRIVATE.fullmatch(name)}
        require(private == set(REVIEWED_PRIVATE_CLASSES), "Native private probe compiled family differs from the exact reviewed set")
        for name, expected in REVIEWED_PRIVATE_CLASSES.items():
            require(sha(current.read(name)) == expected, f"Native private probe bytecode changed: {name}")
        require({name for name in after if name.startswith(("thaumcraft/api/items/CasterStoredStack", "thaumcraft/gametest/CasterStoredStackGameTests")) and name.endswith(".class")}
                == {name for name in PRODUCTION_CLASS_SET if "CasterStoredStack" in name}, "Native immutable caster storage compiled family differs from the exact reviewed set")
        for name, expected in REVIEWED_PRODUCTION_CLASSES.items():
            require(name in after and sha(current.read(name)) == expected, f"Reviewed immutable caster storage/regression bytecode changed: {name}")
        for name in sorted(before | after):
            old = previous.read(name) if name in before else None
            new = current.read(name) if name in after else None
            if old == new:
                continue
            require(name == ATLAS or name in REVIEWED_PRIVATE_CLASSES or name in REVIEWED_PRODUCTION_CLASSES,
                    f"Protected accepted alchemy entry changed: {name}")
            changed.append({"entry": name, "beforeSha256": sha(old) if old is not None else None, "afterSha256": sha(new)})
        require(any(row["entry"] == ATLAS for row in changed), "Source sprite atlas fix is absent")
        require(native.json_value(current.read(ATLAS)) == EXPECTED_ATLAS, "Atlas sources differ from the exact three reviewed native sprite entries")
        require(native.json_value(previous.read(ATLAS)) == {"sources": EXPECTED_ATLAS["sources"][:1]}, "Accepted original atlas baseline changed")
        jei = {name for name in before if name.startswith("thaumcraft/client/jei/") and name.endswith(".class")}
        require(len(jei) == 41 and {name for name in after if name.startswith("thaumcraft/client/jei/") and name.endswith(".class")} == jei
                and all(previous.read(name) == current.read(name) for name in jei), "Any of the 41 accepted JEI classes changed")
        classes = resources = 0
        for relative, suffix in (("classes/java/main", ".class"), ("resources/main", None)):
            directory = unlinked(build / relative)
            require(directory.is_dir(), "Missing current compiled/processed caster build outputs")
            for path in sorted(directory.rglob("*")):
                if not path.is_file() or suffix and path.suffix != suffix:
                    continue
                path = unlinked(path)
                name = path.relative_to(directory).as_posix()
                require(name in after and bounded(path, 64 * 1024 * 1024) == current.read(name), f"Final caster JAR differs from actual compiled/processed output: {name}")
                if suffix:
                    classes += 1
                else:
                    resources += 1
        require(classes >= 1737 and resources >= 2863, "Current caster artifact classes/resources are incomplete")
        source_assets = []
        for kind, expected in TEXTURES.items():
            name = f"assets/thaumcraft/textures/entity/armor/caster_{kind}.png"
            require(name in source_names and source.read(name) == current.read(name) == previous.read(name)
                    and sha(current.read(name)) == expected, "Original caster PNG bytes were changed or replaced")
            source_assets.append({"entry": name, "sha256": expected, "sourceBytesUnchanged": True})
        for name in MODELS:
            entry = f"assets/thaumcraft/models/item/{name}.json"
            require(entry in source_names and native.json_value(source.read(entry)) == native.json_value(current.read(entry))
                    and previous.read(entry) == current.read(entry), "Original caster model geometry/UVs/predicates were changed")
            source_assets.append({"entry": entry, "sha256": sha(current.read(entry)), "sourceJsonEqual": True})
        require("assets/thaumcraft/models/item/caster_advanced.json" not in after, "Visual fix introduced an unreviewed advanced caster item model")
    return {"finalJar": evidence(jar, 64 * 1024 * 1024), "acceptedAlchemyJar": evidence(baseline, 64 * 1024 * 1024),
            "sourceBeta26Jar": evidence(original, 64 * 1024 * 1024), "changedEntries": changed,
            "identicalJeiClasses": len(jei), "matchingCompiledClasses": classes, "matchingProcessedResources": resources,
            "reviewedSources": source_evidence, "originalCasterAssets": source_assets,
            "productionEntries": [row for row in changed if row["entry"] in REVIEWED_PRODUCTION_CLASSES and not row["entry"].startswith("thaumcraft/gametest/")],
            "regressionEntries": [row for row in changed if row["entry"].startswith("thaumcraft/gametest/")],
            "privateProbeEntries": [row for row in changed if row["entry"] in REVIEWED_PRIVATE_CLASSES],
            "reviewedProductionClasses": REVIEWED_PRODUCTION_CLASSES}


def verify_units(repo, build_log):
    build = repo / "port/build"
    require(build_log.is_relative_to(build), "Complete caster test/JAR/GameTest build log is outside the reviewed build directory")
    log_text = bounded(build_log, 32 * 1024 * 1024).decode("utf-8-sig")
    require("BUILD SUCCESSFUL" in log_text and "BUILD FAILED" not in log_text and "Exception in thread" not in log_text,
            "Missing successful complete caster test/JAR/GameTest build")
    require("> Task :runGameTestServer" in log_text
            and re.findall(r"All (\d+) required tests passed", log_text) == [str(REQUIRED_GAME_TESTS)]
            and re.findall(r"(\d+) GAME TESTS COMPLETE IN", log_text) == [str(REQUIRED_GAME_TESTS)]
            and "Game test server shutting down" in log_text and "All dimensions are saved" in log_text
            and not re.search(r"\b[1-9]\d* required tests failed\b", log_text),
            "Complete immutable caster regression did not pass all 332 required native GameTests and normal server shutdown")
    suites = sorted((build / "test-results/test").glob("TEST-*.xml"))
    require(len(suites) >= 199, "Missing complete existing caster regression suite")
    totals = {name: 0 for name in ("tests", "failures", "errors", "skipped")}
    seen, records = set(), []
    for path in suites:
        root = ET.fromstring(bounded(path))
        require(root.tag == "testsuite" and root.attrib.get("name") not in seen, "Duplicate/invalid native unit test suite")
        seen.add(root.attrib.get("name"))
        cases = root.findall("testcase")
        require(len(cases) == int(root.attrib["tests"]) and not root.findall(".//failure") and not root.findall(".//error"), "Unit suite case count/failure details differ")
        for name in totals:
            totals[name] += int(root.attrib.get(name, 0))
        require(sum(bool(case.find("skipped") is not None) for case in cases) == int(root.attrib.get("skipped", 0)), "Unit skipped case details differ")
        records.append(evidence(path))
    require(totals["tests"] >= 1134 and totals["failures"] == totals["errors"] == 0 and totals["skipped"] == 2,
            "Complete existing unit suite did not pass with exactly two reviewed opt-in skips")
    require("thaumcraft.datagen.BannerResourcesTest" in seen, "Source native atlas resource assertions were not run")
    return {"unitTotals": totals, "unitPassed": totals["tests"] - totals["skipped"], "unitSuites": records,
            "gameTests": {"requiredPassed": REQUIRED_GAME_TESTS, "completeNativeSuite": True, "normalServerShutdown": True},
            "buildLog": evidence(build_log, 32 * 1024 * 1024)}


def exact_stack(value, focused):
    stack = nbt.compound(nbt.snbt(value))
    expected = {"id": nbt.Tag(8, "thaumcraft:caster_basic"), "count": nbt.Tag(3, 1)}
    if focused:
        expected["components"] = nbt.Tag(10, {"thaumcraft:caster_focus": nbt.Tag(10, {"id": nbt.Tag(8, "thaumcraft:focus_1"), "count": nbt.Tag(3, 1)})})
    require(stack == expected, "Native visual fixture differs from the genuine plain/focus1 basic caster stack")
    return nbt.Tag(10, stack)


def inventory(value):
    tag = nbt.snbt(value) if isinstance(value, str) else value
    require(tag.kind == 9 and len(tag.value) <= 41, "Native caster inventory is not a bounded typed list")
    result = {}
    for child in tag.value:
        row = dict(nbt.compound(child))
        slot = nbt.scalar(row.pop("Slot"), 1)
        require(0 <= slot <= 40 and slot not in result, "Native caster inventory has duplicate/unreviewed slots")
        require(nbt.scalar(row["count"], 3) > 0, "Native caster inventory contains empty stacks")
        result[slot] = nbt.Tag(10, row)
    return result


def one_step(report, name):
    matches = [row["details"] for row in report.get("steps", []) if row.get("step") == name]
    require(len(matches) == 1, f"Missing/duplicate genuine native caster step: {name}")
    return matches[0]


def verify_native(repo, root):
    require(root.parent == unlinked(repo / "port/build/jei-review") and re.fullmatch(r"[A-Za-z0-9_-]{1,65}", root.name), "Caster run must be a fresh immediate private JEI review directory")
    report_path = root / "client/caster-texture-review.json"
    scenario_path = root / "client/jei-review-scenario.json"
    report, scenario = document(report_path), document(scenario_path)
    require(report == scenario, "Sealed native caster report aliases differ")
    require(report.get("schemaVersion") == 1 and report.get("purpose") == PURPOSE and report.get("passed") is True and report.get("phase") == "complete"
            and report.get("casterVisualReview") is True and report.get("mode") == "caster-textures"
            and report.get("registeredCasterItem") == "thaumcraft:caster_basic" and report.get("advancedItemTested") is False
            and report.get("researchFixturesGranted") == 0 and report.get("createdFreshWorld") is True
            and report.get("world") == "jei-review" and unlinked(Path(report["clientDirectory"])) == root / "client",
            "Native caster identity/fixture scope/terminal state differs")
    require(isinstance(report.get("steps"), list) and tuple(row.get("step") for row in report["steps"]) == NATIVE_STEPS
            and all(isinstance(row.get("details"), dict) for row in report["steps"]), "Native caster execution steps differ from the reviewed ordinary fixture/frame/SWAP path")
    step_times = [utc(row["at"]) for row in report["steps"]]
    require(step_times == sorted(step_times), "Actual native caster receipt times moved backwards")
    ready = one_step(report, "fresh-private-world-ready")
    require(ready.get("fixedSeed") == 62121120261009 and all(ready.get(name) is True for name in ("creative", "cheats", "peaceful", "flat", "actualMemoryConnection"))
            and unlinked(Path(ready["privateWorld"])) == root / "client/saves/jei-review"
            and type(ready.get("aspectRevision")) is int and ready["aspectRevision"] > 0
            and type(ready.get("recipeRevision")) is int and ready["recipeRevision"] > 0
            and re.fullmatch(r"[0-9a-f]{64}", ready.get("researchCatalogDigest", "")), "Actual fresh creative bootstrap/accepted owner snapshots missing")
    fixtures = report["casterFixtures"]
    require(fixtures.get("actualOwnedServerThread") is True and fixtures.get("actualCreativeOwner") is True
            and fixtures.get("actualServerMenuId") == 0 and str(uuid.UUID(fixtures["ownerUuid"])) == OWNER_UUID
            and fixtures == one_step(report, "caster-owned-server-fixtures-received"), "Visual fixtures did not belong to the actual private creative server owner/journal")
    plain, focused = exact_stack(fixtures["emptyStackNbt"], False), exact_stack(fixtures["focusedStackNbt"], True)
    before, after = inventory(fixtures["inventoryBeforeNbt"]), inventory(fixtures["inventoryAfterNbt"])
    require(before == {} and after == {0: plain, 1: focused}, "Native fixture wrote inventory beyond the exact two declared source stacks")
    require(fixtures.get("nativeFullMenuS2CPublished") is True and type(fixtures.get("serverPublishedStateId")) is int
            and fixtures.get("actualOwnerS2C") is True and fixtures.get("actualAcceptedS2CStateId") == fixtures["serverPublishedStateId"],
            "Native visual fixture owner menu S2C publication missing")
    swap = report["casterSwap"]
    require(swap.get("menuId") == 0 and swap.get("slot") == 37 and swap.get("button") == 0 and swap.get("clickType") == "SWAP"
            and all(swap.get(name) is True for name in ("nativeC2S", "actualOwnerS2C", "actualServerAccepted", "nativeFullMenuS2CPublished"))
            and type(swap.get("serverPublishedStateId")) is int and swap.get("actualAcceptedS2CStateId") == swap["serverPublishedStateId"]
            and inventory(swap["inventoryBeforeNbt"]) == after and inventory(swap["inventoryAfterNbt"]) == {0: focused, 1: plain},
            "Real vanilla caster SWAP/C2S/full-menu S2C acceptance/typed conservation differs")
    dispatched = one_step(report, "caster-native-swap-dispatched")
    require(swap == one_step(report, "caster-native-swap-server-and-s2c-received") and swap.get("actualOwnedServerThread") is True
            and swap.get("menuStateIdBefore") == fixtures["actualAcceptedS2CStateId"]
            and all(swap.get(key) == value for key, value in dispatched.items()), "Actual vanilla SWAP dispatch/server receipt journal differs")
    atlas = report["casterAtlas"]
    require(isinstance(atlas, list) and len(atlas) == 2 and {row.get("spriteId") for row in atlas} == {f"thaumcraft:entity/armor/caster_{kind}" for kind in TEXTURES}
            and all(row.get("width") == row.get("height") == 32 and row.get("missing") is False for row in atlas), "Actual native atlas sprite identity/source dimensions missing")
    require(one_step(report, "caster-two-source-atlas-sprites") == {"sprites": atlas}, "Actual native source atlas sprite receipt differs from the sealed journal")
    frames = report["casterFrames"]
    require(isinstance(frames, list) and len(frames) == 2 and [row.get("variant") for row in frames] == ["empty", "focused"], "Native caster frames are missing/out of order")
    for index, frame in enumerate(frames):
        require(frame.get("actualOwnerS2C") is True and frame.get("actualNativeItemModel") is True and frame.get("firstPerson") is True
                and frame.get("actualAcceptedS2CStateId") == (fixtures, swap)[index]["actualAcceptedS2CStateId"] and frame.get("focusProperty") == index
                and frame.get("quads") == (10, 15)[index] and frame.get("tint0Quads") == 10 and frame.get("tint1Quads") == (0, 5)[index]
                and frame.get("sprites") == ["thaumcraft:entity/armor/caster_basic"] and type(frame.get("nativeClientTick")) is int
                and exact_stack(frame["heldStackNbt"], bool(index)) == (plain, focused)[index], "Actual registered basic caster resolved model/property/quads/sprite differs from original source")
        require(frame == one_step(report, f"caster-native-{frame['variant']}-model"), "Native resolved held model differs from its actual receipt journal")
    require(frames[0]["nativeClientTick"] < frames[1]["nativeClientTick"], "Actual held variant native ticks are not distinct/increasing")
    screenshots = report["casterScreenshots"]
    require(isinstance(screenshots, list) and len(screenshots) == 2 and [row.get("variant") for row in screenshots] == ["empty", "focused"], "Native two held-variant screenshot callbacks missing")
    images = []
    for index, row in enumerate(screenshots):
        path = unlinked(Path(row["path"]))
        require(path.parent == root / "client/screenshots" and path.name == f"caster-basic-{row['variant']}.png"
                and row.get("width") == 1280 and row.get("height") == 720 and row.get("nativeFramebufferCallback") is True
                and type(row.get("nativeClientTick")) is int and row["nativeClientTick"] >= frames[index]["nativeClientTick"], "Native framebuffer callback/path/dimensions/context differ")
        require(row == one_step(report, f"caster-native-{row['variant']}-screenshot"), "Actual native framebuffer callback differs from its sealed receipt journal")
        images.append(native.png(root, path))
    require(images[0]["sha256"] != images[1]["sha256"], "Native two caster variant frames are identical")
    runtime_path = root / "client/caster-runtime.json"
    runtime = document(runtime_path)
    process = report["clientProcess"]
    require(runtime.get("schemaVersion") == 1 and runtime.get("purpose") == "thaumcraft-caster-texture-runtime"
            and runtime.get("task") == "runJeiReviewClient" and runtime.get("gradleExitCode") == 0 and runtime.get("normalJavaExecExitCode") == 0
            and runtime.get("terminationMethod") == "normal-javaexec-return" and runtime.get("clientProcess") == process
            and unlinked(Path(runtime["clientLog"])) == root / "client.log" and unlinked(Path(runtime["clientReport"])) == report_path,
            "Native standard JavaExec normal-exit inference/context differs")
    require(type(process.get("pid")) is int and process["pid"] > 0 and process.get("actualNativeMainThread") is True
            and utc(runtime["startedUtc"]) <= utc(process["startUtc"]) <= step_times[0]
            and step_times[-1] <= utc(report["observedAt"]) <= utc(runtime["completedUtc"]), "Missing actual native process/receipt identity/time bounds")
    executable = unlinked(Path(process["executable"]))
    require(executable == unlinked(repo.parent / "toolchain/jdk21/bin/java.exe"), "Native caster process used an unreviewed runtime")
    arguments = process.get("vmInputArguments")
    require(isinstance(arguments, list) and all(isinstance(value, str) for value in arguments)
            and "-Dthaumcraft.jeiReview=true" in arguments and "-Dthaumcraft.casterVisualReview=true" in arguments,
            "Actual native JVM was not the explicitly opted-in private caster review")
    log_path = root / "client.log"
    text = bounded(log_path, 32 * 1024 * 1024).decode("utf-8-sig")
    require("> Task :runJeiReviewClient" in text and "BUILD SUCCESSFUL" in text and "Stopping!" in text
            and "All chunks are saved" in text and "BUILD FAILED" not in text
            and "Exception in thread" not in text and "Process 'command" not in text,
            "Native JavaExec task did not emit actual successful normal client/world shutdown")
    require(not any("caster_" in line and ("Missing textures" in line or "Unable to load model" in line) for line in text.splitlines()), "Native caster model/texture loading still emitted a target failure")
    saved_path = root / f"client/saves/jei-review/playerdata/{OWNER_UUID}.dat"
    saved = nbt.compound(nbt.read_nbt(bounded(saved_path, 1024 * 1024)))
    actual_uuid = nbt.scalar(saved["UUID"], 11)
    require(len(actual_uuid) == 4 and str(uuid.UUID(bytes=b"".join(struct.pack(">I", value & 0xFFFFFFFF) for value in actual_uuid))) == OWNER_UUID
            and nbt.scalar(saved["playerGameType"], 3) == 1 and nbt.scalar(saved["SelectedItemSlot"], 3) == 0
            and inventory(saved["Inventory"]) == inventory(swap["inventoryAfterNbt"]),
            "Independently saved actual creative owner/final exact native swapped inventory differs")
    return {"nativeReport": evidence(report_path), "nativeScenario": evidence(scenario_path), "nativeRuntime": evidence(runtime_path),
            "nativeLog": evidence(log_path, 32 * 1024 * 1024), "screenshots": images, "clientProcess": process,
            "exitInference": "Normal JVM exit0 inferred from successful standard Gradle JavaExec task; native stopping/world-save log independently checked",
            "nativeVariants": frames, "actualAtlasSprites": atlas,
            "nativeFixture": fixtures, "nativeSwap": swap, "independentSavedPlayer": evidence(saved_path, 1024 * 1024)}


def verify(args):
    repo = unlinked(Path(__file__).resolve().parent.parent)
    root, jar, build_log = unlinked(args.root), unlinked(args.jar), unlinked(args.build_log)
    result = {"schema": 1, "stage": 12, "passed": True, "status": "PASS", "observedAt": datetime.now(timezone.utc).isoformat(),
              "scope": "Source caster sprite atlas repair, immutable focus/picked component storage, and genuine basic held empty/focus1 rendering",
              "limits": ["Declared two item fixtures in a fresh private creative integrated world", "No advanced caster item registered/tested", "Two native framebuffer frames; no full rendering parity claim"]}
    result.update(verify_jar(repo, jar))
    result.update(verify_units(repo, build_log))
    result.update(verify_native(repo, root))
    preserved = root / "accepted-caster-textures.jar"
    require(bounded(preserved, 64 * 1024 * 1024) == bounded(jar, 64 * 1024 * 1024),
            "Exact private immutable caster JAR copy differs from actual normal compiled/processed build")
    result["preservedJar"] = evidence(preserved, 64 * 1024 * 1024)
    result["verifier"] = evidence(Path(__file__))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--build-log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = verify(args)
        repo = unlinked(Path(__file__).resolve().parent.parent)
        output = unlinked(args.output)
        require(output == repo / "reference/validation/phase12-caster-textures-final-current.json", "Caster final evidence must use the reviewed validation output path")
        with output.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print(json.dumps({"passed": True, "changedEntries": len(result["changedEntries"]), "unitTotals": result["unitTotals"], "nativeScreenshots": len(result["screenshots"])}))
        return 0
    except (OSError, EOFError, ValueError, KeyError, TypeError, IndexError, OverflowError, ET.ParseError, zipfile.BadZipFile) as failure:
        print(f"Caster texture verification failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
