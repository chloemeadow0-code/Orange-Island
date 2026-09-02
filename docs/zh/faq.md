# 常见问题

## API 与提供商

### 如何获取 API 密钥？

- **Google Gemini**：[Google AI Studio](https://aistudio.google.com/apikey) — 有免费额度
- **OpenAI**：[Platform API Keys](https://platform.openai.com/api-keys)
- **Anthropic**：[Console API Keys](https://console.anthropic.com/)
- **DeepSeek**：[Platform](https://platform.deepseek.com/)
- **OpenRouter**：[Keys page](https://openrouter.ai/keys)
- **Brave Search**：[Brave Search API](https://api.search.brave.com/)

### 同一个提供商可以使用多个 API 密钥吗？

可以。每个提供商支持多个命名密钥。点按单选按钮切换活跃密钥。适合在工作/个人密钥之间切换或保留备用密钥。详见 [API 提供商](provider.md#api-keys)。

### 如何添加自定义提供商？

前往 设置 → 提供商 → **+ 添加自定义提供商**。输入名称和 Base URL。任何兼容 OpenAI 的端点都可以使用。详见 [自定义提供商](provider.md#custom-providers)。

---

## 本地模型

### 哪些 GGUF 模型可用？

橘子岛 支持 GGUF 格式的聊天和嵌入模型。聊天模型需要能装入设备内存（1–8B 参数量，取决于 RAM）。嵌入模型更小（100–500 MB）。详见 [本地模型](local-model.md)。

### 如何离线运行模型？

通过 设置 → 提供商 → Local → **导入 GGUF 模型** 导入 GGUF 聊天模型。如需完全离线的语义搜索，还需要导入 GGUF 嵌入模型。无需网络连接。

### 为什么本地模型这么慢？

本地推理在设备 CPU 上运行，天然比云端 API 慢。建议：使用较小的模型（1–3B 参数）、较低量化（Q4_K_M）、较短的上下文窗口、关闭后台应用。

---

## 嵌入与搜索

### 为什么嵌入模型测试失败？

常见原因：

- **模型名称错误** — 检查精确拼写，包括 Ollama 标签（如 `qwen3-embedding:8b`）
- **Base URL 错误** — 确保端点支持 `/v1/embeddings`
- **缺少 API 密钥** — 某些提供商需要认证
- **网络** — 检查与端点的连通性

### 关键词搜索和 RAG 搜索有什么区别？

关键词搜索匹配精确文本。RAG（语义搜索）按含义匹配 — "数据库设置" 可以找到 "Room 配置"，即使没有共同词语。RAG 需要嵌入模型和已缓存的消息。详见 [对话搜索](search.md)。

### 如何在 Ollama 中使用嵌入？

1. 在机器上安装 Ollama
2. 拉取嵌入模型：`ollama pull qwen3-embedding:8b`
3. 在 橘子岛 中，用 **Ollama** 预设添加远程嵌入模型
4. Base URL：`http://<host>:11434/v1`
5. 输入包含标签的精确模型名称（如 `qwen3-embedding:8b`）
6. API 密钥留空

---

## 记忆

### 活跃记忆和已保存记忆有什么区别？

**活跃记忆** 是随每次 API 调用一同发送的单一持久上下文 —— 模型始终能看到它。**已保存记忆** 是一组命名文件，模型按需搜索和检索。活跃记忆用于持久事实；已保存记忆用于参考资料。详见 [记忆与缓存](memory.md)。

### 模型能修改我的记忆吗？

可以，如果你在 设置 → 记忆中启用了 **访问已保存记忆** 和/或 **访问活跃记忆**。模型可以通过工具调用创建、读取、编辑和删除记忆。所有权限默认关闭。

---

## Shell 与工具

### 如何设置远程 Shell 访问？

在目标机器上部署 [Conch](https://github.com/newo-ether/conch) 服务器，然后在 设置 → Shell 中添加设备，填入 URL 和 API 密钥。详见 [远程 Shell](shell.md)。

### 可以不用 API 密钥搜索网络吗？

可以。**DuckDuckGo Lite** 是默认的网络搜索提供商，无需 API 密钥。开箱即用 — 只需在 设置 → 网络搜索 中启用网络搜索即可。如需更高可靠性，可配置基于 API 的提供商（Brave、Serper、Tavily、SearXNG）。详见 [网络搜索](web-search.md)。

### Shell 连接是加密的吗？

是的。Conch 使用 ECDH 密钥交换 + AES-256-GCM 加密 + HMAC-SHA256 签名。橘子岛 与 Conch 服务器之间的所有流量均为端到端加密。

---

## 数据

### 如何备份数据？

创建手动 `.橘子岛` 备份。如需免手动保护，在 设置 → 数据控制 → 自动备份 中启用**自动备份** — 它会在后台定期备份你的数据。详见 [数据迁移](import-export.md)。

### 能从 ChatGPT 或 Claude 导入吗？

可以。从 ChatGPT 或 Claude 导出数据（它们提供 `.zip` 文件），然后在 设置 → 数据控制 → **第三方** 中导入。支持合并和替换两种策略。详见 [数据迁移](import-export.md#third-party-import)。

### API 密钥会包含在导出文件中吗？

可选。导出界面允许你切换是否包含 API 密钥。启用时会有警告提示。密钥以明文形式存储在 `.橘子岛` 文件中，因此仅在完全设备迁移且信任目标时包含。

---

## 通用

### 数据存储在哪里？

所有数据存储在 Android 设备的 Room 数据库中。橘子岛 没有服务器、没有云同步、没有遥测。消息直接从你的设备发送到你配置的 AI 提供商。

### 橘子岛 支持多语言吗？

支持。应用 UI 支持 **English**、**中文** 和 **繁體中文（繁体中文）**。设置 → 语言。切换后需要重启。

### 如何反馈问题或请求功能？

在 [GitHub](https://github.com/chloemeadow0-code/Orange-Island/issues) 上提交 issue。贡献方式见 README 的 [Contributing](https://github.com/chloemeadow0-code/Orange-Island#contributing) 部分。
