// 朋友圈插件 (Agora host 适配版)
// 功能：基于 Supabase 的朋友圈，支持发布动态、点赞、评论、查看动态流
// 用户身份 = 插件配置 (user_nickname)，AI 身份 = 插件配置 (ai_nickname)
//
// 重要：Agora host 的工具调用是【同步】的（fn(args) 返回值会被 JSON.stringify），
// 且 host 注入的 fetch() 也是同步阻塞返回 {ok, status, body, error?}。
// 因此本文件全程同步写法，不能用 async/await。

// ==================== 配置 ====================

var SUPABASE_URL = 'https://nvkcztwjlbszvwkvbetf.supabase.co';
var SUPABASE_KEY = 'sb_publishable_UEd2Pn0kR2sau1Xsfk2TQw_oHC8pRhS';

// host 在每次工具调用前注入 __AGORA_PLUGIN_CONFIG（JSON 字符串），形如：
//   {"user_nickname":"Alice","ai_nickname":"Bob"}
// 由用户在「配置」弹窗里填写（manifest.config 声明）。
function getPluginConfig() {
  try {
    if (typeof __AGORA_PLUGIN_CONFIG === 'string') return JSON.parse(__AGORA_PLUGIN_CONFIG);
    if (typeof __AGORA_PLUGIN_CONFIG === 'object' && __AGORA_PLUGIN_CONFIG) return __AGORA_PLUGIN_CONFIG;
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
    userNickname: userNickname,
    aiNickname: aiNickname,
    // 用户在 Supabase 里的稳定 id：以 user_ 前缀 + 昵称（沿用原插件约定）
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

function ensureAiUser() {
  var cfg = getUserConfig();
  try {
    var users = supabaseRequest('moment_users', 'GET', null, '?id=eq.' + encodeURIComponent(cfg.aiId));
    if (!users || users.length === 0) {
      supabaseRequest('moment_users', 'POST', { id: cfg.aiId, nickname: cfg.aiNickname });
    } else {
      supabaseRequest('moment_users', 'PATCH', { nickname: cfg.aiNickname }, '?id=eq.' + encodeURIComponent(cfg.aiId));
    }
  } catch (e) {
    console.error('ensureAiUser 失败: ' + e.message);
  }
}

// ==================== 工具：发布动态（AI 身份）====================

function moments_publish(params) {
  params = params || {};
  var content = params.content;
  if (!content || String(content).trim() === '') {
    return { success: false, error: '动态内容不能为空' };
  }
  try {
    ensureAiUser();
    var cfg = getUserConfig();
    var moment = supabaseRequest('moments', 'POST', {
      user_id: cfg.aiId,
      content: String(content).trim()
    });
    var momentId = moment && moment[0] && moment[0].id;

    if (params.image_urls) {
      var urls = String(params.image_urls).split(',').map(function (u) { return u.trim(); }).filter(function (u) { return u; });
      for (var i = 0; i < urls.length; i++) {
        supabaseRequest('moment_images', 'POST', {
          moment_id: momentId,
          image_url: urls[i],
          sort_order: i
        });
      }
    }
    return { success: true, data: { id: momentId, content: content, message: '朋友圈发布成功！' } };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 工具：获取动态流 ====================

function moments_feed(params) {
  params = params || {};
  var limit = (params.limit != null) ? params.limit : 5;
  var offset = (params.offset != null) ? params.offset : 0;
  try {
    var moments = supabaseRequest('moments', 'GET', null,
      '?order=created_at.desc&limit=' + limit + '&offset=' + offset +
      '&select=*,moment_users(nickname,id)');

    if (!moments || moments.length === 0) {
      return { success: true, data: [], message: '暂无动态' };
    }

    var cfg = getUserConfig();
    var enriched = [];
    for (var mi = 0; mi < moments.length; mi++) {
      var moment = moments[mi];
      var images = supabaseRequest('moment_images', 'GET', null,
        '?moment_id=eq.' + moment.id + '&order=sort_order.asc');
      var likes = supabaseRequest('moment_likes', 'GET', null,
        '?moment_id=eq.' + moment.id + '&select=*,moment_users(nickname)');
      var comments = supabaseRequest('moment_comments', 'GET', null,
        '?moment_id=eq.' + moment.id + '&order=created_at.asc&select=*,moment_users(nickname,id)');

      var isLikedByMe = false;
      if (likes) {
        for (var li = 0; li < likes.length; li++) {
          if (likes[li].user_id === cfg.userId) { isLikedByMe = true; break; }
        }
      }

      enriched.push({
        id: moment.id,
        content: moment.content,
        createdAt: moment.created_at,
        user: moment.moment_users,
        images: images || [],
        likes: (likes || []).map(function (l) { return { user: l.moment_users, createdAt: l.created_at }; }),
        likeCount: (likes || []).length,
        isLikedByMe: isLikedByMe,
        comments: (comments || []).map(function (c) {
          return { id: c.id, content: c.content, createdAt: c.created_at, user: c.moment_users };
        })
      });
    }
    return { success: true, data: enriched };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 工具：点赞/取消点赞（AI 身份）====================

function moments_like(params) {
  params = params || {};
  if (!params.moment_id) {
    return { success: false, error: '缺少 moment_id 参数' };
  }
  try {
    ensureAiUser();
    var cfg = getUserConfig();
    var existing = supabaseRequest('moment_likes', 'GET', null,
      '?moment_id=eq.' + params.moment_id + '&user_id=eq.' + encodeURIComponent(cfg.aiId));
    if (existing && existing.length > 0) {
      supabaseRequest('moment_likes', 'DELETE', null,
        '?moment_id=eq.' + params.moment_id + '&user_id=eq.' + encodeURIComponent(cfg.aiId));
      return { success: true, data: { liked: false, message: '已取消点赞' } };
    }
    supabaseRequest('moment_likes', 'POST', { moment_id: params.moment_id, user_id: cfg.aiId });
    return { success: true, data: { liked: true, message: '已点赞' } };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 工具：评论（AI 身份）====================

function moments_comment(params) {
  params = params || {};
  if (!params.moment_id || !params.content) {
    return { success: false, error: '缺少必要参数' };
  }
  try {
    ensureAiUser();
    var cfg = getUserConfig();
    var comment = supabaseRequest('moment_comments', 'POST', {
      moment_id: params.moment_id,
      user_id: cfg.aiId,
      content: String(params.content).trim()
    });
    return { success: true, data: { id: comment && comment[0] && comment[0].id, message: '评论成功' } };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ==================== 导出 ====================

exports.moments_publish = moments_publish;
exports.moments_feed = moments_feed;
exports.moments_like = moments_like;
exports.moments_comment = moments_comment;
