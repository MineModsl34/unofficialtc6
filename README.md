# Thaumcraft 6 → NeoForge 1.21.1 (unofficial research port)

An unofficial port of Thaumcraft 6.1.BETA26 (Minecraft 1.12.2) to Minecraft 1.21.1 / NeoForge.
A personal research project. **Do not distribute**: Thaumcraft is All Rights Reserved (Azanor); all credit for
the original mod, its design and assets goes to its author.

- [PORTING-PLAN.md](PORTING-PLAN.md): scope, system mapping 1.12.2 → 1.21.1, stages and their status.
- [PORTING.md](PORTING.md): porting decisions, deviations from TC6 and bugs found in the original.

## Layout

| Path | What it is |
|---|---|
| `port/` | The mod: NeoForge 21.1, Minecraft 1.21.1, ModDevGradle, Java 21, modid `thaumcraft` |
| `tools/` | Helper scripts (asset/lang import, progress counter, test runners) |

The decompiled original is not part of this repository and is never committed.

## Building

Requires JDK 21.

```sh
cd port
./gradlew build              # compile, unit tests, jar in port/build/libs
./gradlew runClient          # start the game with the mod
./gradlew runServer          # dedicated server
./gradlew runGameTestServer  # in-world GameTests
./gradlew runData            # regenerate src/generated/resources
```

Required mods: Curios (baubles) and TerraBlender (Thaumcraft biomes in the normal world). Optional: JEI (recipe
categories, aspect lookup, recipe transfer).

## 1.12.2 (MCP) → 1.21.1 (Mojang) names

The names differ: `World` → `Level`, `EntityPlayer` → `Player`, `IBlockState` → `BlockState`,
`TileEntity` → `BlockEntity`, `NBTTagCompound` → `CompoundTag`, `ItemStack` stayed (but NBT → Data Components).
Look for counterparts by meaning, not by name.
