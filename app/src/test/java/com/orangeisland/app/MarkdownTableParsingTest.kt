package com.orangeisland.app

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Test

/**
 * 纯 JUnit 测试（无需模拟器），直接验证底层 JetBrains markdown 解析器
 * (org.jetbrains:markdown:0.7.5) 对这段特定 GFM 表格输入的识别结果。
 *
 * 如果解析器没有把这段文本识别成 GFM_TABLE，说明问题出在解析层，
 * 渲染层（mikepenz/multiplatform-markdown-renderer）无论如何都救不回来；
 * 如果解析器正确识别了 TABLE，问题就在 Orange Island 应用层的某处预处理
 * 或流式渲染逻辑里。
 */
class MarkdownTableParsingTest {

    @Test
    fun `GFM table with hash header parses as table node`() {
        val text = """
            | # | 工具 | 功能 | 状态 | 说明 |
            |---|------|------|------|------|
            | 1 | **search_notion** | 搜索页面/数据库 | ✅ | 搜到了你的 6 个数据库，包括日记本、黑历史大全等 |
            | 2 | **get_page** | 获取页面属性 | ✅ | 成功读取到刚创建页面的标题、心情、标签等属性 |
        """.trimIndent()

        val flavour = GFMFlavourDescriptor()
        val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(text)

        // 递归打印整棵 AST，看解析器到底认出了什么结构
        fun dump(node: ASTNode, depth: Int) {
            println("  ".repeat(depth) + node.type.toString())
            node.children.forEach { dump(it, depth + 1) }
        }
        dump(tree, 0)

        // 在整棵 AST 里搜索有没有 GFM_TABLE 节点（它可能在深层，不是根的直接子节点）
        fun findTable(node: ASTNode): Boolean {
            if (node.type == GFMElementTypes.TABLE) return true
            return node.children.any { findTable(it) }
        }

        val hasTableNode = findTable(tree)
        assert(hasTableNode) {
            "解析结果里没有表格节点！说明 org.jetbrains:markdown 0.7.5 在这段" +
            "输入上确实没把它识别成 GFM 表格，问题在解析器这一层，不在渲染层。\n" +
            "实际 AST 输出见上面的 dump。"
        }
    }
}
