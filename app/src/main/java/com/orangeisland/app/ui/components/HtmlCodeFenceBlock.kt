package com.orangeisland.app.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

/**
 * Full-featured wrapper around [HtmlCodePreview]: language label, copy, share, toggle
 * preview/source, and fullscreen -- feature parity with the plain code block's action row,
 * just with a live-render option added for html/svg.
 */
@Composable
fun HtmlCodeFenceBlock(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
) {
    var previewMode by remember(code) { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = language,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                val iconSize = 16.dp

                IconButton(onClick = { clipboardManager.setText(AnnotatedString(code)) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = iconTint, modifier = Modifier.size(iconSize))
                }
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, code)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = iconTint, modifier = Modifier.size(iconSize))
                }
                IconButton(onClick = { previewMode = !previewMode }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (previewMode) Icons.Default.Code else Icons.Default.Visibility,
                        contentDescription = if (previewMode) "View source" else "Preview",
                        tint = iconTint,
                        modifier = Modifier.size(iconSize),
                    )
                }
                if (previewMode) {
                    IconButton(onClick = { fullscreen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = iconTint, modifier = Modifier.size(iconSize))
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            if (previewMode) {
                HtmlCodePreview(
                    html = code,
                    language = language,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            } else {
                SelectionContainer {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                HtmlCodePreview(
                    html = code,
                    language = language,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.shapes.small),
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "Close")
                }
            }
        }
    }
}
