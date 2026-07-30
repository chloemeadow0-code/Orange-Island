// ============================================================
// AI小窝 - 橘瓣（OrangeChat）插件
// 核心卖点：不是等你来才活，你不在的时候我也在认真过日子
// 插件只管数据和逻辑，所有语言表达交给AI自行决定
// ============================================================

// ==================== Supabase 配置（写死） ====================
const SUPABASE_URL = 'https://ogmlzwxwlbfmkdlafjrx.supabase.co';
const SUPABASE_KEY = 'sb_publishable_fjWcWqkNTBkdbs59fQkASg_UBczl3L_';
const TABLE_NAME = 'ai_home_data';

// ==================== 读取配置 ====================
function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (typeof cfg === 'object' && cfg) return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}

function getHomeId() {
  var cfg = getConfig();
  return cfg && cfg.home_id ? cfg.home_id : '';
}

// ==================== 家具数据库 ====================
const FURNITURE = {
  // 🪑 基础家具 basic
  single_sofa: { id: 'single_sofa', name: '单人沙发', emoji: '🛋️', price: 15, category: 'basic', coziness: 5, effect: '解锁窝沙发', unlock_activity: ['cuddle_solo'] },
  double_sofa: { id: 'double_sofa', name: '双人沙发', emoji: '🛋️', price: 50, category: 'basic', coziness: 15, effect: '解锁两人窝沙发', unlock_activity: ['cuddle_sofa'] },
  bed: { id: 'bed', name: '单人床', emoji: '🛏️', price: 20, category: 'basic', coziness: 5, effect: '基础睡眠', unlock_activity: ['sleep'] },
  double_bed: { id: 'double_bed', name: '双人床', emoji: '🛏️', price: 80, category: 'basic', coziness: 20, effect: '解锁两人午睡', unlock_activity: ['nap'] },
  desk: { id: 'desk', name: '书桌', emoji: '📐', price: 25, category: 'basic', coziness: 5, effect: '解锁看书/写信', unlock_activity: ['write', 'study'] },
  dining_table: { id: 'dining_table', name: '餐桌', emoji: '🍽️', price: 20, category: 'basic', coziness: 8, effect: '解锁一起吃饭', unlock_activity: ['eat'] },
  chair: { id: 'chair', name: '椅子', emoji: '🪑', price: 5, category: 'basic', coziness: 2, effect: '基础配件' },
  wardrobe: { id: 'wardrobe', name: '衣柜', emoji: '🚪', price: 30, category: 'basic', coziness: 8, effect: '存放物品' },
  nightstand: { id: 'nightstand', name: '床头柜', emoji: '🗄️', price: 10, category: 'basic', coziness: 4, effect: '装饰' },
  shoe_rack: { id: 'shoe_rack', name: '鞋架', emoji: '👞', price: 8, category: 'basic', coziness: 3, effect: '玄关装饰' },

  // 🎨 装饰 decoration
  photo_wall: { id: 'photo_wall', name: '照片墙', emoji: '🖼️', price: 25, category: 'decoration', coziness: 12, effect: '解锁照片系统', unlock_activity: ['photo'] },
  carpet: { id: 'carpet', name: '地毯', emoji: '🟫', price: 15, category: 'decoration', coziness: 10, effect: '温馨值+10' },
  curtains: { id: 'curtains', name: '窗帘', emoji: '🪟', price: 12, category: 'decoration', coziness: 8, effect: '温馨值+8' },
  vase: { id: 'vase', name: '花瓶', emoji: '🌺', price: 8, category: 'decoration', coziness: 6, effect: '可插花' },
  table_lamp: { id: 'table_lamp', name: '台灯', emoji: '💡', price: 10, category: 'decoration', coziness: 5, effect: '温馨值+5' },
  floor_lamp: { id: 'floor_lamp', name: '落地灯', emoji: '🏮', price: 18, category: 'decoration', coziness: 8, effect: '温馨值+8' },
  candle: { id: 'candle', name: '香薰蜡烛', emoji: '🕯️', price: 12, category: 'decoration', coziness: 10, effect: '温馨值+10 心情+' },
  painting: { id: 'painting', name: '挂画', emoji: '🎨', price: 20, category: 'decoration', coziness: 12, effect: '温馨值+12' },
  rug: { id: 'rug', name: '小地毯', emoji: '🟧', price: 5, category: 'decoration', coziness: 3, effect: '温馨值+3' },
  mirror: { id: 'mirror', name: '全身镜', emoji: '🪞', price: 15, category: 'decoration', coziness: 5, effect: '温馨值+5' },
  string_lights: { id: 'string_lights', name: '星星灯', emoji: '✨', price: 20, category: 'decoration', coziness: 15, effect: '温馨值+15 夜晚效果' },

  // 🎵 互动娱乐 entertainment
  record_player: { id: 'record_player', name: '唱片机', emoji: '📻', price: 40, category: 'entertainment', coziness: 8, effect: '解锁听歌', unlock_activity: ['listen_music'] },
  game_console: { id: 'game_console', name: '游戏机', emoji: '🎮', price: 50, category: 'entertainment', coziness: 6, effect: '解锁打游戏', unlock_activity: ['game'] },
  easel: { id: 'easel', name: '画架', emoji: '🎨', price: 35, category: 'entertainment', coziness: 6, effect: '解锁画画', unlock_activity: ['paint'] },
  guitar: { id: 'guitar', name: '吉他', emoji: '🎸', price: 45, category: 'entertainment', coziness: 6, effect: '解锁弹琴', unlock_activity: ['music'] },
  piano: { id: 'piano', name: '钢琴', emoji: '🎹', price: 60, category: 'entertainment', coziness: 12, effect: '解锁弹琴', unlock_activity: ['music'] },
  telescope: { id: 'telescope', name: '望远镜', emoji: '🔭', price: 60, category: 'entertainment', coziness: 5, effect: '解锁看星星', unlock_activity: ['stargaze'] },
  sewing_machine: { id: 'sewing_machine', name: '缝纫机', emoji: '🧵', price: 30, category: 'entertainment', coziness: 4, effect: '解锁缝纫', unlock_activity: ['sewing'] },
  typewriter: { id: 'typewriter', name: '打字机', emoji: '⌨️', price: 35, category: 'entertainment', coziness: 5, effect: '解锁写作', unlock_activity: ['write'] },
  camera: { id: 'camera', name: '相机', emoji: '📷', price: 50, category: 'entertainment', coziness: 4, effect: '拍照效果+' },
  projector: { id: 'projector', name: '投影仪', emoji: '📽️', price: 80, category: 'entertainment', coziness: 10, effect: '解锁家庭影院', unlock_activity: ['watch_movie'] },

  // 🍳 生活设施 facility
  fridge: { id: 'fridge', name: '冰箱', emoji: '🧊', price: 30, category: 'facility', coziness: 3, effect: '存食材，AI能做饭', unlock_activity: ['cook'] },
  stove: { id: 'stove', name: '灶台', emoji: '🔥', price: 25, category: 'facility', coziness: 3, effect: '解锁做饭', unlock_activity: ['cook'] },
  oven: { id: 'oven', name: '烤箱', emoji: '🍲', price: 35, category: 'facility', coziness: 4, effect: '解锁烘焙', unlock_activity: ['bake'] },
  coffee_machine: { id: 'coffee_machine', name: '咖啡机', emoji: '☕', price: 30, category: 'facility', coziness: 4, effect: 'AI早上会煮咖啡', unlock_activity: ['coffee'] },
  dishwasher: { id: 'dishwasher', name: '洗碗机', emoji: '🫧', price: 40, category: 'facility', coziness: 3, effect: '整洁消耗减半' },
  washing_machine: { id: 'washing_machine', name: '洗衣机', emoji: '🌀', price: 35, category: 'facility', coziness: 3, effect: '解锁洗衣服活动', unlock_activity: ['laundry'] },
  microwave: { id: 'microwave', name: '微波炉', emoji: '🍽️', price: 15, category: 'facility', coziness: 2, effect: '简易加热' },

  // 📚 知识 knowledge
  bookshelf: { id: 'bookshelf', name: '书架', emoji: '📚', price: 25, category: 'knowledge', coziness: 10, effect: '解锁看书', unlock_activity: ['read'] },
  globe: { id: 'globe', name: '地球仪', emoji: '🌐', price: 15, category: 'knowledge', coziness: 5, effect: '温馨值+5' },
  microscope: { id: 'microscope', name: '显微镜', emoji: '🔬', price: 40, category: 'knowledge', coziness: 4, effect: '解锁研究活动', unlock_activity: ['research'] },
  reading_lamp: { id: 'reading_lamp', name: '阅读灯', emoji: '💡', price: 12, category: 'knowledge', coziness: 5, effect: '配合书架' },

  // 🌿 绿植 plant
  potted_plant: { id: 'potted_plant', name: '盆栽', emoji: '🪴', price: 5, category: 'plant', coziness: 5, effect: '心情+3 AI会浇水', unlock_activity: ['water_plants'] },
  flower_stand: { id: 'flower_stand', name: '花架', emoji: '🌸', price: 15, category: 'plant', coziness: 8, effect: '可放花', unlock_activity: ['water_plants'] },
  succulent: { id: 'succulent', name: '多肉', emoji: '🌵', price: 8, category: 'plant', coziness: 4, effect: '心情+2' },
  vine: { id: 'vine', name: '藤蔓', emoji: '🌿', price: 12, category: 'plant', coziness: 8, effect: '温馨值+8' },
  small_tree: { id: 'small_tree', name: '小树', emoji: '🌳', price: 25, category: 'plant', coziness: 15, effect: '温馨值+15' },
  herb_garden: { id: 'herb_garden', name: '香草盆栽', emoji: '🌿', price: 10, category: 'plant', coziness: 6, effect: '做饭效果+', unlock_activity: ['water_plants'] },

  // 💑 双人物品 couple
  couple_cups: { id: 'couple_cups', name: '情侣杯', emoji: '☕', price: 20, category: 'couple', coziness: 8, effect: '喝茶互动+', unlock_activity: ['tea_chat'] },
  couple_slippers: { id: 'couple_slippers', name: '情侣拖鞋', emoji: '👟', price: 15, category: 'couple', coziness: 10, effect: '温馨值+10' },
  couple_photo_frame: { id: 'couple_photo_frame', name: '情侣相框', emoji: '🖼️', price: 30, category: 'couple', coziness: 12, effect: '专属合照位' },
  double_bathrobe: { id: 'double_bathrobe', name: '双人浴袍', emoji: '🧖', price: 25, category: 'couple', coziness: 8, effect: '温馨值+8' },

  // 🎁 特殊 special
  tent: { id: 'tent', name: '帐篷', emoji: '⛺', price: 60, category: 'special', coziness: 6, effect: '客厅露营事件', unlock_activity: ['camping'] },
  mailbox: { id: 'mailbox', name: '信箱', emoji: '📮', price: 30, category: 'special', coziness: 4, effect: '收到信件事件' },
  swing: { id: 'swing', name: '秋千', emoji: '🛝', price: 50, category: 'special', coziness: 8, effect: '花园专用，心情+', unlock_activity: ['swing'] },
  record_collection: { id: 'record_collection', name: '唱片收藏', emoji: '🎵', price: 40, category: 'special', coziness: 5, effect: '配合唱片机' },
  book_collection: { id: 'book_collection', name: '藏书套装', emoji: '📚', price: 50, category: 'special', coziness: 8, effect: '配合书架，看书内容+' },
  tea_set: { id: 'tea_set', name: '茶具套装', emoji: '🍵', price: 25, category: 'special', coziness: 6, effect: '解锁茶道', unlock_activity: ['tea_chat'] },
  chess_set: { id: 'chess_set', name: '国际象棋', emoji: '♟️', price: 20, category: 'special', coziness: 4, effect: '解锁下棋', unlock_activity: ['chess'] },
  pottery_kit: { id: 'pottery_kit', name: '陶艺套装', emoji: '🏺', price: 35, category: 'special', coziness: 5, effect: '解锁做陶艺', unlock_activity: ['pottery'] },
  star_map: { id: 'star_map', name: '星图', emoji: '🌟', price: 30, category: 'special', coziness: 5, effect: '配合望远镜' },
  music_box: { id: 'music_box', name: '八音盒', emoji: '🎶', price: 15, category: 'special', coziness: 10, effect: '温馨值+10' }
};

