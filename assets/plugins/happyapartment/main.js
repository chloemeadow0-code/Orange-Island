// 幸福公寓·AI 沙盒  main.js  (Orange Island 插件, QuickJS 同步沙箱)
// 玩法来源: 《幸福公寓物语》GDD 还原数值 + 难度增强层(单一标准模式)。
// 本文件实现"给 AI 玩"的无界面游戏引擎: AI 通过工具观察状态、执行动作来经营公寓。
// 兼容约束: 全部同步函数; 用 var/传统函数; 不用 const/let/箭头/模板字符串;
//           不用 Array.filter/some/includes(改用 for+indexOf); 返回对象由宿主 JSON.stringify。

var STATE = null;

// ---------------- 随机数 (可复现) ----------------
function makeRng(seed) {
  var s = (seed >>> 0);
  if (s === 0) s = 123456789;
  return function () {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}
function rngRange(rng, a, b) { return a + Math.floor(rng() * (b - a + 1)); }
function rngChance(rng, p) { return rng() < p; }
function clamp(v, lo, hi) { if (v < lo) return lo; if (v > hi) return hi; return v; }
function inArray(arr, v) { for (var i = 0; i < arr.length; i++) { if (arr[i] === v) return true; } return false; }
function pickR(arr, rng) { return arr[Math.floor(rng() * arr.length)]; }

// ---------------- 数据表 (来自 GDD 还原) ----------------
// 家具: id, name, floor, comfort(舒适), cost(购置现金), unlock
//   unlock = null                  -> 开局即可用
//   {t:'rank',cat,tier}            -> 该榜单该档夺冠后解锁
//   {t:'career',job}               -> 首次有居民转职为该职业后解锁
//   {t:'behavior',key}             -> 满足条件后解锁 (elderFirst/fridge8/karaoke)
//   {t:'all_champion'}             -> 八榜世界冠军全包后解锁
var FURNITURE = [
  { id: "bed_normal", name: "普通床", floor: "elastic", comfort: 3, cost: 800, unlock: null },
  { id: "lamp", name: "台灯", floor: "elastic", comfort: 3, cost: 600, unlock: null },
  { id: "fan", name: "电风扇", floor: "elastic", comfort: 2, cost: 400, unlock: null },
  { id: "window4", name: "四色窗", floor: "elastic", comfort: 2, cost: 500, unlock: null },
  { id: "cabinet", name: "橱柜", floor: "elastic", comfort: 6, cost: 6000, unlock: null },
  { id: "tansu", name: "趟门柜橱", floor: "elastic", comfort: 7, cost: 6000, unlock: null },
  { id: "dresser", name: "化妆间", floor: "elastic", comfort: 6, cost: 1500, unlock: null },
  { id: "closet", name: "壁橱", floor: "elastic", comfort: 7, cost: 1800, unlock: null },
  { id: "wardrobe", name: "衣柜", floor: "elastic", comfort: 7, cost: 1800, unlock: null },
  { id: "crib", name: "婴儿床", floor: "green", comfort: 8, cost: 6000, unlock: null },
  { id: "robot", name: "宇宙机器人", floor: "green", comfort: 8, cost: 6000, unlock: null },
  { id: "tv_digital", name: "数位电视", floor: "green", comfort: 7, cost: 5000, unlock: null },
  { id: "game_console", name: "游戏机组合", floor: "green", comfort: 7, cost: 5000, unlock: null },
  { id: "arch", name: "拱形铁架", floor: "green", comfort: 6, cost: 2000, unlock: null },
  { id: "vase", name: "花瓶", floor: "green", comfort: 4, cost: 1500, unlock: null },
  { id: "carpet_elegant", name: "高雅地毯", floor: "green", comfort: 4, cost: 1500, unlock: { t: "career", job: "舞者" } },
  { id: "bookshelf_big", name: "超大书架", floor: "green", comfort: 5, cost: 4000, unlock: null },
  { id: "poster", name: "美女海报", floor: "green", comfort: 5, cost: 1500, unlock: null },
  { id: "tableware", name: "餐具架", floor: "orange", comfort: 8, cost: 2000, unlock: null },
  { id: "fridge", name: "冰箱", floor: "orange", comfort: 8, cost: 3000, unlock: null },
  { id: "kitchen", name: "厨房", floor: "orange", comfort: 8, cost: 3000, unlock: null },
  { id: "oven", name: "烤箱台", floor: "orange", comfort: 8, cost: 3000, unlock: null },
  { id: "vacuum", name: "吸尘器", floor: "orange", comfort: 9, cost: 3000, unlock: null },
  { id: "clock", name: "挂钟", floor: "orange", comfort: 9, cost: 1000, unlock: null },
  { id: "phone", name: "电话", floor: "pink", comfort: 9, cost: 2000, unlock: null },
  { id: "heart_bed", name: "心形床", floor: "pink", comfort: 9, cost: 7000, unlock: { t: "career", job: "指挥家" } },
  { id: "heart_rug", name: "心形毛毯", floor: "pink", comfort: 9, cost: 7000, unlock: null },
  { id: "dress_table", name: "化妆台", floor: "pink", comfort: 8, cost: 6000, unlock: null },
  { id: "wash", name: "洗面台", floor: "pink", comfort: 8, cost: 6000, unlock: null },
  { id: "bath_indep", name: "独立浴室", floor: "tile", comfort: 3, cost: 3000, unlock: null },
  { id: "toilet_west", name: "洋式厕所", floor: "tile", comfort: 3, cost: 2000, unlock: null },
  { id: "shower", name: "淋浴室", floor: "tile", comfort: 4, cost: 3000, unlock: null },
  { id: "bathtub", name: "浴缸", floor: "tile", comfort: 4, cost: 3000, unlock: null },
  { id: "wash_dress", name: "洗面化妆台", floor: "tile", comfort: 2, cost: 1500, unlock: null },
  { id: "washer", name: "洗衣机", floor: "tile", comfort: 2, cost: 2000, unlock: null },
  { id: "sauna", name: "三温暖", floor: "tile", comfort: 5, cost: 4000, unlock: null },
  { id: "massage_bath", name: "按摩浴缸", floor: "tile", comfort: 5, cost: 4000, unlock: { t: "career", job: "鉴定师" } },
  { id: "japan_bath", name: "日式浴盘", floor: "tile", comfort: 3, cost: 2000, unlock: { t: "career", job: "骑师" } },

  // --- 排行解锁 (GDD §3.1 A) ---
  { id: "wood", name: "木质", floor: "elastic", comfort: 5, cost: 800, unlock: { t: "rank", cat: "rent", tier: "local" } },
  { id: "carpet", name: "地毯", floor: "elastic", comfort: 6, cost: 1200, unlock: { t: "rank", cat: "rent", tier: "national" } },
  { id: "wood_bed", name: "木板床", floor: "elastic", comfort: 7, cost: 2000, unlock: { t: "rank", cat: "rent", tier: "world" } },
  { id: "orange_bed", name: "橙色的床", floor: "orange", comfort: 7, cost: 2000, unlock: { t: "rank", cat: "comfort", tier: "local" } },
  { id: "green_bed", name: "绿色的床", floor: "green", comfort: 8, cost: 2000, unlock: { t: "rank", cat: "comfort", tier: "national" } },
  { id: "pink_bed", name: "桃色的床", floor: "pink", comfort: 9, cost: 3000, unlock: { t: "rank", cat: "comfort", tier: "world" } },
  { id: "blanket", name: "毛毯", floor: "elastic", comfort: 5, cost: 800, unlock: { t: "rank", cat: "brain", tier: "local" } },
  { id: "red_blanket", name: "红色毛毯", floor: "elastic", comfort: 10, cost: 2500, unlock: { t: "rank", cat: "hobby", tier: "local" } },

  // --- 职业首转解锁 (GDD §3.1 B) ---
  { id: "mini_company", name: "迷你公司", floor: "orange", comfort: 6, cost: 3000, unlock: { t: "career", job: "单口相声演员" } },
  { id: "pond", name: "池塘", floor: "green", comfort: 5, cost: 1500, unlock: { t: "career", job: "冒险家" } },
  { id: "karaoke", name: "卡拉OK房", floor: "green", comfort: 8, cost: 4000, unlock: { t: "career", job: "作曲家" } },
  { id: "white_window", name: "白色的窗", floor: "green", comfort: 6, cost: 2000, unlock: { t: "career", job: "宇航员" } },
  { id: "plant", name: "盆栽", floor: "green", comfort: 4, cost: 1500, unlock: { t: "career", job: "运动员" } },
  { id: "big_tree", name: "大树", floor: "green", comfort: 6, cost: 2000, unlock: { t: "career", job: "芭蕾舞者" } },
  { id: "pine", name: "针叶树", floor: "green", comfort: 6, cost: 2000, unlock: { t: "career", job: "超级名模" } },
  { id: "colorful_carpet", name: "七彩地毯", floor: "green", comfort: 5, cost: 1500, unlock: { t: "career", job: "搞笑艺人" } },
  { id: "wall_lamp", name: "壁灯", floor: "elastic", comfort: 4, cost: 1500, unlock: { t: "career", job: "护士" } },
  { id: "ireland_kitchen", name: "爱尔兰厨房", floor: "orange", comfort: 8, cost: 4000, unlock: { t: "career", job: "学者" } },

  // --- 行为/事件解锁 (GDD §3.1 C) ---
  { id: "stylish_bed", name: "时髦的床", floor: "pink", comfort: 8, cost: 3000, unlock: { t: "behavior", key: "elderFirst" } },
  { id: "kitchen_set", name: "配套式厨房", floor: "orange", comfort: 8, cost: 3000, unlock: { t: "behavior", key: "fridge8" } },
  { id: "bar", name: "吧台", floor: "green", comfort: 7, cost: 2500, unlock: { t: "behavior", key: "karaoke" } },

  // --- 全冠解锁 ---
  { id: "deluxe_bath", name: "豪华浴室", floor: "tile", comfort: 6, cost: 5000, unlock: { t: "all_champion" } }
];

// 专门房间配方 (22 个, 按床基类) floor + 配置 need1/need2 + 房租/舒适/属性
var RECIPES = [
  { room: "宠物房", floor: "elastic", need1: ["bed_normal", "lamp"], need2: null, rent: 2, comfort: 3, attrs: ["宠物", "爱好", "人气"] },
  { room: "柔风屋", floor: "elastic", need1: ["fan", "window4"], need2: null, rent: 2, comfort: 0, attrs: ["锻炼"] },
  { room: "收藏室", floor: "elastic", need1: ["cabinet", "tansu"], need2: ["cabinet", "dresser"], rent: 5, comfort: 6, attrs: ["收集"] },
  { room: "收藏日式房间", floor: "elastic", need1: ["closet", "wardrobe"], need2: null, rent: 6, comfort: 7, attrs: ["收集"] },
  { room: "小孩房", floor: "green", need1: ["crib", "robot"], need2: null, rent: 6, comfort: 8, attrs: ["多向"] },
  { room: "鉴赏屋", floor: "green", need1: ["robot", "poster"], need2: null, rent: 6, comfort: 0, attrs: ["收集", "时尚"] },
  { room: "游戏间", floor: "green", need1: ["tv_digital", "game_console"], need2: null, rent: 5, comfort: 7, attrs: ["游戏"] },
  { room: "游戏室", floor: "green", need1: ["game_console", "arch"], need2: null, rent: 6, comfort: 0, attrs: ["游戏"] },
  { room: "草绿色房间", floor: "green", need1: ["vase", "carpet_elegant"], need2: null, rent: 4, comfort: 0, attrs: ["饲养"] },
  { room: "狂热者间", floor: "green", need1: ["bookshelf_big", "poster"], need2: null, rent: 5, comfort: 0, attrs: ["读书", "收集"] },
  { room: "便利的厨房甲", floor: "orange", need1: ["tableware", "fridge"], need2: null, rent: 8, comfort: 0, attrs: ["料理"] },
  { room: "便利的厨房乙", floor: "orange", need1: ["kitchen", "oven"], need2: null, rent: 8, comfort: 0, attrs: ["料理"] },
  { room: "家政间", floor: "orange", need1: ["vacuum", "clock"], need2: null, rent: 9, comfort: 0, attrs: ["时尚"] },
  { room: "电话室", floor: "pink", need1: ["phone", "carpet_elegant"], need2: null, rent: 9, comfort: 0, attrs: ["聊天"] },
  { room: "华丽的卧房", floor: "pink", need1: ["heart_bed", "heart_rug"], need2: null, rent: 7, comfort: 9, attrs: ["多向"] },
  { room: "化妆室", floor: "pink", need1: ["dress_table", "wash"], need2: null, rent: 6, comfort: 8, attrs: ["时尚"] },
  { room: "组合浴室", floor: "tile", need1: ["bath_indep", "toilet_west"], need2: null, rent: 3, comfort: 0, attrs: ["时尚"] },
  { room: "浴室", floor: "tile", need1: ["shower", "bathtub"], need2: null, rent: 4, comfort: 0, attrs: ["时尚"] },
  { room: "洗衣室", floor: "tile", need1: ["wash_dress", "washer"], need2: null, rent: 2, comfort: 0, attrs: ["时尚"] },
  { room: "洗澡间", floor: "tile", need1: ["sauna", "shower"], need2: null, rent: 3, comfort: 4, attrs: ["锻炼"] },
  { room: "浴池室", floor: "tile", need1: ["massage_bath", "sauna"], need2: null, rent: 4, comfort: 5, attrs: ["锻炼"] },
  { room: "泡澡间", floor: "tile", need1: ["japan_bath", "massage_bath"], need2: null, rent: 2, comfort: 3, attrs: ["锻炼"] }
];

// 14 种兴趣 -> 可提升该兴趣的家具
var INTERESTS = {
  "学习": ["toilet_west", "washer", "cabinet"],
  "高科技": ["oven", "washer", "tv_digital", "bookshelf_big", "robot", "white_window"],
  "游戏": ["game_console", "poster", "arch"],
  "电影": ["tv_digital"],
  "读书": ["bookshelf_big", "closet"],
  "音乐": ["arch", "karaoke"],
  "运动": ["fan", "vacuum", "plant", "big_tree", "pine"],
  "锻炼": ["sauna", "massage_bath"],
  "饲养": ["vase", "robot", "pond"],
  "收集": ["cabinet", "poster", "bookshelf_big", "carpet", "colorful_carpet"],
  "聊天": ["phone", "clock", "bar"],
  "料理": ["kitchen", "fridge", "tableware", "kitchen_set", "ireland_kitchen"],
  "时尚": ["wash", "dress_table", "closet", "wardrobe", "stylish_bed"],
  "交通": ["vacuum", "pond"]
};

// 竞赛榜单 (8 项) 与 档次
var COMP_CATS = ["comfort", "rent", "brain", "hobby", "sport", "charm", "population", "wealth"];
var TIERS = ["local", "national", "world"];
// 各榜单 LOCAL 基准值 (对手强度), 乘以 tier 倍率生成对手
var COMP_PAR = { comfort: 400, rent: 400, brain: 350, hobby: 350, sport: 350, charm: 350, population: 6, wealth: 25000 };
var COMP_TIER_MULT = { local: 1.0, national: 1.8, world: 3.0 };

// 职业 (具名职业) income=基准月收入
var JOBS = [
  { name: "学生", income: 60 },
  { name: "自由职业者", income: 110 },
  { name: "上班族", income: 140 },
  { name: "艺术家", income: 130 },
  { name: "护士", income: 150 },
  { name: "程序员", income: 180 },
  { name: "学者", income: 200 },
  { name: "名模", income: 240 },
  { name: "指挥家", income: 260 },
  { name: "骑师", income: 160 },
  { name: "单口相声演员", income: 170 },
  { name: "冒险家", income: 190 },
  { name: "作曲家", income: 220 },
  { name: "鉴定师", income: 210 },
  { name: "宇航员", income: 280 },
  { name: "运动员", income: 200 },
  { name: "芭蕾舞者", income: 230 },
  { name: "超级名模", income: 250 },
  { name: "舞者", income: 210 },
  { name: "搞笑艺人", income: 180 }
];
// 职业首次转职 -> 解锁家具 (GDD §3.1 B)
var CAREER_FURN = {
  "骑师": "japan_bath", "鉴定师": "massage_bath", "舞者": "carpet_elegant", "指挥家": "heart_bed",
  "单口相声演员": "mini_company", "冒险家": "pond", "作曲家": "karaoke", "宇航员": "white_window",
  "运动员": "plant", "芭蕾舞者": "big_tree", "超级名模": "pine", "搞笑艺人": "colorful_carpet",
  "护士": "wall_lamp", "学者": "ireland_kitchen"
};
var NAMES = ["阿明", "小琳", "老王", "美佳", "大壮", "诗涵", "阿杰", "丽华", "志远", "晓彤", "建国", "雅婷", "浩然", "欣怡", "子轩", "若曦"];

// ---------------- 单一标准模式 (GDD 难度增强: 有点难度) ----------------
var DIFF = {
  label: "标准模式",
  sThreshold: 700,        // S 级需总舒适 >= 700
  upkeepMult: 1.2,        // 维护费倍率
  rentCap: 1.15,          // 单房租金上限倍率
  transferFailPenalty: 6, // 转职失败属性损失
  eventFreq: 0.10,        // 月度随机事件概率
  ageRate: 1.0,           // 月度老化速率
  recruitScale: 0.9,      // 招租研究点消耗缩放
  popTarget: 8            // 人口目标
};

// ---------------- 查询辅助 ----------------
function furnById(id) {
  for (var i = 0; i < FURNITURE.length; i++) { if (FURNITURE[i].id === id) return FURNITURE[i]; }
  return null;
}
function furnIndexInRoom(room, id) {
  for (var i = 0; i < room.furniture.length; i++) { if (room.furniture[i].id === id) return i; }
  return -1;
}
function containsAll(room, needArr) {
  if (!needArr) return false;
  for (var i = 0; i < needArr.length; i++) { if (furnIndexInRoom(room, needArr[i]) < 0) return false; }
  return true;
}
function detectSpecial(room) {
  var best = null;
  for (var i = 0; i < RECIPES.length; i++) {
    var r = RECIPES[i];
    if (r.floor !== room.floor) continue;
    if (containsAll(room, r.need1) || containsAll(room, r.need2)) {
      if (!best || r.comfort > best.comfort) best = r;
    }
  }
  return best;
}
function isUnlocked(f) {
  if (!f.unlock) return true;
  var u = f.unlock;
  if (u.t === "rank") return inArray(STATE.rankWins[u.tier], u.cat);
  if (u.t === "career") return !!STATE.firstCareer[u.job];
  if (u.t === "behavior") return !!STATE.behaviorFlags[u.key];
  if (u.t === "all_champion") return !!STATE.allChampion;
  return true;
}
function computeRoom(room) {
  var c = 0;
  for (var i = 0; i < room.furniture.length; i++) {
    var f = furnById(room.furniture[i].id);
    if (f) c += f.comfort;
  }
  var sp = detectSpecial(room);
  if (sp) { c += sp.comfort; room.specialName = sp.room; room.specialRent = sp.rent; room.specialAttrs = sp.attrs; }
  else { room.specialName = null; room.specialRent = 0; room.specialAttrs = []; }
  room.comfort = c;
  if (room.brokenMonths > 0) room.comfort = Math.floor(room.comfort / 2);
  return room.comfort;
}
function computeAllRooms() {
  var total = 0;
  for (var i = 0; i < STATE.rooms.length; i++) { total += computeRoom(STATE.rooms[i]); }
  STATE.totalComfort = total;
  return total;
}
function getRoom(id) {
  for (var i = 0; i < STATE.rooms.length; i++) { if (STATE.rooms[i].id === id) return STATE.rooms[i]; }
  return null;
}
function getResident(id) {
  if (!id) return null;
  for (var i = 0; i < STATE.residents.length; i++) { if (STATE.residents[i].id === id) return STATE.residents[i]; }
  return null;
}
function roomUpkeep(room) {
  var up = 5;
  for (var i = 0; i < room.furniture.length; i++) {
    var f = furnById(room.furniture[i].id);
    if (f) up += Math.round(f.cost / 400);
  }
  return up;
}
function computeRent(room, res) {
  var base = 50;
  var mult = 1 + room.comfort / 200;
  var special = room.specialRent > 0 ? 1.25 : 1;
  var rm = room.rentMult || 1;
  var r = base * mult * special * rm;
  r = Math.min(r, base * DIFF.rentCap * special * rm * (1 + room.comfort / 200)); // 上限保护
  if (res && res.kids > 0) r *= (1 + 0.1 * res.kids);
  return Math.round(r);
}
function ratingGrade(total) {
  var t = DIFF.sThreshold;
  if (total >= t) return "S";
  if (total >= t * 0.7) return "A";
  if (total >= t * 0.45) return "B";
  if (total >= t * 0.25) return "C";
  return "D";
}
function score() {
  var s = STATE.cash + STATE.research * 50 + STATE.tickets * 10 + STATE.totalComfort * 0.5;
  return Math.round(s);
}

// ---------------- 新游戏 ----------------
exports.new_game = function (params) {
  params = params || {};
  var seed = (typeof params.seed === "number") ? params.seed : 20260804;
  STATE = createInitialState(seed);
  return { success: true, mode: DIFF.label, message: "幸福公寓开业! 起始现金5000/研究点20/卷轴2, 4间房(弹/绿/橙/砖). 每月自动+1研究点(运营)且每住户+1研究点(租金衍生), 不会卡死在0研究点. 存档已绑定用户名(配置项username), 数据存 Supabase. 用 list_furniture 看家具, place_furniture 装修, recruit 招租, advance 收租.", state: snapshot() };
};

function snapshot() {
  var rooms = [];
  for (var i = 0; i < STATE.rooms.length; i++) {
    var rm = STATE.rooms[i];
    var res = getResident(rm.residentId);
    rooms.push({
      id: rm.id, floor: rm.floor, capacity: rm.capacity, furniture: rm.furniture.slice(),
      comfort: rm.comfort, brokenMonths: rm.brokenMonths, rentMult: rm.rentMult,
      specialName: rm.specialName, specialRent: rm.specialRent, specialAttrs: rm.specialAttrs,
      resident: res ? { id: res.id, name: res.name, job: res.job, age: res.age, brain: res.brain, sport: res.sport, hobby: res.hobby, charm: res.charm, kids: res.kids, married: res.married, sick: res.sick } : null,
      rent: res ? computeRent(rm, res) : 0
    });
  }
  return {
    time: { year: STATE.year, month: STATE.month },
    mode: DIFF.label,
    resources: { cash: STATE.cash, research: STATE.research, tickets: STATE.tickets, totalComfort: STATE.totalComfort, grade: ratingGrade(STATE.totalComfort), score: score() },
    sThreshold: DIFF.sThreshold,
    rooms: rooms,
    residents: STATE.residents.map(function (r) { return { id: r.id, name: r.name, job: r.job, age: r.age, brain: r.brain, sport: r.sport, hobby: r.hobby, charm: r.charm, kids: r.kids, married: r.married, sick: r.sick, cooldownTransfer: r.cooldownTransfer }; }),
    rankWins: STATE.rankWins,
    allChampion: STATE.allChampion,
    unlockedCount: countUnlocked(),
    furnitureTotal: FURNITURE.length
  };
}
function countUnlocked() {
  var n = 0;
  for (var i = 0; i < FURNITURE.length; i++) { if (isUnlocked(FURNITURE[i])) n++; }
  return n;
}

// ---------------- 观察 / 建议 ----------------
exports.observe = function (params) {
  if (!STATE) return { success: false, error: "尚未开局, 请先 new_game" };
  return { success: true, state: snapshot() };
};

exports.legal_actions = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  var acts = [];
  acts.push("list_furniture(floor?) - 查看可用家具(含解锁状态)");
  acts.push("place_furniture(room_id, furniture_id) - 装修/触发专门房间");
  acts.push("remove_furniture(room_id, furniture_id) - 撤除家具(返还50%现金)");
  // 空房
  var empty = [];
  for (var i = 0; i < STATE.rooms.length; i++) { if (!STATE.rooms[i].residentId) empty.push(STATE.rooms[i].id); }
  if (empty.length > 0) acts.push("recruit(room_id=" + empty.join("/") + ") - 招租");
  acts.push("advance(months?) - 推进月份: 收租/维护/老化/事件");
  acts.push("enter_competition(category, tier) - 参赛(comfort/rent/brain/hobby/sport/charm/population/wealth x local/national/world)");
  acts.push("use_item(type, resident_id?, ...) - transfer/medicine/upgrade/young/love/child/rent_scroll/adv_transfer");
  acts.push("expand(floor) - 扩建新房(消耗现金+研究点)");
  acts.push("set_rent(room_id, mult) - 手动调整租金倍率(0.5~2)");
  return { success: true, actions: acts };
};

