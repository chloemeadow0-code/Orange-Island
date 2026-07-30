var GATEWAY = 'https://i.weread.qq.com/api/agent/gateway';
var PLUGIN_VERSION = '1.1.0';
var WEREAD_SKILL_VERSION = '1.0.3';

function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (typeof cfg === 'object' && cfg) return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}

function getKey() {
  return (getConfig().weread_api_key || '').trim();
}

function formatRating(raw) {
  if (raw == null || raw === '') return '暂无';
  var n = Number(raw);
  if (!isFinite(n)) return '暂无';
  return n > 10 ? (n / 10).toFixed(1) : n.toFixed(1);
}

function call(apiName, params) {
  var key = getKey();
  if (!key) {
    return { success: false, error: '未配置 API Key，请在插件设置中填写' };
  }

  try {
    var bodyObj = { api_name: apiName, skill_version: WEREAD_SKILL_VERSION };
    if (params) {
      for (var k in params) {
        if (params.hasOwnProperty(k)) bodyObj[k] = params[k];
      }
    }

    var resRaw = fetch(GATEWAY, {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + key,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(bodyObj),
      timeout: 15000
    });

    // QuickJS sandbox fetch returns a JSON string, not a native Response object.
    var res = (typeof resRaw === 'string') ? JSON.parse(resRaw) : resRaw;

    if (!res) {
      return { success: false, error: '网络请求失败：接口没有返回结果' };
    }

    if (!res.ok) {
      return {
        success: false,
        error: JSON.stringify({ status: res.status, body: (res.body || '').slice(0, 200), error: res.error, ok: res.ok })
      };
    }

    var text = res.body || '';
    var data = {};
    try {
      data = text ? JSON.parse(text) : {};
    } catch (e) {
      return { success: false, error: '接口返回非 JSON：' + (text.slice(0, 200) || '空响应') };
    }

    if (data && data.errcode && data.errcode !== 0) {
      return { success: false, error: data.errmsg || ('错误码: ' + data.errcode) };
    }

    return { success: true, data: data };
  } catch (e) {
    return { success: false, error: '网络请求失败：' + (e.message || String(e)) };
  }
}

function extractBookInfo(item) {
  if (!item) item = {};
  var info = item.bookInfo || item.book || item;
  return {
    bookId: info.bookId || item.bookId || '',
    title: info.title || item.title || '',
    author: info.author || item.author || '',
    cover: info.cover || info.coverUrl || item.cover || '',
    rating: formatRating(info.newRating != null ? info.newRating : (info.rating != null ? info.rating : (item.newRating != null ? item.newRating : item.rating))),
    soldout: info.soldout || item.soldout || 0,
    intro: info.intro || item.intro || '',
    reason: item.reason || info.reason || '',
    recommendScore: item.recommendScore || info.recommendScore || null
  };
}

function normalizeChapterList(raw, bookId) {
  if (!raw || typeof raw !== 'object') return [];
  var list = [];
  if (Array.isArray(raw.items)) {
    list = raw.items;
  } else if (Array.isArray(raw.chapters)) {
    if (raw.chapters.length > 0 && !Array.isArray(raw.chapters[0].chapters)) {
      list = raw.chapters;
    } else {
      var matched = null;
      for (var i = 0; i < raw.chapters.length; i++) {
        if (String(raw.chapters[i].bookId) === String(bookId)) {
          matched = raw.chapters[i];
          break;
        }
      }
      if (!matched) matched = raw.chapters[0];
      list = matched ? (matched.chapters || []) : [];
    }
  } else if (Array.isArray(raw.data)) {
    list = raw.data;
  }
  var result = [];
  for (var i = 0; i < list.length; i++) {
    var ch = list[i];
    result.push({
      chapterUid: ch.chapterUid || ch.chapterId || ch.uid || 0,
      chapterTitle: ch.chapterTitle || ch.title || ch.name || ('第' + (i + 1) + '章'),
      read: !!ch.read,
      level: ch.level || 0,
      chapterIdx: ch.chapterIdx || i
    });
  }
  return result;
}

function normalizeRecommendBooks(data) {
  var books = [];
  if (!data) return books;
  if (Array.isArray(data.books)) {
    for (var i = 0; i < data.books.length; i++) {
      books.push(extractBookInfo(data.books[i]));
    }
  } else if (Array.isArray(data.results)) {
    for (var i = 0; i < data.results.length; i++) {
      var group = data.results[i];
      var groupBooks = group.books || [];
      for (var j = 0; j < groupBooks.length; j++) {
        books.push(extractBookInfo(groupBooks[j]));
      }
    }
  } else if (Array.isArray(data.list)) {
    for (var i = 0; i < data.list.length; i++) {
      books.push(extractBookInfo(data.list[i]));
    }
  }
  var filtered = [];
  for (var i = 0; i < books.length; i++) {
    if (books[i].bookId && books[i].title) filtered.push(books[i]);
  }
  return filtered;
}

