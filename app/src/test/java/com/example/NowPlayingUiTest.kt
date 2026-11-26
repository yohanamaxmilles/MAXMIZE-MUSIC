package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.data.model.PlaybackUiState
import com.example.data.model.Track
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.theme.MaxmizeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `now playing shows only lyrics pill above up next and removes queue and eq pills`() {
        val sampleTrack = Track(
            id = "t1",
            mediaStoreId = 1L,
            contentUri = "content://media/external/audio/media/1",
            title = "Midnight Groove",
            artist = "Maxmize Artist",
            album = "After Hours",
            albumArtist = "Maxmize Artist",
            genre = "Afrobeats",
            durationMs = 215_000L,
            trackNumber = 1,
            year = 2026,
            mimeType = "audio/mpeg",
            filePath = "/music/t1.mp3",
            folderPath = "/music",
            folderName = "music",
            fileSizeBytes = 5_000_000L,
            dateAddedSec = 1_700_000_000L,
            albumId = 10L,
            artworkUri = null,
            customArtworkUri = null,
        )
        val state = PlaybackUiState(
            activeMediaId = sampleTrack.id,
            currentTrack = sampleTrack,
            queue = listOf(sampleTrack),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 45_000L,
            durationMs = 215_000L,
        )
        val posFlow = MutableStateFlow(45_000L)

        composeTestRule.setContent {
            MaxmizeTheme {
                NowPlayingScreen(
                    playbackState = state,
                    positionMsFlow = posFlow,
                    onCollapse = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSeekForward = {},
                    onSeekBackward = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onSelectQueueIndex = {},
                    onMoveQueueItem = { _, _ -> },
                    onRemoveFromQueue = {},
                    onClearQueue = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onOpenLyrics = {},
                    onOpenSleepTimer = {},
                    onSpeedChange = {},
                    onBalanceChange = {},
                    onAddToPlaylist = {},
                    onEditMetadata = {},
                    onTrackInfo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_lyrics_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("up_next_open_queue_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_queue_btn").assertDoesNotExist()
        composeTestRule.onNodeWithTag("now_playing_eq_btn").assertDoesNotExist()
    }
}