// ---------------- 家具 ----------------
exports.list_furniture = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var floor = params.floor || null;
  var out = [];
  for (var i = 0; i < FURNITURE.length; i++) {
    var f = FURNITURE[i];
    if (floor && f.floor !== floor) continue;
    var ok = isUnlocked(f);
    out.push({ id: f.id, name: f.name, floor: f.floor, comfort: f.comfort, cost: f.cost, unlocked: ok, unlock: ok ? null : f.unlock });
  }
  return { success: true, count: out.length, unlockedCount: countUnlocked(), total: FURNITURE.length, furniture: out };
};

exports.place_furniture = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var room = getRoom(params.room_id);
  if (!room) return { success: false, error: "房间不存在: " + params.room_id };
  var f = furnById(params.furniture_id);
  if (!f) return { success: false, error: "家具不存在: " + params.furniture_id };
  if (furnIndexInRoom(room, f.id) >= 0) return { success: false, error: "房间已有该家具" };
  if (room.furniture.length >= room.capacity) return { success: false, error: "房间容量已满(" + room.capacity + ")" };
  if (!isUnlocked(f)) return { success: false, error: "家具未解锁: " + f.name + " (条件:" + unlockDesc(f.unlock) + ")" };
  if (STATE.cash < f.cost) return { success: false, error: "现金不足(" + STATE.cash + "<" + f.cost + ")" };
  STATE.cash -= f.cost;
  room.furniture.push({ id: f.id });
  if (f.id === "fridge") { STATE.fridgeCount += 1; if (STATE.fridgeCount >= 8 && !STATE.behaviorFlags.fridge8) { STATE.behaviorFlags.fridge8 = true; STATE.unlocks.push("行为解锁: 冰箱放置8次 -> 配套式厨房"); } }
  computeAllRooms();
  var sp = room.specialName;
  return { success: true, room: room.id, comfort: room.comfort, special: sp, message: sp ? ("触发专门房间: " + sp) : "已放置" };
};

