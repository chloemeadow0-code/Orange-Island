# 入門指南

本指南將引導您安裝 橘子岛、新增您的第一組 API 金鑰，並傳送您的第一則訊息。

## 安裝

### 從 F-Droid 安裝（推薦）

橘子岛 可從 F-Droid（開放原始碼的 Android 應用程式商店）取得。

1. 在您的裝置上安裝 [F-Droid](https://f-droid.org/)
2. 開啟 F-Droid，搜尋 **橘子岛**
3. 點選 **安裝**

### 從 GitHub Releases 安裝

1. 前往 [Releases 頁面](https://github.com/orangeisland/app/releases)
2. 下載最新的 `.apk` 檔案
3. 在您的裝置上開啟該檔案，並在提示時確認安裝

### 從原始碼建置

若您偏好自行建置：

1. 複製儲存庫：
   ```
   git clone https://github.com/orangeisland/app.git
   ```
2. 在 [Android Studio](https://developer.android.com/studio)（Ladybug 或更新版本）中開啟專案
3. 同步 Gradle 並建置

需求：Android SDK 34+、JDK 17+。

---

## 首次啟動

當您首次開啟 橘子岛 時，會看到一個包含文字輸入框的歡迎畫面。在您可以開始聊天之前，需要先設定提供者和 API 金鑰。

### 步驟 1：新增 API 金鑰

1. 點選導覽列中的 **設定** 圖示（右下角的齒輪）
2. 在 **服務** 下方，點選 **提供者**
3. 從清單中選擇一個提供者（例如 **OpenAI**、**Anthropic**、**Google**）
4. 點選 **新增金鑰**
5. 為您的金鑰輸入名稱（例如「個人」）並貼上您的 API 金鑰
6. 點選 **新增**

??? tip "我要去哪裡取得 API 金鑰？"
    - **Google Gemini**：[Google AI Studio](https://aistudio.google.com/apikey) — 提供免費額度
    - **OpenAI**：[Platform API Keys](https://platform.openai.com/api-keys)
    - **Anthropic**：[Console API Keys](https://console.anthropic.com/)
    - **DeepSeek**：[Platform](https://platform.deepseek.com/)
    - **OpenRouter**：[Keys 頁面](https://openrouter.ai/keys)

    請參閱 [API 提供者](provider.md) 頁面以了解每個提供者的詳細資訊。

### 步驟 2：同步模型

1. 返回設定並點選 **模型**（位於 **服務** 下方）
2. 點選 **從所有提供者同步**
3. 橘子岛 會為所有已設定的提供者擷取最新的模型清單
4. 同步完成後，點選一個模型將其設為您的 **預設模型**

### 步驟 3：傳送您的第一則訊息

1. 點選 **返回箭頭** 回到聊天畫面
2. 在底部的輸入欄位中輸入訊息
3. 點選 **傳送**（紙飛機圖示）

模型將會即時串流輸出其回應。

---

## 應用程式版面配置

橘子岛 擁有以聊天畫面為中心的簡潔版面配置：

### 頂端列

- **對話標題** — 顯示目前的對話名稱（點選可重新命名）
- **漢堡選單** (:material-menu:) — 開啟對話抽屜
- **更多選單** (:material-dots-vertical:) — 各對話設定（模型、系統提示、生成參數）

### 對話抽屜

點選 **漢堡選單** 或從左側邊緣向右滑動以開啟：

- **搜尋列** — 透過關鍵字或語義搜尋尋找過往對話
- **對話清單** — 所有對話，最新者優先顯示
- **設定** (:material-cog:) — 設定提供者、模型、提示等
- **新對話** — 開始一個全新的對話

### 聊天畫面

- **訊息區域** — 可捲動的對話記錄，支援 Markdown 渲染
- **底部列** — 文字輸入、模型選擇器、附件按鈕 (+) 和傳送按鈕

---

## 下一步

- [設定系統提示](system-prompts.md) 以自訂模型行為
- [設定網頁搜尋](web-search.md) 以取得即時網際網路存取
- [探索代理工具](tools.md) — Shell 執行、檔案操作與記憶體
- [從 Claude 或 ChatGPT 匯入資料](import-export.md)
- [執行本地模型](local-model.md) 以供離線使用
