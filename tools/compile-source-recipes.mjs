/** Read-only TC6 ConfigRecipes lexical AST compiler. It NEVER boots Minecraft or uses the display oracle.
 * Emits reviewed native JSON to stdout; checked-in file edits are made separately with apply_patch.
 * Unsupported source expressions fail closed into unresolved, never guessed into fake items.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const root = path.resolve(import.meta.dirname, '..');
const sourceRoot = path.join(root, 'reference/tc6/src');
const sourcePath = path.join(sourceRoot, 'thaumcraft/common/config/ConfigRecipes.java');
const sourceBytes = fs.readFileSync(sourcePath);
const original = sourceBytes.toString('utf8');
const source = original.replace(/\/\*[\s\S]*?\*\//g, s => s.replace(/[^\r\n]/g, ' ')).replace(/\/\/[^\r\n]*/g, s => ' '.repeat(s.length));
const sourceSha256 = crypto.createHash('sha256').update(sourceBytes).digest('hex');
const trim = value => value.trim();
function split(expression) {
  let depth = 0, quote = '', escaped = false, start = 0;
  const values = [];
  for (let index = 0; index < expression.length; index++) {
    const ch = expression[index];
    if (quote) { if (escaped) escaped = false; else if (ch === '\\') escaped = true; else if (ch === quote) quote = ''; continue; }
    if (ch === '"' || ch === "'") quote = ch;
    else if ('([{'.includes(ch)) depth++;
    else if (')]}'.includes(ch)) depth--;
    else if (ch === ',' && depth === 0) { values.push(expression.slice(start, index).trim()); start = index + 1; }
  }
  if (depth || quote) throw new Error('Unbalanced source arguments');
  if (expression.slice(start).trim()) values.push(expression.slice(start).trim());
  return values;
}
function closeAt(text, open) {
  const expected = {'(':')', '[':']', '{':'}'}[text[open]];
  let depth = 0, quote = '', escaped = false;
  for (let index = open; index < text.length; index++) {
    const ch = text[index];
    if (quote) { if (escaped) escaped = false; else if (ch === '\\') escaped = true; else if (ch === quote) quote = ''; continue; }
    if (ch === '"' || ch === "'") quote = ch;
    else if (ch === text[open]) depth++;
    else if (ch === expected && --depth === 0) return index;
  }
  throw new Error('Unclosed source expression');
}
function constructed(expression, name) {
  expression = trim(expression);
  const prefix = `new ${name}(`;
  if (!expression.startsWith(prefix)) throw new Error(`Expected ${name}, got ${expression}`);
  const open = prefix.length - 1, end = closeAt(expression, open);
  return {args:split(expression.slice(open + 1, end)), suffix:expression.slice(end + 1).trim()};
}
function array(expression) {
  expression = trim(expression);
  if (expression.startsWith('new Object[]{')) return split(expression.slice(13, -1));
  return [expression];
}
function string(expression) {
  if (!/^"(?:[^"\\]|\\.)*"$/.test(trim(expression))) throw new Error(`Nonliteral string: ${expression}`);
  return JSON.parse(trim(expression));
}
function integer(expression) {
  expression = trim(expression).replace(/^\(byte\)/, '');
  if (!/^-?\d+$/.test(expression)) throw new Error(`Nonliteral integer: ${expression}`);
  return Number(expression);
}
const groups = new Map([['defaultGroup', '']]);
for (const match of source.matchAll(/ResourceLocation\s+(\w+)\s*=\s*(new ResourceLocation\([^;]+\));/g)) {
  try { groups.set(match[1], resource(match[2])); } catch (_) { /* reviewed unsupported expression below */ }
}
function resource(expression) {
  if (groups.has(trim(expression))) return groups.get(trim(expression));
  const {args} = constructed(expression, 'ResourceLocation');
  const id = args.length === 2 ? string(args[0]) + ':' + string(args[1]) : string(args[0]);
  return (id.includes(':') ? id : 'minecraft:' + id).toLowerCase();
}
const aspectSource = fs.readFileSync(path.join(sourceRoot, 'thaumcraft/api/aspects/Aspect.java'), 'utf8');
const aspectIds = Object.fromEntries([...aspectSource.matchAll(/(?:static final Aspect|static Aspect)\s+(\w+)\s*=\s*new Aspect\(\s*"([^"]+)"/g)].map(m => [m[1],m[2]]));
function aspects(expression) {
  if (trim(expression) === 'null') return {};
  if (!trim(expression).startsWith('new AspectList()')) throw new Error(`Dynamic aspect list: ${expression}`);
  const result = {};
  for (const match of expression.matchAll(/\.(add|merge)\(\s*Aspect\.(\w+)\s*,\s*(\d+)\s*\)/g)) {
    const id = aspectIds[match[2]];
    if (!id) throw new Error('Unmapped aspect ' + match[2]);
    result[id] = match[1] === 'merge' ? Math.max(result[id] ?? 0, Number(match[3])) : (result[id] ?? 0) + Number(match[3]);
  }
  const remaining = expression.replace(/new AspectList\(\)/, '').replace(/\.(add|merge)\(\s*Aspect\.\w+\s*,\s*\d+\s*\)/g, '').trim();
  if (remaining) throw new Error('Unsupported aspect list tail ' + remaining);
  return result;
}
function walk(folder) {
  return fs.readdirSync(folder, {withFileTypes:true}).flatMap(entry => entry.isDirectory() ? walk(path.join(folder,entry.name)) : [path.join(folder,entry.name)]);
}
const javaFiles = walk(sourceRoot).filter(file => file.endsWith('.java'));
const byClass = new Map(javaFiles.map(file => [path.basename(file, '.java'), file]));
const bindings = new Map();
const families = {
  ingots:['ingot',['thaumium','void','brass']], nuggets:['nugget',['iron','copper','tin','silver','lead','quicksilver','thaumium','void','brass','quartz','rareearth']],
  clusters:['cluster',['iron','gold','copper','tin','silver','lead','cinnabar','quartz']], plate:['plate',['brass','iron','thaumium','void']],
  mind:['mind',['clockwork','biothaumic']], modules:['module',['vision','aggression']], phial:['phial',['empty','filled']],
  baubles:['',['amulet_mundane','ring_mundane','girdle_mundane','ring_apprentice','amulet_fancy','ring_fancy','girdle_fancy']],
  turretPlacer:['turret',['basic','advanced','bore']], amuletVis:['',['amulet_vis','amulet_vis_crafted']],
  chunks:['chunk',['beef','chicken','pork','fish','rabbit','mutton']]
};
const explicit = { 'ItemsTC.seals':'seal_blank', 'BlocksTC.activatorRail':'activator_rail', 'BlocksTC.condenserlattice':'condenser_lattice', 'BlocksTC.condenserlatticeDirty':'condenser_lattice_dirty' };
for (const config of ['ConfigItems','ConfigBlocks']) {
  const text = fs.readFileSync(path.join(sourceRoot, `thaumcraft/common/config/${config}.java`), 'utf8');
  for (const match of text.matchAll(/((?:ItemsTC|BlocksTC)\.\w+)\s*=\s*(?:\([^;\n]+?\))?\s*(?:registerBlock\()?\s*new ([\w.]+)\(/g)) {
    const field = match[1], klass = match[2], open = match.index + match[0].length - 1;
    const args = split(text.slice(open + 1, closeAt(text, open)));
    let id, proof;
    const literal = args.find(arg => /^"[a-z0-9_]+"$/.test(arg));
    if (literal) { id = string(literal); proof = `Config${field.startsWith('Items') ? 'Items' : 'Blocks'} constructor literal`; }
    else if (byClass.has(klass)) {
      const file = byClass.get(klass), body = fs.readFileSync(file,'utf8');
      const registry = /setRegistryName\((?:"thaumcraft",\s*)?"([a-z0-9_]+)"\)/.exec(body);
      const superString = /super\([^;]*?"([a-z0-9_]+)"[^;]*\);/.exec(body);
      if (registry || superString) { id = (registry ?? superString)[1]; proof = path.relative(sourceRoot,file).replaceAll('\\','/') + ' constructor registry literal'; }
    }
    if (id) bindings.set(field,{source:field, modernId:'thaumcraft:' + id, proof});
  }
}
for (const [field,id] of Object.entries(explicit)) bindings.set(field,{source:field,modernId:'thaumcraft:'+id,proof:'Reviewed source constructor or approved future flattened variant contract'});
const colors = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black'];
const oldColors = colors.map(value => ({light_blue:'lightblue',light_gray:'silver'}[value] ?? value));
const vanillaRename = {REEDS:'sugar_cane',POTIONITEM:'potion',NETHER_BRICK:'nether_bricks',STONE_SLAB:'smooth_stone_slab',BRICK_BLOCK:'bricks',NOTEBLOCK:'note_block',LIT_PUMPKIN:'jack_o_lantern',MELON_BLOCK:'melon',HARDENED_CLAY:'terracotta',STONEBRICK:'stone_bricks',SKULL:'skeleton_skull',WEB:'cobweb',GRASS:'grass_block',GOLDEN_CHESTPLATE:'golden_chestplate'};
const vanillaFamilies = {WOOL:colors.map(color => color+'_wool'),DYE:['ink_sac','red_dye','green_dye','cocoa_beans','lapis_lazuli','purple_dye','cyan_dye','light_gray_dye','gray_dye','pink_dye','lime_dye','yellow_dye','light_blue_dye','magenta_dye','orange_dye','bone_meal'],FISH:['cod','salmon','tropical_fish','pufferfish'],COAL:['coal','charcoal'],SKULL:['skeleton_skull','wither_skeleton_skull','zombie_head','player_head','creeper_head','dragon_head'],STONE:['stone','granite','polished_granite','diorite','polished_diorite','andesite','polished_andesite'],LOG:['oak_log','spruce_log','birch_log','jungle_log'],LOG2:['acacia_log','dark_oak_log'],PLANKS:['oak_planks','spruce_planks','birch_planks','jungle_planks','acacia_planks','dark_oak_planks']};
const durable = /(?:axe|pick|shovel|hoe|sword|helm|chest|legs|boots|bow|shears|fishing_rod|flint_and_steel|scribe|scribing)/;
function itemIds(expression, metadata = 0) {
  expression = trim(expression);
  const dye = /^BlocksTC\.(nitor|banners|candles)\.get\(EnumDyeColor\.(\w+)\)$/.exec(expression);
  if (dye) return [`thaumcraft:${{nitor:'nitor',banners:'banner',candles:'candle'}[dye[1]]}_${dye[2].toLowerCase()}`];
  const family = /^ItemsTC\.(\w+)$/.exec(expression);
  if (family && families[family[1]]) {
    const [prefix, names] = families[family[1]];
    const ids = names.map(name => 'thaumcraft:' + (prefix ? prefix + '_' : '') + name);
    if (metadata === 32767) return ids;
    if (!ids[metadata]) throw new Error(`Unmapped TC metadata: ${expression}:${metadata}`);
    bindings.set(expression + ';' + metadata, {source:expression+';'+metadata,modernId:ids[metadata],proof:'Reviewed original variant order; explicit flattened native item contract, NOT registration'});
    return [ids[metadata]];
  }
  if (bindings.has(expression)) { if (metadata !== 0 && metadata !== 32767) throw new Error(`Non-family TC metadata ${expression}:${metadata}`); return [bindings.get(expression).modernId]; }
  const vanilla = /^(?:Items|Blocks)\.(\w+)$/.exec(expression);
  if (vanilla) {
    const key = vanilla[1], familyIds = vanillaFamilies[key];
    if (familyIds) {
      if (metadata === 32767) return familyIds.map(id => 'minecraft:' + id);
      if (!familyIds[metadata]) throw new Error(`Unmapped vanilla metadata ${expression}:${metadata}`);
      return ['minecraft:' + familyIds[metadata]];
    }
    if (metadata !== 0 && metadata !== 32767) throw new Error(`Unsupported vanilla metadata ${expression}:${metadata}`);
    return ['minecraft:' + (expression === 'Items.NETHER_BRICK' ? 'nether_brick' : vanillaRename[key] ?? key.toLowerCase())];
  }
  throw new Error('Unbound source item ' + expression);
}
const itemVariables = new Map();
for (const match of source.matchAll(/ItemStack\s+(\w+)\s*=\s*(new ItemStack\([^;]+\));/g)) {
  try { itemVariables.set(match[1], stack(match[2])); } catch (_) { /* dynamic loop values are handled explicitly */ }
}
const enchantNames = ['COLLECTOR','DESTRUCTIVE','BURROWING','SOUNDING','REFINING','ARCING','ESSENCE','VISBATTERY','VISCHARGE','SWIFT','AGILE','INFESTED','LAMPLIGHT'];
for (const match of source.matchAll(/EnumInfusionEnchantment\.addInfusionEnchantment\((\w+),\s*EnumInfusionEnchantment\.(\w+),\s*(\d+)\)/g)) {
  const item = itemVariables.get(match[1]); if (!item) throw new Error('Unknown enchanted source variable '+match[1]);
  item.components ??= {}; item.components['thaumcraft:infusion_enchantments'] ??= [];
  item.components['thaumcraft:infusion_enchantments'].push({id:enchantNames.indexOf(match[2]),lvl:Number(match[3])});
}
for (const match of source.matchAll(/(\w+)\.setTagInfo\("Potion",\s*new NBTTagString\("([^"]+)"\)\)/g)) {
  const item = itemVariables.get(match[1]); if (!item) throw new Error('Unknown source potion variable');
  item.components ??= {}; item.components['minecraft:potion_contents'] = {potion:match[2]};
}
function stack(expression) {
  expression = trim(expression);
  if (itemVariables.has(expression)) return structuredClone(itemVariables.get(expression));
  const crystal = /^(?:ThaumcraftApiHelper\.makeCrystal\(Aspect\.(\w+)\)|ConfigItems\.(\w+)_CRYSTAL)$/.exec(expression);
  if (crystal) return {id:'thaumcraft:crystal_essence',count:1,components:{'thaumcraft:aspects':{[aspectIds[crystal[1] ?? crystal[2]]]:1}}};
  const phial = /^ItemPhial\.makeFilledPhial\(Aspect\.(\w+)\)$/.exec(expression);
  if (phial) return {id:'thaumcraft:phial_filled',count:1,components:{'thaumcraft:aspects':{[aspectIds[phial[1]]]:10}}};
  const seal = /^GolemHelper\.getSealStack\("([a-z0-9_]+):([a-z0-9_]+)"\)$/.exec(expression);
  if (seal) {
    const id = `${seal[1]}:seal_${seal[2]}`;
    bindings.set(expression,{source:expression,modernId:id,proof:'Source semantic seal registry key→ItemSealPlacer ordered metadata→approved planned flattened genuine item contract, NOT registration or NBT migration'});
    return {id,count:1};
  }
  if (/^FluidUtil\.getFilledBucket\(new FluidStack\(ConfigBlocks\.FluidDeath\.instance,\s*1000\)\)$/.test(expression)) {
    bindings.set(expression,{source:expression,modernId:'thaumcraft:liquid_death_bucket',proof:'Approved genuine 1000 mB death fluid bucket contract, NOT fluid/item registration'});
    return {id:'thaumcraft:liquid_death_bucket',count:1};
  }
  let count = 1, metadata = 0, item = expression;
  if (expression.startsWith('new ItemStack(')) {
    const {args,suffix} = constructed(expression,'ItemStack'); if (suffix) throw new Error('Dynamic ItemStack suffix '+suffix);
    item = args[0]; count = args.length > 1 ? integer(args[1]) : 1; metadata = args.length > 2 ? integer(args[2]) : 0;
  }
  const ids = itemIds(item,metadata);
  if (ids.length !== 1) throw new Error('Wildcard result is not a real native ItemStack: '+expression);
  const result = {id:ids[0],count};
  // Durable source metadata is wear, not a new registry identity. Outputs preserve source pristine/default wear.
  if (metadata !== 32767 && metadata > 0 && durable.test(ids[0])) result.components = {'minecraft:damage':metadata};
  return result;
}
const oreSpecial = {nitor:'c:nitor',plankWood:'minecraft:planks',logWood:'minecraft:logs',slabWood:'minecraft:wooden_slabs',stickWood:'c:rods/wooden',trapdoorWood:'minecraft:wooden_trapdoors',string:'c:strings',leather:'c:leathers',wool:'minecraft:wool',cobblestone:'c:cobblestones',paneGlass:'c:glass_panes',blockGlass:'c:glass_blocks',workbench:'c:workbenches',bookshelf:'c:bookshelves',chest:'c:chests'};
function ore(name) {
  if (oreSpecial[name]) return oreSpecial[name];
  const match = /^(ingot|nugget|plate|gem|dust|ore|block|dye)([A-Z].*)$/.exec(name);
  if (!match) throw new Error('Unbound source OreDictionary key '+name);
  const plural = {ingot:'ingots',nugget:'nuggets',plate:'plates',gem:'gems',dust:'dusts',ore:'ores',block:'storage_blocks',dye:'dyes'}[match[1]];
  let value = match[2].replace(/([a-z])([A-Z])/g,'$1_$2').toLowerCase();
  if (value === 'silver' && match[1] === 'dye') value = 'light_gray';
  return `c:${plural}/${value}`;
}
function ingredient(expression, requireNBT = false) {
  expression = trim(expression);
  // Source Forge OreDictionary stone's vanilla default is only metadata-zero stone.
  // Modern c:stones also accepts granite/andesite/diorite: that would silently broaden TC6.
  if (expression === '"stone"') return {item:'minecraft:stone'};
  if (expression === 'Ingredient.fromStacks(nitorStacks)') return colors.map(color => ({item:'thaumcraft:nitor_'+color}));
  if (/^"/.test(expression)) return {tag:ore(string(expression))};
  if (expression.startsWith('new IngredientNBTTC(')) return ingredient(constructed(expression,'IngredientNBTTC').args[0],true);
  if (expression.startsWith('Ingredient.fromItem(')) return {item:itemIds(split(expression.slice(20,-1))[0])[0]};
  if (expression.startsWith('new ItemStack(')) {
    const {args} = constructed(expression,'ItemStack');
    const metadata = args.length > 2 ? integer(args[2]) : 0;
    const ids = itemIds(args[0], metadata);
    if (ids.length > 1) return ids.map(item => ({item}));
    if (metadata === 32767) return {item:ids[0]};
    const value = stack(expression);
    if (requireNBT || value.components) return {type:'thaumcraft:nbt',stack:value};
    if (durable.test(value.id)) return {type:'thaumcraft:nbt',stack:{...value,components:{'minecraft:damage':metadata}}};
    return {item:value.id};
  }
  const value = stack(expression);
  return requireNBT || value.components ? {type:'thaumcraft:nbt',stack:value} : {item:value.id};
}
function output(expression) {
  if (trim(expression).startsWith('new Object[]{')) {
    const [label,value] = array(expression);
    let match = /^new NBTTag(Byte|Int)\((.*)\)$/.exec(value);
    if (!match) throw new Error('Unsupported static output tag '+value);
    return {tag:string(label),value:integer(match[2])+(match[1] === 'Byte' ? 'b' : '')};
  }
  return stack(expression);
}
function shaped(parts) {
  parts = parts.flatMap(part => part.startsWith('new Object[]{') ? array(part) : [part]);
  const pattern = [], key = {};
  let index = 0;
  while (index < parts.length && parts[index].startsWith('"')) pattern.push(string(parts[index++]));
  for (; index < parts.length; index+=2) {
    if (!/^'.'$/.test(parts[index]) || !parts[index+1]) throw new Error('Unsupported shaped recipe key '+parts[index]);
    key[parts[index][1]] = ingredient(parts[index+1]);
  }
  if (!pattern.length || pattern.length > 3 || pattern.some(row => row.length > 3)) throw new Error('Invalid source crafting pattern');
  return {pattern,key};
}
const existingDeferred = path.join(root,'port/src/main/resources/assets/thaumcraft/porting/deferred_recipes.json');
const previouslyDeferred = new Set(fs.existsSync(existingDeferred) ? JSON.parse(fs.readFileSync(existingDeferred,'utf8')).recipes.map(row => row.id) : []);
const nativeIds = new Set();
for (const folder of ['port/src/generated/resources/data/thaumcraft/recipe','port/src/main/resources/data/thaumcraft/recipe']) {
  if (!fs.existsSync(path.join(root,folder))) continue;
  for (const file of fs.readdirSync(path.join(root,folder))) if (file.endsWith('.json')) {
    const id = 'thaumcraft:'+file.slice(0,-5); if (!previouslyDeferred.has(id)) nativeIds.add(id);
  }
}
const definitions = [], unresolved = [], skipped = [];
function nativePayload(name, args) {
  if (name === 'addCrucibleRecipe') {
    const ctor = constructed(args[1],'CrucibleRecipe');
    const [research,result,catalyst,cost] = ctor.args;
    const payload = {type:'thaumcraft:crucible',research:string(research),result:output(result),catalyst:ingredient(catalyst),aspects:aspects(cost)};
    if (ctor.suffix) { const m = /^\.setGroup\((.*)\)$/.exec(ctor.suffix); if (!m) throw new Error('Crucible suffix '+ctor.suffix); payload.group=resource(m[1]); }
    return payload;
  }
  if (name === 'addInfusionCraftingRecipe') {
    const {args:parts,suffix} = constructed(args[1],'InfusionRecipe'); if (suffix) throw new Error('Infusion source suffix');
    const [research,result,instability,cost,central,...components] = parts;
    return {type:'thaumcraft:infusion',research:string(research),result:output(result),instability:integer(instability),aspects:aspects(cost),central:ingredient(central),components:components.flatMap(part => array(part)).map(part => ingredient(part))};
  }
  if (name === 'addArcaneCraftingRecipe') {
    const shapeless = args[1].startsWith('new ShapelessArcaneRecipe(');
    const {args:parts,suffix} = constructed(args[1],shapeless ? 'ShapelessArcaneRecipe' : 'ShapedArcaneRecipe'); if (suffix) throw new Error('Arcane source suffix');
    const [group,research,vis,cost,result,...inputs] = parts;
    const costs = aspects(cost);
    return {type:'thaumcraft:arcane_'+(shapeless?'shapeless':'shaped'),group:resource(group),research:string(research),vis:integer(vis),crystals:['aer','ignis','aqua','terra','ordo','perditio'].map(aspect => costs[aspect] ?? 0),_sourceCrystals:costs,result:output(result),...(shapeless ? {ingredients:inputs.flatMap(part=>array(part)).map(part=>ingredient(part))} : shaped(inputs))};
  }
  const [id,group,result,...inputs] = args;
  const shapeless = name === 'shapelessOreDictRecipe';
  return {type:'minecraft:crafting_'+(shapeless?'shapeless':'shaped'),group:resource(group),category:'misc',result:output(result),...(shapeless ? {ingredients:inputs.flatMap(part=>array(part)).map(part=>ingredient(part))} : shaped(inputs))};
}
function dependencies(payload) {
  const items = new Set(), tags = new Set();
  function inspect(value) {
    if (!value || typeof value !== 'object') return;
    for (const [key,child] of Object.entries(value)) {
      if ((key === 'item' || key === 'id') && typeof child === 'string' && child.startsWith('thaumcraft:')) items.add(child);
      if (key === 'tag' && typeof child === 'string' && child.includes(':')) tags.add(child);
      inspect(child);
    }
  }
  inspect(payload);
  return {items:[...items].sort(),tags:[...tags].sort()};
}
function add(id, sourceLine, sourceExpression, payload) {
  if (definitions.some(row=>row.id===id)) throw new Error('Duplicate static native definition '+id);
  const sourceCrystals = payload.type.startsWith('thaumcraft:arcane_') ? payload._sourceCrystals ?? {} : undefined;
  delete payload._sourceCrystals;
  const dependency = dependencies(payload);
  payload['neoforge:conditions'] = [...dependency.items.map(item=>({type:'neoforge:item_exists',item})),...dependency.tags.map(tag=>({type:'neoforge:not',value:{type:'neoforge:tag_empty',tag}}))];
  if (!payload['neoforge:conditions'].length) throw new Error('Future recipe lacks genuine dependency conditions '+id);
  definitions.push({id,sourceLine,sourceExpression,...(sourceCrystals ? {sourceCrystals} : {}),dependencyItems:dependency.items,dependencyTags:dependency.tags,payload});
}
const callNames = /(?:ThaumcraftApi\.(addArcaneCraftingRecipe|addCrucibleRecipe|addInfusionCraftingRecipe)|\b(oreDictRecipe|shapelessOreDictRecipe)|GameRegistry\.(addShapedRecipe))\s*\(/g;
for (const match of source.matchAll(callNames)) {
  const name = match[1] ?? match[2] ?? match[3], open = match.index + match[0].length - 1, end = closeAt(source,open);
  const args = split(source.slice(open+1,end)), sourceLine = source.slice(0,match.index).split('\n').length;
  const sourceExpression = original.slice(match.index,end+1).replace(/\r\n/g,'\n');
  if (args[0] === 'String name') continue; // Method declarations, not recipe registration calls.
  if (sourceLine === 190 || sourceLine === 210 || sourceLine === 2800 || sourceLine === 2931) {
    skipped.push({id:null,sourceLine,reason:'Reviewed source Aspect/EnumDyeColor loop already expanded by genuine native providers'}); continue;
  }
  let id;
  try {
    id = name === 'oreDictRecipe' || name === 'shapelessOreDictRecipe' ? 'thaumcraft:' + string(args[0]).toLowerCase() : resource(args[0]);
    // Post-aspect hedge is genuinely dynamic. Display/fake/group recipes are never converted.
    if (nativeIds.has(id)) { skipped.push({id,sourceLine,reason:'Native executable provider already owns this id'}); continue; }
    if (sourceLine >= 3010) { skipped.push({id,sourceLine,reason:'Source postAspects runtime recipe; real object aspect costs are computed by API'}); continue; }
    add(id,sourceLine,sourceExpression,nativePayload(name,args));
  } catch (failure) { unresolved.push({id:id??null,sourceLine,sourceExpression,reason:failure.message}); }
}
// Exactly expand the source EnumDyeColor banner loop; source wool metadata is the loop's dye index.
const bannerRow = unresolved.find(row => row.sourceExpression.includes('"thaumcraft:Banner"'));
if (bannerRow) {
  for (let index=0;index<colors.length;index++) {
    const id='thaumcraft:banner'+oldColors[index]; if (nativeIds.has(id)) continue;
    add(id,bannerRow.sourceLine,bannerRow.sourceExpression,{type:'thaumcraft:arcane_shaped',group:'thaumcraft:banners',research:'BASEINFUSION',vis:10,crystals:[0,0,0,0,0,0],result:{id:'thaumcraft:banner_'+colors[index],count:1},pattern:['WS','WS','WB'],key:{W:{item:'minecraft:'+colors[index]+'_wool'},S:{tag:'c:rods/wooden'},B:{tag:'minecraft:wooden_slabs'}}});
    bindings.set('BlocksTC.banners.get(EnumDyeColor.'+colors[index].toUpperCase()+')',{source:'BlocksTC.banners.get(EnumDyeColor.'+colors[index].toUpperCase()+')',modernId:'thaumcraft:banner_'+colors[index],proof:'Source BlockBannerTC dye loop; lightblue/silver canonicalized to light_blue/light_gray'});
  }
  unresolved.splice(unresolved.indexOf(bannerRow),1);
}
const report={format:1,source:'reference/tc6/src/thaumcraft/common/config/ConfigRecipes.java',sourceSha256,notes:[
  'Read-only lexical source AST; no display oracle alternatives, no Java evaluation or Minecraft bootstrap.',
  'Native recipe definitions are guarded before ItemStack/Ingredient decoding; registry ids are planned source-derived contracts, NOT fake registrations or future gameplay completion.',
  'OreDictionary symbols remain native tags; source metadata families become explicit flattened variants; Infusion tag outputs retain label+SNBT and real typed components.',
  'Existing native providers own current recipes; postAspects owns dynamic hedge costs; fake/group definitions stay display-only.'
],bindings:[...bindings.values()].sort((a,b)=>a.source.localeCompare(b.source)),recipes:definitions.sort((a,b)=>a.id.localeCompare(b.id)),unresolved,existingNative:skipped};
const bonuses = [];
for (const match of source.matchAll(/ThaumcraftApi\.addSmeltingBonus\s*\(/g)) {
  const open = match.index+match[0].length-1,end = closeAt(source,open),args=split(source.slice(open+1,end));
  let input;
  if (args[0].startsWith('"')) { const legacy=string(args[0]); input={legacy_ore:legacy,tag:ore(legacy)}; }
  else { const part=constructed(args[0],'ItemStack').args; input={items:itemIds(part[0],part.length>2?integer(part[2]):0)}; }
  const output=stack(args[1]),chance=args.length>2?Number(args[2].replace(/F$/,'')):0.33;
  if (!Number.isFinite(chance)||chance<0||chance>1) throw new Error('Invalid source bonus chance');
  const payload={source_order:bonuses.length,input,output,chance};
  const required=[output.id,...(input.items??[])].filter(id=>id.startsWith('thaumcraft:'));
  payload['neoforge:conditions']=[...new Set(required)].sort().map(item=>({type:'neoforge:item_exists',item}));
  if(input.tag) payload['neoforge:conditions'].push({type:'neoforge:not',value:{type:'neoforge:tag_empty',tag:input.tag}});
  bonuses.push({id:'thaumcraft:source_'+String(bonuses.length).padStart(2,'0'),sourceLine:source.slice(0,match.index).split('\n').length,
    sourceExpression:original.slice(match.index,end+1),payload});
}
if(bonuses.length!==35) throw new Error('Source smelting bonus registration count changed');
const option=process.argv[2]??'summary';
if(option==='bonusjson') console.log(JSON.stringify({format:1,source:report.source,sourceSha256,notes:[
  '35 ordered logical source registrations; duplicate oreGold/oreIron inputs with different outputs/chances intentionally coexist.',
  'Native source_00..34 ids are NEW datapack definition identities, not TC6 registry/hash migration.',
  'Source wildcard fish metadata32767 becomes exactly4 native fish alternatives; cluster wildcard exactly8 cluster alternatives. Each family remains one logical bonus, not multiple RNG trials.',
  'Future chunk outputs have item_exists conditions before native decoding; infernal-furnace chance/RNG/XP/block gameplay remains its own phase6 consumer.'
],rules:bonuses}));
else if(option==='json') { const offset=Number(process.argv[3]??0),limit=Number(process.argv[4]??definitions.length); console.log(JSON.stringify({...report,recipes:report.recipes.slice(offset,offset+limit)})); }
else if(option==='unresolved') console.log(JSON.stringify(unresolved,null,2));
else if(option==='bindings') console.log(JSON.stringify(report.bindings,null,2));
else console.log(JSON.stringify({sourceSha256,totalDeferred:definitions.length,types:Object.fromEntries([...new Set(definitions.map(row=>row.payload.type))].map(type=>[type,definitions.filter(row=>row.payload.type===type).length])),unresolved:unresolved.map(({id,sourceLine,reason})=>({id,sourceLine,reason})),existingNative:skipped.length},null,2));