exports.remove_furniture = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var room = getRoom(params.room_id);
  if (!room) return { success: false, error: "房间不存在" };
  var idx = furnIndexInRoom(room, params.furniture_id);
  if (idx < 0) return { success: false, error: "房间无此家具" };
  var f = furnById(params.furniture_id);
  var refund = Math.round(f.cost * 0.5);
  STATE.cash += refund;
  room.furniture.splice(idx, 1);
  computeAllRooms();
  return { success: true, refund: refund, comfort: room.comfort, special: room.specialName };
};

function unlockDesc(u) {
  if (!u) return "开局可用";
  if (u.t === "rank") return u.cat + "榜" + tierName(u.tier) + "夺冠";
  if (u.t === "career") return "首次转职为" + u.job;
  if (u.t === "behavior") return "行为条件:" + u.key;
  if (u.t === "all_champion") return "八榜世界全冠";
  return "未知";
}
function tierName(t) { if (t === "local") return "地域"; if (t === "national") return "全国"; return "世界"; }

// ---------------- 招租 ----------------
exports.recruit = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var room = getRoom(params.room_id);
  if (!room) return { success: false, error: "房间不存在" };
  if (room.residentId) return { success: false, error: "房间已有居民" };
  var c = room.comfort;
  var tier, jobs, cost;
  if (c < 50) { tier = "低"; jobs = ["学生", "自由职业者"]; cost = 5; }
  else if (c < 90) { tier = "中"; jobs = ["上班族", "护士", "艺术家"]; cost = 35; }
  else if (c < 130) { tier = "高"; jobs = ["程序员", "名模", "学者"]; cost = 100; }
  else { tier = "顶"; jobs = ["指挥家", "宇航员", "鉴定师", "骑师", "作曲家"]; cost = 200; }
  cost = Math.round(cost * DIFF.recruitScale);
  if (STATE.research < cost) return { success: false, error: "研究点不足(" + STATE.research + "<" + cost + ") 需先竞赛/扩张积累" };
  STATE.research -= cost;
  var jobName = pickR(jobs, STATE.rng);
  var inc = jobIncome(jobName);
  var res = {
    id: "p" + (STATE.residents.length + 1),
    name: pickR(NAMES, STATE.rng),
    job: jobName, income: inc, age: rngRange(STATE.rng, 18, 55),
    brain: rngRange(STATE.rng, 15, 45), sport: rngRange(STATE.rng, 15, 45),
    hobby: rngRange(STATE.rng, 15, 45), charm: rngRange(STATE.rng, 15, 45),
    kids: 0, married: false, sick: false, sickMonths: 0, cooldownTransfer: 0
  };
  STATE.residents.push(res);
  room.residentId = res.id;
  // 行为解锁: 作曲家入住 -> karaoke; 老年 -> elderFirst
  if (jobName === "作曲家" && !STATE.behaviorFlags.karaoke) { STATE.behaviorFlags.karaoke = true; STATE.unlocks.push("行为解锁: 作曲家入住 -> 吧台"); }
  if (res.age >= 60 && !STATE.behaviorFlags.elderFirst) { STATE.behaviorFlags.elderFirst = true; STATE.unlocks.push("行为解锁: 首位老年居民 -> 时髦的床"); }
  return { success: true, resident: { id: res.id, name: res.name, job: res.job, income: res.income, age: res.age }, tier: tier, researchCost: cost, rent: computeRent(room, res) };
};
function jobIncome(name) {
  for (var i = 0; i < JOBS.length; i++) { if (JOBS[i].name === name) return JOBS[i].income; }
  return 100;
}

