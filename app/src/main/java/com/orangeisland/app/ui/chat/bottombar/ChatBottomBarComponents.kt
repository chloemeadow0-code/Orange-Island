package com.orangeisland.app.ui.chat.bottombar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.orangeisland.app.ui.theme.ChatType

fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    color: Color,
    width: androidx.compose.ui.unit.Dp = 3.dp
): Modifier = drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0) {
        val viewPortHeight = size.height
        val totalHeight = scrollState.maxValue + viewPortHeight
        val thumbHeight = (viewPortHeight / totalHeight) * viewPortHeight
        val thumbOffset = (scrollState.value / totalHeight.toFloat()) * viewPortHeight
        drawRoundRect(color = color, topLeft = Offset(size.width - width.toPx() - 4.dp.toPx(), thumbOffset), size = Size(width.toPx(), thumbHeight), cornerRadius = CornerRadius(width.toPx() / 2))
    }
}

@Composable
internal fun ProviderBadge(provider: String) {
    val badgeColor = when (provider.lowercase()) {
        "google", "gemini" -> MaterialTheme.colorScheme.onPrimaryContainer
        "anthropic" -> Color(0xFFD97757)
        "openai" -> Color(0xFF74AA9C)
        else -> MaterialTheme.colorScheme.primary
    }
    val badgeBackground = when (provider.lowercase()) {
        "google", "gemini" -> MaterialTheme.colorScheme.primaryContainer
        else -> badgeColor.copy(alpha = 0.15f)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = badgeBackground
    ) {
        Text(
            provider,
            style = ChatType.micro,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
