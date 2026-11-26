package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.LibraryCategory
import com.example.data.model.MixMode
import com.example.data.model.SmartPlaylistType
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.EditMetadataSheet
import com.example.ui.components.MaxmizeBottomBar
import com.example.ui.components.MaxmizeNavigationRail
import com.example.ui.components.MiniPlayer
import com.example.ui.components.MaxmizeIcons
import com.example.ui.components.NavDestinationItem
import com.example.ui.components.SleepTimerSheet
import com.example.ui.components.TrackInfoSheet
import com.example.ui.navigation.MaxmizeRoute
import com.example.ui.screens.AutomixScreen
import com.example.ui.screens.AutomixSettingsScreen
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.CreateOrEditPlaylistDialog
import com.example.ui.screens.DjModeScreen
import com.example.ui.screens.DjQueueScreen
import com.example.ui.screens.DjSectionTab
import com.example.ui.screens.EqualizerScreen
import com.example.ui.lyrics.LyricsScreen
import com.example.ui.lyrics.LyricsViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrackMenuEvent
import com.example.ui.theme.MaxmizeTheme
import com.example.ui.viewmodel.MaxmizeViewModel
import com.example.ui.viewmodel.PendingWriteEdit

private data class TopRouteBinding(
    val navItem: NavDestinationItem,
    val route: MaxmizeRoute,
)

