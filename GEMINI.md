# HypCro Workspace Rules & Architecture Target

## Project Specifications & Overview

### Toolchain & Runtime Specifications
- **Target Minecraft Version**: `26.2` (Strictly 26.2 unobfuscated Mojang mappings)
- **Mod Loader**: Fabric Loader (`0.16.0+`)
- **Fabric API**: `0.155.2+26.2`
- **Language**: Kotlin 2.4.10 (`fabric-language-kotlin:1.13.13+kotlin.2.4.10`, `org.jetbrains.kotlin.jvm:2.4.10`)
- **Serialization**: `kotlinx-serialization-json:1.6.3`
- **Concurrency**: `kotlinx-coroutines-core:1.8.1`
- **JDK Runtime & Toolchain**: Azul Zulu 25 (`Java 25.0.1`) targeting JVM 25 across JavaCompile, KotlinCompile, and Mixin compatibility
- **Gradle Version**: `9.2.0` (Gradle wrapper `gradle-9.2.0-bin.zip`)
- **Gradle Plugin**: `fabric-loom:1.15.5` with `fabric.loom.disableObfuscation=true` in `gradle.properties`

### Project Overview
HypCro is a high-performance, client-side Hypixel SkyBlock Garden farming helper mod built natively for Fabric 26.2 on Java 25 and Kotlin 2.4.10. HypCro replaces legacy Forge 1.8.9 reflective hacks and external background injection tools with native Fabric mixins, non-blocking coroutines, humanized kinematics, autonomous 3D flight pathfinding, in-world Gizmo vector rendering, and real-time watchdog failsafes.

Key architectural highlights include:
- **Macro Engine**: Centralized state machine automation for W/S linear farming and vertical crop layouts with automatic hoe selection and Squeaky Mousemat integration.
- **Humanized Kinematics**: Hardware mouse sensitivity GCD quantization, continuous delta Bezier control point generation, micro-tremor noise, precision slowdown within 15 degrees, and critically damped spring physics for flight camera tracking.
- **Native 3D Navigation**: Three selectable 3D flight pathfinders (Theta*, smoothed 3D A*, BIT* Batch Informed Trees) with sub-step collision raycasting, altitude clearance bias, 35-degree corner braking, and stuck recomputation.
- **Autonomous Pest Extermination**: 24-plot Garden routing, multi-tier vacuum detection (T1 to T5), radial clearance cone protection for preserved plots, scoreboard and tablist scraping, and automated return flow.
- **Auto Bouncy Ball Automation**: Real-time ArmorStand trajectory prediction and pure keyboard strafe positioning for automated beach ball bouncing minigames.
- **Watchdog Failsafes**: Real-time surveillance of forced server rotations with a 400ms debounce window, packet-level teleport detection with a 6.0b threshold, randomized hotbar slot checks, unfamiliar GUI popups, server restart broadcasts, and airborne stall recovery.
- **Decoupled Camera**: 360-degree FreeLook camera with third-person raycast bypass enabling smooth zoom up to 50 blocks.
- **Vector Rendering**: Modern debug vector rendering via Minecraft's native `net.minecraft.gizmos.Gizmos` and `net.minecraft.gizmos.GizmoStyle` APIs instead of deprecated immediate mode OpenGL.
- **Async Configuration**: Non-blocking JSON configuration persistence using Kotlinx Serialization and dedicated IO channel actors.
- **Shared Utilities Foundation**: Centralized utilities for SkyBlock item NBT resolution, canonical angle geometry, and Garden state reading.

---

## RULES

### Ground Truth & Anti-Habit Verification Protocol
- **Double-check EVERYTHING against the active codebase**: Never make assumptions, invent features, state unverified mechanics, or describe commands, keybinds, configs, or behaviors out of habit or general memory (e.g. never assume `.hypcro start` exists when execution actually goes through GUI keybinds). Every piece of code, architectural explanation, parameter, keybinding, and test instruction MUST be verified by reading the relevant source files before responding or modifying code.
- **Never guess Mojang or Fabric API signatures**: Minecraft 26.2 contains substantial internal refactorings. Do not rely on outdated memory or search results from older releases (1.8 through 1.20).
- **Inspect local Loom cache deobf bytecode**: When investigating vanilla classes, methods, or fields, always inspect the local deobfuscated JAR located in the Loom cache at `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-043a8b3edf/26.2/minecraft-merged-043a8b3edf-26.2.jar` using `javap -p` or class decompilation to obtain exact, guaranteed method signatures.

### DO NOT ATTEMPT TO REMOVE THESE
- Correct comments, non-duplicated comments
- Print telemetry, commented telemetry

