# 共读插件（ReadTogether）设计方案

> 目标：在橘子岛（Orange Island / Agora）内实现一个可导入 txt / markdown 的沉浸式共读插件，边看书边与 AI 实时讨论，翻页即同步上下文，并针对长文本做上下文压缩。

---

## 1. 产品定位与一句话描述

**共读 = 电子书阅读器 + 会话式 AI 副驾驶。**  
用户导入一本书后，应用自动分页；右侧（或底部）出现 AI 聊天面板。AI 始终“读”到用户当前页，能针对本页内容、前文伏笔、全书脉络进行问答。长书自动压缩进上下文窗口，避免本地小模型爆窗。

---

## 2. 与现有架构的集成点

橘子岛当前是 **MVVM + 手动 DI + Room + Compose** 架构。共读插件复用以下已有能力：

| 现有能力 | 复用方式 |
|---|---|
| `AppContainer` 手动 DI | 新增 `ReadTogetherManager`、`ReadTogetherRepository`、`ReadTogetherToolProvider` |
| `ChatDatabase` (Room v12) | 新增 4 张表，升级至 v13 |
| `LargeTextStore` | 书籍原始文本、页文本、压缩摘要都走外部存储 |
| `ToolProvider` / `ToolDispatcher` | 新增 `ReadTogetherToolProvider`，让 AI 可调“翻到某页 / 全书摘要 / 当前页引用” |
| `GenerationManager` | 共读聊天复用现有流式生成、工具循环、模型选择 |
| `ChatViewModel` | 在阅读室模式下，把当前页注入系统提示词 |
| `SettingsScreen` 分组 | 在“知识管理”或“工具”分组新增“共读”入口 |
| SAF / `ActivityResultContracts.OpenDocument` | 导入 txt / markdown |

---

## 3. 数据模型设计（Room）

新增 4 张表，全部外键约束到 `conversations`（方便与现有聊天体系打通）。

```kotlin
@Entity(tableName = "readtogether_books")
data class ReadTogetherBookEntity(
    @PrimaryKey val id: String,                    // UUID
    val conversationId: String,                    // 绑定一个专属聊天
    val title: String,
    val author: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val sourceUri: String? = null,                 // 原始 SAF uri（仅记录）
    val rawTextPointer: String,                    // LargeTextStore key
    val totalPages: Int,
    val currentPage: Int = 0,                      // 0-based，用户最后停留
    val compressionLevel: Int = 0,                 // 0=原书未压缩，1=摘要，2=章节摘要
    val bookSummaryPointer: String? = null,        // 全书摘要
    val tocJson: String? = null                    // 目录结构 JSON
)

@Entity(
    tableName = "readtogether_pages",
    foreignKeys = [ForeignKey(
        entity = ReadTogetherBookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookId", "pageNumber"], unique = true)]
)
data class ReadTogetherPageEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageNumber: Int,                           // 0-based
    val startChar: Int,
    val endChar: Int,
    val textPointer: String,                       // LargeTextStore key（本页纯文本）
    val headingPathJson: String? = null,           // ["第一章","第一节"] 层级路径
    val chapterSummaryPointer: String? = null      // 本章摘要（用于压缩）
)

@Entity(
    tableName = "readtogether_notes",
    foreignKeys = [ForeignKey(
        entity = ReadTogetherBookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReadTogetherNoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageNumber: Int,
    val quoteText: String? = null,                 // 划线内容
    val noteText: String? = null,                  // 用户批注
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "readtogether_chat_context",
    foreignKeys = [ForeignKey(
        entity = ReadTogetherBookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReadTogetherChatContextEntity(
    @PrimaryKey val bookId: String,                // 与 book 1:1
    val systemPromptPointer: String? = null,       // 当前生效的系统提示词（含页上下文）
    val lastInjectedPage: Int = -1,                // 上次注入的页码
    val precedingSummaryPointer: String? = null,   // 前文滚动摘要
    val updatedAt: Long = System.currentTimeMillis()
)
```

### 3.1 LargeTextStore 复用

书籍、页、摘要都是大文本，全部复用 `LargeTextStore.encode/decode`，不在 Room 里直接存大字段。

---

## 4. 自动分页算法

### 4.1 目标

- 导入 txt / markdown 后自动拆页。
- 页大小可配置（默认 1200 字符 / 页，约等于手机屏 2~3 屏）。
- 分页边界优先停在段落、空行、标题后，避免句子被拦腰截断。
- 支持动态重排：用户改字体/行距后，页码可重新映射，但划线笔记需通过 `startChar`/`endChar` 锚定到原文。

### 4.2 算法流程

