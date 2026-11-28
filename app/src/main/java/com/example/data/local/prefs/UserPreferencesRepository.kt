package com.example.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AccentPreset
import com.example.data.model.AutomixConfig
import com.example.data.model.EnergyProgression
import com.example.data.model.DjVoiceGender
import com.example.data.model.FontChoice
import com.example.data.model.HarmonicMixLevel
import com.example.data.model.MixMode
import com.example.data.model.MoodFilter
import com.example.data.model.SortOption
import com.example.data.model.SortOrder
import com.example.data.model.ThemeMode
import com.example.data.model.TransitionStyle
import com.example.data.model.UiDensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "maxmize_prefs")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentPreset: AccentPreset = AccentPreset.LIME,
    val fontChoice: FontChoice = FontChoice.FIGTREE,
    val fontScale: Float = 1.0f,
    val uiDensity: UiDensity = UiDensity.COMFORTABLE,
    val eqEnabled: Boolean = true,
    val eqPreset: String = "Normal",
    val eqBandLevelsMb: List<Int> = emptyList(),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val audioBalance: Float = 0.0f,
    val crossfadeSeconds: Int = 0,
    val gaplessEnabled: Boolean = true,
    val skipSilenceEnabled: Boolean = false,
    val loudnessNormalizationEnabled: Boolean = false,
    val minDurationSec: Int = 5,
    val autoScanOnLaunch: Boolean = true,
    val mediaStoreVersionToken: String = "",
    val resumePausedOnReopen: Boolean = true,
    val showNotificationSeekButtons: Boolean = true,
    val sortOption: SortOption = SortOption.TITLE,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    // Smart Lyrics Preferences
    val autoDownloadLyrics: Boolean = true,
    val lyricsWifiOnly: Boolean = false,
    val downloadLyricsOnPlayStart: Boolean = true,
    val lyricsFontSizeSp: Float = 19f,
    val musixmatchApiKey: String = "",
    val audiusApiKey: String = "",
    // Smart Artwork Preferences
    val autoDownloadArtwork: Boolean = false,
    val artworkWifiOnly: Boolean = true,
    val autoFixArtworkOnScan: Boolean = false,
    // Automix & DJ Intelligence Preferences
    val automixConfig: AutomixConfig = AutomixConfig(),
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val FONT_CHOICE = stringPreferencesKey("font_choice")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val UI_DENSITY = stringPreferencesKey("ui_density")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val VIRTUALIZER = intPreferencesKey("virtualizer")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val AUDIO_BALANCE = floatPreferencesKey("audio_balance")
        val CROSSFADE_SEC = intPreferencesKey("crossfade_sec")
        val GAPLESS_ENABLED = booleanPreferencesKey("gapless_enabled")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val LOUDNESS_NORM = booleanPreferencesKey("loudness_norm")
        val MIN_DURATION_SEC = intPreferencesKey("min_duration_sec")
        val AUTO_SCAN_ON_LAUNCH = booleanPreferencesKey("auto_scan_on_launch")
        val MEDIASTORE_VERSION = stringPreferencesKey("mediastore_version")
        val RESUME_PAUSED_ON_REOPEN = booleanPreferencesKey("resume_paused_on_reopen")
        val NOTIF_SEEK_BUTTONS = booleanPreferencesKey("notif_seek_buttons")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_ORDER = stringPreferencesKey("sort_order")

        // Lyrics
        val AUTO_DOWNLOAD_LYRICS = booleanPreferencesKey("auto_download_lyrics")
        val LYRICS_WIFI_ONLY = booleanPreferencesKey("lyrics_wifi_only")
        val DOWNLOAD_LYRICS_ON_PLAY = booleanPreferencesKey("download_lyrics_on_play")
        val LYRICS_FONT_SIZE = floatPreferencesKey("lyrics_font_size")
        val MUSIXMATCH_API_KEY = stringPreferencesKey("musixmatch_api_key")
        val AUDIUS_API_KEY = stringPreferencesKey("audius_api_key")

        // Artwork
        val AUTO_DOWNLOAD_ARTWORK = booleanPreferencesKey("auto_download_artwork")
        val ARTWORK_WIFI_ONLY = booleanPreferencesKey("artwork_wifi_only")
        val AUTO_FIX_ARTWORK_ON_SCAN = booleanPreferencesKey("auto_fix_artwork_on_scan")

        // Automix & DJ Intelligence
        val DJ_ENABLED = booleanPreferencesKey("dj_enabled")
        val DJ_PROFILE_ID = stringPreferencesKey("dj_profile_id")
        val DJ_MODE = stringPreferencesKey("dj_mode")
        val DJ_BPM_MIN = intPreferencesKey("dj_bpm_min")
        val DJ_BPM_MAX = intPreferencesKey("dj_bpm_max")
        val DJ_BPM_MATCH = booleanPreferencesKey("dj_bpm_match")
        val DJ_KEY_MATCH = booleanPreferencesKey("dj_key_match")
        val DJ_KEY_LOCK = booleanPreferencesKey("dj_key_lock")
        val DJ_STRICT_GENRE = booleanPreferencesKey("dj_strict_genre")
        val DJ_HARMONIC_LEVEL = stringPreferencesKey("dj_harmonic_level")
        val DJ_ENERGY_PROGRESSION = stringPreferencesKey("dj_energy_progression")
        val DJ_PHRASE_MATCH = booleanPreferencesKey("dj_phrase_match")
        val DJ_OPEN_FORMAT_BRIDGING = booleanPreferencesKey("dj_open_format_bridging")
        val DJ_LOOKAHEAD = intPreferencesKey("dj_lookahead")
        val DJ_W_BPM = floatPreferencesKey("dj_w_bpm")
        val DJ_W_HARMONIC = floatPreferencesKey("dj_w_harmonic")
        val DJ_W_ENERGY = floatPreferencesKey("dj_w_energy")
        val DJ_W_GENRE = floatPreferencesKey("dj_w_genre")
        val DJ_GENRE_PREF = stringPreferencesKey("dj_genre_pref")
        val DJ_MIX_DURATION = intPreferencesKey("dj_mix_duration")
        val DJ_TRANSITION_SEC = intPreferencesKey("dj_transition_sec")
        val DJ_TRANSITION_STYLE = stringPreferencesKey("dj_transition_style")
        val DJ_AUTO_MOOD = booleanPreferencesKey("dj_auto_mood")
        val DJ_MOOD = stringPreferencesKey("dj_mood")
        val DJ_AUTO_GAIN = booleanPreferencesKey("dj_auto_gain")
        val DJ_AVOID_REPEATS = booleanPreferencesKey("dj_avoid_repeats")
        val DJ_VOICE_GENDER = stringPreferencesKey("dj_voice_gender")
        val DJ_VOICE_RATE = floatPreferencesKey("dj_voice_rate")
        val DJ_VOICE_VOLUME = floatPreferencesKey("dj_voice_volume")
        val DJ_SOURCE_PLAYLIST_ID = stringPreferencesKey("dj_source_playlist_id")
        val DJ_SOURCE_FOLDER = stringPreferencesKey("dj_source_folder")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            accentPreset = prefs[Keys.ACCENT_PRESET]?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() }
                ?: AccentPreset.LIME,
            fontChoice = prefs[Keys.FONT_CHOICE]?.let { runCatching { FontChoice.valueOf(it) }.getOrNull() }
                ?: FontChoice.FIGTREE,
            fontScale = (prefs[Keys.FONT_SCALE] ?: 1.0f).coerceIn(0.88f, 1.18f),
            uiDensity = prefs[Keys.UI_DENSITY]?.let { runCatching { UiDensity.valueOf(it) }.getOrNull() }
                ?: UiDensity.COMFORTABLE,
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: true,
            eqPreset = prefs[Keys.EQ_PRESET] ?: "Normal",
            eqBandLevelsMb = prefs[Keys.EQ_BANDS]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList(),
            bassBoostStrength = prefs[Keys.BASS_BOOST] ?: 0,
            virtualizerStrength = prefs[Keys.VIRTUALIZER] ?: 0,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            audioBalance = prefs[Keys.AUDIO_BALANCE] ?: 0.0f,
            crossfadeSeconds = prefs[Keys.CROSSFADE_SEC] ?: 0,
            gaplessEnabled = prefs[Keys.GAPLESS_ENABLED] ?: true,
            skipSilenceEnabled = prefs[Keys.SKIP_SILENCE] ?: false,
            loudnessNormalizationEnabled = prefs[Keys.LOUDNESS_NORM] ?: false,
            minDurationSec = prefs[Keys.MIN_DURATION_SEC] ?: 5,
            autoScanOnLaunch = prefs[Keys.AUTO_SCAN_ON_LAUNCH] ?: true,
            mediaStoreVersionToken = prefs[Keys.MEDIASTORE_VERSION] ?: "",
            resumePausedOnReopen = prefs[Keys.RESUME_PAUSED_ON_REOPEN] ?: true,
            showNotificationSeekButtons = prefs[Keys.NOTIF_SEEK_BUTTONS] ?: true,
            sortOption = prefs[Keys.SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }
                ?: SortOption.TITLE,
            sortOrder = prefs[Keys.SORT_ORDER]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
                ?: SortOrder.ASCENDING,
            autoDownloadLyrics = prefs[Keys.AUTO_DOWNLOAD_LYRICS] ?: true,
            lyricsWifiOnly = prefs[Keys.LYRICS_WIFI_ONLY] ?: false,
            downloadLyricsOnPlayStart = prefs[Keys.DOWNLOAD_LYRICS_ON_PLAY] ?: true,
            lyricsFontSizeSp = (prefs[Keys.LYRICS_FONT_SIZE] ?: 19f).coerceIn(14f, 28f),
            musixmatchApiKey = prefs[Keys.MUSIXMATCH_API_KEY] ?: "",
            audiusApiKey = prefs[Keys.AUDIUS_API_KEY] ?: "",
            autoDownloadArtwork = prefs[Keys.AUTO_DOWNLOAD_ARTWORK] ?: false,
            artworkWifiOnly = prefs[Keys.ARTWORK_WIFI_ONLY] ?: true,
            autoFixArtworkOnScan = prefs[Keys.AUTO_FIX_ARTWORK_ON_SCAN] ?: false,
            automixConfig = readAutomixConfig(prefs),
        )
    }

    private fun readAutomixConfig(prefs: Preferences): AutomixConfig {
        val mixMode = prefs[Keys.DJ_MODE]?.let { runCatching { MixMode.valueOf(it) }.getOrNull() } ?: MixMode.SMART
        val transStyle = prefs[Keys.DJ_TRANSITION_STYLE]?.let {
            runCatching { TransitionStyle.valueOf(it) }.getOrNull()
        } ?: TransitionStyle.SMOOTH_FADE
        val mood = prefs[Keys.DJ_MOOD]?.let { runCatching { MoodFilter.valueOf(it) }.getOrNull() } ?: MoodFilter.ENERGETIC
        val harmonicLevel = prefs[Keys.DJ_HARMONIC_LEVEL]?.let {
            runCatching { HarmonicMixLevel.valueOf(it) }.getOrNull()
        } ?: HarmonicMixLevel.BALANCED
        val energyProg = prefs[Keys.DJ_ENERGY_PROGRESSION]?.let {
            runCatching { EnergyProgression.valueOf(it) }.getOrNull()
        } ?: EnergyProgression.STEADY

        return AutomixConfig(
            enabled = prefs[Keys.DJ_ENABLED] ?: false,
            activeProfileId = prefs[Keys.DJ_PROFILE_ID] ?: "club_house",
            mode = mixMode,
            bpmMin = (prefs[Keys.DJ_BPM_MIN] ?: 60).coerceIn(50, 200),
            bpmMax = (prefs[Keys.DJ_BPM_MAX] ?: 180).coerceIn(70, 220),
            bpmMatchEnabled = prefs[Keys.DJ_BPM_MATCH] ?: true,
            keyMatchingEnabled = prefs[Keys.DJ_KEY_MATCH] ?: true,
            keyLockEnabled = prefs[Keys.DJ_KEY_LOCK] ?: true,
            strictGenreMatching = prefs[Keys.DJ_STRICT_GENRE] ?: true,
            harmonicMixLevel = harmonicLevel,
            energyProgression = energyProg,
            phraseMatchEnabled = prefs[Keys.DJ_PHRASE_MATCH] ?: true,
            openFormatBridging = prefs[Keys.DJ_OPEN_FORMAT_BRIDGING] ?: false,
            lookAheadCount = (prefs[Keys.DJ_LOOKAHEAD] ?: 4).coerceIn(1, 5),
            bpmWeight = (prefs[Keys.DJ_W_BPM] ?: 0.30f).coerceIn(0f, 1f),
            harmonicWeight = (prefs[Keys.DJ_W_HARMONIC] ?: 0.28f).coerceIn(0f, 1f),
            energyWeight = (prefs[Keys.DJ_W_ENERGY] ?: 0.18f).coerceIn(0f, 1f),
            genreWeight = (prefs[Keys.DJ_W_GENRE] ?: 0.24f).coerceIn(0f, 1f),
            genrePreference = prefs[Keys.DJ_GENRE_PREF] ?: "All Genres",
            mixDurationMinutes = (prefs[Keys.DJ_MIX_DURATION] ?: 45).coerceIn(10, 240),
            transitionDurationSec = (prefs[Keys.DJ_TRANSITION_SEC] ?: 8).coerceIn(2, 16),
            transitionStyle = transStyle,
            autoSelectMood = prefs[Keys.DJ_AUTO_MOOD] ?: true,
            mood = mood,
            autoGainEnabled = prefs[Keys.DJ_AUTO_GAIN] ?: true,
            avoidRepeats = prefs[Keys.DJ_AVOID_REPEATS] ?: true,
            djVoiceGender = prefs[Keys.DJ_VOICE_GENDER]?.let { runCatching { DjVoiceGender.valueOf(it) }.getOrNull() } ?: DjVoiceGender.DEFAULT,
            djVoiceRate = (prefs[Keys.DJ_VOICE_RATE] ?: 0.96f).coerceIn(0.75f, 1.15f),
            djVoiceVolume = (prefs[Keys.DJ_VOICE_VOLUME] ?: 1.0f).coerceIn(0.25f, 1.0f),
            sourcePlaylistId = prefs[Keys.DJ_SOURCE_PLAYLIST_ID]?.toLongOrNull(),
            sourceFolderPath = prefs[Keys.DJ_SOURCE_FOLDER]?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun getOnce(): UserPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        context.dataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setFontChoice(choice: FontChoice) {
        context.dataStore.edit { it[Keys.FONT_CHOICE] = choice.name }
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale.coerceIn(0.88f, 1.18f) }
    }

    suspend fun setUiDensity(density: UiDensity) {
        context.dataStore.edit { it[Keys.UI_DENSITY] = density.name }
    }

    suspend fun setEqSettings(
        enabled: Boolean,
        preset: String,
        bandLevelsMb: List<Int>,
        bassBoost: Int,
        virtualizer: Int,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EQ_ENABLED] = enabled
            prefs[Keys.EQ_PRESET] = preset
            prefs[Keys.EQ_BANDS] = bandLevelsMb.joinToString(",")
            prefs[Keys.BASS_BOOST] = bassBoost.coerceIn(0, 1000)
            prefs[Keys.VIRTUALIZER] = virtualizer.coerceIn(0, 1000)
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setAudioBalance(balance: Float) {
        context.dataStore.edit { it[Keys.AUDIO_BALANCE] = balance.coerceIn(-1.0f, 1.0f) }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CROSSFADE_SEC] = seconds.coerceIn(0, 12) }
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GAPLESS_ENABLED] = enabled }
    }

    suspend fun setSkipSilenceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SKIP_SILENCE] = enabled }
    }

    suspend fun setLoudnessNormalizationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOUDNESS_NORM] = enabled }
    }

    suspend fun setMinDurationSec(seconds: Int) {
        context.dataStore.edit { it[Keys.MIN_DURATION_SEC] = seconds.coerceIn(0, 120) }
    }

    suspend fun setAutoScanOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SCAN_ON_LAUNCH] = enabled }
    }

    suspend fun setMediaStoreVersionToken(token: String) {
        context.dataStore.edit { it[Keys.MEDIASTORE_VERSION] = token }
    }

    suspend fun setResumePausedOnReopen(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESUME_PAUSED_ON_REOPEN] = enabled }
    }

    suspend fun setShowNotificationSeekButtons(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIF_SEEK_BUTTONS] = enabled }
    }

    suspend fun setSort(option: SortOption, order: SortOrder) {
        context.dataStore.edit {
            it[Keys.SORT_OPTION] = option.name
            it[Keys.SORT_ORDER] = order.name
        }
    }

    suspend fun setAutoDownloadLyrics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DOWNLOAD_LYRICS] = enabled }
    }

    suspend fun setLyricsWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.LYRICS_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setDownloadLyricsOnPlayStart(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DOWNLOAD_LYRICS_ON_PLAY] = enabled }
    }

    suspend fun setLyricsFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.LYRICS_FONT_SIZE] = sizeSp.coerceIn(14f, 28f) }
    }

    suspend fun setMusixmatchApiKey(key: String) {
        context.dataStore.edit { it[Keys.MUSIXMATCH_API_KEY] = key.trim() }
    }

    suspend fun setAudiusApiKey(key: String) {
        context.dataStore.edit { it[Keys.AUDIUS_API_KEY] = key.trim() }
    }

    suspend fun setAutoDownloadArtwork(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DOWNLOAD_ARTWORK] = enabled }
    }

    suspend fun setArtworkWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.ARTWORK_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setAutoFixArtworkOnScan(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FIX_ARTWORK_ON_SCAN] = enabled }
    }

    suspend fun updateAutomixConfig(transform: (AutomixConfig) -> AutomixConfig) {
        context.dataStore.edit { prefs ->
            val current = readAutomixConfig(prefs)
            val next = transform(current)
            prefs[Keys.DJ_ENABLED] = next.enabled
            prefs[Keys.DJ_PROFILE_ID] = next.activeProfileId
            prefs[Keys.DJ_MODE] = next.mode.name
            prefs[Keys.DJ_BPM_MIN] = next.bpmMin.coerceIn(50, 200)
            prefs[Keys.DJ_BPM_MAX] = next.bpmMax.coerceIn(next.bpmMin + 5, 220)
            prefs[Keys.DJ_BPM_MATCH] = next.bpmMatchEnabled
            prefs[Keys.DJ_KEY_MATCH] = next.keyMatchingEnabled
            prefs[Keys.DJ_KEY_LOCK] = next.keyLockEnabled
            prefs[Keys.DJ_STRICT_GENRE] = next.strictGenreMatching
            prefs[Keys.DJ_HARMONIC_LEVEL] = next.harmonicMixLevel.name
            prefs[Keys.DJ_ENERGY_PROGRESSION] = next.energyProgression.name
            prefs[Keys.DJ_PHRASE_MATCH] = next.phraseMatchEnabled
            prefs[Keys.DJ_OPEN_FORMAT_BRIDGING] = next.openFormatBridging
            prefs[Keys.DJ_LOOKAHEAD] = next.lookAheadCount.coerceIn(1, 5)
            prefs[Keys.DJ_W_BPM] = next.bpmWeight.coerceIn(0f, 1f)
            prefs[Keys.DJ_W_HARMONIC] = next.harmonicWeight.coerceIn(0f, 1f)
            prefs[Keys.DJ_W_ENERGY] = next.energyWeight.coerceIn(0f, 1f)
            prefs[Keys.DJ_W_GENRE] = next.genreWeight.coerceIn(0f, 1f)
            prefs[Keys.DJ_GENRE_PREF] = next.genrePreference
            prefs[Keys.DJ_MIX_DURATION] = next.mixDurationMinutes.coerceIn(10, 240)
            prefs[Keys.DJ_TRANSITION_SEC] = next.transitionDurationSec.coerceIn(2, 16)
            prefs[Keys.DJ_TRANSITION_STYLE] = next.transitionStyle.name
            prefs[Keys.DJ_AUTO_MOOD] = next.autoSelectMood
            prefs[Keys.DJ_MOOD] = next.mood.name
            prefs[Keys.DJ_AUTO_GAIN] = next.autoGainEnabled
            prefs[Keys.DJ_AVOID_REPEATS] = next.avoidRepeats
            prefs[Keys.DJ_VOICE_GENDER] = next.djVoiceGender.name
            prefs[Keys.DJ_VOICE_RATE] = next.djVoiceRate.coerceIn(0.75f, 1.15f)
            prefs[Keys.DJ_VOICE_VOLUME] = next.djVoiceVolume.coerceIn(0.25f, 1.0f)
            if (next.sourcePlaylistId != null) {
                prefs[Keys.DJ_SOURCE_PLAYLIST_ID] = next.sourcePlaylistId.toString()
            } else {
                prefs.remove(Keys.DJ_SOURCE_PLAYLIST_ID)
            }
            if (!next.sourceFolderPath.isNullOrBlank()) {
                prefs[Keys.DJ_SOURCE_FOLDER] = next.sourceFolderPath
            } else {
                prefs.remove(Keys.DJ_SOURCE_FOLDER)
            }
        }
    }
}
