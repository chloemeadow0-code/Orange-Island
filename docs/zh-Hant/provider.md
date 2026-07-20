# API 提供者

橘子岛 直接連線到 AI 提供者 — 無中間人、無訂閱、無遙測。您帶上自己的 API 金鑰，一切從您的裝置執行。

## 內建提供者

| 提供者 | 基礎 URL | 模型 | 備註 |
|----------|----------|--------|-------|
| **Google** | `https://generativelanguage.googleapis.com/v1beta` | Gemini 系列 | 可透過 Google AI Studio 使用免費層級 |
| **OpenAI** | `https://api.openai.com/v1` | GPT-4、GPT-4o、o 系列 | 支援推理模型 |
| **Anthropic** | `https://api.anthropic.com/v1` | Claude 系列 | 支援延伸思考 |
| **DeepSeek** | `https://api.deepseek.com/v1` | DeepSeek-V3、DeepSeek-R1 | 支援推理模型 |
| **Qwen** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Qwen 系列 | 透過阿里雲 DashScope |
| **Ollama** | `http://localhost:11434/v1` | 任何已拉取的模型 | 自託管，無需 API 金鑰 |
| **OpenRouter** | `https://openrouter.ai/api/v1` | 多提供者 | 透過單一 API 存取多個模型 |
| **本機** | N/A | GGUF 模型 | 裝置端執行，透過 llama.cpp，完全離線 |

## 切換提供者

點選設定中的提供者選擇器來切換提供者。每個提供者維護自己的：

- API 金鑰
- 基礎 URL（可編輯，用於代理/自託管）
- 模型清單

---

## API 金鑰

### 每個提供者多個金鑰

每個提供者支援多個具名 API 金鑰。這可實現：

- **輪替** — 在不同使用層級間切換金鑰
- **組織** — 區分工作和個人使用
- **備援** — 保留備用金鑰

### 管理金鑰

1. 前往 **設定 → 提供者**
2. 選擇一個提供者
3. 在 **API 金鑰** 下，點選 **新增金鑰**
4. 輸入 **名稱**（例如「工作」、「個人」、「團隊共用」）和 **金鑰值**
5. 點選 **新增**

點選圓形按鈕來設定使用中的金鑰。長按金鑰可 **編輯** 或 **刪除**。

### 金鑰安全

!!! warning
    API 金鑰以加密方式儲存在本機的 Room 資料庫中。它們永遠不會被傳送到 橘子岛 伺服器（根本沒有伺服器）。但是，如果您將它們包含在 `.橘子岛` 匯出檔案中，它們會以純文字形式匯出。

---

## 自訂提供者

新增任何與 OpenAI 相容的 API 端點：

1. 前往 **設定 → 提供者**
2. 點選提供者清單底部的 **+ 新增自訂提供者**
3. 輸入：
    - **提供者名稱** — 任意顯示名稱
    - **基礎 URL** — API 端點
4. 點選 **新增**

橘子岛 會從 `{base_url}/v1/models` 取得模型清單。新增後，自訂提供者的運作方式與內建提供者完全相同：新增 API 金鑰、同步模型和聊天。

### 使用案例

- **自託管** — 連線到 vLLM、LocalAI、text-generation-webui 或其他與 OpenAI 相容的伺服器
- **代理** — 透過公司代理或 API 閘道路由
- **替代端點** — 使用 Azure OpenAI、Cloudflare AI Gateway 或其他相容服務

### 重新命名或刪除

長按自訂提供者可 **重新命名** 或 **刪除**。刪除會移除該提供者及其所有金鑰。

!!! warning
    內建提供者無法重新命名或刪除。

---

## 基礎 URL 覆寫

每個提供者（包括內建提供者）都有可編輯的 **基礎 URL**。這適用於：

- **代理**：透過 `https://my-proxy.example.com/v1` 路由
- **自託管**：指向您自己的執行實體
- **區域路由**：使用特定區域的端點

---

## 同步模型

新增 API 金鑰後，同步模型清單：

1. 前往 **設定 → 模型**
2. 點選 **從所有提供者同步**
3. 橘子岛 會從每個已設定的提供者取得可用模型

Snackbar 會顯示同步進度和結果。然後您可以啟用/停用個別模型並設定預設模型。

---

## 提供者特定說明

### Google Gemini

- API 金鑰來自 [Google AI Studio](https://aistudio.google.com/apikey)
- 提供免費層級（有速率限制）
- 支援程式碼執行和搜尋接地（內建工具）

### OpenAI

- API 金鑰來自 [Platform](https://platform.openai.com/api-keys)
- 推理模型（o1、o3）需要特定的 API 存取權限
- 支援串流、工具和視覺功能

### Anthropic

- API 金鑰來自 [Console](https://console.anthropic.com/)
- 延伸思考，可設定 token 預算
- 支援工具使用及並行呼叫

### Ollama

- 無需 API 金鑰（本機網路）
- 基礎 URL 通常為 `http://<host>:11434/v1`
- 模型清單從 Ollama 的 API 取得
- 請參閱 [FAQ](faq.md) 了解 Ollama 特定疑難排解

### OpenRouter

- 單一 API 金鑰存取 200+ 模型
- 按 token 付費的定價因模型而異
- 適合在沒有個別提供者帳號的情況下試用不同模型

### 本機（llama.cpp）

- 無需網路
- GGUF 模型檔案儲存在裝置上
- 請參閱 [本機模型](local-model.md) 了解設定方式