### Strict Architectural Centralization & Anti-Fragmentation Guardrails
1. **Centralized SkyBlock Item & NBT Resolution**:
   - All SkyBlock item identification, NBT `ExtraAttributes` ID queries, farming tool searches, vacuum tier lookups, mousemat detection, and beach ball slot lookups MUST reside in `com.hypcro.util.SkyBlockItemHelper`.
   - Never write one-off `DataComponents.CUSTOM_DATA` parsers or duplicate hotbar scanning loops in individual engines or screens.

2. **Standardized Angle Math & Kinematics**:
   - All angle wrapping, degree difference calculations, and tolerance checks MUST use `com.hypcro.util.AngleUtils`.
   - Never write manual modulo wrapping math like `(((yaw1 - yaw2 + 180f) % 360f + 360f) % 360f) - 180f` in feature code.

3. **Centralized Tablist & Scoreboard Parsing**:
   - All text color stripping, tablist extraction, sidebar scoreboard reading, and Garden location checks MUST use `com.hypcro.util.GardenStateReader`.
   - Never parse scoreboard player teams or online player tab entries directly in command handlers or feature engines.

4. **Master Macro Lifecycle & State Orchestration**:
   - `com.hypcro.farming.MacroController` is the single authoritative source of truth for all bot operations.
   - All global active checks MUST call `MacroController.isAnyMacroActive()`.
   - All emergency stops, GUI openings, ESC menu hooks, and failsafe aborts MUST call `MacroController.stopAllMacros(reason)`.
   - Never write cascading multi-line stop chains across different engines in mixins or input listeners.

5. **Job-Specific Entrypoint Validation**:
   - Every public automation entrypoint MUST validate necessary preconditions before spawning background coroutines.
   - Farming macro requires Garden location, valid player/level, matching hoe, and Squeaky Mousemat. If Auto Pester is enabled, vacuum presence is also verified upfront.
   - Pest Destroyer requires Garden location and valid vacuum on hotbar.
   - Auto Bouncy Ball requires valid player/level and Bouncy Beach Ball on hotbar.

### Anti-Detection and Failsafe Rules
1. **Hardware GCD Sensitivity Quantization**:
   All camera rotations must be quantized through Minecraft hardware mouse sensitivity curves to prevent anticheat detection signatures:
   ```
   multiplier = sensitivity * 0.6 + 0.2
   gcd = multiplier * multiplier * multiplier * 1.2
   delta = wrapDegrees(targetAngle - currentAngle)
   quantizedDelta = round(delta / gcd) * gcd
   newAngle = currentAngle + quantizedDelta
   ```

2. **Continuous Delta Bezier Trajectories**:
   - All Bezier control points must be calculated relative to `startYaw` and `startPitch` in continuous delta space to eliminate 360-degree wrapping spin bugs.
   - Inject realistic human micro-tremors (8Hz to 20Hz with amplitudes of +-0.16 deg yaw and +-0.10 deg pitch).
   - In high precision mode, when within 15 degrees of target, insert a 300ms pause and reduce rotation velocity by 20x for human micro-adjustments.
   - For real-time flight tracking, apply critically damped spring kinematics (`damping = 1.0`, `omega = 7.0 + (dpi / 20) * 9.0`) without angular snapping.

3. **Forced Rotation Check with Rolling Baseline**:
   - Maintain a rolling 5-sample baseline of player yaw and pitch.
   - If an external angle delta exceeds 5.0 degrees and persists longer than 400ms, immediately trigger macro abort, screen unlock, and sound the non-attenuated alarm.
   - If the angle returns to normal within 400ms (such as minor network jitter), log a watchdog alert without reacting.

4. **Teleport Distance Detection**:
   - Hook `ClientPacketListener.handleMovePlayer` (`ClientboundPlayerPositionPacket`).
   - If server position delta exceeds 6.0 blocks, trigger an immediate emergency script abort, unlock user input, and play the high-priority alarm sound.

5. **Randomized Hotbar Slot Validation**:
   - Monitor `player.inventory.selectedSlot` against the expected tool slot.
   - Trigger abort if mismatch persists across 2 to 4 consecutive ticks to prevent empty-hand farming.

6. **Unfamiliar GUI & Captcha Detection**:
   - Hook `Minecraft.setScreen` to monitor incoming container screens.
   - Allow screens to render for user visibility while immediately halting automated inputs and sounding the alert.

7. **Server Restart Protection**:
   - Regex match incoming chat packets for `.*server (?:closing|reboot).*`.
   - Cleanly stop macro, pause 2000ms, and dispatch `/hub`.

8. **Airborne Stall Recovery**:
   - `AntiStuckEngine` pulses sneak (Shift) for 200ms to 600ms to safely land flying or falling players before macro startup. Abort startup if ungrounded after 25 attempts.

