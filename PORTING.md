# Porting log

The plan and stage status are in [PORTING-PLAN.md](PORTING-PLAN.md). Unfinished code is marked `TODO(port)` in
`port/src`.

## General

**Registration.** `RegisterEvent` calls the Config* classes, which fill the static fields of `BlocksTC`/`ItemsTC`,
as in TC6, without `DeferredHolder.get()` all over the code.

**Proxies.** `IProxy` is kept only for calls from common code into the client (`getClientWorld` and so on);
`ClientProxy` is created in `ThaumcraftClient` (`@Mod(dist = CLIENT)`). The lifecycle uses mod bus events.

**Config.** The static fields `ModConfig.CONFIG_*` are kept; values are synced from `ModConfigSpec`.
Graphics → client, misc → server (in the world folder), world → common. `overworldDim` and `dimensionOuterId` are
removed: 1.21 has no numeric dimension ids.

**Entity ids.** TC6 CamelCase (`thaumcraft:FluxRift`) → snake_case (`thaumcraft:flux_rift`).

**Languages.** `.lang` → `.json` with `tools/convert_lang.py`; key renames in `tools/lang_remap.json`.

**Assets.** `tools/import_assets.py`: `textures/blocks` → `block`, `items` → `item`. Models, blockstates, loot and
recipes are not copied: datagen generates them.

**Network.** Each of the 40 `IMessage`s became a `CustomPacketPayload` record with a `StreamCodec`. The server never
trusts client state: research, payments, crafting and focus results are decided on the server.

## Aspects

