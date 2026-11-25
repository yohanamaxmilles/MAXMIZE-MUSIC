package com.example.player.djvoice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.model.DjVoiceGender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Offline DJ announcements backed by installed Android TTS voices.
 * Voice gender is inferred from installed voice identifiers where possible.
 * Devices without a matching voice keep their current system voice.
 */
class DjVoiceDirector(
    context: Context,
    private val locale: Locale = Locale.ENGLISH,
    private val onSpeechStarted: () -> Unit = {},
    private val onSpeechFinished: () -> Unit = {},
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val ready = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val speakingCount = AtomicInteger(0)
    private val queueLock = Any()
    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = ArrayDeque<String>()
    private var textToSpeech: TextToSpeech? = null
    private var sequence = 0L
    @Volatile private var genderPreference = DjVoiceGender.DEFAULT
    @Volatile private var speechRate = 0.96f
    @Volatile private var speechVolume = 1.0f

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (closed.get()) return@TextToSpeech
            val engine = textToSpeech ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech

            val languageResult = engine.setLanguage(locale)
            engine.setSpeechRate(speechRate)
            engine.setPitch(1.0f)
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            selectPreferredVoice(engine)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finishUtterance()
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finishUtterance()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finishUtterance()
                override fun onError(utteranceId: String?, errorCode: Int) = finishUtterance()
            })

            val messages = synchronized(queueLock) {
                ready.set(
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                )
                pending.toList().also { pending.clear() }
            }
            if (ready.get()) messages.forEach(::speak)
        }
        preferenceScope.launch {
            UserPreferencesRepository(appContext).preferencesFlow.collect { prefs ->
                setGenderPreference(prefs.automixConfig.djVoiceGender)
                setSpeechRate(prefs.automixConfig.djVoiceRate)
                setSpeechVolume(prefs.automixConfig.djVoiceVolume)
            }
        }
    }

    private fun finishUtterance() {
        val remaining = speakingCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (remaining == 0) onSpeechFinished()
    }

    fun setGenderPreference(value: DjVoiceGender) {
        genderPreference = value
        textToSpeech?.let(::selectPreferredVoice)
    }

    fun setSpeechRate(value: Float) {
        speechRate = value.coerceIn(0.75f, 1.15f)
        textToSpeech?.setSpeechRate(speechRate)
    }

    fun setSpeechVolume(value: Float) {
        speechVolume = value.coerceIn(0.25f, 1.0f)
    }

    fun availableVoiceNames(): List<String> =
        textToSpeech?.voices.orEmpty()
            .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
            .map { it.name }
            .sorted()

    fun selectedVoiceName(): String = textToSpeech?.voice?.name.orEmpty()

    fun previewVoice() {
        speak("This is the Maxmize DJ voice. Your music stays clear while I make the announcements.")
    }

    private fun selectPreferredVoice(engine: TextToSpeech) {
        val voices = engine.voices.orEmpty()
            .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
        val tokens = when (genderPreference) {
            DjVoiceGender.FEMALE -> listOf("female", "woman", "sfg", "zira", "samantha", "victoria", "aria")
            DjVoiceGender.MALE -> listOf("male", "man", "sfb", "david", "mark", "daniel")
            DjVoiceGender.DEFAULT -> emptyList()
        }
        if (tokens.isEmpty()) return
        val match = voices
            .filter { voice ->
                val id = voice.name.lowercase(Locale.ROOT)
                tokens.any(id::contains)
            }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.latency })
            .firstOrNull()
        if (match != null) engine.voice = match
    }

    fun welcome() {
        speak("Welcome to Maxmize Music. The DJ session is starting now.")
    }

    fun announceTrack(title: String, artist: String) {
        val cleanTitle = title.trim().take(90)
        val cleanArtist = artist.trim().take(70)
        if (cleanTitle.isBlank()) return
        val announcement = if (cleanArtist.isBlank()) {
            "Coming up next, $cleanTitle."
        } else {
            "Coming up next, $cleanTitle by $cleanArtist."
        }
        speak(announcement)
    }

    fun farewell() {
        speak("Thank you for listening to Maxmize Music. The DJ session has ended.")
    }

    fun speak(message: String) {
        if (closed.get()) return
        val clean = message.trim().take(220)
        if (clean.isBlank()) return
        val queueBeforeReady = synchronized(queueLock) {
            if (!ready.get()) {
                if (pending.size >= 4) pending.removeFirst()
                pending.addLast(clean)
                true
            } else false
        }
        if (queueBeforeReady) return

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume)
        }
        val engine = textToSpeech ?: return
        val id = "maxmize-dj-voice-${++sequence}"
        if (speakingCount.getAndIncrement() == 0) onSpeechStarted()
        val result = engine.speak(clean, TextToSpeech.QUEUE_ADD, params, id)
        if (result == TextToSpeech.ERROR) finishUtterance()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(queueLock) { pending.clear() }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        preferenceScope.cancel()
        ready.set(false)
    }
}
