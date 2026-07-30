# 网易云音乐插件 · 开发交接说明

> 本文档面向**插件开发者**。橘子岛 app 已就绪媒体控制能力（v1），你只需在此基础上开发搜索/歌词/UI 插件。
> 参考已有示例：`docs/plugin-examples/mini-chatroom/`（结构与你将开发的插件完全一致）。

---

## 一、app 已为你内置的能力（不用你实现）

橘子岛 app 新增了 **3 个内置工具**，模型按聊天语义自主调用，**你的插件不要重复实现它们**：

| 工具名 | 作用 | 关键参数 |
|---|---|---|
| `get_now_playing` | 读当前播放：歌/歌手/专辑/封面/进度/状态 | 可选 `package`（如 `com.netease.cloudmusic`） |
| `control_media` | 播放 / 暂停 / 上一首 / 下一首 / 拖动进度 | `action`（`play`/`pause`/`next`/`previous`/`seek`）；seek 必带 `position_ms`（**毫秒**） |
| `list_media_apps` | 列出当前有媒体会话的 app | 无参数 |

**权限与开关**：这些工具在「设置 → 设备访问 → 音乐控制」开关打开 + 用户授予「通知访问」权限后才出现。
开关关闭或权限不足时，工具会**立即消失**，调用时返回真实错误（如 `permission_denied`），不会假装成功。

### `get_now_playing` 返回示例

```json
{
  "packageName": "com.netease.cloudmusic",
  "mediaId": "186016",
  "title": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "coverUrl": "https://p1.music.126.net/....jpg",
  "durationMs": 263000,
  "positionMs": 43100,
  "isPlaying": true,
  "state": "playing"
}
```

`state` 取值：`playing` / `paused` / `stopped` / `buffering` / `error` / `fast_forwarding` / `rewinding` / `skipping_to_next` / `skipping_to_previous` / `none`。

### `control_media` 返回示例

成功（控制后的最新状态 + 执行的 action）：

```json
{
  "packageName": "com.netease.cloudmusic",
  "title": "晴天",
  "isPlaying": true,
  "state": "playing",
  "positionMs": 43100,
  "action": "play"
}
```

VIP/版权/地区锁定时（play/seek 后状态没推进，app 会诚实告警，绝不假装成功）：

```json
{
  "isPlaying": false,
  "state": "paused",
  "action": "play",
  "warning": "position/state did not advance; the track may be VIP-only, region-locked, or otherwise unplayable."
}
```

### 错误返回示例（所有工具统一格式）

```json
{ "error": "permission_denied", "message": "Notification access not granted. ..." }
{ "error": "no_active_session", "message": "No active media session. Open a music app and start playback first." }
{ "error": "not_yet_active",    "message": "Listener permission is granted but the service hasn't bound yet. Try again in a moment." }
{ "error": "playback_failed",   "message": "Controlling 'com.netease.cloudmusic' failed: ..." }
{ "error": "not_controllable",  "message": "The session for '...' has no transport controls." }
```

---

## 二、给你的 ui.html 准备的新能力：只读 `getMediaInfo`

app 给插件 WebView 桥加了一个**只读**方法（与 `get_now_playing` 工具返回结构完全一致）：

```js
// ui.html 里直接调，同步返回
var info = orangeisland.getMediaInfo("com.netease.cloudmusic");
// → { title, artist, album, coverUrl, durationMs, positionMs, isPlaying, state, packageName }
//   或 { error: "no_active_session", message: "..." }
```

要点：
- **只读**——不能暂停/切歌，控制只能由模型走 `control_media` 工具。
- 建议用 `setInterval` 每 1~2 秒轮询一次刷新进度条（参考 `mini-chatroom/ui.html` 的 2 秒轮询写法）。
- `coverUrl` 直接给 `<img src>` 用，封面下载与缓存由 app 自动处理，你**不需要**也无法存文件。

---

## 三、你的插件只做这三类事

标准 ZIP 插件结构：

