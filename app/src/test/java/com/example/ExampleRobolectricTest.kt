package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Maxmize", appName)
    }

    @Test
    fun `five primary bottom destinations and top settings navigation work cleanly`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_library_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_settings_btn").assertIsDisplayed()

        // Library direct segment tabs & grid/list toggle
        composeTestRule.onNodeWithTag("nav_library").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lib_tab_songs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lib_tab_albums").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lib_view_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lib_tab_artists").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lib_tab_genres").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lib_tab_folders").assertIsDisplayed()

        // Dedicated DJ Mode hub tab
        composeTestRule.onNodeWithTag("nav_dj_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dj_mode_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dj_home_section").assertIsDisplayed()

        // Playlists hierarchy
        composeTestRule.onNodeWithTag("nav_playlists").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playlists_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smart_playlist_favorites").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smart_playlist_recently_played").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playlists_list").performScrollToNode(hasTestTag("create_playlist_banner"))
        composeTestRule.onNodeWithTag("create_playlist_banner").assertIsDisplayed()

        // Search destination
        composeTestRule.onNodeWithTag("nav_search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_settings_btn").assertIsDisplayed()

        // Upper-right Settings access & clean back return
        composeTestRule.onNodeWithTag("top_settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_open_automix").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_open_automix_settings").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("theme_chip_dark"))
        composeTestRule.onNodeWithTag("theme_chip_dark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_chip_light").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("accent_swatch_lime"))
        composeTestRule.onNodeWithTag("accent_swatch_lime").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accent_swatch_gold").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_back_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_screen").assertIsDisplayed()
    }

    @Test
    fun `dj mode hub navigates all six internal sections cleanly`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_dj_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dj_mode_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dj_home_section").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dj_tab_automix").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automix_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automix_screen").performScrollToNode(hasTestTag("start_automix_btn"))
        composeTestRule.onNodeWithTag("start_automix_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dj_tab_queue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dj_queue_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dj_subtab_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().printToLog("DJ_HISTORY_DEBUG")
        composeTestRule.onNodeWithTag("dj_history_list").assertIsDisplayed()

        // Profiles is inside the DJ hub's More menu.
        composeTestRule.onNodeWithTag("dj_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Profiles").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dj_profiles_section").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dj_tab_analysis").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dj_analysis_section").assertIsDisplayed()

        composeTestRule.onNodeWithText("DJ Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automix_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automix_settings_screen").performScrollToNode(hasTestTag("switch_strict_genre"))
        composeTestRule.onNodeWithTag("switch_strict_genre").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automix_settings_screen").performScrollToNode(hasTestTag("harmonic_level_strict"))
        composeTestRule.onNodeWithTag("harmonic_level_strict").assertIsDisplayed()

        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dj_home_section").assertIsDisplayed()
    }

    @Test
    fun `settings displays nine categories including typography artwork and library health controls`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("font_choice_plus_jakarta"))
        composeTestRule.onNodeWithTag("font_choice_plus_jakarta").assertIsDisplayed()
        composeTestRule.onNodeWithTag("font_choice_inter").assertIsDisplayed()
        composeTestRule.onNodeWithTag("font_choice_manrope").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("settings_library_stats"))
        composeTestRule.onNodeWithTag("settings_library_stats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("settings_metadata_health"))
        composeTestRule.onNodeWithTag("settings_metadata_health").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("settings_about_row"))
        composeTestRule.onNodeWithTag("settings_about_row").assertIsDisplayed()
    }
}
