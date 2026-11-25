package com.example.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

enum class AccentPreset(val displayName: String) {
    LIME("Lime"),
    GOLD("Gold"),
    CRIMSON("Crimson"),
    SLATE("Blue"),
    VIOLET("Violet"),
    EMERALD("Emerald"),
    ROSE("Rose"),
    MONOCHROME("Mono"),
}

enum class FontChoice(val label: String) {
    INTER("Inter"),
    MANROPE("Manrope"),
    PLUS_JAKARTA("Plus Jakarta"),
}

enum class UiDensity(val label: String) {
    COMFORTABLE("Comfortable"),
    COMPACT("Compact"),
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

enum class SortOption(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    DATE_ADDED("Date Added"),
}

enum class SortOrder {
    ASCENDING,
    DESCENDING,
}

enum class LibraryCategory(val label: String) {
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    GENRES("Genres"),
    FOLDERS("Folders"),
    RECENTLY_ADDED("Recently Added"),
    RECENTLY_PLAYED("Recently Played"),
    MOST_PLAYED("Most Played"),
    FAVORITES("Favorites"),
}

enum class SmartPlaylistType(val title: String, val subtitle: String) {
    RECENTLY_PLAYED("Recently Played", "Your latest listening sessions"),
    MOST_PLAYED("Most Played", "Heavy rotation favorites"),
    RECENTLY_ADDED("Recently Added", "Fresh tracks on your device"),
    FAVORITES("Favorites Mix", "All tracks you loved"),
    TOP_RATED("Top Rated", "Songs you rated 4 stars or higher"),
    HIGH_ENERGY("High Energy Mix", "Upbeat tracks ready for Party & DJ Mode"),
    CHILL_VIBES("Chill Vibes", "Smooth lower-energy selections"),
    RECENTLY_SKIPPED("Recently Skipped", "Tracks skipped early in recent sessions"),
}

@Immutable
data class PlaybackUiState(
    val activeMediaId: String? = null,
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val audioBalance: Float = 0.0f,
    val crossfadeSeconds: Int = 0,
    val skipSilenceEnabled: Boolean = false,
    val loudnessNormalizationEnabled: Boolean = false,
    val audioSessionId: Int = 0,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val isServiceConnected: Boolean = false,
)

@Immutable
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val stopAfterCurrentTrack: Boolean = false,
)

@Immutable
data class ScanState(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalFound: Int = 0,
    val lastScanAt: Long = 0L,
    val statusMessage: String? = null,
)

@Immutable
data class EqBandState(
    val index: Int,
    val centerFreqHz: Int,
    val levelMillibels: Int,
)

@Immutable
data class EqualizerUiState(
    val isSupported: Boolean = false,
    val isBassBoostSupported: Boolean = false,
    val isVirtualizerSupported: Boolean = false,
    val isEnabled: Boolean = true,
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val bands: List<EqBandState> = emptyList(),
    val currentPresetName: String = "Normal",
    val availablePresets: List<String> = DEFAULT_EQ_PRESETS,
    val bassBoostStrength: Int = 0, // 0..1000
    val virtualizerStrength: Int = 0, // 0..1000
    val unavailableReason: String? = null,
) {
    companion object {
        val DEFAULT_EQ_PRESETS = listOf(
            "Normal",
            "Flat",
            "Rock",
            "Pop",
            "Jazz",
            "Classical",
            "Dance",
            "Electronic",
            "Hip-Hop",
            "Acoustic",
            "Custom",
        )
    }
}

@Immutable
data class LyricWord(
    val startMs: Long,
    val text: String,
)

@Immutable
data class LyricLine(
    val timestampMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
)

enum class LyricsSource(val badgeLabel: String) {
    NONE("UNAVAILABLE"),
    MANUAL_OR_IMPORTED("LOCAL / IMPORTED"),
    SIDECAR_LRC("LOCAL .LRC"),
    EMBEDDED_TAG("EMBEDDED"),
    ONLINE_LRCLIB("LRCLIB"),
    ONLINE_MUSIXMATCH("MUSIXMATCH"),
    ONLINE_FALLBACK("ONLINE"),
}

@Immutable
data class LyricsResult(
    val trackId: String,
    val lines: List<LyricLine> = emptyList(),
    val rawText: String = "",
    val isSynced: Boolean = false,
    val hasWordSync: Boolean = false,
    val source: LyricsSource = LyricsSource.NONE,
    val syncOffsetMs: Long = 0L,
    val providerName: String = "",
    val isLoading: Boolean = false,
    val statusNote: String? = null,
)

@Immutable
data class LyricsSearchCandidate(
    val id: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val hasSyncedLyrics: Boolean,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val provider: String,
    val matchScore: Int,
)

// --- Offline Audio Analysis, Hierarchical Genre & Intelligent DJ / Automix Models ---

enum class MixMode(val label: String, val description: String) {
    SMART("Smart", "Genre-safe harmonic & tempo mixing"),
    DJ_MODE("DJ Mode", "Pro beatmatched dual-deck phrase mixing"),
    PARTY("Party", "High-energy nonstop floor transitions"),
}

