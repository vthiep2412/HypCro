package com.hypcro.config

import com.hypcro.HypCroMod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    private class SaveRequest(val text: String, val completion: CompletableDeferred<Unit>?)

    private val saveChannel = Channel<SaveRequest>(Channel.UNLIMITED)

    init {
        ioScope.launch {
            for (request in saveChannel) {
                val ok = writeTextToFile(request.text)
                if (ok) {
                    request.completion?.complete(Unit)
                } else {
                    request.completion?.completeExceptionally(RuntimeException("Failed to write config file"))
                }
            }
        }
    }

    @Synchronized
    fun save(async: Boolean = true) {
        val text = json.encodeToString(config)
        if (async) {
            val result = saveChannel.trySend(SaveRequest(text, null))
            if (result.isFailure) {
                writeTextToFile(text)
            }
        } else {
            val deferred = CompletableDeferred<Unit>()
            val result = saveChannel.trySend(SaveRequest(text, deferred))
            if (result.isSuccess) {
                try {
                    runBlocking {
                        withTimeout(2000L) {
                            deferred.await()
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    writeTextToFile(text)
                } catch (e: Exception) {
                    // Already failed and logged inside writeTextToFile; do not retry duplicate write
                }
            } else {
                writeTextToFile(text)
            }
        }
    }

    private fun writeTextToFile(text: String): Boolean {
        return try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(text)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            HypCroMod.logError("Failed to save config to ${configFile.name}: ${e.message}")
            false
        }
    }
}

