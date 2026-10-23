"""Read-only check of the completed phase-12 build, runtime reports and screenshots."""
import argparse
import hashlib
import json
import re
import struct
import xml.etree.ElementTree as ET
import zipfile
import zlib
from datetime import datetime, timezone
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compare_runtime_baseline(baseline, current):
    allowed = re.compile(r'thaumcraft/client/jei/JeiTransferClientAudit(?:\$[^/]+)?\.class')
    differences = []
    with zipfile.ZipFile(baseline) as previous, zipfile.ZipFile(current) as final:
        old_names, new_names = previous.namelist(), final.namelist()
        require(len(old_names) == len(set(old_names)) and len(new_names) == len(set(new_names)), 'Baseline/final JAR contains duplicate entries')
        names = sorted(set(old_names) | set(new_names))
        protected = 0
        for name in names:
            old = previous.read(name) if name in previous.NameToInfo else None
            new = final.read(name) if name in final.NameToInfo else None
            if not allowed.fullmatch(name):
                require(old == new, f'Reused runtime baseline differs outside the transfer client test fixture: {name}')
                protected += 1
            elif old != new:
                differences.append(dict(entry=name, baselineSha256=hashlib.sha256(old).hexdigest() if old is not None else None,
                                        finalSha256=hashlib.sha256(new).hexdigest() if new is not None else None))
    return dict(path=str(baseline.resolve()), sha256=digest(baseline), finalJarSha256=digest(current),
                comparedEntries=len(names), identicalProtectedEntries=protected,
                allowedClassFamily='thaumcraft/client/jei/JeiTransferClientAudit.class and its nested classes',
                allowedClassDifferences=differences, allowedClassDifferenceCount=len(differences), protectedDifferences=0)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def positive(value):
    return type(value) is int and value > 0


def steps(report, required):
    rows = report.get('steps', [])
    result = {}
    previous = -1
    for name in required:
        matches = [(index, row.get('details', {})) for index, row in enumerate(rows) if row.get('step') == name]
        require(len(matches) == 1, f'Scenario requires exactly one completed step: {name}')
        index, details = matches[0]
        require(index > previous, f'Scenario steps are out of order: {name}')
        previous = index
        result[name] = details
    return result


def fresh_world(report, directory):
    require(report.get('passed') is True and report.get('phase') == 'complete', 'Private client scenario did not complete')
    require(report.get('createdFreshWorld') is True and report.get('world') == 'jei-review', 'Missing fresh private review world')
    require(Path(report.get('clientDirectory', '')).resolve() == directory.resolve(), 'Scenario belongs to another client directory')
    ready = steps(report, ['fresh-private-world-ready'])['fresh-private-world-ready']
    require(Path(ready.get('privateWorld', '')).resolve() == (directory / 'saves/jei-review').resolve(), 'Readiness belongs to another world')
    require(ready.get('fixedSeed') == 62121120261009 and all(ready.get(key) is True for key in
            ('creative', 'cheats', 'peaceful', 'flat', 'actualMemoryConnection')), 'Native private-world bootstrap evidence is incomplete')
    require(positive(ready.get('aspectRevision')) and positive(ready.get('recipeRevision'))
            and re.fullmatch(r'[0-9a-f]{64}', ready.get('researchCatalogDigest', '')), 'Three accepted initial snapshots are missing')
    return ready