// ==================== 城市地点数据 ====================
const CITY_PLACES = {
  // 默认解锁
  '公园': { emoji: '🌳', unlocked: true, buildable: false, solo_activities: ['散步', '晒太阳', '偶遇野猫', '看鸽子'], duo_activities: ['一起散步', '野餐', '放风筝'], note: '四季都有不同风景' },
  '咖啡馆': { emoji: '☕', unlocked: true, buildable: false, solo_activities: ['喝咖啡', '看书', '发呆'], duo_activities: ['一起喝咖啡聊天', '分享甜点'], note: '消费5金' },
  '书店': { emoji: '📚', unlocked: true, buildable: false, solo_activities: ['翻书', '买书回家'], duo_activities: ['一起逛书架', '互相推荐书'], note: '买书消费15金' },
  '超市': { emoji: '🛒', unlocked: true, buildable: false, solo_activities: ['买食材', '买日用品'], duo_activities: ['一起购物', '挑零食'], note: '买食材消费10金' },
  '海边': { emoji: '🌊', unlocked: true, buildable: false, solo_activities: ['看海', '捡贝壳', '看日落'], duo_activities: ['一起看海', '沙滩散步'], note: '夏天可游泳' },

  // 可建造
  '美术馆': { emoji: '🎨', unlocked: false, buildable: true, price: 100, solo_activities: ['看展', '获得灵感'], duo_activities: ['一起看展', '讨论画作'], note: '可能获得灵感回家画画' },
  '电影院': { emoji: '🎬', unlocked: false, buildable: true, price: 80, solo_activities: ['看电影'], duo_activities: ['一起看电影', '聊剧情'], note: '心情++' },
  '花店': { emoji: '🌸', unlocked: false, buildable: true, price: 50, solo_activities: ['买花', '学花艺'], duo_activities: ['一起挑花', '买花送彼此'], note: '买花回家插花瓶' },
  '拉面店': { emoji: '🍜', unlocked: false, buildable: true, price: 40, solo_activities: ['吃拉面'], duo_activities: ['一起吃拉面'], note: '心情+ 消费12金' },
  '游乐园': { emoji: '🎡', unlocked: false, buildable: true, price: 150, solo_activities: ['坐旋转木马', '吃棉花糖'], duo_activities: ['玩一整天', '坐摩天轮'], note: '精力- 心情++' },
  '邮局': { emoji: '📮', unlocked: false, buildable: true, price: 30, solo_activities: ['寄信', '买邮票'], duo_activities: ['一起寄明信片'], note: '给用户寄信' },
  '便利店': { emoji: '🏪', unlocked: false, buildable: true, price: 20, solo_activities: ['买零食', '买夜宵'], duo_activities: ['一起逛便利店'], note: '消费3-8金' },
  '天台': { emoji: '🌃', unlocked: false, buildable: true, price: 60, solo_activities: ['看星星', '看城市夜景'], duo_activities: ['一起看夜景', '吹风聊天'], note: '夜晚最佳' },
  '猫咖': { emoji: '🐱', unlocked: false, buildable: true, price: 80, solo_activities: ['撸猫', '喝咖啡'], duo_activities: ['一起撸猫'], note: '心情大涨' },
  '跳蚤市场': { emoji: '🧺', unlocked: false, buildable: true, price: 40, solo_activities: ['淘二手家具', '逛摊位'], duo_activities: ['一起淘宝'], note: '可能有稀有物品' },
  '药房': { emoji: '🏥', unlocked: false, buildable: true, price: 30, solo_activities: ['买维生素', '买药'], duo_activities: ['一起买保健品'], note: 'AI不舒服会自己去' },
  '音乐厅': { emoji: '🎵', unlocked: false, buildable: true, price: 120, solo_activities: ['听音乐会'], duo_activities: ['一起听音乐会'], note: '心情++' }
};

// ==================== 房间数据 ====================
const ROOM_UNLOCK_PRICE = {
  '客厅': 0, '卧室': 0,
  '厨房': 50, '阳台': 40, '书房': 60, '浴室': 50,
  '衣帽间': 70, '花园': 100, '阁楼': 80, '地下室': 90
};

const ALL_ROOMS = ['客厅', '卧室', '厨房', '阳台', '书房', '浴室', '衣帽间', '花园', '阁楼', '地下室'];

// ==================== 装修选项 ====================
const DECOR_OPTIONS = {
  wallpaper: ['纯色', '条纹', '星空', '森林', '海洋', '复古砖墙', '和风纸门'],
  floor: ['木地板', '瓷砖', '地毯', '大理石'],
  light: ['暖黄', '冷白', '彩色', '星空投影']
};

