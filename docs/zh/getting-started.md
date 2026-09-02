# 快速开始

本指南将带你完成安装 橘子岛、添加第一个 API 密钥并发送第一条消息。

## 安装

### 通过 F-Droid（推荐）

橘子岛 已上架 F-Droid 开源应用商店。

1. 在设备上安装 [F-Droid](https://f-droid.org/)
2. 打开 F-Droid，搜索 **橘子岛**
3. 点按 **安装**

### 通过 GitHub Releases

1. 访问 [Releases 页面](https://github.com/chloemeadow0-code/Orange-Island/releases)
2. 下载最新的 `.apk` 文件
3. 在设备上打开文件，按提示确认安装

### 从源码构建

如果你更喜欢自己构建：

1. 克隆仓库：
   ```
   git clone https://github.com/chloemeadow0-code/Orange-Island.git
   ```
2. 在 [Android Studio](https://developer.android.com/studio)（Ladybug 或更新版本）中打开项目
3. 同步 Gradle 并构建

要求：Android SDK 34+、JDK 17+。

---

## 首次启动

首次打开 橘子岛 时，你会看到一个带有文本输入框的欢迎界面。在开始聊天之前，需要先配置提供商和 API 密钥。

### 第一步：添加 API 密钥

1. 点按导航栏中的 **设置** 图标（右下角齿轮）
2. 在 **服务** 下，点按 **提供商**
3. 从列表中选择一个提供商（如 **OpenAI**、**Anthropic**、**Google**）
4. 点按 **添加新密钥**
5. 输入密钥名称（如 "个人"）并粘贴 API 密钥
6. 点按 **添加**

??? tip "从哪里获取 API 密钥？"
    - **Google Gemini**：[Google AI Studio](https://aistudio.google.com/apikey) — 有免费额度
    - **OpenAI**：[Platform API Keys](https://platform.openai.com/api-keys)
    - **Anthropic**：[Console API Keys](https://console.anthropic.com/)
    - **DeepSeek**：[Platform](https://platform.deepseek.com/)
    - **OpenRouter**：[Keys page](https://openrouter.ai/keys)

    各提供商的详细信息见 [API 提供商](provider.md) 页面。

### 第二步：同步模型

1. 返回设置，点按 **模型**（在 **服务** 下）
2. 点按 **从所有提供商同步**
3. 橘子岛 获取所有已配置提供商的最新模型列表
4. 同步完成后，点按某个模型将其设为 **默认模型**

### 第三步：发送第一条消息

1. 点按 **返回箭头** 回到聊天界面
2. 在底部的输入框中输入消息
3. 点按 **发送**（纸飞机图标）

模型将实时流式输出响应。

---

## 应用布局

橘子岛 以聊天界面为中心，布局简洁：

### 顶部栏

- **对话标题** — 显示当前对话名称（点按可重命名）
- **汉堡菜单** (:material-menu:) — 打开对话抽屉
- **更多菜单** (:material-dots-vertical:) — 单对话设置（模型、系统提示词、生成参数）

### 对话抽屉

点按 **汉堡菜单** 或从左侧边缘右滑打开：

- **搜索栏** — 通过关键词或语义搜索查找历史对话
- **对话列表** — 所有对话，按时间排序
- **设置** (:material-cog:) — 配置提供商、模型、提示词等
- **新对话** — 开始全新对话

### 聊天界面

- **消息区域** — 可滚动的对话历史，支持 Markdown 渲染
- **底部栏** — 文本输入、模型选择器、附件按钮 (+) 和发送按钮

---

## 下一步

- [配置系统提示词](system-prompts.md) 自定义模型行为
- [设置网络搜索](web-search.md) 获取实时互联网信息
- [探索代理工具](tools.md) — Shell 执行、文件操作和记忆
- [导入数据](import-export.md) 从 Claude 或 ChatGPT 迁移
- [运行本地模型](local-model.md) 实现离线使用