// ---------------- 扩建 ----------------
exports.expand = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var floor = params.floor || "elastic";
  var okFloor = inArray(["elastic", "green", "orange", "tile", "pink"], floor);
  if (!okFloor) return { success: false, error: "无效地板: " + floor };
  var n = STATE.rooms.length;
  var costCash = 1500 + n * 400;
  var costRes = 2 + Math.floor(n / 4);
  if (STATE.cash < costCash) return { success: false, error: "现金不足(" + STATE.cash + "<" + costCash + ")" };
  if (STATE.research < costRes) return { success: false, error: "研究点不足(" + STATE.research + "<" + costRes + ")" };
  STATE.cash -= costCash; STATE.research -= costRes;
  var id = "r" + (n + 1);
  STATE.rooms.push({ id: id, floor: floor, capacity: 4, furniture: [], comfort: 0, brokenMonths: 0, rentMult: 1, specialName: null, specialRent: 0, specialAttrs: [], residentId: null });
  return { success: true, room_id: id, floor: floor, costCash: costCash, costResearch: costRes, totalRooms: STATE.rooms.length };
};

// ---------------- 推进时间 ----------------
exports.advance = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var months = (typeof params.months === "number") ? params.months : 1;
  if (months < 1) months = 1;
  if (months > 60) months = 60;
  var events = [];
  for (var m = 0; m < months; m++) {
    stepMonth(events);
    if (!STATE) break;
  }
  computeAllRooms();
  return { success: true, time: { year: STATE.year, month: STATE.month }, cash: STATE.cash, debt: STATE.cash < 0 ? STATE.cash : 0, totalComfort: STATE.totalComfort, grade: ratingGrade(STATE.totalComfort), events: events.slice(-8) };
};