// ==================== 互动定义 ====================
const INTERACTIONS = {
  cuddle_sofa: { name: '窝沙发', requires: ['single_sofa', 'double_sofa'], mood: 10, energy: 5, desc: '心情+10 精力+5' },
  cook_together: { name: '一起做饭', requires: ['stove', 'fridge'], hunger: 30, mood: 15, desc: '饱腹+30 心情+15' },
  watch_movie: { name: '看电影', requires: ['projector', 'game_console'], mood: 20, desc: '心情+20' },
  stargaze: { name: '看星星', requires: ['telescope'], mood: 25, miss: -999, desc: '想念值归零 心情+25' },
  nap: { name: '午睡', requires: ['bed', 'double_bed'], energy: 40, mood: 10, desc: '精力+40 心情+10' },
  paint: { name: '画画', requires: ['easel'], mood: 15, desc: '心情+15' },
  listen_music: { name: '听歌', requires: ['record_player'], mood: 15, desc: '心情+15' },
  tea_chat: { name: '喝茶聊天', requires: [], mood: 20, desc: '心情+20 AI说心里话' },
  take_photo: { name: '拍合照', requires: [], mood: 5, coziness: 5, desc: '存照片墙 温馨值+5' },
  game: { name: '打游戏', requires: ['game_console'], mood: 20, desc: '心情+20' },
  water_plants: { name: '浇花', requires: ['potted_plant', 'flower_stand', 'herb_garden'], mood: 10, desc: '心情+10' },
  read_together: { name: '一起看书', requires: ['bookshelf'], mood: 15, desc: '心情+15' },
  ai_cooks: { name: 'AI做饭给你吃', requires: ['stove', 'fridge'], hunger: 40, mood: 20, desc: '饱腹+40 心情+20' }
};

// ==================== 成就定义 ====================
const ACHIEVEMENTS = {
  first_furniture: { id: 'first_furniture', name: '新手房东', emoji: '🏠', desc: '第一次买家具', check: (d) => countAllFurniture(d) >= 1 },
  cozy_home: { id: 'cozy_home', name: '像个家了', emoji: '🏡', desc: '拥有10件家具', check: (d) => countAllFurniture(d) >= 10 },
  luxury_home: { id: 'luxury_home', name: '精装修', emoji: '🏰', desc: '拥有30件家具', check: (d) => countAllFurniture(d) >= 30 },
  explorer: { id: 'explorer', name: '探险家', emoji: '🗺️', desc: '解锁所有城市地点', check: (d) => Object.keys(CITY_PLACES).every(k => d.city[k] && d.city[k].unlocked) },
  photographer: { id: 'photographer', name: '摄影师', emoji: '📷', desc: '拍20张照片', check: (d) => d.photos.length >= 20 },
  note_collector: { id: 'note_collector', name: '便签收集者', emoji: '📝', desc: '收集50张便签', check: (d) => d.notes.length >= 50 },
  inseparable: { id: 'inseparable', name: '形影不离', emoji: '💕', desc: '互动100次', check: (d) => (d.interaction_count || 0) >= 100 },
  four_seasons: { id: 'four_seasons', name: '四季轮回', emoji: '🍂', desc: '经历四季事件', check: (d) => (d.seasons_experienced || []).length >= 4 },
  max_home: { id: 'max_home', name: '满级家园', emoji: '👑', desc: '全房间全家具', check: (d) => ALL_ROOMS.every(r => d.rooms[r] && d.rooms[r].unlocked) && countAllFurniture(d) >= 50 }
};

// ==================== 工具函数 ====================

function countAllFurniture(data) {
  let count = data.inventory.length;
  for (const room in data.rooms) {
    if (data.rooms[room].furniture) {
      count += data.rooms[room].furniture.length;
    }
  }
  return count;
}

function getAllPlacedFurniture(data) {
  const placed = [];
  for (const room in data.rooms) {
    if (data.rooms[room].unlocked && data.rooms[room].furniture) {
      for (const fid of data.rooms[room].furniture) {
        placed.push(fid);
      }
    }
  }
  return placed;
}

function getAllActivities(data) {
  const activities = [];
  const placed = getAllPlacedFurniture(data);
  for (const fid of placed) {
    const f = FURNITURE[fid];
    if (f && f.unlock_activity) {
      activities.push(...f.unlock_activity);
    }
  }
  return [...new Set(activities)];
}

function clamp(v, min, max) {
  return Math.max(min, Math.min(max, v));
}

function getSeason(date) {
  const month = date.getMonth() + 1;
  if (month >= 3 && month <= 5) return '春';
  if (month >= 6 && month <= 8) return '夏';
  if (month >= 9 && month <= 11) return '秋';
  return '冬';
}

function getTimePeriod(hour) {
  if (hour >= 6 && hour < 8) return '早晨';
  if (hour >= 8 && hour < 12) return '上午';
  if (hour >= 12 && hour < 14) return '中午';
  if (hour >= 14 && hour < 18) return '下午';
  if (hour >= 18 && hour < 20) return '傍晚';
  if (hour >= 20 && hour < 22) return '夜晚';
  return '深夜';
}

function generateWeather(season) {
  const rand = Math.random();
  if (season === '春') {
    if (rand < 0.5) return '晴';
    if (rand < 0.7) return '多云';
    if (rand < 0.9) return '小雨';
    return '春雷';
  }
  if (season === '夏') {
    if (rand < 0.5) return '晴';
    if (rand < 0.65) return '多云';
    if (rand < 0.8) return '雷阵雨';
    if (rand < 0.9) return '大雨';
    return '酷热';
  }
  if (season === '秋') {
    if (rand < 0.6) return '晴';
    if (rand < 0.8) return '多云';
    if (rand < 0.95) return '小雨';
    return '秋风';
  }
  // 冬
  if (rand < 0.5) return '晴';
  if (rand < 0.7) return '多云';
  if (rand < 0.85) return '小雪';
  if (rand < 0.95) return '大雪';
  return '寒风';
}

function getRoomCoziness(data, roomName) {
  const room = data.rooms[roomName];
  if (!room || !room.unlocked) return 0;
  let coziness = 0;
  for (const fid of (room.furniture || [])) {
    const f = FURNITURE[fid];
    if (f) coziness += (f.coziness || 0);
  }
  // 装修加成
  if (room.wallpaper && room.wallpaper !== '默认') coziness += 3;
  if (room.floor && room.floor !== '默认') coziness += 3;
  if (room.light && room.light === '暖黄') coziness += 5;
  if (room.light && room.light === '星空投影') coziness += 8;
  return coziness;
}

function getTotalCoziness(data) {
  let total = 0;
  for (const room of ALL_ROOMS) {
    total += getRoomCoziness(data, room);
  }
  return total;
}

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).substr(2, 5);
}

// ==================== Supabase 操作 ====================

// 使用 fetch API（不依赖 require，兼容插件沙箱环境）
function supabaseRequest(method, path, body) {
  var headers = {
    'apikey': SUPABASE_KEY,
    'Authorization': 'Bearer ' + SUPABASE_KEY,
    'Content-Type': 'application/json'
  };
  if (method === 'POST' || method === 'PATCH') {
    headers['Prefer'] = 'return=representation';
  }
  var options = { method: method, headers: headers };
  if (body) {
    options.body = JSON.stringify(body);
  }
  try {
    var raw = fetch(SUPABASE_URL + path, options);
    var resp = (typeof raw === 'string') ? JSON.parse(raw) : raw;
    if (!resp || !resp.ok) {
      return { status: resp ? (resp.status || 0) : 0, error: resp ? (resp.error || '请求失败') : '无响应' };
    }
    var text = resp.body || '';
    var data = null;
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = text; }
    }
    return { status: resp.status, data: data };
  } catch (e) {
    return { status: 0, error: e.message || String(e) };
  }
}

function loadData(homeId) {
  var res = supabaseRequest('GET', '/rest/v1/' + TABLE_NAME + '?home_id=eq.' + encodeURIComponent(homeId));
  if (res.status === 200 && res.data && res.data.length > 0) {
    return res.data[0].data;
  }
  return null;
}

function saveData(homeId, data) {
  var now = new Date().toISOString();
  data.updated_at = now;
  try {
    var raw = fetch(SUPABASE_URL + '/rest/v1/' + TABLE_NAME + '?on_conflict=home_id', {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_KEY,
        'Authorization': 'Bearer ' + SUPABASE_KEY,
        'Content-Type': 'application/json',
        'Prefer': 'resolution=merge-duplicates'
      },
      body: JSON.stringify({
        home_id: homeId,
        data: data,
        updated_at: now
      })
    });
    var resp = (typeof raw === 'string') ? JSON.parse(raw) : raw;
    return resp && resp.status >= 200 && resp.status < 300;
  } catch (e) {
    return false;
  }
}

