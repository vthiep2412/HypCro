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

object HypCroMod : ClientModInitializer {
    const val MOD_ID = "hypcro"
    lateinit var openGuiKey: KeyMapping

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

        // Intercept client-side .hypcro command before it reaches the server
        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            if (message.trim().equals(".hypcro", ignoreCase = true)) {
                Minecraft.getInstance().execute {
                    handleOpenGuiOrStop()
                }
                false // Cancel sending to server
            } else {
                if (com.hypcro.farming.WSFarmEngine.isRunning) {
                    log("Chat blocked while macro")
                    false
                } else {
                    true
                }
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openGuiKey.consumeClick()) {
                handleOpenGuiOrStop()
            }
            com.hypcro.farming.WSFarmEngine.onClientTick(client)
        }

        com.hypcro.config.ConfigManager.load()
    }

    private fun handleOpenGuiOrStop() {
        val client = Minecraft.getInstance()
        if (com.hypcro.failsafe.HypcroWatchdog.isAlarmActive) {
            com.hypcro.failsafe.HypcroWatchdog.silenceAlarm()
            log("Watchdog alarm silenced. Press END or type .hypcro again to open GUI.")
            return
        }
        if (WSFarmEngine.isRunning) {
            WSFarmEngine.stopMacro(reason = "User Request")
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
        sendRaw("§8[§bHypCro§8] §7$message")
    }

    fun logSuccess(message: String) {
        sendRaw("§8[§aHypCro §8✔] §f$message")
    }

    fun logWarn(message: String) {
        sendRaw("§8[§6HypCro §e⚠§8] §e$message")
    }

    fun logWatchdog(message: String) {
        sendRaw("§8[§cHypCro Watchdog§8] §c$message")
    }

    fun logStartBanner(crop: String, yaw: Float, pitch: Float, toolName: String?) {
        sendRaw("")
        sendRaw("§8§m----------------------------------------")
        sendRaw(" §8[§bHypCro§8] §a§lMACRO STARTED")
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
        sendRaw(" §8[§7HypCro§8] §c§lMACRO STOPPED §8(§f$reason§8)")
        sendRaw(" §8• §7Press toggle key or type §b.hypcro§7 to open menu")
        sendRaw("§8§m----------------------------------------")
        sendRaw("")
    }

    fun logAlarmBanner(reason: String) {
        sendRaw("")
        sendRaw("§4§m========================================")
        sendRaw(" §8[§cHypCro §4🚨§8] §c§lFAILSAFE TRIGGERED")
        sendRaw(" §8• §f$reason")
        sendRaw(" §8• §7Macro aborted §8• §eAlarm active")
        sendRaw("§4§m========================================")
        sendRaw("")
    }
}
