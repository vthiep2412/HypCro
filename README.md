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
- **Auto Experiment Table Add-ons**:
  - Automated solver for Chronomatron and Ultrasequencer minigames before Superpairs.
  - Native table scanning within 4.5 blocks with humanized Bezier camera rotation via `MouseMovementEngine`.
  - Automatic highest Stakes tier selection (Metaphysical down to Beginner) with insufficient XP safety detection.
  - 100% focus-independent container slot clicking via Minecraft's native `slotClicked` invoker.
  - Configurable click speeds: Slow (250-350ms), Medium (130-200ms), and Fast (70-110ms) with randomized millisecond jitter.
  - **Maximize XP** toggle: stops at max bonus clicks cap (Round 12 Chrono, Round 10 Ultraseq) or solves continuously to maximize Enchanting XP.
  - Clean completion alert: closes all menus and plays 3 loud bell chimes (100ms apart at 1.5 volume) so you can roll Superpairs manually.
- **Persistent On-Screen HUD & BPS Tracker**:
  - Live in-game status card rendering current macro engine, active crop, uptime duration, instantaneous BPS, average BPS, and direction.
  - State-specific layouts for Farming, Auto Pester sweeps, Manual Pester, and Auto Bouncy Ball.
  - Sharp minimalist aesthetic with dynamic left/right border color responders (Green for active running, Yellow for warnings, Red for staff/watchdog alerts, and Gray for idle).
  - Dedicated **HUD** tab in the main deck with Macro Status toggle and Background Opacity slider (10% to 100%).
  - Interactive **HUD Editor** (`HudEditScreen`) with drag-to-move repositioning across all screen corners and scroll-wheel scaling (50% to 250%).
  - Centralized `CropBpsTracker` measuring instantaneous crop breaking rates and session averages.
- **Visuals & ESP Suite**:
  - **Player ESP**:
    - Automatic detection of party members via full chat regex matching (`PartyApi`).
    - Party members highlighted in bright **Green** (`#00FF00`) with Gizmo bounding boxes and `[PARTY]` billboard nametags through walls.
    - Other players highlighted in **Cyan** (`#00FFFF`) through walls with optional distance in meters `(24m)`.
    - Dedicated **Player ESP** card in the ESP tab with toggles for Party Player, Other Player, `  └ Show Distance:`, and clickable color swatches.
  - **Chest ESP & Crystal Hollows Lockpick Helper**:
    - **Crystal Hollows Active**: Automatically checks scoreboard/tablist for `Area: Crystal Hollows` so chest scanning and lockpick rendering run exclusively in the Crystal Hollows.
    - **Chest ESP**: Highlights nearby chests in **Warm Gold** (`#FFAA00`). Once opened by the player, normal chests are memorized in a blacklist so they stop rendering. Crystal Hollows lockpick chests automatically stay persistent.
    - **Auto Lockpick Chest Detection**: Automatically detects lockpick chests whenever `CRIT` particles spawn near a chest within 20 blocks of the player.
    - **Always-Visible Red Lockpick Helper**: Tracks `CRIT` particles from incoming packets and renders a small solid **Red** cube (`#FF0000`) on the exact sweet-spot aiming location with full X-ray visibility (`setAlwaysOnTop`).
    - Dedicated **Chest & Lockpick ESP** card in the ESP tab with toggles for Chest ESP, Lockpick Helper, and clickable color swatches.
  - **Jerry's Workshop White Gifts Waypoints**:
    - **Jerry's Workshop Active**: Exclusively active when in `Area: Jerry's Workshop` on tablist or scoreboard.
    - **20 Pre-Configured White Gift Locations**: Accurately pre-loaded with all 20 standard White Gift coordinates across rooftops, trees, cliffs, docks, and hidden alcoves.
    - **Un-Capped Distance Scaling**: Waypoint text size scales directly with distance without a maximum cap (`(0.045f * dist).coerceAtLeast(0.28f)`), canceling perspective shrink and maintaining crystal-clear readability from 5 to 300+ blocks.
    - **Auto-Hide on Collect**: Automatically marks gifts as collected when right-clicked or approached within 2 blocks so collected presents vanish from screen.
    - Dedicated **Jerry's Workshop** card in the ESP tab with White Gifts toggle, White (`#FFFFFF`) color swatch, live collected counter `(Collected: X / 20)`, and `Reset Collected` button.
  - **Party API**:
    - Ported from SkyHanni with comprehensive regex pattern matching for joins, leaves, kicks, offline kicks, voluntary/leave transfers, disbands, and party lists.
    - Dot-command query: `.hypcroparty`.
  - **Dungeon ESP**:
    - Dedicated Dungeon card in the ESP tab with 45Hz scan refresh and x-ray Gizmo vector bounding boxes up to 128 blocks away.
    - Independent toggles and customizable color swatches for Secret Bats (Brown default `#8B4513`), Starred Mobs (Orange default `#F57738`), Lost Adventurers (Gold Yellow default `#FEE15C`), Shadow Assassins (Purple default `#5B2CB2`), and Diamond Guys (Aqua default `#57C2F7`).
  - In-world 3D Pest ESP with custom Gizmo vector color styling (through-wall bounding boxes).
  - Interactive Color Picker Modal with a 2D Saturation/Value gradient canvas, vertical rainbow Hue slider, bidirectional Hex input box, and quick preset color chips.
  - Real-time synchronization between ESP sidebar tab and feature configs.
  - Pathfinding Visualizer with 3D Gizmo waypoints, flight trajectories, and goal markers embedded directly under Pathfinding settings.
- **Settings Sub-Tabs & Navigation**:
  - **Movement**: Mouse Movement configuration and 3D Pathfinding & Flying options.
  - **Failsafe**: WatchDog detection suite and Key & Mouse Lock options.
  - **QOL**:
    - **Free Look**: 360-degree third-person camera decoupling with smooth scroll zoom up to 50 blocks.
    - **Freecam**: Pure client-side no-clip camera flight through blocks via `U` keybind. Supports WASD movement, Space/Shift vertical flight, configurable flight speed (0.1x to 5.0x), and 1.2x sprint boost. Strictly zero angle snap on toggle off.
  - Dedicated **ESP**, **HUD**, and **Settings** navigation in the lower sidebar group.
- **Testing Command Suite**:
  - `.hypcrotest party`: Prints all currently tracked party members and party leader status.
  - `.hypcrotest currentyear`: Reads and prints the active SkyBlock Year from the sidebar scoreboard.
  - `.hypcrotest movecam <pitch> <yaw>`: Tests camera rotation to exact angles.
  - `.hypcrotest flyto <x> <y> <z> [pitch] [yaw]`: Executes 3D flight navigation (supports `~` for undefined coordinates).
  - `.hypcrotest pathfind <x> <y> <z>`: Computes path, benchmarks calculation time in milliseconds, and validates chunk boundaries.

## Build
To build the `.jar`:
```bash
./gradlew build
```
Drop the resulting `.jar` from `build/libs/` into `.minecraft/mods/`.

