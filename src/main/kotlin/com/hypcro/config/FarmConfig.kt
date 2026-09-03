package com.hypcro.config

import kotlinx.serialization.Serializable

@Serializable
data class AngleConfig(
    var yaw: Float = 0.0f,
    var pitch: Float = 0.0f
)

@Serializable
data class CropSetting(
    var useCustomAngles: Boolean = false,
    var yaw: Float = 0.0f,
    var pitch: Float = 0.0f,
    var useCustomSpeed: Boolean = false,
    var speed: Int = 100
)

@Serializable
data class ModeConfig(
    var globalAngles: AngleConfig = AngleConfig(),
    var globalSpeed: Int = 100,
    var crops: MutableMap<String, CropSetting> = mutableMapOf(
        "WHEAT" to CropSetting(useCustomAngles = true, yaw = -116.57f, pitch = 3.0f),
        "CARROT" to CropSetting(),
        "POTATO" to CropSetting(),
        "NETHER_WART" to CropSetting(),
        "MUSHROOM" to CropSetting()
    )
)

@Serializable
data class MouseMovementConfig(
    var humanize: Boolean = true,
    var highPrecision: Boolean = false,
    var movementType: String = "GCD", // "Simple", "Bezier", "GCD"
    var overshoot: Boolean = true,
    var dpiSpeed: Int = 10 // 1 to 20
)

@Serializable
data class VisualsConfig(
    var pathfindingVisualizer: Boolean = true,
    var verbosePathfindingVisual: Boolean = false
)

@Serializable
data class QOLConfig(
    var freeLookMode: String = "HOLD",
    var freeLookInvertZoom: Boolean = false,
    var freeLookRememberZoom: Boolean = false,
    var freeLookSavedZoom: Float = 4.0f,
    var freeLookRespectInvertMouse: String = "ON", // "OFF", "ON", "ALWAYS"
    var freecamSpeed: Double = 1.0,
    var freecamHideGui: Boolean = false,
    var autoSprint: Boolean = false,
    var fasterRClick: Boolean = false
)

@Serializable
data class AntiStuckConfig(
    var checkFlying: Boolean = true
)

@Serializable
data class WatchDogConfig(
    var checkRotation: Boolean = true,
    var debounceRotation: Boolean = true,
    var checkTeleport: Boolean = true,
    var checkHotbarSlot: Boolean = true,
    var checkFarmingInterruption: Boolean = true,
    var checkBpsDrop: Boolean = true,
    var checkUnfamiliarGui: Boolean = true
)

@Serializable
data class InputLockConfig(
    var lockHotbar: Boolean = true,
    var lockMovement: Boolean = true,
    var lockAllOtherKeybinds: Boolean = true,
    var lockMouse: Boolean = true,
    var blockChatAndCommands: Boolean = true
)

@Serializable
data class GeneralConfig(
    var mouseMovement: MouseMovementConfig = MouseMovementConfig(),
    var visuals: VisualsConfig = VisualsConfig(),
    var antiStuck: AntiStuckConfig = AntiStuckConfig(),
    var watchdog: WatchDogConfig = WatchDogConfig(),
    var inputLock: InputLockConfig = InputLockConfig()
)

@Serializable
data class PestDestroyerConfig(
    var pestEsp: Boolean = true,
    var pestEspColor: String = "#EF4444",
    var flightEngineVersion: String = "V2", // "V2" (Decoupled BetterBot), "CLASSIC" (Legacy)
    var pathfindingAlgorithm: String = "Theta*", // "Theta*", "3D A* with Smoothing", "BIT*"
    var bitStarTimeSeconds: Double = 1.0, // Computation time budget in seconds for BIT*
    var stopAfterDestination: Boolean = true,
    var getRooftop: Boolean = true,
    var teleportablePlots: MutableSet<Int> = (1..24).toMutableSet(),
    var keepPest: Boolean = false,
    var leavePestPlots: MutableSet<Int> = mutableSetOf(),
    var derpy: Boolean = false
)

@Serializable
enum class BouncyBallMode {
    CALM,
    AGGRESSIVE,
    SMART
}

@Serializable
data class BouncyBallConfig(
    var autoMove: Boolean = true,
    var mode: BouncyBallMode = BouncyBallMode.CALM,
    var smartOffset: Double = 0.06,
    var targetBounces: Int = 40,
    var goBackToStart: Boolean = true,
    var visualizeTrajectory: Boolean = true,
    var visualizeLandingBox: Boolean = true
)

@Serializable
data class DungeonConfig(
    var batEsp: Boolean = false,
    var batEspColor: String = "#8B4513",
    var starMobsEsp: Boolean = false,
    var starMobsEspColor: String = "#F57738",
    var minibossEsp: Boolean = false,
    var minibossColor: String = "#FFFF00"
)

@Serializable
data class HudConfig(
    var enabled: Boolean = true,
    var opacity: Float = 0.80f,
    var posX: Float = -1f,
    var posY: Float = -1f,
    var scale: Float = 1.0f
)

@Serializable
enum class ExperimentSpeed {
    SLOW,
    MEDIUM,
    FAST
}

@Serializable
data class ExperimentAddonsConfig(
    var speed: ExperimentSpeed = ExperimentSpeed.MEDIUM,
    var maximizeXp: Boolean = false
)

@Serializable
data class PlayerEspConfig(
    var enabled: Boolean = false,
    var partyEsp: Boolean = false,
    var partyColor: String = "#00FF00",
    var otherPlayerEsp: Boolean = false,
    var otherPlayerColor: String = "#00FFFF",
    var showDistance: Boolean = true,
    var renderNametags: Boolean = true
)

@Serializable
data class ChestEspConfig(
    var enabled: Boolean = false,
    var chestEsp: Boolean = false,
    var chestColor: String = "#FFAA00",
    var lockpickHelper: Boolean = false,
    var helperColor: String = "#FF0000"
)

@Serializable
data class JerryGiftsConfig(
    var enabled: Boolean = false,
    var color: String = "#FFFFFF",
    var lastSavedYear: Int = 0,
    var discoveredCoords: MutableMap<String, List<Double>> = mutableMapOf(),
    var collectedCoords: MutableSet<String> = mutableSetOf()
)

@Serializable
data class FarmConfig(
    var activeMethod: String = "WS",
    var autoActivePest: Boolean = false,
    var pestTriggerCount: Int = 4,
    var pestRangeMin: Int = 3,
    var pestRangeMax: Int = 4,
    var wsConfig: ModeConfig = ModeConfig(),
    var verticalConfig: ModeConfig = ModeConfig(),
    var qolConfig: QOLConfig = QOLConfig(),
    var generalConfig: GeneralConfig = GeneralConfig(),
    var pestDestroyer: PestDestroyerConfig = PestDestroyerConfig(),
    var bouncyBall: BouncyBallConfig = BouncyBallConfig(),
    var experimentAddons: ExperimentAddonsConfig = ExperimentAddonsConfig(),
    var dungeon: DungeonConfig = DungeonConfig(),
    var playerEsp: PlayerEspConfig = PlayerEspConfig(),
    var chestEsp: ChestEspConfig = ChestEspConfig(),
    var jerryGifts: JerryGiftsConfig = JerryGiftsConfig(),
    var hud: HudConfig = HudConfig()
)