```kotlin
fun paginate(raw: String, pageTarget: Int = 1200): List<PageBoundary> {
    // 1. 把 markdown 转为带结构的纯文本 + 标题索引
    val (plain, headings) = MarkdownParser.toPlainTextWithHeadings(raw)

    // 2. 按自然段落切分为 chunks（段落优先，长段落按句子再切）
    val chunks = plain.splitParagraphs(minChunk = 80, maxChunk = pageTarget)

    // 3. 贪心装箱：优先装满 pageTarget，回退到段落边界
    val pages = mutableListOf<PageBoundary>()
    var currentStart = 0
    var currentLen = 0
    val currentChunks = mutableListOf<TextChunk>()

    for (chunk in chunks) {
        if (currentLen + chunk.length > pageTarget && currentChunks.isNotEmpty()) {
            pages.add(PageBoundary(currentStart, currentStart + currentLen, currentChunks.toList()))
            currentStart += currentLen
            currentLen = 0
            currentChunks.clear()
        }
        currentChunks.add(chunk)
        currentLen += chunk.length
    }
    if (currentChunks.isNotEmpty()) {
        pages.add(PageBoundary(currentStart, currentStart + currentLen, currentChunks.toList()))
    }
    return pages
}
```

### 4.3 更精准的分页（可选高级模式）

如果希望“一页刚好一屏”，可用 Compose `SubcomposeLayout` / `TextMeasurer` 在真实渲染尺寸下测量文本高度，按可见高度分页。该模式较慢，作为高级开关。

### 4.4 Markdown 解析

- 标题保留为目录（`#` → 一级，`##` → 二级）。
- 代码块、列表、引用仅保留可阅读文本（不需要渲染样式到阅读器）。
- 图片链接替换为占位符 `[图片: 描述]`。

---

## 5. 上下文压缩方案（核心）

本地 GGUF 模型上下文通常 4K~8K；上传书籍动辄几十万字。必须分层压缩。

### 5.1 三层上下文塔

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: 当前页原文（固定，~1200 字符）                    │
│  Layer 2: 当前章节摘要（若章节长，再压缩）                    │
│  Layer 3: 全书目录 + 每章一句话摘要                         │
│  Layer 4: 近期聊天历史（按 token 占用动态截断）             │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 压缩触发策略

| 条件 | 动作 |
|---|---|
| 单页原文 < 可用上下文 10% | 当前页全文注入 |
| 单章 < 可用上下文 30% | 当前章全文 + 其他章摘要 |
| 全书 < 可用上下文 60% | 全书全文（小书直接全塞） |
| 否则 | 当前页全文 + 当前章摘要 + 全书目录/摘要 + 前文滚动摘要 |

### 5.3 前文滚动摘要（Rolling Summary）

用户从第 1 页翻到第 10 页时，把 1~9 页的内容逐步压缩成一段“前文摘要”，每翻 5~10 页用本地模型或云端模型刷新一次：

```
Prompt: 请用 300 字概括以下章节已读部分，保留关键人物、事件、伏笔：
{上一段摘要 + 新读页面原文}
```

该摘要保存在 `ReadTogetherChatContextEntity.precedingSummaryPointer`。

### 5.4 全书摘要生成

导入后后台任务生成：
1. 先按章节拆分；
2. 每章单独 summarization（本地小模型或用户当前模型）；
3. 再把章节摘要汇总成全书摘要。

生成过程用 WorkManager，失败可重试，UI 显示“AI 正在读这本书…”。

### 5.5 摘要提示词模板

```
你是这本书的阅读伴侣。用户正在阅读《{title}》。

【当前页】（第 {page+1}/{total} 页）
{currentPageText}

【当前章摘要】
{chapterSummary}

【全书脉络】
{bookSummary}

【已读前文摘要】
{precedingSummary}

请基于以上信息回答用户问题。需要引用原文时请标注“第 X 页”。
如果用户没有明确问题，可以主动总结本页要点、解释难点、提问引发思考。
```

---

## 6. AI 实时阅读聊天设计

### 6.1 聊天绑定

- 每本书对应一个 `conversationId`。
- 打开“阅读室”时，复用现有 `ChatViewModel` 的生成能力，但系统提示词动态替换为“当前页上下文”。
- 聊天历史就是该 conversation 的 messages。

### 6.2 翻页即同步

用户翻到第 N 页时：
1. 更新 `ReadTogetherBookEntity.currentPage`。
2. 重新构建 system prompt：当前页 + 压缩上下文。
3. 把 system prompt 写入当前 conversation 的 `systemPromptId` 指向的 prompt，或在生成时临时注入到第一条 system 消息。
4. 如果用户开启了“AI 主动讲解”，则自动触发一次模型调用，输出本页导读。

