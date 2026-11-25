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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.components.TrackRow
import com.example.ui.components.formatDurationSummary
import com.example.ui.theme.BadgeAmberBg
import com.example.ui.theme.BadgeAmberFg
import com.example.ui.theme.BadgeBlueBg
import com.example.ui.theme.BadgeBlueFg
import com.example.ui.theme.BadgeIndigoBg
import com.example.ui.theme.BadgeIndigoFg
import com.example.ui.theme.BadgePinkBg
import com.example.ui.theme.BadgePinkFg
import com.example.ui.theme.BadgePurpleBg
import com.example.ui.theme.BadgePurpleFg
import com.example.ui.theme.BadgeRedBg
import com.example.ui.theme.BadgeRedFg
import com.example.ui.theme.BadgeTealBg
import com.example.ui.theme.BadgeTealFg

private data class LibraryDirEntry(
    val category: LibraryCategory,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeBg: Color,
    val badgeFg: Color,
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
    onOpenSearch: () -> Unit,
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
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var overflowMenuOpen by remember { mutableStateOf(false) }

    if (selectedCategory != null) {
        BackHandler {
            if (multiSelectMode) {
                multiSelectMode = false
                selectedIds = emptySet()
            } else {
                onSelectCategory(null)
            }
        }
    }

    val displayedTracks = remember(selectedCategory, sortedTracks, allTracks) {
        when (selectedCategory) {
            LibraryCategory.SONGS -> sortedTracks
            LibraryCategory.RECENTLY_ADDED -> allTracks.sortedByDescending { it.dateAddedSec }
            LibraryCategory.RECENTLY_PLAYED -> allTracks.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }
            LibraryCategory.MOST_PLAYED -> allTracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
            LibraryCategory.FAVORITES -> allTracks.filter { it.isFavorite }
            else -> sortedTracks
        }
    }

    val directoryEntries = remember(allTracks, artists, albums, genres, folders) {
        val favCount = allTracks.count { it.isFavorite }
        val mostPlayedCount = allTracks.count { it.playCount > 0 }
        val recentCount = allTracks.take(50).size
        listOf(
            LibraryDirEntry(LibraryCategory.SONGS, "All Songs", "${allTracks.size} songs", Icons.Rounded.MusicNote, BadgeRedBg, BadgeRedFg),
            LibraryDirEntry(LibraryCategory.ARTISTS, "Artists", "${artists.size} artists", Icons.Rounded.Person, BadgeBlueBg, BadgeBlueFg),
            LibraryDirEntry(LibraryCategory.ALBUMS, "Albums", "${albums.size} albums", Icons.Rounded.Album, BadgePurpleBg, BadgePurpleFg),
            LibraryDirEntry(LibraryCategory.GENRES, "Genres", "${genres.size} genres", Icons.Rounded.Category, BadgeAmberBg, BadgeAmberFg),
            LibraryDirEntry(LibraryCategory.FOLDERS, "Folders", "${folders.size} folders", Icons.Rounded.Folder, BadgeAmberBg, BadgeAmberFg),
            LibraryDirEntry(LibraryCategory.FAVORITES, "Favorites", "$favCount songs", Icons.Rounded.Favorite, BadgePinkBg, BadgePinkFg),
            LibraryDirEntry(LibraryCategory.RECENTLY_ADDED, "Recently Added", "$recentCount songs", Icons.Rounded.Schedule, BadgeIndigoBg, BadgeIndigoFg),
            LibraryDirEntry(LibraryCategory.MOST_PLAYED, "Most Played", "$mostPlayedCount songs", Icons.Rounded.BarChart, BadgeTealBg, BadgeTealFg),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("library_screen"),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (selectedCategory != null && !multiSelectMode) 8.dp else 20.dp,
                    end = 8.dp,
                    top = 14.dp,
                    bottom = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedCategory != null && !multiSelectMode) {
                    IconButton(onClick = { onSelectCategory(null) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Back to library categories",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Text(
                    text = when {
                        multiSelectMode -> "${selectedIds.size} Selected"
                        selectedCategory != null -> selectedCategory.label
                        else -> "Library"
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = if (selectedCategory == null) 26.sp else 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
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
                    if (selectedCategory == null) {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search library")
                        }
                    } else {
                        IconButton(onClick = { multiSelectMode = true }) {
                            Icon(Icons.Rounded.Checklist, contentDescription = "Select songs")
                        }
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.Rounded.Sort, contentDescription = "Sort options")
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
                    }

                    Box {
                        IconButton(onClick = { overflowMenuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Library options")
                        }
                        DropdownMenu(expanded = overflowMenuOpen, onDismissRequest = { overflowMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rescan Library") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { overflowMenuOpen = false; onRescan(false) },
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh Metadata") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { overflowMenuOpen = false; onRescan(true) },
                            )
                            DropdownMenuItem(
                                text = { Text("Import Folder") },
                                leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                                onClick = { overflowMenuOpen = false; onImportFolder() },
                            )
                        }
                    }
                }
            }
        }

        if (scanState.isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        if (selectedCategory == null) {
            val primaryEntries = directoryEntries.filter {
                it.category in setOf(
                    LibraryCategory.SONGS,
                    LibraryCategory.ALBUMS,
                    LibraryCategory.ARTISTS,
                    LibraryCategory.GENRES,
                    LibraryCategory.FOLDERS,
                )
            }
            val quickEntries = directoryEntries.filter {
                it.category in setOf(
                    LibraryCategory.FAVORITES,
                    LibraryCategory.RECENTLY_ADDED,
                    LibraryCategory.RECENTLY_PLAYED,
                    LibraryCategory.MOST_PLAYED,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "BROWSE YOUR MUSIC",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                    )
                }
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height(288.dp),
                        contentPadding = PaddingValues(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false,
                    ) {
                        items(primaryEntries, key = { it.category.name }) { entry ->
                            val shape = RoundedCornerShape(16.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(89.dp)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable { onSelectCategory(entry.category) }
                                    .padding(12.dp)
                                    .testTag("lib_dir_${entry.category.name.lowercase()}"),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(entry.badgeBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = entry.icon,
                                        contentDescription = null,
                                        tint = entry.badgeFg,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = entry.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "QUICK ACCESS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickEntries, key = { it.category.name }) { entry ->
                            EditorialChip(
                                label = entry.title,
                                selected = false,
                                onClick = { onSelectCategory(entry.category) },
                                modifier = Modifier.testTag("lib_quick_${entry.category.name.lowercase()}"),
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        } else {
            // Category Tabs
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LibraryCategory.entries, key = { it.name }) { category ->
                    EditorialChip(
                        label = category.label,
                        selected = selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        modifier = Modifier.testTag("lib_tab_${category.name.lowercase()}"),
                    )
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)

            when (selectedCategory) {
                LibraryCategory.SONGS,
                LibraryCategory.RECENTLY_ADDED,
                LibraryCategory.RECENTLY_PLAYED,
                LibraryCategory.MOST_PLAYED,
                LibraryCategory.FAVORITES -> {
                    if (displayedTracks.isEmpty()) {
                        EmptyCategoryState(
                            title = "Nothing in ${selectedCategory.label}",
                            subtitle = if (allTracks.isEmpty()) {
                                "Scan your device or import a music folder to start listening."
                            } else {
                                "Tracks matching ${selectedCategory.label.lowercase()} will appear here as you listen."
                            },
                            onAction = if (allTracks.isEmpty()) ({ onRescan(false) }) else null,
                            actionLabel = if (allTracks.isEmpty()) "Scan Device" else null,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${displayedTracks.size} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    OutlinedButton(
                                        onClick = { onPlayTracks(displayedTracks, 0, true) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    ) {
                                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Shuffle")
                                    }
                                }
                            }
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
                    if (albums.isEmpty()) {
                        EmptyCategoryState("No Albums", "Scan your local music library to browse albums.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 152.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(albums, key = { it.key }) { album ->
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
                    }
                }

                LibraryCategory.ARTISTS -> {
                    if (artists.isEmpty()) {
                        EmptyCategoryState("No Artists", "Scan your local music library to browse artists.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(artists, key = { it.artistName }) { artist ->
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
                                        Text(
                                            text = artist.artistName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${artist.albumCount} albums · ${artist.trackCount} songs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                LibraryCategory.GENRES -> {
                    if (genres.isEmpty()) {
                        EmptyCategoryState("No Genres", "Genre metadata will appear after scanning tracks.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(genres, key = { it.genreName }) { genre ->
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
                                    Icon(
                                        imageVector = Icons.Rounded.Category,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(genre.genreName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${genre.trackCount} songs · ${formatDurationSummary(genre.totalDurationMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                LibraryCategory.FOLDERS -> {
                    if (folders.isEmpty()) {
                        EmptyCategoryState("No Folders", "Scan device storage or import a folder to browse directories.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(folders, key = { it.folderPath }) { folder ->
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
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp),
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.folderName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${folder.trackCount} songs · ${folder.folderPath}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = { onExcludeFolder(folder.folderPath) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderOff,
                                            contentDescription = "Exclude folder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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