### Vector Rendering Rules
- **Use Vanilla Gizmos**: All in-world debug visuals, bounding boxes, trajectories, and search nodes must use Minecraft 26.2's `net.minecraft.gizmos.Gizmos` and `net.minecraft.gizmos.GizmoStyle` (`ARGB.color`).
- **No Legacy Immediate Mode OpenGL**: Never use direct GL11, GL15, Tessellator, or BufferBuilder matrix manipulation.
- **Pest ESP Styling**: Render red bounding cuboids with `setAlwaysOnTop()` enabled for x-ray visibility through blocks.
- **Pathfinding Visualizer Styling**: Render active exploration trees with yellow for expanding nodes, green for open candidate nodes, red for obstructed nodes, and cyan for final selected flight paths.

### Input and Key Injection Rules
- **Virtual Input via MacroInputController**: All bot actions and movement states must be coordinated through `MacroInputController`.
- **Impulse Vector Injection**: Inject virtual inputs into `ClientInput.keyPresses` (`Input`) and `ClientInput.moveVector` (`Vec2`) via `KeyboardInputMixin` without generating synthetic OS events.
- **Input Conflict Prevention**: Use `KeyboardMixin` and `MouseMixin` to block physical keyboard and mouse inputs during locked macro execution while preserving access to essential controls (ESC, F1, F3, F11, inventory, chat).
- **Continuous Attack and Use Loops**: Drive block breaking and vacuum usage loops via `MinecraftMixin` invokers (`invokeStartAttack`, `invokeContinueAttack`, `invokeStartUseItem`) with `missTime` reset to 0 even during open chat screens.

---

## Conventions & Structure

### Complete Workspace Directory Map

#### 1. Root Files
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `build.gradle.kts` | Kotlin DSL Gradle build script | Configures Fabric Loom 1.15.5, Kotlin 2.4.10, Java 25 compiler options, Fabric API, and Kotlinx serialization |
| `settings.gradle.kts` | Gradle settings file | Declares root project name as `hypcro` and sets up Maven repository endpoints |
| `gradle.properties` | JVM and Loom build properties | Sets `fabric.loom.disableObfuscation=true` for unobfuscated Mojang mappings |
| `gradlew.bat` | Windows Gradle wrapper batch script | Enables reproducible Gradle builds without requiring pre-installed Gradle |
| `GEMINI.md` | Workspace specifications, toolchain targets, and ground truth rules | Defines core architecture guidelines, JDK runtime (Azul Zulu 25), and Minecraft 26.2 protocol |
| `PROJECT.md` | Project architecture and milestone roadmap | Tracks subsystem boundaries, interface contracts, feature inventory, and code layout |
| `README.md` | Project documentation and feature overview | Explains features (GUI, mouse engine, pathfinding, pest destroyer), commands, and build steps |
| `ICON.svg` | Scalable Vector Graphics mod icon | Vector artwork used for branding and UI design assets |
| `icon.png` | Raster icon asset (37 KB) | High-resolution bitmap icon packaged into mod JAR and distribution metadata |
| `fix.md` | Development scratchpad note | Documents a known bug regarding pest death identification via UUID on bats |
| `futurePlan.md` | Project roadmap and feature planning document | Catalogs planned enhancements including W/S movement humanization, persistent HUD, and failsafes |
| `plan.md` | Staff-check vulnerability catalog and defense strategies | Details admin inspection vectors (block barriers, bait entities, vehicle traps) and defensive mod architecture |
| `patch.py` | Python automation patching script | Applies programmatic code edits to `CentralMovementCoordinator.kt` for flight activation and deviation logic |
| `scratch_mouse.kt` | Kotlin reflection inspection script | Standalone utility to print method names from Minecraft `MouseHandler` class during development |
| `scratch_test.kt` | Kotlin reflection inspection script | Standalone utility to inspect Minecraft `KeyEvent` method signatures |
| `test_keymapping.kt` | Kotlin reflection inspection script | Standalone utility to inspect Minecraft `KeyMapping` method signatures |
| `.gitignore` | Git ignore rules | Excludes build artifacts, Gradle daemon files, Kotlin cache, virtual environments, and IDE metadata |

#### 2. Gradle Wrapper (`gradle/`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle wrapper executable archive | Bootstraps Gradle execution and downloads designated Gradle version if missing |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle wrapper version configuration | Configures wrapper to download and execute Gradle 9.2.0-bin |