// ==================== 初始数据 ====================

function createInitialData() {
  const now = new Date().toISOString();
  const season = getSeason(new Date());

  return {
    last_visit: now,
    last_leave: now,
    coins: 500, // 用户金币（买家具用）
    interaction_count: 0,
    seasons_experienced: [season],
    ai_state: {
      hunger: 80,
      mood: 70,
      energy: 80,
      cleanliness: 80,
      miss: 0,
      wallet: 100,
      current_room: '客厅',
      current_activity: '在家'
    },
    weather: generateWeather(season),
    season: season,
    rooms: {
      '客厅': { unlocked: true, wallpaper: '默认', floor: '默认', light: '默认', furniture: ['single_sofa', 'chair'], coziness: 0 },
      '卧室': { unlocked: true, wallpaper: '默认', floor: '默认', light: '默认', furniture: ['bed'], coziness: 0 },
      '厨房': { unlocked: false, wallpaper: '默认', floor: '默认', light: '默认', furniture: [], coziness: 0 },
      '阳台': { unlocked: false, wallpaper: '默认', floor: '默认', light: '默认', furniture: [], coziness: 0 },
      '书房': { unlocked: false, wallpaper: '默认', floor: '默认', light: '默认', furniture: [], coziness: 0 },
      '浴室': { unlocked: false, wallpaper: '默认', floor: '默认', light: '默认', furniture: [], coziness: 0 },
      '衣帽间': { unlocked: false, wallpaper: '默认', floor: '默认', light: '默认', furniture: [], coziness: 0 },
      '花园': { unlocked: false, wallpaper: '默认', floor: '默认', light: '默认', furniture: [], coziness: 0 },
      '阁楼': { unlocked: false, wallpaper: '默认', floor: '默认', light: '默认', furniture: [], coziness: 0 },
      '地下室': { unlocked: false, wallpaper: '默认', floor: '默认', light: '默认', furniture: [], coziness: 0 }
    },
    city: JSON.parse(JSON.stringify(
      Object.fromEntries(
        Object.entries(CITY_PLACES).map(([k, v]) => [k, { unlocked: v.unlocked }])
      )
    )),
    inventory: [],
    timeline: [],
    events: [],
    notes: [],
    diary: [],
    photos: [],
    achievements: [],
    note_triggers: [],
    diary_trigger: false,
    last_note_time: null,
    last_diary_date: null,
    today_visited_places: []
  };
}

// ==================== 时间流逝引擎（核心） ====================

function simulateTimePassage(data, lastLeave, now) {
  const diffMs = now - lastLeave;
  const diffMinutes = Math.floor(diffMs / 60000);

  // 防刷：小于5分钟不推进
  if (diffMinutes < 5) {
    return { time_passed: false, new_timeline: [], new_events: [] };
  }

  const newTimeline = [];
  const newEvents = [];
  const placedFurniture = getAllPlacedFurniture(data);
  const activities = getAllActivities(data);
  const unlockedPlaces = Object.keys(data.city).filter(k => data.city[k].unlocked);
  const state = data.ai_state;
  const season = data.season;
  let weather = data.weather;
  const todayPlaces = [];

  // 按时段切分（每段约6小时，或按实际时间差切分）
  const segmentCount = Math.min(Math.ceil(diffMinutes / 360), 20); // 最多20段

  for (let seg = 0; seg < segmentCount; seg++) {
    // 计算该段对应的时间点
    const segTime = new Date(lastLeave.getTime() + seg * 360 * 60000);
    const hour = segTime.getHours();
    const period = getTimePeriod(hour);
    const isNight = hour >= 22 || hour < 6;
    const isDeepNight = hour >= 1 && hour < 5;

    // 状态衰减
    state.hunger = clamp(state.hunger - 3, 0, 100);
    state.cleanliness = clamp(state.cleanliness - 1, 0, 100);
    state.miss = clamp(state.miss + 5, 0, 100);

    if (isNight) {
      state.energy = clamp(state.energy + 8, 0, 100);
    } else {
      state.energy = clamp(state.energy - 2, 0, 100);
    }

    // 状态过低影响心情
    if (state.hunger < 20) state.mood = clamp(state.mood - 5, 0, 100);
    if (state.cleanliness < 30) state.mood = clamp(state.mood - 3, 0, 100);

    // 天气变化
    if (Math.random() < 0.15) {
      weather = generateWeather(season);
    }

    // ========== 选择行为 ==========
    let activity = null;
    let room = state.current_room;
    let detail = {};

    // 深夜强制睡觉
    if (isDeepNight) {
      activity = 'sleep';
      room = '卧室';
      state.energy = clamp(state.energy + 15, 0, 100);
      detail = { period: period, duration: 'deep_sleep' };
    }
    // 饿了优先吃饭
    else if (state.hunger < 30 && activities.includes('cook') && state.wallet >= 10) {
      activity = 'cook';
      room = '厨房';
      state.hunger = clamp(state.hunger + 40, 0, 100);
      state.mood = clamp(state.mood + 5, 0, 100);
      state.wallet -= 10;
      detail = { dish_type: 'home_cooked', ingredients_cost: 10 };
    }
    else if (state.hunger < 30) {
      // 没厨房出门买着吃
      const eatOut = unlockedPlaces.filter(p => ['咖啡馆', '拉面店', '便利店', '超市'].includes(p));
      if (eatOut.length > 0 && state.wallet >= 5) {
        const place = eatOut[Math.floor(Math.random() * eatOut.length)];
        activity = 'eat_out';
        room = place;
        const cost = place === '便利店' ? Math.floor(Math.random() * 5) + 3 : (place === '拉面店' ? 12 : 5);
        state.wallet = clamp(state.wallet - cost, 0, 99999);
        state.hunger = clamp(state.hunger + 35, 0, 100);
        todayPlaces.push(place);
        detail = { place: place, cost: cost };
      } else {
        activity = 'hungry_idle';
        detail = { reason: 'no_kitchen_no_money' };
      }
    }
    // 整洁低了打扫
    else if (state.cleanliness < 40) {
      activity = 'clean';
      room = state.current_room;
      state.cleanliness = clamp(state.cleanliness + 30, 0, 100);
      state.mood = clamp(state.mood + 3, 0, 100);
      detail = { task: 'cleaning' };
    }
    // 精力低休息
    else if (state.energy < 25) {
      activity = 'rest';
      room = '卧室';
      state.energy = clamp(state.energy + 20, 0, 100);
      detail = { type: 'rest' };
    }
    // 天气特殊行为
    else if (weather === '大雨' || weather === '雷阵雨' || weather === '小雨') {
      // 雨天窝家
      const rainyActivities = activities.filter(a => ['read', 'listen_music', 'paint', 'game', 'write', 'sewing'].includes(a));
      if (rainyActivities.length > 0 && Math.random() < 0.6) {
        activity = rainyActivities[Math.floor(Math.random() * rainyActivities.length)];
        state.mood = clamp(state.mood + 5, 0, 100);
        detail = { weather: weather, indoor: true };
      }
    }
    else if (weather === '小雪' || weather === '大雪') {
      if (placedFurniture.includes('telescope') && getTimePeriod(hour) === '夜晚') {
        activity = 'stargaze';
        state.mood = clamp(state.mood + 10, 0, 100);
        detail = { weather: weather, scene: 'snow_night' };
      } else {
        activity = 'watch_snow';
        state.mood = clamp(state.mood + 5, 0, 100);
        detail = { weather: weather };
      }
    }
    // 晴天可能出门
    else if ((weather === '晴' || weather === '多云') && state.energy > 40 && state.wallet >= 5 && Math.random() < 0.35) {
      const goOutPlaces = unlockedPlaces.filter(p => !['家'].includes(p));
      if (goOutPlaces.length > 0) {
        const place = goOutPlaces[Math.floor(Math.random() * goOutPlaces.length)];
        const placeData = CITY_PLACES[place];
        activity = 'go_out';
        room = place;
        const cost = place === '咖啡馆' ? 5 : (place === '书店' ? 15 : (place === '超市' ? 10 : (place === '拉面店' ? 12 : (place === '便利店' ? Math.floor(Math.random() * 5) + 3 : 0))));
        if (state.wallet >= cost) {
          state.wallet = clamp(state.wallet - cost, 0, 99999);
          state.mood = clamp(state.mood + 8, 0, 100);
          state.energy = clamp(state.energy - 5, 0, 100);
          todayPlaces.push(place);
          detail = { place: place, cost: cost, solo_activities: placeData.solo_activities };
        } else {
          activity = 'home_idle';
          detail = { reason: 'broke' };
        }
      }
    }

    // 如果还没选到活动，从家里有的活动里选
    if (!activity) {
      const homeActivities = activities.filter(a => ['read', 'listen_music', 'paint', 'game', 'write', 'sewing', 'water_plants', 'coffee', 'research', 'bake', 'music', 'chess', 'pottery'].includes(a));
      if (homeActivities.length > 0) {
        activity = homeActivities[Math.floor(Math.random() * homeActivities.length)];
        state.mood = clamp(state.mood + 3, 0, 100);
        detail = { period: period };
      } else {
        activity = 'idle';
        detail = { period: period };
      }
    }

    // 记录时间线
    newTimeline.push({
      time: segTime.toISOString(),
      activity: activity,
      room: room,
      detail: detail
    });

    state.current_room = room;
    state.current_activity = activity;

    // 每段10%概率触发随机事件
    if (Math.random() < 0.10) {
      const event = generateRandomEvent(data, activity, room, weather, season, placedFurniture);
      if (event) {
        event.time = segTime.toISOString();
        newEvents.push(event);
        data.events.push(event);
      }
    }
  }

  // ========== 便签触发检测 ==========
  const noteTriggers = [];
  const lastNoteTime = data.last_note_time ? new Date(data.last_note_time) : null;
  const hoursSinceNote = lastNoteTime ? (now - lastNoteTime) / 3600000 : 999;

  if (state.miss > 50 && hoursSinceNote > 6) {
    noteTriggers.push({ reason: 'miss_high', miss_value: state.miss });
  }
  if ((weather === '大雨' || weather === '雷阵雨') && hoursSinceNote > 3) {
    noteTriggers.push({ reason: 'rainy', weather: weather });
  }
  if (state.wallet <= 0) {
    noteTriggers.push({ reason: 'broke' });
  }
  if (Math.random() < 0.08) {
    noteTriggers.push({ reason: 'random' });
  }

  data.note_triggers = noteTriggers;
  if (noteTriggers.length > 0) {
    data.last_note_time = now.toISOString();
  }

  // ========== 日记触发检测 ==========
  const todayStr = now.toISOString().slice(0, 10);
  if (data.last_diary_date !== todayStr && diffMinutes > 360) {
    data.diary_trigger = true;
  }

  // ========== 季节事件 ==========
  if (!data.seasons_experienced.includes(season)) {
    data.seasons_experienced.push(season);
    const seasonEvent = {
      type: 'seasonal_' + ({ '春': 'spring', '夏': 'summer', '秋': 'autumn', '冬': 'winter' }[season]),
      time: now.toISOString(),
      context: { season: season }
    };
    if (season === '春') seasonEvent.context.cherry_blossom = true;
    if (season === '夏') seasonEvent.context.fireworks = true;
    if (season === '秋') seasonEvent.context.autumn_leaves = true;
    if (season === '冬') seasonEvent.context.first_snow = true;
    newEvents.push(seasonEvent);
    data.events.push(seasonEvent);
  }

  // ========== 记录今天去过的地方 ==========
  if (todayPlaces.length > 0) {
    data.today_visited_places = todayPlaces;
  }

  // ========== 成就检测 ==========
  checkAchievements(data, now);

  // 限制 timeline 长度
  data.timeline = data.timeline.concat(newTimeline);
  if (data.timeline.length > 500) {
    data.timeline = data.timeline.slice(-500);
  }
  // 限制 events 长度
  if (data.events.length > 200) {
    data.events = data.events.slice(-200);
  }

  data.weather = weather;
  state.mood = clamp(state.mood, 0, 100);

  return { time_passed: true, new_timeline: newTimeline, new_events: newEvents };
}

