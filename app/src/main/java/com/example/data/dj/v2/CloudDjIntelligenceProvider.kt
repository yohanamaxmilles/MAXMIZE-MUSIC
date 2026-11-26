package com.example.data.dj.v2

/** Hybrid-mode boundary. Credentials belong on an authenticated backend, never in the APK. */
interface CloudDjIntelligenceProvider {
    suspend fun enrich(request: CloudDjEnrichmentRequest): CloudDjEnrichmentResult
}

data class CloudDjEnrichmentRequest(
    val trackId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val localAnalysisVersion: Int,
    val localGenre: String?,
    val localBpm: Float?,
    val localBpmConfidence: Float,
    val localKey: String?,
    val localKeyConfidence: Float,
    val audioFingerprint: String?,
    val userConsentedToMetadataLookup: Boolean,
)

data class CloudDjEnrichmentResult(
    val contentKind: AudioContentKind?,
    val contentConfidence: Float,
    val primaryGenre: String?,
    val subgenre: String?,
    val genreFamily: String?,
    val genreConfidence: Float,
    val moodTags: Set<String>,
    val source: String,
    val modelVersion: String,
    val expiresAtEpochMs: Long?,
)