#### 3. UI Image Assets (`img/`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `img/Carrot.png` | 16x16 Carrot texture sprite | Visual icon representing Carrot crop in UI |
| `img/Nether_Wart.png` | 16x16 Nether Wart texture sprite | Visual icon representing Nether Wart crop in UI |
| `img/Potato.png` | 16x16 Potato texture sprite | Visual icon representing Potato crop in UI |
| `img/Wheat.png` | 16x16 Wheat texture sprite | Visual icon representing Wheat crop in UI |

#### 4. Legacy Native & Python Controller (`.legacy/`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `.legacy/app.py` | PyQt5 desktop GUI application | Legacy user interface for configuring bot settings, macros, and overlay |
| `.legacy/bg_controller.py` | Background input simulator | Sends background Windows mouse and keyboard messages to the game window |
| `.legacy/icons.py` | Base64 encoded icon repository | Provides embedded image resources for PyQt5 legacy interface widgets |
| `.legacy/injector.py` | Windows DLL injector utility | Injects `telemetry_bridge.dll` into running `javaw.exe` Minecraft process |
| `.legacy/mem_scanner.py` | Windows memory scanner | Scans Minecraft JVM memory to locate player position and yaw/pitch pointers |
| `.legacy/widgets.py` | Custom PyQt5 UI widgets | Implements sliders, toggles, and styled components for legacy desktop application |
| `.legacy/telemetry_bridge.dll` | Compiled native C++ telemetry bridge DLL | Injected DLL that reads JVM memory and emits UDP telemetry packets |
| `.legacy/telemetry_bridge_v2.dll` | Updated native telemetry bridge DLL | Version 2 of injected DLL with enhanced JVM pointer scanning |
| `.legacy/native/bridge.cpp` | C++ JNI bridge source code | Attaches to JVM via `JNI_GetCreatedJavaVMs` and transmits player coordinates over UDP socket |
| `.legacy/native/jni_headers.h` | JNI structure declarations header | Minimal custom JNI header avoiding external JDK include requirements during C++ build |

#### 5. Java Mixin Bytecode Injectors (`src/main/java/com/hypcro/mixins/`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/java/com/hypcro/mixins/CameraMixin.java` | Bytecode injector into `net.minecraft.client.Camera` | Overrides camera yaw and pitch when FreeLook is active and bypasses third-person camera block collision raycasting |
| `src/main/java/com/hypcro/mixins/ClientPacketListenerMixin.java` | Bytecode injector into `ClientPacketListener` | Intercepts `ClientboundPlayerPositionPacket` for teleport watchdog checks and blocks user chat/commands when input lock is enabled |
| `src/main/java/com/hypcro/mixins/CommandSuggestionsMixin.java` | Bytecode injector into `CommandSuggestions` | Provides custom client-side tab completion suggestions for all `.hypcro` dot-commands |
| `src/main/java/com/hypcro/mixins/KeyboardInputMixin.java` | Bytecode injector into `KeyboardInput` | Injects virtual movement keypresses and directional impulses from `MacroInputController` into player input calculations |
| `src/main/java/com/hypcro/mixins/KeyboardMixin.java` | Bytecode injector into `KeyboardHandler` | Blocks physical keyboard inputs during active macros according to Input Lock configuration and routes global stops |
| `src/main/java/com/hypcro/mixins/LocalPlayerMixin.java` | Bytecode injector into `LocalPlayer` | Overrides client-side sprint input states in `aiStep` and enforces sprinting cancellation when virtual sprint is released |
| `src/main/java/com/hypcro/mixins/MinecraftMixin.java` | Bytecode injector and invoker for `Minecraft` | Exposes invokers for attack and item use loops, tick-syncs key states during chat screens, and triggers watchdog on unfamiliar GUI popups |
| `src/main/java/com/hypcro/mixins/MouseMixin.java` | Bytecode injector into `MouseHandler` | Intercepts mouse scroll and cursor movement for FreeLook smooth zoom and camera lock enforcement via `MacroController.isAnyMacroActive()` |
| `src/main/java/com/hypcro/mixins/OptionsAccessor.java` | Mixin accessor interface for `Options` | Exposes getters for `invertXMouse` and `invertYMouse` option instances |

#### 6. Kotlin Source Code (`src/main/kotlin/com/hypcro/`)

##### Root Entrypoint (`com.hypcro`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/HypCroMod.kt` | Mod entrypoint implementing `ClientModInitializer` | Registers keybinds (`END` for GUI, `V` for FreeLook), registers client tick listeners, routes dot-commands, and provides formatted in-game logging |

##### Bouncy Beach Ball Subsystem (`com.hypcro.bouncy`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/bouncy/AutoBouncyBall.kt` | Autonomous beach ball bouncing minigame bot | Tracks falling ArmorStand balls, predicts landing coordinates, and drives keyboard strafe movement without rotating the player camera |

