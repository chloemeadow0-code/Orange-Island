# 常見問題

## API 與提供者

### 如何取得 API 金鑰？

- **Google Gemini**：[Google AI Studio](https://aistudio.google.com/apikey) — 提供免費額度
- **OpenAI**：[Platform API Keys](https://platform.openai.com/api-keys)
- **Anthropic**：[Console API Keys](https://console.anthropic.com/)
- **DeepSeek**：[Platform](https://platform.deepseek.com/)
- **OpenRouter**：[Keys 頁面](https://openrouter.ai/keys)
- **Brave Search**：[Brave Search API](https://api.search.brave.com/)

### 我可以為同一個提供者使用多組 API 金鑰嗎？

可以。每個提供者都支援多組具名金鑰。點選圓形按鈕來選擇使用中的金鑰。這對於在工作/個人金鑰之間輪換或準備備用金鑰非常實用。請參閱 [API 提供者](provider.md#api-keys)。

### 如何新增自訂提供者？

前往設定 → 提供者 → **+ 新增自訂提供者**。輸入名稱和基礎 URL。任何相容於 OpenAI 的端點都可以使用。請參閱 [自訂提供者](provider.md#custom-providers)。

---

## 本地模型

### 哪些 GGUF 模型可以運作？

橘子岛 支援用於聊天和嵌入的 GGUF 格式。聊天模型應能放入裝置記憶體（視 RAM 而定，約 1–8B 參數）。嵌入模型則小得多（100–500 MB）。請參閱 [本地模型](local-model.md)。

### 如何離線執行模型？

透過設定 → 提供者 → 本地 → **匯入 GGUF 模型** 來匯入 GGUF 聊天模型。若要完全離線進行語義搜尋，也請匯入一個 GGUF 嵌入模型。不需要網路連線。

### 為什麼我的本地模型這麼慢？

本地推論是在您裝置的 CPU 上執行的，本質上就比雲端 API 慢。建議：使用較小的模型（1–3B 參數）、較低的量化（Q4_K_M）、較短的上下文視窗，並關閉背景應用程式。

---

## 嵌入與搜尋

### 為什麼我的嵌入模型測試失敗？

常見原因：

- **模型名稱錯誤** — 檢查確切的拼寫，包含 Ollama 標籤（例如 `qwen3-embedding:8b` 而非 `qwen3-embedding`）
- **基礎 URL 錯誤** — 確保端點支援 `/v1/embeddings`
- **缺少 API 金鑰** — 某些提供者即使對嵌入也需要驗證
- **網路** — 檢查與端點的連線

### 關鍵字搜尋和 RAG 搜尋有什麼不同？

關鍵字搜尋比對確切的文字。RAG（語義搜尋）則按含義比對 — 「資料庫設定」可以找到「Room 設定」，即使沒有共用詞彙。RAG 需要嵌入模型和已快取的訊息。請參閱 [對話搜尋](search.md)。

### 如何使用 Ollama 進行嵌入？

1. 在機器上安裝 Ollama
2. 拉取嵌入模型：`ollama pull qwen3-embedding:8b`
3. 在 橘子岛 中，使用 **Ollama** 預設新增遠端嵌入模型
4. 使用 `http://<host>:11434/v1` 作為基礎 URL
5. 輸入確切的模型名稱，包含標籤（例如 `qwen3-embedding:8b`）
6. 將 API 金鑰留空

---

## 記憶體

### 活躍記憶和已儲存記憶有什麼不同？

**活躍記憶** 是每次 API 呼叫都會包含的單一持久上下文 — 模型始終會看見它。**已儲存記憶** 是一組具名檔案，模型可以依需求搜尋和擷取。使用活躍記憶來存放持久的事實；使用已儲存記憶來存放參考資料。請參閱 [記憶體與快取](memory.md)。

### 模型可以修改我的記憶嗎？

可以，若您在設定 → 記憶體中啟用 **存取已儲存記憶** 和/或 **存取活躍記憶**。模型可以透過工具呼叫來建立、讀取、編輯和刪除記憶。所有權限預設為關閉。

---

## Shell 與工具

### 如何設定遠端 Shell 存取？

在您的目標機器上部署 [Conch](https://github.com/newo-ether/conch) 伺服器，然後在設定 → Shell 中使用其 URL 和 API 金鑰新增裝置。請參閱 [遠端 Shell](shell.md)。

### 我可以不使用 API 金鑰來搜尋網頁嗎？

可以。**DuckDuckGo Lite** 是預設的網頁搜尋提供者，無需 API 金鑰。只需在設定 → 網頁搜尋中啟用網頁搜尋，即可立即使用。若需要更高的可靠性，請設定 API 型提供者（Brave、Serper、Tavily、SearXNG）。請參閱 [網頁搜尋](web-search.md)。

### Shell 連線是否加密？

是的。Conch 使用 ECDH 金鑰交換 + AES-256-GCM 加密 + HMAC-SHA256 簽章。橘子岛 與 Conch 伺服器之間的所有流量均為端對端加密。

---

## 資料

### 如何備份我的資料？

前往設定 → 資料控制 → **匯出資料** 以建立手動 `.橘子岛` 備份。如需無憂保護，請在設定 → 資料控制 → 自動備份中啟用**自動備份** — 它會在背景定期備份您的資料。請參閱 [資料可攜性](import-export.md)。

### 我可以從 ChatGPT 或 Claude 匯入嗎？

可以。從 ChatGPT 或 Claude 匯出您的資料（它們提供 `.zip` 檔案），然後在設定 → 資料控制 → **第三方** 中匯入。支援合併和取代兩種策略。請參閱 [資料可攜性](import-export.md#third-party-import)。

### 我的 API 金鑰是否包含在匯出中？

可以包含，但這是可選的。匯出畫面讓您可以切換是否包含 API 金鑰。啟用時會顯示警告。金鑰以純文字形式儲存在 `.橘子岛` 檔案中，因此僅在進行完整裝置遷移到受信任的目的地時才包含它們。

---

## 一般

### 我的資料儲存在哪裡？

所有資料都儲存在您 Android 裝置本地的 Room 資料庫中。橘子岛 沒有伺服器、沒有雲端同步、沒有遙測。訊息直接從您的裝置傳送到您設定的 AI 提供者。

### 橘子岛 支援多種語言嗎？

是的。應用程式 UI 支援 **英文**、**中文** 和 **繁體中文**。設定 → 語言。切換後需要重新啟動。

### 如何回報錯誤或請求功能？

在 [GitHub](https://github.com/chloemeadow0-code/Orange-Island/issues) 上開啟 Issue。如需貢獻，請參閱 README 的 [Contributing](https://github.com/chloemeadow0-code/Orange-Island#contributing) 章節。
