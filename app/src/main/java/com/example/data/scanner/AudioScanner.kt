package com.example.data.scanner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.db.ExclusionDao
import com.example.data.local.db.SafSourceEntity
import com.example.data.local.db.TrackDao
import com.example.data.local.db.TrackEntity
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.model.ScanState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class AudioScanner(
    private val context: Context,
    private val trackDao: TrackDao,
    private val exclusionDao: ExclusionDao,
    private val prefsRepo: UserPreferencesRepository,
) {
    private val scanMutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var enrichmentJob: Job? = null
    private var watcherJob: Job? = null
    private var mediaObserver: ContentObserver? = null

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "mp4", "3gp", "amr"
        )
        private val ALBUM_ART_BASE_URI = Uri.parse("content://media/external/audio/albumart")
        private const val CHUNK_SIZE = 80
    }

    fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun startWatchingMediaStore(onLibraryUpdated: (() -> Unit)? = null) {
        if (mediaObserver != null || !hasAudioPermission()) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                watcherJob?.cancel()
                watcherJob = backgroundScope.launch {
                    delay(1500L)
                    scanLibrary(forceMetadataRefresh = false)
                    onLibraryUpdated?.invoke()
                }
            }
        }
        mediaObserver = observer
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        }
    }

    fun stopWatchingMediaStore() {
        mediaObserver?.let { obs ->
            runCatching { context.contentResolver.unregisterContentObserver(obs) }
        }
        mediaObserver = null
        watcherJob?.cancel()
    }

    suspend fun importSafSource(uri: Uri, isTree: Boolean): Int = withContext(Dispatchers.IO) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }

        val displayName = runCatching {
            if (isTree) {
                DocumentFile.fromTreeUri(context, uri)?.name
            } else {
                DocumentFile.fromSingleUri(context, uri)?.name
            }
        }.getOrNull() ?: "Imported Audio"

        exclusionDao.upsertSafSource(
            SafSourceEntity(
                treeUri = uri.toString(),
                isTree = isTree,
                displayName = displayName,
            )
        )
        scanLibrary(forceMetadataRefresh = false)
    }

    /**
     * Pipeline A: Ultra-fast MediaStore & SAF discovery with progressive chunked upserts (every 80 tracks)
     * and DATE_MODIFIED incremental skipping. Never blocks UI on slow MediaMetadataRetriever calls.
     */
    suspend fun scanLibrary(forceMetadataRefresh: Boolean = false): Int = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            _scanState.value = ScanState(
                isScanning = true,
                scannedCount = 0,
                totalFound = 0,
                statusMessage = "Scanning local audio library...",
            )

            val existingMap = trackDao.getAllTracksOnce().associateBy { it.id }
            val excludedFolders = exclusionDao.getExcludedFoldersOnce().map { it.folderPath }.toSet()
            val prefs = prefsRepo.getOnce()
            val minDurationMs = prefs.minDurationSec * 1000L

            val discoveredIds = mutableListOf<String>()
            val seenDedupKeys = HashSet<String>()
            val pendingBatch = mutableListOf<TrackEntity>()
            var totalIndexed = 0

            suspend fun flushBatch() {
                if (pendingBatch.isNotEmpty()) {
                    trackDao.upsertTracks(pendingBatch.toList())
                    pendingBatch.clear()
                }
            }

            if (hasAudioPermission()) {
                queryMediaStoreFast(
                    existingMap = existingMap,
                    excludedFolders = excludedFolders,
                    minDurationMs = minDurationMs,
                    forceRefresh = forceMetadataRefresh,
                    onTrackDiscovered = { entity ->
                        val dedupKey = "${entity.filePath.lowercase()}|${entity.fileSizeBytes}|${entity.durationMs}"
                        if (seenDedupKeys.add(dedupKey)) {
                            discoveredIds.add(entity.id)
                            pendingBatch.add(entity)
                            totalIndexed++
                        }
                    },
                    onChunkBoundary = {
                        if (pendingBatch.size >= CHUNK_SIZE) {
                            trackDao.upsertTracks(pendingBatch.toList())
                            pendingBatch.clear()
                        }
                    },
                )
                flushBatch()
            }

            val safSources = exclusionDao.getSafSourcesOnce()
            for (source in safSources) {
                val safTracks = scanSafSource(
                    source = source,
                    existingMap = existingMap,
                    excludedFolders = excludedFolders,
                    minDurationMs = minDurationMs,
                    forceRefresh = forceMetadataRefresh,
                )
                for (t in safTracks) {
                    val dedupKey = "${t.title.lowercase()}|${t.fileSizeBytes}|${t.durationMs}"
                    if (seenDedupKeys.add(dedupKey)) {
                        discoveredIds.add(t.id)
                        pendingBatch.add(t)
                        totalIndexed++
                        if (pendingBatch.size >= CHUNK_SIZE) flushBatch()
                    }
                }
            }
            flushBatch()

            if (discoveredIds.isEmpty()) {
                trackDao.markAllUnavailable()
            } else {
                trackDao.markUnavailableExcept(discoveredIds)
            }
            trackDao.deleteUnavailableTracks()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val msVer = MediaStore.getVersion(context)
                    prefsRepo.setMediaStoreVersionToken(msVer)
                }
            }

            val now = System.currentTimeMillis()
            _scanState.value = ScanState(
                isScanning = false,
                scannedCount = totalIndexed,
                totalFound = totalIndexed,
                lastScanAt = now,
                statusMessage = if (totalIndexed == 0) "No audio files found" else "Indexed $totalIndexed tracks",
            )

            // Pipeline B: Kick off non-blocking bounded technical metadata enrichment
            if (totalIndexed > 0) {
                startBackgroundEnrichment(force = forceMetadataRefresh)
            }

            totalIndexed
        }
    }

    /**
     * Pipeline B: Bounded (2-worker) asynchronous MediaMetadataRetriever enrichment for technical tags
     * (bitrate, sampleRate, embedded genre/year) without delaying initial library rendering.
     */
    fun startBackgroundEnrichment(force: Boolean = false) {
        enrichmentJob?.cancel()
        enrichmentJob = backgroundScope.launch {
            val all = trackDao.getAllTracksOnce()
            val needsEnrich = all.filter { t ->
                force || t.bitrateKbps == 0 || t.genre.equals("Unknown Genre", ignoreCase = true) || t.year == 0
            }
            if (needsEnrich.isEmpty()) return@launch

            val semaphore = Semaphore(2)
            val updates = mutableListOf<TrackEntity>()
            for (t in needsEnrich) {
                semaphore.withPermit {
                    val enriched = extractTechnicalMetadata(Uri.parse(t.contentUri))
                    val newGenre = if ((t.genre.isBlank() || t.genre.equals("Unknown Genre", true)) && enriched.genre.isNotBlank()) {
                        enriched.genre
                    } else t.genre
                    val newYear = if (t.year == 0 && enriched.year > 0) enriched.year else t.year
                    val newBitrate = if (enriched.bitrateKbps > 0) enriched.bitrateKbps else t.bitrateKbps
                    val newSampleRate = if (enriched.sampleRateHz > 0) enriched.sampleRateHz else t.sampleRateHz

                    if (newGenre != t.genre || newYear != t.year || newBitrate != t.bitrateKbps || newSampleRate != t.sampleRateHz) {
                        updates.add(
                            t.copy(
                                genre = newGenre,
                                year = newYear,
                                bitrateKbps = newBitrate,
                                sampleRateHz = newSampleRate,
                            )
                        )
                    }
                    if (updates.size >= 25) {
                        trackDao.upsertTracks(updates.toList())
                        updates.clear()
                    }
                }
            }
            if (updates.isNotEmpty()) {
                trackDao.upsertTracks(updates.toList())
            }
        }
    }

    private suspend fun queryMediaStoreFast(
        existingMap: Map<String, TrackEntity>,
        excludedFolders: Set<String>,
        minDurationMs: Long,
        forceRefresh: Boolean,
        onTrackDiscovered: (TrackEntity) -> Unit,
        onChunkBoundary: suspend () -> Unit,
    ) {
        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.ALBUM_ARTIST)
            projectionList.add(MediaStore.Audio.Media.GENRE)
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projectionList.toTypedArray(),
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val total = cursor.count
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val albumArtistCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                } else -1
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                var processed = 0
                while (cursor.moveToNext()) {
                    processed++
                    if (processed % 25 == 0 || processed == total) {
                        _scanState.value = _scanState.value.copy(
                            scannedCount = processed,
                            totalFound = total,
                        )
                    }

                    val id = cursor.getLong(idCol)
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    if (durationMs < minDurationMs) continue

                    val displayName = if (displayCol >= 0) cursor.getString(displayCol).orEmpty() else ""
                    val rawPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else ""
                    val filePath = rawPath.ifBlank { "/$relPath$displayName" }

                    if (rawPath.isNotBlank()) {
                        val file = File(rawPath)
                        if (file.parentFile?.exists() == true && !file.exists()) continue
                    }

                    val folderPath = when {
                        rawPath.isNotBlank() -> File(rawPath).parent ?: "/"
                        relPath.isNotBlank() -> "/${relPath.trimEnd('/')}"
                        else -> "/Music"
                    }
                    if (excludedFolders.any { folderPath == it || folderPath.startsWith("$it/") }) {
                        continue
                    }

                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
                    if (ext.isNotBlank() && ext !in SUPPORTED_EXTENSIONS && !mime.startsWith("audio/")) {
                        continue
                    }

                    val trackId = "ms_$id"
                    val existing = existingMap[trackId]
                    val dateModifiedSec = if (modCol >= 0) cursor.getLong(modCol) else 0L

                    // Incremental fast-path: skip parsing unchanged records unless forceRefresh
                    if (!forceRefresh && existing != null &&
                        dateModifiedSec > 0L && existing.dateModifiedSec == dateModifiedSec &&
                        existing.durationMs == durationMs
                    ) {
                        onTrackDiscovered(existing.copy(isAvailable = true))
                        onChunkBoundary()
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol).orEmpty() else ""
                    val title = rawTitle.ifBlank { displayName.substringBeforeLast('.') }.ifBlank { "Untitled Track" }
                    val rawArtist = if (artistCol >= 0) cursor.getString(artistCol).orEmpty() else ""
                    val artist = normalizeArtist(rawArtist)
                    val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol).orEmpty() else ""
                    val album = normalizeAlbum(rawAlbum, folderPath)
                    val albumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol).orEmpty().ifBlank { artist } else artist
                    val genre = if (genreCol >= 0) {
                        cursor.getString(genreCol).orEmpty()
                    } else existing?.genre.orEmpty()
                    val trackNum = if (trackCol >= 0) cursor.getInt(trackCol) % 1000 else 0
                    val year = (if (yearCol >= 0) cursor.getInt(yearCol) else 0)
                        .takeIf { it > 0 } ?: (existing?.year ?: 0)
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else null
                    val artworkUri = albumId?.takeIf { it > 0L }?.let {
                        ContentUris.withAppendedId(ALBUM_ART_BASE_URI, it).toString()
                    }

                    onTrackDiscovered(
                        TrackEntity(
                            id = trackId,
                            mediaStoreId = id,
                            contentUri = contentUri.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            albumArtist = albumArtist,
                            genre = genre.ifBlank { "Unknown Genre" },
                            durationMs = durationMs,
                            trackNumber = trackNum,
                            year = year,
                            mimeType = mime.ifBlank { "audio/*" },
                            filePath = filePath,
                            folderPath = folderPath,
                            folderName = folderPath.substringAfterLast('/').ifBlank { "Internal Storage" },
                            fileSizeBytes = sizeBytes,
                            dateAddedSec = dateAddedSec,
                            dateModifiedSec = dateModifiedSec,
                            albumId = albumId,
                            artworkUri = artworkUri,
                            playCount = existing?.playCount ?: 0,
                            lastPlayedAt = existing?.lastPlayedAt ?: 0L,
                            isFavorite = existing?.isFavorite ?: false,
                            rating = existing?.rating ?: 0,
                            skipCount = existing?.skipCount ?: 0,
                            lastSkippedAt = existing?.lastSkippedAt ?: 0L,
                            isAvailable = true,
                            sampleRateHz = existing?.sampleRateHz ?: 0,
                            bitrateKbps = existing?.bitrateKbps ?: 0,
                        )
                    )
                    onChunkBoundary()
                }
            }
        }
    }

    private fun scanSafSource(
        source: SafSourceEntity,
        existingMap: Map<String, TrackEntity>,
        excludedFolders: Set<String>,
        minDurationMs: Long,
        forceRefresh: Boolean,
    ): List<TrackEntity> {
        val uri = Uri.parse(source.treeUri)
        val result = mutableListOf<TrackEntity>()
        val files = mutableListOf<Pair<DocumentFile, String>>()

        if (source.isTree) {
            val root = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
            collectSafAudioFiles(root, "/SAF/${source.displayName}", files)
        } else {
            val single = DocumentFile.fromSingleUri(context, uri) ?: return emptyList()
            if (single.exists()) {
                files.add(single to "/SAF/Imported")
            }
        }

        for ((doc, folderPath) in files) {
            if (excludedFolders.any { folderPath == it || folderPath.startsWith("$it/") }) continue
            val docUri = doc.uri
            val trackId = "saf_${sha1Short(docUri.toString())}"
            val existing = existingMap[trackId]
            val modSec = (doc.lastModified() / 1000L).coerceAtLeast(0L)

            val shouldExtract = forceRefresh || existing == null || (modSec > 0L && existing.dateModifiedSec != modSec)
            val meta = if (shouldExtract) extractFullMetadata(docUri, doc.name.orEmpty()) else null

            val durationMs = meta?.durationMs ?: existing?.durationMs ?: 0L
            if (durationMs < minDurationMs) continue

            result.add(
                TrackEntity(
                    id = trackId,
                    mediaStoreId = null,
                    contentUri = docUri.toString(),
                    title = meta?.title ?: existing?.title ?: doc.name.orEmpty().substringBeforeLast('.'),
                    artist = meta?.artist ?: existing?.artist ?: "Unknown Artist",
                    album = meta?.album ?: existing?.album ?: source.displayName,
                    albumArtist = meta?.albumArtist ?: existing?.albumArtist ?: "Unknown Artist",
                    genre = meta?.genre?.ifBlank { "Unknown Genre" } ?: existing?.genre ?: "Unknown Genre",
                    durationMs = durationMs,
                    trackNumber = meta?.trackNumber ?: existing?.trackNumber ?: 0,
                    year = meta?.year ?: existing?.year ?: 0,
                    mimeType = doc.type ?: existing?.mimeType ?: "audio/*",
                    filePath = "$folderPath/${doc.name.orEmpty()}",
                    folderPath = folderPath,
                    folderName = folderPath.substringAfterLast('/').ifBlank { source.displayName },
                    fileSizeBytes = doc.length(),
                    dateAddedSec = modSec.takeIf { it > 0 } ?: (System.currentTimeMillis() / 1000L),
                    dateModifiedSec = modSec,
                    albumId = null,
                    artworkUri = docUri.toString(),
                    playCount = existing?.playCount ?: 0,
                    lastPlayedAt = existing?.lastPlayedAt ?: 0L,
                    isFavorite = existing?.isFavorite ?: false,
                    rating = existing?.rating ?: 0,
                    skipCount = existing?.skipCount ?: 0,
                    lastSkippedAt = existing?.lastSkippedAt ?: 0L,
                    isAvailable = true,
                    sampleRateHz = meta?.sampleRateHz ?: existing?.sampleRateHz ?: 0,
                    bitrateKbps = meta?.bitrateKbps ?: existing?.bitrateKbps ?: 0,
                )
            )
        }
        return result
    }

    private fun collectSafAudioFiles(
        dir: DocumentFile,
        currentPath: String,
        out: MutableList<Pair<DocumentFile, String>>,
        depth: Int = 0,
    ) {
        if (depth > 8) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (child.isDirectory) {
                val childName = child.name ?: "Folder"
                collectSafAudioFiles(child, "$currentPath/$childName", out, depth + 1)
            } else if (child.isFile) {
                val name = child.name.orEmpty()
                val ext = name.substringAfterLast('.', "").lowercase()
                val mime = child.type.orEmpty()
                if (ext in SUPPORTED_EXTENSIONS || mime.startsWith("audio/")) {
                    out.add(child to currentPath)
                }
            }
        }
    }

    private data class TechnicalMeta(
        val genre: String = "",
        val year: Int = 0,
        val bitrateKbps: Int = 0,
        val sampleRateHz: Int = 0,
    )

    private data class FullMeta(
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val genre: String,
        val durationMs: Long,
        val trackNumber: Int,
        val year: Int,
        val bitrateKbps: Int,
        val sampleRateHz: Int,
    )

    private fun extractTechnicalMetadata(uri: Uri): TechnicalMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty()
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            } else 0
            TechnicalMeta(
                genre = genre,
                year = year,
                bitrateKbps = bitrateBps / 1000,
                sampleRateHz = sampleRate,
            )
        } catch (_: Throwable) {
            TechnicalMeta()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractFullMetadata(uri: Uri, fileName: String): FullMeta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.ifBlank { null }
                ?: fileName.substringBeforeLast('.').ifBlank { "Untitled Track" }
            val artist = normalizeArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty())
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.ifBlank { null } ?: "Unknown Album"
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.ifBlank { null } ?: artist
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty()
            val trackNum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.toIntOrNull() ?: 0
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            } else 0
            FullMeta(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                durationMs = duration,
                trackNumber = trackNum,
                year = year,
                bitrateKbps = bitrateBps / 1000,
                sampleRateHz = sampleRate,
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun normalizeArtist(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed.equals("<unknown>", ignoreCase = true)) {
            "Unknown Artist"
        } else {
            trimmed
        }
    }

    private fun normalizeAlbum(raw: String, folderPath: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed.equals("<unknown>", ignoreCase = true)) {
            folderPath.substringAfterLast('/').ifBlank { "Unknown Album" }
        } else {
            trimmed
        }
    }

    private fun sha1Short(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return bytes.take(10).joinToString("") { "%02x".format(it) }
    }
}
