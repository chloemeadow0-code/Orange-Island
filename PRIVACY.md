# Privacy Policy

**Last updated: September 2, 2026**

橘子岛 (Orange Island) is an AI chat client that communicates directly with the API providers you configure. This policy explains how your data is handled.

## Data Collection

- **Conversations** are stored **locally on your device only**. They are never sent to the developer and are never used for training.
- **API keys and model credentials** are stored **locally on your device** and are only used to authenticate requests to the AI providers you configure.
- Messages you send are transmitted **directly from your device** to the AI provider's API (e.g., Google Gemini, OpenAI, Anthropic, Ollama). 橘子岛 does not intermediate or log these requests.

### Crash reporting (optional, opt-in)

橘子岛 has **no analytics, no telemetry, and no automatic data upload**. There is one exception you control:

- If the app crashes, the report is saved **locally on your device**. On the next launch, the app asks whether you want to send it. **Nothing is uploaded unless you explicitly tap "send".**
- A submitted crash report contains only: the error stack trace, app version, Android version, device manufacturer/model, a timestamp, and short diagnostic breadcrumbs. It contains **no conversation content, no API keys, and no personal identifiers**, and is not shared with third parties.

### Optional user-configured cloud sync

Some optional features (such as health-data sync, and certain community plugins like Moments or Question Box) let you connect **your own** Supabase project or server by entering your own URL and keys. In that case, data is sent to and stored under **your own** cloud project — the developer never has access to it. These features are off unless you configure them.

## Third-Party Services

When you use 橘子岛, your messages and attached files are sent to the AI provider(s) you select. Each provider has its own privacy policy:

