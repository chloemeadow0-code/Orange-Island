<div align="center">
  <img src="app/src/main/assets/橘子岛_transparent_large.png" alt="橘子岛 Logo" width="120" />

  # 橘子岛

  **BYOK LLM 客户端 — 多提供商接入、智能代理工作流、远程设备控制**

  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-Native-blue.svg)](https://kotlinlang.org/)
  <br/>[English](README.md) | **中文**

  <img src="assets/feature_graphic.png" alt="橘子岛 — 夺回数据主权的 BYOK AI 应用" width="100%" />
</div>

## 下载

> 应用商店渠道正在筹备中，当前请通过以下方式获取。

[![GitHub Releases](https://img.shields.io/badge/GitHub-Releases-blue?logo=github)](https://github.com/chloemeadow0-code/Orange-Island/releases)

- **GitHub Releases** — 从 [Releases 页面](https://github.com/chloemeadow0-code/Orange-Island/releases) 下载最新 `.apk`。
- **从源码构建** — 用 Android Studio 克隆并构建（详见[快速开始](#快速开始)）。

---

**橘子岛** — 为 AI 重度用户打造的 BYOK Android 客户端。接入 8+ 内置提供商（外加无限自定义端点），使用自己的 API 密钥，支持对话分支树、llama.cpp 本地推理、加密远程 Shell 控制。所有数据存储在本地，无日志泄露。开源，MIT 协议。

## 截图

<table>
<tr>
<td width="33%"><img src="assets/screenshot_1.jpg" alt="聊天" width="100%"/></td>
<td width="33%"><img src="assets/screenshot_2.jpg" alt="工具" width="100%"/></td>
<td width="33%"><img src="assets/screenshot_3.jpg" alt="设置" width="100%"/></td>
</tr>
</table>

## 为什么选择 橘子岛？

- **无中间层：** 直连 API。无遥测、无追踪、无企业服务器记录你的对话。一切存储在本地 Room 数据库中。
- **非线性思维：** 树形消息数据库让你可以编辑任意历史消息、重新生成回复、探索备选分支，不会丢失上下文。
- **原生智能代理：** 多轮工具调用，支持联网搜索、代码执行、远程文件操作、记忆管理、语义对话搜索。
- **远程控制：** 通过 [Conch](https://github.com/newo-ether/conch) 协议管理服务器、编辑文件、搜索远程代码 — ECDH + AES-256-GCM 端到端加密。

## 功能特性

### 多提供商接入
- **8 个内置提供商：** OpenAI、Anthropic、Google Gemini、DeepSeek、通义千问（DashScope）、OpenRouter、Ollama、本地（GGUF via llama.cpp）
- **无限自定义提供商**，支持任意 Base URL 和 API 密钥
- **BYOK：** 使用自己的 API 密钥 — 无需订阅，无中间层
- **每个提供商支持多个 API 密钥**，可命名别名，方便轮换
- 每个提供商可独立覆盖 Base URL，适配代理和自托管端点

### 智能代理工具
模型可在多轮循环中自主调用以下工具：
- **联网搜索** — DuckDuckGo Lite（匿名免密钥）、Brave、Serper、Tavily、SearXNG
- **代码执行** — Gemini 代码执行、PRoot Alpine Linux 沙盒 + SAF 文件访问
- **图片生成** — BYOK 文生图，OpenAI 兼容 `/v1/images/generations`，聊天内直接渲染
- **远程 Shell 与文件 I/O** — 通过 [Conch](https://github.com/newo-ether/conch) 协议执行命令、读写/编辑/搜索远程文件
- **记忆** — 跨对话的持久活跃记忆和记忆文件存储
- **对话搜索** — 基于 RAG 的对话历史语义搜索

### 深度推理
- 支持深度推理：OpenAI o1/o3、Anthropic extended thinking、Gemini thinking、DeepSeek-R1、通义千问 QwQ
- 可配置推理等级（低/中/高）
- 流式思考标签渲染，可折叠 UI + 耗时追踪

### 本地智能
- **本地 LLM 推理** via llama.cpp — 完全离线运行 GGUF 模型
- **本地 embedding** — 设备端语义搜索（RAG）对话历史
- **Ollama** 提供商 — 接入局域网自托管模型

### 远程设备控制（Conch 协议）
- ECDH 密钥交换 + AES-256-GCM 加密 + HMAC-SHA256 签名
- 令牌桶速率限制 + 基于 nonce 的防重放保护
- **多设备支持** — 配置多台远程服务器并切换
- **MCP 集成** — Conch 可作为 Claude Desktop MCP 服务器

### 知识管理
- **RAG 语义搜索** 基于余弦相似度搜索所有历史对话
- 可配置相似度阈值和关键词/模型搜索方式
- 可独立选择 embedding 模型（远程或本地），不依赖聊天模型
- **上下文窗口管理** — 实时 token 计数和滑动窗口
- 可视化上下文范围指示器，淡化窗口外的消息

### 数据可移植
- **.橘子岛 导出/导入：** 对话、记忆、提示词、设置、API 密钥打包为单一可移植文件
- **合并、替换、跳过** 三种导入策略
- **自动备份** — 基于 WorkManager 的周期性备份，可配置周期、分类和保留策略
- **第三方导入：** Claude 和 ChatGPT 导出格式（.zip / .json）
- 导出和导入流程均有 API 密钥安全提醒

### 个性化定制
- **系统提示词模板**，三段式编辑器（系统提示词 + 用户前置 + 用户后置）
- 变量替换：`{sent_time}`、`{sent_date}` 及可扩展变量系统
- 每个对话独立切换模型和系统提示词
- 聊天底栏可按消息切换模型
- 每对话生成参数覆盖（温度、maxTokens、惩罚项等）
- **自动标题生成**，可配置生成模型

### UI & 交互
- 现代 Material 3 设计，Jetpack Compose + 动态取色（Material You）
- 亮色 / 暗色 / 跟随系统主题模式，可配置配色方案
- **非线性分支：** 编辑任意历史消息，分支进入备选对话路径
- 实时流式响应，消息锚定 + 动画自动滚动
- 全局触觉反馈（长按、选择、成功/错误）
- 沉浸式手势图片与媒体查看器
- Markdown 渲染，支持语法高亮、LaTeX 数学公式、代码块
- 图片、视频、PDF、文件附件支持及缩略图预览
- iOS 风格折叠大标题设置页，共享页面过渡动画
- 模糊效果支持性能可配置开关
- 支持英文、简体中文和繁体中文

## 文档

📖 **[浏览用户手册](https://docs.orangeisland.app/)** — 24 个页面，涵盖安装、提供商、工具、搜索、记忆、Shell 等。

🏗️ **[架构指南](ARCHITECTURE.md)** — 完整的代码库导览：数据层、API 提供商、JNI、UI 及数据流。

## 快速开始

### 环境要求
- [Android Studio](https://developer.android.com/studio)（推荐 Ladybug 及以上）
- Android SDK 34+
- 任一支持提供商的 API 密钥

### 快速配置

<table>
<tr>
<td width="20%"><b>① 启动</b><br/>在设备上打开 橘子岛。</td>
<td width="20%"><b>② 设置</b><br/>从导航栏打开<b>设置</b>。</td>
<td width="20%"><b>③ API 密钥</b><br/>选择<b>提供商</b>，添加你的 <b>API 密钥</b>。</td>
<td width="20%"><b>④ 模型</b><br/><b>模型</b> →「从所有提供商同步」。</td>
<td width="20%"><b>⑤ 定制</b><br/>系统提示词、上下文、搜索、记忆。</td>
</tr>
</table>

### 运行本地模型

<table>
<tr>
<td width="25%"><b>① 放置</b><br/>将 GGUF 模型文件放到设备上。</td>
<td width="25%"><b>② 导入</b><br/>设置 → 提供商 → 本地 →「导入 GGUF 模型」。</td>
<td width="25%"><b>③ 配置</b><br/>设置上下文大小、温度等参数。</td>
<td width="25%"><b>④ 选择</b><br/>从聊天模型选择器中选择你的本地模型。</td>
</tr>
</table>

### 设置远程 Shell（Conch）

<table>
<tr>
<td width="33%"><b>① 部署</b><br/>在目标机器上部署 <a href="https://github.com/newo-ether/conch">Conch 服务器</a>。</td>
<td width="33%"><b>② 添加设备</b><br/>设置 → Shell 设备 → 添加 URL 和 API 密钥。</td>
<td width="33%"><b>③ 使用</b><br/>模型会自动发现 Shell 设备，用于执行命令、文件操作和搜索。</td>
</tr>
</table>

## 技术栈

- **语言：** [Kotlin](https://kotlinlang.org/)
- **UI 框架：** [Jetpack Compose](https://developer.android.com/jetpack/compose)（Material 3，动态取色）
- **架构：** MVVM + Kotlin Coroutines & Flow
- **本地存储：** [Room Database](https://developer.android.com/training/data-storage/room) 树形消息结构 + DataStore Preferences
- **网络：** OkHttp + SSE 流式传输
- **序列化：** `kotlinx.serialization`
- **原生：** llama.cpp via Android NDK（CMake）用于本地 LLM 推理和 embedding
- **图片加载：** Coil
- **Markdown：** Multiplatform Markdown Renderer M3
- **数学公式：** JLaTeXMath-Android

## 参与贡献

欢迎贡献！可以 Fork 仓库、提交 Pull Request 或创建 Issue。

## 隐私

橘子岛 不会收集、存储或传输任何个人数据。所有对话、API 密钥和设置均存储在本地设备上。消息直接从你的设备发送到你配置的 AI 提供商 — 无中间服务器、无遥测、无追踪。详见[隐私政策](PRIVACY.md)。

## 许可证

本项目是基于 newo-ether 的开源项目"Agora"（[MIT License](LICENSE)）二次修改、重塑品牌后的产物"橘子岛 / Orange Island"，原始版权声明与详情见 [NOTICE](NOTICE)。

> **请勿就本应用联系原作者。** 原作者未参与橘子岛的开发，也不对本应用承担任何责任或存在任何隶属关系。如遇问题、疑问或反馈，请通过本仓库的 Issue 或应用内反馈渠道联系我们。

此外，本应用还打包了若干第三方组件，各自遵循其原始许可证：llama.cpp（MIT）、PRoot（GPL-2.0）、talloc（LGPL-3.0）、JLaTeXMath（GPL-2.0 及 Classpath 例外）。它们的完整源码包含在本仓库的 [`thirdparty/`](thirdparty/) 目录中，版权声明与许可全文见 [NOTICE](NOTICE)，以及在应用内"设置 → 关于 → 开源许可声明"中查看。

## 开源协议

| 组件 | 协议 | 版权 | 源码位置 |
|---|---|---|---|
| 橘子岛（本 fork 全部一手代码） | MIT | (c) 2026 橘子岛贡献者 | 本仓库 |
| Agora（本 fork 基于的上游项目） | MIT | (c) 2026 newo-ether | 见 [NOTICE](NOTICE) |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) —— 本地大模型推理引擎 | MIT | (c) 2023-2026 The ggml authors | [`thirdparty/llama.cpp`](thirdparty/llama.cpp) |
| [PRoot](https://github.com/termux/proot)（Termux 补丁版）—— Linux 环境 | GPL-2.0 | (c) STMicroelectronics；补丁由 Termux 维护 | [`thirdparty/proot`](thirdparty/proot) |
| [talloc](https://www.samba.org/ftp/talloc/)（Samba）—— 内存分配器 | LGPL-3.0-or-later | (c) Andrew Tridgell、Stefan Metzmacher | [`thirdparty/talloc`](thirdparty/talloc) |
| [JLaTeXMath](https://github.com/opencollab/jlatexmath) —— LaTeX 公式渲染 | GPL-2.0 + Classpath 例外 | (c) Scilab Enterprises / opencollab；Android 移植版 Dimitry Ivanov | 经 `ru.noties:jlatexmath-android` 打包 |
| Gradle Wrapper | Apache-2.0 | Gradle 贡献者 | `gradlew` |
| 其余全部依赖（Kotlin/Compose/OkHttp 等） | Apache-2.0、MIT、BSD、EPL、ISC 等 | 各自作者 | 见应用内"设置 → 关于 → 第三方开源许可" |

GPL-2.0、LGPL-3.0、GPL-3.0 的许可全文已随应用内置（设置 → 关于 → 开源许可声明），也可在 [gnu.org](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html)、[LGPL-3.0](https://www.gnu.org/licenses/lgpl-3.0.html)、[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) 查看。
