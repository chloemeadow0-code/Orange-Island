// 网络隔离诊断 v1.0.0
// main.js 只测试宿主 fetch，不做搜索、不做播放、不做 deeplink

var EXAMPLE = "https://example.com/";
var NETEASE_HOME = "https://music.163.com/";
var NETEASE_SEARCH = "https://music.163.com/api/search/get?s=%E7%88%B1%E7%88%B1%E7%88%B1&type=1&limit=1&offset=0";

function text(value) {
  return String(value == null ? "" : value).trim();
}

function readConfig() {
  try {
    if (typeof __OI_PLUGIN_CONFIG !== "undefined" && __OI_PLUGIN_CONFIG) {
      return typeof __OI_PLUGIN_CONFIG === "string" ? JSON.parse(__OI_PLUGIN_CONFIG) : __OI_PLUGIN_CONFIG;
    }
  } catch (e) {}
  return {};
}

function globalString(name) {
  try {
    if (name === "conversation" && typeof __OI_CONVERSATION_ID === "string") return text(__OI_CONVERSATION_ID);
    if (name === "project" && typeof __OI_PROJECT_ID === "string") return text(__OI_PROJECT_ID);
    if (name === "user" && typeof __OI_USER_ID === "string") return text(__OI_USER_ID);
  } catch (e) {}
  return "";
}

function preview(value) {
  return String(value == null ? "" : value).slice(0, 900);
}

function fetchOnce(url, mode) {
  var response;
  try {
    response = fetch(url, {
      method: "GET",
      headers: {
        "Accept": "application/json, text/plain, text/html, */*",
        "Referer": "https://music.163.com/",
        "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
      },
      timeout: 18000
    });
  } catch (e) {
    return {
      ok: false,
      mode: mode,
      requestUrl: url,
      httpStatus: 0,
      errorCode: "FETCH_EXCEPTION",
      message: String(e && e.message || e),
      rawSummary: "",
      responseKeys: []
    };
  }

  if (response && typeof response.then === "function") {
    return {
      ok: false,
      mode: mode,
      requestUrl: url,
      httpStatus: 0,
      errorCode: "ASYNC_FETCH_UNSUPPORTED",
      message: "宿主 fetch 返回了 Promise，当前契约要求同步返回",
      rawSummary: "",
      responseKeys: []
    };
  }

  var ok = response && response.ok === true;
  var status = Number(response && response.status) || 0;
  var body = response && response.body;
  if (body === undefined && response && response.data !== undefined) body = response.data;
  var bodyText = typeof body === "string" ? body : JSON.stringify(body == null ? "" : body);

  if (!ok) {
    return {
      ok: false,
      mode: mode,
      requestUrl: url,
      httpStatus: status,
      errorCode: text(response && (response.error || response.message)) || "HTTP_REQUEST_FAILED",
      message: text(response && (response.error || response.message)) || "网络请求失败",
      rawSummary: preview(bodyText),
      responseKeys: response && typeof response === "object" ? Object.keys(response) : []
    };
  }

  return {
    ok: true,
    mode: mode,
    requestUrl: url,
    httpStatus: status || 200,
    rawSummary: preview(bodyText),
    bodyLength: bodyText ? bodyText.length : 0,
    responseKeys: response && typeof response === "object" ? Object.keys(response) : []
  };
}

exports.get_plugin_context = function () {
  return {
    ok: true,
    conversationId: globalString("conversation"),
    projectId: globalString("project"),
    userId: globalString("user"),
    config: readConfig()
  };
};

exports.diagnose_main_fetch = function () {
  var probes = [
    fetchOnce(EXAMPLE, "example_basic"),
    fetchOnce(NETEASE_HOME, "music_home"),
    fetchOnce(NETEASE_SEARCH, "music_search")
  ];

  var conclusion = "需要看三个 probe 的结果";
  if (!probes[0].ok) {
    conclusion = "example.com 也失败，优先查橘子岛的 main.js fetch/宿主网络层";
  } else if (probes[1].ok && !probes[2].ok) {
    conclusion = "网易云首页通了，但搜索接口不通，优先查网易云接口兼容性或请求头";
  } else if (probes[1].ok && probes[2].ok) {
    conclusion = "main.js fetch 没问题，问题不在宿主主网络层";
  } else if (!probes[1].ok) {
    conclusion = "example.com 通、网易云首页不通，优先查网易云域名/证书/站点策略";
  }

  return {
    ok: true,
    conclusion: conclusion,
    probes: probes
  };
};
