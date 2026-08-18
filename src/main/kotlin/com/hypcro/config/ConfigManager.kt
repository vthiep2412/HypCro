package com.hypcro.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object ConfigManager {
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val configFile: File by lazy {
        FabricLoader.getInstance().configDir.resolve("hypcro.json").toFile()
    }

    var config: FarmConfig = FarmConfig()
        private set

    fun load() {
        if (configFile.exists()) {
            try {
                val content = configFile.readText()
                config = json.decodeFromString(content)
            } catch (e: Exception) {
                config = FarmConfig()
                save(async = false)
            }
        } else {
            config = FarmConfig()
            save(async = false)
        }
    }

    @Synchronized
    fun save(async: Boolean = true) {
        if (async) {
            ioScope.launch {
                saveSync()
            }
        } else {
            saveSync()
        }
    }

    @Synchronized
    private fun saveSync() {
        try {
            configFile.parentFile?.mkdirs()
            val text = json.encodeToString(config)
            configFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

