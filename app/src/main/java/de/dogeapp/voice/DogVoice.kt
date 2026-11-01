package de.dogeapp.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** The tablet speaks for the dog: German text-to-speech with a higher, playful pitch. */
class DogVoice(context: Context) {
    private var ready = false
    private val pending = mutableMapOf<String, () -> Unit>()

    private lateinit var tts: TextToSpeech

    init {
        // The init callback arrives later on the main thread, after tts is assigned.
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts.setLanguage(Locale.GERMAN)
                ready = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                tts.setPitch(1.6f)
                tts.setSpeechRate(1.05f)
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        })
    }

    private fun finish(id: String?) {
        val done = synchronized(pending) { pending.remove(id) }
        done?.invoke()
    }

    val isReady: Boolean get() = ready

    /** Speaks [text] and returns when it is finished (or after a safety timeout). */
    suspend fun say(text: String) {
        if (!ready || text.isBlank()) return
        withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val id = UUID.randomUUID().toString()
                synchronized(pending) { pending[id] = { if (cont.isActive) cont.resume(Unit) } }
                cont.invokeOnCancellation {
                    synchronized(pending) { pending.remove(id) }
                    tts.stop()
                }
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                    finish(id)
                }
            }
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