##### Camera Subsystem (`com.hypcro.camera`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/camera/FreeLookManager.kt` | Decoupled 360-degree third-person camera manager | Maintains independent free yaw/pitch angles, handles smooth scroll zooming (up to 50 blocks), respects mouse sensitivity curves, and persists zoom settings |

##### Configuration Subsystem (`com.hypcro.config`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/config/ConfigManager.kt` | Thread-safe configuration manager | Handles JSON serialization/deserialization via Kotlinx Serialization and writes asynchronously to `.minecraft/config/hypcro.json` |
| `src/main/kotlin/com/hypcro/config/CropType.kt` | Crop enumeration | Defines supported crops: `WHEAT`, `CARROT`, `POTATO`, `NETHER_WART`, `MUSHROOM` |
| `src/main/kotlin/com/hypcro/config/FarmConfig.kt` | Configuration data structures | Defines schema for angles, mouse kinematics, visuals, QOL, anti-stuck, watchdog, input lock, pest destroyer, bouncy ball, and mode profiles |
| `src/main/kotlin/com/hypcro/config/FarmMode.kt` | Farming mode enumeration | Defines supported macro modes: `WS` (W/S linear farming) and `VERTICAL` (Vertical farming) |

##### Failsafe Subsystem (`com.hypcro.failsafe`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/failsafe/AntiStuckEngine.kt` | Airborne state detector and ground recovery | Checks if the player is flying or falling before starting a macro and sneaks down to ground safely |
| `src/main/kotlin/com/hypcro/failsafe/HypcroWatchdog.kt` | Multi-layered watchdog monitoring engine | Real-time surveillance of forced server rotations with 400ms debounce, unexpected hotbar slot changes, teleport distance checks (6b threshold), server restart messages, and non-attenuated alarm audio |

##### Farming Macro Subsystem (`com.hypcro.farming`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/farming/FarmEngineHelper.kt` | Farming environmental helper | Centralizes forward crop block raycasting and lower bounding box water immersion checks for all farm engines |
| `src/main/kotlin/com/hypcro/farming/IFarmEngine.kt` | Farm engine interface | Declares standard lifecycle methods (`startMacro`, `stopMacro`, `abortScript`, `onClientTick`, `detectCrop`) and angle status |
| `src/main/kotlin/com/hypcro/farming/MacroController.kt` | Central macro orchestrator and state coordinator | Enforces Garden area verification, manages global bot states (`isAnyMacroActive`), coordinates master stops (`stopAllMacros`), and routes client ticks |
| `src/main/kotlin/com/hypcro/farming/MacroInputController.kt` | Virtual input coordinator | Controls virtual key states (W, S, A, D, Jump, Shift, Sprint, Attack, UseItem) and calculates movement impulse vectors for mixin injection |
| `src/main/kotlin/com/hypcro/farming/MousematHelper.kt` | Squeaky Mousemat automation utility | Reads lore NBT on hotbar mousemat items, sends `/setyaw` and `/setpitch` commands, and performs physical left-clicks to align angles |
| `src/main/kotlin/com/hypcro/farming/VerticalCropFarmEngine.kt` | Vertical crop farming engine stub | Placeholder engine for upcoming vertical farm mechanics |
| `src/main/kotlin/com/hypcro/farming/WSFarmEngine.kt` | Primary W/S crop farming implementation | Implements W/S movement loops, automatically selects hoes via `SkyBlockItemHelper`, aligns angles via `AngleUtils`, and manages wall collision turning |

##### User Interface Subsystem (`com.hypcro.gui` & `com.hypcro.gui.widgets`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/gui/CropSettingsModal.kt` | Modal configuration screen for crop angles and speeds | Allows setting global and per-crop yaw, pitch, and speed values |
| `src/main/kotlin/com/hypcro/gui/MainFarmingScreen.kt` | Primary mod dashboard GUI | Full-screen interactive dashboard with sidebar navigation, sub-tab navigation pills, status cards, mode dropdowns, and settings widgets |
| `src/main/kotlin/com/hypcro/gui/widgets/DualRangeSliderWidget.kt` | Dual-thumb range slider UI widget | Controls minimum and maximum pest count thresholds for automated pest sweeps |
| `src/main/kotlin/com/hypcro/gui/widgets/InfoIconWidget.kt` | Informational tooltip icon widget | Displays explanatory help text upon mouse hover |
| `src/main/kotlin/com/hypcro/gui/widgets/PillToggleWidget.kt` | Segmented pill button selector widget | Provides compact multi-option switching for boolean toggles and enum settings |
| `src/main/kotlin/com/hypcro/gui/widgets/PlotGridModal.kt` | 5x5 Garden Plot selector modal | Renders interactive 5x5 plot layout for configuring teleportable and preserved pest plots |
| `src/main/kotlin/com/hypcro/gui/widgets/SectionBoxWidget.kt` | Outlined section container widget | Groups related configuration options under distinct visual headers |
| `src/main/kotlin/com/hypcro/gui/widgets/SingleSliderWidget.kt` | Single-thumb numeric slider widget | Adjusts numeric settings such as DPI speed and speeds |