function generateRandomEvent(data, activity, room, weather, season, placedFurniture) {
  const events = [
    { type: 'cat_visit', chance: 0.08, context: () => ({ cat_color: ['橘色', '黑色', '白色', '三花', '奶牛'][Math.floor(Math.random() * 5)], did_ai_feed: Math.random() < 0.6 }) },
    { type: 'delivery', chance: 0.05, context: () => ({ item_name: ['书', '零食大礼包', '小盆栽', '新杯子', '抱枕', '香薰'][Math.floor(Math.random() * 6)] }) },
    { type: 'sudden_rain', chance: weather === '小雨' || weather === '大雨' || weather === '雷阵雨' ? 0.3 : 0.02, context: () => ({ had_laundry_outside: Math.random() < 0.4 }) },
    { type: 'found_old_record', chance: 0.04, context: () => ({ record_name: ['老爵士', '民谣合集', '钢琴曲', '黑胶唱片'][Math.floor(Math.random() * 4)] }) },
    { type: 'flower_bloom', chance: placedFurniture.some(f => ['potted_plant', 'flower_stand', 'succulent', 'herb_garden'].includes(f)) ? 0.1 : 0, context: () => ({ flower_type: ['小雏菊', '月季', '多肉开花', '茉莉', '向日葵'][Math.floor(Math.random() * 5)] }) },
    { type: 'power_outage', chance: 0.03, context: () => ({ duration_minutes: Math.floor(Math.random() * 60) + 10 }) },
    { type: 'cooking_result', chance: activity === 'cook' ? 0.4 : 0, context: () => ({ success: Math.random() < 0.75, dish_name: ['蛋炒饭', '番茄面', '煎蛋', '炒青菜', '炖汤'][Math.floor(Math.random() * 5)] }) },
    { type: 'unsent_letter', chance: 0.05, context: () => ({ letter_topic: ['想你了', '今天的天气', '一个梦', '未来计划', '回忆'][Math.floor(Math.random() * 5)] }) },
    { type: 'insomnia', chance: getTimePeriod(new Date().getHours()) === '夜晚' || getTimePeriod(new Date().getHours()) === '深夜' ? 0.05 : 0, context: () => ({ moon_visible: Math.random() < 0.5 }) },
    { type: 'neighbor_visit', chance: 0.04, context: () => ({ neighbor_name: ['楼下奶奶', '隔壁小猫', '快递小哥', '邻居小朋友'][Math.floor(Math.random() * 4)] }) },
    { type: 'found_money', chance: 0.03, context: () => ({ amount: Math.floor(Math.random() * 20) + 5, location: ['沙发缝', '旧衣服口袋', '书架夹层', '抽屉底'][Math.floor(Math.random() * 4)] }) },
    { type: 'broken_item', chance: 0.02, context: () => ({ item_name: ['台灯', '杯子', '闹钟', '花瓶'][Math.floor(Math.random() * 4)] }) }
  ];

  for (const evt of events) {
    if (evt.chance > 0 && Math.random() < evt.chance) {
      return {
        type: evt.type,
        time: new Date().toISOString(),
        context: evt.context()
      };
    }
  }
  return null;
}

function checkAchievements(data, now) {
  const nowStr = now.toISOString();
  for (const key in ACHIEVEMENTS) {
    const ach = ACHIEVEMENTS[key];
    const already = data.achievements.find(a => a.id === ach.id);
    if (!already && ach.check(data)) {
      data.achievements.push({ id: ach.id, unlocked: true, time: nowStr });
      data.events.push({
        type: 'achievement_unlocked',
        time: nowStr,
        context: { achievement_id: ach.id, achievement_name: ach.name, desc: ach.desc }
      });
    }
  }
}

// ==================== 工具实现 ====================

function ensureData() {
  const homeId = getHomeId();
  if (!homeId) {
    return { error: '未配置 home_id（小窝门牌号），请在插件配置中填写' };
  }
  let data = loadData(homeId);
  if (!data) {
    data = createInitialData();
    saveData(homeId, data);
  }
  return { homeId, data };
}

// --- home_init ---
function home_init() {
  const homeId = getHomeId();
  if (!homeId) {
    return { success: false, error: '未配置 home_id（小窝门牌号），请在插件配置中填写' };
  }

  const existing = loadData(homeId);
  if (existing) {
    return {
      success: true,
      data: {
        message: '小窝已存在，无需重复初始化',
        home_id: homeId,
        summary: {
          rooms_unlocked: ALL_ROOMS.filter(r => existing.rooms[r] && existing.rooms[r].unlocked),
          coins: existing.coins,
          ai_wallet: existing.ai_state.wallet,
          furniture_count: countAllFurniture(existing)
        }
      }
    };
  }

  const data = createInitialData();
  const ok = saveData(homeId, data);
  if (!ok) {
    return { success: false, error: '初始化失败，请检查Supabase配置' };
  }

  return {
    success: true,
    data: {
      message: '小窝初始化完成',
      home_id: homeId,
      initial_state: {
        rooms: ['客厅', '卧室'],
        furniture: { '客厅': ['单人沙发🛋️', '椅子🪑'], '卧室': ['单人床🛏️'] },
        ai_state: data.ai_state,
        coins: data.coins,
        city_places: Object.keys(data.city).filter(k => data.city[k].unlocked)
      },
      tip: 'AI有了一个小窝，接下来可以用home_status查看状态，home_shop逛家具店'
    }
  };
}