function stepMonth(events) {
  var rng = STATE.rng;
  // 收租
  var income = 0;
  for (var i = 0; i < STATE.rooms.length; i++) {
    var rm = STATE.rooms[i];
    var res = getResident(rm.residentId);
    if (res) income += computeRent(rm, res);
  }
  STATE.cash += income;
  // 研究点来源: 每月基础+1(公寓运营) + 每住户+1(租金衍生), 保证不会卡死在0研究点死循环
  STATE.research += 1 + STATE.residents.length;
  // 维护
  var upkeep = 0;
  for (var j = 0; j < STATE.rooms.length; j++) { upkeep += roomUpkeep(STATE.rooms[j]); }
  upkeep = Math.round(upkeep * DIFF.upkeepMult);
  STATE.cash -= upkeep;
  // 负债利息: 现金为负时每月额外罚息(开罗真实体验; 不强制赶人, 保证可破局)
  if (STATE.cash < 0) {
    var interest = Math.max(50, Math.ceil(-STATE.cash * 0.05));
    STATE.cash -= interest;
    events.push({ type: "debt", interest: interest, balance: STATE.cash });
  }
  // 老化 + 退休 + 病假
  var survivors = [];
  for (var k = 0; k < STATE.residents.length; k++) {
    var r = STATE.residents[k];
    r.age += Math.round(DIFF.ageRate);
    if (r.cooldownTransfer > 0) r.cooldownTransfer -= 1;
    if (r.sick) {
      r.sickMonths += 1;
      if (r.sickMonths >= 3 && rngChance(rng, 0.3)) {
        // 病逝搬走
        freeRoomOf(r.id);
        events.push({ type: "leave_sick", name: r.name });
        continue;
      }
    }
    if (r.age > 75) { freeRoomOf(r.id); events.push({ type: "retire", name: r.name, age: r.age }); continue; }
    if (r.age >= 60 && !STATE.behaviorFlags.elderFirst) { STATE.behaviorFlags.elderFirst = true; STATE.unlocks.push("行为解锁: 首位老年居民 -> 时髦的床"); }
    survivors.push(r);
  }
  STATE.residents = survivors;
  // 设备故障恢复
  for (var b = 0; b < STATE.rooms.length; b++) { if (STATE.rooms[b].brokenMonths > 0) STATE.rooms[b].brokenMonths -= 1; }
  // 随机事件
  if (rngChance(rng, DIFF.eventFreq)) {
    var etype = pickR(["sickness", "equipment", "festival", "complaint"], rng);
    if (etype === "sickness") {
      if (STATE.residents.length > 0) {
        var sr = pickR(STATE.residents, rng);
        var attr = pickR(["brain", "sport", "hobby", "charm"], rng);
        sr[attr] = Math.max(1, sr[attr] - 8);
        sr.sick = true; sr.sickMonths = 0;
        events.push({ type: "sickness", name: sr.name, attr: attr });
      }
    } else if (etype === "equipment") {
      var br = pickR(STATE.rooms, rng);
      br.brokenMonths = rngRange(rng, 1, 2);
      events.push({ type: "equipment", room: br.id, months: br.brokenMonths });
    } else if (etype === "festival") {
      var bonus = rngRange(rng, 500, 2000);
      STATE.cash += bonus;
      events.push({ type: "festival", bonus: bonus });
    } else if (etype === "complaint") {
      var pen = rngRange(rng, 200, 800);
      STATE.cash = Math.max(0, STATE.cash - pen);
      events.push({ type: "complaint", penalty: pen });
    }
  }
  // 月度推进
  STATE.month += 1;
  if (STATE.month > 12) { STATE.month = 1; STATE.year += 1; }
}
function freeRoomOf(resId) {
  for (var i = 0; i < STATE.rooms.length; i++) { if (STATE.rooms[i].residentId === resId) { STATE.rooms[i].residentId = null; return; } }
}