def observations(report):
    require(report.get('passed') is True and not report.get('failures'), 'Final JEI runtime audit failed')
    history = report.get('observations', [])
    require(bool(history), 'Runtime observation history is empty')
    previous = 0
    for row in history:
        number = row.get('observation')
        require(positive(number) and number > previous, 'Runtime observations must have increasing identities')
        previous = number
        require(row.get('passed') is True and not row.get('failures'), f'Runtime observation {number} failed')
        require(row.get('memoryConnection') is True and row.get('acceptedSnapshotsReady') is True
                and all(positive(row.get(key)) for key in ('connectionGeneration', 'aspectRevision', 'recipeRevision', 'knowledgeGeneration')),
                f'Observation {number} lacks actual owner snapshots')
        categories = row.get('categories', [])
        require(row.get('registeredCategoryCount') == 5 and len(categories) == 5
                and {category.get('type') for category in categories} == {'thaumcraft:arcane_shaped', 'thaumcraft:crucible',
                    'thaumcraft:infusion', 'thaumcraft:aspect_combinations', 'thaumcraft:item_aspects'}
                and all(category.get('registered') is True for category in categories), f'Observation {number} lacks JEI categories')
        for kind, source_key in [('arcane', 'acceptedArcaneSources'), ('crucible', 'acceptedCrucibleSources'), ('infusion', 'acceptedInfusionSources')]:
            counts = row.get('recipeCounts', {}).get(kind, {})
            source_ids = row.get(kind + 'SourceIds', [])
            require(positive(row.get(source_key)) and positive(counts.get('currentIncludingHidden'))
                    and len(source_ids) == len(set(source_ids)) == row[source_key] == counts.get('distinctSourceIds')
                    and counts['currentIncludingHidden'] >= len(source_ids)
                    and type(counts.get('visible')) is int and counts['visible'] >= 0
                    and counts['visible'] == counts.get('expectedVisible'), f'Observation {number} has incomplete {kind} recipes')
        dynamic = row.get('dynamicInfusion', {})
        require(dynamic.get('runicSourcePresent') is True, f'Observation {number} lacks runic upgrades')
        for kind, count in [('enchantmentSources', 8), ('tagSources', 6)]:
            values = dynamic.get(kind, [])
            require(len(values) == count and len({value.get('sourceId') for value in values}) == count
                    and all(value.get('represented') is True for value in values), f'Observation {number} lacks dynamic infusion {kind}')
        triple = row.get('tripleMeat', {})
        source_count = triple.get('nativeSourceCount')
        require(positive(row.get('tripleMeatRevision')) and positive(source_count), f'Observation {number} omitted native triple meat crafting')
        expected = source_count * 20
        require(all(triple.get(key) == expected for key in ('expectedCurrentProjections', 'currentProjections', 'visibleProjections',
                'sourceValidatedTuples', 'bookmarkRoundtrips', 'outputFocusCountIncludingHidden')),
                f'Observation {number} does not validate all actual triple meat tuples/bookmarks/output focus')
        require(triple.get('allGenerationsIncludingHidden', 0) >= expected, f'Observation {number} lacks indexed triple meat projections')
        layouts = row.get('layouts', [])
        require(bool(layouts) and all(layout.get('created') is True and layout.get('extrasCreatedAndTicked') is True
                and layout.get('inputSlots') == layout.get('expectedInputSlots')
                and layout.get('outputSlots') == layout.get('expectedOutputSlots') for layout in layouts), f'Observation {number} has invalid recipe layouts')
        bookmarks = row.get('bookmarks', [])
        require(bool(bookmarks) and all(bookmark.get('roundtripCurrentHolder') is True for bookmark in bookmarks), f'Observation {number} has broken bookmarks')
    require(report.get('observation') == history[-1].get('observation'), 'Final audit is not the latest recorded observation')
    return {row['observation']: row for row in history}


