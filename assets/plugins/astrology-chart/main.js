var BASE_URL = 'https://json.freeastrologyapi.com';
var LAST_ERROR = null;

function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (typeof cfg === 'object' && cfg) return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}

function getApiKey() {
  return (getConfig().api_key || '').trim();
}

function requireKey() {
  if (!getApiKey()) return { success: false, error: '未配置 API Key，请在插件设置中填写 freeastrologyapi.com 的 Key' };
  return null;
}

function buildPayload(params) {
  params = params || {};
  var payload = {
    year: Math.floor(params.year || 2000),
    month: Math.floor(params.month || 1),
    date: Math.floor(params.date || 1),
    hours: Math.floor(params.hours || 0),
    minutes: Math.floor(params.minutes || 0),
    seconds: Math.floor(params.seconds !== undefined ? params.seconds : 0),
    latitude: Number(params.latitude || 0),
    longitude: Number(params.longitude || 0),
    timezone: Number(params.timezone || 0),
    config: {
      language: (params.language || 'en').trim(),
      observation_point: (params.observation_point || 'topocentric').trim(),
      ayanamsha: 'tropical',
      house_system: (params.house_system || 'Placidus').trim()
    }
  };
  return payload;
}

// 单次请求超时。host (PluginSandbox) 对整个工具调用有 30s 硬超时，而
// astro_interpret 会串行发起 3 次 API 调用；若单次给到 15s，最坏 45s 必然被
// host 掐断（表现为 AI 说“联系中断”）。这里压到 8s，使嵌套链路在最坏
// 8s×3≈24s 内收敛，远离 host 上限。重试只发生在网络层失败时（见 fetchWithRetry）。
var REQUEST_TIMEOUT_MS = 8000;

// Busy-wait sleep —— host 的 fetch 是同步语义（QuickJS function，非 async），
// 插件里没有 Promise/await 可用，所以退避只能用同步忙等。仅在网络层失败的重试
// 之间触发，稳态请求零开销。
function sleepSync(ms) {
  var deadline = Date.now() + ms;
  while (Date.now() < deadline) { /* spin */ }
}

// 是否属于“值得重试”的网络层失败：status===0 是 host 对连接失败 / 读超时 /
// DNS 失败的统一编码（HTTP 协议没有 0 状态码）。4xx/5xx 是服务端语义错误，
// 重试只会加剧限流，一律不重试。
function isTransientFailure(resp) {
  return !resp || resp.status === 0;
}

// 指数退避（带 ±20% 抖动）。固定序列 500 / 1200 / 2900 ms，避免多端同步重试
// 造成的惊群。第 n 次重试前的等待（n 从 1 开始）。
function backoffDelay(n) {
  var base = n === 1 ? 500 : (n === 2 ? 1200 : 2900);
  var jitter = base * 0.2 * (Math.random() * 2 - 1);
  return Math.max(0, Math.round(base + jitter));
}

// 带重试的 fetch。最多尝试 maxAttempts 次；仅对网络层失败重试。
function fetchWithRetry(url, payload, maxAttempts) {
  var attempts = maxAttempts || 3;
  var lastResp = null;
  for (var i = 0; i < attempts; i++) {
    var raw;
    try {
      raw = fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-api-key': getApiKey()
        },
        body: JSON.stringify(payload),
        timeout: REQUEST_TIMEOUT_MS
      });
    } catch (e) {
      // fetch 本体抛异常同样视为网络层失败，可重试。
      lastResp = { ok: false, status: 0, error: '请求异常: ' + (e.message || String(e)) };
      if (i < attempts - 1) { sleepSync(backoffDelay(i + 1)); continue; }
      break;
    }
    lastResp = (typeof raw === 'string') ? JSON.parse(raw) : raw;
    if (!isTransientFailure(lastResp)) break;
    if (i < attempts - 1) sleepSync(backoffDelay(i + 1));
  }
  return lastResp;
}

