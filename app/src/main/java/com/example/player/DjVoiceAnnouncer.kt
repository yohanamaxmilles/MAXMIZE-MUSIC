package com.example.player

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    @Volatile private var speaking = false

    val isSpeaking: Boolean get() = speaking

    init {
        textToSpeech = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (released || status != TextToSpeech.SUCCESS) return
        val engine = textToSpeech ?: return
        engine.setAudioAttributes(
            PlatformAudioAttributes.Builder()
                .setUsage(PlatformAudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(PlatformAudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking = true
            }

            override fun onDone(utteranceId: String?) {
                speaking = false
                speakPendingAnnouncement()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speaking = false
                speakPendingAnnouncement()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                speaking = false
                speakPendingAnnouncement()
            }
        })
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
        if (!ready || speaking) {
            pendingAnnouncement = phrase
            return
        }
        speak(phrase)
    }

    private fun speakPendingAnnouncement() {
        if (released || !ready || speaking) return
        val next = pendingAnnouncement ?: return
        pendingAnnouncement = null
        speak(next)
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
        speaking = false
        pendingAnnouncement = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
