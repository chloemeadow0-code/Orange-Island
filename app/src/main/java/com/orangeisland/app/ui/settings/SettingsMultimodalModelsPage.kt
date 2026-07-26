package com.orangeisland.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.ModelId
import com.orangeisland.app.model.apiModelName
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMultimodalModelsPage(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val transcriptionModel by viewModel.settings.imageTranscriptionModel.collectAsState()
    val transcriptionEnabledModels by viewModel.settings.imageTranscriptionEnabledModels.collectAsState()
    val imageGenModel by viewModel.settings.imageGenModel.collectAsState()
    val imageGenSize by viewModel.settings.imageGenSize.collectAsState()
    val embeddingModels by viewModel.settings.embeddingModels.collectAsState()

    var detailScreen by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler {
        if (detailScreen != null) {
            detailScreen = null
        } else {
            onBack()
        }
    }

    GuardedAnimatedContent(
        targetState = detailScreen,
        forward = detailScreen != null
    ) { screen ->
        when (screen) {
            "transcription" -> SettingsTranscriptionPage(
                viewModel = viewModel,
                onBack = { detailScreen = null }
            )
            "imagegen" -> SettingsImageGenPage(
                viewModel = viewModel,
                onBack = { detailScreen = null }
            )
            "search" -> SettingsSearchPage(
                viewModel = viewModel,
                onBack = { detailScreen = null }
            )
            else -> {
                CollapsingSettingsScaffold(
                    title = stringResource(R.string.settings_group_multimodal),
                    onBack = onBack
                ) {
                    SettingsGroupColumn {
                        SettingsGroup(
                            title = stringResource(R.string.settings_group_multimodal),
                            items = listOf(
                                {
                                    val tm = transcriptionModel
                                    val supporting = if (tm != null) {
                                        val alias = modelAliases[tm]
                                        val parsed = ModelId.parse(tm)
                                        val displayName = alias ?: parsed.apiModelName
                                        "$displayName · ${transcriptionEnabledModels.size} enabled"
                                    } else {
                                        stringResource(R.string.transcription_no_model)
                                    }
                                    SettingsItem(
                                        headlineContent = { Text(stringResource(R.string.settings_transcription)) },
                                        supportingContent = { Text(supporting) },
                                        leadingContent = {
                                            IslandIcon(IslandIcons.Transcription, size = 38.dp)
                                        },
                                        modifier = Modifier.clickable { detailScreen = "transcription" }
                                    )
                                },
                                {
                                    val igm = imageGenModel
                                    val supporting = if (igm != null) {
                                        val alias = modelAliases[igm]
                                        val parsed = ModelId.parse(igm)
                                        val displayName = alias ?: parsed.apiModelName
                                        "$displayName · $imageGenSize"
                                    } else {
                                        stringResource(R.string.image_gen_no_model)
                                    }
                                    SettingsItem(
                                        headlineContent = { Text(stringResource(R.string.settings_image_gen)) },
                                        supportingContent = { Text(supporting) },
                                        leadingContent = {
                                            IslandIcon(IslandIcons.ImageGeneration, size = 38.dp)
                                        },
                                        modifier = Modifier.clickable { detailScreen = "imagegen" }
                                    )
                                },
                                {
                                    val supporting = if (embeddingModels.isEmpty()) {
                                        stringResource(R.string.no_embedding_models)
                                    } else {
                                        "${embeddingModels.size} ${if (embeddingModels.size == 1) "model" else "models"} configured"
                                    }
                                    SettingsItem(
                                        headlineContent = { Text(stringResource(R.string.models_embedding_vector)) },
                                        supportingContent = { Text(supporting) },
                                        leadingContent = {
                                            IslandIcon(IslandIcons.SettingsSearch, size = 38.dp)
                                        },
                                        modifier = Modifier.clickable { detailScreen = "search" }
                                    )
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}