function apiCall(endpoint, payload) {
  var url = BASE_URL + endpoint;
  var resp = fetchWithRetry(url, payload, 3);

  if (!resp || !resp.ok) {
    // status===0 = 连接失败 / 超时 / DNS 失败。HTTP 没有 0 状态码，直接吐
    // “HTTP 0” 会让 AI 转述成 “连不上 HTTP 0 / 联系中断”，故替换成可读文案。
    if (!resp || resp.status === 0) {
      LAST_ERROR = '网络连接失败（可能超时或占星服务暂不可达），已重试 3 次仍失败';
    } else {
      LAST_ERROR = resp.error || ('HTTP ' + resp.status);
    }
    return { success: false, error: LAST_ERROR };
  }

  var text = resp.body || '';
  var data;
  try {
    data = text ? JSON.parse(text) : {};
  } catch (e) {
    return { success: false, error: '接口返回非 JSON: ' + (text.slice(0, 200) || '空响应') };
  }

  if (data && data.statusCode && data.statusCode !== 200) {
    return { success: false, error: data.message || ('API 错误码: ' + data.statusCode) };
  }

  LAST_ERROR = null;
  return { success: true, data: data };
}

/* ---------- 工具函数 ---------- */

function astro_planets(params) {
  var err = requireKey();
  if (err) return err;
  var payload = buildPayload(params);
  var res = apiCall('/western/planets', payload);
  if (!res.success) return res;
  var output = res.data.output || [];
  var planets = [];
  for (var i = 0; i < output.length; i++) {
    var p = output[i];
    var nameObj = p.planet || {};
    var signObj = p.zodiac_sign || {};
    planets.push({
      name: nameObj.en || '',
      nameLocal: nameObj[payload.config.language] || nameObj.en || '',
      fullDegree: p.fullDegree,
      normDegree: p.normDegree,
      isRetro: String(p.isRetro || 'False'),
      signNumber: signObj.number,
      signName: (signObj.name || {}).en || '',
      signNameLocal: (signObj.name || {})[payload.config.language] || (signObj.name || {}).en || ''
    });
  }
  return { success: true, data: { planets: planets, birthInfo: {
    year: payload.year, month: payload.month, date: payload.date,
    hours: payload.hours, minutes: payload.minutes,
    latitude: payload.latitude, longitude: payload.longitude, timezone: payload.timezone
  }}};
}

function astro_houses(params) {
  var err = requireKey();
  if (err) return err;
  var payload = buildPayload(params);
  var res = apiCall('/western/houses', payload);
  if (!res.success) return res;
  var houses = [];
  var rawHouses = (res.data.output || {}).Houses || [];
  for (var i = 0; i < rawHouses.length; i++) {
    var h = rawHouses[i];
    var signObj = h.zodiac_sign || {};
    houses.push({
      house: h.House,
      degree: h.degree,
      normDegree: h.normDegree,
      signNumber: signObj.number,
      signName: (signObj.name || {}).en || '',
      signNameLocal: (signObj.name || {})[payload.config.language] || (signObj.name || {}).en || ''
    });
  }
  return { success: true, data: { houses: houses }};
}

function astro_aspects(params) {
  var err = requireKey();
  if (err) return err;
  var payload = buildPayload(params);
  var res = apiCall('/western/aspects', payload);
  if (!res.success) return res;
  var output = res.data.output || [];
  var aspects = [];
  for (var i = 0; i < output.length; i++) {
    var a = output[i];
    aspects.push({
      planet1: (a.planet_1 || {}).en || '',
      planet2: (a.planet_2 || {}).en || '',
      aspect: (a.aspect || {}).en || ''
    });
  }
  return { success: true, data: { aspects: aspects, count: aspects.length }};
}

function astro_wheel_chart(params) {
  var err = requireKey();
  if (err) return err;
  var payload = buildPayload(params);
  var res = apiCall('/western/natal-wheel-chart', payload);
  if (!res.success) return res;
  return { success: true, data: { wheelChartUrl: res.data.output || '' }};
}