### 6.3 两种模式

| 模式 | 行为 |
|---|---|
| **被动问答** | 用户翻页后只更新上下文，不自动发消息；用户提问时 AI 基于当前页回答 |
| **主动导读** | 每翻到一页，AI 自动发一条简短消息（要点 / 提问），用户可回复 |

### 6.4 让 AI 能“翻书”的工具

新增 `ReadTogetherToolProvider`，暴露 3 个函数给模型：

```json
{
  "name": "readtogether_get_current_page",
  "description": "Get the text of the current page the user is reading."
}
{
  "name": "readtogether_get_page",
  "description": "Get the text of a specific page. Use when user refers to a page number.",
  "parameters": { "pageNumber": { "type": "integer" } }
}
{
  "name": "readtogether_search_book",
  "description": "Search the book for a keyword and return relevant snippets.",
  "parameters": { "query": { "type": "string" }, "limit": { "type": "integer" } }
}
{
  "name": "readtogether_get_summary",
  "description": "Get chapter-level or whole-book summary.",
  "parameters": { "scope": { "enum": ["chapter", "book"] } }
}
```

这样 AI 可以主动“翻回去查前文”，避免一次性塞太多上下文。

---

## 7. 阅读室 UI 设计

### 7.1 入口

- 主界面 Drawer / 工具栏新增“共读”入口。
- 设置页面“知识管理”分组新增“共读插件”。
- 导入流程：按钮 → SAF 选择 `.txt` / `.md` → 解析 → 自动分页 → 进入阅读室。

### 7.2 阅读室布局

手机默认上下分屏；平板/折叠屏/横屏左右分屏。

```
┌─────────────────────────────────────────────┐
│  TopBar: 书名  页码  设置  关闭              │
├─────────────────────────────────────────────┤
│                                             │
│  ┌─────────────────┐  ┌─────────────────┐  │
│  │                 │  │ AI: 本章开头... │  │
│  │   阅读区        │  │                 │  │
│  │   (Pager)       │  │   聊天区        │  │
│  │                 │  │   (LazyColumn)  │  │
│  │  左右滑翻页      │  │                 │  │
│  │                 │  │  ┌─────────────┐  │  │
│  │                 │  │  │ 输入框      │  │  │
│  └─────────────────┘  └─────────────────┘  │
│                                             │
└─────────────────────────────────────────────┘
```

### 7.3 阅读区

- `HorizontalPager` 翻页，配合 `detectHorizontalDragGestures` 或 Pager 自带手势。
- 支持字号、行距、夜间模式（跟随系统或独立开关）。
- 长按选词 → 划线 / 添加笔记。
- 底部显示页码进度条。

### 7.4 聊天区

- 复用现有 `MessageList` + `ChatBottomBar`，但隐藏高级设置按钮（或保留但限制模型选择）。
- 消息气泡中若 AI 引用了“第 X 页”，可点击跳转。

### 7.5 组件拆分建议

```
ui/readtogether/
├── ReadTogetherRoomScreen.kt      // 阅读室主屏
├── BookReaderPanel.kt             // 左侧/上方阅读区
├── ReadTogetherChatPanel.kt       // 右侧/下方聊天区
├── BookImportSheet.kt             // 导入进度 + 书架列表
├── PageIndicator.kt               // 页码与进度
├── BookNoteDialog.kt              // 划线笔记弹窗
└── ReadTogetherViewModel.kt       // 阅读室状态机
```

---

## 8. 业务层设计

### 8.1 Repository

```kotlin
class ReadTogetherRepository(
    private val dao: ReadTogetherDao,
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun importBook(uri: Uri, conversationId: String, title: String?): ReadTogetherBook
    fun observeBooks(): Flow<List<ReadTogetherBookEntity>>
    fun observeBook(bookId: String): Flow<ReadTogetherBookEntity?>
    suspend fun getPage(bookId: String, pageNumber: Int): ReadTogetherPageEntity?
    suspend fun updateCurrentPage(bookId: String, pageNumber: Int)
    suspend fun saveNote(note: ReadTogetherNoteEntity)
    suspend fun buildSystemPrompt(bookId: String, pageNumber: Int): String
    suspend fun ensureSummaries(bookId: String)
}
```

### 8.2 Manager

`ReadTogetherManager` 负责导入、分页、摘要生成、压缩策略协调。注册到 `AppContainer`。

### 8.3 ViewModel

`ReadTogetherViewModel`：
- 持有当前书、当前页、页面内容、聊天历史。
- 翻页时调用 `buildSystemPrompt` 并通知 `ChatViewModel` 更新系统提示。
- 处理用户划线、笔记、导入。

---

## 9. 文件导入流程