def validate_with_jei(scenario, audit, directory):
    fresh = fresh_world(scenario, directory)
    require(scenario.get('withJei') is True, 'With-JEI scenario unexpectedly used the without-JEI branch')
    names = ['fresh-private-world-ready', 'initial-research-hiding', 'hide-disabled-live-runtime', 'eight-rendered-jei-screens',
             'actual-owner-knowledge-s2c-unlock', 'live-api-add-accepted', 'live-api-mutable-cost-update-accepted',
             'live-api-result-replacement-accepted', 'live-api-delete-accepted-and-cleaned',
             'actual-datapack-reload-and-bookmark-generation', 'actual-disconnect-reconnect-new-owner-connection',
             'real-client-transfer-and-paid-take']
    evidence = steps(scenario, names)
    history = observations(audit)
    audit_names = [names[index] for index in (1, 2, 4, 9, 10)]
    last_number = 0
    for name in audit_names:
        details = evidence[name]
        number = details.get('observation')
        require(number in history and number > last_number, f'Step {name} lacks a new actual runtime observation')
        actual = history[number]
        require(details.get('passed') is True and all(details.get(key) == actual.get(key) for key in
                ('connectionGeneration', 'aspectRevision', 'recipeRevision', 'knowledgeDigest', 'hideUnknownResearch')),
                f'Step {name} differs from the actual runtime report')
        last_number = number
    initial, disabled, unlocked, reload, reconnect = [evidence[name] for name in audit_names]
    require(initial.get('hideUnknownResearch') is True and disabled.get('hideUnknownResearch') is False
            and unlocked.get('hideUnknownResearch') is True, 'Live research-hiding toggles were not observed')
    require(positive(unlocked.get('fixtureResearchKeysGranted')) and unlocked.get('knowledgeDigest') != initial.get('knowledgeDigest'),
            'Owner research unlock did not produce a new received knowledge state')
    for kind in ('arcane', 'crucible', 'infusion'):
        require(disabled['recipeCounts'][kind]['visible'] == disabled['recipeCounts'][kind]['currentIncludingHidden'],
                f'Disabled research hiding did not expose current {kind} recipes')
        require(unlocked['recipeCounts'][kind]['visible'] > initial['recipeCounts'][kind]['visible'], f'Research unlock did not expose {kind} recipes')
    require(initial['connectionGeneration'] == disabled['connectionGeneration'] == unlocked['connectionGeneration'] == reload['connectionGeneration'],
            'Pre-reconnect stages unexpectedly changed the owner connection')
    require(reload['aspectRevision'] > unlocked['aspectRevision'] >= fresh['aspectRevision'], 'Actual datapack reload did not update received aspect metadata')
    require(reconnect['connectionGeneration'] > reload['connectionGeneration'], 'Disconnect/reconnect did not establish a new accepted owner connection')
    for name in (names[9], names[10]):
        require(bool(evidence[name].get('bookmarks'))
                and all(bookmark.get('previousTokenResolvesCurrentHolder') is True for bookmark in evidence[name]['bookmarks']),
                f'Step {name} did not rebind previous bookmarks to the actual current generation')
    previous_revision = unlocked['recipeRevision']
    for name, output, air in [(names[5], 'minecraft:diamond', 1), (names[6], 'minecraft:diamond', 2),
                              (names[7], 'minecraft:emerald', 2), (names[8], None, None)]:
        details = evidence[name]
        revision = details.get('acceptedRevision')
        present = output is not None
        require(positive(revision) and revision > previous_revision and details.get('sourceId') == 'thaumcraft:jei_review_api'
                and details.get('present') is present, f'Live API stage did not advance accepted metadata: {name}')
        require(not present or (details.get('result') == output and details.get('air') == air), f'Live API stage has wrong source output/cost: {name}')
        matches = [row for row in history.values() if row['connectionGeneration'] == initial['connectionGeneration'] and row['recipeRevision'] == revision]
        require(bool(matches), f'Live API stage lacks an actual indexed JEI observation: {name}')
        for row in matches:
            api = row.get('apiFixture', {})
            require(api.get('acceptedPresent') is present and api.get('jeiPresent') is present, f'Actual JEI API presence is stale: {name}')
            require(api.get('diamondCurrentFocusCountIncludingHidden') == int(output == 'minecraft:diamond')
                    and api.get('emeraldCurrentFocusCountIncludingHidden') == int(output == 'minecraft:emerald'), f'Actual JEI output focus is stale: {name}')
            if present:
                require(api.get('acceptedOutput') == api.get('jeiOutput') == output and api.get('acceptedAirAmount') == api.get('jeiAirAmount') == air
                        and api.get('indexedAmounts') == {'aer': air}, f'Actual JEI API result/aspect quantities differ: {name}')
            else:
                require(api.get('removedBookmarkRejected') is True, 'Removed API fixture bookmark still resolves')
        previous_revision = revision
    require(reload['recipeRevision'] >= previous_revision, 'Reload regressed the current recipe catalog')
    return dict(observations=len(history), initialConnection=initial['connectionGeneration'], reconnectedConnection=reconnect['connectionGeneration'],
                reloadAspectBefore=unlocked['aspectRevision'], reloadAspectAfter=reload['aspectRevision'], nativeTripleMeatProjections=audit['tripleMeat']['currentProjections'])


