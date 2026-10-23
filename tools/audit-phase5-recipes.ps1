param([ValidateSet('Summary', 'Gaps', 'Entries', 'Json')][string]$View = 'Summary', [int]$Offset = 0, [int]$Limit = 0)
$ErrorActionPreference = 'Stop'
# Read-only, no Minecraft/bootstrap/Gradle/JVM. This is a coverage inventory, NOT a recipe generator.
$taskRoot = Split-Path -Parent $PSScriptRoot
$asset = Join-Path $taskRoot 'port/src/generated/resources/assets/thaumcraft/research_recipe_display/catalog.json'
$expectedHash = 'A21D695F4E39E03E665300D35A1C3D53F4573D2579B0D53006A20A5AC3BD2CB2'
if ((Get-FileHash -LiteralPath $asset -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Original display oracle identity changed; audit its provenance first.' }
$original = Get-Content -Raw -LiteralPath $asset | ConvertFrom-Json
# These four genuine definitions are conditional and absent from the vanilla-only original capture.
foreach ($metal in @(@('copper',2),@('tin',3),@('silver',4),@('lead',5))) {
    $original.recipes += [pscustomobject]@{
        id = 'thaumcraft:metal_purification_' + $metal[0]; kind = 'CRUCIBLE'; research = 'METALPURIFICATION'
        sourceClass = 'thaumcraft.api.crafting.CrucibleRecipe'; output = 'thaumcraft:cluster;1;' + $metal[1]
        ingredients = @(); conditionalSourceDefinition = $true
    }
}
$registryIds = [System.Collections.Generic.HashSet[string]]::new()
foreach ($config in @('ConfigItems', 'ConfigBlocks')) {
    $source = Get-Content -Raw -LiteralPath (Join-Path $taskRoot "port/src/main/java/thaumcraft/common/config/$config.java")
    $source = [regex]::Replace($source, '(?s)/\*.*?\*/', '')
    $source = [regex]::Replace($source, '(?m)//[^\r\n]*$', '')
    foreach ($match in [regex]::Matches($source, '(?:simple|register|gear|crystal)\("([a-z0-9_]+)"\s*[,)]')) {
        [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value)
    }
    # Only dynamically expanded families explicitly present in those actual registration loops.
    foreach ($prefix in @('candle', 'nitor')) {
        if ($source.Contains('"' + $prefix + '_" + dye.getName()')) {
            foreach ($color in @('white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black')) {
                [void]$registryIds.Add("thaumcraft:${prefix}_$color")
            }
        }
    }
    if ($source.Contains('"curio_" + variant')) {
        foreach ($variant in @('arcane','preserved','ancient','eldritch','knowledge','twisted','rites')) { [void]$registryIds.Add("thaumcraft:curio_$variant") }
    }
    if ($source.Contains('"celestial_notes_" + note.variantName()')) {
        foreach ($variant in @('sun','stars_1','stars_2','stars_3','stars_4','moon_1','moon_2','moon_3','moon_4','moon_5','moon_6','moon_7','moon_8')) {
            [void]$registryIds.Add("thaumcraft:celestial_notes_$variant")
        }
    }
}
$groupRegistrations = @('essentia/TransportRegistries','devices/ProcessingRegistries','devices/DeviceRegistries','devices/MirrorRegistries','crafting/FocalRegistries')
foreach ($group in $groupRegistrations) {
    $groupSource = Get-Content -Raw -LiteralPath (Join-Path $taskRoot "port/src/main/java/thaumcraft/common/tiles/$group.java")
    foreach ($match in [regex]::Matches($groupSource, '(?:\.put|Map\.of)\("([a-z0-9_]+)"\s*,\s*BlocksTC\.')) {
        [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value)
    }
}
# Stage7 actual item factory maps, only when ConfigItems really calls and registers them.
$itemConfig = Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'port/src/main/java/thaumcraft/common/config/ConfigItems.java')
foreach ($factory in @('tools/EquipmentRegistries','armor/WearableRegistries','casters/CasterRegistries')) {
    $factoryPath = Join-Path $taskRoot "port/src/main/java/thaumcraft/common/items/$factory.java"
    $factoryName = ($factory -split '/')[-1]
    if (!(Test-Path -LiteralPath $factoryPath) -or !$itemConfig.Contains("$factoryName.createItems()")) { continue }
    $factorySource = Get-Content -Raw -LiteralPath $factoryPath
    $factorySource = [regex]::Replace($factorySource, '(?s)/\*.*?\*/|(?m)//[^\r\n]*$', '')
    foreach ($match in [regex]::Matches($factorySource, '\.put\("([a-z0-9_]+)"\s*,')) { [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value) }
    # Exact native ArmorItem.Type loop and its source suffix expression, not an ID allowlist.
    if ($factorySource.Contains('List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS)') -and
        $factorySource.Contains('"helm" : type == ArmorItem.Type.CHESTPLATE ? "chest" : "legs"')) {
        foreach ($match in [regex]::Matches($factorySource, '\.put\("([a-z0-9_]+_)"\s*\+\s*suffix')) {
            foreach ($suffix in @('helm','chest','legs')) { [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value + $suffix) }
        }
    }
}
# Stage8 factories are counted only when the common owners actually consume them.
$blockConfig = Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'port/src/main/java/thaumcraft/common/config/ConfigBlocks.java')
foreach ($factory in @('entities/Mob8NaturalRegistries','entities/construct/Mob8ConstructRegistries','entities/Mob8MiscRegistries','entities/Mob8SpawnEggs','blocks/basic/BannerRegistries','blocks/world/taint/Taint8Registries')) {
    $factoryPath = Join-Path $taskRoot "port/src/main/java/thaumcraft/common/$factory.java"
    $factoryName = ($factory -split '/')[-1]
    $method = if ($factory.StartsWith('blocks/')) { 'createBlocks' } else { 'createItems' }
    $owner = if ($method -eq 'createBlocks') { $blockConfig } else { $itemConfig }
    if (!(Test-Path -LiteralPath $factoryPath) -or !$owner.Contains("$factoryName.$method()")) { continue }
    $factorySource = Get-Content -Raw -LiteralPath $factoryPath
    $factorySource = [regex]::Replace($factorySource, '(?s)/\*.*?\*/|(?m)//[^\r\n]*$', '')
    $body = [regex]::Match($factorySource, "(?s)$method\(\)\s*\{(.*?)(?=\r?\n\s*(?:@|public |private |}))").Groups[1].Value
    foreach ($match in [regex]::Matches($body, '(?:\.put|Map\.of)\("([a-z0-9_]+)"\s*,')) { [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value) }
    if ($factoryName -eq 'Mob8MiscRegistries') {
        foreach ($match in [regex]::Matches($body, '"([a-z0-9_]+)"\s*,\s*ItemsTC\.')) { [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value) }
    }
    if ($factoryName -eq 'Mob8SpawnEggs') {
        foreach ($match in [regex]::Matches($body, 'egg\(result,"([a-z0-9_]+)"\s*,')) { [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value + '_spawn_egg') }
    }
}
function Get-JavaFactoryBody([string]$source, [string]$method) {
    $head = [regex]::Match($source, "\b$method\(\)\s*\{")
    if (!$head.Success) { return '' }
    $start = $head.Index + $head.Length
    $depth = 1; $quoted = $false; $escaped = $false
    for ($index = $start; $index -lt $source.Length; $index++) {
        $character = $source[$index]
        if ($quoted) {
            if ($escaped) { $escaped = $false }
            elseif ($character -eq '\') { $escaped = $true }
            elseif ($character -eq '"') { $quoted = $false }
            continue
        }
        if ($character -eq '"') { $quoted = $true; continue }
        if ($character -eq '{') { $depth++ }
        elseif ($character -eq '}') {
            $depth--
            if ($depth -eq 0) { return $source.Substring($start, $index - $start) }
        }
    }
    throw "Unbalanced factory body: $method"
}
# Stage9: count genuine owner-consumed factories, including source registry-key expansion.
$golemFactoryPath = Join-Path $taskRoot 'port/src/main/java/thaumcraft/common/tiles/crafting/Golem9BuilderRegistries.java'
if (Test-Path -LiteralPath $golemFactoryPath) {
    $golemFactory = Get-Content -Raw -LiteralPath $golemFactoryPath
    foreach ($method in @('createItems','createBlocks')) {
        $owner = if ($method -eq 'createItems') { $itemConfig } else { $blockConfig }
        if (!$owner.Contains("Golem9BuilderRegistries.$method()")) { continue }
        $body = Get-JavaFactoryBody $golemFactory $method
        foreach ($match in [regex]::Matches($body, '\.put\("([a-z0-9_]+)"\s*,')) {
            [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value)
        }
    }
}
$sealFactoryPath = Join-Path $taskRoot 'port/src/main/java/thaumcraft/common/golems/seals/Golem9SealRegistries.java'
if ((Test-Path -LiteralPath $sealFactoryPath) -and $itemConfig.Contains('Golem9SealRegistries.createItems()')) {
    $sealFactory = Get-Content -Raw -LiteralPath $sealFactoryPath
    $body = Get-JavaFactoryBody $sealFactory 'createItems'
    foreach ($match in [regex]::Matches($body, '\.put\("([a-z0-9_]+)"\s*,')) {
        [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value)
    }
    if ($body.Contains('"seal_"+id.getPath()') -and $body.Contains('SealHandler.getRegisteredSeals()')) {
        $registration = Get-JavaFactoryBody $sealFactory 'registerTypes'
        foreach ($match in [regex]::Matches($registration, 'registerSeal\(new (Seal[A-Za-z]+)\(\)\)')) {
            $sealSource = Get-Content -Raw -LiteralPath (Join-Path (Split-Path $sealFactoryPath) ($match.Groups[1].Value + '.java'))
            $key = [regex]::Match($sealSource, 'getKey\(\)\s*\{\s*return\s*"thaumcraft:([a-z0-9_]+)"')
            if (!$key.Success) { throw ('Missing proven seal registry key: ' + $match.Groups[1].Value) }
            [void]$registryIds.Add('thaumcraft:seal_' + $key.Groups[1].Value)
        }
    }
}

# Stage10: genuine factory bodies, only if the shared owner actually registers their products.
foreach ($factory in @('tiles/crafting/Phase10RiftRegistries','blocks/misc/DeathFluidRegistries','items/misc/Eldritch10ItemRegistries')) {
    $factoryPath = Join-Path $taskRoot "port/src/main/java/thaumcraft/common/$factory.java"
    $factoryName = ($factory -split '/')[-1]
    $method = if ($factoryName -eq 'Phase10RiftRegistries') { 'createBlocks' } else { 'createItems' }
    $owner = if ($method -eq 'createBlocks') { $blockConfig } else { $itemConfig }
    if (!(Test-Path -LiteralPath $factoryPath) -or !$owner.Contains("$factoryName.$method()")) { continue }
    $body = Get-JavaFactoryBody (Get-Content -Raw -LiteralPath $factoryPath) $method
    foreach ($match in [regex]::Matches($body, '(?:\.put|Map\.of)\("([a-z0-9_]+)"\s*,')) {
        [void]$registryIds.Add('thaumcraft:' + $match.Groups[1].Value)
    }
}

# Only the actual flattened food registration loop; do not invent later-stage items.
# Stage11 actual owner-consumed banner loop. Never count planned variants without real BlockBannerTC.
$taskBannerFactory = Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'port/src/main/java/thaumcraft/common/blocks/basic/BannerRegistries.java')
$taskBannerBody = Get-JavaFactoryBody $taskBannerFactory 'createBlocks'
if ($blockConfig.Contains('BannerRegistries.createBlocks()') -and
    $taskBannerBody -match 'for\s*\(DyeColor dye:DyeColor\.values\(\)\)' -and
    $taskBannerBody.Contains('new BlockBannerTC(dye)') -and $taskBannerBody.Contains('BlocksTC.banners.put(dye,banner)') -and
    $taskBannerBody.Contains('blocks.put("banner_"+dye.getName(),banner)')) {
    foreach ($color in @('white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black')) {
        [void]$registryIds.Add("thaumcraft:banner_$color")
    }
}
$foodSource = Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'port/src/main/java/thaumcraft/common/items/consumables/ItemChunksEdible.java')
if ($foodSource -match 'VARIANTS\s*=\s*List\.of\(([^)]+)\)') {
    foreach ($match in [regex]::Matches($Matches[1], '"([a-z0-9_]+)"')) {
        [void]$registryIds.Add('thaumcraft:chunk_' + $match.Groups[1].Value)
    }
}
$fluidSource = Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'port/src/main/java/thaumcraft/common/blocks/misc/PurifyingFluidRegistries.java')
if ($fluidSource.Contains('new BucketItem(SOURCE') -and $fluidSource.Contains('thaumcraft:purifying_fluid_bucket')) { [void]$registryIds.Add('thaumcraft:purifying_fluid_bucket') }
$families = @{
    ingot = @('thaumium','void','brass')
    nugget = @('iron','copper','tin','silver','lead','quicksilver','thaumium','void','brass','quartz','rareearth')
    cluster = @('iron','gold','copper','tin','silver','lead','cinnabar','quartz')
    plate = @('brass','iron','thaumium','void')
    mind = @('clockwork','biothaumic')
    module = @('vision','aggression')
    curio = @('arcane','preserved','ancient','eldritch','knowledge','twisted','rites')
    phial = @('empty','filled')
    turret = @('basic','advanced','bore')
    seal = @('blank','pickup','pickup_advanced','fill','fill_advanced','empty','empty_advanced','harvest','butcher','guard','guard_advanced','lumber','breaker','use','provider','stock','breaker_advanced')
    chunks = @('beef','chicken','pork','fish','rabbit','mutton')
    celestial_notes = @('sun','stars_1','stars_2','stars_3','stars_4','moon_1','moon_2','moon_3','moon_4','moon_5','moon_6','moon_7','moon_8')
}
$baubles = @('amulet_mundane','ring_mundane','girdle_mundane','ring_apprentice','amulet_fancy','ring_fancy','girdle_fancy')
function Resolve-TcToken([string]$token) {
    $fields = $token.Split(';', 4)
    $item = $fields[0]
    if (!$item.StartsWith('thaumcraft:')) { return @() }
    $base = $item.Substring(11)
    $metadata = if ($fields.Length -ge 3) { [int]$fields[2] } else { 0 }
    if ($families.ContainsKey($base)) {
        $family = @($families[$base])
        $modernBase = if ($base -eq 'chunks') { 'chunk' } else { $base }
        if ($metadata -eq 32767) { return @($family | ForEach-Object { "thaumcraft:${modernBase}_$_" }) }
        if ($metadata -ge 0 -and $metadata -lt $family.Count) { return @("thaumcraft:${modernBase}_" + $family[$metadata]) }
        return @($item + '#unmapped-meta-' + $metadata)
    }
    if ($base -eq 'baubles' -and $metadata -ge 0 -and $metadata -lt $baubles.Count) { return @('thaumcraft:' + $baubles[$metadata]) }
    # Charged vis amulet is not the already ported stone/meta0 amulet.
    if ($base -eq 'amulet_vis' -and $metadata -eq 1) { return @('thaumcraft:amulet_vis_crafted') }
    # wand_workbench is the source focal manipulator, NOT arcane_workbench. No false alias.
    $base = $base.Replace('nitor_lightblue','nitor_light_blue').Replace('nitor_silver','nitor_light_gray')
    $base = $base.Replace('candle_lightblue','candle_light_blue').Replace('candle_silver','candle_light_gray')
    $base = $base.Replace('banner_lightblue','banner_light_blue').Replace('banner_silver','banner_light_gray')
    return @('thaumcraft:' + $base)
}
$native = @{}
$duplicates = [System.Collections.Generic.List[string]]::new()
foreach ($resourceRoot in @('port/src/generated/resources/data/thaumcraft/recipe', 'port/src/main/resources/data/thaumcraft/recipe')) {
    $path = Join-Path $taskRoot $resourceRoot
    if (!(Test-Path -LiteralPath $path)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $path -Filter '*.json' -File) {
        $id = 'thaumcraft:' + $file.BaseName
        if ($native.ContainsKey($id)) { $duplicates.Add($id) }
        $native[$id] = Get-Content -Raw -LiteralPath $file.FullName | ConvertFrom-Json
    }
}
function Find-NativeTcItems($node, [System.Collections.Generic.HashSet[string]]$found) {
    if ($null -eq $node -or $node -is [string] -or $node -is [ValueType]) { return }
    if ($node -is [Array]) { foreach ($child in $node) { Find-NativeTcItems $child $found }; return }
    foreach ($property in $node.PSObject.Properties) {
        if ($property.Name -eq 'neoforge:conditions') { continue }
        if ($property.Name -in @('id', 'item') -and $property.Value -is [string] -and $property.Value.StartsWith('thaumcraft:')) {
            [void]$found.Add($property.Value)
        }
        Find-NativeTcItems $property.Value $found
    }
}
# This checks only genuine item registry availability, not loaded tag/gameplay acceptance.
# Known !tag_empty gates remain 'potentially available'; no Minecraft is launched to evaluate tags.
function Test-StaticItemCondition($condition) {
    switch ($condition.type) {
        'neoforge:item_exists' { return $registryIds.Contains([string]$condition.item) }
        'neoforge:or' { foreach ($child in $condition.values) { if (Test-StaticItemCondition $child) { return $true } }; return $false }
        'neoforge:and' { foreach ($child in $condition.values) { if (!(Test-StaticItemCondition $child)) { return $false } }; return $true }
        'neoforge:not' { return !(Test-StaticItemCondition $condition.value) }
        'neoforge:tag_empty' { return $false } # Explicitly unknown/potentially nonempty; not runtime tag verification.
        default { throw ('Unknown static recipe condition: ' + $condition.type) }
    }
}
$deferredNative = [System.Collections.Generic.HashSet[string]]::new()
foreach ($id in $native.Keys) {
    foreach ($condition in $native[$id].'neoforge:conditions') {
        if (!(Test-StaticItemCondition $condition)) { [void]$deferredNative.Add($id); break }
    }
}
$ast = Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'port/src/main/resources/assets/thaumcraft/porting/deferred_recipes.json') | ConvertFrom-Json
$astById = @{}
foreach ($definition in $ast.recipes) { $astById[$definition.id] = $definition }
$nativeMissingItems = @()
foreach ($id in $native.Keys) {
    $itemIds = [System.Collections.Generic.HashSet[string]]::new()
    Find-NativeTcItems $native[$id] $itemIds
    $missingItems = @($itemIds | Where-Object { !$registryIds.Contains($_) } | Sort-Object)
    if ($missingItems.Count -and !$deferredNative.Contains($id)) { $nativeMissingItems += [pscustomobject]@{ modernId = $id; missingItemIds = $missingItems } }
}
$dynamicHedge = @('hedge_gunpowder','hedge_slime','hedge_glowstone','hedge_dye','hedge_clay','hedge_string','hedge_web','hedge_lava')
$infusionBlueprints = @('thaumcraft:infusionaltar','thaumcraft:infusionaltarancient','thaumcraft:infusionaltareldritch')
$futureBlueprints = @('thaumcraft:infernalfurnace','thaumcraft:thaumatorium','thaumcraft:golempress')
$futureBlueprintBindings = @{
    'thaumcraft:infernalfurnace' = @('thaumcraft:infernal_furnace','thaumcraft:placeholder_brick','thaumcraft:placeholder_obsidian')
    'thaumcraft:thaumatorium' = @('thaumcraft:thaumatorium','thaumcraft:thaumatorium_top','thaumcraft:metal_alchemical','thaumcraft:crucible')
    'thaumcraft:golempress' = @('thaumcraft:golem_builder','thaumcraft:placeholder_bars','thaumcraft:placeholder_cauldron','thaumcraft:placeholder_anvil','thaumcraft:placeholder_table','thaumcraft:table_stone')
}
$entries = foreach ($recipe in $original.recipes) {
    if (!$recipe.id.StartsWith('thaumcraft:') -or $recipe.kind -eq 'GROUP') { continue }
    $missing = [System.Collections.Generic.HashSet[string]]::new()
    $dependencies = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($output in @(Resolve-TcToken $recipe.output)) {
        [void]$dependencies.Add($output)
        if (!$registryIds.Contains($output)) { [void]$missing.Add($output) }
    }
    foreach ($slot in $recipe.ingredients) {
        $alternatives = @($slot)
        $hasAvailable = $alternatives.Count -eq 0
        $slotTc = [System.Collections.Generic.HashSet[string]]::new()
        foreach ($alternative in $alternatives) {
            if ($alternative.StartsWith('minecraft:')) { $hasAvailable = $true; continue }
            foreach ($dependency in @(Resolve-TcToken $alternative)) {
                [void]$dependencies.Add($dependency); [void]$slotTc.Add($dependency)
                if ($registryIds.Contains($dependency)) { $hasAvailable = $true }
            }
        }
        if (!$hasAvailable) { foreach ($dependency in $slotTc) { [void]$missing.Add($dependency) } }
    }
    # No static output describes these stateful/custom recipes; missing actual content must be explicit.
    $specialDependencies = switch ($recipe.id) {
        'thaumcraft:liquiddeath' { @('thaumcraft:liquid_death_bucket') }
        'thaumcraft:robedye' { @('thaumcraft:cloth_chest','thaumcraft:cloth_legs','thaumcraft:cloth_boots') }
        'thaumcraft:voidarmordye' { @('thaumcraft:void_robe_helm','thaumcraft:void_robe_chest','thaumcraft:void_robe_legs') }
        default { @() }
    }
    foreach ($dependency in $specialDependencies) {
        [void]$dependencies.Add($dependency)
        if (!$registryIds.Contains($dependency)) { [void]$missing.Add($dependency) }
    }
    $modernId = $recipe.id
    # Existing older providers used DyeColor registry names for these two source translation ids.
    if ($modernId -eq 'thaumcraft:tallowcandlelightblue') { $modernId = 'thaumcraft:tallowcandlelight_blue' }
    if ($modernId -eq 'thaumcraft:tallowcandlesilver') { $modernId = 'thaumcraft:tallowcandlelight_gray' }
    if ($astById.ContainsKey($modernId)) {
        # The source AST knows actual semantic seal/turret/charged amulet bindings and tag outputs.
        # Do not mistake old collapsed metadata tokens from the display inventory for native item ids.
        $dependencies.Clear(); $missing.Clear()
        foreach ($dependency in $astById[$modernId].dependencyItems) {
            [void]$dependencies.Add($dependency)
            if (!$registryIds.Contains($dependency)) { [void]$missing.Add($dependency) }
        }
    }
    # The original display materials omit replacement machine/proxy bindings. Include the
    # actual FutureDustTriggers.resolve requirements so [] does not falsely mean readiness.
    if ($futureBlueprintBindings.ContainsKey($recipe.id)) {
        foreach ($dependency in $futureBlueprintBindings[$recipe.id]) {
            [void]$dependencies.Add($dependency)
            if (!$registryIds.Contains($dependency)) { [void]$missing.Add($dependency) }
        }
    }
    $status = if ($recipe.id -match 'fake') { 'source-display-only' }
        elseif ($deferredNative.Contains($modernId)) { 'native-definition-gated-future-content' }
        elseif ($native.ContainsKey($modernId)) { 'native-resource-present' }
        elseif ($dynamicHedge -contains $recipe.id.Substring(11)) { 'source-api-postAspects' }
        elseif ($recipe.kind -eq 'BLUEPRINT' -and $infusionBlueprints -contains $recipe.id) { 'native-infusion-blueprint-handler' }
        elseif ($recipe.kind -eq 'BLUEPRINT' -and $futureBlueprints -contains $recipe.id) { if ($missing.Count -gt 0) { 'native-blueprint-gated-actual-device' } else { 'native-blueprint-handler-registered-bindings' } }
        elseif ($recipe.kind -eq 'BLUEPRINT') { 'unknown-multiblock-handler-review' }
        elseif ($missing.Count -gt 0) { 'unported-dependency' }
        else { 'gap-with-registered-dependencies' }
    [pscustomobject]@{
        id = $recipe.id; sourceType = $recipe.kind; sourceClass = $recipe.sourceClass
        sourceOutput = $recipe.output; status = $status; modernId = $modernId
        nativeType = if ($native.ContainsKey($modernId)) { $native[$modernId].type } else { $null }
        tcDependencies = @($dependencies | Sort-Object); missingTcDependencies = @($missing | Sort-Object)
        requiredBlueprintBindings = if ($futureBlueprintBindings.ContainsKey($recipe.id)) { $futureBlueprintBindings[$recipe.id] } else { @() }
        conditionalSourceDefinition = ($recipe.conditionalSourceDefinition -eq $true)
        sourceAstLine = if ($astById.ContainsKey($modernId)) { $astById[$modernId].sourceLine } else { $null }
    }
}
$notes = @(
    'Read-only original display oracle is an inventory, NEVER executable crafting data.',
    'Native resource present means checked-in JSON only, not codec/gameplay/visual acceptance.',
    'Native definition gated future content means genuine future item_exists conditions currently fail BEFORE codec decode. This is complete native recipe DATA, NOT future item/device gameplay.',
    'Future static magic/ordinary definitions come from the original ConfigRecipes lexical AST, not captured display alternatives. Planned flattened item bindings are in the reviewed manifest.',
    'Registered dependency is static ConfigItems/ConfigBlocks and actual device-group factory evidence, not full semantic proof.',
    'Four conditional copper/tin/silver/lead source definitions are appended to the original capture; native loaded-tag conditions replace foundOre gates.',
    'Nine initializeSmelting recipes and 35 ordered addSmeltingBonus rules are outside the crafting display oracle; separate source-line evidence follows in nonCraftingEntries. Stage6 now supplies the real infernal furnace bonus consumer and all six flattened meat chunks; this static audit is not gameplay acceptance.',
    'All six BLUEPRINT definitions use native dust/caster handlers, not recipe JSON. Actual factory bindings are checked dynamically, including the stage9 golem press. Registered bindings do not prove world assembly acceptance.',
    'Group entries are research display groups, not executable recipes.'
)
$nonCrafting = @()
$bonusManifestPath = Join-Path $taskRoot 'port/src/main/resources/assets/thaumcraft/porting/smelting_bonus.json'
$bonusByLine = @{}
if (Test-Path -LiteralPath $bonusManifestPath) {
    $bonusManifest = Get-Content -Raw -LiteralPath $bonusManifestPath | ConvertFrom-Json
    if ($bonusManifest.sourceSha256 -ne $ast.sourceSha256) { throw 'Bonus and recipe source identities differ.' }
    foreach ($row in $bonusManifest.rules) {
        if ($bonusByLine.ContainsKey([int]$row.sourceLine)) { throw 'Duplicate bonus source line.' }
        $bonusByLine[[int]$row.sourceLine] = $row
    }
}
$recipeSourceLines = Get-Content -LiteralPath (Join-Path $taskRoot 'reference/tc6/src/thaumcraft/common/config/ConfigRecipes.java')
$smeltIds = @('quicksilver_from_ore','amber_from_ore','quartz_from_ore','charcoal_from_greatwood','charcoal_from_silverwood','iron_from_cluster','gold_from_cluster','quicksilver_from_cluster','quartz_from_cluster')
$smeltIndex = 0
for ($index = 0; $index -lt $recipeSourceLines.Count; $index++) {
    $line = $recipeSourceLines[$index].Trim()
    if ($line.StartsWith('GameRegistry.addSmelting(')) {
        $id = 'thaumcraft:' + $smeltIds[$smeltIndex++]
        $nonCrafting += [pscustomobject]@{ diagnosticKey = 'source:smelting_' + ($index + 1); sourceLine = $index + 1; sourceType = 'SMELTING'; sourceDefinition = $line; modernId = $id; status = if ($native.ContainsKey($id)) { 'native-resource-present' } else { 'gap' } }
    } elseif ($line.StartsWith('ThaumcraftApi.addSmeltingBonus(')) {
        $row = $bonusByLine[$index + 1]
        $bonusPath = if ($null -ne $row) { 'port/src/generated/resources/data/thaumcraft/smelting_bonus/' + $row.id.Substring(11) + '.json' } else { $null }
        $bonusStatus = 'gap'
        if ($bonusPath -and (Test-Path -LiteralPath (Join-Path $taskRoot $bonusPath))) {
            $bonusStatus = 'native-source-api-data-present'
            foreach ($condition in $row.payload.'neoforge:conditions') {
                if (!(Test-StaticItemCondition $condition)) { $bonusStatus = 'native-source-api-gated-future-content'; break }
            }
        }
        $nonCrafting += [pscustomobject]@{
            diagnosticKey = 'source:smelting_bonus_' + ($index + 1); sourceLine = $index + 1
            sourceType = 'INFERNAL_FURNACE_BONUS'; sourceDefinition = $line
            modernId = if ($null -ne $row) { $row.id } else { $null }; status = $bonusStatus
            nativeDataPath = $bonusPath; sourceOrder = if ($null -ne $row) { $row.payload.source_order } else { $null }
            chance = if ($null -ne $row) { $row.payload.chance } else { $null }
            consumerStage = 6
        }
    }
}
if ($View -eq 'Json') {
    $selectedEntries = if ($Limit -gt 0) { @($entries | Select-Object -Skip $Offset -First $Limit) } else { @($entries) }
    [ordered]@{ scope = 'Static TC6 ConfigRecipes coverage inventory'; sourceSha256 = $expectedHash.ToLower(); nativeDefinitionsSourceSha256 = $ast.sourceSha256; sourceAstUnresolved = @($ast.unresolved); notes = $notes; registeredTcItemIds = @($registryIds | Sort-Object); duplicateResourceIds = @($duplicates); nativeMissingItems = $nativeMissingItems; totalEntries = @($entries).Count; entries = @($selectedEntries); nonCraftingEntries = $nonCrafting } | ConvertTo-Json -Depth 8 -Compress
} elseif ($View -eq 'Entries') {
    $entries | ForEach-Object { "{0}`t{1}`t{2}`t{3}`t{4}" -f $_.id,$_.sourceType,$_.sourceOutput,$_.status,($_.missingTcDependencies -join ',') }
} elseif ($View -eq 'Gaps') {
    $entries | Where-Object status -eq 'gap-with-registered-dependencies' | ForEach-Object { "{0}`t{1}`t{2}" -f $_.id,$_.sourceType,$_.sourceOutput }
} else {
    $entries | Group-Object sourceType,status | Sort-Object Name | Select-Object Name,Count
    if ($duplicates.Count) { 'DUPLICATE RESOURCE IDS: ' + ($duplicates -join ', ') }
    if ($nativeMissingItems.Count) { $nativeMissingItems | ConvertTo-Json -Depth 4 }
    $notes
}
