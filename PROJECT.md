# Project: HypCro

## Architecture
HypCro is a Hypixel Skyblock Garden farming helper mod built natively for Minecraft 26.1.2 using Fabric Loader (0.16.0+) and Kotlin 2.4.10 on Java 25.

### Subsystem Boundaries and Data Flow
1. **Toolchain & Runtime**:
   - Fabric Loom 1.15.5 with `fabric.loom.disableObfuscation=true`
   - Minecraft 26.1.2 unobfuscated Mojang mappings (`com.mojang:minecraft:26.1.2`)
   - Kotlin 2.4.10 (`fabric-language-kotlin:1.13.13+kotlin.2.4.10`)
   - JDK Runtime Azul Zulu 25 (`Java 25.0.1`) targeting JVM 25 across JavaCompile, KotlinCompile, and Mixin compatibility
   - Gradle 9.2.0 wrapper

2. **Core Subsystems**:
   - **Entrypoint & Event Bus** (`com.hypcro.HypCroMod`): Registers keybindings (END for GUI, V for FreeLook), client tick listeners, and dot-commands.
   - **Macro Engine & Input Coordination** (`com.hypcro.farming`): Manages macro lifecycles via `IFarmEngine`, `MacroController`, `WSFarmEngine`, `VerticalCropFarmEngine`, `MousematHelper`, and injects input impulses into `ClientInput` via `MacroInputController` and `KeyboardInputMixin`.
   - **Anti-Detection Kinematics** (`com.hypcro.movement.MouseMovementEngine`): Hardware mouse sensitivity GCD quantization, continuous delta Bezier control points to avoid 360-degree flip bugs, spring physics damping, and precision slowdown when within 15 degrees of target.
   - **3D Pathfinding Suite** (`com.hypcro.pathfinding` & `com.hypcro.movement.CentralMovementCoordinator`): Native Theta*, smoothed A*, and BIT* (Batch Informed Trees) 3D flight pathfinders with sub-step raycasting, clearance penalties, 35-degree corner braking, and 350ms stuck recomputation.
   - **Pest Destroyer Suite** (`com.hypcro.pest`): Autonomous 24-plot routing, vacuum tier detection (T1 to T5), radial safe shooting angles with clearance cones, and return warp handlers.
   - **Failsafes & Watchdog** (`com.hypcro.failsafe.HypcroWatchdog`): Forced rotation check with rolling baseline and 400ms debounce, teleport distance check with 6b threshold, and randomized hotbar mismatch checks.
   - **Rendering & HUD** (`com.hypcro.pest.PestESP`, `com.hypcro.pathfinding.PathfindingVisualizer`): Native vector rendering via `net.minecraft.gizmos.Gizmos` and `net.minecraft.gizmos.GizmoStyle`.
   - **Camera Management** (`com.hypcro.camera.FreeLookManager` & `CameraMixin`): Decoupled 360-degree FreeLook with smooth scroll zoom up to 50 blocks.
   - **User Interface** (`com.hypcro.gui` & `com.hypcro.gui.widgets`): Modern GUI dashboard with tab pills, plot grid selector, dual-range sliders, and crop settings modals.
   - **Configuration** (`com.hypcro.config`): JSON serialization via Kotlinx Serialization writing asynchronously to `.minecraft/config/hypcro.json`.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Toolchain & Build Specification | Document exact Gradle 9.2.0, Java 25, Kotlin 2.4.10, Loom 1.15.5, and Minecraft 26.1.2 toolchain | M1 | survey |
| 2 | Workspace Rules & Protocols | Document Minecraft API Research & Ground Truth Protocol, Loom cache deobf jar inspection, comment preservation, and strict guidelines | M1 | survey |
| 3 | Comprehensive Directory Map | Exhaustive file and directory mapping for all root, gradle, src, resources, legacy, and reference directories | M1 | survey |
| 4 | Architecture & Subsystem Specification | Complete architectural documentation of all 10 Kotlin packages, 9 Java Mixins, and reference Learn codebases | M1 | survey |
| 5 | Standardized GEMINI.md Generation | Generate and verify complete, production-grade GEMINI.md adhering to workspace structure | M1 | survey |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Generate GEMINI.md | Full repository audit documentation, toolchains, rules, protocols, and annotated file map | none | DONE |

## Interface Contracts
### Ground Truth Protocol <-> Loom Cache
- When inspecting vanilla Minecraft 26.1.2 classes, methods, or fields, inspect local deobfuscated JAR in Loom cache (`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-043a8b3edf/26.1.2/minecraft-merged-043a8b3edf-26.1.2.jar`) using `javap -p` or class decompilation.

### Mod Loader <-> Mixin Compatibility
- Compatibility level: `JAVA_25`
- Target mappings: Mojang 26.1.2 unobfuscated (`fabric.loom.disableObfuscation=true`)

## Code Layout
- `GEMINI.md`: Root configuration and rules reference
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: Build system files
- `src/main/java/com/hypcro/mixins/`: Java Mixin classes
- `src/main/kotlin/com/hypcro/`: Kotlin mod source code (10 functional packages)
- `src/main/resources/`: Metadata, mixin configs, assets, and localization
- `Learn/`: External reference codebases (FarmHelper v2 and Aether)