// ---------------- 竞赛 ----------------
function catVal(cat) {
  if (cat === "comfort") return STATE.totalComfort;
  if (cat === "rent") { var s = 0; for (var i = 0; i < STATE.rooms.length; i++) { var rr = getResident(STATE.rooms[i].residentId); if (rr) s += computeRent(STATE.rooms[i], rr); } return s; }
  if (cat === "brain") { var b = 0; for (var i = 0; i < STATE.residents.length; i++) b += STATE.residents[i].brain; return b; }
  if (cat === "hobby") { var h = 0; for (var i = 0; i < STATE.residents.length; i++) h += STATE.residents[i].hobby; return h; }
  if (cat === "sport") { var sp = 0; for (var i = 0; i < STATE.residents.length; i++) sp += STATE.residents[i].sport; return sp; }
  if (cat === "charm") { var c = 0; for (var i = 0; i < STATE.residents.length; i++) c += STATE.residents[i].charm; return c; }
  if (cat === "population") return STATE.residents.length;
  if (cat === "wealth") { var w = STATE.cash; for (var r = 0; r < STATE.rooms.length; r++) { for (var f = 0; f < STATE.rooms[r].furniture.length; f++) { var ff = furnById(STATE.rooms[r].furniture[f].id); if (ff) w += ff.cost * 0.3; } } w += STATE.research * 50 + STATE.tickets * 20; return Math.round(w); }
  return 0;
}
exports.enter_competition = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var cat = params.category;
  var tier = params.tier || "local";
  if (!inArray(COMP_CATS, cat)) return { success: false, error: "无效榜单: " + cat + " 可选:" + COMP_CATS.join("/") };
  if (!inArray(TIERS, tier)) return { success: false, error: "无效档次: " + tier + " 可选:local/national/world" };
  var player = catVal(cat);
  var mult = COMP_TIER_MULT[tier];
  var par = COMP_PAR[cat];
  var better = 0;
  var OPP = 9;
  for (var i = 0; i < OPP; i++) {
    var ov = Math.round(par * mult * (0.85 + STATE.rng() * 0.3));
    if (ov > player) better += 1;
  }
  var rank = better + 1;
  var won = false, gold = 0, research = 0, tickets = 0;
  if (rank === 1) {
    won = true;
    if (tier === "local") { gold = 3000; research = 3; }
    else if (tier === "national") { gold = 8000; research = 6; tickets = 1; }
    else { gold = 20000; research = 12; tickets = 3; }
    if (!inArray(STATE.rankWins[tier], cat)) STATE.rankWins[tier].push(cat);
    // 检查全冠
    if (tier === "world") {
      var all = true;
      for (var c2 = 0; c2 < COMP_CATS.length; c2++) { if (!inArray(STATE.rankWins.world, COMP_CATS[c2])) { all = false; break; } }
      if (all && !STATE.allChampion) { STATE.allChampion = true; STATE.unlocks.push("全冠解锁: 八榜世界冠军 -> 豪华浴室"); }
    }
  } else if (rank <= 3) {
    gold = Math.round((tier === "world" ? 20000 : tier === "national" ? 8000 : 3000) * 0.4);
    research = 2;
  } else if (rank <= 6) {
    gold = Math.round((tier === "world" ? 20000 : tier === "national" ? 8000 : 3000) * 0.15);
    research = 1;
  } else if (rank <= 10) {
    gold = 500;
  }
  STATE.cash += gold; STATE.research += research; STATE.tickets += tickets;
  return { success: true, category: cat, tier: tier, playerValue: player, rank: rank, total: OPP + 1, champion: won, reward: { gold: gold, research: research, tickets: tickets }, newlyUnlocked: (won ? (tier + ":" + cat) : null) };
};

