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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.components.TrackRow
import com.example.ui.components.formatTimecode
import com.example.ui.theme.BadgeBlueBg
import com.example.ui.theme.LavenderCard
import com.example.ui.theme.LavenderCardDeep
import com.example.ui.theme.LavenderCardText
import com.example.ui.theme.BadgeBlueFg
import com.example.ui.theme.BadgeGoldBg
import com.example.ui.theme.BadgeGoldFg
import com.example.ui.theme.BadgePurpleBg
import com.example.ui.theme.BadgePurpleFg
import com.example.ui.theme.BadgeRedBg
import com.example.ui.theme.BadgeRedFg
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle

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
    onOpenSearch: () -> Unit,
    onOpenAutomix: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenCategory: (LibraryCategory) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMenuAction: (Track, TrackMenuEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflowOpen by remember { mutableStateOf(false) }

    val recentlyPlayed = remember(tracks) {
        tracks.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }.take(12)
    }
    val recentlyAdded = remember(tracks) {
        tracks.sortedByDescending { it.dateAddedSec }.take(12)
    }
    val mostPlayed = remember(tracks) {
        tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(6)
    }
    val favoriteTracks = remember(tracks) {
        tracks.filter { it.isFavorite }.take(10)
    }
    val featuredAlbums = remember(albums) {
        val recent = albums.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }
        if (recent.isNotEmpty()) recent.take(10) else albums.take(10)
    }
    val recentPlaylists = remember(playlists) {
        playlists.sortedByDescending { it.createdAt }.take(8)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        // Editorial Two-Tone Brand Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    val greeting = remember {
                        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                            in 5..11 -> "Good morning"
                            in 12..16 -> "Good afternoon"
                            else -> "Good evening"
                        }
                    }
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .testTag("home_search_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search library",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_settings_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { overflowOpen = true },
                            modifier = Modifier
                                .padding(start = 8.dp, end = 12.dp)
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .testTag("home_rescan_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Library options",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rescan Library") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { overflowOpen = false; onRescan(false) },
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh Metadata") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { overflowOpen = false; onRescan(true) },
                            )
                            DropdownMenuItem(
                                text = { Text("Import Folder") },
                                leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                                onClick = { overflowOpen = false; onImportFolder() },
                            )
                            DropdownMenuItem(
                                text = { Text("Add Audio File") },
                                leadingIcon = { Icon(Icons.Rounded.AudioFile, contentDescription = null) },
                                onClick = { overflowOpen = false; onImportAudioFile() },
                            )
                        }
                    }
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
                        text = scanState.statusMessage ?: "Scanning device audio...",
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
                modifier = Modifier.padding(bottom = 14.dp),
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

        // Hero Editorial Card (Solid dark surface + crisp border, zero gradients)
        item {
            HeroBannerCard(
                tracks = tracks,
                albums = albums,
                isScanning = scanState.isScanning,
                hasAudioPermission = hasAudioPermission,
                onPlayAll = {
                    if (tracks.isNotEmpty()) {
                        onPlayTracks(tracks, 0, false)
                    } else if (!hasAudioPermission) {
                        onRequestPermission()
                    } else {
                        onRescan(false)
                    }
                },
            )
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
            // Recently Played Carousel
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

            // Quick Access 4-tile grid
            item {
                EditorialSectionHeader(title = "Quick Access")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickAccessTile(
                        label = "All Songs",
                        icon = Icons.Rounded.MusicNote,
                        badgeBg = BadgeRedBg,
                        badgeFg = BadgeRedFg,
                        onClick = { onOpenCategory(LibraryCategory.SONGS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_all_songs_card"),
                    )
                    QuickAccessTile(
                        label = "Artists",
                        icon = Icons.Rounded.Person,
                        badgeBg = BadgeBlueBg,
                        badgeFg = BadgeBlueFg,
                        onClick = { onOpenCategory(LibraryCategory.ARTISTS) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAccessTile(
                        label = "Albums",
                        icon = Icons.Rounded.Album,
                        badgeBg = BadgePurpleBg,
                        badgeFg = BadgePurpleFg,
                        onClick = { onOpenCategory(LibraryCategory.ALBUMS) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAccessTile(
                        label = if (isAutomixActive) "Mixing" else "Automix",
                        icon = Icons.Rounded.GraphicEq,
                        badgeBg = BadgeGoldBg,
                        badgeFg = BadgeGoldFg,
                        onClick = onOpenAutomix,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_automix_card"),
                    )
                }
            }

            // Continue Listening
            val continueTrack = playbackState.currentTrack ?: recentlyPlayed.firstOrNull()
            if (continueTrack != null) {
                item {
                    EditorialSectionHeader(title = "Continue Listening")
                    ContinueListeningCard(
                        track = continueTrack,
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
                    )
                }
            }

            // Featured Albums
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

            // Recently Added
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

            // Recently Created Playlists
            if (recentPlaylists.isNotEmpty()) {
                item {
                    EditorialSectionHeader(title = "Your Playlists")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recentPlaylists, key = { "pl_${it.id}" }) { playlist ->
                            Column(
                                modifier = Modifier
                                    .width(126.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenPlaylist(playlist.id) },
                            ) {
                                PlaylistCollageArtwork(
                                    customCoverUri = playlist.customCoverUri,
                                    artworkUris = playlist.artworkUris,
                                    modifier = Modifier.size(126.dp),
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

            // Favorite Tracks
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

            // Most Played
            if (mostPlayed.isNotEmpty()) {
                item {
                    EditorialSectionHeader(
                        title = "Most Played",
                        actionLabel = "See all",
                        onAction = { onOpenCategory(LibraryCategory.MOST_PLAYED) },
                    )
                }
                itemsIndexed(mostPlayed, key = { _, t -> "mp_${t.id}" }) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MonoTimecodeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(22.dp),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            TrackRow(
                                track = track,
                                isCurrent = playbackState.currentTrack?.id == track.id,
                                isPlaying = playbackState.isPlaying,
                                onClick = { onPlayTracks(mostPlayed, index, false) },
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
            }
        }
    }
}

@Composable
private fun HeroBannerCard(
    tracks: List<Track>,
    albums: List<AlbumSummary>,
    isScanning: Boolean,
    hasAudioPermission: Boolean,
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
            .background(LavenderCard)
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
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = LavenderCardText,
            )
            Text(
                text = if (tracks.isEmpty()) {
                    "Offline · Fast · Powerful"
                } else {
                    "Offline · ${tracks.size} Tracks · Lossless"
                },
                style = MaterialTheme.typography.bodySmall,
                color = LavenderCardText.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onPlayAll,
                enabled = !isScanning,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LavenderCardDeep,
                    contentColor = Color.White,
                ),
                modifier = Modifier.testTag("home_shuffle_all_card"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        tracks.isNotEmpty() -> "Play All"
                        !hasAudioPermission -> "Grant Access"
                        else -> "Scan Music"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
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
                    .background(LavenderCardDeep.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = LavenderCardDeep,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickAccessTile(
    label: String,
    icon: ImageVector,
    badgeBg: Color,
    badgeFg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = badgeFg,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
    EditorialGroupCard(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("empty_library_card"),
        cornerRadius = 16.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (hasAudioPermission) "No local audio files found" else "Access your offline music",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = if (hasAudioPermission) {
                    "Add MP3, FLAC, WAV, AAC, M4A, OGG, or OPUS files to your device, rescan storage, or import a specific music folder."
                } else {
                    "Maxmize scans and plays audio stored directly on your device with zero internet connection or account required."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (!hasAudioPermission) {
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_audio_permission_btn"),
                ) {
                    Text("Grant Audio Access")
                }
            } else {
                Button(
                    onClick = onRescan,
                    enabled = !isScanning,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_rescan_btn"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanning) "Scanning..." else "Rescan Device")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onImportFolder,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_folder_btn"),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onImportAudioFile,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_file_btn"),
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add File", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningCard(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    onCardClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
) {
    EditorialGroupCard(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clickable(onClick = onCardClick),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(
                artworkUri = track.customArtworkUri ?: track.artworkUri,
                fallbackUri = track.contentUri,
                modifier = Modifier.size(60.dp),
                cornerRadius = 10.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
                Spacer(modifier = Modifier.height(6.dp))
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
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
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

@Composable
private fun EditorialSquareTrackCard(
    track: Track,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            artworkUri = track.customArtworkUri ?: track.artworkUri,
            fallbackUri = track.contentUri,
            modifier = Modifier.size(124.dp),
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
            .width(124.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            artworkUri = album.artworkUri,
            modifier = Modifier.size(124.dp),
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
