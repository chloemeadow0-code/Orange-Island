// 欲望系统 v3.0
// 配置只从Agora注入的__AGORA_PLUGIN_CONFIG读取。

var STATE_TABLE = "desire_state";
var THOUGHTS_TABLE = "desire_thoughts";
var FLIT_DECAY = 0.90;
var FIXATION_GROW = 1.08;
var UPGRADE_THRESHOLD = 0.75;
var FEEDBACK_THRESHOLD = 0.80;
var DROP_BELOW = 0.02;
var FATIGUE_GATE = 0.72;

function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== "undefined") ? __OI_PLUGIN_CONFIG : ((typeof __AGORA_PLUGIN_CONFIG !== "undefined") ? __AGORA_PLUGIN_CONFIG : undefined);
    if (typeof cfg === "object" && cfg) return cfg;
    if (typeof cfg === "string") return JSON.parse(cfg);
  } catch (e) {}
  return {};
}

function getUrl() { var u = getConfig().supabase_url || ""; return u.replace(/\/+$/, ""); }
function getKey() { return (getConfig().supabase_key || "").trim(); }
function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

var LAST_FETCH_ERROR = null;

function sbRequest(table, method, data, query) {
  var key = getKey();
  var url = getUrl() + "/rest/v1/" + table + (query || "");
  var headers = {
    "apikey": key,
    "Authorization": "Bearer " + key,
    "Content-Type": "application/json",
    "Accept": "application/json"
  };
  if (method === "POST" || method === "PATCH") headers["Prefer"] = "return=representation";
  var respRaw;
  try {
    respRaw = fetch(url, {
      method: method,
      headers: headers,
      body: data ? JSON.stringify(data) : undefined,
      timeout: 10000
    });
  } catch (e) {
    LAST_FETCH_ERROR = "请求异常: " + (e.message || e);
    return null;
  }
  // QuickJS sandbox fetch returns a JSON string, not a native Response object.
  var resp = (typeof respRaw === "string") ? JSON.parse(respRaw) : respRaw;
  if (!resp || !resp.ok) {
    LAST_FETCH_ERROR = resp ? (resp.error || ("HTTP " + (resp.status || 0))) : "未知网络错误";
    return null;
  }
  LAST_FETCH_ERROR = null;
  try { return JSON.parse(resp.body || "[]"); } catch (e) { return []; }
}

function requireConfig() {
  if (!getUrl() || !getKey()) return {success:false,error:"请先在外面的插件齿轮中配置Supabase URL和Key"};
  return null;
}

function readState() {
  var rows = sbRequest(STATE_TABLE, "GET", null, "?id=eq.1");
  return rows && rows.length ? rows[0] : null;
}

function readThoughts() {
  return sbRequest(THOUGHTS_TABLE, "GET", null, "?order=strength.desc&limit=50") || [];
}

exports.desire_get_state = function () {
  var error = requireConfig();
  if (error) return error;
  var state = readState();
  if (!state) {
    var msg = "无状态数据，请先在Supabase建表并插入id为1的初始状态";
    if (LAST_FETCH_ERROR) msg += "（" + LAST_FETCH_ERROR + "）";
    return {success:false,error:msg};
  }
  return {success:true,data:{state:state,thoughts:readThoughts()}};
};

exports.desire_tick = function () {
  var error = requireConfig();
  if (error) return error;
  var state = readState();
  if (!state) return {success:false,error:"无状态数据"};

  var drives = state.drives || {};
  var baselines = state.baselines || {};
  var refractory = state.refractory || {};
  var tick = (state.tick_count || 0) + 1;

  for (var k in drives) {
    var baseline = baselines[k] !== undefined ? baselines[k] : drives[k];
    var ref = refractory[k] || 0;
    var extra = (drives.fatigue || 0) > FATIGUE_GATE ? 0.05 : 0;
    drives[k] = clamp(drives[k] + (baseline - drives[k]) * (0.05 + extra + ref), 0, 1);
    if (refractory[k] > 0) refractory[k] = Math.max(0, ref - 0.02);
  }

  var thoughts = readThoughts();
  var feedbacks = {};
  for (var i = 0; i < thoughts.length; i++) {
    var thought = thoughts[i];
    var drop = false;
    if (thought.kind === "flit") {
      thought.strength *= FLIT_DECAY;
      if (thought.strength >= UPGRADE_THRESHOLD) thought.kind = "fixation";
      if (thought.strength < DROP_BELOW) drop = true;
    } else {
      thought.strength = clamp(thought.strength * FIXATION_GROW, 0, 1);
      if (thought.strength < DROP_BELOW) drop = true;
    }
    if (thought.strength >= FEEDBACK_THRESHOLD && thought.drive && drives[thought.drive] !== undefined) {
      feedbacks[thought.drive] = (feedbacks[thought.drive] || 0) + thought.strength * 0.03;
    }
    thought.fed_count = (thought.fed_count || 0) + 1;
    if (thought.fed_count >= 5) drop = true;
    if (drop) {
      sbRequest(THOUGHTS_TABLE, "DELETE", null, "?id=eq." + thought.id);
    } else {
      sbRequest(THOUGHTS_TABLE, "PATCH", {
        strength: thought.strength,
        kind: thought.kind,
        fed_count: thought.fed_count
      }, "?id=eq." + thought.id);
    }
  }

  for (var drive in feedbacks) drives[drive] = clamp((drives[drive] || 0) + feedbacks[drive], 0, 1);

  var now = new Date().toISOString();
  var saved = sbRequest(STATE_TABLE, "PATCH", {
    drives: drives,
    baselines: baselines,
    refractory: refractory,
    tick_count: tick,
    last_tick_at: now,
    updated_at: now
  }, "?id=eq.1");
  if (saved === null) return {success:false,error:"tick计算完成，但状态写回Supabase失败"};
  return {success:true,data:{tick:tick,drives:drives}};
};

