package de.dogeapp.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Keeps Android's speech recognizer listening in a loop (German) and hands every recognized
 * phrase – with its alternatives – to [onPhrase]. Uses the device's speech service
 * (on Samsung tablets usually Google or Samsung voice input).
 */
class VoiceController(
    private val context: Context,
    private val onPhrase: (List<String>) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onProblem: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (active) return
        if (!isAvailable) {
            onProblem("Keine Spracherkennung auf diesem Gerät gefunden")
            return
        }
        active = true
        listen()
    }

    fun stop() {
        active = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        onListening(false)
    }

    private fun listen() {
        if (!active) return
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        runCatching { r.startListening(intent) }.onFailure { restart(1000) }
    }

    private fun restart(delayMs: Long) {
        if (!active) return
        main.postDelayed({ listen() }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onListening(true)
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = onListening(false)
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (list.isNotEmpty()) onPhrase(list)
            restart(150)
        }

        override fun onError(error: Int) {
            onListening(false)
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onProblem("Mikrofon-Berechtigung fehlt")
                    stop()
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    onProblem("Spracherkennung braucht Internet oder das deutsche Offline-Sprachpaket")
                    restart(3000)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    // Recreate the recognizer, some devices get stuck otherwise.
                    recognizer?.destroy()
                    recognizer = null
                    restart(800)
                }
                else -> restart(250) // no match / speech timeout: just listen again
            }
        }
    }
}