**Ore Dictionary → item tags.** The string APIs (`registerObjectTag(String, ...)`) take a tag id
(`"c:ingots/iron"`), with wildcard support (`"c:gears*"`). Where a 1.12 oredict group does not match the 1.21 tag
(Forge's `stone` is only smooth stone), an explicit item list is used.

**Item key.** 1.12.2: a hash of the NBT serialisation (item + meta + NBT). 1.21: `ItemStack.hashItemAndComponents`.
Lookup: exact stack → without damage (former meta 32767) → item without components (former "without NBT") →
generation.

**Where aspects are computed.** In 1.12.2 client and server derived aspects from recipes themselves. In 1.21 only the
server has recipes, and the hash keys depend on the JVM. So the server builds the table on start and after
`/reload` (`AspectEvents`), precomputes the aspects of every item and sends the table to clients in
`PacketSyncObjectTags` (stack + list). Bonuses (armour, weapons, enchantments, essentia jars) are still computed on
either side.

**Tool tiers.** 1.21 has no harvest level: wood/gold 0, stone 1, iron 2, diamond 3, netherite 4; modded tiers by the
`incorrect_for_*_tool` tag.

**Enchantment rarity** is replaced by weight: 10 common, 5 uncommon (+2), 2 rare (+4), 1 very rare (+6).
Enchantments added after 1.12.2 give only "magic", like unknown enchantments in TC6.

**Potions.** Reagents come from `PotionBrewing` through the access transformer, as in TC6, with the same
"first recipe" order.

**Shulker boxes.** In TC6 coloured ones inherited the purple box's aspects (the base in 1.12). In 1.21 the base is
the undyed box and dyeing is a special recipe, so they inherit from the undyed one.

**Mobs.** `EntityList.getEntityString` ("Zombie") → `EntityType.getKey` ("minecraft:zombie"). The elder guardian is
a separate mob in 1.21.

**Serialisation.** `AspectList` has a `CODEC`/`STREAM_CODEC` and `equals`/`hashCode`, so it can be a Data Component.
The NBT format (`Aspects: [{key, amount}]`) is kept. Unknown aspects are skipped on read (TC6 stored a null key).

**Generation order and caching (differs from TC6).** Values generated while `ConfigAspects` runs are dropped and
recomputed from the full table (otherwise the lapis block was computed before lapis and stayed empty). An empty
result of a nested call cut short by a recipe cycle is not cached.

**Choosing an ingredient variant (differs from TC6).** TC6 took the first variant. In 1.21 list ingredients often
start with a derived item (chiselled quartz for the quartz slab), which gives a cycle; the first variant that has
aspects is taken.

**Smelting and stonecutting (extension).** If crafting gives nothing, smelting and then stonecutting recipes are
used (the same ×0.75/count formula). This does not affect items TC6 derived from crafting.

**1.21 items.** `ConfigAspects.registerModernItemAspects`: base materials by analogy with TC6, groups by rules
(copper oxidation = base + entropy 1..3, waxed = unwaxed, infested blocks = base + bestia, buckets of creatures).

**Other mods' items (not in TC6).** A second pass runs over items still empty after the normal table, so known
values never change:
1. `ConfigTagAspects`: common-tag rules (logs like oak, stone like stone, ingots/gems/dusts/ores by material, plates,
   gears and rods by their material's ingot).
2. Recipes of any type, including other mods' machines and altars.
3. `AspectGapEvent` on `NeoForge.EVENT_BUS`: addons fill items made in their own machines or unique items.
4. Last resort for natural blocks without tags or recipes, by the tool that mines them, and for fluid buckets.

**Commands.** `/thaumcraft aspects missing` lists items without aspects; `/thaumcraft aspects dump` writes every item
with aspects to `logs/thaumcraft-aspects.txt` for comparing tables between versions.

## Blocks

**Hardness.** In 1.12 `setResistance(r)` stored `r*3`, `setHardness(h)` raised resistance to `h*5`, and the result was
divided by 5. So in 1.21 `explosionResistance = max(0.6 * r, h)`, respecting the call order (ores and logs 3.0,
planks 2.0). Light `setLightLevel(f)` → `(int)(15 * f)`.

**Material → tags.** `Material.ROCK` (crystal support) is the tag `thaumcraft:crystal_support`; cinderpearl soil is
`thaumcraft:cinderpearl_soil`; `setHarvestLevel` is `mineable/*` and `needs_*_tool`; OreDictionary is `c:` tags.

**Vis crystals.** Unlisted face properties and `CrystalModel` are replaced by six `BooleanProperty`s and a multipart
blockstate: each support face gets a model from `models/obj/crystal.obj` (NeoForge OBJ loader, shard group
visibility). TC6's random shard choice (shuffle by position) became 4 weighted variants per size. A crystal without
support breaks through `updateShape` → AIR, keeping the drop.

**Trees.** Saplings are `SaplingBlock`s with their own feature choice, without the extra RNG draws of the modern
`TreeGrower` (greatwood only from 2x2). Leaves are `LeavesBlock` ("placed by a player" = `persistent`), dropping no
sticks; the sapling and quicksilver nugget chances are recomputed from the 1.12 formulas. TC logs are not in
`logs_that_burn`: they have their own burn time (data map `furnace_fuels`) and charcoal recipe.

**Models.** Simple models (cube, pillar, stairs, slabs) are described in datagen. Models with elements and OBJ are
copied by `tools/import_models.py`. Random stone rotations are blockstate variants.

**Slabs.** The 1.12 `slab_double_*` blocks are not needed: a double slab is the `type=double` state of the same
`SlabBlock`.

**Magical forest grass.** 1.21 `SpreadingSnowyDirtBlock` spreads itself, while 1.12 `BlockGrass` spread normal grass,
so the random tick is delegated to vanilla grass. Greatwood leaves and the grass take the biome colour, as in TC6.

**Porous stone.** `rand(15) + fortune > 13` → a find, otherwise gravel; in the loot table as `alternatives`.

## World

**Aura.** Stored in a chunk Data Attachment (`thaumcraft:aura`) instead of chunk NBT; the separate per-dimension
`AuraThread` is replaced by the same calculation on the server tick every 20 ticks. Numbers and order of operations
are kept. The aura base is the average of the biome modifiers of the chunk and its 4 neighbours × 500 × N(1, 0.1),
read without loading neighbouring chunks.

**Biomes.** Inserted into the normal world through TerraBlender. `BiomeDictionary.Type` is replaced by `c:`/
`minecraft:` biome tags.

**Ores and crystals from the 1.21 world bottom (deviation).** In TC6 the ranges started at Y 0 (the 1.12 world), so
the deepslate layer (Y −64…0) had no cinnabar, quartz or crystals. The lower bound is now `getMinBuildHeight()`; the
upper bounds are unchanged: crystals up to surface − 6, cinnabar up to 50, quartz up to 63, amber unchanged. The
number of attempts and `oreDensity` are unchanged. `tc_ore_replaceables` includes deepslate and tuff. Only new chunks
are affected.

## Client

All 31 FX classes, the shaders and the special renderers are rewritten on native render types and vertex consumers
instead of immediate-mode OpenGL; GLE tubes and beams are generated as meshes. The Thaumometer item model keeps
vanilla's GUI/ground/frame transforms, since the TC6 translations compensated a 1.12 Forge offset.

## Integrations

**JEI** (optional, built in): arcane workbench, crucible, infusion (including infusion enchantments and runic
upgrades), aspect combinations, aspect composition of items, transfer into the arcane workbench and hiding recipes
without research, covering the features of ThaumicJEI 1.7.0.

## Bugs found in the original

- `ThaumcraftApi.exists()` looked up a `String` in a `Map<Integer, …>` and always returned `false`. Fixed in the port.
- Because of this the merge branch in `AspectEventProxy.registerComplexObjectTag` and the early return in
  `ThaumcraftCraftingManager.generateTags` never ran (the merge branch was also buggy:
  `tmp.merge(tag, tmp.getAmount(tag))`). TC6's actual behaviour is kept so the values match.
- `getBonusTags` for essentia jars cleared the list right inside the `objectTags` table. The port uses a copy.
- TC6 registers taint seeds and the dark oak fence gate twice, and has no oak fence gate. Kept as is.
- `sounds.json` had the key `runicshieldecharge` (a typo) while the code registered `runicshieldcharge`, so the sound
  was never found. Fixed.
