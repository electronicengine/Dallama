package android.llama.cpp

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class LLamaAndroid {
    private val tag: String? = this::class.simpleName

    private val threadLocalState: ThreadLocal<State> = ThreadLocal.withInitial { State.Idle }
    private var _model : Long = 0L
    private var _context : Long = 0L

    private val runLoop: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        thread(start = false, name = "Llm-RunLoop") {

            System.loadLibrary("llama-android")
            log_to_android()
            backend_init(false)
            it.run()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception: Throwable ->
            }
        }
    }.asCoroutineDispatcher()

//    private val nlen: Int = 256

    private external fun log_to_android()
    private external fun load_model(filename: String): Long
    private external fun free_model(model: Long)
    private external fun new_context(model: Long, embedding: Boolean, n_threads: Int, pooling_type: Int): Long
    private external fun free_context(context: Long)
    private external fun backend_init(numa: Boolean)
    private external fun backend_free()
    private external fun new_batch(nTokens: Int, embd: Int, nSeqMax: Int): Long
    private external fun free_batch(batch: Long)
    private external fun new_sampler(tempe: Float): Long
    private external fun free_sampler(sampler: Long)
    private external fun get_similarity(emb1: FloatArray, emb2: FloatArray ): Float
    private external fun calculate_embeddings(model_pointer: Long, context_pointer: Long, text_: String): FloatArray
    private external fun bench_model(
        context: Long,
        model: Long,
        batch: Long,
        pp: Int,
        tg: Int,
        pl: Int,
        nr: Int
    ): String

    private external fun system_info(): String

    private external fun completion_init(
        context: Long,
        batch: Long,
        inputText: String,
        formatChat: Boolean,
        nLen: Int,
        systemPrompt: String,
        chatTemplate: String // <-- new parameter
    ): Int

    private external fun completion_loop(
        context: Long,
        batch: Long,
        sampler: Long,
        nLen: Int,
        ncur: IntVar
    ): String?

    private external fun kv_cache_clear(context: Long)

    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String {
        return withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    bench_model(state.context, state.model, state.batch, pp, tg, pl, nr)
                }

                else -> throw IllegalStateException("No model loaded")
            }
        }
    }

    suspend fun get_embedding(text: String) : FloatArray {
        return withContext(runLoop) {
            val state = threadLocalState.get()
            when (state) {
                is State.Loaded -> {
                    val embArr =  calculate_embeddings(state.model, state.context, text)
                    return@withContext embArr
                }

                else -> throw IllegalStateException("No model loaded")
            }
        }
    }

    suspend fun calculate_similarity(emb1: FloatArray, emb2: FloatArray ): Float {
        return withContext(runLoop) {
            val state = threadLocalState.get()
            when (state) {
                is State.Loaded -> {
                    val embArr =  get_similarity(emb1, emb2)
                    return@withContext embArr
                }

                else -> throw IllegalStateException("No model loaded")
            }
        }
    }


    suspend fun load(pathToModel: String, temperature: Float, embedding: Boolean, nThreads: Int, poolingType: Int) {
        withContext(runLoop) {
            when (threadLocalState.get()) {
                is State.Idle -> {
                    val model = load_model(pathToModel)
                    if (model == 0L)  throw IllegalStateException("load_model() failed")
                    _model = model

                    val context = new_context(model, embedding ,nThreads, poolingType)
                    if (context == 0L) throw IllegalStateException("new_context() failed")
                    _context = context

                    val batch = new_batch(512, 0, 1)
                    if (batch == 0L) throw IllegalStateException("new_batch() failed")

                    val sampler = new_sampler(temperature)
                    if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

                    threadLocalState.set(State.Loaded(model, context, batch, sampler))
                }
                else -> throw IllegalStateException("Model already loaded")
            }
        }
    }

    fun send(message: String, formatChat: Boolean = false, prompt: String,max_token: Int =256, template: String): Flow<String> = flow {
        when (val state = threadLocalState.get()) {
            is State.Loaded -> {

                val ncur = IntVar(completion_init(state.context, state.batch, message, formatChat, max_token, prompt, template))
                while (ncur.value <= max_token) {
                    val str = completion_loop(state.context, state.batch, state.sampler, max_token, ncur)
                    if (str == null) {
                        break
                    }
                    emit(str)
                }
                kv_cache_clear(state.context)
            }
            else -> {}
        }
    }.flowOn(runLoop)

    /**
     * Unloads the model and frees resources.
     *
     * This is a no-op if there's no model loaded.
     */
    suspend fun unload() {
        withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    free_context(state.context)
                    free_model(state.model)
                    free_batch(state.batch)
                    free_sampler(state.sampler);

                    threadLocalState.set(State.Idle)
                }
                else -> {}
            }
        }
    }

    private class IntVar(value: Int) {
        @Volatile
        var value: Int = value
            private set

        fun inc() {
            synchronized(this) {
                value += 1
            }
        }
    }

    private sealed interface State {
        data object Idle: State
        data class Loaded(val model: Long, val context: Long, val batch: Long, val sampler: Long): State
    }

}
