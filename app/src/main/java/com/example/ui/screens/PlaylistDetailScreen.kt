package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PlaybackUiState
import com.example.data.model.PlaylistWithTracks
import com.example.data.model.Track
import com.example.ui.components.PlaylistCollageArtwork
import com.example.ui.components.TrackRow
import com.example.ui.components.formatDurationSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistWithTracks: PlaylistWithTracks?,
    allLibraryTracks: List<Track>,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int, Boolean) -> Unit,
    onRenameAndDescribe: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddTracks: (Long, List<String>) -> Unit,
    onRemoveTrack: (Long, String) -> Unit,
    onReorderTracks: (Long, List<String>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMenuAction: (Track, TrackMenuEvent) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }

    val playlist = playlistWithTracks?.playlist
    val tracks = playlistWithTracks?.tracks.orEmpty()

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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
                    text = playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (playlist != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAddSongsSheet = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add songs")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit Name & Description") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = { menuExpanded = false; showEditDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Playlist") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = { menuExpanded = false; showDeleteConfirm = true },
                            )
                        }
                    }
                }
            }
        }

        if (playlist == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Playlist not found")
            }
            return
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
                    PlaylistCollageArtwork(
                        customCoverUri = playlist.customCoverUri,
                        artworkUris = playlist.artworkUris,
                        modifier = Modifier.size(176.dp),
                        cornerRadius = 16.dp,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    if (playlist.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playlist.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${tracks.size} songs · ${formatDurationSummary(tracks.sumOf { it.durationMs })}",
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
                            Text("Play All")
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

            if (tracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("This playlist is empty", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { showAddSongsSheet = true },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Songs")
                        }
                    }
                }
            } else {
                itemsIndexed(tracks, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TrackRow(
                                track = track,
                                isCurrent = playbackState.currentTrack?.id == track.id,
                                isPlaying = playbackState.isPlaying,
                                onClick = { onPlayTracks(tracks, index, false) },
                                onToggleFavorite = { onToggleFavorite(track.id) },
                                onPlayNext = { onTrackMenuAction(track, TrackMenuEvent.PlayNext) },
                                onAddToQueue = { onTrackMenuAction(track, TrackMenuEvent.AddToQueue) },
                                onEditMetadata = { onTrackMenuAction(track, TrackMenuEvent.EditMetadata) },
                                onViewLyrics = { onTrackMenuAction(track, TrackMenuEvent.ViewLyrics) },
                                onTrackInfo = { onTrackMenuAction(track, TrackMenuEvent.TrackInfo) },
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val mutable = tracks.map { it.id }.toMutableList()
                                    val moved = mutable.removeAt(index)
                                    mutable.add(index - 1, moved)
                                    onReorderTracks(playlist.id, mutable)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                if (index < tracks.lastIndex) {
                                    val mutable = tracks.map { it.id }.toMutableList()
                                    val moved = mutable.removeAt(index)
                                    mutable.add(index + 1, moved)
                                    onReorderTracks(playlist.id, mutable)
                                }
                            },
                            enabled = index < tracks.lastIndex,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onRemoveTrack(playlist.id, track.id) },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                Icons.Rounded.RemoveCircleOutline,
                                contentDescription = "Remove from playlist",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && playlist != null) {
        CreateOrEditPlaylistDialog(
            title = "Edit Playlist",
            initialName = playlist.name,
            initialDescription = playlist.description,
            confirmLabel = "Save",
            onConfirm = { name, desc ->
                onRenameAndDescribe(playlist.id, name, desc)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showDeleteConfirm && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("Your local audio files will not be deleted.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDeletePlaylist(playlist.id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showAddSongsSheet && playlist != null) {
        val existingIds = tracks.map { it.id }.toSet()
        val available = allLibraryTracks.filterNot { it.id in existingIds }
        var pickedIds by remember { mutableStateOf(setOf<String>()) }

        ModalBottomSheet(
            onDismissRequest = { showAddSongsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Add Songs to ${playlist.name}", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = {
                            if (pickedIds.isNotEmpty()) {
                                onAddTracks(playlist.id, pickedIds.toList())
                            }
                            showAddSongsSheet = false
                        },
                        enabled = pickedIds.isNotEmpty(),
                        modifier = Modifier.testTag("confirm_add_songs_to_playlist"),
                    ) {
                        Text("Add (${pickedIds.size})")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (available.isEmpty()) {
                    Text(
                        "All indexed tracks are already in this playlist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        itemsIndexed(available, key = { _, t -> t.id }) { _, track ->
                            val checked = track.id in pickedIds
                            ListItem(
                                headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            pickedIds = if (checked) pickedIds - track.id else pickedIds + track.id
                                        },
                                    )
                                },
                                modifier = Modifier.clickable {
                                    pickedIds = if (checked) pickedIds - track.id else pickedIds + track.id
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
