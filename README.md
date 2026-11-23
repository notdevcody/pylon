<img alt="Pylon logo" src="src/main/resources/assets/pylon/icon.png" width="128px" />

# Pylon
Pylon allows legacy Minecraft versions to run with LWJGL 3. It replaces the outdated windowing system with SDL 3, and serves as a library for mods.

## Downloads
* [Latest Release](https://github.com/notdevcody/pylon/releases/latest)
* [Latest Nightly](https://nightly.link/notdevcody/pylon/workflows/nightly/main/pylon-nightly.zip)

## Installation
> [!IMPORTANT]
> On versions **older than 1.6** a deAWT mod is required. [LegacyFix](https://github.com/betacraftuk/legacyfix) is recommended.

### Forge
Depending on the game version:
- 1.3 or older: Follow [Other](#Other).
- 1.4: Put the mod jar into the `.minecraft/coremods` folder.
- 1.5 or newer: Put the mod jar into the `.minecraft/mods` folder.

### Fabric
Put the mod jar into the `.minecraft/mods` folder.

### Other
If you are playing on Vanilla or using an older mod loader (like Risugami's), you need to install Pylon as a Java Agent.

Pylon can also be put in place of LWJGL2 directly, but game patches won't be available.

## Features
### Fixes
- Lag from high-polling-rate mice
- Keys and mouse buttons getting stuck
- Crash when resizing the window on macOS

### Improvements
- System IME support
- More precise mouse input
- Proper clipboard handling
- Reduced display / input overhead
- Dark mode for the window title bar
- Better fullscreen and window resizing

## Settings
Settings are set via JVM arguments (`-Dpylon.property=value`).

| Property           | Values                                | Default      |
|--------------------|---------------------------------------|--------------|
| `pylon.fullscreen` | `borderless`, `overhang`, `exclusive` | `borderless` |
| `pylon.rawInput`   | `true`, `false`                       | `true`       |
| `pylon.highDpi`    | `true`, `false`                       | `false`      |
| `pylon.ime`        | `true`, `false`                       | `true`       |

## Developers
Pylon is published to the Clover Client Maven, and can be added as a dependency like so:
```kts
repositories {
    maven("https://maven.cloverclient.com/releases")
}

dependencies {
    modImplementation("pl.tomgirl:pylon:${version}")
}
```
