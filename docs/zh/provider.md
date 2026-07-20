# API 提供商

橘子岛 直连 AI 提供商——无中间商，无订阅，无遥测。你自带 API 密钥，一切从你的设备运行。

## 内置提供商

| 提供商 | Base URL | 模型 | 备注 |
|----------|----------|--------|-------|
| **Google** | `https://generativelanguage.googleapis.com/v1beta` | Gemini 系列 | 通过 Google AI Studio 提供免费额度 |
| **OpenAI** | `https://api.openai.com/v1` | GPT-4、GPT-4o、o 系列 | 支持推理模型 |
| **Anthropic** | `https://api.anthropic.com/v1` | Claude 系列 | 支持扩展思考 |
| **DeepSeek** | `https://api.deepseek.com/v1` | DeepSeek-V3、DeepSeek-R1 | 支持推理模型 |
| **Qwen** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Qwen 系列 | 通过阿里云 DashScope |
| **Ollama** | `http://localhost:11434/v1` | 任意已拉取的模型 | 自托管，无需 API 密钥 |
| **OpenRouter** | `https://openrouter.ai/api/v1` | 多提供商 | 通过一个 API 访问多种模型 |
| **Local** | 无 | GGUF 模型 | 通过 llama.cpp 在设备上运行，完全离线 |

## 切换提供商

在设置中点按提供商选择器切换。每个提供商独立维护：

- API 密钥
- Base URL（可编辑，用于代理/自托管）
- 模型列表

---

## API 密钥 {#api-keys}

### 每个提供商多个密钥

每个提供商支持多个命名 API 密钥。这支持：

- **轮换** — 在不同用量层级之间切换
- **组织** — 区分工作和个人使用
- **备用** — 保留备份密钥

### 管理密钥

1. 前往 **设置 → 提供商**
2. 选择一个提供商
3. 在 **API 密钥** 下，点按 **添加新密钥**
4. 输入**名称**（如 "工作"、"个人"、"团队共享"）和**密钥值**
5. 点按 **添加**

点按单选按钮设置活跃密钥。长按密钥可**编辑**或**删除**。

### 密钥安全

!!! warning
    API 密钥存储在加密的 Room 数据库中。它们永远不会发送到 橘子岛 服务器（橘子岛 没有服务器）。但如果包含在 `.橘子岛` 导出文件中，它们会以明文形式导出。

---

## 自定义提供商 {#custom-providers}

添加任意兼容 OpenAI 的 API 端点：

1. 前往 **设置 → 提供商**
2. 点按提供商列表底部的 **+ 添加自定义提供商**
3. 输入：
    - **提供商名称** — 任意显示名称
    - **Base URL** — API 端点
4. 点按 **添加**

橘子岛 从 `{base_url}/v1/models` 获取模型列表。添加后，自定义提供商与内置提供商完全一样使用：添加 API 密钥、同步模型、聊天。

### 使用场景

- **自托管** — 连接 vLLM、LocalAI、text-generation-webui 或其他兼容 OpenAI 的服务器
- **代理** — 通过企业代理或 API 网关路由
- **替代端点** — 使用 Azure OpenAI、Cloudflare AI Gateway 或其他兼容服务

### 重命名或删除

长按自定义提供商可**重命名**或**删除**。删除会移除提供商及其所有密钥。

!!! warning
    内置提供商不能被重命名或删除。

---

## Base URL 覆写

每个提供商（包括内置）都有可编辑的 **Base URL**。适用于：

- **代理**：通过 `https://my-proxy.example.com/v1` 路由
- **自托管**：指向你自己的实例
- **区域路由**：使用区域特定的端点

---

## 同步模型

添加 API 密钥后，同步模型列表：

1. 前往 **设置 → 模型**
2. 点按 **从所有提供商同步**
3. 橘子岛 从每个已配置的提供商获取可用模型

Snackbar 显示同步进度和结果。然后你可以启用/禁用单个模型并设置默认模型。

---

## 各提供商说明

### Google Gemini

- API 密钥来自 [Google AI Studio](https://aistudio.google.com/apikey)
- 免费额度可用，有速率限制
- 支持代码执行和搜索接地（内置工具）

### OpenAI

- API 密钥来自 [Platform](https://platform.openai.com/api-keys)
- 推理模型（o1、o3）需要特定 API 访问权限
- 流式、工具调用和视觉均支持

### Anthropic

- API 密钥来自 [Console](https://console.anthropic.com/)
- 扩展思考，可配置 token 预算
- 支持工具调用和并行调用

### Ollama

- 无需 API 密钥（本地网络）
- Base URL 通常为 `http://<host>:11434/v1`
- 模型列表从 Ollama API 获取
- 详见 [常见问题](faq.md) 中的 Ollama 故障排除

### OpenRouter

- 一个 API 密钥访问 200+ 模型
- 按 token 付费，价格因模型而异
- 适合尝试不同模型而无需单独注册提供商账户

### Local (llama.cpp)

- 无需网络
- GGUF 模型文件存储在设备上
- 详见 [本地模型](local-model.md) 的设置说明