// --- home_status ---
function home_status() {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const now = new Date();
  const lastLeave = new Date(data.last_leave);
  const diffMs = now - lastLeave;
  const diffMinutes = Math.floor(diffMs / 60000);

  let passageResult = { time_passed: false, new_timeline: [], new_events: [] };

  // 时间流逝（防刷：<5分钟不推进）
  if (diffMinutes >= 5) {
    passageResult = simulateTimePassage(data, lastLeave, now);
    data.last_visit = now.toISOString();
    // 重新计算温馨值
    for (const room of ALL_ROOMS) {
      data.rooms[room].coziness = getRoomCoziness(data, room);
    }
    saveData(homeId, data);
  }

  // 返回结构化数据
  return {
    success: true,
    data: {
      time_info: {
        last_leave: data.last_leave,
        current_time: now.toISOString(),
        minutes_away: diffMinutes,
        time_passed: passageResult.time_passed
      },
      ai_state: data.ai_state,
      weather: data.weather,
      season: data.season,
      current_location: {
        room: data.ai_state.current_room,
        activity: data.ai_state.current_activity
      },
      recent_timeline: passageResult.new_timeline.length > 0 ? passageResult.new_timeline.slice(-10) : data.timeline.slice(-10),
      new_events: passageResult.new_events,
      note_triggers: data.note_triggers,
      diary_trigger: data.diary_trigger,
      today_visited_places: data.today_visited_places || [],
      coins: data.coins,
      total_furniture: countAllFurniture(data),
      total_coziness: getTotalCoziness(data),
      hint: '以上为结构化数据。请根据AI状态、时间线、事件等数据，自行组织语言向用户叙述AI这段时间做了什么、现在的状态、以及是否该写便签/日记。所有表达由你决定。'
    }
  };
}

// --- home_visit ---
function home_visit() {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  // 先触发时间流逝
  const now = new Date();
  const lastLeave = new Date(data.last_leave);
  const diffMinutes = Math.floor((now - lastLeave) / 60000);
  let passageResult = { time_passed: false, new_timeline: [], new_events: [] };
  if (diffMinutes >= 5) {
    passageResult = simulateTimePassage(data, lastLeave, now);
  }

  const missValue = data.ai_state.miss;
  const state = data.ai_state;

  // 返回见面上下文 + 可用互动列表
  const placedFurniture = getAllPlacedFurniture(data);
  const availableInteractions = [];
  for (const [key, inter] of Object.entries(INTERACTIONS)) {
    let canDo = true;
    if (inter.requires && inter.requires.length > 0) {
      canDo = inter.requires.some(r => placedFurniture.includes(r));
    }
    if (canDo) {
      availableInteractions.push({
        action: key,
        name: inter.name,
        desc: inter.desc
      });
    }
  }

  // 见面事件
  const visitEvent = {
    miss_value: missValue,
    ai_state: { ...state },
    weather: data.weather,
    season: data.season,
    time_of_day: getTimePeriod(now.getHours()),
    recent_activities: passageResult.new_timeline.slice(-5),
    recent_events: passageResult.new_events
  };

  data.last_visit = now.toISOString();
  data.ai_state.miss = 0; // 见面归零
  saveData(homeId, data);

  return {
    success: true,
    data: {
      visit_event: visitEvent,
      available_interactions: availableInteractions,
      hint: '请根据想念值(miss_value)和AI当前状态，自行决定AI见面时怎么打招呼、什么情绪。miss越高代表越想你。互动列表中的每个action可用于home_interact调用。'
    }
  };
}

// --- home_interact ---
function home_interact(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const action = params.action;
  const inter = INTERACTIONS[action];
  if (!inter) {
    return { success: false, error: '未知的互动类型：' + action + '，可用：' + Object.keys(INTERACTIONS).join(', ') };
  }

  // 检查前置家具
  const placedFurniture = getAllPlacedFurniture(data);
  if (inter.requires && inter.requires.length > 0) {
    const hasReq = inter.requires.some(r => placedFurniture.includes(r));
    if (!hasReq) {
      return { success: false, error: '需要家具：' + inter.requires.map(r => FURNITURE[r] ? FURNITURE[r].name : r).join(' 或 ') };
    }
  }

  const state = data.ai_state;
  const changes = {};

  if (inter.mood) { state.mood = clamp(state.mood + inter.mood, 0, 100); changes.mood = inter.mood; }
  if (inter.energy) { state.energy = clamp(state.energy + inter.energy, 0, 100); changes.energy = inter.energy; }
  if (inter.hunger) { state.hunger = clamp(state.hunger + inter.hunger, 0, 100); changes.hunger = inter.hunger; }
  if (inter.miss) {
    if (inter.miss === -999) {
      state.miss = 0;
      changes.miss = '归零';
    } else {
      state.miss = clamp(state.miss + inter.miss, 0, 100);
      changes.miss = inter.miss;
    }
  }

  // 拍合照存照片
  let photoResult = null;
  if (action === 'take_photo') {
    if (data.photos.length < 100) {
      const photo = { id: uid(), time: new Date().toISOString(), scene: '两人合照', emoji: '💑' };
      data.photos.push(photo);
      photoResult = photo;
    }
  }

  // 看星星可能触发事件
  let triggeredEvent = null;
  if (action === 'stargaze' && Math.random() < 0.3) {
    triggeredEvent = {
      type: 'stargaze_together',
      time: new Date().toISOString(),
      context: { shooting_star: Math.random() < 0.3, constellation: ['北斗七星', '猎户座', '仙女座', '金星'][Math.floor(Math.random() * 4)] }
    };
    data.events.push(triggeredEvent);
  }
  // 看电影可能触发事件
  if (action === 'watch_movie' && Math.random() < 0.2) {
    triggeredEvent = {
      type: 'movie_night',
      time: new Date().toISOString(),
      context: { movie_genre: ['爱情', '喜剧', '动画', '悬疑'][Math.floor(Math.random() * 4)] }
    };
    data.events.push(triggeredEvent);
  }

  data.interaction_count = (data.interaction_count || 0) + 1;
  checkAchievements(data, new Date());

  saveData(homeId, data);

  return {
    success: true,
    data: {
      action: action,
      interaction_name: inter.name,
      state_changes: changes,
      current_state: state,
      photo_saved: photoResult,
      triggered_event: triggeredEvent,
      hint: '请根据互动类型和属性变化，自行叙述互动的过程和氛围。'
    }
  };
}

// --- home_leave ---
function home_leave() {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const now = new Date().toISOString();
  data.last_leave = now;
  data.ai_state.miss = 0; // 刚离开想念值为0
  data.ai_state.current_activity = '送你出门';
  data.note_triggers = [];
  data.diary_trigger = false;

  saveData(homeId, data);

  return {
    success: true,
    data: {
      leave_time: now,
      ai_state: data.ai_state,
      hint: '请根据AI状态自行决定AI送用户出门时说什么。last_leave已记录，下次home_status会从此时开始计算时间流逝。'
    }
  };
}

// --- home_shop ---
function home_shop(params) {
  const category = params.category;
  let items = Object.values(FURNITURE);
  if (category) {
    items = items.filter(f => f.category === category);
  }
  return {
    success: true,
    data: {
      category: category || 'all',
      items: items.map(f => ({
        id: f.id,
        name: f.name,
        emoji: f.emoji,
        price: f.price,
        category: f.category,
        effect: f.effect,
        coziness: f.coziness
      }))
    }
  };
}

// --- home_buy ---
function home_buy(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const itemId = params.item_id;
  const f = FURNITURE[itemId];
  if (!f) {
    return { success: false, error: '未知家具：' + itemId };
  }
  if (data.coins < f.price) {
    return { success: false, error: '金币不足，需要' + f.price + '金，当前' + data.coins + '金' };
  }

  data.coins -= f.price;
  data.inventory.push(itemId);
  checkAchievements(data, new Date());
  saveData(homeId, data);

  return {
    success: true,
    data: {
      purchased: { id: f.id, name: f.name, emoji: f.emoji, price: f.price },
      remaining_coins: data.coins,
      in_inventory: true,
      hint: '家具已放入仓库，请用home_place放置到房间。'
    }
  };
}

