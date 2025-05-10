package com.example.dallama

import android.llama.cpp.LLamaAndroid
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch


class LlamaModel(private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance()): ViewModel() {
    companion object {
        @JvmStatic
        private val NanosPerSecond = 1_000_000_000.0
    }

    private val tag: String? = this::class.simpleName
    var maxTokeSize by mutableStateOf(TextFieldValue("256"))
    var prompt by mutableStateOf(TextFieldValue("You are helpful assistant."))
    var topK by mutableStateOf(TextFieldValue("50"))
    var topP by mutableStateOf(TextFieldValue("0.9"))
    var temperature by mutableStateOf(TextFieldValue("0.3"))

    var messages by mutableStateOf(listOf<Message>(Message( "Initializing...","System")))
        private set

    var message by mutableStateOf(Message("", ""))
        private set

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            try {
                llamaAndroid.unload()
            } catch (exc: IllegalStateException) {
                messages = messages + Message( exc.message ?: "Unknown error","System")
            }
        }
    }

    fun send() {
        // Add message to the list
        messages = messages + message.copy()
        messages = messages + Message( "","Bot")

        viewModelScope.launch {
            llamaAndroid.send(message.content,false, prompt.text, maxTokeSize.text.toInt())

                .catch { exc ->
                    Log.e(tag, "send() failed", exc)
                    Log.e(tag, "send() failed", exc)
                    messages = messages + Message( exc.message ?: "Unknown error","System")
                }
                .collect { response ->

                    messages = messages.dropLast(1) + messages.last().copy(content = messages.last().content + response)
                }
        }
    }

    fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1) {
        viewModelScope.launch {
            try {
                val start = System.nanoTime()
                val warmupResult = llamaAndroid.bench(pp, tg, pl, nr)
                val end = System.nanoTime()

                // Convert warmup result to a Message
                messages = messages + Message( "Warmup Result: $warmupResult","System")

                val warmup = (end - start).toDouble() / NanosPerSecond
                messages = messages + Message( "Warm-up time: $warmup seconds, please wait...","System")

                if (warmup > 5.0) {
                    messages = messages + Message( "Warm-up took too long, aborting benchmark","System")
                    return@launch
                }

                val benchmarkResult = llamaAndroid.bench(512, 128, 1, 3)
                messages = messages + Message( "Benchmark Result: $benchmarkResult","System")

            } catch (exc: IllegalStateException) {
                Log.e(tag, "bench() failed", exc)
                messages = messages + Message( exc.message ?: "Unknown error","System")
            }
        }
    }

    fun load(pathToModel: String, embedding : Boolean = false) {
        viewModelScope.launch {
            try {
                llamaAndroid.load(pathToModel, temperature.text.toFloat(), embedding)
                messages = messages + Message("Loaded: $pathToModel","System")
            } catch (exc: IllegalStateException) {
                Log.e(tag, "load() failed", exc)
                messages = messages + Message(exc.message ?: "Unknown error","System")
            }
        }
    }

    fun updateMessage(newMessage: String, sender: String = "Bot") {
        message = Message(newMessage, sender)
    }

    fun clear() {
        messages = listOf()
    }

    fun log(message: String) {
        messages = messages + Message(message, "Log")
    }
}

