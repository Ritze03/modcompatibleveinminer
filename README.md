# Mod Compatible Veinminer

A server-side Fabric veinminer: mine one ore block with a pickaxe and the whole connected vein breaks.

Mod name `ModCompatibleVeinminer`, mod id `modcompatibleveinminer`, version 1.2.0, by Ritze03. "The (mostly) Mod compatible Veinminer!"

## How it works

It decides by item and block id names, not by a tag list, so modded ores and pickaxes work out of the box. Any item whose id path ends with `_pickaxe` and any block whose id path ends with `_ore` or `ancient_debris`, from any namespace, counts.

A vein is mined when:

- you hold a pickaxe (id ends with `_pickaxe`) that is the correct tool for the ore;
- you are not sneaking (sneak to mine a single block).

The vein:

- contains only blocks of exactly the same block type (iron ore and deepslate iron ore are separate veins);
- connects through all 26 neighbours of each block, diagonals included;
- has at most 50 blocks, including the one you mined.

Every block is broken as if you mined it yourself:

- Fortune and Silk Touch apply;
- ores drop XP (not with Silk Touch);
- the pickaxe loses durability per block (Unbreaking applies);
- mining stats and hunger count;
- protection and claim mods that hook block breaking see each block.

If the pickaxe breaks during a vein, the vein stops there.

There is no config, no commands and no keybinds.

## Download

Get the jar for your Minecraft version from the GitHub release
[v1.2.0](https://github.com/Ritze03/modcompatibleveinminer/releases/tag/v1.2.0).

| Minecraft | Jar | Java |
|---|---|---|
| 1.20.1 - 1.20.4 | `modcompatibleveinminer-1.2.0+mc1.20.1-1.20.4.jar` | 17+ |
| 1.20.5 - 1.20.6 | `modcompatibleveinminer-1.2.0+mc1.20.5-1.20.6.jar` | 21+ |
| 1.21 - 1.21.8 | `modcompatibleveinminer-1.2.0+mc1.21-1.21.8.jar` | 21+ |
| 1.21.9 - 1.21.10 | `modcompatibleveinminer-1.2.0+mc1.21.9-1.21.10.jar` | 21+ |
| 1.21.11 | `modcompatibleveinminer-1.2.0+mc1.21.11.jar` | 21+ |
| 26.1 - 26.2 (26.1, 26.1.1, 26.1.2, 26.2) | `modcompatibleveinminer-1.2.0+mc26.1-26.2.jar` | 25+ |
| 26.3 | `modcompatibleveinminer-1.2.0+mc26.3.jar` | 25+ |

Pre-releases and release candidates are not supported.

## Install

- Needs Fabric Loader 0.16.4 or newer. Fabric API is not needed.
- Server-side only: put the jar in the server's `mods` folder. Players do not need it. In singleplayer, put it in your own `mods` folder.
- Restart after installing or updating. Fabric loads mods only at startup.

## Building

Uses the Gradle wrapper, Stonecutter (one source tree, one jar per version range under `versions/<node>/build/libs/`) and Fabric Loom.

- Run Gradle with JDK 25 (`JAVA_HOME` pointing at a JDK 25).
- Also have JDK 21 installed for the 1.20.x and 1.21.x nodes (the 1.20.1 node compiles with `--release 17`).

```
./gradlew build                # every jar, plus the game tests on 26.3
./gradlew :26.3:runGameTest    # only the game tests
./gradlew :1.21:build          # one node
```

The shipped jar is the one without `-sources`.

## History

The original 1.0.0 and 1.1.0 jars are on [Modrinth](https://modrinth.com/mod/modcompatibleveinminer). Their source was lost, so 1.2.0 is a rebuild from the published jars, extended to 1.20.1 - 26.3.

Changes from 1.1.0: Fortune, Silk Touch and XP now apply to vein-mined blocks (1.1.0 dropped them as if mined by hand without a tool); the vein stops when the pickaxe breaks; new Minecraft versions.

## License

MIT, see `LICENSE`.
