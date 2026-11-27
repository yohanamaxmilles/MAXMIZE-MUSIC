package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.online.AudiusMusicProvider
import com.example.data.online.OnlinePlaybackSource
import com.example.data.online.OnlineTrack
import kotlinx.coroutines.launch

@Composable
fun OnlineMusicScreen(
    apiKey: String,
    onSaveApiKey: (String) -> Unit,
    onPlayTrack: (OnlineTrack, OnlinePlaybackSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var apiKeyDraft by remember { mutableStateOf(apiKey) }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var playingTrackId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Search music from independent artists on Audius.") }

    LaunchedEffect(apiKey) { apiKeyDraft = apiKey }

    fun searchMusic() {
        if (apiKeyDraft.isBlank()) {
            message = "Add your free Audius API key first."
            return
        }
        if (query.isBlank()) {
            message = "Enter a song, artist or genre."
            return
        }
        scope.launch {
            isSearching = true
            message = ""
            tracks = emptyList()
            runCatching { AudiusMusicProvider(apiKeyDraft).search(query) }
                .onSuccess { result ->
                    tracks = result.tracks
                    message = if (result.tracks.isEmpty()) {
                        "No public, streamable tracks found. Try another search."
                    } else {
                        "Found ${result.tracks.size} tracks. Select Play to listen."
                    }
                }
                .onFailure { error ->
                    message = error.message ?: "Search failed. Check your connection and API key."
                }
            isSearching = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "MAXMIZE",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 3f),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Online Music",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Search and stream public tracks from independent artists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Connect Audius", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Audius offers a free API plan. Create a key at api.audius.co/plans, then paste it below. No subscription or payment is required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { uriHandler.openUri("https://api.audius.co/plans") }) {
                        Text("Get a free Audius API key")
                    }
                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Audius API key") },
                        placeholder = { Text("Paste your API key") },
                    )
                    Button(onClick = { onSaveApiKey(apiKeyDraft) }, enabled = apiKeyDraft.isNotBlank()) {
                        Text("Save API key")
                    }
                    if (apiKey.isNotBlank()) {
                        Text("API key saved on this device.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search songs, artists or genres") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    )
                    Button(
                        onClick = ::searchMusic,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSearching,
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Searching")
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Search Audius")
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) {
            item {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(tracks, key = { "${it.providerId}:${it.id}" }) { track ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = "Artwork for ${track.title}",
                        modifier = Modifier.size(62.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                        Text(track.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text("Audius · ${formatTrackDuration(track.durationMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (track.permalink != null) {
                            TextButton(onClick = { uriHandler.openUri(track.permalink) }, contentPadding = PaddingValues(0.dp)) {
                                Text("Open artist track page")
                            }
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                playingTrackId = track.id
                                runCatching { AudiusMusicProvider(apiKeyDraft).getPlaybackSource(track) }
                                    .onSuccess { source ->
                                        onPlayTrack(track, source)
                                        message = "Sent ${track.title} to the MAXMIZE player."
                                    }
                                    .onFailure { error -> message = error.message ?: "Unable to play this track." }
                                playingTrackId = null
                            }
                        },
                        enabled = playingTrackId == null,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (playingTrackId == track.id) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${track.title}")
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Music provided by Audius. Some tracks are unavailable for third-party streaming and are excluded from results. DJ processing for online tracks is disabled.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTrackDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
