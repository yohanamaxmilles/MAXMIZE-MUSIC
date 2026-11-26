package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.backup.BackupRepository
import com.example.data.dj.AudioAnalysisEngine
import com.example.data.local.db.MaxmizeDatabase
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.lyrics.LyricsRepository
import com.example.data.metadata.ArtworkRepository
import com.example.data.metadata.MetadataEditor
import com.example.data.repository.MusicRepository
import com.example.data.scanner.AudioScanner
import com.example.player.AudioEffectsManager
import com.example.player.AutomixRuntime
import com.example.player.PlaybackController

class AppContainer(context: Application) {
    val database: MaxmizeDatabase by lazy { MaxmizeDatabase.getInstance(context) }
    val prefsRepo: UserPreferencesRepository by lazy { UserPreferencesRepository(context) }
    val audioScanner: AudioScanner by lazy {
        AudioScanner(
            context = context,
            trackDao = database.trackDao(),
            exclusionDao = database.exclusionDao(),
            prefsRepo = prefsRepo,
        )
    }
    val musicRepository: MusicRepository by lazy {
        MusicRepository(
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao(),
            searchHistoryDao = database.searchHistoryDao(),
            exclusionDao = database.exclusionDao(),
        )
    }
    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository(
            context = context,
            lyricsDao = database.lyricsDao(),
            prefsRepo = prefsRepo,
        )
    }
    val audioAnalysisEngine: AudioAnalysisEngine by lazy {
        AudioAnalysisEngine(
            context = context,
            analysisDao = database.analysisDao(),
        )
    }
    val automixRuntime: AutomixRuntime by lazy {
        AutomixRuntime(mixHistoryDao = database.mixHistoryDao())
    }
    val metadataEditor: MetadataEditor by lazy {
        MetadataEditor(context = context, trackDao = database.trackDao())
    }
    val artworkRepository: ArtworkRepository by lazy {
        ArtworkRepository(
            context = context,
            artworkCacheDao = database.artworkCacheDao(),
            trackDao = database.trackDao(),
            prefsRepo = prefsRepo,
        )
    }
    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            context = context,
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao(),
            exclusionDao = database.exclusionDao(),
            lyricsDao = database.lyricsDao(),
            prefsRepo = prefsRepo,
        )
    }
    val audioEffectsManager: AudioEffectsManager by lazy {
        AudioEffectsManager(prefsRepo = prefsRepo)
    }
    val playbackController: PlaybackController by lazy {
        PlaybackController(
            context = context,
            musicRepository = musicRepository,
            playbackStateDao = database.playbackStateDao(),
            prefsRepo = prefsRepo,
            automixRuntime = automixRuntime,
        )
    }
}

class MaxmizeApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(64L * 1024L * 1024L)
                    .build()
            }
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }
}
