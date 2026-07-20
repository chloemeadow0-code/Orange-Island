package com.orangeisland.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeisland.app.R
import com.orangeisland.app.ui.theme.MonoFamily
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding

private fun isMarkdownFile(fileName: String): Boolean =
    fileName.endsWith(".md", true) || fileName.endsWith(".markdown", true)

@Composable
fun TextFileViewer(
    content: String,
    fileName: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onClose() }

    val isMarkdown = remember(fileName) { isMarkdownFile(fileName) }
    var showOverlay by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val t = MaterialTheme.typography
        val viewerTypography = markdownTypography(
            text = t.bodyLarge,
            h1 = t.headlineMedium,
            h2 = t.headlineSmall,
            h3 = t.titleLarge,
            h4 = t.titleMedium,
            h5 = t.titleSmall,
            h6 = t.titleSmall,
            code = t.bodyMedium.copy(fontFamily = MonoFamily, fontSize = 13.sp),
            inlineCode = t.bodyMedium.copy(fontFamily = MonoFamily, fontSize = 13.sp),
        )
        val viewerPadding = markdownPadding(block = 7.dp)

        // Content
        if (isMarkdown) {
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {}
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 96.dp, bottom = 56.dp)
                ) {
                    Markdown(content = content, modifier = Modifier.fillMaxWidth(), typography = viewerTypography, padding = viewerPadding)
                                    }
            }
        } else {
            val scrollState = rememberScrollState()
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {}
                        .verticalScroll(scrollState)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 96.dp, bottom = 56.dp)
                ) {
                    Text(
                        content,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                                    }
            }
        }

        // Top alpha gradient
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                        0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                        1.0f to Color.Transparent
                    ))
            )
        }

        // Top overlay — filename + close button, midline aligned
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(50)).widthIn(max = 320.dp)
                    ) {
                        Text(
                            fileName,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(48.dp).shadow(8.dp, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.provider_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp).padding(12.dp)
                    )
                }
            }
        }
    }
}
