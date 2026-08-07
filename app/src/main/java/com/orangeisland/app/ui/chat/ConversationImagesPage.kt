package com.orangeisland.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orangeisland.app.ui.components.CircularBackButton
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Per-conversation image cleanup page. Lists every image attached to any message in the
 * conversation as a selectable grid; deleting a selection strips those paths from the
 * owning message's `images` list (DB only — on-disk files are left untouched) so future
 * turns no longer send them as context. Text and non-selected images are unaffected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationImagesPage(
    conversationId: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // Triple(messageId, imagePath, timestamp)
    var images by remember(conversationId) { mutableStateOf<List<Triple<String, String, Long>>?>(null) }
    var selected by remember(conversationId) { mutableStateOf(setOf<Pair<String, String>>()) }
    var showConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        images = viewModel.getConversationImages(conversationId)
    }

    val allSelected = images != null && images!!.isNotEmpty() &&
        selected.size == images!!.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理图片" + (images?.let { " (${it.size})" } ?: "")) },
                navigationIcon = { CircularBackButton(onClick = onBack) },
                actions = {
                    if (!images.isNullOrEmpty()) {
                        TextButton(onClick = {
                            selected = if (allSelected) emptySet() else images!!.map { it.first to it.second }.toSet()
                        }) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!selected.isEmpty()) {
                Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("已选 ${selected.size} 张", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { showConfirm = true },
                            enabled = !isDeleting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(if (isDeleting) "删除中…" else "删除所选")
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            images == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            images!!.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        "这个对话里还没有图片",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp) + padding,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(images!!, key = { "${it.first}_${it.second}" }) { (messageId, path, _) ->
                        val key = messageId to path
                        val isSelected = key in selected
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selected = if (isSelected) selected - key else selected + key
                                }
                        ) {
                            coil.compose.AsyncImage(
                                model = path,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.35f))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Black.copy(alpha = 0.35f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("删除 ${selected.size} 张图片？") },
            text = { Text("只会从这个对话的消息里去掉这些图片的引用，以后不会再作为上下文发给模型。文字内容不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    isDeleting = true
                    val toDelete = selected
                    scope.launch {
                        viewModel.deleteConversationImages(conversationId, toDelete)
                        images = images?.filterNot { (mid, path, _) -> (mid to path) in toDelete }
                        selected = emptySet()
                        isDeleting = false
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            }
        )
    }
}

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    start = this.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
        other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    end = this.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
        other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
)
