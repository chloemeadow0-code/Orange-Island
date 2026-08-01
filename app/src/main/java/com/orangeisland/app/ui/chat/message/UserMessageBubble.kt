package com.orangeisland.app.ui.chat.message

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Shape
import com.orangeisland.app.R
import com.orangeisland.app.util.noOpBringIntoView
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.ui.chat.AttachmentThumbnailItem
import com.orangeisland.app.ui.chat.ThumbnailClickHandlers
import com.orangeisland.app.ui.chat.findMetaForIndex
import com.orangeisland.app.ui.chat.resolveAttachmentType
import com.orangeisland.app.ui.common.LocalOrangeIslandHaptics
import com.orangeisland.app.ui.theme.ChatType

/**
 * The right-aligned user message bubble: attachment thumbnails, the message text
 * (or an inline editor), the branch switcher, and the copy/edit/overflow action row.
 * Extracted from [MessageItem]; the parent owns the info/delete dialogs, triggered
 * here via [onShowInfo] / [onShowDelete].
 */
@Composable
internal fun UserMessageBubble(
    message: ChatMessage,
    shape: Shape,
    backgroundColor: Color,
    textColor: Color,
    contextAlpha: Modifier,
    shouldAnimate: Boolean,
    isEditing: Boolean,
    isLoading: Boolean,
    isEditingAllowed: Boolean,
    branchIndex: Int,
    totalBranches: Int,
    onEdit: (String, String) -> Unit,
    onCancelEdit: () -> Unit,
    onStartEdit: () -> Unit,
    onSwitchBranch: (Int) -> Unit,
    onMediaClick: (List<String>, Int) -> Unit,
    onFileContentClick: ((fileName: String, content: String) -> Unit)?,
    onPdfPagesClick: ((pages: List<String>, startIndex: Int) -> Unit)?,
    onShowInfo: () -> Unit,
    onShowDelete: () -> Unit,
    bubbleBackgroundImagePath: String = "",
    bubbleCornerRadiusOverride: Float? = null,
    bubbleMaskColor: Color = Color.Unspecified,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalOrangeIslandHaptics.current
    var showMenu by remember { mutableStateOf(false) }

    val effectiveShape = bubbleCornerRadiusOverride?.let {
        androidx.compose.foundation.shape.RoundedCornerShape(it.dp)
    } ?: shape

    Column(horizontalAlignment = Alignment.End) {
        Surface(
            shape = effectiveShape,
            color = if (bubbleBackgroundImagePath.isNotBlank()) Color.Transparent else backgroundColor,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(contextAlpha)
                .then(if (shouldAnimate) Modifier.animateContentSize(animationSpec = tween(500)) else Modifier)
        ) {
            if (bubbleBackgroundImagePath.isNotBlank()) {
                Box {
                    coil.compose.AsyncImage(
                        model = bubbleBackgroundImagePath,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    // Configurable scrim -- color follows the user's custom bubble color setting,
                    // opacity follows Settings > 自定义颜色 > 用户气泡遮罩透明度.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(bubbleMaskColor)
                    )
                    UserMessageBubbleContent(
                        message = message,
                        textColor = textColor,
                        isEditing = isEditing,
                        isLoading = isLoading,
                        onEdit = onEdit,
                        onCancelEdit = onCancelEdit,
                        onMediaClick = onMediaClick,
                        onFileContentClick = onFileContentClick,
                        onPdfPagesClick = onPdfPagesClick,
                    )
                }
            } else {
                UserMessageBubbleContent(
                    message = message,
                    textColor = textColor,
                    isEditing = isEditing,
                    isLoading = isLoading,
                    onEdit = onEdit,
                    onCancelEdit = onCancelEdit,
                    onMediaClick = onMediaClick,
                    onFileContentClick = onFileContentClick,
                    onPdfPagesClick = onPdfPagesClick,
                )
            }
        }

        if (totalBranches > 1 && !isEditing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .then(contextAlpha)
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = { onSwitchBranch(-1) }, enabled = branchIndex > 0 && isEditingAllowed, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier = Modifier.size(16.dp))
                }
                Text("${branchIndex + 1} / $totalBranches", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { onSwitchBranch(1) }, enabled = branchIndex < totalBranches - 1 && isEditingAllowed, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (!isEditing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.then(contextAlpha)
            ) {
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(message.text)); haptics.success() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                IconButton(onClick = { onStartEdit() }, enabled = isEditingAllowed, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp), tint = LocalContentColor.current.copy(alpha = if (isEditingAllowed) 0.6f else 0.3f))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    DropdownMenu(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 16.dp,
                        shape = RoundedCornerShape(12.dp),
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.info)) },
                            onClick = { showMenu = false; onShowInfo() },
                            leadingIcon = { Icon(Icons.Default.Info, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = if (!isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) },
                            onClick = { showMenu = false; onShowDelete() },
                            enabled = !isLoading,
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = if (!isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The inner content of [UserMessageBubble]: inline editor or attachment thumbnails + text.
 * Extracted verbatim so the bubble can be rendered once over a photo background and once plain.
 */
@Composable
private fun UserMessageBubbleContent(
    message: ChatMessage,
    textColor: Color,
    isEditing: Boolean,
    isLoading: Boolean,
    onEdit: (String, String) -> Unit,
    onCancelEdit: () -> Unit,
    onMediaClick: (List<String>, Int) -> Unit,
    onFileContentClick: ((fileName: String, content: String) -> Unit)?,
    onPdfPagesClick: ((pages: List<String>, startIndex: Int) -> Unit)?,
) {
    if (isEditing) {
        val editState = rememberTextFieldState(message.text)
        val editScrollState = rememberScrollState()
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.noOpBringIntoView()) {
                TextField(
                    state = editState,
                    scrollState = editScrollState,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onCancelEdit() }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { onEdit(message.id, editState.text.toString()) }, enabled = !isLoading) { Text(stringResource(R.string.send)) }
            }
        }
    } else {
        Column(
            modifier = Modifier.padding(16.dp).noOpBringIntoView(),
            horizontalAlignment = Alignment.Start
        ) {
            val hasMetaItems = message.attachmentMeta?.items?.isNotEmpty() == true
            if (message.images.isNotEmpty() || hasMetaItems) {
                val ctx = LocalContext.current
                val meta = remember(message.attachmentMeta) {
                    message.attachmentMeta
                }
                // Build display items: skip non-first video/PDF frames, add meta-only items
                val displayItems = remember(message.images, meta) {
                    val skipIndices = mutableSetOf<Int>()
                    if (meta != null) {
                        for (item in meta.items) {
                            val count = item.pageCount ?: 1
                            if (item.imageIndex != null && count > 1 && (item.type == "video" || item.type == "pdf")) {
                                for (i in item.imageIndex + 1 until item.imageIndex + count) {
                                    skipIndices.add(i)
                                }
                            }
                        }
                    }
                    // Image-backed items
                    val imageItems = message.images.mapIndexedNotNull { index, path ->
                        if (index in skipIndices) null
                        else {
                            val item = findMetaForIndex(meta, index)
                            Triple(index, path, item)
                        }
                    }
                    // Meta-only items (file/PDF without image representation)
                    val metaOnlyItems = meta?.items
                        ?.filter { it.imageIndex == null && (it.type == "file" || it.type == "pdf" || it.type == "image") }
                        ?.map { Triple(-1, "", it) }
                        ?: emptyList()
                    imageItems + metaOnlyItems
                }

                // Collect all image/video URLs for the pager
                val allMediaUrls = remember(displayItems) {
                    displayItems.mapNotNull { (_, imagePath, metaItem) ->
                        val t = resolveAttachmentType(imagePath, metaItem, ctx)
                        when (t) {
                            "image" -> if (imagePath.isNotEmpty()) imagePath else null
                            "video" -> metaItem?.originalUri
                            else -> null
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.padding(bottom = if (message.text.isNotEmpty()) 8.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(displayItems) { itemIdx, (index, imagePath, metaItem) ->
                        val type = remember(imagePath, metaItem?.type) {
                            resolveAttachmentType(imagePath, metaItem, ctx)
                        }
                        val isVideo = type == "video"
                        val isPdf = type == "pdf"
                        val isFileType = type == "file"

                        val fileName = metaItem?.fileName ?: imagePath.substringAfterLast("/")
                        val pdfPages = if (type == "pdf") {
                            metaItem?.imageIndex?.let { start ->
                                val count = metaItem.pageCount ?: 1
                                val end = (start + count).coerceAtMost(message.images.size)
                                if (start in 0 until message.images.size) message.images.subList(start, end) else emptyList()
                            } ?: emptyList()
                        } else emptyList()

                        val mediaIndex = allMediaUrls.indexOf(
                            when (type) {
                                "video" -> metaItem?.originalUri
                                else -> imagePath
                            }
                        ).coerceAtLeast(0)

                        AttachmentThumbnailItem(
                            type = type,
                            imagePath = imagePath,
                            fileName = fileName,
                            originalUri = metaItem?.originalUri,
                            textContent = metaItem?.textContent,
                            pdfPages = pdfPages,
                            allMediaUrls = allMediaUrls,
                            mediaIndex = mediaIndex,
                            handlers = ThumbnailClickHandlers(
                                onMediaClick = onMediaClick,
                                onFileClick = onFileContentClick,
                                onPdfClick = onPdfPagesClick
                            )
                        )
                        if (type == "pdf" && metaItem?.warning != null) {
                            Text(metaItem.warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (message.text.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = ChatType.userBody,
                        color = textColor
                    )
                }
            }
        }
    }
}