def validate_without_jei(scenario, directory):
    fresh_world(scenario, directory)
    require(scenario.get('withJei') is False, 'Without-JEI scenario actually loaded JEI')
    details = steps(scenario, ['fresh-private-world-ready', 'without-jei-native-client-and-three-accepted-snapshots'])[
        'without-jei-native-client-and-three-accepted-snapshots']
    require(details.get('jeiLoaded') is False and details.get('commonNativeRecipeClassesLoaded') is True
            and details.get('actualMemoryConnection') is True and positive(details.get('aspectRevision')) and positive(details.get('recipeRevision'))
            and re.fullmatch(r'[0-9a-f]{64}', details.get('researchCatalogDigest', '')), 'Without-JEI common classes/three accepted snapshots are unproven')


def contains(outer, inner):
    fields = ('x', 'y', 'width', 'height')
    require(all(type(rect.get(key)) is int for rect in (outer, inner) for key in fields)
            and all(rect['width'] > 0 and rect['height'] > 0 for rect in (outer, inner)), 'Invalid native GUI bounds')
    return inner['x'] >= outer['x'] and inner['y'] >= outer['y'] and inner['x'] + inner['width'] <= outer['x'] + outer['width'] \
        and inner['y'] + inner['height'] <= outer['y'] + outer['height']


def png(path):
    data = path.read_bytes()
    require(data.startswith(b'\x89PNG\r\n\x1a\n') and len(data) >= 1024, f'Invalid native screenshot: {path}')
    offset, size, image_data, ended = 8, None, False, False
    while offset < len(data):
        require(offset + 12 <= len(data), f'Truncated PNG chunk: {path}')
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        end = offset + 12 + length
        require(end <= len(data), f'Truncated PNG body: {path}')
        require(zlib.crc32(data[offset + 4:end - 4]) & 0xffffffff == struct.unpack('>I', data[end - 4:end])[0], f'Corrupted PNG CRC: {path}')
        if kind == b'IHDR':
            require(offset == 8 and length == 13 and size is None, f'Invalid PNG header: {path}')
            size = struct.unpack('>II', data[offset + 8:offset + 16])
        if kind == b'IDAT':
            image_data = True
        if kind == b'IEND':
            require(length == 0 and end == len(data), f'Incomplete PNG terminator: {path}')
            ended = True
        offset = end
    require(size is not None and image_data and ended and all(value > 0 for value in size), f'Incomplete screenshot: {path}')
    return dict(path=str(path.resolve()), bytes=len(data), width=size[0], height=size[1], sha256=digest(path))


def validate_window_probe(probe, original_scale, label):
    require(all(probe.get(key) == size for key, size in [('nativeFramebufferWidth', 1280), ('nativeFramebufferHeight', 720),
            ('cachedFramebufferWidth', 1280), ('cachedFramebufferHeight', 720), ('renderTargetWidth', 1280), ('renderTargetHeight', 720)]),
            f'Actual native framebuffer/cache/render target are not 1280x720: {label}')
    require(positive(probe.get('nativeWindowWidth')) and positive(probe.get('nativeWindowHeight'))
            and type(probe.get('guiWidth')) is int and probe['guiWidth'] >= 320
            and type(probe.get('guiHeight')) is int and probe['guiHeight'] >= 240
            and probe.get('iconified') is False and probe.get('visible') is True and probe.get('stableTicks') == 10,
            f'Actual native window was unavailable or unstable: {label}')
    require(type(probe.get('guiScalePreference')) is int and probe['guiScalePreference'] == original_scale,
            f'Native capture changed the configured GUI scale: {label}')