```
netease-music/
├── manifest.json   ← 声明 allowedHosts + 你的工具 + config
├── main.js         ← 用 fetch 实现搜索/歌词/封面 URL
└── ui.html         ← 显示当前播放 + 搜索结果界面
```

### 1. manifest.json

```json
{
  "id": "com.you.netease-music",
  "name": "网易云助手",
  "version": "0.1.0",
  "allowedHosts": ["music.163.com"],
  "tools": [
    {
      "name": "search_songs",
      "description": "搜索歌曲，返回歌曲ID/歌名/歌手/时长",
      "parameters": [
        { "name": "keyword", "type": "string", "required": true, "description": "搜索关键词" }
      ]
    },
    {
      "name": "get_lyrics",
      "description": "根据歌曲ID获取歌词",
      "parameters": [
        { "name": "songId", "type": "string", "required": true, "description": "网易云歌曲ID" }
      ]
    }
  ],
  "ui": "ui.html",
  "config": []
}
```

**关键点**：
- `allowedHosts` 是你 `fetch` 的**唯一网络出口**。不在白名单的 host，`fetch` 直接拒绝（返回 `{ok:false, error:"Host 'xxx' not in plugin's allowedHosts list"}`）。填网易云域名。
- 媒体控制工具**不要**写进你的 manifest——那是 app 内置的，写了反而重复。
- `config` 字段目前只支持 `type:"string"`。
- 工具返回值会回到模型并触发继续回复，所以返回 JSON 要清晰（带稳定的歌曲 `id`、歌名、歌手），**别返回 HTML**。

### 2. main.js（fetch 是同步的，必须记住）

`fetch` **不是 Promise，是同步返回**。直接拿返回值用：

```js
exports.search_songs = function(args) {
  var r = fetch("https://music.163.com/api/search/get?s=" + encodeURIComponent(args.keyword), {
    method: "GET",
    headers: { /* 网易云需要的 header */ }
  });
  // r = { ok:bool, status:int, body:string }
  if (!r.ok) return { error: "search_failed", status: r.status, detail: r.error };
  var data = JSON.parse(r.body);
  return {
    songs: (data.result && data.result.songs || []).map(function(s) {
      return {
        id: s.id,                          // ← 稳定 ID，收藏要靠它
        name: s.name,
        artist: s.artists[0].name,
        duration: s.duration
      };
    })
  };
};
```

**fetch 契约（硬性）**：
- 返回 `{ ok, status, body }`，`body` 是字符串，需自己 `JSON.parse`。
- 响应体上限 **512KB**，超出被截断并加 `truncated:true`。
- 必须 https（明文 http 只允许局域网/localhost）。
- 超时 30 秒。
- 工具函数必须**同步 return**（没有 async fetch）。

### 3. ui.html（界面 + 读当前播放）

```js
var cfg = orangeisland.config;

function refresh() {
  var info = orangeisland.getMediaInfo("com.netease.cloudmusic");
  if (info.error) { /* 显示"未在播放" */ return; }
  // info.title / info.artist / info.coverUrl / info.positionMs / info.durationMs / info.isPlaying
  // 渲染播放卡片；封面直接 <img src={info.coverUrl}>
}
setInterval(refresh, 2000);
refresh();
```

---

## 四、main.js 里可直接用的全局变量

宿主在每次工具调用前自动注入（同步读取）：

| 变量 | 含义 | 示例 |
|---|---|---|
| `__OI_CONVERSATION_ID` | 当前对话 ID | `"conv_abc"` |
| `__OI_PROJECT_ID` | 当前项目 ID | `"proj_xyz"` |
| `__OI_USER_ID` | 用户/设备 ID（稳定 UUID） | — |
| `__OI_PLUGIN_CONFIG` | 你的齿轮配置（已是对象，直接用） | `{ "nickname":"小明" }` |

异步函数（main.js 里 `await`）：

| 函数 | 作用 | 返回 |
|---|---|---|
| `await sendChatMessage(text)` | 向**当前对话**发消息（会触发 AI 回复） | `"true"` / `"false"` |
| `await readChatHistory(limit)` | 读当前对话历史 | JSON 字符串数组 |
| `await readProjectMemories(projectId)` | 读项目长期记忆 | JSON 字符串数组 |

