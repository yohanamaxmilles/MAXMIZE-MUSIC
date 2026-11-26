package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.LyricLine
import com.example.data.model.LyricsDocument
import com.example.data.model.LyricsSource
import com.example.data.model.Track
import com.example.ui.lyrics.LyricsScreenContent
import com.example.ui.lyrics.LyricsScreenUiState
import com.example.ui.lyrics.LyricsSearchUiState
import com.example.ui.theme.MaxmizeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LyricsScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleTrack() = Track(
        id = "track_lyrics_1",
        mediaStoreId = 10L,
        contentUri = "content://media/external/audio/media/10",
        title = "Kwa Ajili Yako",
        artist = "Harmonize",
        album = "Single",
        albumArtist = "Harmonize",
        genre = "Bongo Flava",
        durationMs = 200_000L,
        trackNumber = 1,
        year = 2025,
        mimeType = "audio/mpeg",
        filePath = "/music/kwa_ajili.mp3",
        folderPath = "/music",
        folderName = "music",
        fileSizeBytes = 4_000_000L,
        dateAddedSec = 1_700_000_000L,
        albumId = 10L,
        artworkUri = null,
        customArtworkUri = null,
    )

    @Test
    fun `error state renders Retry button and triggers callback`() {
        var retried = false

        composeTestRule.setContent {
            MaxmizeTheme {
                LyricsScreenContent(
                    uiState = LyricsScreenUiState.Error(
                        track = sampleTrack(),
                        message = "Offline — connect to the internet to fetch lyrics.",
                        isOffline = true,
                    ),
                    searchState = LyricsSearchUiState(),
                    fontSizeSp = 22f,
                    onDismiss = {},
                    onRetry = { retried = true },
                    onRefresh = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onLineTapped = {},
                    onUserScrolled = {},
                    onResumeAutoFollow = {},
                    onSearchOnline = {},
                    onApplyCandidate = {},
                    onAdjustSyncOffset = {},
                    onResetSyncOffset = {},
                    onSaveManualLyrics = {},
                    onSaveSyncedLines = {},
                    onImportFileClick = {},
                    onClearManualLyrics = {},
                    onFontSizeChange = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_error_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_retry_btn").assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun `synced content supports tap-to-seek, resume auto-sync pill, and sync or edit modals`() {
        var tappedLine: LyricLine? = null
        var resumedFollow = false
        var adjustedOffsetDelta = 0L
        var savedSyncedLines: List<LyricLine>? = null
        var savedManualText: String? = null

        val lines = listOf(
            LyricLine(timestampMs = 5_000L, text = "Mstari wa kwanza"),
            LyricLine(timestampMs = 12_000L, text = "Mstari wa pili"),
        )
        val doc = LyricsDocument(
            trackId = "track_lyrics_1",
            lines = lines,
            rawText = "[00:05.00]Mstari wa kwanza\n[00:12.00]Mstari wa pili",
            isSynced = true,
            source = LyricsSource.ONLINE_LRCLIB,
            providerName = "LRCLIB",
        )

        composeTestRule.setContent {
            MaxmizeTheme {
                LyricsScreenContent(
                    uiState = LyricsScreenUiState.Content(
                        track = sampleTrack(),
                        document = doc,
                        activeLineIndex = 0,
                        activeWordIndex = -1,
                        playbackPositionMs = 12_340L,
                        durationMs = 200_000L,
                        isPlaying = true,
                        isBoundToActiveTrack = true,
                        isAutoFollowEnabled = false,
                    ),
                    searchState = LyricsSearchUiState(),
                    fontSizeSp = 22f,
                    onDismiss = {},
                    onRetry = {},
                    onRefresh = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onLineTapped = { tappedLine = it },
                    onUserScrolled = {},
                    onResumeAutoFollow = { resumedFollow = true },
                    onSearchOnline = {},
                    onApplyCandidate = {},
                    onAdjustSyncOffset = { delta -> adjustedOffsetDelta += delta },
                    onResetSyncOffset = {},
                    onSaveManualLyrics = { savedManualText = it },
                    onSaveSyncedLines = { savedSyncedLines = it },
                    onImportFileClick = {},
                    onClearManualLyrics = {},
                    onFontSizeChange = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_synced_list").assertIsDisplayed()

        // Tap lyric line to seek
        composeTestRule.onNodeWithTag("lyric_line_1").performClick()
        assertEquals(12_000L, tappedLine?.timestampMs)

        // Tap Resume Auto-Sync pill
        composeTestRule.onNodeWithTag("lyrics_follow_sync_btn").assertIsDisplayed().performClick()
        assertTrue(resumedFollow)

        // Open More -> Timing & Sync dialog -> adjust offset + stamp + save
        composeTestRule.onNodeWithTag("lyrics_more_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_menu_sync").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sync_nudge_plus_100").assertIsDisplayed().performClick()
        assertEquals(100L, adjustedOffsetDelta)
        composeTestRule.onNodeWithTag("sync_stamp_next_btn").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("sync_save_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertNotNull(savedSyncedLines)
        assertEquals(12_340L, savedSyncedLines!![0].timestampMs)

        // Open More -> Edit / Paste Lyrics -> edit and save
        composeTestRule.onNodeWithTag("lyrics_more_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_menu_edit").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_editor_field").assertIsDisplayed()
            .performTextReplacement("[00:10.00]Edited line one\n[00:15.00]Edited line two")
        composeTestRule.onNodeWithTag("lyrics_editor_save_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(savedManualText?.contains("Edited line one") == true)
    }
}
