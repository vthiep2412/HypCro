# HypCro Implementation Plan

## Goal
Build a stealth, client-only Fabric Kotlin mod on Minecraft 26.1.2 with Java 25 that automates crop farming for 5 single-block crops using genuine in-game item interactions (Squeaky Mousemat) and strict failsafe watchdog systems.

---

## 1. Project Infrastructure
- `build.gradle.kts`: Configured for Gradle 9.2.0, Fabric Loom 1.15.5, Kotlin 2.4.10, Java 25.
- `gradle.properties`: `fabric.loom.disableObfuscation=true` for 26.1.2 unobfuscated Mojang mapping sets.
- `fabric.mod.json`: Client environment only, no custom network channels.
- `assets/hypcro/lang/en_us.json`: Controls menu keybind translations.

---

## 2. Configuration & Data Models
- **CropType**: `WHEAT`, `CARROT`, `POTATO`, `NETHER_WART`, `MUSHROOM`.
- **FarmMode**: `WS` (W/S Lane traversal) and `VERTICAL`.
- **FarmConfig**: Separated configs for `wsConfig` and `verticalConfig` with global angles/speed and per-crop custom overrides.
- **ConfigManager**: Reads/writes `.minecraft/config/hypcro.json`.

---

## 3. Genuine Squeaky Mousemat Alignment
- **Detection**: Parse `DataComponents.CUSTOM_DATA` (`id == "SQUEAKY_MOUSEMAT"`) and `DataComponents.LORE`.
- **Angle Alignment Flow**:
  1. Switch to mousemat hotbar slot (300ms delay).
  2. If target yaw/pitch differs from mousemat settings, send `/setyaw <yaw>` (350ms delay) and `/setpitch <pitch>` (350ms delay).
  3. Send genuine item use / left-click interaction packet on client thread (300ms delay) so Hypixel snaps the camera legitimately.
  4. Confirm player camera angles match target within 0.05 tolerance.
  5. Swap back to prioritized farming tool slot (300ms delay).

---

## 4. Farming Execution Engine (WS Mode)
- **Crop Raycast**: Scan horizontally 3 blocks forward using player's yaw vector at foot/ground level. Abort if non-supported block is found.
- **Tool Selector**: Match Skyblock hoe IDs (`THEORETICAL_HOE_*`, `FUNGI_CUTTER`) from `DataComponents.CUSTOM_DATA`.
- **Traversal State Rules**:
  - `isPlayerFeetInWater == true` -> hold `W` key + `Attack` key.
  - `isPlayerFeetInWater == false` -> hold `S` key + `Attack` key.
- **Polling Loop**: Every 0.2s check water status and position delta. If stalled unexpectedly, trigger watchdog.
- **Debounce**: 500ms cooldown on start/stop requests.

---

## 5. Watchdog & Failsafe System
- **Hotbar Slot Check**: If active slot switches away from expected tool, trigger alarm.
- **Teleport Check**: If delta distance > 4.0 blocks, trigger alarm.
- **Server Restart Detection**: Chat regex catches scheduled restarts, halts macro, waits 2.0s, runs `/hub`.
- **Alarm**: Parallel Ghast scream + Anvil land audio loop.

---

## 6. In-Game GUI Deck
- **Trigger**: `.hypcro` in chat or `END` key.
- **Stop on Run**: If macro is running, pressing key or command stops macro and logs notification. Second press opens deck.
- **Deck Screen**:
  - Dark theme deck with left sidebar `> Farming`.
  - `CROP FARMING` card with Status indicator.
  - Mode selector dropdown `[ Mode: W/S ▼ ]` in top right.
  - `⚙ Settings` button.
  - Clicking card starts macro and auto-closes GUI.
- **Settings Modal**:
  - Centered Mode pill switcher (`[ W/S ] | Vertical`).
  - Pitch & Yaw section with Global and Custom fields.
  - Crop selector dropdown (`[ Wheat ▼ ]`).
  - Speed section with Global and Custom inputs.
  - Apply and Cancel buttons.
