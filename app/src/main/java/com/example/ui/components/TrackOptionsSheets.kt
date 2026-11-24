package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.metadata.MetadataEditor
import com.example.data.model.ArtworkSearchCandidate
import com.example.data.model.Playlist
import com.example.data.model.SleepTimerState
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    trackCount: Int,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateFields by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = if (trackCount > 1) "Add $trackCount songs to playlist" else "Add to Playlist",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (showCreateFields) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_playlist_name_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showCreateFields = false }) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreatePlaylist(newName, newDesc)
                                onDismiss()
                            }
                        },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.testTag("confirm_create_playlist_btn"),
                    ) {
                        Text("Create & Add")
                    }
                }
            } else {
                ListItem(
                    headlineContent = { Text("New Playlist", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickable { showCreateFields = true }
                        .testTag("create_new_playlist_option"),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.trackCount} songs") },
                            leadingContent = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                onSelectPlaylist(playlist.id)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoSheet(
    track: Track,
    analysis: TrackAnalysis? = null,
    canUndo: Boolean = false,
    onSetRating: ((Int) -> Unit)? = null,
    onUndoMetadata: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var localRating by remember(track.id, track.rating) { mutableIntStateOf(track.rating) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Track Details", style = MaterialTheme.typography.titleLarge)
                if (canUndo && onUndoMetadata != null) {
                    TextButton(
                        onClick = {
                            onUndoMetadata()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("undo_track_metadata_btn"),
                    ) {
                        Icon(Icons.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo Edit")
                    }
                }
            }

            if (onSetRating != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Rating", style = MaterialTheme.typography.labelLarge)
                    Row {
                        (1..5).forEach { star ->
                            IconButton(
                                onClick = {
                                    val next = if (localRating == star) 0 else star
                                    localRating = next
                                    onSetRating(next)
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (star <= localRating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "Rate $star stars",
                                    tint = if (star <= localRating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoLine("Title", track.title)
            InfoLine("Artist", track.artist)
            InfoLine("Album", track.album)
            InfoLine("Album Artist", track.albumArtist)
            InfoLine("Genre", track.genre)
            if (track.year > 0) InfoLine("Year", track.year.toString())
            if (track.trackNumber > 0) InfoLine("Track #", track.trackNumber.toString())
            InfoLine("Format", "${track.formatLabel} (${track.mimeType})")
            InfoLine("Duration", formatTimecode(track.durationMs))
            if (track.bitrateKbps > 0) InfoLine("Bitrate", "${track.bitrateKbps} kbps")
            if (track.sampleRateHz > 0) InfoLine("Sample Rate", "${track.sampleRateHz} Hz")

            if (analysis != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Audio DSP & DJ Analysis",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                InfoLine("Tempo", "${analysis.bpm.toInt()} BPM (Stability ${(analysis.tempoStability * 100).roundToInt()}%)")
                InfoLine("Key", "${analysis.camelotCode} · ${analysis.musicalKey}")
                InfoLine("Classified Genre", "${analysis.subgenre} (${analysis.primaryGenre})")
                InfoLine("Energy / Danceability", "${(analysis.energy * 100).roundToInt()}% / ${(analysis.danceability * 100).roundToInt()}%")
                InfoLine("Loudness", String.format("%.1f LUFS · DR %.1f dB", analysis.loudnessLufs, analysis.dynamicRangeDb))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoLine("File Size", formatFileSize(track.fileSizeBytes))
            InfoLine("Location", track.filePath)
            InfoLine("Plays / Skips", "${track.playCount} plays · ${track.skipCount} early skips")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMetadataSheet(
    track: Track,
    metadataEditor: MetadataEditor,
    artworkCandidates: List<ArtworkSearchCandidate> = emptyList(),
    isSearchingArtwork: Boolean = false,
    onSearchArtworkOnline: ((Track) -> Unit)? = null,
    onApplyArtworkCandidate: ((Track, ArtworkSearchCandidate, (String) -> Unit) -> Unit)? = null,
    onSave: (title: String, artist: String, album: String, genre: String, year: Int?, artworkUri: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    var yearText by remember(track.id) { mutableStateOf(if (track.year > 0) track.year.toString() else "") }
    var pickedArtworkUri by remember(track.id) { mutableStateOf(track.customArtworkUri) }
    val scope = rememberCoroutineScope()

    val suggestedTags = remember(track.id, track.filePath) {
        MetadataEditor.suggestTagsFromFilename(track)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = metadataEditor.copyArtworkToInternalStorage(track.id, uri)
                if (savedPath != null) {
                    pickedArtworkUri = savedPath
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Edit Metadata & Artwork", style = MaterialTheme.typography.titleLarge)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArtworkImage(
                    artworkUri = pickedArtworkUri ?: track.artworkUri,
                    fallbackUri = track.contentUri,
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 10.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("pick_custom_artwork_btn"),
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery")
                        }
                        if (onSearchArtworkOnline != null) {
                            OutlinedButton(
                                onClick = { onSearchArtworkOnline(track) },
                                enabled = !isSearchingArtwork,
                                modifier = Modifier.testTag("find_cover_online_btn"),
                            ) {
                                if (isSearchingArtwork) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Find Cover")
                            }
                        }
                    }
                    if (suggestedTags != null) {
                        OutlinedButton(
                            onClick = {
                                artist = suggestedTags.artist
                                title = suggestedTags.title
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("autofill_filename_btn"),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-fill from filename", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (artworkCandidates.isNotEmpty() && onApplyArtworkCandidate != null) {
                Text(
                    text = "Verified Online Covers (tap to apply)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(artworkCandidates, key = { it.id }) { cand ->
                        val cardShape = RoundedCornerShape(10.dp)
                        Column(
                            modifier = Modifier
                                .width(106.dp)
                                .clip(cardShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, cardShape)
                                .clickable {
                                    onApplyArtworkCandidate(track, cand) { localUri ->
                                        pickedArtworkUri = localUri
                                        if (album.equals("Unknown Album", true) && cand.album.isNotBlank()) {
                                            album = cand.album
                                        }
                                        if (yearText.isBlank() && (cand.releaseYear ?: 0) > 0) {
                                            yearText = cand.releaseYear.toString()
                                        }
                                    }
                                }
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ArtworkImage(
                                artworkUri = cand.imageUrl,
                                modifier = Modifier.size(94.dp),
                                cornerRadius = 8.dp,
                            )
                            Text(
                                text = "${cand.confidenceScore}% · ${cand.provider}",
                                style = MonoBadgeStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = cand.album.ifBlank { cand.title },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Song Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("Album") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { if (it.length <= 4) yearText = it.filter(Char::isDigit) },
                    label = { Text("Year") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            title,
                            artist,
                            album,
                            genre,
                            yearText.toIntOrNull(),
                            pickedArtworkUri,
                        )
                        onDismiss()
                    },
                    modifier = Modifier.testTag("save_metadata_btn"),
                ) {
                    Text("Save Changes")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    sleepTimer: SleepTimerState,
    onStartMinutes: (Int) -> Unit,
    onStopAfterCurrentTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null)
                Text(text = "Sleep Timer", style = MaterialTheme.typography.titleLarge)
            }

            if (sleepTimer.isActive) {
                Text(
                    text = if (sleepTimer.stopAfterCurrentTrack) {
                        "Playback will pause after the current track finishes."
                    } else {
                        "Pausing playback in ${formatTimecode(sleepTimer.remainingMs)}"
                    },
                    style = MonoTimecodeStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                OutlinedButton(
                    onClick = { onCancelTimer(); onDismiss() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_sleep_timer_btn"),
                ) {
                    Text("Turn Off Timer")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            val presets = listOf(10, 15, 30, 45, 60)
            presets.forEach { minutes ->
                ListItem(
                    headlineContent = { Text("$minutes minutes") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickable { onStartMinutes(minutes); onDismiss() }
                        .testTag("sleep_timer_${minutes}m"),
                )
            }
            ListItem(
                headlineContent = { Text("End of current track") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clickable { onStopAfterCurrentTrack(); onDismiss() }
                    .testTag("sleep_timer_end_of_track"),
            )
        }
    }
}