@Composable
fun MaxmizeApp(
    viewModel: MaxmizeViewModel,
    lyricsViewModel: LyricsViewModel,
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val automixState by viewModel.automixState.collectAsStateWithLifecycle()

    var hasAudioPerm by remember { mutableStateOf(viewModel.hasAudioPermission()) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }

    // Shared modal states
    var playlistTargetTracks by remember { mutableStateOf<List<Track>?>(null) }
    var multiSelectPlaylistIds by remember { mutableStateOf<List<String>?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var editMetadataTrack by remember { mutableStateOf<Track?>(null) }

    var isLyricsOverlayOpen by remember { mutableStateOf(false) }

    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var pendingWriteEdit by remember { mutableStateOf<PendingWriteEdit?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        lyricsViewModel.statusMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        hasAudioPerm = viewModel.hasAudioPermission()
        if (hasAudioPerm) {
            viewModel.startMediaStoreWatcher()
            viewModel.scanLibrary(false)
        }
    }

    val requestAudioPermissions = {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
        audioPermLauncher.launch(perms)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importSafFolder(uri)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importSafFile(uri)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    val writeConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val pending = pendingWriteEdit
        pendingWriteEdit = null
        if (result.resultCode == android.app.Activity.RESULT_OK && pending != null) {
            viewModel.completePendingMediaStoreWrite(pending)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.pendingWriteIntent.collect { (sender, edit) ->
            pendingWriteEdit = edit
            writeConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPerm) {
            requestAudioPermissions()
        } else {
            viewModel.startMediaStoreWatcher()
            viewModel.autoScanIfEnabled()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topBindings = remember {
        listOf(
            TopRouteBinding(
                NavDestinationItem("home", "Home", MaxmizeIcons.HomeFilled, MaxmizeIcons.Home, "nav_home"),
                MaxmizeRoute.Home,
            ),
            TopRouteBinding(
                NavDestinationItem("library", "Library", MaxmizeIcons.LibraryFilled, MaxmizeIcons.Library, "nav_library"),
                MaxmizeRoute.Library,
            ),
            TopRouteBinding(
                NavDestinationItem(
                    id = "dj_mode",
                    label = "DJ Mode",
                    selectedIcon = MaxmizeIcons.PulseFilled,
                    unselectedIcon = MaxmizeIcons.Pulse,
                    testTag = "nav_dj_mode",
                    accentHighlighted = true,
                ),
                MaxmizeRoute.DjMode,
            ),
            TopRouteBinding(
                NavDestinationItem("playlists", "Playlists", MaxmizeIcons.QueueFilled, MaxmizeIcons.Queue, "nav_playlists"),
                MaxmizeRoute.Playlists,
            ),
            TopRouteBinding(
                NavDestinationItem("search", "Search", MaxmizeIcons.SearchFilled, MaxmizeIcons.Search, "nav_search"),
                MaxmizeRoute.Search,
            ),
        )
    }

    val navItems = remember(topBindings) { topBindings.map { it.navItem } }
    val selectedNavId = remember(currentDestination, topBindings) {
        topBindings.firstOrNull { binding ->
            currentDestination?.hasRoute(binding.route::class) == true
        }?.navItem?.id
    }

    val handleTrackMenu: (Track, TrackMenuEvent) -> Unit = { track, event ->
        when (event) {
            TrackMenuEvent.PlayNext -> {
                viewModel.playbackController.playNext(track)
                viewModel.notifyMessage("Playing \"${track.title}\" next")
            }
            TrackMenuEvent.AddToQueue -> {
                viewModel.playbackController.addToQueue(track)
                viewModel.notifyMessage("Added \"${track.title}\" to queue")
            }
            TrackMenuEvent.AddToPlaylist -> playlistTargetTracks = listOf(track)
            TrackMenuEvent.EditMetadata -> editMetadataTrack = track
            TrackMenuEvent.AutoFixMetadata -> viewModel.smartAutoFixTrack(track)
            TrackMenuEvent.ViewLyrics -> {
                lyricsViewModel.openForTrack(track)
                isLyricsOverlayOpen = true
            }
            TrackMenuEvent.GoToAlbum -> {
                navController.navigate(MaxmizeRoute.AlbumDetail(MusicRepository.albumKey(track)))
            }
            TrackMenuEvent.GoToArtist -> {
                navController.navigate(MaxmizeRoute.ArtistDetail(track.artist))
            }
            TrackMenuEvent.TrackInfo -> infoTrack = track
        }
    }

    val dismissTransientOverlays = {
        isNowPlayingOpen = false
        isLyricsOverlayOpen = false
        lyricsViewModel.clearTrackOverride()
        showSleepTimerSheet = false
        playlistTargetTracks = null
        multiSelectPlaylistIds = null
        infoTrack = null
        editMetadataTrack = null
    }

    val isTopDestination: (androidx.navigation.NavDestination?) -> Boolean = { dest ->
        dest != null && topBindings.any { dest.hasRoute(it.route::class) }
    }

    val navigateTopLevel: (MaxmizeRoute) -> Unit = { route ->
        dismissTransientOverlays()
        // Pop any pushed secondary screens (Settings, Equalizer, Detail views, etc.) before
        // switching or returning to a top-level tab so Settings never gets saved into tab state.
        while (navController.currentDestination?.let { !isTopDestination(it) } == true) {
            if (!navController.popBackStack()) break
        }
        if (navController.currentDestination?.hasRoute(route::class) != true) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val openSettings: () -> Unit = {
        dismissTransientOverlays()
        if (navController.currentDestination?.hasRoute(MaxmizeRoute.Settings::class) != true) {
            navController.navigate(MaxmizeRoute.Settings) {
                launchSingleTop = true
            }
        }
    }

    val openDjMode: (DjSectionTab) -> Unit = { tab ->
        viewModel.selectDjTab(tab)
        navigateTopLevel(MaxmizeRoute.DjMode)
    }

    val onNavSelect: (NavDestinationItem) -> Unit = { item ->
        val binding = topBindings.firstOrNull { it.navItem.id == item.id }
        if (binding != null) {
            if (binding.route == MaxmizeRoute.Library) {
                viewModel.selectLibraryCategory(LibraryCategory.SONGS)
            }
            navigateTopLevel(binding.route)
        }
    }

    MaxmizeTheme(
        themeMode = prefs.themeMode,
        accentPreset = prefs.accentPreset,
        fontChoice = prefs.fontChoice,
        fontScale = prefs.fontScale,
        uiDensity = prefs.uiDensity,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (!useNavRail) {
                            Column {
                                if (playbackState.currentTrack != null) {
                                    MiniPlayer(
                                        playbackState = playbackState,
                                        positionMsFlow = viewModel.playbackPositionMs,
                                        isAutomixActive = automixState.isActive,
                                        onOpenNowPlaying = { isNowPlayingOpen = true },
                                        onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                                        onSkipNext = viewModel.playbackController::skipNext,
                                        onSkipPrevious = viewModel.playbackController::skipPrevious,
                                    )
                                }
                                MaxmizeBottomBar(
                                    items = navItems,
                                    selectedId = selectedNavId,
                                    onSelect = onNavSelect,
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .statusBarsPadding()
                            .let { if (useNavRail) it.navigationBarsPadding() else it },
                    ) {
                        if (useNavRail) {
                            MaxmizeNavigationRail(
                                items = navItems,
                                selectedId = selectedNavId,
                                onSelect = onNavSelect,
                            )
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Box(modifier = Modifier.weight(1f)) {
                                NavHost(
                                    navController = navController,
                                    startDestination = MaxmizeRoute.Home,
                                    enterTransition = {
                                        if (isTopDestination(initialState.destination) && isTopDestination(targetState.destination)) {
                                            fadeIn(tween(150, easing = FastOutSlowInEasing))
                                        } else {
                                            slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 5 } +
                                                fadeIn(tween(180))
                                        }
                                    },
                                    exitTransition = {
                                        if (isTopDestination(initialState.destination) && isTopDestination(targetState.destination)) {
                                            fadeOut(tween(110, easing = FastOutSlowInEasing))
                                        } else {
                                            slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it / 6 } +
                                                fadeOut(tween(150))
                                        }
                                    },
                                    popEnterTransition = {
                                        if (isTopDestination(initialState.destination) && isTopDestination(targetState.destination)) {
                                            fadeIn(tween(150, easing = FastOutSlowInEasing))
                                        } else {
                                            slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 6 } +
                                                fadeIn(tween(180))
                                        }
                                    },
                                    popExitTransition = {
                                        if (isTopDestination(initialState.destination) && isTopDestination(targetState.destination)) {
                                            fadeOut(tween(110, easing = FastOutSlowInEasing))
                                        } else {
                                            slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 5 } +
                                                fadeOut(tween(150))
                                        }
                                    },
                                ) {
                                    composable<MaxmizeRoute.Home> {
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val albums by viewModel.albums.collectAsStateWithLifecycle()
                                        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                                        val scanState by viewModel.scanState.collectAsStateWithLifecycle()
                                        HomeScreen(
                                            tracks = tracks,
                                            albums = albums,
                                            playlists = playlists,
                                            playbackState = playbackState,
                                            positionMsFlow = viewModel.playbackPositionMs,
                                            scanState = scanState,
                                            hasAudioPermission = hasAudioPerm,
                                            isAutomixActive = automixState.isActive,
                                            onRequestPermission = requestAudioPermissions,
                                            onRescan = viewModel::scanLibrary,
                                            onImportFolder = { folderPickerLauncher.launch(null) },
                                            onImportAudioFile = { filePickerLauncher.launch(arrayOf("audio/*")) },
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                                            onOpenNowPlaying = { isNowPlayingOpen = true },
                                            onOpenSettings = openSettings,
                                            onOpenDjMode = { openDjMode(DjSectionTab.HOME) },
                                            onOpenSearch = { navigateTopLevel(MaxmizeRoute.Search) },
                                            onOpenCategory = { category ->
                                                viewModel.selectLibraryCategory(category)
                                                navigateTopLevel(MaxmizeRoute.Library)
                                            },
                                            onOpenAlbum = { key ->
                                                navController.navigate(MaxmizeRoute.AlbumDetail(key))
                                            },
                                            onOpenPlaylist = { id ->
                                                navController.navigate(MaxmizeRoute.PlaylistDetail(id))
                                            },
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onTrackMenuAction = handleTrackMenu,
                                        )
                                    }

                                    composable<MaxmizeRoute.Library> {
                                        val selectedCategory by viewModel.selectedLibraryCategory.collectAsStateWithLifecycle()
                                        val sortedTracks by viewModel.sortedTracks.collectAsStateWithLifecycle()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val albums by viewModel.albums.collectAsStateWithLifecycle()
                                        val artists by viewModel.artists.collectAsStateWithLifecycle()
                                        val genres by viewModel.genres.collectAsStateWithLifecycle()
                                        val folders by viewModel.folders.collectAsStateWithLifecycle()
                                        val scanState by viewModel.scanState.collectAsStateWithLifecycle()
                                        LibraryScreen(
                                            selectedCategory = selectedCategory,
                                            onSelectCategory = viewModel::selectLibraryCategory,
                                            sortedTracks = sortedTracks,
                                            allTracks = tracks,
                                            albums = albums,
                                            artists = artists,
                                            genres = genres,
                                            folders = folders,
                                            sortOption = prefs.sortOption,
                                            sortOrder = prefs.sortOrder,
                                            onChangeSort = viewModel::setSort,
                                            scanState = scanState,
                                            playbackState = playbackState,
                                            onRescan = viewModel::scanLibrary,
                                            onImportFolder = { folderPickerLauncher.launch(null) },
                                            onOpenSettings = openSettings,
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onOpenAlbum = { key -> navController.navigate(MaxmizeRoute.AlbumDetail(key)) },
                                            onOpenArtist = { name -> navController.navigate(MaxmizeRoute.ArtistDetail(name)) },
                                            onOpenGenre = { genre -> navController.navigate(MaxmizeRoute.GenreDetail(genre)) },
                                            onOpenFolder = { path -> navController.navigate(MaxmizeRoute.FolderDetail(path)) },
                                            onExcludeFolder = viewModel::excludeFolder,
                                            onCreatePlaylistFromSelected = { ids -> multiSelectPlaylistIds = ids },
                                            onTrackMenuAction = handleTrackMenu,
                                        )
                                    }

                                    composable<MaxmizeRoute.DjMode> {
                                        val selectedDjTab by viewModel.selectedDjTab.collectAsStateWithLifecycle()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val trackAnalyses by viewModel.trackAnalyses.collectAsStateWithLifecycle()
                                        val mixHistory by viewModel.mixHistory.collectAsStateWithLifecycle()
                                        val genres by viewModel.genres.collectAsStateWithLifecycle()
                                        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                                        val customDjProfiles by viewModel.customDjProfiles.collectAsStateWithLifecycle()
                                        val genreRules by viewModel.genreRules.collectAsStateWithLifecycle()
                                        DjModeScreen(
                                            selectedTab = selectedDjTab,
                                            onSelectTab = viewModel::selectDjTab,
                                            automixState = automixState,
                                            automixConfig = prefs.automixConfig,
                                            playbackState = playbackState,
                                            tracks = tracks,
                                            analyses = trackAnalyses,
                                            mixHistory = mixHistory,
                                            genres = genres,
                                            playlists = playlists,
                                            profiles = customDjProfiles,
                                            genreRules = genreRules,
                                            onOpenSettings = openSettings,
                                            onOpenSearch = { navigateTopLevel(MaxmizeRoute.Search) },
                                            onStartAutomix = { viewModel.startAutomix(playbackState.currentTrack?.id) },
                                            onStopAutomix = viewModel::stopAutomix,
                                            onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                                            onSkipNextMix = viewModel.playbackController::skipAutomixNext,
                                            onToggleBpmMatch = {
                                                viewModel.updateAutomixConfig { it.copy(bpmMatchEnabled = !it.bpmMatchEnabled) }
                                            },
                                            onToggleKeyLock = {
                                                viewModel.updateAutomixConfig { it.copy(keyLockEnabled = !it.keyLockEnabled) }
                                            },
                                            onCycleMixMode = {
                                                val entries = MixMode.entries
                                                val next = entries[(prefs.automixConfig.mode.ordinal + 1) % entries.size]
                                                viewModel.updateAutomixConfig { it.copy(mode = next) }
                                            },
                                            onAnalyzeLibrary = viewModel::analyzeLibraryBatch,
                                            onCorrectAnalysis = viewModel::correctTrackAnalysis,
                                            onPlayDjTrackNow = viewModel.playbackController::playDjQueueTrackNow,
                                            onMoveDjQueueItem = viewModel.playbackController::moveDjQueueItem,
                                            onRemoveDjQueueItem = viewModel.playbackController::removeDjQueueItem,
                                            onRecomputeQueue = viewModel.playbackController::recomputeDjQueue,
                                            onClearHistory = viewModel::clearMixHistory,
                                            onUpdateConfig = viewModel::updateAutomixConfig,
                                            onApplyProfile = viewModel::applyDjProfile,
                                            onSaveCustomProfile = viewModel::saveCustomDjProfile,
                                            onDeleteCustomProfile = viewModel::deleteCustomDjProfile,
                                            onUpsertGenreRule = viewModel::upsertGenreRule,
                                            onDeleteGenreRule = viewModel::deleteGenreRule,
                                        )
                                    }

                                    composable<MaxmizeRoute.Playlists> {
                                        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                                        val smartCounts by viewModel.smartPlaylistCounts.collectAsStateWithLifecycle()
                                        PlaylistsScreen(
                                            playlists = playlists,
                                            smartPlaylistCounts = smartCounts,
                                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                                            onOpenPlaylist = { id -> navController.navigate(MaxmizeRoute.PlaylistDetail(id)) },
                                            onOpenSmartPlaylist = { type ->
                                                navController.navigate(MaxmizeRoute.SmartPlaylistDetail(type.name))
                                            },
                                            onPlayPlaylist = viewModel::playPlaylist,
                                            onRenamePlaylist = viewModel::renameAndDescribePlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            onOpenSettings = openSettings,
                                        )
                                    }

                                    composable<MaxmizeRoute.Search> {
                                        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                                        val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
                                        val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
                                        val artists by viewModel.artists.collectAsStateWithLifecycle()
                                        SearchScreen(
                                            query = searchQuery,
                                            results = searchResults,
                                            searchHistory = searchHistory,
                                            suggestedArtists = artists,
                                            playbackState = playbackState,
                                            onQueryChange = viewModel::updateSearchQuery,
                                            onCommitQuery = viewModel::commitSearchQuery,
                                            onDeleteHistoryItem = viewModel::deleteSearchHistoryItem,
                                            onClearHistory = viewModel::clearSearchHistory,
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onOpenAlbum = { key -> navController.navigate(MaxmizeRoute.AlbumDetail(key)) },
                                            onOpenArtist = { name -> navController.navigate(MaxmizeRoute.ArtistDetail(name)) },
                                            onOpenGenre = { genre -> navController.navigate(MaxmizeRoute.GenreDetail(genre)) },
                                            onOpenFolder = { path -> navController.navigate(MaxmizeRoute.FolderDetail(path)) },
                                            onOpenPlaylist = { id -> navController.navigate(MaxmizeRoute.PlaylistDetail(id)) },
                                            onTrackMenuAction = handleTrackMenu,
                                            onOpenSettings = openSettings,
                                        )
                                    }

                                    composable<MaxmizeRoute.Settings> {
                                        val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
                                        val lyricsBatchStatus by viewModel.lyricsBatchStatus.collectAsStateWithLifecycle()
                                        val artworkBatchStatus by viewModel.artworkBatchStatus.collectAsStateWithLifecycle()
                                        val failedArtworkCount by viewModel.failedArtworkCount.collectAsStateWithLifecycle()
                                        val metadataHealth by viewModel.metadataHealth.collectAsStateWithLifecycle()
                                        val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle()
                                        val undoHistory by viewModel.undoHistory.collectAsStateWithLifecycle()
                                        val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        SettingsScreen(
                                            prefs = prefs,
                                            equalizerState = equalizerState,
                                            lyricsBatchStatus = lyricsBatchStatus,
                                            artworkBatchStatus = artworkBatchStatus,
                                            failedArtworkCount = failedArtworkCount,
                                            metadataHealth = metadataHealth,
                                            libraryStats = libraryStats,
                                            undoHistory = undoHistory,
                                            excludedFolders = excludedFolders,
                                            trackCount = tracks.size,
                                            hasAudioPermission = hasAudioPerm,
                                            onBack = { navController.popBackStack() },
                                            onRequestPermission = requestAudioPermissions,
                                            onSetThemeMode = viewModel::setThemeMode,
                                            onSetAccentPreset = viewModel::setAccentPreset,
                                            onSetFontChoice = viewModel::setFontChoice,
                                            onSetFontScale = viewModel::setFontScale,
                                            onSetUiDensity = viewModel::setUiDensity,
                                            onOpenEqualizer = { navController.navigate(MaxmizeRoute.Equalizer) },
                                            onToggleBassBoost = { enabled ->
                                                viewModel.audioEffectsManager.setBassBoost(if (enabled) 550 else 0)
                                            },
                                            onToggleVirtualizer = { enabled ->
                                                viewModel.audioEffectsManager.setVirtualizer(if (enabled) 550 else 0)
                                            },
                                            onSetGapless = viewModel::setGaplessEnabled,
                                            onSetSkipSilence = viewModel::setSkipSilenceEnabled,
                                            onSetLoudnessNormalization = viewModel::setLoudnessNormalizationEnabled,
                                            onSetCrossfade = viewModel.playbackController::setCrossfadeSeconds,
                                            onSetPlaybackSpeed = viewModel.playbackController::setPlaybackSpeed,
                                            onSetResumePaused = viewModel::setResumePausedOnReopen,
                                            onSetAutoDownloadLyrics = viewModel::setAutoDownloadLyrics,
                                            onSetLyricsWifiOnly = viewModel::setLyricsWifiOnly,
                                            onSetDownloadLyricsOnPlayStart = viewModel::setDownloadLyricsOnPlayStart,
                                            onSetMusixmatchApiKey = viewModel::setMusixmatchApiKey,
                                            onBatchFetchLyrics = viewModel::batchFetchMissingLyrics,
                                            onClearLyricsCache = {
                                                viewModel.clearLyricsCache()
                                                lyricsViewModel.onCacheCleared()
                                            },
                                            onSetAutoFixArtworkOnScan = viewModel::setAutoFixArtworkOnScan,
                                            onSetArtworkWifiOnly = viewModel::setArtworkWifiOnly,
                                            onBatchFixMissingArtwork = { viewModel.batchFixMissingArtwork(false) },
                                            onRetryFailedArtwork = viewModel::retryFailedArtwork,
                                            onClearArtworkCache = viewModel::clearArtworkCache,
                                            onEditTrackMetadata = { tr -> editMetadataTrack = tr },
                                            onAutoFixTrackArtwork = viewModel::autoFixArtworkForTrack,
                                            onUndoMetadataEntry = viewModel::undoMetadataEntry,
                                            onSetAutoScan = viewModel::setAutoScanOnLaunch,
                                            onSetMinDuration = viewModel::setMinDurationSec,
                                            onSetNotifSeek = viewModel::setShowNotificationSeekButtons,
                                            onRescan = viewModel::scanLibrary,
                                            onImportFolder = { folderPickerLauncher.launch(null) },
                                            onIncludeFolder = viewModel::includeFolder,
                                            onExportBackup = {
                                                exportBackupLauncher.launch("maxmize_backup_${System.currentTimeMillis()}.json")
                                            },
                                            onImportBackup = {
                                                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                                            },
                                            onOpenSearch = { navigateTopLevel(MaxmizeRoute.Search) },
                                        )
                                    }

                                    composable<MaxmizeRoute.Automix> {
                                        val trackAnalyses by viewModel.trackAnalyses.collectAsStateWithLifecycle()
                                        AutomixScreen(
                                            automixState = automixState,
                                            automixConfig = prefs.automixConfig,
                                            playbackState = playbackState,
                                            analyses = trackAnalyses,
                                            onBack = { navController.popBackStack() },
                                            onOpenSettings = { navController.navigate(MaxmizeRoute.AutomixSettings) },
                                            onOpenDjQueue = { navController.navigate(MaxmizeRoute.DjQueue) },
                                            onStartAutomix = { viewModel.startAutomix(playbackState.currentTrack?.id) },
                                            onStopAutomix = viewModel::stopAutomix,
                                            onSkipNextMix = viewModel.playbackController::skipAutomixNext,
                                            onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                                            onToggleBpmMatch = {
                                                viewModel.updateAutomixConfig { it.copy(bpmMatchEnabled = !it.bpmMatchEnabled) }
                                            },
                                            onToggleKeyLock = {
                                                viewModel.updateAutomixConfig { it.copy(keyLockEnabled = !it.keyLockEnabled) }
                                            },
                                            onCycleMixMode = {
                                                val entries = MixMode.entries
                                                val next = entries[(prefs.automixConfig.mode.ordinal + 1) % entries.size]
                                                viewModel.updateAutomixConfig { it.copy(mode = next) }
                                            },
                                            onAnalyzeLibrary = viewModel::analyzeLibraryBatch,
                                            onCorrectAnalysis = viewModel::correctTrackAnalysis,
                                        )
                                    }

                                    composable<MaxmizeRoute.AutomixSettings> {
                                        val genres by viewModel.genres.collectAsStateWithLifecycle()
                                        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                                        val customDjProfiles by viewModel.customDjProfiles.collectAsStateWithLifecycle()
                                        val genreRules by viewModel.genreRules.collectAsStateWithLifecycle()
                                        AutomixSettingsScreen(
                                            config = prefs.automixConfig,
                                            genres = genres,
                                            playlists = playlists,
                                            profiles = customDjProfiles,
                                            genreRules = genreRules,
                                            onBack = { navController.popBackStack() },
                                            onUpdateConfig = viewModel::updateAutomixConfig,
                                            onApplyProfile = viewModel::applyDjProfile,
                                            onSaveCustomProfile = viewModel::saveCustomDjProfile,
                                            onDeleteCustomProfile = viewModel::deleteCustomDjProfile,
                                            onUpsertGenreRule = viewModel::upsertGenreRule,
                                            onDeleteGenreRule = viewModel::deleteGenreRule,
                                        )
                                    }

                                    composable<MaxmizeRoute.DjQueue> {
                                        val trackAnalyses by viewModel.trackAnalyses.collectAsStateWithLifecycle()
                                        val mixHistory by viewModel.mixHistory.collectAsStateWithLifecycle()
                                        DjQueueScreen(
                                            automixState = automixState,
                                            playbackState = playbackState,
                                            analyses = trackAnalyses,
                                            mixHistory = mixHistory,
                                            onBack = { navController.popBackStack() },
                                            onOpenSearch = { navigateTopLevel(MaxmizeRoute.Search) },
                                            onPlayDjTrackNow = viewModel.playbackController::playDjQueueTrackNow,
                                            onMoveDjQueueItem = viewModel.playbackController::moveDjQueueItem,
                                            onRemoveDjQueueItem = viewModel.playbackController::removeDjQueueItem,
                                            onRecomputeQueue = viewModel.playbackController::recomputeDjQueue,
                                            onStopAutomix = viewModel::stopAutomix,
                                            onClearHistory = viewModel::clearMixHistory,
                                        )
                                    }

                                    composable<MaxmizeRoute.SmartPlaylistDetail> { entry ->
                                        val route = entry.toRoute<MaxmizeRoute.SmartPlaylistDetail>()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val trackAnalyses by viewModel.trackAnalyses.collectAsStateWithLifecycle()
                                        val type = runCatching { SmartPlaylistType.valueOf(route.typeName) }
                                            .getOrDefault(SmartPlaylistType.RECENTLY_PLAYED)
                                        val smartTracks = remember(tracks, trackAnalyses, type) {
                                            viewModel.smartPlaylistTracks(type)
                                        }
                                        CollectionDetailScreen(
                                            title = type.title,
                                            subtitle = type.subtitle,
                                            artworkUri = smartTracks.firstOrNull()?.displayArtwork,
                                            tracks = smartTracks,
                                            playbackState = playbackState,
                                            onBack = { navController.popBackStack() },
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onTrackMenuAction = handleTrackMenu,
                                        )
                                    }

                                    composable<MaxmizeRoute.AlbumDetail> { entry ->
                                        val route = entry.toRoute<MaxmizeRoute.AlbumDetail>()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val albums by viewModel.albums.collectAsStateWithLifecycle()
                                        val albumTracks = remember(tracks, route.albumKey) {
                                            tracks.filter { MusicRepository.albumKey(it) == route.albumKey }
                                                .sortedBy { it.trackNumber }
                                        }
                                        val albumInfo = albums.firstOrNull { it.key == route.albumKey }
                                        CollectionDetailScreen(
                                            title = albumInfo?.albumName ?: albumTracks.firstOrNull()?.album ?: "Album",
                                            subtitle = albumInfo?.artistName ?: albumTracks.firstOrNull()?.artist ?: "",
                                            artworkUri = albumInfo?.artworkUri,
                                            tracks = albumTracks,
                                            playbackState = playbackState,
                                            onBack = { navController.popBackStack() },
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onTrackMenuAction = handleTrackMenu,
                                        )
                                    }

                                    composable<MaxmizeRoute.ArtistDetail> { entry ->
                                        val route = entry.toRoute<MaxmizeRoute.ArtistDetail>()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val artists by viewModel.artists.collectAsStateWithLifecycle()
                                        val artistTracks = remember(tracks, route.artistName) {
                                            tracks.filter { it.artist == route.artistName }
                                        }
                                        val artistInfo = artists.firstOrNull { it.artistName == route.artistName }
                                        CollectionDetailScreen(
                                            title = route.artistName,
                                            subtitle = "${artistInfo?.albumCount ?: 0} albums",
                                            artworkUri = artistInfo?.artworkUri,
                                            tracks = artistTracks,
                                            playbackState = playbackState,
                                            onBack = { navController.popBackStack() },
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onTrackMenuAction = handleTrackMenu,
                                        )
                                    }

                                    composable<MaxmizeRoute.GenreDetail> { entry ->
                                        val route = entry.toRoute<MaxmizeRoute.GenreDetail>()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val genreTracks = remember(tracks, route.genreName) {
                                            tracks.filter { it.genre == route.genreName }
                                        }
                                        CollectionDetailScreen(
                                            title = route.genreName,
                                            subtitle = "Genre",
                                            artworkUri = genreTracks.firstOrNull()?.displayArtwork,
                                            tracks = genreTracks,
                                            playbackState = playbackState,
                                            onBack = { navController.popBackStack() },
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onTrackMenuAction = handleTrackMenu,
                                        )
                                    }

                                    composable<MaxmizeRoute.FolderDetail> { entry ->
                                        val route = entry.toRoute<MaxmizeRoute.FolderDetail>()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val folderTracks = remember(tracks, route.folderPath) {
                                            tracks.filter { it.folderPath == route.folderPath }
                                        }
                                        CollectionDetailScreen(
                                            title = route.folderPath.substringAfterLast('/').ifBlank { "Folder" },
                                            subtitle = route.folderPath,
                                            artworkUri = folderTracks.firstOrNull()?.displayArtwork,
                                            tracks = folderTracks,
                                            playbackState = playbackState,
                                            onBack = { navController.popBackStack() },
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onTrackMenuAction = handleTrackMenu,
                                        )
                                    }

                                    composable<MaxmizeRoute.PlaylistDetail> { entry ->
                                        val route = entry.toRoute<MaxmizeRoute.PlaylistDetail>()
                                        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                                        val playlistWithTracks by viewModel.observePlaylistWithTracks(route.playlistId)
                                            .collectAsStateWithLifecycle(initialValue = null)
                                        PlaylistDetailScreen(
                                            playlistWithTracks = playlistWithTracks,
                                            allLibraryTracks = tracks,
                                            playbackState = playbackState,
                                            onBack = { navController.popBackStack() },
                                            onPlayTracks = viewModel.playbackController::playTracks,
                                            onRenameAndDescribe = viewModel::renameAndDescribePlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            onAddTracks = viewModel::addTracksToPlaylist,
                                            onRemoveTrack = viewModel::removeTrackFromPlaylist,
                                            onReorderTracks = viewModel::reorderPlaylistTracks,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onTrackMenuAction = handleTrackMenu,
                                        )
                                    }

                                    composable<MaxmizeRoute.Equalizer> {
                                        val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
                                        EqualizerScreen(
                                            eqState = equalizerState,
                                            playbackSpeed = playbackState.playbackSpeed,
                                            audioBalance = playbackState.audioBalance,
                                            onBack = { navController.popBackStack() },
                                            onToggleEnabled = viewModel.audioEffectsManager::setEnabled,
                                            onSelectPreset = viewModel.audioEffectsManager::applyPreset,
                                            onBandLevelChange = viewModel.audioEffectsManager::setBandLevel,
                                            onBassBoostChange = viewModel.audioEffectsManager::setBassBoost,
                                            onVirtualizerChange = viewModel.audioEffectsManager::setVirtualizer,
                                            onResetEq = viewModel.audioEffectsManager::reset,
                                            onSpeedChange = viewModel.playbackController::setPlaybackSpeed,
                                            onBalanceChange = viewModel.playbackController::setAudioBalance,
                                        )
                                    }
                                }
                            }

                            if (useNavRail && playbackState.currentTrack != null) {
                                MiniPlayer(
                                    playbackState = playbackState,
                                    positionMsFlow = viewModel.playbackPositionMs,
                                    isAutomixActive = automixState.isActive,
                                    onOpenNowPlaying = { isNowPlayingOpen = true },
                                    onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                                    onSkipNext = viewModel.playbackController::skipNext,
                                    onSkipPrevious = viewModel.playbackController::skipPrevious,
                                )
                            }
                        }
                    }
                }
            }

            // Full-Screen Expandable Now Playing Overlay
            AnimatedVisibility(
                visible = isNowPlayingOpen && playbackState.currentTrack != null,
                enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160)),
            ) {
                NowPlayingScreen(
                    playbackState = playbackState,
                    positionMsFlow = viewModel.playbackPositionMs,
                    isAutomixActive = automixState.isActive,
                    onCollapse = { isNowPlayingOpen = false },
                    onOpenDjMode = {
                        isNowPlayingOpen = false
                        openDjMode(DjSectionTab.AUTOMIX)
                    },
                    onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                    onSeekTo = viewModel.playbackController::seekTo,
                    onSeekForward = { viewModel.playbackController.seekForward() },
                    onSeekBackward = { viewModel.playbackController.seekBackward() },
                    onSkipNext = viewModel.playbackController::skipNext,
                    onSkipPrevious = viewModel.playbackController::skipPrevious,
                    onSelectQueueIndex = viewModel.playbackController::playTrackAtQueueIndex,
                    onMoveQueueItem = viewModel.playbackController::moveQueueItem,
                    onRemoveFromQueue = viewModel.playbackController::removeFromQueue,
                    onClearQueue = viewModel.playbackController::clearQueue,
                    onToggleShuffle = viewModel.playbackController::toggleShuffle,
                    onCycleRepeat = viewModel.playbackController::cycleRepeat,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpenLyrics = { _ ->
                        lyricsViewModel.openForCurrentPlayback()
                        isLyricsOverlayOpen = true
                    },
                    onStartDjMix = { tr ->
                        viewModel.startAutomix(tr.id)
                        isNowPlayingOpen = false
                        openDjMode(DjSectionTab.AUTOMIX)
                    },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onSpeedChange = viewModel.playbackController::setPlaybackSpeed,
                    onBalanceChange = viewModel.playbackController::setAudioBalance,
                    onAddToPlaylist = { tr -> playlistTargetTracks = listOf(tr) },
                    onEditMetadata = { tr -> editMetadataTrack = tr },
                    onTrackInfo = { tr -> infoTrack = tr },
                )
            }

            // Full-Screen Lyrics Screen (Reactively driven by LyricsViewModel)
            AnimatedVisibility(
                visible = isLyricsOverlayOpen,
                enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160)),
            ) {
                LyricsScreen(
                    viewModel = lyricsViewModel,
                    onDismiss = {
                        isLyricsOverlayOpen = false
                        lyricsViewModel.clearTrackOverride()
                    },
                )
            }

            // Shared Bottom Sheets & Dialogs
            playlistTargetTracks?.let { target ->
                val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                AddToPlaylistSheet(
                    playlists = playlists,
                    trackCount = target.size,
                    onSelectPlaylist = { pid ->
                        viewModel.addTracksToPlaylist(pid, target.map { it.id })
                    },
                    onCreatePlaylist = { name, desc ->
                        viewModel.createPlaylist(name, desc, target.map { it.id })
                    },
                    onDismiss = { playlistTargetTracks = null },
                )
            }

            multiSelectPlaylistIds?.let { ids ->
                CreateOrEditPlaylistDialog(
                    title = "Create Playlist (${ids.size} songs)",
                    initialName = "",
                    initialDescription = "",
                    confirmLabel = "Create",
                    onConfirm = { name, desc ->
                        viewModel.createPlaylist(name, desc, ids)
                        multiSelectPlaylistIds = null
                    },
                    onDismiss = { multiSelectPlaylistIds = null },
                )
            }

            infoTrack?.let { tr ->
                val trackAnalyses by viewModel.trackAnalyses.collectAsStateWithLifecycle()
                val undoHistory by viewModel.undoHistory.collectAsStateWithLifecycle()
                TrackInfoSheet(
                    track = tr,
                    analysis = trackAnalyses[tr.id],
                    canUndo = undoHistory.any { it.trackId == tr.id },
                    onSetRating = { rating -> viewModel.setTrackRating(tr.id, rating) },
                    onUndoMetadata = { viewModel.undoLastEditForTrack(tr.id) },
                    onDismiss = { infoTrack = null },
                )
            }

            editMetadataTrack?.let { tr ->
                val artworkCandidates by viewModel.artworkCandidates.collectAsStateWithLifecycle()
                val isSearchingArtwork by viewModel.isSearchingArtwork.collectAsStateWithLifecycle()
                EditMetadataSheet(
                    track = tr,
                    metadataEditor = viewModel.container.metadataEditor,
                    artworkCandidates = artworkCandidates,
                    isSearchingArtwork = isSearchingArtwork,
                    onSearchArtworkOnline = viewModel::searchArtworkForTrack,
                    onApplyArtworkCandidate = viewModel::applyArtworkCandidate,
                    onSmartAutoFix = viewModel::previewSmartAutoFix,
                    onSave = { title, artist, album, genre, year, art ->
                        viewModel.saveTrackMetadata(tr, title, artist, album, genre, year, art)
                    },
                    onDismiss = {
                        viewModel.clearArtworkCandidates()
                        editMetadataTrack = null
                    },
                )
            }

            if (showSleepTimerSheet) {
                SleepTimerSheet(
                    sleepTimer = playbackState.sleepTimer,
                    onStartMinutes = viewModel.playbackController::startSleepTimer,
                    onStopAfterCurrentTrack = viewModel.playbackController::stopAfterCurrentTrack,
                    onCancelTimer = viewModel.playbackController::cancelSleepTimer,
                    onDismiss = { showSleepTimerSheet = false },
                )
            }
        }
    }
}
