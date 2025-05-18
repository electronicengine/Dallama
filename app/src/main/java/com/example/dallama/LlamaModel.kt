package com.example.dallama

import android.llama.cpp.LLamaAndroid
import android.system.SystemCleaner
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch


class LlamaModel(val name: String = "Chat Model") {
    companion object {
        @JvmStatic
        private val NanosPerSecond = 1_000_000_000.0
    }
    private val llamaAndroid: LLamaAndroid = LLamaAndroid()
    private var loaded: Boolean = false
    private val tag: String? = this::class.simpleName
    var maxTokeSize by mutableStateOf(TextFieldValue("2048"))
    var systemMessage by mutableStateOf(TextFieldValue("You are helpful assistant."))
    var topK by mutableStateOf(TextFieldValue("50"))
    var topP by mutableStateOf(TextFieldValue("0.9"))
    var temperature by mutableStateOf(TextFieldValue("0.3"))
    var chatTemplate = "<|system|>\n{system}\n<|user|>\n{user}\n<|assistant|>\n"

    var messages by mutableStateOf(listOf<Message>(Message("Select a Model! ", "$name System", System.currentTimeMillis())))
        private set

    var message by mutableStateOf(Message("", "", System.currentTimeMillis()))
        private set

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun clearResources() {
        scope.launch {
            try {
                llamaAndroid.unload()
                loaded = false
            } catch (exc: IllegalStateException) {
                messages = messages + Message(exc.message ?: "Unknown error", "$name System", System.currentTimeMillis())
            }
        }
    }

    fun send() {
        messages = messages + message.copy()
        messages = messages + Message("", "$name Bot", System.currentTimeMillis())
        scope.launch {

            llamaAndroid.send(message.content, false, systemMessage.text, maxTokeSize.text.toInt(), chatTemplate)
                .catch { exc ->
                    messages = messages + Message(exc.message ?: "Unknown error", "$name System", System.currentTimeMillis())
                }
                .collect { response ->
                    messages = messages.dropLast(1) + messages.last().copy(content = messages.last().content + response)
                }
        }
    }

    fun updateSystemMessage(newSystemMessage: String) {
        systemMessage = TextFieldValue(newSystemMessage)
    }

    fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1) {
        scope.launch {
            try {
                val start = System.nanoTime()
                val warmupResult = llamaAndroid.bench(pp, tg, pl, nr)
                val end = System.nanoTime()
                messages = messages + Message("Warmup Result: $warmupResult", "$name System", System.currentTimeMillis())
                val warmup = (end - start).toDouble() / NanosPerSecond
                messages = messages + Message("Warm-up time: $warmup seconds", "$name System", System.currentTimeMillis())
                if (warmup > 5.0) {
                    messages = messages + Message("Warm-up took too long, aborting benchmark", "$name System", System.currentTimeMillis())
                    return@launch
                }
            } catch (exc: IllegalStateException) {
                messages = messages + Message(exc.message ?: "Unknown error", "$name System", System.currentTimeMillis())
            }
        }
    }

    suspend fun load(pathToModel: String, embedding: Boolean = false) {
        try {
            llamaAndroid.load(pathToModel, temperature.text.toFloat(), embedding)
            messages = messages + Message("Loaded: $pathToModel", "$name System", System.currentTimeMillis())
            loaded = true
        } catch (exc: IllegalStateException) {
            messages = messages + Message(exc.message ?: "Unknown error", "$name System", System.currentTimeMillis())
        }
    }

    fun updateMessage(newMessage: String, sender: String = "$name Bot") {
        message = Message(newMessage, sender, System.currentTimeMillis())
    }

    fun addToMessages(newMessage: String, sender: String = "$name Bot") {
        messages = messages + Message(newMessage, sender, System.currentTimeMillis())
    }

    fun clear() {
        messages = listOf()
    }

    fun log(message: String) {
        messages = messages + Message(message, "$name Log", System.currentTimeMillis())
    }

    fun isLoaded(): Boolean {
        return loaded
    }

    suspend fun unload() {
        try {
            llamaAndroid.unload()
            llamaAndroid.unload()
            loaded = false
            messages = messages + Message("Model is UnLoaded", "$name System", System.currentTimeMillis())

        } catch (exc: IllegalStateException) {
            messages = messages + Message(exc.message ?: "Unknown error", "$name System", System.currentTimeMillis())
        }

    }

    suspend fun calculateEmbedding(text: String): FloatArray {
        try {
            return llamaAndroid.get_embedding(text)
        } catch (exc: IllegalStateException) {
            messages = messages + Message(exc.message ?: "Unknown error", "$name System", System.currentTimeMillis())
            return FloatArray(0)
        }
    }

    suspend fun calculateSimilarity(embd1: FloatArray, embd2: FloatArray): Float {
        try {
            return llamaAndroid.calculate_similarity(embd1, embd2)
        } catch (exc: IllegalStateException) {
            messages = messages + Message(exc.message ?: "Unknown error", "$name System", System.currentTimeMillis())
            return 2.0f
        }
    }
}

