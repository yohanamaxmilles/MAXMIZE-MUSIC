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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Playlist
import com.example.data.model.SmartPlaylistType
import com.example.ui.components.MaxmizeTopHeader
import com.example.ui.components.PlaylistCollageArtwork
import com.example.ui.components.formatDurationSummary
import com.example.ui.theme.LavenderCard
import com.example.ui.theme.LavenderCardDeep
import com.example.ui.theme.MonoBadgeStyle

private val SECONDARY_SMART_PLAYLISTS = SmartPlaylistType.entries.filter {
    it != SmartPlaylistType.FAVORITES && it != SmartPlaylistType.RECENTLY_PLAYED
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    smartPlaylistCounts: Map<SmartPlaylistType, Int> = emptyMap(),
    onCreatePlaylist: (name: String, description: String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmartPlaylist: (SmartPlaylistType) -> Unit = {},
    onPlayPlaylist: (Long, Boolean) -> Unit,
    onRenamePlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("playlists_screen"),
    ) {
        MaxmizeTopHeader(
            title = "Playlists",
            subtitle = "${playlists.size} CUSTOM · SMART MIXES",
            onOpenSettings = onOpenSettings,
            actions = {
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_playlist_btn"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create playlist")
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("playlists_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Pinned Top Collections: Favorites & Recently Played (no duplication below)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PinnedSmartTile(
                        title = "Favorites",
                        count = smartPlaylistCounts[SmartPlaylistType.FAVORITES] ?: 0,
                        icon = Icons.Rounded.Favorite,
                        onClick = { onOpenSmartPlaylist(SmartPlaylistType.FAVORITES) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("smart_playlist_favorites"),
                    )
                    PinnedSmartTile(
                        title = "Recently Played",
                        count = smartPlaylistCounts[SmartPlaylistType.RECENTLY_PLAYED] ?: 0,
                        icon = Icons.Rounded.History,
                        onClick = { onOpenSmartPlaylist(SmartPlaylistType.RECENTLY_PLAYED) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("smart_playlist_recently_played"),
                    )
                }
            }

            // 2. Smart Auto-Generated Playlists (distinct from Favorites & Recently Played)
            item {
                Text(
                    text = "Smart Playlists",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(SECONDARY_SMART_PLAYLISTS, key = { it.name }) { smartType ->
                        val shape = RoundedCornerShape(14.dp)
                        val count = smartPlaylistCounts[smartType] ?: 0
                        Column(
                            modifier = Modifier
                                .width(154.dp)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                .clickable { onOpenSmartPlaylist(smartType) }
                                .padding(12.dp)
                                .testTag("smart_playlist_${smartType.name.lowercase()}"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LavenderCardDeep),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = LavenderCard,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            Text(
                                text = smartType.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "$count songs",
                                style = MonoBadgeStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // 3. Your Playlists + Create Playlist row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Your Playlists",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val bannerShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bannerShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), bannerShape)
                        .clickable { showCreateDialog = true }
                        .padding(14.dp)
                        .testTag("create_playlist_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Create Playlist",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Curate a custom mix from your local tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                        Text("No Custom Playlists Yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap Create Playlist above to organize your local songs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    val cardShape = RoundedCornerShape(14.dp)
                    var menuOpen by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, cardShape)
                            .clickable { onOpenPlaylist(playlist.id) }
                            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
                            .testTag("playlist_card_${playlist.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistCollageArtwork(
                            customCoverUri = playlist.customCoverUri,
                            artworkUris = playlist.artworkUris,
                            modifier = Modifier.size(56.dp),
                            cornerRadius = 10.dp,
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (playlist.description.isNotBlank()) {
                                Text(
                                    text = playlist.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "${playlist.trackCount} songs · ${formatDurationSummary(playlist.totalDurationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Playlist options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Play All") },
                                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                                    onClick = { menuOpen = false; onPlayPlaylist(playlist.id, false) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Shuffle") },
                                    leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                                    onClick = { menuOpen = false; onPlayPlaylist(playlist.id, true) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename & Edit") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = { menuOpen = false; editingPlaylist = playlist },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = { menuOpen = false; deletingPlaylist = playlist },
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateOrEditPlaylistDialog(
            title = "New Playlist",
            initialName = "",
            initialDescription = "",
            confirmLabel = "Create",
            onConfirm = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    editingPlaylist?.let { target ->
        CreateOrEditPlaylistDialog(
            title = "Edit Playlist",
            initialName = target.name,
            initialDescription = target.description,
            confirmLabel = "Save",
            onConfirm = { name, desc ->
                onRenamePlaylist(target.id, name, desc)
                editingPlaylist = null
            },
            onDismiss = { editingPlaylist = null },
        )
    }

    deletingPlaylist?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingPlaylist = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("Your local audio files will not be deleted.") },
            confirmButton = {
                Button(onClick = {
                    onDeletePlaylist(target.id)
                    deletingPlaylist = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlaylist = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PinnedSmartTile(
    title: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LavenderCardDeep),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = LavenderCard,
                modifier = Modifier.size(19.dp),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$count songs",
                style = MonoBadgeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CreateOrEditPlaylistDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_field"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("playlist_desc_field"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("playlist_dialog_confirm"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
