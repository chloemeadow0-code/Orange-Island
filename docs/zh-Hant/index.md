# 橘子岛 使用手冊

歡迎來到 橘子岛 使用手冊。橘子岛 是一款 Android 的 BYOK（自帶金鑰）LLM 客戶端，支援多提供者存取、非線性分支對話、代理工具呼叫以及遠端裝置控制。

## 快速連結

### 入門指南

- **[入門指南](getting-started.md)** — 安裝、設定並傳送您的第一則訊息
- **[常見問題](faq.md)** — 常見問題解答

### 核心功能

- **[對話](conversations.md)** — 非線性分支、訊息操作、串流輸出、Markdown 渲染
- **[API 提供者](provider.md)** — 連接 OpenAI、Anthropic、Google、DeepSeek、Ollama 及自訂端點
- **[模型](models.md)** — 啟用/停用模型、別名、各提供者模型同步
- **[系統提示](system-prompts.md)** — 三段式編輯器、變數替換、各對話切換
- **[生成](generation.md)** — 溫度、top P、最大 token 數、思考模式、頻率/存在懲罰
- **[標題生成](title-generation.md)** — 自動生成對話標題
- **[圖片轉錄](transcription.md)** — 為不支援視覺的提供者提供的圖片轉文字管線
- **[圖片生成](image-generation.md)** — 作為聊天工具的文字轉圖片生成
- **[外觀](appearance.md)** — 主題模式、色彩方案、動態色彩、方案風格

### 代理工具

- **[總覽](tools.md)** — 多輪工具呼叫的運作方式
- **[網頁搜尋](web-search.md)** — DuckDuckGo Lite、Brave、Serper、Tavily、SearXNG 整合
- **[遠端 Shell (Conch)](shell.md)** — 加密遠端指令執行、檔案操作、MCP 整合
- **[沙箱](sandbox.md)** — 本地 Alpine Linux 環境，用於隔離的指令執行

### 知識管理

- **[對話搜尋](search.md)** — 關鍵字與語義 (RAG) 搜尋聊天記錄
- **[嵌入 / RAG](embedding.md)** — 設定用於語義檢索的嵌入模型
- **[記憶體與快取](memory.md)** — 活躍記憶、已儲存記憶、自動快取

### 更多

- **[本地模型](local-model.md)** — 透過 llama.cpp 在裝置上執行 GGUF 模型
- **[PDF 匯入](pdf-import.md)** — 擷取 PDF 頁面並傳送給視覺模型
- **[資料可攜性](import-export.md)** — 匯出/匯入 .橘子岛 檔案、自動備份、從 Claude 和 ChatGPT 匯入
- **[語言](language.md)** — 在 English、中文、繁體中文或系統預設值之間切換
- **[關於](about.md)** — 版本資訊、更新、文件開關、連結、評分

---

## 關於 橘子岛

橘子岛 是一款面向 AI 進階使用者的 BYOK Android 客戶端：

- **無中介層**：直接 API 連線，無遙測，無追蹤
- **裝置端儲存**：所有資料均存放於本地的 Room 資料庫中
- **非線性對話**：編輯任何過往訊息，探索替代分支
- **預設代理**：多輪工具呼叫，支援網頁搜尋、圖片生成、程式碼執行、Shell、檔案操作與記憶體
- **遠端控制**：透過加密的 Conch 協定管理伺服器
- **開放原始碼**：MIT 授權，[原始碼於 GitHub](https://github.com/chloemeadow0-code/Orange-Island)
