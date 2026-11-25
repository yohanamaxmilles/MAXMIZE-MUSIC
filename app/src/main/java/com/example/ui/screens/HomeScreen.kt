package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.data.model.AlbumSummary
import com.example.data.model.LibraryCategory
import com.example.data.model.PlaybackUiState
import com.example.data.model.Playlist
import com.example.data.model.ScanState
import com.example.data.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EditorialGroupCard
import com.example.ui.components.EditorialSectionHeader
import com.example.ui.components.PlaylistCollageArtwork
import com.example.ui.components.TopProfileSettingsButton
import com.example.ui.components.TrackRow
import com.example.ui.components.formatTimecode
import com.example.ui.theme.LavenderCard
import com.example.ui.theme.LavenderCardDeep
import com.example.ui.theme.LavenderCardText
import com.example.ui.theme.MonoBadgeStyle
import java.util.Calendar

@Composable
fun HomeScreen(
    tracks: List<Track>,
    albums: List<AlbumSummary>,
    playlists: List<Playlist>,
    playbackState: PlaybackUiState,
    scanState: ScanState,
    hasAudioPermission: Boolean,
    isAutomixActive: Boolean = false,
    onRequestPermission: () -> Unit,
    onRescan: (Boolean) -> Unit,
    onImportFolder: () -> Unit,
    onImportAudioFile: () -> Unit,
    onPlayTracks: (List<Track>, Int, Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDjMode: () -> Unit = {},
    onOpenCategory: (LibraryCategory) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMenuAction: (Track, TrackMenuEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflowOpen by remember { mutableStateOf(false) }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val recentlyPlayed = remember(tracks) {
        tracks.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }.take(12)
    }
    val recentlyAdded = remember(tracks) {
        tracks.sortedByDescending { it.dateAddedSec }.take(12)
    }
    val favoriteTracks = remember(tracks) {
        tracks.filter { it.isFavorite }.take(10)
    }
    val featuredAlbums = remember(albums) {
        val recent = albums.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }
        if (recent.isNotEmpty()) recent.take(10) else albums.take(10)
    }
    val recommendedPlaylists = remember(playlists) {
        playlists.sortedByDescending { it.createdAt }.take(8)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Maxmize",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 38.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TopProfileSettingsButton(onClick = onOpenSettings)
                }
            }
        }

        if (scanState.isScanning) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Updating your library…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Quick filter pills (jump into library categories)
        item {
            val chips = listOf(
                "All" to LibraryCategory.SONGS,
                "Recent" to LibraryCategory.RECENTLY_PLAYED,
                "Favorites" to LibraryCategory.FAVORITES,
                "Most played" to LibraryCategory.MOST_PLAYED,
                "Albums" to LibraryCategory.ALBUMS,
                "Artists" to LibraryCategory.ARTISTS,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            ) {
                itemsIndexed(chips, key = { _, c -> c.first }) { index, (label, category) ->
                    val selected = index == 0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable { onOpenCategory(category) }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                EmptyLibraryGuidance(
                    hasAudioPermission = hasAudioPermission,
                    isScanning = scanState.isScanning,
                    onRequestPermission = onRequestPermission,
                    onRescan = { onRescan(false) },
                    onImportFolder = onImportFolder,
                    onImportAudioFile = onImportAudioFile,
                )
            }
        } else {
            // 1. Lavender Editorial Hero Card
            item {
                HeroBannerCard(
                    tracks = tracks,
                    albums = albums,
                    isScanning = scanState.isScanning,
                    onPlayAll = { onPlayTracks(tracks, 0, false) },
                )
            }

            // 2. Continue Listening Card (when active or recent track exists)
            val continueTrack = playbackState.currentTrack ?: recentlyPlayed.firstOrNull()
            if (continueTrack != null) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    ContinueListeningHeroCard(
                        track = continueTrack,
                        totalTracks = tracks.size,
                        isPlaying = playbackState.currentTrack?.id == continueTrack.id && playbackState.isPlaying,
                        positionMs = if (playbackState.currentTrack?.id == continueTrack.id) playbackState.positionMs else 0L,
                        onCardClick = {
                            if (playbackState.currentTrack?.id == continueTrack.id) {
                                onOpenNowPlaying()
                            } else {
                                onPlayTracks(listOf(continueTrack), 0, false)
                            }
                        },
                        onPlayPauseClick = {
                            if (playbackState.currentTrack?.id == continueTrack.id) {
                                onTogglePlayPause()
                            } else {
                                onPlayTracks(listOf(continueTrack), 0, false)
                            }
                        },
                        onPlayAll = { onPlayTracks(tracks, 0, false) },
                    )
                }
            }

            // 3. Quick DJ Mode Card
            item {
                Spacer(modifier = Modifier.height(12.dp))
                QuickDjModeCard(
                    isAutomixActive = isAutomixActive,
                    onOpenDjMode = onOpenDjMode,
                )
            }

            // 3. Recently Played
            if (recentlyPlayed.isNotEmpty()) {
                item {
                    EditorialSectionHeader(
                        title = "Recently Played",
                        actionLabel = "See all",
                        onAction = { onOpenCategory(LibraryCategory.RECENTLY_PLAYED) },
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(recentlyPlayed, key = { _, t -> "rp_${t.id}" }) { index, track ->
                            EditorialSquareTrackCard(
                                track = track,
                                onClick = { onPlayTracks(recentlyPlayed, index, false) },
                            )
                        }
                    }
                }
            }

            // 4. Recently Added
            if (recentlyAdded.isNotEmpty()) {
                item {
                    EditorialSectionHeader(
                        title = "Recently Added",
                        actionLabel = "See all",
                        onAction = { onOpenCategory(LibraryCategory.RECENTLY_ADDED) },
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(recentlyAdded, key = { _, t -> "add_${t.id}" }) { index, track ->
                            EditorialSquareTrackCard(
                                track = track,
                                onClick = { onPlayTracks(recentlyAdded, index, false) },
                            )
                        }
                    }
                }
            }

            // 5. Favorites
            if (favoriteTracks.isNotEmpty()) {
                item {
                    EditorialSectionHeader(
                        title = "Favorites",
                        actionLabel = "See all",
                        onAction = { onOpenCategory(LibraryCategory.FAVORITES) },
                    )
                }
                itemsIndexed(favoriteTracks.take(4), key = { _, t -> "fav_${t.id}" }) { index, track ->
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = track,
                            isCurrent = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            onClick = { onPlayTracks(favoriteTracks, index, false) },
                            onToggleFavorite = { onToggleFavorite(track.id) },
                            onPlayNext = { onTrackMenuAction(track, TrackMenuEvent.PlayNext) },
                            onAddToQueue = { onTrackMenuAction(track, TrackMenuEvent.AddToQueue) },
                            onAddToPlaylist = { onTrackMenuAction(track, TrackMenuEvent.AddToPlaylist) },
                            onEditMetadata = { onTrackMenuAction(track, TrackMenuEvent.EditMetadata) },
                            onViewLyrics = { onTrackMenuAction(track, TrackMenuEvent.ViewLyrics) },
                            onGoToAlbum = { onTrackMenuAction(track, TrackMenuEvent.GoToAlbum) },
                            onGoToArtist = { onTrackMenuAction(track, TrackMenuEvent.GoToArtist) },
                            onTrackInfo = { onTrackMenuAction(track, TrackMenuEvent.TrackInfo) },
                        )
                    }
                }
            }

            // 6. Featured Albums
            if (featuredAlbums.isNotEmpty()) {
                item {
                    EditorialSectionHeader(
                        title = "Featured Albums",
                        actionLabel = "See all",
                        onAction = { onOpenCategory(LibraryCategory.ALBUMS) },
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(featuredAlbums, key = { "fa_${it.key}" }) { album ->
                            EditorialAlbumCard(
                                album = album,
                                onClick = { onOpenAlbum(album.key) },
                            )
                        }
                    }
                }
            }

            // 7. Recommended Playlists
            if (recommendedPlaylists.isNotEmpty()) {
                item {
                    EditorialSectionHeader(title = "Recommended Playlists")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recommendedPlaylists, key = { "pl_${it.id}" }) { playlist ->
                            Column(
                                modifier = Modifier
                                    .width(128.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenPlaylist(playlist.id) },
                            ) {
                                PlaylistCollageArtwork(
                                    customCoverUri = playlist.customCoverUri,
                                    artworkUris = playlist.artworkUris,
                                    modifier = Modifier.size(128.dp),
                                    cornerRadius = 12.dp,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${playlist.trackCount} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningHeroCard(
    track: Track,
    totalTracks: Int,
    isPlaying: Boolean,
    positionMs: Long,
    onCardClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPlayAll: () -> Unit,
) {
    EditorialGroupCard(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .clickable(onClick = onCardClick)
            .testTag("home_continue_listening_card"),
        cornerRadius = 24.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isPlaying) "NOW PLAYING" else "CONTINUE LISTENING",
                    style = MonoBadgeStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Play All ($totalTracks)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onPlayAll)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(
                    artworkUri = track.customArtworkUri ?: track.artworkUri,
                    fallbackUri = track.contentUri,
                    modifier = Modifier.size(64.dp),
                    cornerRadius = 10.dp,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${track.artist} · ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val dur = track.durationMs.coerceAtLeast(1L)
                    val progress = (positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (positionMs > 0L) {
                            "${formatTimecode(positionMs)} / ${formatTimecode(track.durationMs)}"
                        } else {
                            formatTimecode(track.durationMs)
                        },
                        style = MonoBadgeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBannerCard(
    tracks: List<Track>,
    albums: List<AlbumSummary>,
    isScanning: Boolean,
    onPlayAll: () -> Unit,
) {
    val coverUris = remember(tracks, albums) {
        val albumArts = albums.mapNotNull { it.artworkUri }.distinct()
        if (albumArts.isNotEmpty()) albumArts.take(4)
        else tracks.mapNotNull { it.displayArtwork }.distinct().take(4)
    }
    val shape = RoundedCornerShape(32.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Your Music.\nYour Way.",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 23.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${tracks.size} songs · Offline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onPlayAll,
                enabled = !isScanning,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.testTag("home_shuffle_all_card"),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play All", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        if (coverUris.isNotEmpty()) {
            PlaylistCollageArtwork(
                customCoverUri = null,
                artworkUris = coverUris,
                modifier = Modifier.size(104.dp),
                cornerRadius = 22.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickDjModeCard(
    isAutomixActive: Boolean,
    onOpenDjMode: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isAutomixActive) 1.dp else 0.75.dp,
                color = if (isAutomixActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onOpenDjMode)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("home_automix_card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isAutomixActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "DJ Mode",
                tint = if (isAutomixActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isAutomixActive) "DJ Mode · Automix Live" else "DJ Mode & Automix",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isAutomixActive) {
                    "Dual-deck beatmatching active · Tap for console"
                } else {
                    "Harmonic mixing, DJ profiles, BPM & key studio"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Open DJ Mode",
            tint = if (isAutomixActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibraryGuidance(
    hasAudioPermission: Boolean,
    isScanning: Boolean,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit,
    onImportFolder: () -> Unit,
    onImportAudioFile: () -> Unit,
) {
    // The library is filled automatically in the background. The only action a listener ever
    // needs here is allowing access to their music (an Android requirement).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 72.dp)
            .testTag("empty_library_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (hasAudioPermission && isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Text(
            text = when {
                !hasAudioPermission -> "Play the music on your phone"
                isScanning -> "Finding your music…"
                else -> "No music yet"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when {
                !hasAudioPermission -> "Allow access so Maxmize can find your songs. Everything stays on your device."
                isScanning -> "This only takes a moment."
                else -> "Songs on your phone will appear here automatically."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!hasAudioPermission) {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                modifier = Modifier.testTag("grant_audio_permission_btn"),
            ) {
                Text("Allow access", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EditorialSquareTrackCard(
    track: Track,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            artworkUri = track.customArtworkUri ?: track.artworkUri,
            fallbackUri = track.contentUri,
            modifier = Modifier.size(132.dp),
            cornerRadius = 12.dp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
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
}

@Composable
private fun EditorialAlbumCard(
    album: AlbumSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            artworkUri = album.artworkUri,
            modifier = Modifier.size(132.dp),
            cornerRadius = 12.dp,
            fallbackIcon = Icons.Rounded.Album,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.albumName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album.trackCount} songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

sealed interface TrackMenuEvent {
    data object PlayNext : TrackMenuEvent
    data object AddToQueue : TrackMenuEvent
    data object AddToPlaylist : TrackMenuEvent
    data object EditMetadata : TrackMenuEvent
    data object ViewLyrics : TrackMenuEvent
    data object GoToAlbum : TrackMenuEvent
    data object GoToArtist : TrackMenuEvent
    data object TrackInfo : TrackMenuEvent
}
