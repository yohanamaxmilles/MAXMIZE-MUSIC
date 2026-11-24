package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.db.MetadataUndoEntity
import com.example.data.model.LibraryStats
import com.example.data.model.MetadataHealthReport
import com.example.data.model.Track
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimecode
import com.example.ui.theme.MonoTimecodeStyle

@Composable
fun LibraryStatsDialog(
    stats: LibraryStats,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library Analytics") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatLine("Total Tracks", "${stats.totalTracks}")
                StatLine("Total Duration", formatTimecode(stats.totalDurationMs))
                StatLine("Total Storage", formatFileSize(stats.totalSizeBytes))
                StatLine("Total Plays / Early Skips", "${stats.totalPlays} plays · ${stats.totalSkips} skips")
                StatLine("Favorites / DSP Analyzed", "${stats.favoriteCount} fav · ${stats.analyzedCount} analyzed")

                if (stats.formatBreakdown.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Formats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    stats.formatBreakdown.forEach { (fmt, cnt) ->
                        StatLine(fmt, "$cnt tracks")
                    }
                }
                if (stats.topGenres.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Top Genres", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    stats.topGenres.forEach { (g, cnt) ->
                        StatLine(g, "$cnt tracks")
                    }
                }
                if (stats.topTracks.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Most Played", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    stats.topTracks.forEach { t ->
                        StatLine(t.title, "${t.playCount} plays")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun MetadataHealthDialog(
    health: MetadataHealthReport,
    onEditTrack: (Track) -> Unit,
    onAutoFixArtwork: (Track) -> Unit,
    onBatchFixArtwork: () -> Unit,
    onDismiss: () -> Unit,
) {
    val flagged = (health.missingArtistTracks +
        health.missingAlbumTracks +
        health.missingGenreTracks +
        health.missingArtworkTracks +
        health.missingYearTracks).distinctBy { it.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metadata Health (${health.totalIssueTracks})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatLine("Missing Artist", "${health.missingArtistTracks.size}")
                StatLine("Missing Album", "${health.missingAlbumTracks.size}")
                StatLine("Missing Genre", "${health.missingGenreTracks.size}")
                StatLine("Missing Cover Art", "${health.missingArtworkTracks.size}")
                StatLine("Missing Release Year", "${health.missingYearTracks.size}")

                if (flagged.isEmpty()) {
                    Text(
                        text = "Your library metadata looks clean and complete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Tap a track to fix tags or fetch art:", style = MaterialTheme.typography.labelMedium)
                    flagged.take(15).forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onEditTrack(track)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${track.artist} · ${track.genre}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (track.customArtworkUri.isNullOrBlank() && track.artworkUri.isNullOrBlank()) {
                                IconButton(onClick = { onAutoFixArtwork(track) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Fetch cover art", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    onDismiss()
                                    onEditTrack(track)
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit track tags", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (health.missingArtworkTracks.isNotEmpty()) {
                    TextButton(onClick = {
                        onBatchFixArtwork()
                        onDismiss()
                    }) { Text("Auto-Fix Covers") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
fun DuplicateFinderDialog(
    health: MetadataHealthReport,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Potential Duplicates (${health.duplicateGroups.size})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (health.duplicateGroups.isEmpty()) {
                    Text("No duplicate songs detected in your library.")
                } else {
                    health.duplicateGroups.take(15).forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "${group.title} · ${group.artist} (${group.tracks.size} copies)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                            group.tracks.forEach { tr ->
                                Text(
                                    text = "${tr.formatLabel} ${if (tr.bitrateKbps > 0) "${tr.bitrateKbps}kbps" else ""} · ${tr.filePath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun UndoHistoryDialog(
    undoHistory: List<MetadataUndoEntity>,
    onUndoEntry: (MetadataUndoEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metadata Edit Undo History") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (undoHistory.isEmpty()) {
                    Text("No recent metadata edits recorded.")
                } else {
                    undoHistory.take(15).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.prevTitle ?: entry.trackId,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Prev: ${entry.prevArtist ?: "--"} · ${entry.prevAlbum ?: "--"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { onUndoEntry(entry) }) {
                                Icon(Icons.Rounded.Undo, contentDescription = "Revert metadata edit")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MonoTimecodeStyle.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
