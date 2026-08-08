package com.orangeisland.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 一条便签。AI 可以通过工具增删改；桌面便签小组件每次开屏随机展示一句。
 *
 * 持久化方式与 [AnniversaryEntry] 一致：序列化为 JSON 列表，存在 DataStore
 * 的 `sticky_notes_json` 键里（见 [com.orangeisland.app.data.SettingsManager]）。
 *
 * @param id 稳定主键，新增时自动生成。
 * @param title 标题，可空。
 * @param content 正文。
 * @param color 配色标签（保留字段，小组件暂不消费）。
 * @param createdAt 创建时间（epoch 毫秒）。
 * @param updatedAt 最近修改时间。
 */
@Serializable
data class StickyNoteEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val color: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