##### Input Subsystem (`com.hypcro.input`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/input/CommandHelper.kt` | Client command helper | Dispatches client chat commands with randomized humanized typing duration simulations |

##### Movement & Kinematics Subsystem (`com.hypcro.movement`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/movement/CentralMovementCoordinator.kt` | Autonomous 3D flight execution coordinator | Manages flight activation, path waypoint following, look-ahead heading, 35-degree corner braking, straightaway sprint boosting, and deviation recomputations |
| `src/main/kotlin/com/hypcro/movement/MouseMovementEngine.kt` | Realistic human mouse simulation engine | Implements Simple, Bezier, and GCD rotation modes, hardware cursor quantization, human micro-tremor vibrations, and critically damped spring flight tracking |

##### Pathfinding Suite (`com.hypcro.pathfinding`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/pathfinding/IPathfinder.kt` | Pathfinding algorithm interface | Declares `computePath(level, start, destination)` contract for 3D flight algorithms |
| `src/main/kotlin/com/hypcro/pathfinding/AStar3DSmoothedPathfinder.kt` | 3D A* pathfinder with string-pulling smoothing | Generates 26-directional grid paths and applies raycast smoothing |
| `src/main/kotlin/com/hypcro/pathfinding/PathfindingVisualizer.kt` | 3D Gizmo pathfinding visualizer | Renders in-world waypoints, goal markers, flight paths, and verbose exploration branches |
| `src/main/kotlin/com/hypcro/pathfinding/RRTStarPathfinder.kt` | BIT* (Batch Informed Trees) sampling pathfinder | Implements informed ellipsoid sampling, lazy collision checks, and iterative tree rewiring |
| `src/main/kotlin/com/hypcro/pathfinding/ThetaStarPathfinder.kt` | Any-angle 3D Theta* pathfinder | Computes smooth line-of-sight paths across 3D space with high-precision sub-step collision checks and ground clearance penalties |

##### Pest Destroyer Subsystem (`com.hypcro.pest`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/pest/PestDestroyerEngine.kt` | Autonomous Garden pest extermination state machine | Controls lifecycle from `/setspawn` to Tablist/Scoreboard plot routing, high-altitude transit, vacuum firing, UUID death polling, and `/warp garden` return |
| `src/main/kotlin/com/hypcro/pest/PestESP.kt` | In-world 3D Pest ESP visualizer | Renders red highlighted bounding boxes around detected pests through walls |
| `src/main/kotlin/com/hypcro/pest/PestTabReader.kt` | Pest-specific tablist and scoreboard scanner | Parses pest counts and infested plot IDs from sidebar and tablist via `GardenStateReader` |
| `src/main/kotlin/com/hypcro/pest/PestTargetTracker.kt` | Pest entity tracking and targeting engine | Identifies pests by name matching and UUID session memory, tracks skull markers, and calculates safe radial angles to prevent collateral kills |
| `src/main/kotlin/com/hypcro/pest/PlotCoordinateData.kt` | Garden plot coordinate database | Static mapping of center coordinates and teleport positions for all 24 Garden plots and center barn |

##### Shared Utilities Subsystem (`com.hypcro.util`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/kotlin/com/hypcro/util/AngleUtils.kt` | Canonical angle geometry and distance utilities | Provides normalized angle differences, pitch/yaw delta comparisons, and angular distance calculations using `Mth.wrapDegrees` |
| `src/main/kotlin/com/hypcro/util/GardenStateReader.kt` | Centralized SkyBlock text and scoreboard scraper | Extracts clean unformatted lines from tablist and sidebar scoreboard, and validates Garden presence |
| `src/main/kotlin/com/hypcro/util/SkyBlockItemHelper.kt` | Centralized SkyBlock item and NBT ExtraAttributes helper | Resolves ExtraAttributes IDs, locates farming hoes, determines vacuum tiers (T1-T5), and detects mousemats and beach balls |

