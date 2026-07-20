# Embedding / RAG

Embedding 模型將文字轉換為能捕捉語意的數值向量。橘子岛 使用這些向量對您的對話記錄進行語義搜尋（RAG）— 根據訊息的意思來尋找，而不僅僅是它們包含的詞語。

## 運作方式

1. 每則訊息被傳送到一個 embedding 模型
2. 模型回傳一個向量（一組數字），代表該訊息的意思
3. 當您搜尋時，您的查詢也會被嵌入
4. 橘子岛 計算查詢向量與所有訊息向量之間的**餘弦相似度**
5. 相似度高於您設定閾值的訊息會作為匹配結果回傳

## 支援的提供者

| 提供者 | Base URL | 需要 API 金鑰 | 備註 |
|----------|----------|------------------|-------|
| **OpenAI** | `https://api.openai.com/v1` | 是 | `text-embedding-3-small`、`text-embedding-3-large` |
| **Mistral** | `https://api.mistral.ai/v1` | 是 | `mistral-embed` |
| **Voyage AI** | `https://api.voyageai.com/v1` | 是 | `voyage-3`、`voyage-3-lite` |
| **SiliconFlow** | `https://api.siliconflow.cn/v1` | 是 | `BAAI/bge-large-zh-v1.5`（中文最佳化） |
| **Ollama** | `http://localhost:11434/v1` | 否 | `qwen3-embedding`、`nomic-embed-text` 等 |
| **自訂** | 任意 | 選填 | 任何 OpenAI 相容的 embeddings 端點 |
| **本機** | N/A | 否 | 透過 llama.cpp 的 GGUF embedding 模型 |

---

## 新增 Embedding 模型

### 遠端 (API)

1. 前往 **設定 → 對話搜尋**
2. 點選 **新增遠端模型**
3. 設定：

| 欄位 | 說明 |
|-------|-------------|
| **提供者** | 從下拉選單中選擇（OpenAI、Mistral、Voyage、SiliconFlow、Ollama、自訂） |
| **模型名稱** | 確切的模型 ID（例如 `text-embedding-3-small`） |
| **Base URL** | 已知提供者會自動填入；可編輯以使用代理 |
| **API 金鑰** | 留空以從您的聊天提供者金鑰自動解析，或輸入專用金鑰 |
| **批次大小** | 每個 API 請求要嵌入的訊息數（1–100） |

4. 點選 **新增** — 儲存前會執行連線測試

!!! tip
    如果您已為聊天設定相同的提供者，API 金鑰欄位為選填。留空，橘子岛 會自動解析您的聊天 API 金鑰。

### 本機 (GGUF)

1. 前往 **設定 → 對話搜尋**
2. 點選 **新增本機模型**
3. 匯入一個 `.gguf` embedding 模型檔案（例如 `bge-small-en-v1.5-q4_k.gguf`）
4. 為其命名
5. 點選 **新增**

Embedding 模型通常比聊天模型小得多 — 最多幾百 MB。

### Ollama

1. 在機器上安裝 Ollama
2. 拉取一個 embedding 模型：`ollama pull qwen3-embedding:8b`
3. 在 橘子岛 中，新增一個遠端模型：
    - 提供者：**Ollama**
    - Base URL：`http://<host>:11434/v1`
    - 模型名稱：`qwen3-embedding:8b`（包含 `:tag`）
    - API 金鑰：留空
4. 點選 **新增**

!!! note
    Ollama 的後綴標籤如 `:8b`、`:latest` 是模型名稱的一部分。請使用 `ollama list` 中的確切名稱。

---

## 快取

新增模型後，您需要快取您的訊息（產生 embeddings）：

1. 在 embedding 模型上點選 **快取**
2. 橘子岛 以批次方式處理所有未快取的訊息
3. 圓形進度指示器顯示目前進度
4. 完成時："所有 N 則訊息已快取"

### 自動快取

啟用**自動快取**可在新訊息到達時自動嵌入它們。這能讓您的搜尋索引始終保持最新。

### 重新快取

點選**重新快取**以刪除所有現有 embeddings 並從頭重建。適用時機：

- 切換到不同的 embedding 模型
- Embedding 品質似乎下降
- 快取不一致

!!! warning
    重新快取無法復原，且對於大型訊息記錄可能需要很長時間。

---

## 批次大小

**批次大小**設定（1–100）控制在快取期間每個 API 請求傳送多少訊息：

- **較高**：更快的快取，但 API 酬載較大
- **較低**：較小的請求，速度較慢但在慢速連線上更可靠

從預設值開始，如果遇到逾時（調低）或想要更快的快取（調高），再進行調整。

---

## 測試您的設定

當您新增遠端模型時，橘子岛 會執行自動連線測試。如果失敗：

1. 檢查模型名稱 — Ollama 需包含標籤（`:8b`、`:latest`）
2. 確認 Base URL 可從您的裝置連線
3. 確認 API 金鑰有效（如有需要）
4. 嘗試該提供者的已知模型名稱

常見錯誤：
- **"模型名稱錯誤"** — 檢查確切拼寫，包括標籤
- **"Base URL 錯誤"** — 確保端點支援 `/v1/embeddings`
- **"缺少 API 金鑰"** — 某些提供者需要驗證
- **"網路錯誤"** — 檢查連線

---

## 提供者推薦

| 使用情境 | 推薦提供者 |
|----------|---------------------|
| **最佳品質（英文）** | Voyage AI `voyage-3` |
| **最佳品質（中文）** | SiliconFlow `BAAI/bge-large-zh-v1.5` |
| **免費 / 自託管** | Ollama `qwen3-embedding` 或 `nomic-embed-text` |
| **完全離線** | 本機 GGUF `bge-small-en-v1.5` |
| **已在使用 OpenAI** | OpenAI `text-embedding-3-small`（便宜、快速） |
