# 橘子岛 用户手册

欢迎查阅 橘子岛 用户手册。橘子岛 是一款 BYOK（自带密钥）Android LLM 客户端，支持多提供商接入、非线形分支对话、代理工具调用、图像生成、代码执行和远程设备控制。

## 快速导航

### 入门

- **[快速开始](getting-started.md)** — 安装、配置、发送第一条消息
- **[常见问题](faq.md)** — 常见问题解答

### 核心功能

- **[对话](conversations.md)** — 非线形分支、消息操作、流式渲染、Markdown 渲染
- **[API 提供商](provider.md)** — 接入 OpenAI、Anthropic、Google、DeepSeek、Ollama 及自定义端点
- **[模型](models.md)** — 启用/禁用模型、别名、按提供商同步模型
- **[系统提示词](system-prompts.md)** — 三段式编辑器、变量替换、单对话切换
- **[生成参数](generation.md)** — 温度、Top P、最大 Token、推理、频率/存在惩罚
- **[标题生成](title-generation.md)** — 自动生成对话标题
- **[图像转录](transcription.md)** — 为不支持视觉的提供商提供的图像转文本管道
- **[图像生成](image-generation.md)** — 作为聊天工具的文生图生成
- **[外观](appearance.md)** — 主题模式、配色方案、动态取色、方案风格

### 代理工具

- **[概述](tools.md)** — 多轮工具调用机制
- **[网络搜索](web-search.md)** — DuckDuckGo Lite（默认）、Brave、Serper、Tavily、SearXNG 集成
- **[远程 Shell (Conch)](shell.md)** — 加密远程命令执行、文件操作、MCP 集成
- **[沙盒](sandbox.md)** — 本地 Alpine Linux 环境，用于隔离的命令执行

### 知识管理

- **[对话搜索](search.md)** — 关键词与语义 (RAG) 搜索聊天记录
- **[嵌入 / RAG](embedding.md)** — 配置嵌入模型进行语义检索
- **[记忆与缓存](memory.md)** — 活跃记忆、已保存记忆、自动缓存

### 更多

- **[本地模型](local-model.md)** — 通过 llama.cpp 在设备上运行 GGUF 模型
- **[PDF 导入](pdf-import.md)** — 提取并发送 PDF 页面给视觉模型
- **[数据迁移](import-export.md)** — 导出/导入 .橘子岛 文件、自动备份，从 Claude 和 ChatGPT 导入
- **[语言](language.md)** — 在英文、中文、繁體中文或系统默认之间切换
- **[关于](about.md)** — 版本信息、更新、文档开关、链接、评分

---

## 关于 橘子岛

橘子岛 是一款面向 AI 高级用户的 BYOK Android 客户端：

- **无中间商**：直连 API，无遥测，无追踪
- **本地存储**：一切数据存储在 Room 数据库中
- **非线形对话**：编辑任意历史消息，探索替代分支
- **原生代理**：多轮工具调用，支持网络搜索、Shell 执行、文件操作、图像生成、代码执行和记忆
- **远程控制**：通过加密的 Conch 协议管理服务器
- **开源**：MIT 协议，[源码在 GitHub](https://github.com/orangeisland/app)
