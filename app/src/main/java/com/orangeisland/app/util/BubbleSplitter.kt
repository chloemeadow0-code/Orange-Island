package com.orangeisland.app.util

/** 围栏代码块 (```...```)，内部换行必须原样保留，不能当作气泡边界。 */
private val FENCED_CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```")

/**
 * 找出文本中"不可在其内部拆分气泡"的字符区间：
 * 1. 围栏代码块 —— 内部换行是代码的一部分，拆开代码就碎了
 * 2. Markdown 表格 —— 连续 2 行及以上、每行都形如 "|...|" 的区块，拆开表格就散架了
 */
private fun findProtectedRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    FENCED_CODE_BLOCK_REGEX.findAll(text).forEach { ranges.add(it.range) }

    fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length > 1 && trimmed.startsWith("|") && trimmed.endsWith("|")
    }

    val lines = text.lines()
    var lineStartOffset = 0
    var tableStartLineIndex = -1
    var tableStartOffset = 0

    lines.forEachIndexed { index, line ->
        if (isTableRow(line)) {
            if (tableStartLineIndex == -1) {
                tableStartLineIndex = index
                tableStartOffset = lineStartOffset
            }
        } else {
            if (tableStartLineIndex != -1 && index - tableStartLineIndex >= 2) {
                ranges.add(tableStartOffset until (lineStartOffset - 1).coerceAtLeast(tableStartOffset))
            }
            tableStartLineIndex = -1
        }
        lineStartOffset += line.length + 1
    }
    if (tableStartLineIndex != -1 && lines.size - tableStartLineIndex >= 2) {
        ranges.add(tableStartOffset until text.length)
    }
    return ranges
}

/**
 * 按模型自己写的换行符 (\n) 把一条助手回复拆分成多个"气泡"片段。
 * 拆分边界 100% 由模型输出的换行决定；唯一的保护是围栏代码块和 Markdown 表格
 * 内部的换行不会被当作气泡边界。拆分后每段 trim 首尾空白，纯空白片段被丢弃；
 * 结果为空时回退为整段原文作为唯一片段。
 */
fun String.splitIntoBubbleSegments(): List<String> {
    if (isBlank()) return listOf(this)

    val protectedRanges = findProtectedRanges(this)
    fun isProtectedIndex(index: Int) = protectedRanges.any { index in it }

    val segments = mutableListOf<String>()
    val current = StringBuilder()
    for (index in indices) {
        val ch = this[index]
        if (ch == '\n' && !isProtectedIndex(index)) {
            segments.add(current.toString())
            current.clear()
        } else {
            current.append(ch)
        }
    }
    segments.add(current.toString())

    val result = segments.map { it.trim('\r').trim() }.filter { it.isNotBlank() }
    return result.ifEmpty { listOf(this.trim()) }
}
