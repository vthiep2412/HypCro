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
data class QOLConfig(
    var freeLookMode: String = "HOLD",
    var freeLookInvertZoom: Boolean = false,
    var freeLookRememberZoom: Boolean = false,
    var freeLookSavedZoom: Float = 4.0f,
    var freeLookRespectInvertMouse: String = "ON" // "OFF", "ON", "ALWAYS"
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
    var antiStuck: AntiStuckConfig = AntiStuckConfig(),
    var watchdog: WatchDogConfig = WatchDogConfig(),
    var inputLock: InputLockConfig = InputLockConfig()
)

@Serializable
data class FarmConfig(
    var activeMethod: String = "WS",
    var wsConfig: ModeConfig = ModeConfig(),
    var verticalConfig: ModeConfig = ModeConfig(),
    var qolConfig: QOLConfig = QOLConfig(),
    var generalConfig: GeneralConfig = GeneralConfig()
)
