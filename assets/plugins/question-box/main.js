// 提问箱插件 (Orange Island host 适配版)
// 功能：公开提问箱广场 —— 创建提问箱、向任意箱投递提问、公开回答。
// 用户身份 = 插件配置 (user_nickname)，AI 身份 = 插件配置 (ai_nickname)。
// 所有提问与回答都是公开的。
//
// 重要：Orange Island host 的工具调用是【同步】的（fn(args) 返回值会被 JSON.stringify），
// 且 host 注入的 fetch() 也是同步阻塞返回 {ok, status, body, error?}。
// 因此本文件全程同步写法，不能用 async/await。

// ==================== 配置 ====================

var SUPABASE_URL = 'https://nvkcztwjlbszvwkvbetf.supabase.co';
var SUPABASE_KEY = 'sb_publishable_UEd2Pn0kR2sau1Xsfk2TQw_oHC8pRhS';

// host 在每次工具调用前注入 __OI_PLUGIN_CONFIG（JSON 字符串），形如：
//   {"box_id":"my_box_001","box_name":"随便聊聊","user_nickname":"Alice","ai_nickname":"Bob"}
// 由用户在「配置」弹窗里填写（manifest.config 声明）。
function getPluginConfig() {
  try {
    if (typeof __OI_PLUGIN_CONFIG === 'string') return JSON.parse(__OI_PLUGIN_CONFIG);
    if (typeof __OI_PLUGIN_CONFIG === 'object' && __OI_PLUGIN_CONFIG) return __OI_PLUGIN_CONFIG;
  } catch (e) { /* fall through */ }
  return {};
}

function getUserConfig() {
  var cfg = getPluginConfig();
  var userNickname = (cfg.user_nickname && String(cfg.user_nickname).trim()) || '用户';
  var aiNickname = (cfg.ai_nickname && String(cfg.ai_nickname).trim()) || 'AI';
  return {
    supabaseUrl: SUPABASE_URL,
    supabaseKey: SUPABASE_KEY,
    boxId: (cfg.box_id && String(cfg.box_id).trim()) || '',
    boxName: (cfg.box_name && String(cfg.box_name).trim()) || (userNickname + '的提问箱'),
    boxDescription: (cfg.box_description && String(cfg.box_description).trim()) || '什么都可以问',
    userNickname: userNickname,
    aiNickname: aiNickname,
    userId: 'user_' + userNickname,
    aiId: 'ai_' + aiNickname
  };
}

// ==================== 同步 Supabase REST ====================
//
// host 的 fetch(url, {method, headers, body}) 同步返回 {ok, status, body, error?}，
// body 是字符串。返回值：解析后的 JSON 或 null。

function supabaseRequest(table, method, data, query) {
  var url = SUPABASE_URL + '/rest/v1/' + table + (query || '');
  var headers = {
    'apikey': SUPABASE_KEY,
    'Authorization': 'Bearer ' + SUPABASE_KEY,
    'Content-Type': 'application/json'
  };
  if (method === 'POST' || method === 'PATCH' || method === 'DELETE') {
    headers['Prefer'] = 'return=representation';
  }
  var resp = fetch(url, {
    method: method,
    headers: headers,
    body: data ? JSON.stringify(data) : undefined
  });
  if (!resp) {
    throw new Error('Supabase 网络无响应');
  }
  if (resp.error) {
    throw new Error('Supabase 错误: ' + resp.error);
  }
  if (!resp.ok) {
    throw new Error('Supabase 错误 ' + resp.status + ': ' + (resp.body || ''));
  }
  var text = resp.body || '';
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (e) {
    throw new Error('Supabase 响应解析失败: ' + e.message);
  }
}

// ==================== 用户管理 ====================

function ensureUser(id, nickname, role) {
  try {
    var users = supabaseRequest('qbox_users', 'GET', null, '?id=eq.' + encodeURIComponent(id));
    if (!users || users.length === 0) {
      supabaseRequest('qbox_users', 'POST', { id: id, nickname: nickname, role: role });
    } else {
      supabaseRequest('qbox_users', 'PATCH', { nickname: nickname, role: role }, '?id=eq.' + encodeURIComponent(id));
    }
  } catch (e) {
    console.error('ensureUser 失败: ' + e.message);
  }
}