---

## 五、"一起听"怎么做（本期用分享版）

你框架里画的"邀请→弹窗→同意→进页面"，本期简化成**分享链接**，app 已支持 deep link：

```js
// 用户点"邀请一起听" → 调现有的 sendChatMessage 发一条带链接的消息
orangeisland.sendChatMessage(
  cfg.windowId,
  "🎵 一起来听《" + songName + "》\n点这里加入：orangeisland://music?text=播放" + songName
);
```

对方点链接，app 会把"播放xxx"作为消息发进会话，模型就会调 `control_media` 去播。

**真·多端同步播放**本期不做（需要后端房间服务 + 信令 + 跨端进度同步），是独立项目。

---

## 六、特别注意事项（对齐你的框架原文）

1. **收藏功能有坑**：网易云 API 要登录 cookie。app 没存这个 cookie，你的 `fetch` 带不上。
   - main.js 的 `fetch` 拿不到 WebView 里的 cookie（当前限制）。
   - **建议本期先不做收藏**，或后续单独评估。

2. **歌词不要自动发给模型**：框架原话是"只有用户主动发消息时才把歌词片段给家机"。
   - 实现：`get_lyrics` 工具按需返回即可，**不要**把整篇歌词塞进 `__OI_ACTIVE_MEMORY`，也**不要**每次工具调用都返回歌词。

3. **稳定 ID 绑定收藏/播放**：框架强调"收藏必须绑定稳定歌曲 ID"。
   - 所以你的工具返回里**一定要带网易云的 `song.id`**，不要只返回歌名。

4. **失败要诚实**：搜索失败就返回 `{error,...}`，别假装搜到。媒体控制的失败 app 已处理（VIP/版权锁定会返回 `warning`）。

---

## 七、参考已有插件示例

仓库里 `docs/plugin-examples/mini-chatroom/` 是一个完整可跑的插件（manifest + main.js + ui.html），**结构和你要写的一模一样**：
- 它的 `ui.html` 用 `setInterval(..., 2000)` 轮询 `getChatHistory` —— 你照抄成轮询 `getMediaInfo` 即可。
- 它的 `main.js` 用 `exports.xxx = function(args){...}` + `await readProjectMemories()` —— 你的 fetch 工具照这个模板写。

---

## 八、开发步骤

1. 新建文件夹 `netease-music/`，放 `manifest.json` / `main.js` / `ui.html`。
2. manifest 里 `allowedHosts` 填 `["music.163.com"]`，先写一个 `search_songs` 工具。
3. main.js 里用 `fetch` 打网易云搜索接口，返回结构化 JSON（带 `song.id`）。
4. 打成 zip，在「设置 → 插件」里安装，开个对话让模型搜歌，验证 `fetch` 通不通。
5. 通了再加 `get_lyrics`、ui.html 显示当前播放。
6. **媒体控制（播放/暂停/切歌）直接在对话里说"播放/暂停/下一首"**，模型会自己调 app 的 `control_media`，你**什么都不用做**。

---

## 附：能力边界速查

| 能力 | 谁做 | 备注 |
|---|---|---|
| 读当前播放/进度/状态 | app 内置 `get_now_playing` | ✅ 已完成 |
| 播放/暂停/上下首/seek(ms) | app 内置 `control_media` | ✅ 已完成 |
| 列出有 session 的 app | app 内置 `list_media_apps` | ✅ 已完成 |
| 插件 UI 显示当前播放 | 你 → `orangeisland.getMediaInfo()` | 只读 |
| 搜索/歌词/封面 URL | 你 → `fetch` + `allowedHosts` | |
| 一起听邀请（分享版） | 你 → `sendChatMessage` + deep link | |
| 收藏 | 你 → `fetch`（需 cookie，⚠️ 本期建议先不做） | |
| 封面本地缓存 | app 自动处理（你只拿 `coverUrl`） | 你无法存文件 |
| 真·同步播放一起听 | 独立项目，本期不做 | |
