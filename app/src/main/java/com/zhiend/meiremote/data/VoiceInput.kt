package com.zhiend.meiremote.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Thin wrapper around the system speech recognizer (no extra dependency). */
object VoiceInput {

    fun create(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit): SpeechRecognizer {
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    onResult(text)
                } else {
                    onError("Không nghe rõ, thử lại")
                }
            }

            override fun onError(error: Int) = onError(errorMessage(error))
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        return sr
    }

    fun start(sr: SpeechRecognizer) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.startListening(intent)
    }

    fun stop(sr: SpeechRecognizer) {
        try {
            sr.stopListening()
        } catch (_: Exception) {
        }
    }

    fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận được giọng nói"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Hết thời gian nói"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Lỗi mạng nhận dạng giọng nói"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Cần quyền micro"
        else -> "Lỗi nhận dạng giọng nói"
    }
}
