# HypCro - Fabric Kotlin SkyBlock Farm Helper

A client-side Fabric mod for Hypixel SkyBlock Garden farming with zero memory injection, safe Squeaky Mousemat auto-alignment, and multi-threaded Hypcro Watchdog failsafes.

## Features
- **In-Game GUI**: Open with `.hypcro` in chat or the `END` key.
- **Crop Farming (W/S Method)**:
  - Supports 5 single-block crops: Wheat, Carrot, Potato, Nether Wart, Mushroom.
  - Automatically identifies tools in hotbar (e.g. `THEORETICAL_HOE_WHEAT`, `FUNGI_CUTTER`).
  - Squeaky Mousemat angle alignment (`/setpitch`, `/setyaw`) without inventory opening.
  - Auto water boundary detection with 0.2s polling.
- **Hypcro Watchdog**:
  - Strict teleport detection (`Δpos > 4.0`).
  - Rotational spike flag (`> 8.0°`).
  - Server restart auto-detection with 2.0s delay and `/hub` warp.
  - Parallel Ghast + Anvil audio alarm.
- **Input Suppression**: Blocks local mouse and movement interference while the macro runs.
- **Configuration**: Stored cleanly in `.minecraft/config/hypcro.json`.

## Build
To build the `.jar`:
```bash
./gradlew build
```
Drop the resulting `.jar` from `build/libs/` into `.minecraft/mods/`.