#### 7. Mod Resources & Metadata (`src/main/resources/`)
| File Path | Primary Function | Architectural Role |
| :--- | :--- | :--- |
| `src/main/resources/fabric.mod.json` | Fabric mod metadata manifest | Declares mod ID `hypcro`, version, entrypoints, Kotlin adapter, and dependencies |
| `src/main/resources/hypcro.mixins.json` | Mixin configuration manifest | Declares mixin package `com.hypcro.mixins`, Java 25 compatibility level, and client mixin classes |
| `src/main/resources/assets/hypcro/lang/en_us.json` | English localization bundle | Provides translations for keybind categories and names |
| `src/main/resources/assets/hypcro/textures/gui/icon.png` | GUI mod logo texture | Texture displayed in top header of in-game screen |
| `src/main/resources/assets/hypcro/textures/gui/crops/carrot.png` | GUI crop sprite texture | Texture for Carrot crop button |
| `src/main/resources/assets/hypcro/textures/gui/crops/nether_wart.png` | GUI crop sprite texture | Texture for Nether Wart crop button |
| `src/main/resources/assets/hypcro/textures/gui/crops/potato.png` | GUI crop sprite texture | Texture for Potato crop button |
| `src/main/resources/assets/hypcro/textures/gui/crops/wheat.png` | GUI crop sprite texture | Texture for Wheat crop button |

#### 8. Reference Codebases (`Learn/`)

##### `Learn/FarmHelper` (Forge 1.8.9 FarmHelper v2)
Legacy Forge 1.8.9 reference codebase containing patterns for crop handling, failsafe triggers, angle rotation kinematics, and remote websocket controls:
- `com.jelly.farmhelperv2.command`: In-game commands (`/farmhelper`, `/rewarp`)
- `com.jelly.farmhelperv2.config`: Monolithic Vigilance configuration trees and webhook payloads
- `com.jelly.farmhelperv2.event`: Custom Forge event hooks for packet, motion, block, and scoreboard events
- `com.jelly.farmhelperv2.failsafe`: 16 discrete failsafes (BedrockCage, Cobweb, Dirt, Evacuate, GuestVisit, Knockback, Rotation, Teleport, etc.)
- `com.jelly.farmhelperv2.feature`: High-level features (AntiStuck, AutoBazaar, AutoGodPot, PestFarmer, PestsDestroyer, PiPMode, Scheduler)
- `com.jelly.farmhelperv2.gui`: Essential/Vigilance GUI wrappers and proxy managers
- `com.jelly.farmhelperv2.handler`: GameState, Baritone, Macro, and Rotation handlers
- `com.jelly.farmhelperv2.hud`: HUD overlays for debug metrics, profits, and runtime status
- `com.jelly.farmhelperv2.macro`: S-Shape and circular macro execution loops
- `com.jelly.farmhelperv2.pathfinder`: Baritone wrapper for flying and walking path execution
- `com.jelly.farmhelperv2.remote`: Discord bot integration and remote websocket command system
- `com.jelly.farmhelperv2.util`: Math, angle, block, player, and reflection utilities

##### `Learn/SkyHanni` (Forge 1.8.9 SkyHanni Mod)
Modern SkyBlock helper mod reference containing rich text parsing, regex item filters, inventory detection, and island state tracking.

##### `Learn/aether` (Fabric 26.2 / 1.21.x Aether Mod)
Modern Fabric reference codebase demonstrating modern Java 25 architectural patterns, mixin hooks, and GUI rendering:
- `dev.aether.bootstrap`: Mod initialization, command registrar, keybind registry, and client tick events
- `dev.aether.config`: Profile-based configuration entries, farming presets, and humanization settings
- `dev.aether.hud`: HUD element framework and overlay status providers
- `dev.aether.macro`: Macro worker threads and state machine lifecycle managers
- `dev.aether.mixin`: Mixin hooks into Minecraft client, rendering, input, and networking
- `dev.aether.modules`: Modular feature implementations (Failsafes, Composter, Pest Clearing, Rewarp, Rotation)
- `dev.aether.modules.pathfinding`: 3D A* navigation engine with Etherwarp teleport integration
- `dev.aether.ui`: Modular UI system with flat panels, chrome bars, and custom components

---

### Architectural Summary & Submodule Interconnections

