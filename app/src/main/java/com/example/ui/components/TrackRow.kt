package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Track
import com.example.ui.theme.LocalDensityMetrics
import com.example.ui.theme.MonoTimecodeStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onEditMetadata: (() -> Unit)? = null,
    onViewLyrics: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onTrackInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val rowBg = when {
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
        isCurrent -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.background
    }
    val shape = RoundedCornerShape(12.dp)
    val densityMetrics = LocalDensityMetrics.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rowBg)
            .let {
                if (isCurrent || isSelected) {
                    it.border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                } else it
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 12.dp, end = 4.dp, top = densityMetrics.rowVerticalPadding, bottom = densityMetrics.rowVerticalPadding)
            .testTag("track_row_${track.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiSelectMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp),
            )
        }

        Box(contentAlignment = Alignment.Center) {
            ArtworkImage(
                artworkUri = track.customArtworkUri ?: track.artworkUri,
                fallbackUri = track.contentUri,
                modifier = Modifier.size(46.dp),
                cornerRadius = 8.dp,
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.68f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = if (isPlaying) "Currently playing" else "Current track",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Uncluttered Editorial Typography: Track Title + Artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (track.isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.cd_favorite),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(13.dp),
            )
        }

        Text(
            text = formatTimecode(track.durationMs),
            style = MonoTimecodeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!isMultiSelectMode) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("track_more_${track.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (onToggleFavorite != null) {
                        DropdownMenuItem(
                            text = { Text(if (track.isFavorite) "Remove Favorite" else "Add to Favorites") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuExpanded = false; onToggleFavorite() },
                        )
                    }
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                            onClick = { menuExpanded = false; onPlayNext() },
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            leadingIcon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
                            onClick = { menuExpanded = false; onAddToQueue() },
                        )
                    }
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                            onClick = { menuExpanded = false; onAddToPlaylist() },
                        )
                    }
                    if (onViewLyrics != null) {
                        DropdownMenuItem(
                            text = { Text("Lyrics") },
                            leadingIcon = { Icon(Icons.Rounded.Lyrics, contentDescription = null) },
                            onClick = { menuExpanded = false; onViewLyrics() },
                        )
                    }
                    if (onEditMetadata != null) {
                        DropdownMenuItem(
                            text = { Text("Edit Metadata & Art") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEditMetadata() },
                        )
                    }
                    if (onGoToAlbum != null) {
                        DropdownMenuItem(
                            text = { Text("Go to Album") },
                            leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                            onClick = { menuExpanded = false; onGoToAlbum() },
                        )
                    }
                    if (onGoToArtist != null) {
                        DropdownMenuItem(
                            text = { Text("Go to Artist") },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            onClick = { menuExpanded = false; onGoToArtist() },
                        )
                    }
                    if (onTrackInfo != null) {
                        DropdownMenuItem(
                            text = { Text("Track Info") },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = { menuExpanded = false; onTrackInfo() },
                        )
                    }
                }
            }
        }
    }
}
