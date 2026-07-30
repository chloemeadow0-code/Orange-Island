var CONFIG = {
  supabaseUrl: '',
  supabaseKey: ''
};

function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (typeof cfg === 'object' && cfg) return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}

function initConfig() {
  var cfg = getConfig();
  var rawUrl = (cfg && cfg.supabase_url) || '';
  CONFIG.supabaseUrl = rawUrl.replace(/\/+$/, '');
  CONFIG.supabaseKey = (cfg && cfg.supabase_key) || '';
}

function checkConfig() {
  if (!CONFIG.supabaseUrl || !CONFIG.supabaseKey) return 'Supabase 未配置完整';
  return null;
}

function supabaseRequest(table, method, data, query) {
  var url = CONFIG.supabaseUrl + '/rest/v1/' + table + (query || '');
  var headers = {
    'apikey': CONFIG.supabaseKey,
    'Authorization': 'Bearer ' + CONFIG.supabaseKey,
    'Content-Type': 'application/json'
  };
  var raw = fetch(url, {
    method: method,
    headers: headers,
    body: data ? JSON.stringify(data) : undefined
  });
  // QuickJS sandbox fetch returns a JSON string, not a native Response object.
  var resp = (typeof raw === 'string') ? JSON.parse(raw) : raw;
  if (!resp || !resp.ok) {
    var bodyText = '';
    try { bodyText = resp ? (resp.body || '') : ''; } catch (e) {}
    return { error: 'Supabase ' + (resp ? resp.status : 0) + ': ' + bodyText };
  }
  var text = resp.body || '';
  if (!text) return null;
  try { return JSON.parse(text); } catch (e) { return text; }
}

function getStickers() {
  var rows = supabaseRequest('stickers', 'GET', null, '?order=created_at.desc');
  if (rows && rows.error) return { error: rows.error };
  if (!rows) return [];
  return rows;
}

function list_stickers() {
  initConfig();
  var err = checkConfig();
  if (err) return { success: false, error: err };
  var stickers = getStickers();
  if (stickers && stickers.error) {
    return { success: false, error: stickers.error };
  }
  var grouped = {};
  for (var i = 0; i < stickers.length; i++) {
    var s = stickers[i];
    var cat = s.category || '通用';
    if (!grouped[cat]) grouped[cat] = [];
    grouped[cat].push({ id: s.id, name: s.name, url: s.url, tags: s.tags || [] });
  }
  return { success: true, data: grouped };
}

function send_sticker(params) {
  initConfig();
  var err = checkConfig();
  if (err) return { success: false, error: err };
  var keyword = (params && params.keyword) || '';
  var stickers = getStickers();
  if (stickers && stickers.error) {
    return { success: false, error: stickers.error };
  }
  if (!stickers || stickers.length === 0) {
    return { success: false, error: '表情包库为空，请先上传表情包' };
  }
  var matched = stickers;
  if (keyword) {
    var kw = keyword.toLowerCase().trim();
    matched = [];
    for (var i = 0; i < stickers.length; i++) {
      var s = stickers[i];
      var nameMatch = s.name.toLowerCase().indexOf(kw) >= 0;
      var tagMatch = false;
      var tags = s.tags || [];
      for (var j = 0; j < tags.length; j++) {
        if (tags[j].toLowerCase().indexOf(kw) >= 0) { tagMatch = true; break; }
      }
      if (nameMatch || tagMatch) matched.push(s);
    }
  }
  if (matched.length === 0) {
    matched = stickers;
  }
  var picked = matched[Math.floor(Math.random() * matched.length)];
  return {
    success: true,
    data: {
      id: picked.id,
      name: picked.name,
      url: picked.url,
      markdown: '![' + picked.name + '](' + picked.url + ')'
    }
  };
}

exports.list_stickers = list_stickers;
exports.send_sticker = send_sticker;