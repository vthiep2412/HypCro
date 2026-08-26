# HypCro - Fabric Kotlin SkyBlock Farm Helper

A client-side Fabric mod for Hypixel SkyBlock Garden farming with zero memory injection, realistic GCD mouse movement, 3D flying pathfinding, autonomous Pest Destroyer, and multi-threaded Hypcro Watchdog failsafes.

## Features
- **In-Game GUI**: Open with `.hypcro` in chat or the `END` key (with live in-game tab completion via `.h`).
- **Mouse Movement Engine**:
  - Exact pitch and yaw adjustments supporting Simple, Bezier (cubic curves with dynamic control points), and GCD modes.
  - Minecraft hardware cursor quantization (`sensitivity * 0.6 + 0.2` cubed * 1.2 step sizing).
  - Configurable human micro-vibration noise, target overshoot settling, and DPI speed scaling (1 to 100).
- **3D Flight & Pathfinding Suite**:
  - Three dedicated pathfinding algorithms: Theta* (any-angle line-of-sight), 3D A* with line-of-sight string-pulling smoothing, and BIT* (Batch Informed Trees) with lazy collision checks and tree rewiring.
  - Adaptive precision flight controller (loose arrival radius in transit, tightening to fine tolerance near target).
  - Automatic destination rotation lock and flight capability failsafe (`potentialStaffCheck("Can't fly :(")`).
- **Pest Destroyer**:
  - Autonomous plot routing across all 24 Garden plots.
  - Universal ArmorStand skull marker tracking for all pest variants.
  - Multi-layer kill confirmation, Vacuum hotbar management, keep-pest radial repositioning to prevent collateral multi-kills, and automatic `/warp garden` return with crop engine restoration.
  - 5x5 Garden Plot selector modals for teleportable plots and preserved pest plots.
- **Visuals & Debugging**:
  - In-world 3D Pest ESP (bounding boxes and distance tags).
  - Pathfinding Visualizer (3D Gizmo waypoints, flight trajectories, and goal markers).
- **Testing Command Suite**:
  - `.hypcrotest movecam <pitch> <yaw>`: Tests camera rotation to exact angles.
  - `.hypcrotest flyto <x> <y> <z> [pitch] [yaw]`: Executes 3D flight navigation (supports `~` for undefined coordinates).
  - `.hypcrotest pathfind <x> <y> <z>`: Computes path, benchmarks calculation time in milliseconds, and validates chunk boundaries.

## Build
To build the `.jar`:
```bash
./gradlew build
```
Drop the resulting `.jar` from `build/libs/` into `.minecraft/mods/`.

