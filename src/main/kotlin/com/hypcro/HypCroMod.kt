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
            log("Stopped script. Press END or type .hypcro again to open GUI.")
            return
        }
        if (client.screen == null) {
            client.setScreen(com.hypcro.gui.MainFarmingScreen())
        }
    }

    fun log(message: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal("§b[Hypcro] §f$message"))
    }

    fun logWatchdog(message: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal("§c[Hypcro Watchdog] §f$message"))
    }
}