def validate_screens(report, directory):
    expected = [('01-arcane', 'thaumcraft:arcane_shaped'), ('02-crucible', 'thaumcraft:crucible'), ('03-infusion', 'thaumcraft:infusion'),
                ('04-aspect-combinations', 'thaumcraft:aspect_combinations'), ('05-item-aspects', 'thaumcraft:item_aspects'),
                ('06-infusion-enchantment', 'thaumcraft:infusion'), ('07-infusion-runic', 'thaumcraft:infusion'),
                ('08-infusion-tag', 'thaumcraft:infusion'), ('09-infusion-scroll', 'thaumcraft:infusion')]
    require(report.get('passed') is True and report.get('phase') == 'COMPLETE' and not report.get('failures'), 'Native screen capture did not complete')
    require(report.get('expectedCaptures') == report.get('completedRequiredCaptures') == 8 and report.get('supplementalCaptures') == 1
            and report.get('completedCaptures') == report.get('plannedNativeCaptures') == 9, 'Nine completed native screen captures are required')
    captures = report.get('captures', [])
    require(len(captures) == 9 and [(row.get('name'), row.get('category')) for row in captures] == expected, 'Native screenshot identities/categories are incomplete')
    recovery = report.get('nativeWindowRecovery', {})
    original_scale = recovery.get('originalGuiScalePreference')
    require(recovery.get('expectedNativeWidth') == 1280 and recovery.get('expectedNativeHeight') == 720
            and type(recovery.get('attempts')) is int and 0 <= recovery['attempts'] <= 3
            and recovery.get('stableTicksRequired') == recovery.get('stableTicks') == 10
            and type(original_scale) is int and original_scale >= 0 and recovery.get('guiScalePreferenceChanged') is False,
            'Native screenshot recovery did not preserve a stable real 1280x720 window and original GUI scale')
    validate_window_probe(recovery.get('lastProbe', {}), original_scale, 'completed native window recovery')
    screenshots = []
    for index, capture in enumerate(captures):
        name = capture['name']
        require(capture.get('saved') is True and capture.get('requiredCategoryCapture') is (index < 8)
                and capture.get('screenClass') == 'mezz.jei.gui.recipes.RecipesGui'
                and capture.get('actualNativeLayout') == 'mezz.jei.library.gui.recipes.RecipeLayout'
                and positive(capture.get('actualNativeSlotCount')) and capture.get('settledTicks', 0) >= capture.get('settleTicksRequired', 30) >= 30,
                f'Capture is not a settled actual JEI recipe screen: {name}')
        probe = capture.get('nativeWindowProbe', {})
        validate_window_probe(probe, original_scale, name)
        require(capture.get('width') == capture.get('pngWidth') == 1280 and capture.get('height') == capture.get('pngHeight') == 720
                and capture.get('guiWidth') == probe['guiWidth'] and capture.get('guiHeight') == probe['guiHeight'],
                f'Native screenshot dimensions differ from the actual stable window: {name}')
        geometry = capture.get('nativeGeometry', {})
        require(all(geometry.get(key) is True for key in ('layoutWithinScreen', 'layoutWithinPanel', 'scrollViewportsWithinLayout')),
                f'Capture lacks complete actual native bounds: {name}')
        screen, panel, border, layout = [geometry.get(key, {}) for key in ('screen', 'panel', 'border', 'layout')]
        require(screen.get('x') == screen.get('y') == 0 and screen.get('width') == capture.get('guiWidth') and screen.get('height') == capture.get('guiHeight')
                and contains(screen, border) and contains(panel, border) and contains(border, layout), f'Native recipe card is cropped: {name}')
        for viewport in geometry.get('scrollViewports', []):
            require(viewport.get('withinLayout') is True and contains(layout, viewport.get('bounds', {})), f'Native scroll viewport is cropped: {name}')
        if index in (3, 4):
            require(capture.get('focusMethod') == 'automated-native-JEI-API' and capture.get('actualNativeFocusPreserved') is True
                    and capture.get('focusRole') == ('OUTPUT' if index == 3 else 'INPUT'), f'Native R/U focus role was not preserved: {name}')
        path = directory / 'screenshots' / (name + '.png')
        require(Path(capture.get('file', '')).resolve() == path.resolve(), f'Capture belongs to another run: {name}')
        screenshot = png(path)
        require(screenshot['bytes'] == capture.get('bytes') and screenshot['width'] == capture.get('width') and screenshot['height'] == capture.get('height'),
                f'Native screenshot pixels differ from capture report: {name}')
        screenshots.append(screenshot)
    scroll = captures[-1].get('scroll', {})
    require(scroll.get('method') == 'native-Screen.mouseScrolled' and positive(scroll.get('hiddenAmount'))
            and scroll.get('handled') is True and scroll.get('viewportMoved') is True
            and 0 <= scroll.get('offsetBefore', -1) < scroll.get('offsetAfter', -1) <= 1, 'Ninth screenshot lacks actual native wheel/viewport movement')
    viewport = scroll.get('viewport', {})
    require(contains(captures[-1]['nativeGeometry']['layout'], viewport)
            and any(row.get('bounds') == viewport and row.get('widget') == scroll.get('widget') for row in captures[-1]['nativeGeometry'].get('scrollViewports', [])),
            'Native wheel probe does not target an observed scroll viewport')
    require(viewport['x'] <= scroll.get('mouseX', -1) < viewport['x'] + viewport['width']
            and viewport['y'] <= scroll.get('mouseY', -1) < viewport['y'] + viewport['height'], 'Native wheel pointer is outside the tested viewport')
    return screenshots


