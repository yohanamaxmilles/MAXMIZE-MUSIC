package com.example.player

import com.example.data.local.db.MixHistoryDao
import com.example.data.local.db.MixHistoryEntity
import com.example.data.model.AutomixUiState
import com.example.data.model.MixHistoryEntry
import com.example.data.model.MixMode
import com.example.data.model.TransitionStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class AutomixRuntime(
    mixHistoryDao: MixHistoryDao,
) {
    private val _uiState = MutableStateFlow(AutomixUiState())
    val uiState: StateFlow<AutomixUiState> = _uiState.asStateFlow()

    val mixHistoryFlow: Flow<List<MixHistoryEntry>> = mixHistoryDao.observeMixHistory()
        .map { list -> list.map { it.toDomain() } }

    fun updateState(transform: (AutomixUiState) -> AutomixUiState) {
        _uiState.update(transform)
    }

    fun setState(newState: AutomixUiState) {
        _uiState.value = newState
    }
}

private fun MixHistoryEntity.toDomain(): MixHistoryEntry = MixHistoryEntry(
    id = id,
    fromTrackId = fromTrackId,
    fromTitle = fromTitle,
    fromArtist = fromArtist,
    toTrackId = toTrackId,
    toTitle = toTitle,
    toArtist = toArtist,
    mixMode = runCatching { MixMode.valueOf(mixMode) }.getOrDefault(MixMode.SMART),
    transitionStyle = runCatching { TransitionStyle.valueOf(transitionStyle) }.getOrDefault(TransitionStyle.SMOOTH_FADE),
    bpmFrom = bpmFrom,
    bpmTo = bpmTo,
    camelotFrom = camelotFrom,
    camelotTo = camelotTo,
    compatibilityScore = compatibilityScore,
    mixedAt = mixedAt,
)
