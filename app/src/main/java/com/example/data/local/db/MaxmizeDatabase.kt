package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        MetadataOverrideEntity::class,
        MetadataUndoEntity::class,
        ArtworkCacheEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        QueueItemEntity::class,
        PlaybackSnapshotEntity::class,
        SearchHistoryEntity::class,
        LyricsEntity::class,
        TrackAnalysisEntity::class,
        CustomDjProfileEntity::class,
        GenreRuleOverrideEntity::class,
        MixHistoryEntity::class,
        ExcludedFolderEntity::class,
        SafSourceEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class MaxmizeDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun artworkCacheDao(): ArtworkCacheDao
    abstract fun djConfigDao(): DjConfigDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun mixHistoryDao(): MixHistoryDao
    abstract fun exclusionDao(): ExclusionDao

    companion object {
        @Volatile
        private var INSTANCE: MaxmizeDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lyrics_cache ADD COLUMN syncOffsetMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lyrics_cache ADD COLUMN provider TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE lyrics_cache ADD COLUMN language TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE lyrics_cache ADD COLUMN hasWordSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS track_analysis (
                        trackId TEXT NOT NULL PRIMARY KEY,
                        bpm REAL NOT NULL,
                        bpmConfidence REAL NOT NULL,
                        tempoStability REAL NOT NULL,
                        musicalKey TEXT NOT NULL,
                        camelotCode TEXT NOT NULL,
                        keyConfidence REAL NOT NULL,
                        energy REAL NOT NULL,
                        rmsDb REAL NOT NULL,
                        loudnessLufs REAL NOT NULL,
                        dynamicRangeDb REAL NOT NULL,
                        onsetDensity REAL NOT NULL,
                        firstBeatMs INTEGER NOT NULL,
                        beatIntervalMs INTEGER NOT NULL,
                        introEndMs INTEGER NOT NULL,
                        outroStartMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        userCorrected INTEGER NOT NULL DEFAULT 0,
                        analyzedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mix_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fromTrackId TEXT NOT NULL,
                        fromTitle TEXT NOT NULL,
                        fromArtist TEXT NOT NULL,
                        toTrackId TEXT NOT NULL,
                        toTitle TEXT NOT NULL,
                        toArtist TEXT NOT NULL,
                        mixMode TEXT NOT NULL,
                        transitionStyle TEXT NOT NULL,
                        bpmFrom REAL NOT NULL,
                        bpmTo REAL NOT NULL,
                        camelotFrom TEXT NOT NULL,
                        camelotTo TEXT NOT NULL,
                        compatibilityScore INTEGER NOT NULL,
                        mixedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN dateModifiedSec INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN skipCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN lastSkippedAt INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE track_analysis ADD COLUMN primaryGenre TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE track_analysis ADD COLUMN secondaryGenre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE track_analysis ADD COLUMN subgenre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE track_analysis ADD COLUMN genreConfidence REAL NOT NULL DEFAULT 0.25")
                db.execSQL("ALTER TABLE track_analysis ADD COLUMN danceability REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE track_analysis ADD COLUMN rhythmSignature TEXT NOT NULL DEFAULT '4/4'")
                db.execSQL("ALTER TABLE track_analysis ADD COLUMN phraseLengthMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE track_analysis ADD COLUMN downbeatConfidence REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE track_analysis ADD COLUMN spectralCentroidHz REAL NOT NULL DEFAULT 1500.0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS metadata_undo_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        trackId TEXT NOT NULL,
                        prevTitle TEXT,
                        prevArtist TEXT,
                        prevAlbum TEXT,
                        prevAlbumArtist TEXT,
                        prevGenre TEXT,
                        prevYear INTEGER,
                        prevCustomArtworkUri TEXT,
                        savedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS artwork_cache (
                        albumKey TEXT NOT NULL PRIMARY KEY,
                        trackId TEXT NOT NULL,
                        localFileUri TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        confidenceScore INTEGER NOT NULL,
                        failedAttempts INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS custom_dj_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        mixMode TEXT NOT NULL,
                        bpmMin INTEGER NOT NULL,
                        bpmMax INTEGER NOT NULL,
                        preferredGenresCsv TEXT NOT NULL,
                        strictGenreMatching INTEGER NOT NULL,
                        harmonicMixLevel TEXT NOT NULL,
                        energyProgression TEXT NOT NULL,
                        transitionStyle TEXT NOT NULL,
                        transitionDurationSec INTEGER NOT NULL,
                        keyLockEnabled INTEGER NOT NULL,
                        phraseMatchEnabled INTEGER NOT NULL,
                        bpmWeight REAL NOT NULL,
                        harmonicWeight REAL NOT NULL,
                        energyWeight REAL NOT NULL,
                        genreWeight REAL NOT NULL,
                        openFormatBridging INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS genre_rule_overrides (
                        genreA TEXT NOT NULL,
                        genreB TEXT NOT NULL,
                        allowed INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(genreA, genreB)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): MaxmizeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MaxmizeDatabase::class.java,
                    "maxmize.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
