package com.orangeisland.app.ui.chat.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.util.Constants
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

/**
 * State holder for the conversation-drawer search subsystem: owns the query text, the
 * debounced result set, and whether search mode is active. The debounce + actual search
 * (semantic vs. literal) run in [rememberDrawerSearchState]; the holder exposes read-only
 * results/isActive so the drawer UI only reads them.
 */
internal class DrawerSearchState(
    private val viewModel: ChatViewModel
) {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Pair<MessageEntity, Float>>>(emptyList())
        private set
    var isActive by mutableStateOf(false)
        private set

    suspend fun runSearch(method: String) {
        if (query.isBlank()) {
            results = emptyList()
            isActive = false
        } else {
            delay(200)
            if (query.isNotBlank()) {
                results = if (method == Constants.SEARCH_METHOD_RAG)
                    viewModel.semanticSearch(query)
                else
                    viewModel.searchMessages(query).map { it to 0f }
                isActive = true
            }
        }
    }
}

@Composable
internal fun rememberDrawerSearchState(viewModel: ChatViewModel): DrawerSearchState {
    val state = remember(viewModel) { DrawerSearchState(viewModel) }
    val manualSearchMethod by viewModel.settings.manualSearchMethod.collectAsState()
    LaunchedEffect(state.query) {
        state.runSearch(manualSearchMethod)
    }
    return state
}
