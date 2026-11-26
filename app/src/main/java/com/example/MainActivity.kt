package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MaxmizeApp
import com.example.ui.lyrics.LyricsViewModel
import com.example.ui.viewmodel.MaxmizeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MaxmizeApplication).container
        setContent {
            val viewModel: MaxmizeViewModel = viewModel(
                factory = MaxmizeViewModel.Factory(container),
            )
            val lyricsViewModel: LyricsViewModel = viewModel(
                factory = LyricsViewModel.Factory(container),
            )
            MaxmizeApp(
                viewModel = viewModel,
                lyricsViewModel = lyricsViewModel,
            )
        }
    }
}