function astro_natal_chart(params) {
  var err = requireKey();
  if (err) return err;

  var payload = buildPayload(params);
  var planetsRes = apiCall('/western/planets', payload);
  var housesRes = apiCall('/western/houses', payload);
  var aspectsRes = apiCall('/western/aspects', payload);

  var planets = [];
  if (planetsRes.success) {
    var po = planetsRes.data.output || [];
    for (var i = 0; i < po.length; i++) {
      var p = po[i];
      var signObj = p.zodiac_sign || {};
      planets.push({
        name: (p.planet || {}).en || '',
        fullDegree: p.fullDegree,
        normDegree: p.normDegree,
        isRetro: String(p.isRetro || 'False'),
        signNumber: signObj.number,
        signName: (signObj.name || {}).en || ''
      });
    }
  }

  var houses = [];
  if (housesRes.success) {
    var ho = (housesRes.data.output || {}).Houses || [];
    for (var i = 0; i < ho.length; i++) {
      var h = ho[i];
      var signObj = h.zodiac_sign || {};
      houses.push({
        house: h.House,
        degree: h.degree,
        normDegree: h.normDegree,
        signNumber: signObj.number,
        signName: (signObj.name || {}).en || ''
      });
    }
  }

  var aspects = [];
  if (aspectsRes.success) {
    var ao = aspectsRes.data.output || [];
    for (var i = 0; i < ao.length; i++) {
      var a = ao[i];
      aspects.push({
        planet1: (a.planet_1 || {}).en || '',
        planet2: (a.planet_2 || {}).en || '',
        aspect: (a.aspect || {}).en || ''
      });
    }
  }

  var errors = [];
  if (!planetsRes.success) errors.push('行星: ' + planetsRes.error);
  if (!housesRes.success) errors.push('宫位: ' + housesRes.error);
  if (!aspectsRes.success) errors.push('相位: ' + aspectsRes.error);

  return {
    success: errors.length === 0 || (planets.length > 0),
    error: errors.length > 0 ? errors.join('；') : undefined,
    data: {
      planets: planets,
      houses: houses,
      aspects: aspects,
      birthInfo: {
        year: payload.year, month: payload.month, date: payload.date,
        hours: payload.hours, minutes: payload.minutes,
        latitude: payload.latitude, longitude: payload.longitude, timezone: payload.timezone
      }
    }
  };
}

/* ---------- 基础中文解盘 ---------- */

var SIGN_CN = {
  'Aries': '白羊座', 'Taurus': '金牛座', 'Gemini': '双子座',
  'Cancer': '巨蟹座', 'Leo': '狮子座', 'Virgo': '处女座',
  'Libra': '天秤座', 'Scorpio': '天蝎座', 'Sagittarius': '射手座',
  'Capricorn': '摩羯座', 'Aquarius': '水瓶座', 'Pisces': '双鱼座'
};

var PLANET_CN = {
  'Sun': '太阳', 'Moon': '月亮', 'Mercury': '水星', 'Venus': '金星',
  'Mars': '火星', 'Jupiter': '木星', 'Saturn': '土星',
  'Uranus': '天王星', 'Neptune': '海王星', 'Pluto': '冥王星',
  'Ascendant': '上升点', 'Descendant': '下降点', 'MC': '天顶', 'IC': '天底',
  'Lilith': '莉莉丝', 'Chiron': '凯龙星', 'Mean Node': '北交点', 'True Node': '真北交点',
  'Ceres': '谷神星', 'Vesta': '灶神星', 'Juno': '婚神星', 'Pallas': '智神星'
};

var HOUSE_MEANING = [
  '自我与外在形象', '财富与价值观', '沟通与学习', '家庭与根基',
  '创造与恋爱', '工作与健康', '婚姻与合作关系', '转化与共享资源',
  '信仰与远行', '事业与社会地位', '社交与理想', '潜意识与灵性'
];

function signCn(name) { return SIGN_CN[name] || name; }
function planetCn(name) { return PLANET_CN[name] || name; }

function findPlanet(planets, name) {
  for (var i = 0; i < planets.length; i++) {
    if (planets[i].name === name) return planets[i];
  }
  return null;
}

function findHouse(houses, num) {
  for (var i = 0; i < houses.length; i++) {
    if (houses[i].house === num) return houses[i];
  }
  return null;
}

function houseOfPlanet(planet, houses) {
  var deg = planet.fullDegree;
  for (var i = 0; i < houses.length; i++) {
    var h = houses[i];
    var nextH = houses[(i + 1) % houses.length];
    var start = h.degree;
    var end = nextH.degree;
    // Handle wrap-around at 360
    if (end < start) {
      if (deg >= start || deg < end) return h.house;
    } else {
      if (deg >= start && deg < end) return h.house;
    }
  }
  return 1;
}

