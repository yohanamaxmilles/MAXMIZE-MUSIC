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
import androidx.compose.ui.graphics.Brush
import com.example.ui.components.MaxmizeIcons
import com.example.ui.components.TopProfileSettingsButton
import com.example.ui.components.TrackRow
import com.example.ui.components.formatTimecode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LavenderCard
import com.example.ui.theme.LavenderCardDeep
import com.example.ui.theme.LavenderCardText
import com.example.ui.theme.MonoBadgeStyle
import kotlinx.coroutines.flow.StateFlow
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
    onOpenSearch: () -> Unit = {},
    onOpenCategory: (LibraryCategory) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMenuAction: (Track, TrackMenuEvent) -> Unit,
    modifier: Modifier = Modifier,
    positionMsFlow: StateFlow<Long>? = null,
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
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MaxmizeIcons.Pulse,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Text(
                            text = "Maxmize",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(onClick = onOpenSearch)
                                .testTag("home_search_btn"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MaxmizeIcons.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        TopProfileSettingsButton(onClick = onOpenSettings)
                    }
                }
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp),
                )
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
                modifier = Modifier.padding(bottom = 6.dp),
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
                EditorialSectionHeader(title = "Curated & trending")
                HeroBannerCard(
                    tracks = tracks,
                    albums = albums,
                    isScanning = scanState.isScanning,
                    onPlayAll = { onPlayTracks(tracks, 0, false) },
                    onShuffleAll = { onPlayTracks(tracks, 0, true) },
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
                        isCurrentTrack = playbackState.currentTrack?.id == continueTrack.id,
                        fallbackPositionMs = if (playbackState.currentTrack?.id == continueTrack.id) playbackState.positionMs else 0L,
                        positionMsFlow = positionMsFlow,
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
                            onAutoFixMetadata = { onTrackMenuAction(track, TrackMenuEvent.AutoFixMetadata) },
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

            // 7. Playlists (reference-style rows with round play buttons)
            if (recommendedPlaylists.isNotEmpty()) {
                item {
                    EditorialSectionHeader(title = "Your playlists")
                }
                items(recommendedPlaylists, key = { "pl_${it.id}" }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(playlist.id) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistCollageArtwork(
                            customCoverUri = playlist.customCoverUri,
                            artworkUris = playlist.artworkUris,
                            modifier = Modifier.size(68.dp),
                            cornerRadius = 20.dp,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${playlist.trackCount} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MaxmizeIcons.Play,
                                contentDescription = "Open playlist",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
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
    isCurrentTrack: Boolean,
    fallbackPositionMs: Long,
    positionMsFlow: StateFlow<Long>?,
    onCardClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPlayAll: () -> Unit,
) {
    EditorialGroupCard(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .clickable(onClick = onCardClick)
            .testTag("home_continue_listening_card"),
        cornerRadius = 28.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isPlaying) "Now playing" else "Continue listening",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Play all ($totalTracks)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onPlayAll)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(
                    artworkUri = track.customArtworkUri ?: track.artworkUri,
                    fallbackUri = track.contentUri,
                    modifier = Modifier.size(68.dp),
                    cornerRadius = 20.dp,
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val livePos = if (isCurrentTrack && positionMsFlow != null) {
                        positionMsFlow.collectAsStateWithLifecycle().value
                    } else {
                        fallbackPositionMs
                    }
                    val dur = track.durationMs.coerceAtLeast(1L)
                    val progress = (livePos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) MaxmizeIcons.Pause else MaxmizeIcons.Play,
                        contentDescription = "Play or pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
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
    onShuffleAll: () -> Unit,
) {
    val coverUris = remember(tracks, albums) {
        val albumArts = albums.mapNotNull { it.artworkUri }.distinct()
        if (albumArts.isNotEmpty()) albumArts.take(4)
        else tracks.mapNotNull { it.displayArtwork }.distinct().take(4)
    }
    val shape = RoundedCornerShape(34.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(shape)
            .background(LavenderCard)
            .padding(start = 22.dp, top = 22.dp, bottom = 22.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Your library",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = LavenderCardText,
            )
            Text(
                text = "${tracks.size} songs, ready offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = LavenderCardText.copy(alpha = 0.72f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(LavenderCardDeep)
                        .clickable(enabled = !isScanning, onClick = onPlayAll)
                        .testTag("home_shuffle_all_card"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MaxmizeIcons.Play,
                        contentDescription = "Play all",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LavenderCardDeep.copy(alpha = 0.12f))
                        .clickable(enabled = !isScanning, onClick = onShuffleAll),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MaxmizeIcons.Shuffle,
                        contentDescription = "Shuffle all",
                        tint = LavenderCardDeep,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (coverUris.isNotEmpty()) {
            PlaylistCollageArtwork(
                customCoverUri = null,
                artworkUris = coverUris,
                modifier = Modifier.size(118.dp),
                cornerRadius = 28.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(LavenderCardDeep.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MaxmizeIcons.Disc,
                    contentDescription = null,
                    tint = LavenderCardDeep,
                    modifier = Modifier.size(44.dp),
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (isAutomixActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
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
                .background(if (isAutomixActive) MaterialTheme.colorScheme.primary else LavenderCardDeep),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MaxmizeIcons.Pulse,
                contentDescription = "DJ Mode",
                tint = if (isAutomixActive) MaterialTheme.colorScheme.onPrimary else LavenderCard,
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
            imageVector = MaxmizeIcons.ChevronRight,
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
                    imageVector = MaxmizeIcons.Disc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
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
            .width(148.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            artworkUri = track.customArtworkUri ?: track.artworkUri,
            fallbackUri = track.contentUri,
            modifier = Modifier.size(148.dp),
            cornerRadius = 22.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
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
            .width(148.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            artworkUri = album.artworkUri,
            modifier = Modifier.size(148.dp),
            cornerRadius = 22.dp,
            fallbackIcon = MaxmizeIcons.Disc,
        )
        Spacer(modifier = Modifier.height(8.dp))
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
    data object AutoFixMetadata : TrackMenuEvent
    data object ViewLyrics : TrackMenuEvent
    data object GoToAlbum : TrackMenuEvent
    data object GoToArtist : TrackMenuEvent
    data object TrackInfo : TrackMenuEvent
}