- [Google Gemini API](https://ai.google.dev/gemini-api/terms)
- [OpenAI API](https://openai.com/policies/privacy-policy)
- [Anthropic API](https://www.anthropic.com/legal/privacy)
- Providers running via Ollama or custom endpoints are under your own control.

Please review the privacy policy of the provider you use.

## Permissions

橘子岛 requests the following Android permissions. Beyond the first three, permissions power **optional tools that only run when you explicitly ask the AI to use them, or when you enable the corresponding feature in Settings**:

- **Internet**: communicate with AI provider APIs.
- **Notifications**: keep foreground services (ongoing generations, music playback, workflows) alive.
- **Storage / file access**: only when you explicitly attach images, videos, or files to a message.
- **Camera**: the "take photo" tool, only when you ask the AI to take a photo.
- **Microphone**: voice input (speech-to-text), only when you start a voice session.
- **Location (precise/coarse)**: the location tool and geofence workflow triggers, only when you ask for location features.
- **Calendar (read/write)**: the calendar tool, only when you ask the AI to read or create calendar entries.
- **Usage stats access**: the app-usage tool and app-usage workflow triggers.
- **Accessibility services**: only if you enable them for workflow automation (app-foreground triggers) or the optional App Lock feature.
- **Notification listener**: only if you enable notification-reading tools or notification-based workflow triggers.
- **Bluetooth**: detecting connected Bluetooth devices (e.g., headphone triggers for workflows, wearable integrations).
- **Run at startup / alarms / wake lock / foreground services / battery optimization exemption**: infrastructure used to restore schedules after reboot and keep workflows, alarms, and media playback reliable.
- **Install packages / display over other apps**: only used if you explicitly confirm the corresponding action (e.g., in-app package install or the desktop-pet floating window).

## Data Retention

All chat history is stored locally in an on-device database. You can delete conversations at any time within the app. Clearing the app's data or uninstalling will remove all local data.

## Children's Privacy

橘子岛 is not directed to children under the age of 13.

## Changes

This policy may be updated from time to time. Changes will be posted on this page.

## Contact

If you have questions about this policy, open an issue at [github.com/chloemeadow0-code/Orange-Island](https://github.com/chloemeadow0-code/Orange-Island/issues).

---

# 隐私政策

**最后更新：2026年9月2日**

橘子岛 是一款 AI 聊天客户端，直接与你配置的 API 提供商通信。本政策说明你的数据如何处理。

## 数据收集

- **对话内容仅存储在你的设备本地**，不会发送给开发者，也不会被用于训练。
- **API 密钥和模型凭证仅存储在你的设备本地**，仅用于向你选择的 AI 提供商认证请求。
- 你发送的消息从你的设备**直接**传输到 AI 提供商的 API（如 Google Gemini、OpenAI、Anthropic、Ollama）。橘子岛 不会中转或记录这些请求。

### 崩溃上报（可选、需主动确认）

橘子岛 **没有任何统计分析、遥测或自动上传**。唯一的例外由你掌控：

- 应用崩溃时，报告**先保存在你的设备本地**。下次启动时应用会询问你是否发送，**你不主动点击"发送"就绝不会上传**。
- 提交的崩溃报告只包含：错误堆栈、应用版本、Android 版本、设备厂商与型号、时间戳和简短的诊断面包屑。**不包含任何聊天内容、API 密钥或个人身份信息**，也不会与第三方共享。

### 可选的自定义云同步

部分可选功能（如健康数据同步，以及朋友圈、提问箱等社区插件）允许你填入**自己的** Supabase 项目地址和密钥。此时数据发送并存储在**你自己的**云项目中，开发者无权访问。这些功能默认关闭，只有你主动配置后才会启用。

## 第三方服务

使用 橘子岛 时，你的消息和附件会发送到你选择的 AI 提供商。每个提供商有自己的隐私政策，请自行查阅：

- [Google Gemini API](https://ai.google.dev/gemini-api/terms)
- [OpenAI API](https://openai.com/policies/privacy-policy)
- [Anthropic API](https://www.anthropic.com/legal/privacy)
- 经 Ollama 或自定义端点运行的提供商由你自行掌控。

## 权限

橘子岛 会请求以下 Android 权限。除前三项外，其余权限对应的都是**可选工具，只在你明确让 AI 使用、或在设置中开启对应功能时才会运行**：

- **网络**：与 AI 提供商 API 通信。
- **通知**：保持前台服务（生成任务、音乐播放、工作流）运行。
- **存储/文件访问**：仅在你主动选择图片、视频或文件附件时使用。
- **相机**："拍照"工具，仅在你要求 AI 拍照时使用。
- **麦克风**：语音输入（语音转文字），仅在你开启语音会话时使用。
- **位置信息（精确定位/粗略定位）**：位置工具和地理围栏工作流触发器，仅在你使用位置相关功能时使用。
- **日历（读/写）**：日历工具，仅在你让 AI 读取或创建日历事项时使用。
- **使用情况访问**：应用使用时长工具与使用情况工作流触发器。
- **无障碍服务**：仅在你为工作流自动化（应用前台触发器）或可选的应用锁定功能主动开启时使用。
- **通知监听**：仅在你开启通知读取工具或基于通知的工作流触发器时使用。
- **蓝牙**：检测已连接的蓝牙设备（如耳机触发工作流、可穿戴设备集成）。
- **开机自启 / 闹钟 / 唤醒锁定 / 前台服务 / 电池优化豁免**：用于重启后恢复计划任务，保障工作流、闹钟和媒体播放稳定运行。
- **安装软件包 / 显示悬浮窗**：仅在你明确确认对应操作时使用（如应用内安装、桌面宠物悬浮窗）。

## 数据保留

所有聊天记录存储在设备本地数据库中。你可以随时在应用内删除对话。清除应用数据或卸载将删除所有本地数据。

## 儿童隐私

橘子岛 不面向 13 岁以下儿童。

## 变更

本政策可能不时更新，更新内容将发布在此页面。

## 联系

如有问题，请在 [github.com/chloemeadow0-code/Orange-Island](https://github.com/chloemeadow0-code/Orange-Island/issues) 提交 issue。