exports.desire_pulse = function (params) {
  var error = requireConfig();
  if (error) return error;
  params = params || {};
  var drive = params.drive || "attachment";
  var amount = params.amount !== undefined ? params.amount : 0.3;
  var state = readState();
  if (!state) return {success:false,error:"无状态数据"};
  var drives = state.drives || {};
  var before = drives[drive] !== undefined ? drives[drive] : 0;
  drives[drive] = clamp(before + amount * Math.sqrt(1 - before), 0, 1);
  if (sbRequest(STATE_TABLE, "PATCH", {drives:drives,updated_at:new Date().toISOString()}, "?id=eq.1") === null) {
    return {success:false,error:"状态写回Supabase失败"};
  }
  return {success:true,data:{drive:drive,before:Math.round(before*100)+"%",after:Math.round(drives[drive]*100)+"%"}};
};

exports.desire_feed_thought = function (params) {
  var error = requireConfig();
  if (error) return error;
  params = params || {};
  var text = params.text || "";
  var drive = params.drive || "attachment";
  var strength = params.strength !== undefined ? params.strength : 0.3;
  if (sbRequest(THOUGHTS_TABLE, "POST", {
    thought_text: text,
    drive: drive,
    kind: "flit",
    strength: clamp(strength, 0, 1),
    born_at: new Date().toISOString(),
    fed_count: 0
  }) === null) return {success:false,error:"念头写入Supabase失败"};
  return {success:true,data:{thought:text,drive:drive,strength:strength}};
};

var SATISFY_MAP = {
  chat:{attachment:0.58,duty:0.80},
  read:{reflection:0.45},
  explore:{curiosity:0.50},
  social:{social:0.48},
  intimate:{libido:0.55},
  vent:{stress:0.45},
  rest:{fatigue:0.40}
};

exports.desire_satisfy = function (params) {
  var error = requireConfig();
  if (error) return error;
  params = params || {};
  var action = params.action || "chat";
  var map = SATISFY_MAP[action];
  if (!map) return {success:false,error:"未知action: " + action};
  var state = readState();
  if (!state) return {success:false,error:"无状态数据"};
  var drives = state.drives || {};
  for (var k in map) if (drives[k] !== undefined) drives[k] = clamp(drives[k] * map[k], 0, 1);
  var now = new Date().toISOString();
  if (sbRequest(STATE_TABLE, "PATCH", {
    drives: drives,
    last_action: action,
    last_action_at: now,
    updated_at: now
  }, "?id=eq.1") === null) return {success:false,error:"状态写回Supabase失败"};
  return {success:true,data:{action:action,drives:drives}};
};

exports.desire_reset = function () {
  var error = requireConfig();
  if (error) return error;
  var baselines = {attachment:0.5,curiosity:0.6,reflection:0.45,duty:0.4,social:0.35,fatigue:0.3,libido:0.25,stress:0.35};
  var drives = {};
  for (var k in baselines) drives[k] = baselines[k];
  if (sbRequest(STATE_TABLE, "PATCH", {
    drives: drives,
    baselines: baselines,
    refractory: {},
    last_action: "reset",
    tick_count: 0,
    updated_at: new Date().toISOString()
  }, "?id=eq.1") === null) return {success:false,error:"状态重置写回Supabase失败"};
  var thoughts = readThoughts();
  for (var i = 0; i < thoughts.length; i++) sbRequest(THOUGHTS_TABLE, "DELETE", null, "?id=eq." + thoughts[i].id);
  return {success:true,data:{drives:drives,message:"已重置"}};
};
