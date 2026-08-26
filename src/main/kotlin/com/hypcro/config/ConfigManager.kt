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

    private class SaveRequest(val text: String, val completion: kotlinx.coroutines.CompletableDeferred<Unit>?)

    private val saveChannel = kotlinx.coroutines.channels.Channel<SaveRequest>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        ioScope.launch {
            for (request in saveChannel) {
                writeTextToFile(request.text)
                request.completion?.complete(Unit)
            }
        }
    }

    @Synchronized
    fun save(async: Boolean = true) {
        val text = json.encodeToString(config)
        if (async) {
            saveChannel.trySend(SaveRequest(text, null))
        } else {
            val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
            saveChannel.trySend(SaveRequest(text, deferred))
            kotlinx.coroutines.runBlocking {
                deferred.await()
            }
        }
    }

    private fun writeTextToFile(text: String) {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

