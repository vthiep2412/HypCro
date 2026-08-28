package com.hypcro

import com.hypcro.farming.WSFarmEngine
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object HypCroMod : ClientModInitializer {
    const val MOD_ID = "hypcro"
    private val LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID)

    lateinit var openGuiKey: KeyMapping
    lateinit var freeLookKey: KeyMapping

    private val CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(MOD_ID, "main")
    )

    override fun onInitializeClient() {
        openGuiKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.hypcro.opengui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_END,
                CATEGORY
            )
        )

        freeLookKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.hypcro.freelook",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
            )
        )

        // Initialize central BPS tracker block break listener
        com.hypcro.util.CropBpsTracker.init()

        // Intercept client-side dot commands before they reach the server
        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            val trimmed = message.trim()
            if (trimmed.startsWith(".")) {
                if (trimmed.startsWith("./")) {
                    true
                } else {
                    handleDotCommand(trimmed)
                }
            } else {
                true
            }
        }

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            com.hypcro.pest.PestTargetTracker.clearSessionMemory()
            com.hypcro.util.CropBpsTracker.resetSession()
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Reset Free Look on disconnect / world unload
            if (client.level == null || client.player == null) {
                if (com.hypcro.camera.FreeLookManager.isFreeLookActive) {
                    com.hypcro.camera.FreeLookManager.reset(client)
                }
            } else {
                com.hypcro.util.CropBpsTracker.onClientTick(client)
            }

            // Render Pathfinding Visualizer, Pest ESP, and Auto Bouncy Ball in-world Gizmos
            if (client.level != null && client.player != null) {
                com.hypcro.pathfinding.PathfindingVisualizer.renderWorld()
                com.hypcro.pest.PestESP.renderWorld()
                com.hypcro.bouncy.AutoBouncyBall.renderWorld()
            }

            while (openGuiKey.consumeClick()) {
                handleOpenGuiOrStop()
            }

            // Free Look Key Handling
            val isHold = com.hypcro.config.ConfigManager.config.qolConfig.freeLookMode.equals("HOLD", ignoreCase = true)
            if (isHold) {
                while (freeLookKey.consumeClick()) { /* Drain clicks */ }
                if (freeLookKey.isDown) {
                    if (!com.hypcro.camera.FreeLookManager.isFreeLookActive) {
                        com.hypcro.camera.FreeLookManager.enable(client)
                    }
                } else {
                    if (com.hypcro.camera.FreeLookManager.isFreeLookActive) {
                        com.hypcro.camera.FreeLookManager.disable(client)
                    }
                }
            } else {
                if (freeLookKey.consumeClick()) {
                    com.hypcro.camera.FreeLookManager.toggle(client)
                    while (freeLookKey.consumeClick()) { /* Drain extra clicks */ }
                }
            }

            com.hypcro.farming.MacroController.onClientTick(client)
        }

        com.hypcro.config.ConfigManager.load()
    }

    private val modScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private var activeTestJob: kotlinx.coroutines.Job? = null

    fun hasActiveTest(): Boolean = activeTestJob?.isActive == true

    fun stopAllTests() {
        activeTestJob?.cancel()
        activeTestJob = null
        com.hypcro.movement.CentralMovementCoordinator.isAbortRequested = true
        com.hypcro.movement.CentralMovementCoordinator.lastAbortTimestamp = System.currentTimeMillis()
        com.hypcro.movement.CentralMovementCoordinator.stopNavigation()
        com.hypcro.pathfinding.PathfindingVisualizer.clearDebug()
    }

    private fun handleDotCommand(cmd: String): Boolean {
        val client = Minecraft.getInstance()
        val parts = cmd.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) return true

        when (parts[0].lowercase()) {
            ".hypcro" -> {
                client.execute { handleOpenGuiOrStop() }
                return false // Intercepted
            }
            ".hypcrogettablist" -> {
                val lines = com.hypcro.pest.PestTabReader.readTabList(client)
                log("=== Tab List (${lines.size} entries) ===")
                if (lines.isEmpty()) {
                    log("Tab list is empty or not loaded.")
                } else {
                    for ((idx, line) in lines.withIndex()) {
                        log("[$idx] $line")
                    }
                }
                return false
            }
            ".hypcrogetscoreboard" -> {
                val lines = com.hypcro.util.GardenStateReader.readScoreboardLines(client)
                log("=== Scoreboard (${lines.size} lines) ===")
                if (lines.isEmpty()) {
                    log("Scoreboard is empty or not loaded.")
                } else {
                    for ((idx, line) in lines.withIndex()) {
                        log("[$idx] $line")
                    }
                }
                return false
            }
            ".hypcropathfindverbose" -> {
                val newState = if (parts.size > 1) {
                    when (parts[1].lowercase()) {
                        "true", "on", "1", "enable" -> true
                        "false", "off", "0", "disable" -> false
                        else -> !com.hypcro.pathfinding.PathfindingVisualizer.isVerbose
                    }
                } else {
                    !com.hypcro.pathfinding.PathfindingVisualizer.isVerbose
                }
                com.hypcro.pathfinding.PathfindingVisualizer.isVerbose = newState
                if (newState) {
                    logSuccess("Pathfinding Verbose mode ENABLED (Yellow=Searching, Green=Reachable, Red=Unreachable, Cyan=Chosen)")
                } else {
                    com.hypcro.pathfinding.PathfindingVisualizer.clearDebug()
                    log("Pathfinding Verbose mode DISABLED")
                }
                return false
            }
            ".hypcrobot" -> {
                val currentVer = com.hypcro.config.ConfigManager.config.pestDestroyer.flightEngineVersion
                if (parts.size < 2) {
                    val newVer = if (currentVer.equals("V2", ignoreCase = true)) "CLASSIC" else "V2"
                    com.hypcro.config.ConfigManager.config.pestDestroyer.flightEngineVersion = newVer
                    com.hypcro.config.ConfigManager.save()
                    logSuccess("Toggled flight engine: $newVer (${if (newVer == "V2") "Decoupled 6-DOF BetterBot" else "Legacy Waypoint Tracking"})")
                    return false
                }
                when (parts[1].lowercase()) {
                    "new", "v2", "decoupled", "better" -> {
                        com.hypcro.config.ConfigManager.config.pestDestroyer.flightEngineVersion = "V2"
                        com.hypcro.config.ConfigManager.save()
                        logSuccess("Flight engine set to: V2 (Decoupled 6-DOF BetterBot)")
                    }
                    "old", "classic", "legacy", "v1" -> {
                        com.hypcro.config.ConfigManager.config.pestDestroyer.flightEngineVersion = "CLASSIC"
                        com.hypcro.config.ConfigManager.save()
                        logSuccess("Flight engine set to: CLASSIC (Legacy Waypoint Tracking)")
                    }
                    else -> {
                        logWarn("Usage: .hypcrobot [new | old]")
                    }
                }
                return false
            }
            ".hypcrobitstar" -> {
                if (parts.size < 2) {
                    val currentSec = com.hypcro.config.ConfigManager.config.pestDestroyer.bitStarTimeSeconds
                    log("Current BIT* compute budget: ${currentSec}s")
                    log("Usage: .hypcrobitstar <seconds> (e.g. .hypcrobitstar 2.5)")
                    return false
                }
                val sec = parts[1].toDoubleOrNull()
                if (sec == null || sec <= 0.0) {
                    logWarn("Invalid duration. Usage: .hypcrobitstar <seconds> (e.g. .hypcrobitstar 2.5)")
                    return false
                }
                com.hypcro.config.ConfigManager.config.pestDestroyer.bitStarTimeSeconds = sec
                com.hypcro.config.ConfigManager.save()
                logSuccess("BIT* pathfinder computation budget set to ${sec}s")
                return false
            }
            ".hypcrotest" -> {
                if (parts.size < 2) {
                    log("HypCro Test Usage:")
                    log("• .hypcrotest movecam <pitch> <yaw>")
                    log("• .hypcrotest flyto <x> <y> <z> [pitch] [yaw]")
                    log("• .hypcrotest pathfind <x> <y> <z>")
                    return false
                }

                when (parts[1].lowercase()) {
                    "movecam" -> {
                        if (parts.size < 4) {
                            logWarn("Usage: .hypcrotest movecam <yaw> <pitch>")
                            return false
                        }
                        val yaw = parts[2].toFloatOrNull() ?: 0f
                        val pitch = parts[3].toFloatOrNull() ?: 0f
                        log("Testing camera rotation: Yaw $yaw, Pitch $pitch")
                        stopAllTests()
                        com.hypcro.movement.CentralMovementCoordinator.isAbortRequested = false
                        activeTestJob = modScope.launch {
                            com.hypcro.movement.MouseMovementEngine.rotateTo(client, yaw, pitch)
                            logSuccess("Camera rotation test complete.")
                        }
                        return false
                    }
                    "flyto" -> {
                        if (parts.size < 5) {
                            logWarn("Usage: .hypcrotest flyto <x> <y> <z> [yaw] [pitch]")
                            return false
                        }
                        val player = client.player ?: return false
                        val curPos = player.position()
                        val targetX = com.hypcro.movement.CentralMovementCoordinator.parseCoordinate(parts[2], curPos.x)
                        val targetY = com.hypcro.movement.CentralMovementCoordinator.parseCoordinate(parts[3], curPos.y)
                        val targetZ = com.hypcro.movement.CentralMovementCoordinator.parseCoordinate(parts[4], curPos.z)
                        val targetYaw = if (parts.size > 5) parts[5].toFloatOrNull() else null
                        val targetPitch = if (parts.size > 6) parts[6].toFloatOrNull() else null

                        log("Testing flight navigation to ($targetX, $targetY, $targetZ)...")
                        stopAllTests()
                        com.hypcro.movement.CentralMovementCoordinator.isAbortRequested = false
                        com.hypcro.movement.CentralMovementCoordinator.lastAbortTimestamp = 0L
                        activeTestJob = modScope.launch {
                            val success = com.hypcro.movement.CentralMovementCoordinator.flyTo(
                                client,
                                targetX = targetX,
                                targetY = targetY,
                                targetZ = targetZ,
                                targetPitch = targetPitch,
                                targetYaw = targetYaw
                            )
                            if (success) {
                                logSuccess("Flight test reached destination.")
                            } else {
                                logWarn("Flight test aborted or failed.")
                            }
                        }
                        return false
                    }
                    "pathfind" -> {
                        if (parts.size < 5) {
                            logWarn("Usage: .hypcrotest pathfind <x> <y> <z>")
                            return false
                        }
                        val player = client.player ?: return false
                        val level = client.level ?: return false
                        val curPos = player.position()
                        val targetX = com.hypcro.movement.CentralMovementCoordinator.parseCoordinate(parts[2], curPos.x) ?: curPos.x
                        val targetY = com.hypcro.movement.CentralMovementCoordinator.parseCoordinate(parts[3], curPos.y) ?: curPos.y
                        val targetZ = com.hypcro.movement.CentralMovementCoordinator.parseCoordinate(parts[4], curPos.z) ?: curPos.z

                        val destPos = net.minecraft.world.phys.Vec3(targetX, targetY, targetZ)
                        val bp = net.minecraft.core.BlockPos(targetX.toInt(), targetY.toInt(), targetZ.toInt())

                        if (!level.hasChunk(bp.x shr 4, bp.z shr 4)) {
                            logWarn("Pathfind destination exceeds loaded chunk render distance!")
                            return false
                        }

                        val start = player.position()
                        stopAllTests()
                        com.hypcro.movement.CentralMovementCoordinator.isAbortRequested = false
                        activeTestJob = modScope.launch {
                            val startTime = System.currentTimeMillis()
                            val path = com.hypcro.movement.CentralMovementCoordinator.getActivePathfinder().computePath(level, start, destPos)
                            val elapsed = System.currentTimeMillis() - startTime
                            if (com.hypcro.movement.CentralMovementCoordinator.isAbortRequested) {
                                com.hypcro.movement.CentralMovementCoordinator.isAbortRequested = false
                                logWarn("Pathfinding calculation aborted by user.")
                            } else {
                                logSuccess("Computed ${path.size} waypoints in ${elapsed}ms via ${com.hypcro.config.ConfigManager.config.pestDestroyer.pathfindingAlgorithm}")
                            }
                        }
                        return false
                    }
                    else -> {
                        logWarn("Unknown test subcommand: ${parts[1]}")
                        return false
                    }
                }
            }
            else -> {
                logWarn("Unknown command: ${parts[0]}")
                return false
            }
        }
        return false
    }

    private fun handleOpenGuiOrStop() {
        val client = Minecraft.getInstance()
        val now = System.currentTimeMillis()
        if (now - com.hypcro.movement.CentralMovementCoordinator.lastAbortTimestamp < 600L) {
            return
        }
        val stoppedAny = com.hypcro.farming.MacroController.stopAllMacros(reason = "User Request")
        if (stoppedAny) {
            logWarn("Macro stopped by user!")
            return
        }
        if (client.screen == null) {
            client.setScreen(com.hypcro.gui.MainFarmingScreen())
        }
    }

    fun sendRaw(message: String) {
        val client = Minecraft.getInstance()
        client.execute {
            client.player?.sendSystemMessage(Component.literal(message))
        }
    }

    fun log(message: String) {
        LOGGER.info(message)
        sendRaw("§8[§b§lHypCro§8] §7$message")
    }

    fun logSuccess(message: String) {
        LOGGER.info(message)
        sendRaw("§8[§a§lHypCro §8✔] §f$message")
    }

    fun logWarn(message: String) {
        sendRaw("§8[§6§lHypCro §e⚠§8] §e$message")
        val client = Minecraft.getInstance()
        client.execute {
            if (client.player != null) {
                client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1.0f, 1.0f))
            }
        }
    }

    fun logError(message: String) {
        LOGGER.error(message)
        sendRaw("§8[§c§lHypCro Error§8] §c$message")
        val client = Minecraft.getInstance()
        client.execute {
            if (client.player != null) {
                client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1.0f, 0.8f))
            }
        }
    }

    fun logWatchdogWarn(message: String) {
        sendRaw("")
        sendRaw("§6§m----------------------------------------")
        sendRaw(" §8[§6§lHypCro Watchdog §e⚠§8] §e§lROTATION WARNING")
        sendRaw(" §8• §e$message")
        sendRaw("§6§m----------------------------------------")
        sendRaw("")
        val client = Minecraft.getInstance()
        client.execute {
            client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.GHAST_SCREAM, 1.0f, 1.5f))
            client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1.0f, 1.0f))
            client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1.0f, 1.2f))
        }
    }

    fun logWatchdog(message: String) {
        sendRaw("§8[§c§lHypCro Watchdog§8] §c$message")
    }

    fun logStartBanner(mode: String, crop: String, yaw: Float, pitch: Float, toolName: String?) {
        sendRaw("")
        sendRaw("§8§m----------------------------------------")
        sendRaw(" §8[§b§lHypCro§8] §a§lMACRO STARTED §8(§b$mode§8)")
        sendRaw(" §8• §7Crop: §f$crop §8• §7Yaw: §f${String.format("%.2f", yaw)}° §8• §7Pitch: §f${String.format("%.2f", pitch)}°")
        if (!toolName.isNullOrBlank()) {
            sendRaw(" §8• §7Tool: §f$toolName")
        }
        sendRaw("§8§m----------------------------------------")
        sendRaw("")
    }

    fun logStopBanner(reason: String) {
        sendRaw("")
        sendRaw("§8§m----------------------------------------")
        sendRaw(" §8[§7§lHypCro§8] §c§lMACRO STOPPED §8(§f$reason§8)")
        sendRaw(" §8• §7Press toggle key or type §b.hypcro§7 to open menu")
        sendRaw("§8§m----------------------------------------")
        sendRaw("")
    }

    fun logAlarmBanner(reason: String) {
        sendRaw("")
        sendRaw("§4§m========================================")
        sendRaw(" §8[§c§lHypCro §4🚨§8] §c§lFAILSAFE TRIGGERED")
        sendRaw(" §8• §f$reason")
        sendRaw(" §8• §7Macro aborted §8• §eAlarm active")
        sendRaw("§4§m========================================")
        sendRaw("")
    }
}
