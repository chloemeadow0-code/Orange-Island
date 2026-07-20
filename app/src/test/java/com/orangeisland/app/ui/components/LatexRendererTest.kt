package com.orangeisland.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexRendererTest {

    @Test
    fun testFullParagraph() {
        // Raw text as received: 显存带宽 $BW = 672\ \mathrm{GB/s}$ 在 $p$ 值 $p < 0.01$
        val text = "显存带宽 \$BW = 672\\ \\mathrm{GB/s}\$ 在 \$p\$ 值 \$p < 0.01\$ 的显著性检验里确实落后了。"
        println("=== Full paragraph ===")
        val spans = parseLatexSpans(text)
        for ((i, s) in spans.withIndex()) {
            val tag = if (s.isLatex) if (s.display) "DISPLAY" else "INLINE" else "TEXT"
            println("  [$i] $tag: '${s.content.take(120)}'")
        }
        val latexSpans = spans.filter { it.isLatex }
        println("Latex count: ${latexSpans.size}")
        latexSpans.forEachIndexed { i, s -> println("  LaTeX[$i]: '${s.content}'") }
        assertEquals(3, spans.count { it.isLatex })
    }

    @Test
    fun testAllDollarCases() {
        val cases = listOf(
            "\$A\$" to true,
            "\$a = \\\$5\$" to true,
            "\$\\\$x\$" to true,
            "\$\\text{Price: }\\\$4.99\$" to true,
            "\$x = \\\$y = \\\$z\$" to true,
            "\$a \\\$ b\$" to true,
            "\$G = F + pV\$" to true,
            "\$p\$" to true,
            "\$p < 0.01\$" to true,
        )
        println("=== Dollar cases ===")
        for ((input, shouldBeLatex) in cases) {
            val spans = parseLatexSpans(input)
            val latexSpans = spans.filter { it.isLatex }
            val ok = if (shouldBeLatex) latexSpans.size == 1 else latexSpans.isEmpty()
            val status = if (ok) "PASS" else "FAIL"
            println("$status: '$input' -> $latexSpans")
            if (!ok && shouldBeLatex) {
                println("  All spans:")
                spans.forEachIndexed { i, s -> println("    [$i] latex=${s.isLatex} '${s.content}'") }
            }
        }
    }

    @Test
    fun testNoSpaceAfterClosingDollar() {
        // Model may output no space between closing $ and Chinese text
        val text = "显存带宽 \$BW = 672\\ \\mathrm{GB/s}在\$p\$值\$p < 0.01\$ 的显著性检验里确实落后了。"
        println("=== No space after closing $ ===")
        val spans = parseLatexSpans(text)
        spans.forEachIndexed { i, s ->
            val tag = if (s.isLatex) "LATEX" else "TEXT"
            println("  [$i] $tag: '${s.content.take(100)}'")
        }
        // Only $p < 0.01$ should be LaTeX — the rest has Chinese mixed in
        val latexSpans = spans.filter { it.isLatex }
        println("Latex count: ${latexSpans.size}")
        latexSpans.forEach { println("  -> '${it.content}'") }
        // Chinese chars outside braces should veto LaTeX even with \mathrm present
        assertTrue("Should not parse mixed Chinese+LaTeX as LaTeX",
            latexSpans.all { !it.content.contains("在") && !it.content.contains("值") })
    }

    @Test
    fun testDollarAmountNotLatex() {
        val cases = listOf(
            "这台工作站花了 \$4,200" to 0,    // bare dollar amount, no closing
            "预算 \$5,000" to 0,               // bare dollar amount, no closing
        )
        println("=== Dollar amount cases ===")
        for ((input, expectedLatexCount) in cases) {
            val spans = parseLatexSpans(input)
            val latexCount = spans.count { it.isLatex }
            val ok = latexCount == expectedLatexCount
            println("${if (ok) "PASS" else "FAIL"}: '$input' -> $latexCount latex spans (expected $expectedLatexCount)")
            if (!ok) spans.forEachIndexed { i, s -> println("  [$i] latex=${s.isLatex} '${s.content}'") }
        }
    }

    @Test
    fun testUserParagraph() {
        // Exact text from text.txt — uses en-dash (–), not adjacent $$
        val text = "价格区间 \$800–\$1,200 的消费卡和 \$3,000+ 的专业卡之间有一道诡异的真空带。" +
                   "二手市场上 Quadro RTX 6000 现在只要 \$600–\$800，但显存带宽 " +
                   "\$BW = 672\\ \\mathrm{GB/s}\$ 在 \$p\$ 值 \$p < 0.01\$ 的显著性检验里确实落后了。"
        println("=== User paragraph ===")
        val spans = parseLatexSpans(text)
        spans.forEachIndexed { i, s ->
            val tag = if (s.isLatex) if (s.display) "D" else "L" else "T"
            println("  [$i] $tag: '${s.content.take(100)}'")
        }
        val latexSpans = spans.filter { it.isLatex }
        println("Latex count: ${latexSpans.size}")
        latexSpans.forEachIndexed { i, s -> println("  LaTeX[$i]: '${s.content}'") }

        // Expected LaTeX formulas
        val expected = listOf("BW = 672\\ \\mathrm{GB/s}", "p", "p < 0.01")
        val actual = latexSpans.map { it.content }
        assertEquals(expected, actual)
    }

    @Test
    fun testInvalidDisplayCandidateDoesNotConsumeLaterDisplayMath() {
        val text = "标题 \$\$ 误触发\n\n\$\$\nD\n\$\$\n后续 \$x\$"
        val latexSpans = parseLatexSpans(text).filter { it.isLatex }

        assertEquals(listOf("D", "x"), latexSpans.map { it.content })
        assertEquals(listOf(true, false), latexSpans.map { it.display })
    }

    @Test
    fun testDisplayLatexMarkdownCarriesDisplayMode() {
        val display = latexToMarkdown("199", display = true)
        val inline = latexToMarkdown("x", display = false)

        assertTrue(display.startsWith("\n\n![latex](latex://display/"))
        assertTrue(display.endsWith(")\n\n"))
        assertTrue(inline.startsWith("![latex](latex://inline/"))
    }

    @Test
    fun testCodeFenceProtectsDollarMath() {
        val text = "```kotlin\nval price = \"\$5$\"\n```\noutside \$x\$"
        val spans = parseLatexSpans(text)
        val textContent = spans.filter { !it.isLatex }.joinToString("") { it.content }

        assertEquals(listOf("x"), spans.filter { it.isLatex }.map { it.content })
        assertTrue(textContent.contains("val price = \"\$5$\""))
    }

    @Test
    fun testUnclosedCodeFenceProtectsToEndOfText() {
        val text = "```kotlin\nval price = \"\$5$\"\noutside \$x\$"
        val spans = parseLatexSpans(text)
        val textContent = spans.joinToString("") { it.content }

        assertEquals(0, spans.count { it.isLatex })
        assertTrue(textContent.contains("outside \$x\$"))
    }

    @Test
    fun testUnclosedInlineCodeProtectsCurrentLine() {
        val text = "`echo \$HOME and \$x\$\noutside \$y\$"
        val spans = parseLatexSpans(text)
        val textContent = spans.filter { !it.isLatex }.joinToString("") { it.content }

        assertEquals(listOf("y"), spans.filter { it.isLatex }.map { it.content })
        assertTrue(textContent.contains("`echo \$HOME and \$x\$"))
    }

}