// ---------------- 道具 ----------------
exports.use_item = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var type = params.type;
  if (!type) return { success: false, error: "缺少 type (transfer/medicine/upgrade/young/love/child/rent_scroll/adv_transfer)" };
  if (type === "transfer" || type === "adv_transfer") {
    var res = getResident(params.resident_id);
    if (!res) return { success: false, error: "居民不存在" };
    if (res.cooldownTransfer > 0) return { success: false, error: "转职冷却中(" + res.cooldownTransfer + "月)" };
    var target = params.target;
    if (!target) return { success: false, error: "缺少 target 职业名" };
    var exist = false;
    for (var i = 0; i < JOBS.length; i++) { if (JOBS[i].name === target) { exist = true; break; } }
    if (!exist) return { success: false, error: "无效职业: " + target };
    var costR = (type === "adv_transfer") ? 20 : 10;
    if (STATE.research < costR) return { success: false, error: "研究点不足(" + STATE.research + "<" + costR + ")" };
    STATE.research -= costR;
    var chance = (type === "adv_transfer") ? 0.9 : 0.7;
    if (rngChance(STATE.rng, chance)) {
      var prev = res.job;
      res.job = target; res.income = jobIncome(target);
      var unlockedFurn = null;
      if (!STATE.firstCareer[target]) {
        STATE.firstCareer[target] = true;
        var fid = CAREER_FURN[target];
        if (fid) { unlockedFurn = fid; STATE.unlocks.push("职业首转解锁: " + target + " -> " + furnById(fid).name); }
      }
      return { success: true, result: "success", resident: res.name, from: prev, to: target, unlockedFurniture: unlockedFurn };
    } else {
      var attr = pickR(["brain", "sport", "hobby", "charm"], STATE.rng);
      res[attr] = Math.max(1, res[attr] - DIFF.transferFailPenalty);
      res.cooldownTransfer = 3;
      return { success: true, result: "fail", resident: res.name, to: target, penaltyAttr: attr, penalty: DIFF.transferFailPenalty, cooldown: 3 };
    }
  }
  if (type === "medicine") {
    var mr = getResident(params.resident_id);
    if (!mr) return { success: false, error: "居民不存在" };
    if (!mr.sick) return { success: false, error: "居民未生病" };
    if (STATE.tickets < 1) return { success: false, error: "卷轴不足(需1)" };
    STATE.tickets -= 1; mr.sick = false; mr.sickMonths = 0;
    return { success: true, result: "cured", resident: mr.name };
  }
  if (type === "upgrade") {
    var ur = getResident(params.resident_id);
    if (!ur) return { success: false, error: "居民不存在" };
    var ua = params.target || "brain";
    if (!inArray(["brain", "sport", "hobby", "charm"], ua)) return { success: false, error: "无效属性:" + ua };
    if (STATE.research < 5) return { success: false, error: "研究点不足(需5)" };
    STATE.research -= 5; ur[ua] += 10;
    return { success: true, result: "upgraded", resident: ur.name, attr: ua, value: ur[ua] };
  }
  if (type === "young") {
    var yr = getResident(params.resident_id);
    if (!yr) return { success: false, error: "居民不存在" };
    if (STATE.tickets < 1) return { success: false, error: "卷轴不足(需1)" };
    STATE.tickets -= 1; yr.age = Math.max(16, yr.age - 5);
    return { success: true, result: "younger", resident: yr.name, age: yr.age };
  }
  if (type === "love") {
    var lr = getResident(params.resident_id);
    if (!lr) return { success: false, error: "居民不存在" };
    if (STATE.tickets < 1) return { success: false, error: "卷轴不足(需1)" };
    STATE.tickets -= 1; lr.married = true;
    return { success: true, result: "married", resident: lr.name };
  }
  if (type === "child") {
    var cr = getResident(params.resident_id);
    if (!cr) return { success: false, error: "居民不存在" };
    if (!cr.married) return { success: false, error: "需先 love 结婚" };
    if (STATE.tickets < 2) return { success: false, error: "卷轴不足(需2)" };
    STATE.tickets -= 2; cr.kids += 1;
    return { success: true, result: "child", resident: cr.name, kids: cr.kids };
  }
  if (type === "rent_scroll") {
    var room = getRoom(params.room_id);
    if (!room) return { success: false, error: "房间不存在" };
    if (STATE.tickets < 2) return { success: false, error: "卷轴不足(需2)" };
    STATE.tickets -= 2; room.rentMult = Math.min(2, (room.rentMult || 1) + 0.1);
    return { success: true, result: "rent_boost", room: room.id, rentMult: room.rentMult };
  }
  return { success: false, error: "未知 type: " + type };
};

// ---------------- 手动调租 ----------------
exports.set_rent = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  params = params || {};
  var room = getRoom(params.room_id);
  if (!room) return { success: false, error: "房间不存在" };
  var mult = params.mult;
  if (typeof mult !== "number") return { success: false, error: "缺少 mult" };
  room.rentMult = clamp(mult, 0.5, 2);
  var res = getResident(room.residentId);
  return { success: true, room: room.id, rentMult: room.rentMult, rent: res ? computeRent(room, res) : 0 };
};

