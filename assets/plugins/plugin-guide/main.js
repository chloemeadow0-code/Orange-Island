// 插件开发指南 v2.0 —— Orange Island 插件开发完整规范
// AI 在帮用户编写/调试插件前必须调用 get_plugin_dev_guide 读取。

var GUIDE = '# Orange Island 插件开发规范 v2.0\n\
\n\
> 本规范由实战调试得出，**请严格遵守**，否则会重蹈已知的坑。\n\
> 涵盖 main.js（QuickJS 沙箱）、ui.html（WebView）、manifest、打包、故障排查全流程。\n\
\n\
## 1. 目录结构与打包\n\
\n\
一个插件最少 2 个文件，最多 3 个，打包成 zip（文件放在 zip **根目录**，不要套文件夹）：\n\
\n\
```\n\
my-plugin.zip\n\
├── manifest.json   # 元数据 + 工具声明 + 配置项声明\n\
├── main.js         # CommonJS 工具实现（exports.xxx = function(params){...}）\n\
└── ui.html         # 可选：插件 UI 页面（完整 HTML 文档）\n\
```\n\
\n\
host (PluginLoader) 校验规则：\n\
- `manifest.id`：必须匹配 `^[a-z0-9](?:[a-z0-9_.\\-]*[a-z0-9])?$`（小写字母/数字/`_`/`.`/`-`，不能以分隔符开头/结尾）\n\
- `manifest.tools`：至少 1 个；工具名必须匹配 `^[a-zA-Z_][a-zA-Z0-9_]*$`（合法 JS 标识符）；不能重复\n\
- `manifest.ui`（若设）：必须匹配 `^[a-z0-9_\\-]+\\.html$`（扁平文件名，**不能含路径/子目录**）\n\
- `manifest.config`（若设）：字段名同工具名规则；不能重复\n\
- zip 内条目：路径不能以 `/`、`\\` 开头，不能含 `..`（防穿越）；每个解压条目上限 5MB，总条目上限 100\n\
\n\
## 2. manifest.json 字段\n\
\n\
```json\n\
{\n\
  "id": "com.example.myplugin",\n\
  "name": "我的插件",\n\
  "version": "1.0.0",\n\
  "author": "作者名",\n\
  "description": "一句话描述",\n\
  "icon": "🔮",\n\
  "allowedHosts": ["api.example.com"],\n\
  "ui": "ui.html",\n\
  "config": [\n\
    {\n\
      "name": "user_nickname",\n\
      "type": "string",\n\
      "label": "用户昵称",\n\
      "description": "在插件中显示的昵称",\n\
      "required": true,\n\
      "placeholder": "我的昵称"\n\
    }\n\
  ],\n\
  "tools": [\n\
    {\n\
      "name": "do_something",\n\
      "description": "这个工具做什么",\n\
      "parameters": [\n\
        {"name": "text", "type": "string", "required": true, "description": "输入文字"},\n\
        {"name": "limit", "type": "integer", "required": false, "description": "数量上限"}\n\
      ]\n\
    }\n\
  ]\n\
}\n\
```\n\
\n\
**字段说明**：\n\
- `allowedHosts`：**fetch() 只能访问这些域名**（小写，不带端口/协议）。空数组 = 禁止任何网络。例：要连 Supabase 就填 `["xxx.supabase.co"]`。子域名自动放行（`api.example.com` 允许 `*.api.example.com`）。\n\
- `ui`：插件 UI 的 HTML 文件名。设了之后，插件列表里点的🌐图标会打开它。\n\
- `config`：用户在装/打开插件时会弹一个配置表单，用户填的值会被注入到 main.js 和 ui.html（见第 5 节）。\n\
- `tools`：AI 可调用的工具。工具名在 LLM 那边会变成 `plugin__<sanitizedId>__<toolName>`。\n\
- `type`：`string` | `integer` | `number` | `boolean`（`int`/`bool` 等别名也接受）。\n\
\n\
## 3. main.js 工具实现（QuickJS 沙箱）\n\
\n\
### 3.1 核心契约（必须遵守）\n\
\n\
```js\n\
// 1) 用 CommonJS exports，不要 export / module.exports（host 只识别 exports.xxx）\n\
exports.my_tool = function (params) {\n\
    // 2) 函数必须是【同步】的！不能用 async/await/Promise。\n\
    //    host 同步取返回值并 JSON.stringify，async 函数会返回 {} （Promise 序列化结果）。\n\
    params = params || {};\n\
    if (!params.text) return { success: false, error: "missing text" };\n\
\n\
    // 3) 想发网络请求用同步 fetch（见第 6 节）。\n\
    var resp = fetch("https://api.example.com/data", {\n\
        method: "POST",\n\
        headers: { "Content-Type": "application/json" },\n\
        body: JSON.stringify({ q: params.text })\n\
    });\n\
    // 4) fetch 返回的不是浏览器 Response，而是 JSON 字符串！必须先 parse\n\
    var result = (typeof resp === "string") ? JSON.parse(resp) : resp;\n\
    if (!result || !result.ok) {\n\
        return { success: false, error: result ? (result.error || ("HTTP " + result.status)) : "无响应" };\n\
    }\n\
    var data = JSON.parse(result.body || "{}");\n\
    return { success: true, data: data };\n\
};\n\
```\n\
\n\
### 3.2 读取配置（向后兼容写法）\n\
\n\
**【致命坑】旧版宿主注入 `config` 全局变量，新版改为 `__OI_PLUGIN_CONFIG`。必须写兼容代码！**\n\
\n\
```js\n\
function getConfig() {\n\
  try {\n\
    // 新版 Orange Island\n\
    var cfg = (typeof __OI_PLUGIN_CONFIG !== "undefined") ? __OI_PLUGIN_CONFIG\n\
          : ((typeof __AGORA_PLUGIN_CONFIG !== "undefined") ? __AGORA_PLUGIN_CONFIG : undefined);\n\
    if (typeof cfg === "object" && cfg) return cfg;\n\
    if (typeof cfg === "string") return JSON.parse(cfg);\n\
  } catch (e) {}\n\
  return {};\n\
}\n\
\n\
function getDeviceId() {\n\
  var id = (typeof __OI_USER_ID !== "undefined") ? __OI_USER_ID\n\
         : ((typeof __AGORA_USER_ID !== "undefined") ? __AGORA_USER_ID : "");\n\
  return (typeof id === "string") ? id : "";\n\
}\n\
```\n\
\n\
### 3.3 host 注入的全局变量\n\
\n\
| 变量 | 类型 | 说明 |\n\
|---|---|---|\n\
| `__OI_USER_ID` | string | 当前设备的稳定 UUID（新版）|\n\
| `__OI_PLUGIN_CONFIG` | object/string | 用户填的配置值（新版）|\n\
| `__AGORA_USER_ID` | string | 旧版设备 ID（兼容用）|\n\
| `__AGORA_PLUGIN_CONFIG` | object/string | 旧版配置（兼容用）|\n\
| `__OI_TOOL_NAME` | string | 当前被调用的工具名 |\n\
| `__OI_TOOL_ARGS` | object | 调用参数（同 params）|\n\
| `fetch` | function | 同步 HTTP（见第 6 节）|\n\
| `console.log/warn/error` | function | 日志打到 logcat（tag: `plugin/<id>`）|\n\
\n\
## 4. fetch 约束（main.js 里）—— 【必读】\n\
\n\
### 4.1 返回值类型（最大坑）\n\
\n\
**QuickJS 沙箱里的 `fetch` 返回的是 JSON 字符串，不是浏览器 Response 对象！**\n\
\n\
```js\n\
var raw = fetch(url, options);\n\
// raw 是一个 JSON 字符串：\n\
// \'{ "ok": true, "status": 200, "body": "...响应文本...", "truncated": false }\'\n\
\n\
// 必须先 parse\n\
var resp = (typeof raw === "string") ? JSON.parse(raw) : raw;\n\
\n\
// 然后才能访问字段\n\
if (!resp || !resp.ok) { /* 处理错误 */ }\n\
var text = resp.body || "";\n\
var data = text ? JSON.parse(text) : {};\n\
```\n\
\n\
### 4.2 选项与限制\n\
\n\
```js\n\
var resp = fetch(url, {\n\
    method: "POST",      // GET | POST | PUT | PATCH | DELETE | HEAD\n\
    headers: {\n\
        "Content-Type": "application/json",\n\
        "Authorization": "Bearer xxx"\n\
    },\n\
    body: JSON.stringify({ q: "hello" }),  // 必须是字符串\n\
    timeout: 15000       // 毫秒，范围 1000-30000，默认 30000\n\
});\n\
```\n\
\n\
**安全规则**：\n\
- URL 必须 http/https；https 强制（除非目标是 localhost/LAN，可走 http）\n\
- 域名必须在 `manifest.allowedHosts` 里，否则请求被拦（返回 `{ok:false, status:0, error:"Host not in allowedHosts"}`）\n\
- 响应体上限 512KB；超出会被截断并设 `truncated:true`\n\
- 不支持 `await`，不支持 `.then()`，不支持 `.text()` / `.json()` 方法\n\
\n\
### 4.3 安全的请求封装模板\n\
\n\
```js\n\
var LAST_ERROR = null;\n\
\n\
function apiRequest(url, method, headers, body, timeout) {\n\
  var raw;\n\
  try {\n\
    raw = fetch(url, {\n\
      method: method || "GET",\n\
      headers: headers || {},\n\
      body: body || undefined,\n\
      timeout: timeout || 15000\n\
    });\n\
  } catch (e) {\n\
    LAST_ERROR = "请求异常: " + (e.message || String(e));\n\
    return null;\n\
  }\n\
  var resp = (typeof raw === "string") ? JSON.parse(raw) : raw;\n\
  if (!resp || !resp.ok) {\n\
    LAST_ERROR = resp ? (resp.error || ("HTTP " + (resp.status || 0))) : "无响应";\n\
    return null;\n\
  }\n\
  LAST_ERROR = null;\n\
  var text = resp.body || "";\n\
  try { return JSON.parse(text); } catch (e) { return text; }\n\
}\n\
```\n\
\n\
## 5. ui.html 插件 UI 页面\n\
\n\
ui.html 是一个完整的 HTML 文档（`<!DOCTYPE html>...`），在 WebView 里加载。\n\
\n\
**host 会自动**：\n\
1. 在页面 HTML 前注入一段 `<script>` bootstrap，定义 `window.orangeisland`\n\
2. 把 `__oiNative` 作为 JavascriptInterface 注入 WebView\n\
\n\
### 5.1 桥接对象改名（兼容写法）\n\
\n\
**【致命坑】旧版宿主注入 `window.agora`，新版改为 `window.orangeisland`。**\n\
UI 里必须同时检测两者：\n\
\n\
```js\n\
var bridge = (typeof orangeisland !== "undefined" && orangeisland)\n\
         ? orangeisland\n\
         : ((typeof agora !== "undefined" && agora) ? agora : null);\n\
\n\
if (!bridge || typeof bridge.call !== "function") {\n\
  // 桥接未加载，提示用户从插件列表打开\n\
}\n\
\n\
// 调用工具\n\
bridge.call("tool_name", { key: "value" }, function (resultJson) {\n\
  var result = (typeof resultJson === "string") ? JSON.parse(resultJson) : resultJson;\n\
  // ...\n\
});\n\
```\n\
\n\
### 5.2 读取配置\n\
\n\
```js\n\
function readConfig() {\n\
  try {\n\
    if (typeof orangeisland !== "undefined" && orangeisland.config) return orangeisland.config;\n\
  } catch (e) {}\n\
  try {\n\
    var cfg = (typeof __OI_PLUGIN_CONFIG !== "undefined") ? __OI_PLUGIN_CONFIG\n\
              : ((typeof __AGORA_PLUGIN_CONFIG !== "undefined") ? __AGORA_PLUGIN_CONFIG : undefined);\n\
    if (typeof cfg === "object" && cfg) return cfg;\n\
    if (typeof cfg === "string") return JSON.parse(cfg);\n\
  } catch (e) {}\n\
  return {};\n\
}\n\
```\n\
\n\
### 5.3 重要约束\n\
\n\
- ui.html 里的 `fetch` 是【浏览器原生 fetch】，返回 Promise，**可以**用 async/await（和 main.js 相反！）\n\
- 配置读取**优先用 `orangeisland.config`**（同步 getter，零竞态），兜底用 `__OI_PLUGIN_CONFIG` 全局\n\
- 页面 origin 是 `about:blank`；跨域请求靠目标服务器返回 `Access-Control-Allow-Origin: *`\n\
- WebView 禁用了 file/dom access；JS 不能读本地文件\n\
- ui.html 是完整 HTML 文档，必须 `<!DOCTYPE html>` 开头；不要把 JS 代码裸放在 DOCTYPE 前（会被当文字渲染）\n\
\n\
## 6. 配置项（manifest.config）工作流\n\
\n\
1. manifest 里声明 `config` 字段（name/type/label/required/placeholder 等）\n\
2. 用户点插件🌐图标时，若 config 非空且未填过，host 自动弹配置表单\n\
3. 用户填完保存，值存到 DataStore（按 pluginId 隔离）\n\
4. host 把值注入 main.js 的 `__OI_PLUGIN_CONFIG` 和 ui.html 的 `orangeisland.config`\n\
5. 插件列表的⚙齿轮按钮可随时改配置\n\
\n\
**type 字段**：目前只支持 `"string"`（渲染为文本输入框）；其他类型会被当成文本框（未来会扩展 number/boolean/select）。\n\
\n\
## 7. 打包成 zip\n\
\n\
```bash\n\
# 必须把 3 个文件放在 zip【根目录】，不能套文件夹\n\
zip my-plugin.zip manifest.json main.js ui.html\n\
```\n\
\n\
PowerShell：\n\
```powershell\n\
Compress-Archive -Path manifest.json,main.js,ui.html -DestinationPath my-plugin.zip\n\
```\n\
\n\
校验：解压后应该直接看到 manifest.json（不是 `my-plugin/manifest.json`）。\n\
\n\
## 8. 已知坑（实战血泪总结）\n\
\n\
| # | 坑 | 后果 | 解决 |\n\
|---|---|---|---|\n\
| 1 | main.js 工具函数 async | host 同步取返回值，async 返回 Promise 被序列化成 `{}` | 全部用同步函数 |\n\
| 2 | main.js 里 `await fetch(...)` | QuickJS 不支持 await，直接报错 | `var raw = fetch(...)` |\n\
| 3 | fetch 返回当 Response 对象用 | 访问 `.ok` / `.body` / `.status` 时全是 undefined，判定永远失败 | 先 `JSON.parse(raw)` |\n\
| 4 | 直接访问 `config` 全局变量 | `ReferenceError: config is not defined` | 用 `getConfig()` 封装兼容 `__OI_PLUGIN_CONFIG` / `__AGORA_PLUGIN_CONFIG` |\n\
| 5 | UI 里检测 `window.agora` | 新版宿主只注入 `orangeisland`，检测失败 | 优先检测 `orangeisland`，回退 `agora` |\n\
| 6 | manifest 缺少 `allowedHosts` | 沙箱拦截所有请求，返回 Host not in allowedHosts | 必填，至少填目标域名 |\n\
| 7 | manifest.ui 含路径 | 校验失败，插件无法加载 | 用 `"ui": "ui.html"`，不是 `"ui/index.html"` |\n\
| 8 | manifest.id 含大写/特殊字符 | 校验失败 | 只用小写字母/数字/`_`/`.`/`-` |\n\
| 9 | 工具名/参数名/config 字段名含 `-` | 校验失败 | 用下划线 `_`，不要用连字符 |\n\
| 10 | zip 套了文件夹 | host 找不到 manifest.json | 文件放 zip 根目录 |\n\
| 11 | `response.text()` / `.json()` | 沙箱 fetch 没有这些方法 | 直接读 `resp.body`（字符串） |\n\
| 12 | Supabase URL 末尾带 `/` | 拼接后变成 `//rest/v1/`，404 | `url.replace(/\\/+$/, "")` 去尾斜杠 |\n\
| 13 | `Array.prototype.filter` / `.some` / `.includes` | QuickJS 可能不支持或行为异常 | 改用 `for` 循环 + `indexOf` |\n\
| 14 | `JSON.stringify(undefined)` | 返回 undefined（不是字符串 `"undefined"`） | 显式判断 `data ? JSON.stringify(data) : undefined` |\n\
| 15 | 函数内用 `const` / `let` / `箭头函数` / `模板字符串` | QuickJS 支持但建议保守；`const` 在旧版可能有坑 | 用 `var` + 传统函数最稳 |\n\
\n\
## 9. 完整最小示例\n\
\n\
**manifest.json**：\n\
```json\n\
{\n\
  "id": "com.example.hello",\n\
  "name": "Hello",\n\
  "version": "1.0.0",\n\
  "allowedHosts": [],\n\
  "config": [{"name":"name","type":"string","label":"你的名字","required":true}],\n\
  "tools": [{\n\
    "name": "greet",\n\
    "description": "打招呼",\n\
    "parameters": []\n\
  }]\n\
}\n\
```\n\
\n\
**main.js**：\n\
```js\n\
function getConfig() {\n\
  try {\n\
    var cfg = (typeof __OI_PLUGIN_CONFIG !== "undefined") ? __OI_PLUGIN_CONFIG\n\
          : ((typeof __AGORA_PLUGIN_CONFIG !== "undefined") ? __AGORA_PLUGIN_CONFIG : undefined);\n\
    if (typeof cfg === "object" && cfg) return cfg;\n\
    if (typeof cfg === "string") return JSON.parse(cfg);\n\
  } catch (e) {}\n\
  return {};\n\
}\n\
\n\
exports.greet = function (params) {\n\
  var cfg = getConfig();\n\
  var name = cfg.name || "世界";\n\
  var deviceId = (typeof __OI_USER_ID === "string") ? __OI_USER_ID\n\
               : ((typeof __AGORA_USER_ID === "string") ? __AGORA_USER_ID : "");\n\
  return { success: true, message: "你好，" + name + "！", deviceId: deviceId };\n\
};\n\
```\n\
\n\
**ui.html**：\n\
```html\n\
<!DOCTYPE html>\n\
<html><head><meta charset="utf-8"><title>Hello</title></head>\n\
<body>\n\
<div id="out">加载中…</div>\n\
<script>\n\
  var bridge = (typeof orangeisland !== "undefined" && orangeisland)\n\
             ? orangeisland : ((typeof agora !== "undefined" && agora) ? agora : null);\n\
  if (!bridge || typeof bridge.call !== "function") {\n\
    document.getElementById("out").textContent = "桥接未加载";\n\
  } else {\n\
    var cfg = bridge.config || {};\n\
    document.getElementById("out").textContent = "你好，" + (cfg.name || "世界") + "！";\n\
  }\n\
</script>\n\
</body></html>\n\
```\n\
\n\
打包：`zip hello.zip manifest.json main.js ui.html`，然后在 app 设置→插件→Import plugin (.zip) 导入。\n\
\n\
## 10. 调试技巧\n\
\n\
1. **看 logcat**：插件代码里的 `console.log("xxx")` 会输出到 Android logcat，tag 为 `plugin/<id>`。\n\
2. **先用最小示例验证**：不要一上来就写几百行，先写一个 `greet` 工具确认环境正常。\n\
3. **逐步增加功能**：先通网络（fetch 能拿到数据），再处理业务逻辑。\n\
4. **manifest 改后重启 App**：host 只在启动时扫描插件目录，修改 manifest 后需要重启才能生效。\n\
5. **fetch 问题先隔离测试**：把请求参数和返回结果打印出来，确认域名在 allowedHosts 里、URL 拼接正确、返回已 parse。\n\
\n\
---\n\
*规范版本：v2.0 | 最后更新：2026-07-27*\n\
';\n\
\n\
exports.get_plugin_dev_guide = function () {\n\
    return { content: GUIDE, format: "markdown" };\n\
};\n