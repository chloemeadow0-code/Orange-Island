# Privacy Policy

**Last updated: September 2, 2026**

橘子岛 (Orange Island) is an AI chat client that communicates directly with the API providers you configure. This policy explains how your data is handled.

## Data Collection

**Conversations and settings are stored locally. Online features send data to the services they use.**

- Conversation history is stored **locally on your device**. Model requests include messages, relevant context, and attachments required for the selected feature.
- API keys and model credentials are stored **locally on your device** and are only used to authenticate requests to the AI providers you configure.
- Requests go from your device to the provider or proxy you configure. Those services may process or retain data under their own policies; this app cannot guarantee that remote services keep no logs.
- Search, remote shell, MCP servers, plugins, and other online tools may send queries, files, or tool arguments to their respective endpoints when used.
- Update checks contact the configured release service. After a crash, a report is stored locally and submitted only if you confirm. It contains diagnostics such as the stack trace, app/Android version, device model, and recent diagnostic events. The configured crash endpoint is `https://crash.orangeisland.app/crash`; this policy does not assert ownership of that domain. Stack traces may include details from an error, so review a report before sharing it.

### Optional user-configured cloud sync

Configured cloud-sync features, including health-data sync and user-installed plugins, can send selected data to the server you choose. Access and retention depend on that service's configuration and policies. These features require configuration before use.

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
- **Storage / file access**: attachments, imports, exports, backups, and file or sandbox tools within the access you grant.
- **Camera**: the "take photo" tool, only when you ask the AI to take a photo.
- **Microphone**: voice input (speech-to-text), only when you start a voice session.
- **Location (precise/coarse)**: the location tool and geofence workflow triggers, only when you ask for location features.
- **Calendar (read/write)**: the calendar tool, only when you ask the AI to read or create calendar entries.
- **Usage stats access**: the app-usage tool and app-usage workflow triggers.
- **Accessibility services**: only if you enable them for workflow automation (app-foreground triggers) or the optional App Lock feature.
- **Notification listener**: only if you enable notification-reading tools or notification-based workflow triggers.
- **Bluetooth**: detecting connected Bluetooth devices (e.g., headphone triggers for workflows, wearable integrations).
- **Wi-Fi / network state**: connectivity checks and Wi-Fi-based workflow triggers.
- **Run at startup / alarms / wake lock / vibrate / foreground services / battery optimization exemption**: infrastructure used to restore schedules after reboot and keep workflows, alarms, and media playback reliable.
- **Install packages / display over other apps**: only used if you explicitly confirm the corresponding action (e.g., in-app package install or the desktop-pet floating window).

## Data Retention

Chat history is stored in the app's local database and can be deleted in the app. Clearing app data or uninstalling removes app-managed local data, but does not erase exported files, backups, or data already sent to remote services. Exports and backups can contain conversations and, if selected, API keys or other credentials; protect these files and choose destinations you trust.

## Children's Privacy

橘子岛 is not directed to children under the age of 13.

## Changes

This policy may be updated from time to time. Changes will be posted on this page.

## Contact

If you have questions about this policy, open an issue in the [Orange Island repository](https://github.com/chloemeadow0-code/Orange-Island/issues). Do not include private conversations or credentials in a public issue.

---

# 隐私政策

**最后更新：2026年9月2日**

橘子岛 是一款 AI 聊天客户端，直接与你配置的 API 提供商通信。本政策说明你的数据如何处理。

## 数据收集

**对话和设置保存在本地。使用在线功能时，会向对应服务发送数据。**

- 对话历史保存在**设备本地**。模型请求会包含所选功能需要的消息、相关上下文及附件。
- API 密钥和模型凭证**仅存储在你的设备本地**，仅用于向你选择的 AI 提供商认证请求。
- 请求由设备发送到你配置的提供商或代理。这些服务可能按各自政策处理或保留数据，本应用无法保证远程服务不记录日志。
- 联网搜索、远程 Shell、MCP 服务器、插件及其他在线工具在使用时，可能向各自端点发送查询、文件或工具参数。
- 检查更新会访问配置的发布服务。崩溃报告先保存在本地，仅在你确认后提交，内容包括错误堆栈、应用及 Android 版本、设备型号和近期诊断事件。当前配置的报告端点为 `https://crash.orangeisland.app/crash`；本政策不对该域名的归属作出声明。错误堆栈可能包含报错细节，分享前请检查报告内容。

### 可选的自定义云同步

配置云同步功能（包括健康数据同步和用户安装的插件）后，选定的数据可能发送到你指定的服务器。访问权限及保留期限取决于该服务的配置和政策；这些功能需要配置后使用。

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
- **存储/文件访问**：用于附件、导入、导出、备份，以及你授予访问范围内的文件或沙盒工具。
- **相机**："拍照"工具，仅在你要求 AI 拍照时使用。
- **麦克风**：语音输入（语音转文字），仅在你开启语音会话时使用。
- **位置信息（精确定位/粗略定位）**：位置工具和地理围栏工作流触发器，仅在你使用位置相关功能时使用。
- **日历（读/写）**：日历工具，仅在你让 AI 读取或创建日历事项时使用。
- **使用情况访问**：应用使用时长工具与使用情况工作流触发器。
- **无障碍服务**：仅在你为工作流自动化（应用前台触发器）或可选的应用锁定功能主动开启时使用。
- **通知监听**：仅在你开启通知读取工具或基于通知的工作流触发器时使用。
- **蓝牙**：检测已连接的蓝牙设备（如耳机触发工作流、可穿戴设备集成）。
- **Wi-Fi / 网络状态**：网络连通性检查与基于 Wi-Fi 的工作流触发器。
- **开机自启 / 闹钟 / 唤醒锁定 / 震动 / 前台服务 / 电池优化豁免**：用于重启后恢复计划任务，保障工作流、闹钟和媒体播放稳定运行。
- **安装软件包 / 显示悬浮窗**：仅在你明确确认对应操作时使用（如应用内安装、桌面宠物悬浮窗）。

## 数据保留

聊天记录保存在应用本地数据库中，可在应用内删除。清除应用数据或卸载会移除应用管理的本地数据，但不会清除已导出的文件、备份或远程服务已收到的数据。导出与备份可包含对话，以及你选择包含的 API 密钥等凭证；请妥善保护文件并选择可信的保存位置。

## 儿童隐私

橘子岛 不面向 13 岁以下儿童。

## 变更

本政策可能不时更新，更新内容将发布在此页面。

## 联系

如有问题，请在[橘子岛仓库](https://github.com/chloemeadow0-code/Orange-Island/issues)提交 issue。不要在公开 issue 中附带私人对话或凭证。