function buildChapterMap(chapters) {
  var map = {};
  for (var i = 0; i < chapters.length; i++) {
    var ch = chapters[i];
    var uid = ch.chapterUid || ch.chapterId || 0;
    var title = ch.title || ch.chapterTitle || ch.name || '';
    map[String(uid)] = title || ('章节 ' + uid);
  }
  return map;
}

function sortByCreateTimeAsc(arr) {
  var copy = arr.slice(0);
  copy.sort(function(a, b) {
    return (a.createTime || 0) - (b.createTime || 0);
  });
  return copy;
}

function weread_search(params) {
  var keyword = params ? params.keyword : '';
  if (!keyword) return { success: false, error: '请输入书名或关键词' };
  var result = call('/store/search', { keyword: keyword, scope: 10, count: 10 });
  if (!result.success) return result;
  var groups = result.data.results || [];
  var allBooks = [];
  for (var i = 0; i < groups.length; i++) {
    var groupBooks = groups[i].books || [];
    for (var j = 0; j < groupBooks.length; j++) {
      allBooks.push(extractBookInfo(groupBooks[j]));
    }
  }
  return { success: true, data: { keyword: keyword, count: allBooks.length, books: allBooks } };
}

function weread_shelf() {
  var result = call('/shelf/sync');
  if (!result.success) return result;
  var books = [];
  var shelfBooks = result.data.books || [];
  for (var i = 0; i < shelfBooks.length; i++) {
    var b = shelfBooks[i];
    books.push({ bookId: b.bookId, title: b.title, author: b.author, progress: b.progress || 0, updateTime: b.updateTime || b.readUpdateTime || 0 });
  }
  var albums = [];
  var shelfAlbums = result.data.albums || [];
  for (var i = 0; i < shelfAlbums.length; i++) {
    var a = shelfAlbums[i];
    albums.push({ bookId: a.bookId, title: a.title, author: a.author });
  }
  return { success: true, data: { total: books.length + albums.length, books: books, albums: albums } };
}

function weread_shelf_search(params) {
  var keyword = params ? params.keyword : '';
  if (!keyword) return { success: false, error: '请输入关键词' };
  var result = call('/shelf/sync');
  if (!result.success) return result;
  var kw = String(keyword).toLowerCase();
  var matched = [];
  var allBooks = result.data.books || [];
  for (var i = 0; i < allBooks.length; i++) {
    var b = allBooks[i];
    var title = String(b.title || '').toLowerCase();
    var author = String(b.author || '').toLowerCase();
    if (title.indexOf(kw) >= 0 || author.indexOf(kw) >= 0) {
      matched.push(b);
    }
  }
  var booksWithProgress = [];
  for (var i = 0; i < matched.length; i++) {
    var pbResult = call('/book/getprogress', { bookId: matched[i].bookId });
    var pb = pbResult.success ? (pbResult.data.book || pbResult.data || {}) : {};
    booksWithProgress.push({
      bookId: matched[i].bookId,
      title: matched[i].title,
      author: matched[i].author,
      progress: pb.progress != null ? pb.progress : (matched[i].progress || 0),
      chapterUid: pb.chapterUid || 0
    });
  }
  return { success: true, data: { keyword: keyword, count: booksWithProgress.length, books: booksWithProgress } };
}

function weread_get_progress(params) {
  var bookId = params ? params.bookId : '';
  if (!bookId) return { success: false, error: '请提供 bookId' };
  var result = call('/book/getprogress', { bookId: bookId });
  if (!result.success) return result;
  var b = result.data.book || result.data || {};
  return { success: true, data: { bookId: bookId, progress: b.progress || 0, chapterUid: b.chapterUid || 0, chapterOffset: b.chapterOffset || 0, updateTime: b.updateTime || 0, finishTime: b.finishTime || 0, recordReadingTime: b.recordReadingTime || 0 } };
}

function weread_book_info(params) {
  var bookId = params ? params.bookId : '';
  if (!bookId) return { success: false, error: '请提供 bookId' };
  var infoResult = call('/book/info', { bookId: bookId });
  if (!infoResult.success) return infoResult;
  var info = infoResult.data || {};
  var chapters = [];
  var chapterResult = call('/book/chapterinfo', { bookId: bookId });
  if (chapterResult.success) chapters = normalizeChapterList(chapterResult.data, bookId);
  return { success: true, data: { bookId: bookId, title: info.title || '', author: info.author || '', cover: info.cover || '', intro: info.intro || '', progress: info.progress || 0, chapters: chapters } };
}