function astro_interpret(params) {
  var err = requireKey();
  if (err) return err;

  var payload = buildPayload(params);
  var chartRes = astro_natal_chart(params);
  if (!chartRes.success && (!chartRes.data || !chartRes.data.planets || chartRes.data.planets.length === 0)) {
    return { success: false, error: chartRes.error || '无法获取星盘数据' };
  }

  var planets = chartRes.data.planets || [];
  var houses = chartRes.data.houses || [];

  var sun = findPlanet(planets, 'Sun');
  var moon = findPlanet(planets, 'Moon');
  var asc = findPlanet(planets, 'Ascendant');
  var mercury = findPlanet(planets, 'Mercury');
  var venus = findPlanet(planets, 'Venus');
  var mars = findPlanet(planets, 'Mars');
  var jupiter = findPlanet(planets, 'Jupiter');
  var saturn = findPlanet(planets, 'Saturn');

  var lines = [];
  lines.push('═══ 本命星盘基础解读 ═══');
  lines.push('');

  if (sun) {
    lines.push('☉ 太阳落在' + signCn(sun.signName) + '（第' + houseOfPlanet(sun, houses) + '宫）');
    lines.push('  核心人格与自我表达带有' + signCn(sun.signName) + '的特质。');
    lines.push('');
  }
  if (moon) {
    lines.push('☽ 月亮落在' + signCn(moon.signName) + '（第' + houseOfPlanet(moon, houses) + '宫）');
    lines.push('  情感需求与内在安全感偏向' + signCn(moon.signName) + '模式。');
    lines.push('');
  }
  if (asc) {
    lines.push('↑ 上升点在' + signCn(asc.signName) + '（第1宫）');
    lines.push('  给外界的第一印象偏向' + signCn(asc.signName) + '气质。');
    lines.push('');
  }
  if (mercury) {
    lines.push('☿ 水星落在' + signCn(mercury.signName) + '（第' + houseOfPlanet(mercury, houses) + '宫）');
    lines.push('  思维方式与表达风格偏向' + signCn(mercury.signName) + '。');
    lines.push('');
  }
  if (venus) {
    lines.push('♀ 金星落在' + signCn(venus.signName) + '（第' + houseOfPlanet(venus, houses) + '宫）');
    lines.push('  爱情观与审美偏好偏向' + signCn(venus.signName) + '。');
    lines.push('');
  }
  if (mars) {
    lines.push('♂ 火星落在' + signCn(mars.signName) + '（第' + houseOfPlanet(mars, houses) + '宫）');
    lines.push('  行动力与欲望表达方式偏向' + signCn(mars.signName) + '。');
    lines.push('');
  }

  // Highlight stellium (3+ planets in same sign)
  var signCounts = {};
  for (var i = 0; i < planets.length; i++) {
    var sn = planets[i].signName;
    signCounts[sn] = (signCounts[sn] || 0) + 1;
  }
  var stellium = [];
  for (var k in signCounts) {
    if (signCounts[k] >= 3) stellium.push(k);
  }
  if (stellium.length > 0) {
    lines.push('✦ 群星聚集：');
    for (var i = 0; i < stellium.length; i++) {
      lines.push('  ' + signCn(stellium[i]) + '区域有' + signCounts[stellium[i]] + '颗行星，该领域能量集中。');
    }
    lines.push('');
  }

  // Retrograde planets
  var retro = [];
  for (var i = 0; i < planets.length; i++) {
    if (planets[i].isRetro === 'True') retro.push(planetCn(planets[i].name));
  }
  if (retro.length > 0) {
    lines.push('↺ 逆行行星：' + retro.join('、'));
    lines.push('  相关领域可能需要更多内省与反复磨合。');
    lines.push('');
  }

  lines.push('═══ 行星宫位简表 ═══');
  var personalPlanets = ['Sun', 'Moon', 'Mercury', 'Venus', 'Mars'];
  for (var i = 0; i < personalPlanets.length; i++) {
    var p = findPlanet(planets, personalPlanets[i]);
    if (p) {
      var h = houseOfPlanet(p, houses);
      lines.push(planetCn(p.name) + ' → 第' + h + '宫（' + HOUSE_MEANING[h - 1] + '）');
    }
  }

  return {
    success: true,
    data: {
      interpretation: lines.join('\n'),
      summary: {
        sunSign: sun ? signCn(sun.signName) : '',
        moonSign: moon ? signCn(moon.signName) : '',
        risingSign: asc ? signCn(asc.signName) : '',
        retrogradeCount: retro.length
      }
    }
  };
}

/* ---------- 导出 ---------- */

exports.astro_planets = astro_planets;
exports.astro_houses = astro_houses;
exports.astro_aspects = astro_aspects;
exports.astro_wheel_chart = astro_wheel_chart;
exports.astro_natal_chart = astro_natal_chart;
exports.astro_interpret = astro_interpret;