```
[ Minecraft Client / Fabric Lifecycle ]
                   │
                   ▼
          [ HypCroMod.kt ] (Keybinds, Tick Listeners, Dot Commands)
                   │
   ┌───────────────┼────────────────────────┐
   ▼               ▼                        ▼
[ GUI Dashboard ] [ HypcroWatchdog ]       [ MacroController ] ◄── [ Master Lifecycle & Stop ]
(MainFarmingScreen) (Packet & State Checks)         │
   │               │                        ┌───────┴───────┬───────────────────┐
   │               │                        ▼               ▼                   ▼
   │               │               [ WSFarmEngine ] [ PestDestroyerEngine ] [ AutoBouncyBall ]
   │               │                        │               │                   │
   │               │                        ▼               ▼                   │
   │               │          [ CentralMovementCoordinator ]                    │
   │               │                        │                                   │
   │               │          ┌─────────────┼─────────────┐                     │
   │               │          ▼             ▼             ▼                     │
   │               │     [ Theta* ]    [ 3D A* ]      [ BIT* ]                  │
   │               │          │             │             │                     │
   │               │          └─────────────┼─────────────┘                     │
   │               │                        ▼                                   │
   │               │            [ MouseMovementEngine ]                         │
   │               │            (GCD, Bezier, Spring)                           │
   │               │                        │                                   │
   ▼               ▼                        ▼                                   ▼
[ ConfigManager ] ◄──────────────── [ MacroInputController ] ◄──────────────────┘
(Async JSON IO)                             │
                                            ▼
                              [ Fabric Mixin Injections ]
                              (KeyboardInputMixin, MouseMixin,
                               KeyboardMixin, MinecraftMixin)
                                            │
                                            ▼
                              [ Minecraft 26.2 Client ]
                                            │
                                            ▼
                              [ Shared Utilities Foundation ]
                              (SkyBlockItemHelper, AngleUtils, GardenStateReader)
```

1. **User Interaction & Configuration**:
   The user presses `END` to open `MainFarmingScreen`. Modifications to settings are immediately committed to memory and asynchronously written to `.minecraft/config/hypcro.json` via `ConfigManager` without render hitching.

2. **Master Macro Lifecycle & Farming Loop**:
   When toggled on, `MacroController` validates the current SkyBlock location (`Garden` required) and enforces required items (farming hoe, Squeaky Mousemat, and vacuum if Auto Pester is active). It initializes the selected engine (`WSFarmEngine`) which evaluates crops ahead, equips the appropriate tool using `SkyBlockItemHelper`, aligns the player's yaw and pitch via `AngleUtils`, and asserts virtual inputs in `MacroInputController`.

3. **Autonomous Pest Routing & Pathfinding**:
   When pests reach the configured threshold, `PestDestroyerEngine` takes control. It validates vacuum presence via `SkyBlockItemHelper`, issues `/setspawn`, reads infested plot coordinates from `GardenStateReader`, and requests a 3D flight trajectory from `ThetaStarPathfinder`, `AStar3DSmoothedPathfinder`, or `RRTStarPathfinder`. `CentralMovementCoordinator` executes the flight path with 35-degree corner braking and continuous look-ahead heading.

4. **Camera & Kinematic Steering**:
   Angle adjustments during macroing and in-flight navigation pass through `MouseMovementEngine`, which enforces hardware GCD sensitivity quantization, cubic Bezier smoothing, human micro-tremors, and spring damping. In-world visuals are rendered via `Gizmos` (`PestESP`, `PathfindingVisualizer`, and `AutoBouncyBall`).

5. **Watchdog Protection & Master Stops**:
   `HypcroWatchdog` runs continuously across all states. If an admin applies a forced rotation exceeding 5 degrees for over 400ms, or teleports the player further than 6.0 blocks, the watchdog aborts all active routines via `MacroController.stopAllMacros()`, restores physical input, and sounds an audible alarm.

---

### Code and Naming Conventions

1. **Kotlin Style & Structure**:
   - Class and Object names use `PascalCase` (e.g. `WSFarmEngine`, `MouseMovementEngine`, `SkyBlockItemHelper`).
   - Function and property names use `camelCase` (e.g. `startMacro`, `quantizedDelta`, `isAnyMacroActive`).
   - Constant values and enum entries use `UPPER_SNAKE_CASE` (e.g. `CROP_WHEAT`, `CORNER_BRAKE_ANGLE`).
   - Package names use lowercase letters without underscores (e.g. `com.hypcro.util`, `com.hypcro.bouncy`).

2. **Concurrency & Threading**:
   - Singletons and long-lived services are declared as Kotlin `object` instances.
   - Long-running or asynchronous operations (such as configuration file saving) use `kotlinx.coroutines` with `Dispatchers.IO` and `SupervisorJob` to prevent blocking the Minecraft render loop.

3. **Mixin Design**:
   - Mixin classes reside in `com.hypcro.mixins` and target compatibility level `JAVA_25`.
   - Never generate synthetic OS key events. Always inject virtual state into `ClientInput.keyPresses` and `ClientInput.moveVector`.
   - Keep mixin injection methods lean, delegating complex logic to `MacroController` or appropriate Kotlin singletons.

4. **Formatting Rules**:
   - Do not use semicolons anywhere in Kotlin code, comments, or documentation text.
   - Do not use em-dashes anywhere in source code comments, descriptions, or markdown documentation.
