package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
    fun `bottom navigation switches across all five destinations`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_library_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_library").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lib_dir_songs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lib_dir_songs").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lib_tab_songs").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_playlists").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playlists_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_playlist_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smart_playlist_recently_played").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_open_automix").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_open_automix_settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("theme_chip_dark"))
        composeTestRule.onNodeWithTag("theme_chip_dark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_chip_light").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("accent_swatch_gold"))
        composeTestRule.onNodeWithTag("accent_swatch_gold").assertIsDisplayed()
    }

    @Test
    fun `automix hub settings and dj queue screens navigate cleanly`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_open_automix").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automix_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automix_screen").performScrollToNode(hasTestTag("start_automix_btn"))
        composeTestRule.onNodeWithTag("start_automix_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("automix_screen").performScrollToNode(hasTestTag("automix_settings_btn"))
        composeTestRule.onNodeWithTag("automix_settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automix_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("automix_settings_screen").performScrollToNode(hasTestTag("switch_strict_genre"))
        composeTestRule.onNodeWithTag("switch_strict_genre").assertIsDisplayed()
        composeTestRule.onNodeWithTag("harmonic_level_strict").assertIsDisplayed()

        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("automix_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("automix_screen").performScrollToNode(hasTestTag("automix_queue_btn"))
        composeTestRule.onNodeWithTag("automix_queue_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dj_queue_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dj_tab_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dj_tab_queue").assertIsDisplayed()
    }

    @Test
    fun `settings displays typography artwork and library health controls`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("settings_library_stats"))
        composeTestRule.onNodeWithTag("settings_library_stats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_metadata_health").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("font_choice_inter"))
        composeTestRule.onNodeWithTag("font_choice_inter").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("font_choice_manrope"))
        composeTestRule.onNodeWithTag("font_choice_manrope").assertIsDisplayed()
    }
}