// ---------------- 总结 ----------------
exports.summary = function (params) {
  if (!STATE) return { success: false, error: "尚未开局" };
  computeAllRooms();
  var lines = [];
  lines.push("== 幸福公寓·" + DIFF.label + " ==");
  lines.push("时间: 第" + STATE.year + "年" + STATE.month + "月");
  var resLine = "资源: 现金" + STATE.cash + " 研究点" + STATE.research + " 卷轴" + STATE.tickets;
  if (STATE.cash < 0) resLine += " [负债! 每月利息5%]";
  lines.push(resLine);
  lines.push("总舒适:" + STATE.totalComfort + " 评级:" + ratingGrade(STATE.totalComfort) + " (S需" + DIFF.sThreshold + ") 综合分:" + score());
  lines.push("房间:" + STATE.rooms.length + " 居民:" + STATE.residents.length + " 已解锁家具:" + countUnlocked() + "/" + FURNITURE.length);
  lines.push("夺冠记录: 地域[" + STATE.rankWins.local.join(",") + "] 全国[" + STATE.rankWins.national.join(",") + "] 世界[" + STATE.rankWins.world.join(",") + "]" + (STATE.allChampion ? " (全冠!)" : ""));
  var spRooms = [];
  for (var i = 0; i < STATE.rooms.length; i++) { if (STATE.rooms[i].specialName) spRooms.push(STATE.rooms[i].id + ":" + STATE.rooms[i].specialName); }
  if (spRooms.length > 0) lines.push("专门房间: " + spRooms.join(", "));
  var tips = [];
  if (STATE.residents.length < STATE.rooms.length) tips.push("向空房 recruit 居民以收租");
  if (STATE.cash < 0) tips.push("负债" + STATE.cash + "元, 每月利息5%, 尽快招租/赢竞赛还债(不会强制赶人)");
  else if (STATE.cash < 1000) tips.push("现金紧张, 注意维护费与租金平衡(维护倍率=" + DIFF.upkeepMult + ")");
  if (STATE.totalComfort < DIFF.sThreshold) tips.push("总舒适未达S(" + DIFF.sThreshold + "), 继续装修/激活专门房间");
  if (STATE.rankWins.world.length < COMP_CATS.length) tips.push("报名 enter_competition 冲击榜单拿奖励解锁家具");
  if (STATE.research < 5 && STATE.residents.length > 0) tips.push("研究点低别慌: 每 advance 一月, 基础+1且每住户+1, 多 advance 即可积累招租");
  if (tips.length > 0) lines.push("建议: " + tips.join("; "));
  return { success: true, text: lines.join("\n"), score: score(), grade: ratingGrade(STATE.totalComfort) };
};

// ==================== Supabase 持久化层 (硬编码, 复用 AI小窝 同一实例) ====================
var SUPABASE_URL = 'https://ogmlzwxwlbfmkdlafjrx.supabase.co';
var SUPABASE_KEY = 'sb_publishable_fjWcWqkNTBkdbs59fQkASg_UBczl3L_';
var TABLE_NAME = 'happy_apartment_data';

function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (cfg && typeof cfg === 'object') return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}
function getUsername() {
  var cfg = getConfig();
  return (cfg && cfg.username) ? cfg.username : '';
}

// 同步 fetch (宿主沙箱封装为阻塞调用, 返回 {ok,status,body})
function supabaseRequest(method, path, body) {
  var headers = { 'apikey': SUPABASE_KEY, 'Authorization': 'Bearer ' + SUPABASE_KEY, 'Content-Type': 'application/json' };
  if (method === 'POST' || method === 'PATCH') headers['Prefer'] = 'return=representation';
  var options = { method: method, headers: headers };
  if (body) options.body = JSON.stringify(body);
  try {
    var raw = fetch(SUPABASE_URL + path, options);
    var resp = (typeof raw === 'string') ? JSON.parse(raw) : raw;
    if (!resp || !resp.ok) return { status: resp ? (resp.status || 0) : 0, error: resp ? (resp.error || '请求失败') : '无响应' };
    var text = resp.body || '';
    var data = null;
    if (text) { try { data = JSON.parse(text); } catch (e) { data = text; } }
    return { status: resp.status, data: data };
  } catch (e) { return { status: 0, error: e.message || String(e) }; }
}

function fetchSaved(uname) {
  var res = supabaseRequest('GET', '/rest/v1/' + TABLE_NAME + '?home_id=eq.' + encodeURIComponent(uname));
  if (res.status === 200 && res.data && res.data.length > 0) return { ok: true, data: res.data[0].data };
  if (res.status === 200) return { ok: true, data: null };
  return { ok: false, error: res.error };
}

function upsertSaved(uname, dataObj) {
  var now = new Date().toISOString();
  try {
    var raw = fetch(SUPABASE_URL + '/rest/v1/' + TABLE_NAME + '?on_conflict=home_id', {
      method: 'POST',
      headers: { 'apikey': SUPABASE_KEY, 'Authorization': 'Bearer ' + SUPABASE_KEY, 'Content-Type': 'application/json', 'Prefer': 'resolution=merge-duplicates' },
      body: JSON.stringify({ home_id: uname, data: dataObj, updated_at: now })
    });
    var resp = (typeof raw === 'string') ? JSON.parse(raw) : raw;
    return !!(resp && resp.status >= 200 && resp.status < 300);
  } catch (e) { return false; }
}

function loadState(uname) {
  var r = fetchSaved(uname);
  if (r.ok && r.data) {
    if (!r.data.rng) r.data.rng = makeRng(r.data.seed || 20260804);  // rng 是函数, 存 supabase 会丢, 读回按 seed 重建
    return r.data;
  }
  if (r.ok && !r.data) return createInitialState(20260804);  // 无存档 -> 新建(末尾 saveState 写回)
  return STATE || createInitialState(20260804);              // 网络失败 -> 回退内存
}

function saveState(uname) {
  if (!STATE) return false;
  var json = JSON.stringify(STATE);   // 自动丢弃 rng(函数) 等非序列化字段
  var obj = JSON.parse(json);
  return upsertSaved(uname, obj);
}

function createInitialState(seed) {
  STATE = {
    seed: seed,
    rng: makeRng(seed),
    diff: DIFF,
    year: 1, month: 1,
    cash: 5000, research: 20, tickets: 2,
    rooms: [],
    residents: [],
    rankWins: { local: [], national: [], world: [] },
    firstCareer: {},
    behaviorFlags: {},
    allChampion: false,
    fridgeCount: 0,
    unlocks: [],
    log: []
  };
  var startFloors = ["elastic", "green", "orange", "tile"];
  for (var i = 0; i < startFloors.length; i++) {
    STATE.rooms.push({ id: "r" + (i + 1), floor: startFloors[i], capacity: 4, furniture: [], comfort: 0, brokenMonths: 0, rentMult: 1, specialName: null, specialRent: 0, specialAttrs: [], residentId: null });
  }
  computeAllRooms();
  return STATE;
}

// 统一包装: 每次工具调用从 Supabase 读最新存档, 调用后写回。配置缺用户名则报错。
function wrap(fn) {
  return function (args) {
    var uname = getUsername();
    if (!uname) return { success: false, error: '未配置用户名, 请在插件配置中填写 username(随便取一个名字, 作为存档隔离标识)' };
    STATE = loadState(uname);
    var r = fn(args || {});
    saveState(uname);
    return r;
  };
}
(function () {
  var names = ['new_game', 'observe', 'legal_actions', 'list_furniture', 'place_furniture', 'remove_furniture', 'recruit', 'expand', 'advance', 'enter_competition', 'use_item', 'set_rent', 'summary'];
  for (var i = 0; i < names.length; i++) {
    (function (n) {
      var orig = exports[n];
      if (!orig) return;
      exports[n] = wrap(orig);
    })(names[i]);
  }
})();
