package com.orangeisland.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

/**
 * Minimal reproducer for GFM table rendering using the *exact* libraries and
 * configuration that Orange Island uses in production:
 * - com.mikepenz:multiplatform-markdown-renderer-m3:0.41.0
 * - org.jetbrains:markdown:0.7.5
 * - GFMFlavourDescriptor as the parser flavour
 *
 * This preview deliberately bypasses **all** Orange Island custom logic
 * (streaming blocks, LaTeX pre-processing, RecomposeSafeMarkdown,
 * MessageItemMarkdown escape rules, etc.) so we can tell whether a table
 * failure is an upstream library bug or something introduced by our own code.
 *
 * If this preview renders a proper table → the bug is in Orange Island’s
 * message-rendering pipeline and should be debugged there.
 * If this preview still shows plain text / broken layout → the bug is in
 * the markdown renderer / parser itself and needs an upstream fix.
 */
private const val TABLE_REPRO_TEXT = """
| # | 工具 | 功能 | 状态 | 说明 |
|---|------|------|------|------|
| 1 | **search_notion** | 搜索页面/数据库 | ✅ | 搜到了你的 6 个数据库，包括日记本、黑历史大全等 |
| 2 | **get_page** | 获取页面属性 | ✅ | 成功读取到刚创建页面的标题、心情、标签等属性 |
"""

@Preview(showBackground = true)
@Composable
fun MarkdownTableReproPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Markdown(
            content = TABLE_REPRO_TEXT.trimIndent(),
            flavour = GFMFlavourDescriptor(),
            colors = markdownColor(),
            typography = markdownTypography(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