enum class TransitionStyle(val label: String, val description: String) {
    SMOOTH_FADE("Smooth Fade", "Equal-power beat-aligned crossfade"),
    BASS_SWAP("Bass Swap", "Switch low-end punch cleanly on downbeat"),
    EQ_FILTER("EQ Blend", "Sculpted vocal & mid-range blend"),
    INTRO_OUTRO("Intro to Outro", "Phrase-matched outro into incoming intro"),
    BEAT_CUT("Beat Cut", "Crisp downbeat cut on phrase boundary"),
}

enum class MoodFilter(val label: String) {
    CHILL("Chill"),
    ENERGETIC("Energetic"),
    PARTY("Party"),
}

enum class HarmonicMixLevel(val label: String, val description: String) {
    STRICT("Strict", "Only exact or adjacent Camelot harmonic keys"),
    BALANCED("Balanced", "Prefer harmonic keys with safe diagonal shifts"),
    FLEXIBLE("Flexible", "Prioritize tempo & genre while favoring key matches"),
}

enum class EnergyProgression(val label: String, val description: String) {
    BUILD_UP("Build Up", "Gradually raise floor energy across the set"),
    PEAK_HOUR("Peak Hour", "Sustain high energy and driving groove"),
    STEADY("Steady Flow", "Maintain smooth consistent energy"),
    CHILL_DOWN("Chill Down", "Ease tempo and energy toward relaxed vibes"),
    WAVE("Wave", "Dynamic peaks and breathing valleys"),
}

@Immutable
data class TrackAnalysis(
    val trackId: String,
    val bpm: Float,
    val bpmConfidence: Float,
    val tempoStability: Float,
    val musicalKey: String,
    val camelotCode: String,
    val keyConfidence: Float,
    val energy: Float,
    val rmsDb: Float,
    val loudnessLufs: Float,
    val dynamicRangeDb: Float,
    val onsetDensity: Float,
    val firstBeatMs: Long,
    val beatIntervalMs: Long,
    val introEndMs: Long,
    val outroStartMs: Long,
    val durationMs: Long,
    val userCorrected: Boolean = false,
    val primaryGenre: String = "Unknown",
    val secondaryGenre: String = "",
    val subgenre: String = "",
    val genreConfidence: Float = 0.25f,
    val danceability: Float = 0.5f,
    val rhythmSignature: String = "4/4",
    val phraseLengthMs: Long = 0L,
    val downbeatConfidence: Float = 0.5f,
    val spectralCentroidHz: Float = 1500f,
)

@Immutable
data class DjProfile(
    val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean = true,
    val mixMode: MixMode = MixMode.SMART,
    val bpmMin: Int = 60,
    val bpmMax: Int = 180,
    val preferredGenres: List<String> = emptyList(),
    val strictGenreMatching: Boolean = true,
    val harmonicMixLevel: HarmonicMixLevel = HarmonicMixLevel.BALANCED,
    val energyProgression: EnergyProgression = EnergyProgression.STEADY,
    val transitionStyle: TransitionStyle = TransitionStyle.SMOOTH_FADE,
    val transitionDurationSec: Int = 8,
    val keyLockEnabled: Boolean = true,
    val phraseMatchEnabled: Boolean = true,
    val bpmWeight: Float = 0.30f,
    val harmonicWeight: Float = 0.28f,
    val energyWeight: Float = 0.18f,
    val genreWeight: Float = 0.24f,
    val openFormatBridging: Boolean = false,
)

@Immutable
data class MixHistoryEntry(
    val id: Long,
    val fromTrackId: String,
    val fromTitle: String,
    val fromArtist: String,
    val toTrackId: String,
    val toTitle: String,
    val toArtist: String,
    val mixMode: MixMode,
    val transitionStyle: TransitionStyle,
    val bpmFrom: Float,
    val bpmTo: Float,
    val camelotFrom: String,
    val camelotTo: String,
    val compatibilityScore: Int,
    val mixedAt: Long,
)

@Immutable
data class AutomixConfig(
    val enabled: Boolean = false,
    val activeProfileId: String = "club_house",
    val mode: MixMode = MixMode.SMART,
    val bpmMin: Int = 60,
    val bpmMax: Int = 180,
    val bpmMatchEnabled: Boolean = true,
    val keyMatchingEnabled: Boolean = true,
    val keyLockEnabled: Boolean = true,
    val strictGenreMatching: Boolean = true,
    val harmonicMixLevel: HarmonicMixLevel = HarmonicMixLevel.BALANCED,
    val energyProgression: EnergyProgression = EnergyProgression.STEADY,
    val phraseMatchEnabled: Boolean = true,
    val openFormatBridging: Boolean = false,
    val lookAheadCount: Int = 4,
    val bpmWeight: Float = 0.30f,
    val harmonicWeight: Float = 0.28f,
    val energyWeight: Float = 0.18f,
    val genreWeight: Float = 0.24f,
    val genrePreference: String = "All Genres",
    val mixDurationMinutes: Int = 45,
    val transitionDurationSec: Int = 8,
    val transitionStyle: TransitionStyle = TransitionStyle.SMOOTH_FADE,
    val autoSelectMood: Boolean = true,
    val mood: MoodFilter = MoodFilter.ENERGETIC,
    val autoGainEnabled: Boolean = true,
    val avoidRepeats: Boolean = true,
    val sourcePlaylistId: Long? = null,
    val sourceFolderPath: String? = null,
)

