package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PlaybackUiState
import com.example.data.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.TrackRow
import com.example.ui.components.formatDurationSummary

@Composable
fun CollectionDetailScreen(
    title: String,
    subtitle: String,
    artworkUri: String?,
    tracks: List<Track>,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMenuAction: (Track, TrackMenuEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Back",
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtworkImage(
                        artworkUri = artworkUri,
                        fallbackUri = tracks.firstOrNull()?.contentUri,
                        modifier = Modifier.size(176.dp),
                        cornerRadius = 16.dp,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$subtitle · ${tracks.size} songs · ${formatDurationSummary(tracks.sumOf { it.durationMs })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { onPlayTracks(tracks, 0, false) },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play")
                        }
                        OutlinedButton(
                            onClick = { onPlayTracks(tracks, 0, true) },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                }
                HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    isCurrent = playbackState.currentTrack?.id == track.id,
                    isPlaying = playbackState.isPlaying,
                    onClick = { onPlayTracks(tracks, index, false) },
                    onToggleFavorite = { onToggleFavorite(track.id) },
                    onPlayNext = { onTrackMenuAction(track, TrackMenuEvent.PlayNext) },
                    onAddToQueue = { onTrackMenuAction(track, TrackMenuEvent.AddToQueue) },
                    onAddToPlaylist = { onTrackMenuAction(track, TrackMenuEvent.AddToPlaylist) },
                    onEditMetadata = { onTrackMenuAction(track, TrackMenuEvent.EditMetadata) },
                    onViewLyrics = { onTrackMenuAction(track, TrackMenuEvent.ViewLyrics) },
                    onGoToAlbum = { onTrackMenuAction(track, TrackMenuEvent.GoToAlbum) },
                    onGoToArtist = { onTrackMenuAction(track, TrackMenuEvent.GoToArtist) },
                    onTrackInfo = { onTrackMenuAction(track, TrackMenuEvent.TrackInfo) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
