package com.example.player.djvoice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** On-device DJ announcements. Installed Android TTS works without a cloud connection. */
class DjVoiceDirector(
    context: Context,
    private val locale: Locale = Locale.getDefault(),
    private val onSpeechStarted: () -> Unit = {},
    private val onSpeechFinished: () -> Unit = {},
) : AutoCloseable {

    private val ready = AtomicBoolean(false)
    private val enabled = AtomicBoolean(true)
    private val appContext = context.applicationContext
    private val pendingAnnouncements = ArrayDeque<String>()
    private val queueLock = Any()
    private var textToSpeech: TextToSpeech? = null
    private var sequence = 0L

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = textToSpeech ?: return@TextToSpeech
                val languageResult = engine.setLanguage(locale)
                engine.setSpeechRate(1.0f)
                engine.setPitch(1.0f)
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onSpeechStarted()
                    override fun onDone(utteranceId: String?) = onSpeechFinished()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = onSpeechFinished()
                    override fun onError(utteranceId: String?, errorCode: Int) = onSpeechFinished()
                })
                val pending = synchronized(queueLock) {
                    ready.set(languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED)
                    pendingAnnouncements.toList().also { pendingAnnouncements.clear() }
                }
                if (enabled.get()) {
                    pending.forEach { message ->
                        engine.speak(message, TextToSpeech.QUEUE_ADD, null, "maxmize-dj-voice-" + (++sequence))
                    }
                }
            }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled.set(value)
        if (!value) {
            synchronized(queueLock) { pendingAnnouncements.clear() }
            textToSpeech?.stop()
        }
    }

    fun welcome(listenerName: String? = null) {
        val name = listenerName?.trim()?.takeIf { it.isNotEmpty() }
        speak(if (name == null) {
            "Welcome. You're listening to Maxmize Music. Let's begin the set."
        } else {
            "Welcome $name. You're listening to Maxmize Music. Let's begin the set."
        })
    }

    fun announceTrack(title: String, artist: String) {
        val cleanTitle = title.trim().take(100)
        val cleanArtist = artist.trim().take(80)
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return
        speak("Coming up next, $cleanTitle by $cleanArtist.")
    }

    fun farewell() {
        speak("Thank you for listening to Maxmize Music. This is the end of the set. See you next time.")
    }

    fun speak(message: String) {
        if (!enabled.get()) return
        val clean = message.trim().take(240)
        if (clean.isBlank()) return
        val queueBeforeReady = synchronized(queueLock) {
            if (!ready.get()) {
                if (pendingAnnouncements.size >= 4) pendingAnnouncements.removeFirst()
                pendingAnnouncements.addLast(clean)
                true
            } else false
        }
        if (queueBeforeReady) return
        val utteranceId = "maxmize-dj-voice-" + (++sequence)
        textToSpeech?.speak(clean, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    override fun close() {
        enabled.set(false)
        synchronized(queueLock) { pendingAnnouncements.clear() }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ready.set(false)
    }
}
