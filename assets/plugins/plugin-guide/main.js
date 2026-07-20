// 插件开发指南 —— 为 AI 提供的 Orange Island 插件开发规范
//
// 本插件只暴露一个工具 get_plugin_dev_guide，返回一份 Markdown 文档。
// AI 在帮用户写/调试插件时调用此工具，按文档规范编写。

var GUIDE = '# Orange Island 插件开发规范\n\
\n\
本规范由实战调试得出，**请严格遵守**，否则会重蹈已知的坑。\n\
\n\
## 1. 目录结构与打包\n\
\n\
一个插件是 3 个文件，打包成 zip（文件放在 zip 根目录，不要套文件夹）：\n\
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
## 3. main.js 工具实现\n\
\n\
**核心契约（必须遵守）**：\n\
\n\
```js\n\
// 1) 用 CommonJS exports，不要 export / module.exports（host 只识别 exports.xxx）\n\
exports.my_tool = function (params) {\n\
    // 2) 函数必须是【同步】的！不能用 async/await/Promise。\n\
    //    host 同步取返回值并 JSON.stringify，async 函数会返回 {} （Promise 序列化结果）。\n\
    params = params || {};\n\
    if (!params.text) return { ok: false, error: "missing text" };\n\
\n\
    // 3) 想发网络请求用同步 fetch（见第 6 节）。\n\
    var resp = fetch("https://api.example.com/data", {\n\
        method: "POST",\n\
        headers: { "Content-Type": "application/json" },\n\
        body: JSON.stringify({ q: params.text })\n\
    });\n\
    if (!resp.ok) return { ok: false, status: resp.status, error: resp.body };\n\
    var data = JSON.parse(resp.body);\n\
    return { ok: true, data: data };\n\
};\n\
```\n\
\n\
**host 在每次工具调用前注入的全局变量**：\n\
\n\
| 变量 | 类型 | 说明 |\n\
|---|---|---|\n\
| `__OI_USER_ID` | string | 当前设备的稳定 UUID（永不变，跨所有插件共享）|\n\
| `__OI_PLUGIN_CONFIG` | object | 用户在配置弹窗填的值，形如 `{"user_nickname":"Alice"}`；无 config 字段时是 `{}` |\n\
| `__OI_TOOL_NAME` | string | 当前被调用的工具名（一般用不到）|\n\
| `__OI_TOOL_ARGS` | object | 调用参数（同 `params` 形参，一般用 params 即可）|\n\
| `fetch` | function | 同步 HTTP（见第 6 节）|\n\
| `console.log/warn/error` | function | 日志打到 logcat（tag: `plugin/<id>`）|\n\
\n\
**读取身份/配置的推荐写法**：\n\
```js\n\
function getDeviceId() {\n\
    return (typeof __OI_USER_ID === "string") ? __OI_USER_ID : "";\n\
}\n\
function getConfig() {\n\
    try {\n\
        if (typeof __OI_PLUGIN_CONFIG === "object" && __OI_PLUGIN_CONFIG) return __OI_PLUGIN_CONFIG;\n\
        if (typeof __OI_PLUGIN_CONFIG === "string") return JSON.parse(__OI_PLUGIN_CONFIG);\n\
    } catch (e) {}\n\
    return {};\n\
}\n\
```\n\
\n\
## 4. ui.html 插件 UI 页面\n\
\n\
ui.html 是一个完整的 HTML 文档（`<!DOCTYPE html>...`），在 WebView 里加载。\n\
\n\
**host 会自动**：\n\
1. 在页面 HTML 前注入一段 `<script>` bootstrap，定义 `window.orangeisland`（见下）\n\
2. 把 `__oiNative` 作为 JavascriptInterface 注入 WebView\n\
\n\
**`window.orangeisland` 桥 API**：\n\
```js\n\
// 异步调用本插件的某个工具（只能调 manifest 里声明的工具）\n\
orangeisland.call("tool_name", { key: "value" }, function (resultJson) {\n\
    var result = JSON.parse(resultJson);\n\
    // result 就是 main.js 里 return 的对象\n\
});\n\
\n\
// 同步读取配置（实时值，零时序竞态）\n\
var cfg = orangeisland.config;          // 用户填的配置对象\n\
var deviceId = orangeisland.deviceId;   // 设备 UUID\n\
```\n\
\n\
**重要约束（踩过的坑）**：\n\
- ui.html 里的 `fetch` 是【浏览器原生 fetch】，返回 Promise，**可以**用 async/await（这点和 main.js 相反！）\n\
- 配置读取**优先用 `orangeisland.config`**（同步 getter，零竞态），兜底用 `__OI_PLUGIN_CONFIG` 全局\n\
- 页面 origin 是 `about:blank`；跨域请求靠目标服务器返回 `Access-Control-Allow-Origin: *`（大多数后端 API 如 Supabase 默认支持）\n\
- WebView 禁用了 file/dom access；JS 不能读本地文件\n\
\n\
**ui.html 读配置的安全写法**：\n\
```js\n\
function readConfig() {\n\
    try {\n\
        if (typeof orangeisland !== "undefined" && orangeisland.config) return orangeisland.config;\n\
    } catch (e) {}\n\
    try {\n\
        if (typeof __OI_PLUGIN_CONFIG === "object") return __OI_PLUGIN_CONFIG;\n\
        if (typeof __OI_PLUGIN_CONFIG === "string") return JSON.parse(__OI_PLUGIN_CONFIG);\n\
    } catch (e) {}\n\
    return {};\n\
}\n\
// 直接读，不需要 waitForConfig 轮询（orangeisland.config 是同步 getter）\n\
var cfg = readConfig();\n\
var nickname = cfg.user_nickname || "默认值";\n\
```\n\
\n\
## 5. 配置项（manifest.config）工作流\n\
\n\
1. manifest 里声明 `config` 字段（name/type/label/required/placeholder 等）\n\
2. 用户点插件🌐图标时，若 config 非空且未填过，host 自动弹配置表单\n\
3. 用户填完保存，值存到 DataStore（按 pluginId 隔离）\n\
4. host 把值注入 main.js 的 `__OI_PLUGIN_CONFIG` 和 ui.html 的 `orangeisland.config`\n\
5. 插件列表的⚙齿轮按钮可随时改配置\n\
\n\
**type 字段**：目前只支持 `"string"`（渲染为文本输入框）；其他类型会被当成文本框（未来会扩展 number/boolean/select）。\n\
\n\
## 6. fetch 约束（main.js 里）\n\
\n\
**main.js 的 fetch 是【同步】的**，签名：\n\
```js\n\
var resp = fetch(url, options);\n\
// options: { method, headers, body, timeout }\n\
//   method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE" | "HEAD"\n\
//   headers: {key: value}\n\
//   body: string（JSON.stringify(...) 或纯文本）\n\
//   timeout: 1000-30000 ms（默认 30000）\n\
//\n\
// resp = { ok: bool, status: int, body: string, truncated?: bool, error?: string }\n\
```\n\
\n\
**安全规则**：\n\
- URL 必须 http/https；https 强制（除非目标是 localhost/LAN，可走 http）\n\
- 域名必须在 `manifest.allowedHosts` 里，否则请求被拦（返回 `{ok:false, status:0, error:"Host not in allowedHosts"}`）\n\
- 响应体上限 512KB；超出会被截断并设 `truncated:true`\n\
\n\
**ui.html 里的 fetch 是浏览器原生的**（Promise/Response），不受 allowedHosts 限制，但受 CORS 限制。\n\
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
## 8. 已知坑（必读）\n\
\n\
1. **main.js 工具函数不能 async**：host 同步取返回值，async 返回 Promise 会被序列化成 `{}`。\n\
2. **main.js fetch 不能 await**：是同步函数，直接 `var resp = fetch(...)`。\n\
3. **ui.html 读配置用 `orangeisland.config`**：不要靠全局 `__OI_PLUGIN_CONFIG`（有时序竞态）。`orangeisland.config` 是同步 getter，零竞态。\n\
4. **ui.html 是完整 HTML 文档**：必须 `<!DOCTYPE html>` 开头；不要把 JS 代码裸放在 DOCTYPE 前（会被当文字渲染）。\n\
5. **manifest.ui 不能含路径**：用 `"ui": "ui.html"`，不是 `"ui/index.html"`。\n\
6. **manifest.id 含特殊字符会被拒**：只能小写字母/数字/`_`/`.`/`-`。\n\
7. **工具名/参数名/config 字段名必须是合法 JS 标识符**：`^[a-zA-Z_][a-zA-Z0-9_]*$`。\n\
8. **exports 而非 export**：用 CommonJS `exports.tool_name = function(){}`；不要 ES module 的 `export`。\n\
9. **fetch 域名要加进 allowedHosts**：连 Supabase 就加 `["xxx.supabase.co"]`，否则请求被拦。\n\
10. **zip 文件放根目录**：不要套文件夹，否则 manifest.json 找不到。\n\
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
exports.greet = function (params) {\n\
    var cfg = (typeof __OI_PLUGIN_CONFIG === "object") ? __OI_PLUGIN_CONFIG : {};\n\
    var name = cfg.name || "世界";\n\
    var deviceId = (typeof __OI_USER_ID === "string") ? __OI_USER_ID : "";\n\
    return { message: "你好，" + name + "！", deviceId: deviceId };\n\
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
    var cfg = (typeof orangeisland !== "undefined" && orangeisland.config) ? orangeisland.config : {};\n\
    document.getElementById("out").textContent = "你好，" + (cfg.name || "世界") + "！";\n\
</script>\n\
</body></html>\n\
```\n\
\n\
打包：`zip hello.zip manifest.json main.js ui.html`，然后在 app 设置→插件→Import plugin (.zip) 导入。\n\
';

exports.get_plugin_dev_guide = function () {
    return { content: GUIDE, format: "markdown" };
};