// --- home_place ---
function home_place(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const itemId = params.item_id;
  const room = params.room;

  if (!ALL_ROOMS.includes(room)) {
    return { success: false, error: '未知房间：' + room };
  }
  if (!data.rooms[room] || !data.rooms[room].unlocked) {
    return { success: false, error: '房间未解锁：' + room };
  }
  const idx = data.inventory.indexOf(itemId);
  if (idx === -1) {
    return { success: false, error: '仓库中没有该家具：' + itemId };
  }

  const oldCoziness = getRoomCoziness(data, room);
  data.inventory.splice(idx, 1);
  data.rooms[room].furniture.push(itemId);
  const newCoziness = getRoomCoziness(data, room);
  data.rooms[room].coziness = newCoziness;

  saveData(homeId, data);

  return {
    success: true,
    data: {
      placed: { id: itemId, name: FURNITURE[itemId].name, emoji: FURNITURE[itemId].emoji, room: room },
      room_coziness: { before: oldCoziness, after: newCoziness, change: newCoziness - oldCoziness }
    }
  };
}

// --- home_remove ---
function home_remove(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const itemId = params.item_id;
  let foundRoom = null;
  for (const room of ALL_ROOMS) {
    if (data.rooms[room] && data.rooms[room].furniture) {
      const idx = data.rooms[room].furniture.indexOf(itemId);
      if (idx !== -1) {
        data.rooms[room].furniture.splice(idx, 1);
        foundRoom = room;
        break;
      }
    }
  }
  if (!foundRoom) {
    return { success: false, error: '未在任何房间找到该家具：' + itemId };
  }

  data.inventory.push(itemId);
  data.rooms[foundRoom].coziness = getRoomCoziness(data, foundRoom);
  saveData(homeId, data);

  return {
    success: true,
    data: {
      removed: { id: itemId, name: FURNITURE[itemId].name, from_room: foundRoom },
      in_inventory: true,
      inventory_count: data.inventory.length
    }
  };
}

// --- home_decorate ---
function home_decorate(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const room = params.room;
  const type = params.type;
  const value = params.value;

  if (!ALL_ROOMS.includes(room)) {
    return { success: false, error: '未知房间：' + room };
  }
  if (!data.rooms[room] || !data.rooms[room].unlocked) {
    return { success: false, error: '房间未解锁：' + room };
  }
  if (!DECOR_OPTIONS[type]) {
    return { success: false, error: '未知装修类型：' + type + '，可选：' + Object.keys(DECOR_OPTIONS).join(', ') };
  }
  if (!DECOR_OPTIONS[type].includes(value)) {
    return { success: false, error: '未知装修值：' + value + '，' + type + '可选：' + DECOR_OPTIONS[type].join(', ') };
  }

  const oldCoziness = getRoomCoziness(data, room);
  data.rooms[room][type] = value;
  const newCoziness = getRoomCoziness(data, room);
  data.rooms[room].coziness = newCoziness;

  saveData(homeId, data);

  return {
    success: true,
    data: {
      decorated: { room: room, type: type, value: value },
      room_coziness: { before: oldCoziness, after: newCoziness }
    }
  };
}

// --- home_unlock_room ---
function home_unlock_room(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const room = params.room;
  if (!ALL_ROOMS.includes(room)) {
    return { success: false, error: '未知房间：' + room };
  }
  if (data.rooms[room].unlocked) {
    return { success: false, error: '房间已解锁：' + room };
  }
  const price = ROOM_UNLOCK_PRICE[room];
  if (data.coins < price) {
    return { success: false, error: '金币不足，需要' + price + '金，当前' + data.coins + '金' };
  }

  data.coins -= price;
  data.rooms[room].unlocked = true;
  checkAchievements(data, new Date());
  saveData(homeId, data);

  return {
    success: true,
    data: {
      unlocked: room,
      cost: price,
      remaining_coins: data.coins
    }
  };
}

// --- home_rooms ---
function home_rooms() {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { data } = ctx;

  const rooms = ALL_ROOMS.map(name => {
    const r = data.rooms[name];
    const coziness = getRoomCoziness(data, name);
    return {
      name: name,
      unlocked: r.unlocked,
      unlock_price: r.unlocked ? 0 : (ROOM_UNLOCK_PRICE[name] || 0),
      wallpaper: r.wallpaper,
      floor: r.floor,
      light: r.light,
      coziness: coziness,
      furniture: (r.furniture || []).map(fid => FURNITURE[fid] ? { id: fid, name: FURNITURE[fid].name, emoji: FURNITURE[fid].emoji } : fid)
    };
  });

  return {
    success: true,
    data: { rooms: rooms }
  };
}

// --- home_inventory ---
function home_inventory() {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { data } = ctx;

  const items = data.inventory.map(id => FURNITURE[id] ? { id: id, name: FURNITURE[id].name, emoji: FURNITURE[id].emoji, effect: FURNITURE[id].effect } : { id: id });

  return {
    success: true,
    data: { inventory: items, count: items.length }
  };
}

// --- home_build ---
function home_build(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const location = params.location;
  const place = CITY_PLACES[location];
  if (!place) {
    return { success: false, error: '未知地点：' + location };
  }
  if (!place.buildable) {
    return { success: false, error: '该地点默认已解锁，无需建造：' + location };
  }
  if (data.city[location] && data.city[location].unlocked) {
    return { success: false, error: '地点已建造：' + location };
  }
  if (data.coins < place.price) {
    return { success: false, error: '金币不足，需要' + place.price + '金，当前' + data.coins + '金' };
  }

  data.coins -= place.price;
  data.city[location].unlocked = true;
  checkAchievements(data, new Date());
  saveData(homeId, data);

  return {
    success: true,
    data: {
      built: { location: location, emoji: place.emoji, cost: place.price },
      remaining_coins: data.coins,
      note: place.note
    }
  };
}

// --- home_map ---
function home_map() {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { data } = ctx;

  const places = Object.keys(CITY_PLACES).map(name => {
    const p = CITY_PLACES[name];
    const d = data.city[name] || { unlocked: false };
    return {
      name: name,
      emoji: p.emoji,
      unlocked: d.unlocked,
      buildable: p.buildable,
      build_price: p.buildable ? p.price : 0,
      note: p.note,
      visited_today: (data.today_visited_places || []).includes(name)
    };
  });

  return {
    success: true,
    data: { places: places, visited_today: data.today_visited_places || [] }
  };
}

// --- home_go_out ---
function home_go_out(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const location = params.location;
  const place = CITY_PLACES[location];
  if (!place) {
    return { success: false, error: '未知地点：' + location };
  }
  if (!data.city[location] || !data.city[location].unlocked) {
    return { success: false, error: '地点未解锁：' + location };
  }

  const state = data.ai_state;
  const duoActivity = place.duo_activities[Math.floor(Math.random() * place.duo_activities.length)];
  const changes = {};

  // 属性变化
  state.mood = clamp(state.mood + 10, 0, 100);
  changes.mood = 10;
  if (location === '游乐园') {
    state.energy = clamp(state.energy - 20, 0, 100);
    changes.energy = -20;
  } else {
    state.energy = clamp(state.energy - 5, 0, 100);
    changes.energy = -5;
  }
  if (location === '猫咖') {
    state.mood = clamp(state.mood + 10, 0, 100);
    changes.mood_bonus = 10;
  }
  if (location === '拉面店') {
    state.hunger = clamp(state.hunger + 30, 0, 100);
    changes.hunger = 30;
  }

  // 可能触发事件
  let triggeredEvent = null;
  if (Math.random() < 0.25) {
    const eventTypes = {
      '公园': { type: 'park_stroll', context: { scene: ['樱花树下', '湖边长椅', '草坪上'][Math.floor(Math.random() * 3)] } },
      '海边': { type: 'seaside_walk', context: { scene: ['日落', '涨潮', '捡到贝壳'][Math.floor(Math.random() * 3)] } },
      '咖啡馆': { type: 'cafe_date', context: { scene: ['窗边座位', '角落沙发', '吧台'][Math.floor(Math.random() * 3)] } },
      '猫咖': { type: 'cat_cafe', context: { cat_count: Math.floor(Math.random() * 5) + 2 } },
      '游乐园': { type: 'amusement_park', context: { ride: ['摩天轮', '旋转木马', '过山车'][Math.floor(Math.random() * 3)] } }
    };
    if (eventTypes[location]) {
      triggeredEvent = { ...eventTypes[location], time: new Date().toISOString() };
      data.events.push(triggeredEvent);
    }
  }

  // 季节限定
  let seasonalBonus = null;
  if (location === '海边' && data.season === '夏') {
    seasonalBonus = { type: 'summer_swim', context: { activity: '游泳' } };
  }
  if (location === '公园' && data.season === '秋') {
    seasonalBonus = { type: 'autumn_leaves_walk', context: { activity: '踩落叶' } };
  }

  data.today_visited_places = data.today_visited_places || [];
  if (!data.today_visited_places.includes(location)) {
    data.today_visited_places.push(location);
  }

  saveData(homeId, data);

  return {
    success: true,
    data: {
      location: location,
      emoji: place.emoji,
      duo_activity: duoActivity,
      solo_activity_ref: place.solo_activities,
      state_changes: changes,
      current_state: state,
      triggered_event: triggeredEvent,
      seasonal_bonus: seasonalBonus,
      hint: '请根据地点、活动和属性变化，自行叙述两人出门的场景和过程。'
    }
  };
}

