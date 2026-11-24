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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Playlist
import com.example.data.model.SmartPlaylistType
import com.example.ui.components.PlaylistCollageArtwork
import com.example.ui.components.formatDurationSummary
import com.example.ui.theme.BadgeGoldBg
import com.example.ui.theme.BadgeGoldFg

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
    onOpenSearch: () -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search playlists")
                }
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_playlist_btn"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create playlist")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Prominent Create Playlist Editorial Banner (Solid tinted surface, zero gradients)
            item {
                val bannerShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bannerShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), bannerShape)
                        .clickable { showCreateDialog = true }
                        .padding(16.dp)
                        .testTag("create_playlist_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
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
                            text = "Start your own playlist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Smart Auto-Generated Playlists
            item {
                Text(
                    text = "Smart Playlists",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(SmartPlaylistType.entries) { smartType ->
                        val shape = RoundedCornerShape(14.dp)
                        val count = smartPlaylistCounts[smartType] ?: 0
                        Column(
                            modifier = Modifier
                                .width(156.dp)
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
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BadgeGoldBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = BadgeGoldFg,
                                    modifier = Modifier.size(18.dp),
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your Playlists",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (playlists.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp),
                        )
                        Text("No Playlists Yet", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Create custom playlists to organize your local tracks, albums, and listening sessions.",
                            style = MaterialTheme.typography.bodyMedium,
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
                            modifier = Modifier.size(58.dp),
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