@Immutable
data class DeckState(
    val deckName: String,
    val track: Track? = null,
    val isActiveForeground: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val bpm: Float = 0f,
    val camelotCode: String = "--",
    val musicalKey: String = "--",
    val genreLabel: String = "",
    val playbackRate: Float = 1.0f,
    val gainDb: Float = 0f,
)

@Immutable
data class DjQueuedTrack(
    val track: Track,
    val analysis: TrackAnalysis?,
    val compatibilityScore: Int,
    val reason: String,
)

@Immutable
data class AutomixUiState(
    val isActive: Boolean = false,
    val isMixingTransition: Boolean = false,
    val transitionProgress: Float = 0f,
    val activeDeckIndex: Int = 0,
    val deckA: DeckState = DeckState(deckName = "Deck A", isActiveForeground = true),
    val deckB: DeckState = DeckState(deckName = "Deck B", isActiveForeground = false),
    val djQueue: List<DjQueuedTrack> = emptyList(),
    val currentCompatibilityScore: Int = 0,
    val statusText: String = "Ready to mix your local library offline",
    val noSuitableTrackReason: String? = null,
    val activeProfileName: String = "Club House",
    val isAnalyzing: Boolean = false,
    val analyzedCount: Int = 0,
    val totalTrackCount: Int = 0,
    val remainingMixMinutes: Int = 0,
)

// --- Smart Artwork, Metadata Health & Library Stats Models ---

@Immutable
data class ArtworkSearchCandidate(
    val id: String,
    val imageUrl: String,
    val title: String,
    val artist: String,
    val album: String,
    val releaseYear: Int?,
    val provider: String,
    val confidenceScore: Int,
)

@Immutable
data class MetadataHealthReport(
    val missingArtistTracks: List<Track> = emptyList(),
    val missingAlbumTracks: List<Track> = emptyList(),
    val missingGenreTracks: List<Track> = emptyList(),
    val missingArtworkTracks: List<Track> = emptyList(),
    val missingYearTracks: List<Track> = emptyList(),
    val duplicateGroups: List<DuplicateTrackGroup> = emptyList(),
) {
    val totalIssueTracks: Int
        get() = (missingArtistTracks + missingAlbumTracks + missingGenreTracks + missingArtworkTracks + missingYearTracks)
            .distinctBy { it.id }.size
}

@Immutable
data class DuplicateTrackGroup(
    val key: String,
    val title: String,
    val artist: String,
    val tracks: List<Track>,
)

@Immutable
data class LibraryStats(
    val totalTracks: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val totalPlays: Int = 0,
    val totalSkips: Int = 0,
    val favoriteCount: Int = 0,
    val analyzedCount: Int = 0,
    val topGenres: List<Pair<String, Int>> = emptyList(),
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val formatBreakdown: List<Pair<String, Int>> = emptyList(),
)

@Serializable
data class AppBackupData(
    val version: Int = 1,
    val exportedAt: Long,
    val playlists: List<BackupPlaylist> = emptyList(),
    val favorites: List<BackupTrackRef> = emptyList(),
    val excludedFolders: List<String> = emptyList(),
    val lyrics: List<BackupLyrics> = emptyList(),
    val metadataOverrides: List<BackupMetadataOverride> = emptyList(),
    val preferences: BackupPreferences? = null,
)

@Serializable
data class BackupPlaylist(
    val name: String,
    val description: String,
    val createdAt: Long,
    val tracks: List<BackupTrackRef>,
)

@Serializable
data class BackupTrackRef(
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
)

@Serializable
data class BackupLyrics(
    val trackRef: BackupTrackRef,
    val rawText: String,
    val isSynced: Boolean,
)

@Serializable
data class BackupMetadataOverride(
    val trackRef: BackupTrackRef,
    val title: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val year: Int?,
)

@Serializable
data class BackupPreferences(
    val themeMode: String,
    val accentPreset: String,
    val eqEnabled: Boolean,
    val eqPreset: String,
    val eqBandLevels: List<Int>,
    val bassBoost: Int,
    val virtualizer: Int,
    val playbackSpeed: Float,
    val audioBalance: Float,
    val crossfadeSeconds: Int,
    val gaplessEnabled: Boolean,
    val minDurationSec: Int,
    val autoScanOnLaunch: Boolean,
    val resumePausedOnReopen: Boolean,
)
