package com.example.player

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Offline DJ voice for Automix. Android's installed TTS engine supplies speech,
 * so no network service or remote voice API is required.
 */
class DjVoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var ready = false
    private var pendingAnnouncement: String? = null
    private var released = false

    init {
        textToSpeech = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (released || status != TextToSpeech.SUCCESS) return
        val engine = textToSpeech ?: return
        val locale = Locale.getDefault()
        val languageResult = engine.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.setLanguage(Locale.US)
        }
        engine.setSpeechRate(1.0f)
        engine.setPitch(0.96f)
        ready = true
        pendingAnnouncement?.let {
            pendingAnnouncement = null
            speak(it)
        }
    }

    fun announceTransition(title: String, artist: String) {
        val safeTitle = title.trim().take(100)
        val safeArtist = artist.trim().take(100)
        if (safeTitle.isBlank()) return
        val phrase = if (safeArtist.isBlank() || safeArtist.equals("Unknown", true)) {
            "Next track. $safeTitle."
        } else {
            "Next track. $safeTitle, by $safeArtist."
        }
        if (!ready) {
            pendingAnnouncement = phrase
            return
        }
        speak(phrase)
    }

    private fun speak(phrase: String) {
        if (released) return
        textToSpeech?.speak(
            phrase,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "maxmize_dj_transition",
        )
    }

    fun shutdown() {
        released = true
        ready = false
        pendingAnnouncement = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