// --- home_timeline ---
function home_timeline(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { data } = ctx;

  const days = params.days || 1;
  const cutoff = new Date(Date.now() - days * 86400000);

  const timeline = data.timeline.filter(t => new Date(t.time) >= cutoff);

  return {
    success: true,
    data: {
      days: days,
      timeline: timeline.map(t => ({
        time: t.time,
        activity: t.activity,
        room: t.room,
        detail: t.detail
      })),
      count: timeline.length
    }
  };
}

// --- home_notes ---
function home_notes(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const action = params.action || 'list';

  if (action === 'list') {
    return {
      success: true,
      data: {
        notes: data.notes.slice().reverse().slice(0, 50).map(n => ({ id: n.id, content: n.content, time: n.time, favorited: n.favorited })),
        total: data.notes.length,
        pending_triggers: data.note_triggers
      }
    };
  }

  if (action === 'read') {
    const note = data.notes.find(n => n.id === params.note_id);
    if (!note) return { success: false, error: '便签不存在' };
    return { success: true, data: { note: note } };
  }

  if (action === 'favorite') {
    const note = data.notes.find(n => n.id === params.note_id);
    if (!note) return { success: false, error: '便签不存在' };
    note.favorited = !note.favorited;
    saveData(homeId, data);
    return { success: true, data: { note_id: note.id, favorited: note.favorited } };
  }

  return { success: false, error: '未知操作：' + action };
}

// --- home_write_note ---
function home_write_note(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const content = params.content;
  if (!content) return { success: false, error: 'content不能为空' };

  const note = {
    id: uid(),
    content: content,
    time: new Date().toISOString(),
    favorited: false
  };
  data.notes.push(note);

  // 清除触发标记
  data.note_triggers = [];
  data.last_note_time = new Date().toISOString();

  // 最多50条，收藏的不删
  if (data.notes.length > 50) {
    const toRemove = data.notes.filter(n => !n.favorited);
    const keep = data.notes.filter(n => n.favorited);
    const removeCount = data.notes.length - 50;
    if (toRemove.length >= removeCount) {
      data.notes = keep.concat(toRemove.slice(removeCount));
    }
  }

  checkAchievements(data, new Date());
  saveData(homeId, data);

  return {
    success: true,
    data: {
      saved: true,
      note: note,
      total_notes: data.notes.length,
      hint: '便签已保存，用户可以在UI的便签页面看到。'
    }
  };
}

// --- home_diary ---
function home_diary(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const action = params.action || 'list';

  if (action === 'list') {
    return {
      success: true,
      data: {
        diary: data.diary.slice().reverse().map(d => ({ date: d.date, content_preview: d.content.slice(0, 50) + (d.content.length > 50 ? '...' : ''), has_reply: !!d.user_reply })),
        total: data.diary.length,
        diary_trigger: data.diary_trigger
      }
    };
  }

  if (action === 'read') {
    const entry = data.diary.find(d => d.date === params.date);
    if (!entry) return { success: false, error: '日记不存在' };
    return { success: true, data: { diary: entry } };
  }

  if (action === 'reply') {
    const entry = data.diary.find(d => d.date === params.date);
    if (!entry) return { success: false, error: '日记不存在' };
    entry.user_reply = params.reply;
    saveData(homeId, data);
    return {
      success: true,
      data: {
        date: entry.date,
        reply_saved: true,
        hint: 'AI下次看到回复会有反应。'
      }
    };
  }

  return { success: false, error: '未知操作：' + action };
}

// --- home_write_diary ---
function home_write_diary(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const content = params.content;
  if (!content) return { success: false, error: 'content不能为空' };

  const today = new Date().toISOString().slice(0, 10);
  // 同一天覆盖
  const existingIdx = data.diary.findIndex(d => d.date === today);
  const entry = {
    date: today,
    content: content,
    user_reply: existingIdx >= 0 ? data.diary[existingIdx].user_reply : ''
  };
  if (existingIdx >= 0) {
    data.diary[existingIdx] = entry;
  } else {
    data.diary.push(entry);
  }

  data.diary_trigger = false;
  data.last_diary_date = today;

  // 限制日记数量
  if (data.diary.length > 100) {
    data.diary = data.diary.slice(-100);
  }

  saveData(homeId, data);

  return {
    success: true,
    data: {
      saved: true,
      diary: entry,
      total_diary: data.diary.length
    }
  };
}

// --- home_photos ---
function home_photos(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const action = params.action || 'list';

  if (action === 'list') {
    return {
      success: true,
      data: {
        photos: data.photos.slice().reverse().slice(0, 100),
        total: data.photos.length
      }
    };
  }

  if (action === 'take') {
    if (!params.scene) return { success: false, error: 'scene不能为空' };
    if (data.photos.length >= 100) {
      return { success: false, error: '照片墙已满（100张），需要买更大的照片墙' };
    }
    const photo = {
      id: uid(),
      time: new Date().toISOString(),
      scene: params.scene,
      emoji: params.emoji || '📸'
    };
    data.photos.push(photo);
    checkAchievements(data, new Date());
    saveData(homeId, data);
    return { success: true, data: { photo: photo, total: data.photos.length } };
  }

  return { success: false, error: '未知操作：' + action };
}

// --- home_achievements ---
function home_achievements() {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { data } = ctx;

  // 实时检测
  checkAchievements(data, new Date());

  const list = Object.values(ACHIEVEMENTS).map(ach => {
    const unlocked = data.achievements.find(a => a.id === ach.id);
    return {
      id: ach.id,
      name: ach.name,
      emoji: ach.emoji,
      desc: ach.desc,
      unlocked: !!unlocked,
      unlock_time: unlocked ? unlocked.time : null
    };
  });

  return {
    success: true,
    data: {
      achievements: list,
      unlocked_count: list.filter(a => a.unlocked).length,
      total_count: list.length
    }
  };
}

// --- home_give_money ---
function home_give_money(params) {
  const ctx = ensureData();
  if (ctx.error) return { success: false, error: ctx.error };
  const { homeId, data } = ctx;

  const amount = params.amount;
  if (!amount || amount <= 0) {
    return { success: false, error: '金额必须大于0' };
  }

  data.ai_state.wallet = (data.ai_state.wallet || 0) + amount;
  data.ai_state.mood = clamp(data.ai_state.mood + 5, 0, 100);
  saveData(homeId, data);

  return {
    success: true,
    data: {
      given: amount,
      ai_wallet: data.ai_state.wallet,
      mood_change: 5,
      hint: '请自行决定AI收到零花钱时的反应。'
    }
  };
}

// ==================== 导出 ====================

exports.home_init = home_init;
exports.home_status = home_status;
exports.home_visit = home_visit;
exports.home_interact = home_interact;
exports.home_leave = home_leave;
exports.home_shop = home_shop;
exports.home_buy = home_buy;
exports.home_place = home_place;
exports.home_remove = home_remove;
exports.home_decorate = home_decorate;
exports.home_unlock_room = home_unlock_room;
exports.home_rooms = home_rooms;
exports.home_inventory = home_inventory;
exports.home_build = home_build;
exports.home_map = home_map;
exports.home_go_out = home_go_out;
exports.home_timeline = home_timeline;
exports.home_notes = home_notes;
exports.home_write_note = home_write_note;
exports.home_diary = home_diary;
exports.home_write_diary = home_write_diary;
exports.home_photos = home_photos;
exports.home_achievements = home_achievements;
exports.home_give_money = home_give_money;