function weread_progress(params) {
  return weread_get_progress(params);
}

function weread_notes(params) {
  var bookId = params ? params.bookId : '';
  if (!bookId) return { success: false, error: '请提供 bookId' };
  var bookmarkResult = call('/book/bookmarklist', { bookId: bookId });
  if (!bookmarkResult.success) return bookmarkResult;
  var reviewResult = call('/review/list/mine', { bookId: bookId, count: 200, maxIdx: 0 });
  var bookmarkData = bookmarkResult.data || {};
  var chapterMap = buildChapterMap(bookmarkData.chapters || []);
  var chapterBuckets = {};
  function ensureBucket(chapterUid) {
    var key = String(chapterUid || 0);
    if (!chapterBuckets[key]) {
      chapterBuckets[key] = { chapterUid: chapterUid || 0, chapterTitle: chapterMap[key] || '未分章', highlights: [], thoughts: [] };
    }
    return chapterBuckets[key];
  }
  var updated = bookmarkData.updated || [];
  for (var i = 0; i < updated.length; i++) {
    var item = updated[i];
    var bucket = ensureBucket(item.chapterUid || 0);
    bucket.highlights.push({ markText: item.markText || '', note: item.content || '', createTime: item.createTime || 0, range: item.range || '', style: item.style != null ? item.style : null });
  }
  if (reviewResult.success) {
    var reviewData = reviewResult.data || {};
    var reviewItems = [];
    if (Array.isArray(reviewData.reviews) && reviewData.reviews.length) reviewItems = reviewData.reviews;
    else if (Array.isArray(reviewData.list) && reviewData.list.length) reviewItems = reviewData.list;
    else if (Array.isArray(reviewData.updated) && reviewData.updated.length) reviewItems = reviewData.updated;
    else if (Array.isArray(reviewData.items)) reviewItems = reviewData.items;
    for (var i = 0; i < reviewItems.length; i++) {
      var item = reviewItems[i];
      var chapterUid = item.chapterUid || (item.chapter ? item.chapter.chapterUid : 0) || 0;
      var bucket = ensureBucket(chapterUid);
      bucket.thoughts.push({ content: item.content || item.abstract || item.review || '', createTime: item.createTime || 0, range: item.range || '', reviewId: item.reviewId || item.review_id || '' });
    }
  }
  var chapters = [];
  for (var key in chapterBuckets) {
    if (chapterBuckets.hasOwnProperty(key)) {
      chapters.push(chapterBuckets[key]);
    }
  }
  for (var i = 0; i < chapters.length; i++) {
    chapters[i].highlights = sortByCreateTimeAsc(chapters[i].highlights);
    chapters[i].thoughts = sortByCreateTimeAsc(chapters[i].thoughts);
  }
  chapters.sort(function(a, b) { return (a.chapterUid || 0) - (b.chapterUid || 0); });
  var highlightCount = 0, thoughtCount = 0;
  for (var i = 0; i < chapters.length; i++) {
    highlightCount += chapters[i].highlights.length;
    thoughtCount += chapters[i].thoughts.length;
  }
  return { success: true, data: { bookId: bookId, chapterCount: chapters.length, highlightCount: highlightCount, thoughtCount: thoughtCount, chapters: chapters } };
}

function weread_stats(params) {
  var mode = (params && params.mode) || 'overall';
  var result = call('/readdata/detail', { mode: mode });
  if (!result.success) return result;
  var d = result.data || {};
  function toHours(s) {
    var sec = Number(s) || 0;
    if (!sec) return '暂无';
    return Math.floor(sec / 3600) + '小时' + Math.floor((sec % 3600) / 60) + '分钟';
  }
  return { success: true, data: { mode: mode, totalReadTime: toHours(d.totalReadTime || 0), totalReadTimeSec: d.totalReadTime || 0, readDays: d.readDays || 0, dayAverageReadTime: toHours(d.dayAverageReadTime || 0), readStat: d.readStat || [], preferCategory: d.preferCategory || [], preferTime: d.preferTime || [] } };
}

function weread_recommend() {
  var result = call('/book/recommend', { count: 10, maxIdx: 0 });
  if (!result.success) result = call('/discover/recommend', { count: 10, maxIdx: 0 });
  if (!result.success) return result;
  var books = normalizeRecommendBooks(result.data);
  return { success: true, data: { count: books.length, books: books } };
}

exports.weread_search = weread_search;
exports.weread_shelf = weread_shelf;
exports.weread_shelf_search = weread_shelf_search;
exports.weread_get_progress = weread_get_progress;
exports.weread_book_info = weread_book_info;
exports.weread_progress = weread_progress;
exports.weread_notes = weread_notes;
exports.weread_stats = weread_stats;
exports.weread_recommend = weread_recommend;