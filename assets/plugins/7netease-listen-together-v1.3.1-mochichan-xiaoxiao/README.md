# 网易云一起听 · 黑胶直连版 v1.3.1

## 本版内容

- 视觉页面调用 `search_songs` 搜索网易云
- `main.js` 使用同步 GET `/api/search/get`
- 搜索结果保留网易云稳定歌曲 ID、歌名、歌手、专辑、封面和时长
- 搜索失败显示错误码、HTTP 状态、请求 URL 和响应摘要
- 当前播放通过 `orangeisland.getMediaInfo("com.netease.cloudmusic")` 只读刷新
- 当前歌曲、封面、进度和播放状态持续显示
- 歌词工具补齐为 manifest 中声明的 `get_song_lyrics`
- 支持手动粘贴 LRC 或普通歌词并按歌曲 ID本地保存
- 支持本地收藏，使用歌曲 ID 去重
- 支持当前对话分享版一起听邀请

## 重要修复：不再用聊天控制媒体

橘子岛文档确认 `getMediaInfo` 是 UI 只读桥，`control_media` 是模型工具。上一版把播放、暂停、上一首、下一首和 seek 转成聊天文本，再调用聊天桥，这会导致拖动进度条跳进新聊天。

v1.3.1 已改成：

```text
视觉页只读显示媒体状态
播放控制按钮不发送聊天，不跳转
进度条禁用，不发送聊天
需要控制时，在当前对话中让 AI 调用内置 control_media
```

只有邀请和聊天输入会使用当前对话发送桥；没有当前对话上下文或桥时，只显示错误，不使用 deep link 兜底。

## 搜索失败的判断

如果页面显示 `HTTP_REQUEST_FAILED` 和 `HTTP 0`，说明宿主同步 fetch 没有拿到网易云 HTTP 响应。

## 指定歌曲打开

搜索结果的“打开网易云”使用 `orpheus://song/<songId>`。这个 deeplink 是否由宿主注册取决于橘子岛 Android 宿主。

## 安装

删除旧版插件后导入本 ZIP。ZIP 根目录包含：

```text
manifest.json
main.js
ui.html
README.md
```

作者：もちちゃん和晓晓