def validate_transfer(report, directory):
    require(report.get('passed') is True and not report.get('failures') and report.get('memoryConnection') is True
            and report.get('singleAndMaximumTransfers') is True and report.get('paidCraftViaClientPacket') is True
            and report.get('jeiStarts') == report.get('jeiCompletions') == 2, 'Two actual successful JEI transfers and a paid craft are required')
    require(Path(report.get('privateWorld', '')).resolve() == (directory / 'saves/jei-review').resolve(), 'Transfer evidence belongs to another world')
    checks = report.get('checks', [])
    before = [(index, row) for index, row in enumerate(checks) if row.get('event') == 'before']
    after = [(index, row) for index, row in enumerate(checks) if row.get('event') == 'after']
    require(len(before) == len(after) == 2 and [row.get('maximum') for _, row in before] == [False, True], 'Actual transfer single/maximum contexts are missing')
    ids = []
    for (start, begin), (end, complete) in zip(before, after):
        identity = begin.get('transferId')
        require(positive(identity) and identity == complete.get('transferId') and start < end
                and begin.get('context') == 'mezz.jei.common.transfer.RecipeTransferContext' and complete.get('result') == 'SUCCESS',
                'JEI context did not receive its actual successful server completion')
        ids.append(identity)
    require(len(set(ids)) == 2 and after[0][0] < before[1][0], 'Transfer callbacks are duplicated or overlap')
    for event in ('single', 'maximum', 'paid-client-craft'):
        require(sum(row.get('event') == event for row in checks) == 1, f'Missing actual server inventory/crafting check: {event}')
    render_indices = []
    for maximum, event, screenshot in [(False, 'client-single-arrangement', 'jei_transfer_single.png'),
                                       (True, 'client-maximum-arrangement', 'jei_transfer_max.png')]:
        matches = [(index, row) for index, row in enumerate(checks) if row.get('event') == event]
        require(len(matches) == 1, f'Missing post-transfer rendered actual workbench state: {event}')
        index, row = matches[0]
        render_indices.append(index)
        require(row.get('maximum') is maximum and row.get('screenshot') == screenshot
                and row.get('screen') == 'thaumcraft.client.gui.GuiArcaneWorkbench' and positive(row.get('menuId'))
                and type(row.get('stateId')) is int and row['stateId'] >= 0
                and type(row.get('renderWaitTicks')) is int and row['renderWaitTicks'] >= 30
                and type(row.get('renderedWorkbenchFrames')) is int and row['renderedWorkbenchFrames'] >= 3
                and row.get('carriedEmpty') is True and type(row.get('availableVis')) in (int, float) and row['availableVis'] >= 20,
                f'Transfer screenshot was not taken after the actual arranged workbench rendered: {event}')
        table, inventory = row.get('tableSlots', []), row.get('inventorySlots', [])
        require(len(table) == 15 and [slot.get('slot') for slot in table] == list(range(15))
                and len(inventory) == 36 and [slot.get('slot') for slot in inventory] == list(range(36)),
                f'Rendered client slot snapshot is incomplete: {event}')
        require(all(type(slot.get('count')) is int and slot['count'] >= 0 and isinstance(slot.get('item'), str)
                for slot in table + inventory), f'Invalid rendered client stack metadata: {event}')
        if maximum:
            expected = {1: ('thaumcraft:vis_resonator', 32), 3: ('thaumcraft:plank_greatwood', 32),
                        5: ('thaumcraft:plank_greatwood', 32), 6: ('minecraft:iron_ingot', 32), 8: ('minecraft:iron_ingot', 32),
                        9: ('thaumcraft:crystal_essence', 64), 11: ('thaumcraft:crystal_essence', 7), 13: ('thaumcraft:crystal_essence', 64)}
            crystals = {9: 'aer', 11: 'aqua', 13: 'ordo'}
            require(sum(slot['count'] for slot in inventory if slot['item'] == 'thaumcraft:vis_resonator') == 32,
                    'Rendered maximum transfer lost the leftover resonators')
        else:
            expected = {slot: ('minecraft:gold_ingot', 1) for slot in (1, 3, 5, 7)}
            expected[4] = ('minecraft:glass_pane', 1)
            crystals = {9 + index: aspect for index, aspect in enumerate(('aer', 'ignis', 'aqua', 'terra', 'ordo', 'perditio'))}
            expected.update({slot: ('thaumcraft:crystal_essence', 1) for slot in crystals})
            require(sum(slot['count'] for slot in inventory if slot['item'] == 'minecraft:gold_ingot') == 4
                    and sum(slot['count'] for slot in inventory if slot['item'] == 'minecraft:glass_pane') == 1
                    and any(slot['item'] == 'minecraft:diamond' and slot['count'] == 3
                            and slot.get('customName') == 'Private JEI transfer fixture' for slot in inventory),
                    'Rendered single transfer lost leftovers or the displaced named diamond stack')
        for slot in table:
            number = slot['slot']
            require((slot['item'], slot['count']) == expected.get(number, ('minecraft:air', 0)), f'Actual rendered transfer grid differs from the acknowledged arrangement: {event}/{number}')
            if number in crystals:
                require(slot.get('aspect') == crystals[number], f'Actual rendered crystal component is wrong: {event}/{number}')
    paid_index, paid = next((index, row) for index, row in enumerate(checks) if row.get('event') == 'paid-client-craft')
    require(after[0][0] < render_indices[0] < paid_index < before[1][0] < after[1][0] < render_indices[1]
            and paid.get('sourceCost') == 20
            and paid.get('visBefore') == 60 and paid.get('visAfter') == 40 and paid.get('visSpent') == 20
            and bool(paid.get('auraFixture')), 'Paid client craft did not consume exact source vis in the controlled live aura fixture')
    screenshots = [png(directory / 'screenshots' / name) for name in ('jei_transfer_single.png', 'jei_transfer_max.png')]
    require(all(image['width'] == 1280 and image['height'] == 720 for image in screenshots), 'Actual transfer screenshots are not native 1280x720 captures')
    return screenshots


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-log', type=Path, required=True)
    parser.add_argument('--server-log', type=Path, required=True)
    parser.add_argument('--client-dir', type=Path, required=True)
    parser.add_argument('--without-server-log', type=Path, required=True)
    parser.add_argument('--without-client-dir', type=Path, required=True)
    parser.add_argument('--runtime-baseline-jar', type=Path,
                        help='Reuse prior server/without-JEI evidence only if this prior JAR matches every final entry except the transfer client test fixture')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    port = root / 'port'
    logs = {}
    for name in ('build_log', 'server_log', 'without_server_log'):
        path = getattr(args, name).resolve()
        content = path.read_text(encoding='utf-8-sig', errors='replace')
        if 'BUILD SUCCESSFUL' not in content:
            raise ValueError(f'Missing successful build: {path}')
        if 'server' in name and 'All 11 required tests passed' not in content:
            raise ValueError(f'Missing actual 11-test completion: {path}')
        logs[name] = dict(path=str(path), sha256=digest(path), requiredGameTests=11 if 'server' in name else None)

    suites = []
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in sorted((port / 'build/test-results/test').glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        row = dict(name=suite.attrib['name'], sha256=digest(path))
        for key in totals:
            row[key] = int(suite.attrib.get(key, 0))
            totals[key] += row[key]
        suites.append(row)
    if not suites or totals['failures'] or totals['errors']:
        raise ValueError(f'Unsuccessful full unit suite: {totals}')

    jar = port / 'build/libs/thaumcraft-6.1.0-port.jar'
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate JAR entries')
        matched = {}
        for label, source in [('classes', 'build/classes/java/main'), ('resources', 'build/resources/main')]:
            base = port / source
            files = [p for p in base.rglob('*') if p.is_file()]
            for path in files:
                relative = path.relative_to(base).as_posix()
                if archive.read(relative) != path.read_bytes():
                    raise ValueError(f'JAR mismatch: {relative}')
            matched[label] = len(files)
        metadata = archive.read('META-INF/neoforge.mods.toml').decode()
        if not re.search(r'modId\s*=\s*"jei"', metadata) or '[19.52.0,)' not in metadata:
            raise ValueError('JEI API 19.52 optional dependency missing')
        jei = [name for name in names if name.startswith('thaumcraft/client/jei/') and name.endswith('.class')]

    reports = []
    loaded = {}
    for directory, files in [(args.client_dir, ['jei-review-scenario.json', 'jei-audit.json', 'jei-screens.json', 'jei-transfer.json']),
                             (args.without_client_dir, ['jei-review-scenario.json'])]:
        for filename in files:
            path = directory / filename
            report = json.loads(path.read_text(encoding='utf-8-sig'))
            if report.get('passed') is not True:
                raise ValueError(f'Runtime report failed: {path}')
            if filename == 'jei-audit.json' and any(not row.get('passed') for row in report.get('observations', [])):
                raise ValueError(f'An earlier runtime observation failed: {path}')
            loaded[(directory.resolve(), filename)] = report
            reports.append(dict(path=str(path.resolve()), sha256=digest(path), evidence=report))

    client = args.client_dir.resolve()
    without = args.without_client_dir.resolve()
    require(client != without, 'With-JEI and without-JEI evidence must come from separate fresh runs')
    for directory in (client, without):
        require(directory.name == 'client' and directory.parent.parent == (port / 'build/jei-review').resolve()
                and re.fullmatch(r'[A-Za-z0-9_-]{1,80}', directory.parent.name), 'Runtime evidence is outside a private review run')
    require(args.build_log.resolve().parent == client.parent, 'Final test/JAR build log must belong to the fresh final client cohort')
    baseline_comparison = None
    if args.runtime_baseline_jar is not None:
        baseline = args.runtime_baseline_jar.resolve()
        require(baseline.is_file() and baseline.parent.parent == (port / 'build/jei-review').resolve()
                and re.fullmatch(r'[A-Za-z0-9_-]{1,80}', baseline.parent.name), 'Runtime baseline JAR must belong to its preserved private review cohort')
        baseline_comparison = compare_runtime_baseline(baseline, jar)
        source_cohort = baseline.parent
    else:
        source_cohort = client.parent
    require(without.parent.name == source_cohort.name + '-nojei', 'Without-JEI evidence does not belong to the proven runtime baseline cohort')
    require(args.server_log.resolve().parent == args.without_server_log.resolve().parent == source_cohort,
            'Reused server logs do not belong to the proven runtime baseline cohort')
    runtime_checks = validate_with_jei(loaded[(client, 'jei-review-scenario.json')], loaded[(client, 'jei-audit.json')], client)
    validate_without_jei(loaded[(without, 'jei-review-scenario.json')], without)
    screenshots = validate_screens(loaded[(client, 'jei-screens.json')], client)
    screenshots.extend(validate_transfer(loaded[(client, 'jei-transfer.json')], client))
    output = dict(schema=2, stage=12, scope='optional ThaumicJEI feature integration and automated game acceptance',
                  observedAt=datetime.now(timezone.utc).isoformat(), projectRoot=str(root), unitTotals=totals,
                  suites=suites, jar=dict(path=str(jar), bytes=jar.stat().st_size, sha256=digest(jar), entries=len(names),
                                         duplicateEntries=0, matched=matched, jeiClasses=len(jei)),
                  gameTestsWithJei=11, gameTestsWithoutJei=11, runtimeChecks=runtime_checks, runtimeReports=reports, screenshots=screenshots,
                  buildAndRuntimeLogProvenance=logs, runtimeBaselineComparison=baseline_comparison,
                  runtimeCohorts=dict(finalClient=str(client), finalBuild=str(args.build_log.resolve()),
                                     serverAndWithoutJeiSource=str(source_cohort), withoutJeiClient=str(without),
                                     reusedServerAndWithoutJeiEvidence=source_cohort != client.parent),
                  bounds='1 Gradle worker, 768 MiB Gradle, 2 CPU; game JVM 1 GiB/2 CPU',
                  limits=['Client scenarios use a fresh integrated server; a separate remote multiplayer cohort is outside this JEI review.',
                          'Automated native screens, focused lookups and actual viewport wheel movement are checked; manual R/U keyboard presses are not exercised.',
                          'This is not a manual visual review of all ported content.'])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(output=str(args.output), unitTotals=totals, jar=output['jar'], screenshots=len(screenshots)), ensure_ascii=False))


if __name__ == '__main__':
    main()
