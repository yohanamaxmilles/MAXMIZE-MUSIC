package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.model.AlbumSummary
import com.example.data.model.ArtistSummary
import com.example.data.model.FolderSummary
import com.example.data.model.GenreSummary
import com.example.data.model.LibraryCategory
import com.example.data.model.PlaybackUiState
import com.example.data.model.ScanState
import com.example.data.model.SortOption
import com.example.data.model.SortOrder
import com.example.data.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EditorialChip
import com.example.ui.components.MaxmizeTopHeader
import com.example.ui.components.TrackRow
import com.example.ui.components.formatDurationSummary
import com.example.ui.theme.MonoBadgeStyle

private val PRIMARY_LIBRARY_TABS = listOf(
    LibraryCategory.SONGS,
    LibraryCategory.ALBUMS,
    LibraryCategory.ARTISTS,
    LibraryCategory.GENRES,
    LibraryCategory.FOLDERS,
)

@Composable
fun LibraryScreen(
    selectedCategory: LibraryCategory?,
    onSelectCategory: (LibraryCategory?) -> Unit,
    sortedTracks: List<Track>,
    allTracks: List<Track>,
    albums: List<AlbumSummary>,
    artists: List<ArtistSummary>,
    genres: List<GenreSummary>,
    folders: List<FolderSummary>,
    sortOption: SortOption,
    sortOrder: SortOrder,
    onChangeSort: (SortOption, SortOrder) -> Unit,
    scanState: ScanState,
    playbackState: PlaybackUiState,
    onRescan: (Boolean) -> Unit,
    onImportFolder: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onPlayTracks: (List<Track>, Int, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onExcludeFolder: (String) -> Unit,
    onCreatePlaylistFromSelected: (List<String>) -> Unit,
    onTrackMenuAction: (Track, TrackMenuEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeCategory = selectedCategory ?: LibraryCategory.SONGS
    val isTrackListCategory = activeCategory == LibraryCategory.SONGS ||
        activeCategory == LibraryCategory.RECENTLY_ADDED ||
        activeCategory == LibraryCategory.RECENTLY_PLAYED ||
        activeCategory == LibraryCategory.MOST_PLAYED ||
        activeCategory == LibraryCategory.FAVORITES

    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var overflowMenuOpen by remember { mutableStateOf(false) }
    var useGridLayout by remember { mutableStateOf(true) }

    if (multiSelectMode || (selectedCategory != null && selectedCategory != LibraryCategory.SONGS)) {
        BackHandler {
            if (multiSelectMode) {
                multiSelectMode = false
                selectedIds = emptySet()
            } else {
                onSelectCategory(LibraryCategory.SONGS)
            }
        }
    }

    val displayedTracks = remember(activeCategory, sortedTracks, allTracks) {
        when (activeCategory) {
            LibraryCategory.SONGS -> sortedTracks
            LibraryCategory.RECENTLY_ADDED -> allTracks.sortedByDescending { it.dateAddedSec }
            LibraryCategory.RECENTLY_PLAYED -> allTracks.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }
            LibraryCategory.MOST_PLAYED -> allTracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
            LibraryCategory.FAVORITES -> allTracks.filter { it.isFavorite }
            else -> sortedTracks
        }
    }

    val sortedAlbums = remember(albums, sortOrder) {
        if (sortOrder == SortOrder.ASCENDING) albums.sortedBy { it.albumName.lowercase() }
        else albums.sortedByDescending { it.albumName.lowercase() }
    }
    val sortedArtists = remember(artists, sortOrder) {
        if (sortOrder == SortOrder.ASCENDING) artists.sortedBy { it.artistName.lowercase() }
        else artists.sortedByDescending { it.artistName.lowercase() }
    }
    val sortedGenres = remember(genres, sortOrder) {
        if (sortOrder == SortOrder.ASCENDING) genres.sortedBy { it.genreName.lowercase() }
        else genres.sortedByDescending { it.genreName.lowercase() }
    }
    val sortedFolders = remember(folders, sortOrder) {
        if (sortOrder == SortOrder.ASCENDING) folders.sortedBy { it.folderName.lowercase() }
        else folders.sortedByDescending { it.folderName.lowercase() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("library_screen"),
    ) {
        MaxmizeTopHeader(
            title = if (multiSelectMode) "${selectedIds.size} Selected" else "Library",
            subtitle = when (activeCategory) {
                LibraryCategory.SONGS -> "${displayedTracks.size} SONGS"
                LibraryCategory.ALBUMS -> "${albums.size} ALBUMS"
                LibraryCategory.ARTISTS -> "${artists.size} ARTISTS"
                LibraryCategory.GENRES -> "${genres.size} GENRES"
                LibraryCategory.FOLDERS -> "${folders.size} FOLDERS"
                else -> "${activeCategory.label} · ${displayedTracks.size} songs"
            },
            onOpenSettings = onOpenSettings,
            actions = {
                if (multiSelectMode) {
                    TextButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onCreatePlaylistFromSelected(selectedIds.toList())
                                multiSelectMode = false
                                selectedIds = emptySet()
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Playlist")
                    }
                    IconButton(onClick = {
                        multiSelectMode = false
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Exit selection")
                    }
                } else {
                    if (isTrackListCategory) {
                        IconButton(onClick = { multiSelectMode = true }) {
                            Icon(Icons.Rounded.Checklist, contentDescription = "Select songs")
                        }
                    }
                }
            },
        )

        if (scanState.isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        // Direct top-level segment tabs: Songs | Albums | Artists | Genres | Folders
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(PRIMARY_LIBRARY_TABS, key = { it.name }) { category ->
                EditorialChip(
                    label = category.label,
                    selected = activeCategory == category,
                    onClick = { onSelectCategory(category) },
                    modifier = Modifier.testTag("lib_tab_${category.name.lowercase()}"),
                )
            }
        }

        // Active sub-filter badge when navigated from Home "See all" (Recently Added / Played / Favorites)
        if (activeCategory !in PRIMARY_LIBRARY_TABS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(0.75.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .clickable { onSelectCategory(LibraryCategory.SONGS) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Filter: ${activeCategory.label}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear filter",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Inline Sort & Layout Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box {
                val chipShape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(chipShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, chipShape)
                        .clickable { sortMenuOpen = true }
                        .padding(horizontal = 10.dp)
                        .testTag("lib_sort_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = "Sort library",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (isTrackListCategory) sortOption.label else "Name",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = if (sortOrder == SortOrder.ASCENDING) {
                            Icons.Rounded.ArrowUpward
                        } else Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    SortOption.entries.forEach { option ->
                        val isCurrent = option == sortOption
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            trailingIcon = if (isCurrent) {
                                {
                                    Icon(
                                        imageVector = if (sortOrder == SortOrder.ASCENDING) {
                                            Icons.Rounded.ArrowUpward
                                        } else Icons.Rounded.ArrowDownward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else null,
                            onClick = {
                                val nextOrder = if (isCurrent) {
                                    if (sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
                                } else SortOrder.ASCENDING
                                onChangeSort(option, nextOrder)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isTrackListCategory && displayedTracks.isNotEmpty()) {
                    val shuffleShape = RoundedCornerShape(9.dp)
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(shuffleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shuffleShape)
                            .clickable { onPlayTracks(displayedTracks, 0, true) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle songs",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Shuffle",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (activeCategory == LibraryCategory.ALBUMS ||
                    activeCategory == LibraryCategory.ARTISTS ||
                    activeCategory == LibraryCategory.GENRES ||
                    activeCategory == LibraryCategory.FOLDERS
                ) {
                    val toggleShape = RoundedCornerShape(9.dp)
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(toggleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, toggleShape)
                            .clickable { useGridLayout = !useGridLayout }
                            .padding(horizontal = 10.dp)
                            .testTag("lib_view_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (useGridLayout) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = if (useGridLayout) "Switch to list view" else "Switch to grid view",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (useGridLayout) "Grid" else "List",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)

        when (activeCategory) {
            LibraryCategory.SONGS,
            LibraryCategory.RECENTLY_ADDED,
            LibraryCategory.RECENTLY_PLAYED,
            LibraryCategory.MOST_PLAYED,
            LibraryCategory.FAVORITES -> {
                if (displayedTracks.isEmpty()) {
                    EmptyCategoryState(
                        title = "Nothing in ${activeCategory.label}",
                        subtitle = if (allTracks.isEmpty()) {
                            "Music on your phone appears here automatically."
                        } else {
                            "Tracks matching ${activeCategory.label.lowercase()} will appear here as you listen."
                        },
                        onAction = null,
                        actionLabel = null,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        itemsIndexed(displayedTracks, key = { _, t -> t.id }) { index, track ->
                            val isSelected = track.id in selectedIds
                            TrackRow(
                                track = track,
                                isCurrent = playbackState.currentTrack?.id == track.id,
                                isPlaying = playbackState.isPlaying,
                                isMultiSelectMode = multiSelectMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (multiSelectMode) {
                                        selectedIds = if (isSelected) selectedIds - track.id else selectedIds + track.id
                                    } else {
                                        onPlayTracks(displayedTracks, index, false)
                                    }
                                },
                                onLongClick = {
                                    multiSelectMode = true
                                    selectedIds = selectedIds + track.id
                                },
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

            LibraryCategory.ALBUMS -> {
                if (sortedAlbums.isEmpty()) {
                    EmptyCategoryState("No Albums", "Albums from your music will appear here.")
                } else if (useGridLayout) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(sortedAlbums, key = { it.key }) { album ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenAlbum(album.key) },
                            ) {
                                ArtworkImage(
                                    artworkUri = album.artworkUri,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(152.dp),
                                    cornerRadius = 12.dp,
                                    fallbackIcon = Icons.Rounded.Album,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = album.albumName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${album.artistName} · ${album.trackCount} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sortedAlbums, key = { it.key }) { album ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable { onOpenAlbum(album.key) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ArtworkImage(
                                    artworkUri = album.artworkUri,
                                    modifier = Modifier.size(50.dp),
                                    cornerRadius = 8.dp,
                                    fallbackIcon = Icons.Rounded.Album,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(album.albumName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${album.artistName} · ${album.trackCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            LibraryCategory.ARTISTS -> {
                if (sortedArtists.isEmpty()) {
                    EmptyCategoryState("No Artists", "Artists from your music will appear here.")
                } else if (useGridLayout) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 144.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(sortedArtists, key = { it.artistName }) { artist ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenArtist(artist.artistName) }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ArtworkImage(
                                    artworkUri = artist.artworkUri,
                                    modifier = Modifier.size(116.dp),
                                    cornerRadius = 58.dp,
                                    fallbackIcon = Icons.Rounded.Person,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(artist.artistName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${artist.trackCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sortedArtists, key = { it.artistName }) { artist ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable { onOpenArtist(artist.artistName) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ArtworkImage(
                                    artworkUri = artist.artworkUri,
                                    modifier = Modifier.size(50.dp),
                                    cornerRadius = 25.dp,
                                    fallbackIcon = Icons.Rounded.Person,
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(artist.artistName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${artist.albumCount} albums · ${artist.trackCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            LibraryCategory.GENRES -> {
                if (sortedGenres.isEmpty()) {
                    EmptyCategoryState("No Genres", "Genres from your music will appear here.")
                } else if (useGridLayout) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sortedGenres, key = { it.genreName }) { genre ->
                            val shape = RoundedCornerShape(12.dp)
                            Column(
                                modifier = Modifier
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable { onOpenGenre(genre.genreName) }
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(genre.genreName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${genre.trackCount} songs", style = MonoBadgeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(sortedGenres, key = { it.genreName }) { genre ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable { onOpenGenre(genre.genreName) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(genre.genreName, style = MaterialTheme.typography.titleMedium)
                                    Text("${genre.trackCount} songs · ${formatDurationSummary(genre.totalDurationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            LibraryCategory.FOLDERS -> {
                if (sortedFolders.isEmpty()) {
                    EmptyCategoryState("No Folders", "Folders with music will appear here.")
                } else if (useGridLayout) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sortedFolders, key = { it.folderPath }) { folder ->
                            val shape = RoundedCornerShape(12.dp)
                            Column(
                                modifier = Modifier
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable { onOpenFolder(folder.folderPath) }
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    IconButton(
                                        onClick = { onExcludeFolder(folder.folderPath) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderOff,
                                            contentDescription = "Exclude folder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Text(folder.folderName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${folder.trackCount} songs", style = MonoBadgeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sortedFolders, key = { it.folderPath }) { folder ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable { onOpenFolder(folder.folderPath) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(folder.folderName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${folder.trackCount} songs · ${folder.folderPath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onExcludeFolder(folder.folderPath) }) {
                                    Icon(Icons.Rounded.FolderOff, contentDescription = "Exclude folder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCategoryState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