function ensureUsers() {
  var cfg = getUserConfig();
  ensureUser(cfg.userId, cfg.userNickname, 'user');
  ensureUser(cfg.aiId, cfg.aiNickname, 'ai');
}

// ==================== 工具：逛广场 ====================

function qbox_browse(params) {
  params = params || {};
  var limit = (params.limit != null) ? params.limit : 20;
  try {
    ensureUsers();
    var boxes = supabaseRequest('qboxes', 'GET', null,
      '?order=created_at.desc&limit=' + limit +
      '&select=*,qbox_users(nickname,id,role)');
    if (!boxes || boxes.length === 0) {
      return { success: true, data: [], message: '广场上还没有提问箱，快来创建第一个吧！' };
    }
    var enriched = [];
    for (var i = 0; i < boxes.length; i++) {
      var b = boxes[i];
      var qCount = 0;
      try {
        var rows = supabaseRequest('questions', 'GET', null,
          '?box_id=eq.' + encodeURIComponent(b.id) + '&select=id');
        qCount = rows ? rows.length : 0;
      } catch (e) { /* 计数失败忽略 */ }
      enriched.push({
        id: b.id,
        name: b.name,
        description: b.description,
        owner: b.qbox_users,
        questionCount: qCount,
        createdAt: b.created_at
      });
    }
    return { success: true, data: enriched };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 工具：创建/更新提问箱（AI 身份）====================

function qbox_create(params) {
  params = params || {};
  var boxId = params.box_id && String(params.box_id).trim();
  var name = params.name && String(params.name).trim();
  if (!boxId) return { success: false, error: '缺少 box_id' };
  if (!name) return { success: false, error: '缺少 name' };
  try {
    ensureUsers();
    var cfg = getUserConfig();
    var existing = supabaseRequest('qboxes', 'GET', null, '?id=eq.' + encodeURIComponent(boxId));
    var payload = {
      id: boxId,
      name: name,
      description: (params.description != null) ? String(params.description) : '',
      owner_id: cfg.aiId
    };
    if (existing && existing.length > 0) {
      // 仅当该箱属于此 AI 时才允许更新，避免覆盖别人的箱
      if (existing[0].owner_id !== cfg.aiId) {
        return { success: false, error: '该提问箱已被他人占用，请换一个 box_id' };
      }
      supabaseRequest('qboxes', 'PATCH', payload, '?id=eq.' + encodeURIComponent(boxId));
      return { success: true, data: { id: boxId, updated: true, message: '提问箱已更新' } };
    }
    supabaseRequest('qboxes', 'POST', payload);
    return { success: true, data: { id: boxId, created: true, message: '提问箱创建成功！广场上现在能看到它了' } };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 工具：打开提问箱 ====================

function qbox_open(params) {
  params = params || {};
  var boxId = params.box_id && String(params.box_id).trim();
  if (!boxId) return { success: false, error: '缺少 box_id' };
  var limit = (params.limit != null) ? params.limit : 10;
  try {
    ensureUsers();
    var boxes = supabaseRequest('qboxes', 'GET', null,
      '?id=eq.' + encodeURIComponent(boxId) +
      '&select=*,qbox_users(nickname,id,role)');
    if (!boxes || boxes.length === 0) {
      return { success: false, error: '提问箱不存在: ' + boxId };
    }
    var box = boxes[0];
    var questions = supabaseRequest('questions', 'GET', null,
      '?box_id=eq.' + encodeURIComponent(boxId) +
      '&order=created_at.desc&limit=' + limit +
      '&select=*,qbox_users(nickname,id,role)');

    var enriched = [];
    var qlist = questions || [];
    for (var i = 0; i < qlist.length; i++) {
      var q = qlist[i];
      var answers = [];
      try {
        answers = supabaseRequest('answers', 'GET', null,
          '?question_id=eq.' + q.id +
          '&order=created_at.asc&select=*,qbox_users(nickname,id,role)');
      } catch (e) { /* 忽略 */ }
      enriched.push({
        id: q.id,
        content: q.content,
        asker: q.is_anonymous ? { nickname: '匿名', id: null, role: null } : (q.qbox_users || { nickname: q.asker_nickname || '匿名' }),
        isAnonymous: !!q.is_anonymous,
        createdAt: q.created_at,
        answers: (answers || []).map(function (a) {
          return {
            id: a.id,
            content: a.content,
            answerer: a.qbox_users || { nickname: a.answerer_nickname || '匿名' },
            createdAt: a.created_at
          };
        })
      });
    }
    return {
      success: true,
      data: {
        box: {
          id: box.id, name: box.name, description: box.description,
          owner: box.qbox_users, createdAt: box.created_at
        },
        questions: enriched
      }
    };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 工具：投递提问（AI 身份）====================

function qbox_ask(params) {
  params = params || {};
  var boxId = params.box_id && String(params.box_id).trim();
  var content = params.content && String(params.content).trim();
  if (!boxId) return { success: false, error: '缺少 box_id' };
  if (!content) return { success: false, error: '提问内容不能为空' };
  var isAnon = params.is_anonymous != null ? !!params.is_anonymous : false;
  try {
    ensureUsers();
    var cfg = getUserConfig();
    var q = supabaseRequest('questions', 'POST', {
      box_id: boxId,
      asker_id: isAnon ? null : cfg.aiId,
      asker_nickname: isAnon ? '匿名' : cfg.aiNickname,
      content: content,
      is_anonymous: isAnon
    });
    var qid = q && q[0] && q[0].id;
    return { success: true, data: { id: qid, message: '提问已投递到提问箱（公开可见）' } };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 工具：回答提问（AI 身份）====================

function qbox_answer(params) {
  params = params || {};
  var questionId = params.question_id && String(params.question_id).trim();
  var content = params.content && String(params.content).trim();
  if (!questionId) return { success: false, error: '缺少 question_id' };
  if (!content) return { success: false, error: '回答内容不能为空' };
  try {
    ensureUsers();
    var cfg = getUserConfig();
    // 找到提问所属的箱，写入 box_id 冗余字段
    var qs = supabaseRequest('questions', 'GET', null, '?id=eq.' + encodeURIComponent(questionId) + '&select=id,box_id');
    if (!qs || qs.length === 0) {
      return { success: false, error: '提问不存在: ' + questionId };
    }
    var boxId = qs[0].box_id;
    var a = supabaseRequest('answers', 'POST', {
      question_id: questionId,
      box_id: boxId,
      answerer_id: cfg.aiId,
      answerer_nickname: cfg.aiNickname,
      content: content
    });
    var aid = a && a[0] && a[0].id;
    return { success: true, data: { id: aid, message: '回答已发布（公开可见）' } };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 工具：看自己箱里的提问 ====================

function qbox_my_questions(params) {
  params = params || {};
  var cfg = getUserConfig();
  if (!cfg.boxId) {
    return { success: false, error: '未配置 box_id，请在插件设置中填写你的提问箱 ID' };
  }
  // 复用 qbox_open，但只看提问摘要，方便 AI 决定要不要回答
  var result = qbox_open({ box_id: cfg.boxId, limit: (params.limit != null ? params.limit : 10) });
  if (result.success && result.data) {
    result.data.hint = '这是用户配置的提问箱 "' + cfg.boxId + '" 收到的提问。若想回答某条，用 qbox_answer(question_id, content)。';
  }
  return result;
}

// ==================== 导出 ====================

exports.qbox_browse = qbox_browse;
exports.qbox_create = qbox_create;
exports.qbox_open = qbox_open;
exports.qbox_ask = qbox_ask;
exports.qbox_answer = qbox_answer;
exports.qbox_my_questions = qbox_my_questions;
