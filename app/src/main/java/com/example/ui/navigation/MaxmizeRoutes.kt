package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface MaxmizeRoute {
    @Serializable data object Home : MaxmizeRoute
    @Serializable data object Library : MaxmizeRoute
    @Serializable data object Online : MaxmizeRoute
    @Serializable data object DjMode : MaxmizeRoute
    @Serializable data object Playlists : MaxmizeRoute
    @Serializable data object Search : MaxmizeRoute
    @Serializable data object Settings : MaxmizeRoute
    @Serializable data class AlbumDetail(val albumKey: String) : MaxmizeRoute
    @Serializable data class ArtistDetail(val artistName: String) : MaxmizeRoute
    @Serializable data class GenreDetail(val genreName: String) : MaxmizeRoute
    @Serializable data class FolderDetail(val folderPath: String) : MaxmizeRoute
    @Serializable data class PlaylistDetail(val playlistId: Long) : MaxmizeRoute
    @Serializable data class SmartPlaylistDetail(val typeName: String) : MaxmizeRoute
    @Serializable data object Equalizer : MaxmizeRoute
    @Serializable data object Automix : MaxmizeRoute
    @Serializable data object AutomixSettings : MaxmizeRoute
    @Serializable data object DjQueue : MaxmizeRoute
}
