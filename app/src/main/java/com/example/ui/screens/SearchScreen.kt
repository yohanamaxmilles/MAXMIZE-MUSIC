package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArtistSummary
import com.example.data.model.PlaybackUiState
import com.example.data.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EditorialChip
import com.example.ui.components.MaxmizeTopHeader
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.SearchResults

private enum class SearchScope(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    GENRES("Genres"),
    PLAYLISTS("Playlists"),
    FOLDERS("Folders"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query: String,
    results: SearchResults,
    searchHistory: List<String>,
    suggestedArtists: List<ArtistSummary>,
    playbackState: PlaybackUiState,
    onQueryChange: (String) -> Unit,
    onCommitQuery: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlayTracks: (List<Track>, Int, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onTrackMenuAction: (Track, TrackMenuEvent) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var scope by remember { mutableStateOf(SearchScope.ALL) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("search_screen"),
    ) {
        MaxmizeTopHeader(
            title = "Search",
            subtitle = "SONGS · ARTISTS · ALBUMS · FOLDERS",
            onOpenSettings = onOpenSettings,
        )

        // Custom Editorial Search Input Bar
        val barShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(48.dp)
                .clip(barShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, barShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Songs, artists, albums, genres, folders...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onCommitQuery(query) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input_field"),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (query.isBlank()) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            TextButton(
                                onClick = onClearHistory,
                                modifier = Modifier.testTag("clear_search_history_btn"),
                            ) {
                                Text("Clear")
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            searchHistory.forEach { term ->
                                val chipShape = RoundedCornerShape(10.dp)
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(chipShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, chipShape)
                                        .clickable { onQueryChange(term) }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = term,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove $term",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onDeleteHistoryItem(term) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (suggestedArtists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Artists in Library",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(suggestedArtists.take(8), key = { it.artistName }) { artist ->
                        SearchEntityRow(
                            icon = Icons.Rounded.Person,
                            title = artist.artistName,
                            subtitle = "${artist.trackCount} songs in library",
                            onClick = {
                                onQueryChange(artist.artistName)
                                onCommitQuery(artist.artistName)
                            },
                        )
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SearchScope.entries, key = { it.name }) { itemScope ->
                    EditorialChip(
                        label = itemScope.label,
                        selected = scope == itemScope,
                        onClick = { scope = itemScope },
                    )
                }
            }

            if (results.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No Results for \"$query\"", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Try another song title, artist, album, genre, or folder name.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if ((scope == SearchScope.ALL || scope == SearchScope.SONGS) && results.songs.isNotEmpty()) {
                        item { SearchSectionTitle("Songs (${results.songs.size})") }
                        itemsIndexed(results.songs, key = { _, t -> "s_${t.id}" }) { idx, track ->
                            TrackRow(
                                track = track,
                                isCurrent = playbackState.currentTrack?.id == track.id,
                                isPlaying = playbackState.isPlaying,
                                onClick = {
                                    onCommitQuery(query)
                                    onPlayTracks(results.songs, idx, false)
                                },
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
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }

                    if ((scope == SearchScope.ALL || scope == SearchScope.ALBUMS) && results.albums.isNotEmpty()) {
                        item { SearchSectionTitle("Albums (${results.albums.size})") }
                        items(results.albums, key = { "al_${it.key}" }) { album ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 3.dp)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable {
                                        onCommitQuery(query)
                                        onOpenAlbum(album.key)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ArtworkImage(
                                    artworkUri = album.artworkUri,
                                    modifier = Modifier.size(46.dp),
                                    cornerRadius = 8.dp,
                                    fallbackIcon = Icons.Rounded.Album,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
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

                    if ((scope == SearchScope.ALL || scope == SearchScope.ARTISTS) && results.artists.isNotEmpty()) {
                        item { SearchSectionTitle("Artists (${results.artists.size})") }
                        items(results.artists, key = { "ar_${it.artistName}" }) { artist ->
                            SearchEntityRow(
                                icon = Icons.Rounded.Person,
                                title = artist.artistName,
                                subtitle = "${artist.albumCount} albums · ${artist.trackCount} songs",
                                onClick = {
                                    onCommitQuery(query)
                                    onOpenArtist(artist.artistName)
                                },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                            )
                        }
                    }

                    if ((scope == SearchScope.ALL || scope == SearchScope.GENRES) && results.genres.isNotEmpty()) {
                        item { SearchSectionTitle("Genres (${results.genres.size})") }
                        items(results.genres, key = { "ge_${it.genreName}" }) { genre ->
                            SearchEntityRow(
                                icon = Icons.Rounded.Category,
                                title = genre.genreName,
                                subtitle = "${genre.trackCount} songs",
                                onClick = {
                                    onCommitQuery(query)
                                    onOpenGenre(genre.genreName)
                                },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                            )
                        }
                    }

                    if ((scope == SearchScope.ALL || scope == SearchScope.PLAYLISTS) && results.playlists.isNotEmpty()) {
                        item { SearchSectionTitle("Playlists (${results.playlists.size})") }
                        items(results.playlists, key = { "pl_${it.id}" }) { playlist ->
                            SearchEntityRow(
                                icon = Icons.Rounded.PlaylistPlay,
                                title = playlist.name,
                                subtitle = "${playlist.trackCount} songs",
                                onClick = {
                                    onCommitQuery(query)
                                    onOpenPlaylist(playlist.id)
                                },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                            )
                        }
                    }

                    if ((scope == SearchScope.ALL || scope == SearchScope.FOLDERS) && results.folders.isNotEmpty()) {
                        item { SearchSectionTitle("Folders (${results.folders.size})") }
                        items(results.folders, key = { "fo_${it.folderPath}" }) { folder ->
                            SearchEntityRow(
                                icon = Icons.Rounded.Folder,
                                title = folder.folderName,
                                subtitle = "${folder.trackCount} songs · ${folder.folderPath}",
                                onClick = {
                                    onCommitQuery(query)
                                    onOpenFolder(folder.folderPath)
                                },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEntityRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