```
用户点击导入
    ↓
SAF 选文件 (.txt / .md)
    ↓
读取 InputStream → String
    ↓
LargeTextStore 保存 rawText
    ↓
MarkdownParser 解析结构（标题/段落）
    ↓
paginate() 生成 PageBoundary 列表
    ↓
为每页保存 ReadTogetherPageEntity（textPointer）
    ↓
创建 conversation（title = 书名）
    ↓
创建 ReadTogetherBookEntity
    ↓
后台 WorkManager 启动 SummarizeWorker
    ↓
进入 ReadTogetherRoomScreen
```

---

## 10. 安全与权限

| 事项 | 方案 |
|---|---|
| 文件读取 | `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` 已过时，全部走 SAF，无需运行时权限 |
| 大文件 | 导入时流式读取，避免 OOM；单文件上限 50MB（可配置） |
| 隐私 | 书籍内容只存本地，不上传；云端模型调用时才发送必要上下文 |

---

## 11. 字符串与主题规范

- 所有新增字符串按 `CLAUDE.md` 要求同步 11 种语言。
- 所有 UI 颜色从 `MaterialTheme.colorScheme` 取值。
- 图标使用 Material3 / `IslandIcons`。

---

## 12. 实现路线图

### Phase 1：MVP（可看的阅读器 + 简单 AI 聊天）
1. 新建 Room 表 + Migration v12 → v13。
2. `ReadTogetherRepository` + `ReadTogetherManager` + `ReadTogetherViewModel`。
3. SAF 导入 txt/md + `MarkdownParser` + 分页。
4. `ReadTogetherRoomScreen`：上下分屏、Pager 翻页、页码显示。
5. 聊天区复用现有 `ChatBottomBar` + `MessageList`。
6. 系统提示词注入：当前页全文 + 前一条用户消息。

### Phase 2：压缩与智能
1. 章节识别与摘要生成（WorkManager）。
2. 三层上下文塔 + 前文滚动摘要。
3. `ReadTogetherToolProvider`：让 AI 可调“翻页 / 搜索 / 摘要”。
4. AI 主动导读模式开关。

### Phase 3：增强体验
1. 书架管理（列表 / 最近阅读 / 删除）。
2. 划线笔记与导出。
3. 真实渲染尺寸精准分页。
4. 阅读字体/行距/主题独立设置页。
5. 搜索全书、跳转到搜索结果页。

---

## 13. 待确认问题

1. **模型调用走本地还是云端？** 建议默认使用用户当前选中的 provider（与主聊天一致），摘要生成可选本地小模型。
2. **分页单位用字符数还是真实渲染？** 第一阶段用字符数，第二阶段增加真实渲染分页。
3. **是否要做“阅读室”独立 Activity 还是嵌在主 NavHost？** 建议独立 `ReadTogetherRoomScreen` 作为全屏 Composable，由 `MainActivity` 承载，避免 Activity 跳转割裂感。
4. **书籍是否共享同一个 conversation？** 建议每本书一个 conversation，便于历史隔离与导出。
5. **是否允许 AI 调用“翻页”工具？** 建议先只让 AI 查询页内容，翻页动作由用户主导，避免体验混乱。

---

## 14. 关键文件清单（未来落地时）

```
app/src/main/java/com/orangeisland/app/
├── data/local/
│   ├── ReadTogetherEntities.kt          // 4 个 Room 实体
│   ├── ReadTogetherDao.kt                 // DAO
│   └── ChatDatabase.kt                    // 修改：新增表 + Migration
├── data/repository/
│   └── ReadTogetherRepository.kt
├── readtogether/
│   ├── ReadTogetherManager.kt
│   ├── MarkdownParser.kt
│   ├── BookPaginator.kt
│   ├── SummarizationWorker.kt
│   └── model/*.kt
├── tool/
│   └── ReadTogetherToolProvider.kt
├── ui/readtogether/
│   ├── ReadTogetherRoomScreen.kt
│   ├── BookReaderPanel.kt
│   ├── ReadTogetherChatPanel.kt
│   ├── BookImportSheet.kt
│   ├── PageIndicator.kt
│   ├── BookNoteDialog.kt
│   └── ReadTogetherViewModel.kt
└── di/AppContainer.kt                      // 注册依赖

app/src/main/res/values(-*)/strings.xml     // 新增 key
```

---

## 15. 总结

该方案把“共读插件”拆成 **导入 → 解析 → 分页 → 存储 → 压缩 → 注入 → 聊天** 六个标准环节，完全贴合橘子岛现有架构。实现后可作为内置功能，也可以进一步抽象为“长文档 RAG + 交互阅读”的通用能力，未来扩展到 PDF、EPUB 也很容易。
