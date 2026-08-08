// 情侣问卷·AI 插件  main.js  (Orange Island 插件, QuickJS 同步沙箱)
// 玩法: AI 通过工具 制定/发布/填写/提交/转发 问卷; 前端 ui/index.html 像问卷星一样展示并填答。
// 前后端共享同一 supabase 实例与三张表 (couple_surveys / couple_survey_responses / couple_survey_forwards)。
// 兼容约束: 同步函数; 用 var/传统函数; 不用 const/let/箭头/模板字符串; 不用 Array.filter/some/includes。
// 网络: 宿主把 fetch 封装为同步阻塞, 返回 { ok, status, body } 或字符串。

var SUPABASE_URL = 'https://nvkcztwjlbszvwkvbetf.supabase.co';
var SUPABASE_KEY = 'sb_publishable_UEd2Pn0kR2sau1Xsfk2TQw_oHC8pRhS';
var SURVEY_T = 'couple_surveys';
var RESP_T = 'couple_survey_responses';
var FWD_T = 'couple_survey_forwards';

// ---------------- 配置 (用户名 + 专属id) ----------------
function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (cfg && typeof cfg === 'object') return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}
function meOrErr() {
  var cfg = getConfig();
  var name = (cfg && cfg.username) ? cfg.username : '';
  var id = (cfg && cfg.owner_id) ? cfg.owner_id : (cfg && cfg.username ? cfg.username : '');
  if (!name || !id) return { error: '未配置用户名/专属id, 请在插件设置中填 username(用户名) 与 owner_id(专属ID)' };
  return { id: id, name: name };
}

// ---------------- 问卷模板库 (内置, 可扩展多套) ----------------
// 每套: { id, name, description, questions:[{type:'text'|'single'|'multiple', title, options?:[...]}] }
var TEMPLATES = [
  {
    id: 'couple_71',
    name: '情侣必答 71 问',
    description: '经典情侣深度问卷, 71 道开放题, 帮你们更了解彼此。',
    questions: [
      { type: 'text', title: '你认为女朋友是一个什么样的人？' },
      { type: 'text', title: '觉得女朋友哪里好？' },
      { type: 'text', title: '你最想让女朋友改正的问题是什么？' },
      { type: 'text', title: '你觉得值得纪念的日子是哪些？' },
      { type: 'text', title: '你认为你和你的女朋友合适吗？' },
      { type: 'text', title: '有没有某些瞬间很心疼女朋友？' },
      { type: 'text', title: '最希望听到对方说什么话？' },
      { type: 'text', title: '你觉得幸福是什么样的？' },
      { type: 'text', title: '最想让女朋友陪自己做什么？' },
      { type: 'text', title: '在一起的时间里最喜欢女朋友称呼你什么？' },
      { type: 'text', title: '如果我们吵了一次很凶的架，你会选择怎么和好？' },
      { type: 'text', title: '有时候会觉得女朋友很烦吗？为什么？' },
      { type: 'text', title: '最想和女朋友一起去哪里？' },
      { type: 'text', title: '女朋友什么地方最吸引你？' },
      { type: 'text', title: '你觉得恋爱中最重要的是什么？' },
      { type: 'text', title: '你认为现在的你，有给足女朋友安全感吗？' },
      { type: 'text', title: '恋爱中给自己打几分？为什么？' },
      { type: 'text', title: '最想对女朋友说的话是什么？' },
      { type: 'text', title: '如果以后会有更好的人出现，你会怎么做？' },
      { type: 'text', title: '如果主动找你要东西会觉得她拜金吗？' },
      { type: 'text', title: '用一种动物形容女朋友' },
      { type: 'text', title: '女朋友说什么你会难过？' },
      { type: 'text', title: '你会觉得女朋友无理取闹吗？' },
      { type: 'text', title: '对女朋友的第一印象' },
      { type: 'text', title: '女朋友说过什么话最让你生气？' },
      { type: 'text', title: '你的恋爱观是什么？' },
      { type: 'text', title: '如果一件礼物很贵但女朋友很喜欢，但是你买不起，你会怎么办？' },
      { type: 'text', title: '女朋友哪一点最让你感动？' },
      { type: 'text', title: '你觉得两个人在一起最重要的是什么？' },
      { type: 'text', title: '如果发现你的朋友在背后说你女朋友的坏话，你会怎么做？' },
      { type: 'text', title: '你的恋爱消费观是什么？' },
      { type: 'text', title: '谈恋爱后你有后悔吗？' },
      { type: 'text', title: '和女朋友谈恋爱后，对你有什么改变吗？' },
      { type: 'text', title: '你觉得女朋友拿得出手吗？' },
      { type: 'text', title: '你觉得你女朋友哪些地方做得不好？' },
      { type: 'text', title: '女朋友送你什么会很开心？' },
      { type: 'text', title: '当你情绪低落的时候，希望女朋友用什么方式安慰你？' },
      { type: 'text', title: '女朋友平常说什么话/做什么事最让你生气、难过？' },
      { type: 'text', title: '跟女朋友在一起的理由' },
      { type: 'text', title: '别人的闲话会动摇你吗？' },
      { type: 'text', title: '你会介意我的小脾气和敏感吗？' },
      { type: 'text', title: '你有没有一瞬间觉得我好可爱？' },
      { type: 'text', title: '我做过哪件事让你偷偷开心了很久？' },
      { type: 'text', title: '你可以接受女朋友的身材外貌有缺点、不完美吗？' },
      { type: 'text', title: '在一起印象最深的一次约会是哪次？为什么？' },
      { type: 'text', title: '一直照顾女朋友会让自己不舒服吗？' },
      { type: 'text', title: '如果可以重来，愿意继续和我走下去吗？' },
      { type: 'text', title: '你更喜欢哪种被爱的方式：陪伴、礼物、情话还是实际行动？' },
      { type: 'text', title: '出现分歧时，你更希望我用什么方式和你沟通？' },
      { type: 'text', title: '哪些我的行为会让你感到难过、失落？' },
      { type: 'text', title: '每次吵架，你内心真实想法是什么？会不会想分手？' },
      { type: 'text', title: '你眼里，我是一个什么样的人？' },
      { type: 'text', title: '我生气/难过时会有什么明显表现？' },
      { type: 'text', title: '我的哪些行为会让你失落？希望我怎么改？' },
      { type: 'text', title: '什么时候你会明显觉得我很爱你？' },
      { type: 'text', title: '你吃醋不安时会直接说还是自己憋着？' },
      { type: 'text', title: '你最欣赏我的三个优点？' },
      { type: 'text', title: '和我在一起最开心的一件事是什么？' },
      { type: 'text', title: '你认为幸福是什么？' },
      { type: 'text', title: '跟女朋友长期异地你心情怎么样？' },
      { type: 'text', title: '觉得长久的感情需要什么？' },
      { type: 'text', title: '我说什么会让你觉得生气？' },
      { type: 'text', title: '最想让我陪你做的一件事' },
      { type: 'text', title: '最想去的地方是哪里？' },
      { type: 'text', title: '我们在一起后，你会觉得自己变化最大的一点是什么？' },
      { type: 'text', title: '从和我认识到现在，最让你暖心记很久的一件事是什么？' },
      { type: 'text', title: '如果以后有更好的人出现，你会怎么做？' },
      { type: 'text', title: '如果有一天你发现不喜欢对方了会怎么办？' },
      { type: 'text', title: '你现在是喜欢我多一点还是习惯多一点？' },
      { type: 'text', title: '此刻你最想做的一件事是什么？' },
      { type: 'text', title: '回答到这里，只想跟我说的一句话' }
    ]
  },
  {
    id: 'love_exam',
    name: '恋爱考试卷（100分）',
    description: '恋爱知识测验: 单选题5题(各5分)+判断题5题(各3分)+情景题3题(各10分)+简答题1题(30分)。可用来互相出题打分。',
    questions: [
      { type: 'single', title: '【单选题】1、当女朋友对你说:"我是不是胖了?"，你应该说?', options: ['还行吧，就比猪瘦点儿', '不会呀，还得多吃点儿', '你自己心里没点数吗?', '是你在我心里分量重了'] },
      { type: 'single', title: '【单选题】2、当女朋友对你说:"没事，我没生气"，你会怎么说?', options: ['那好吧，我打游戏去啦。晋级赛呢!', '嗯嗯，像你这么懂事的女孩不多了', '说吧，又要我买什么?', '带你去吃好吃的，可以原谅我嘛'] },
      { type: 'single', title: '【单选题】3、当女朋友问:"你喜欢什么样的女生?"，你会怎么说?', options: ['对会呼吸的女孩没有抵抗力', '那必须是漂亮的、身材好的', '当初不是你非要跟我在一起的吗?', '这个答案很长，要用一生来回答'] },
      { type: 'single', title: '【单选题】4、当女朋友对你说:"我好像有点感冒了。"，你会怎么说?', options: ['谁叫你穿那么少', '离我远点。别传染我', '让她多喝热水', '快躺下休息。我去给你买药'] },
      { type: 'single', title: '【单选题】5、你问女朋友晚上吃什么，她说:"随便。"你会怎么做?', options: ['不耐烦的对她说，什么是随便', '那就随便点外卖，TA爱吃不吃', '让她给个选择范围，再去做饭', '自己定好餐厅，带她去吃饭'] },
      { type: 'single', title: '【判断题】1、前女友请你帮她去搬家，你觉得虽然分手了但总算认识一场就去了，为避免现女友误会没告诉她此事。', options: ['对', '错'] },
      { type: 'single', title: '【判断题】2、单身的女同事说电脑坏了，找你晚上去她家里帮忙修，你如实跟女朋友说了然后去帮女同事修电脑。', options: ['对', '错'] },
      { type: 'single', title: '【判断题】3、好哥们儿约你去"按摩"会所放松，你以女朋友在家里等你为由拒绝他。', options: ['对', '错'] },
      { type: 'single', title: '【判断题】4、女朋友发来文章"最适合情侣去的十个旅游地点"，你马上回复她去哪不重要，重要的是和你在一起。', options: ['对', '错'] },
      { type: 'single', title: '【判断题】5、女朋友的闺蜜一直对你不满意、常劝她分手，你说闺蜜是心机婊并让女朋友和她绝交。', options: ['对', '错'] },
      { type: 'single', title: '【情景题】1、你的网盘里保存着前女友的照片，被女朋友看见了你会怎么解释?', options: ['解释说这就是普通朋友，没有特殊关系', '从此网盘设置密码，保留私人空间，不随便让女朋友动自己的手机', '大方承认是前女友，并立马删除照片', '说前女友不如现女友漂亮，让现女友不用在意'] },
      { type: 'single', title: '【情景题】2、你的异性朋友和你单独喝酒，晚上太晚她住的宿舍锁门了你会怎么做?', options: ['把她一个人扔下，自己先回去', '带她去开个房，送她去住宾馆', '把她送到女朋友住的地方，让女朋友照顾她', '我不会单独和异性出去喝酒'] },
      { type: 'single', title: '【情景题】3、上班时间你在开会，没能及时回复女朋友的信息，她因此生气了，你会怎么做?', options: ['事后和她解释，自己是在工作', '告诉她自己努力工作挣钱是为了更好地养她', '这种女人要不得，立马和她分手', '和她讲道理，让她明白事情的轻重缓急'] },
      { type: 'text', title: '【简答题】你有1000块钱，女友想借400，前女友想借200。你还剩?' }
    ]
  },
  {
    id: 'cohabit_rules',
    name: '同居生活守则 60 问',
    description: '同居/合住生活习惯与边界考察: 厨房饮食/客厅起居/卧室休息/家务分工/消费社交/卫浴边界沟通 六篇各10题。均为是非题。',
    questions: [
      { type: 'single', title: '【厨房饮食篇】1.吃完饭要立刻洗碗吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】2.油烟大可以不开抽油烟机吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】3.外卖盒可以堆到次日再扔吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】4.饮品可以瓶口共用吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】5.冰箱食物谁买谁整理吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】6.做完饭台面要及时擦拭吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】7.能接受吃饭吧唧嘴吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】8.剩菜冰箱存放可超两天吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】9.砧板生熟需要分开吗？', options: ['是', '否'] },
      { type: 'single', title: '【厨房饮食篇】10.零食能不问就直接吃吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】11.换下衣物别随手扔沙发吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】12.能随意换对方正在看的节目吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】13.零食碎屑要立刻打扫吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】14.朋友上门聚会要提前商量吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】15.可以长时间外放短视频吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】16.抱枕用完要归位吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】17.能接受客厅抽烟吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】18.快递箱不要长期堆客厅吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】19.接受深夜打游戏吵闹吗？', options: ['是', '否'] },
      { type: 'single', title: '【客厅起居篇】20.不能穿鞋踩踏床铺吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】21.起床需要整理床铺吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】22.熬夜玩手机顾及对方光线吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】23.床上可以吃东西吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】24.作息不同要互相迁就安静吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】25.衣物别堆床头床边吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】26.接受打呼噜磨牙吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】27.卧室垃圾要当日清理吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】28.不许私自翻看对方手机吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】29.早起动静不要吵醒熟睡的人吗？', options: ['是', '否'] },
      { type: 'single', title: '【卧室休息篇】30.床上不能穿鞋踩吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】31.家务需要两人轮流分担吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】32.扫地拖地要有固定周期吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】33.脏衣服不要长期堆积吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】34.谁弄脏优先谁打扫吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】35.可以攒大量衣服再机洗吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】36.接受对方几乎不干家务吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】37.衣物洗完及时晾晒吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】38.打扫杂物要分类收纳吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】39.不想做家务可以出钱请保洁吗？', options: ['是', '否'] },
      { type: 'single', title: '【家务分工篇】40.打扫能中途搁置吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】41.日常开销定期沟通对账吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】42.大额消费要提前商量吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】43.异性单独见面需要告知吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】44.外借钱财要互相商量吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】45.拿对方东西要打招呼吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】46.纪念日互相准备礼物吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】47.外出赴约需要报备吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】48.接受超前透支消费吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】49.存款情况需要坦诚吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费社交篇】50.跟朋友吐槽伴侣要有分寸吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】51.洗澡后擦除地面水渍吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】52.用完马桶处理马桶圈吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】53.洗澡后清理地漏毛发吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】54.护肤品不经允许不要共用吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】55.毛巾浴巾不要混用吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】56.不要久占卫生间玩手机吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】57.洗完澡浴室通风防潮吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】58.内衣袜子不和外衣混洗吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】59.家里要有独处空间吗？', options: ['是', '否'] },
      { type: 'single', title: '【卫浴边界沟通】60.吵架不许冷战、说狠话、乱吐槽吗？', options: ['是', '否'] }
    ]
  },
  {
    id: 'marriage_expect',
    name: '婚恋期待 50 问',
    questions: [
      // 一、婚恋期待篇
      { type: 'single', title: '【婚恋期待】1.结婚一定要办婚礼吗？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】2.婚后必须要生孩子吗？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】3.能够接受婚后异地生活吗？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】4.恋爱多久适合登记结婚？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】5.婚后需要和父母保持高频来往吗？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】6.可以接受婚前同居试婚吗？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】7.离婚这件事可以接受吗？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】8.结婚必须要有彩礼嫁妆吗？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】9.婚后节日仪式感是否必须保留？', options: ['是', '否'] },
      { type: 'single', title: '【婚恋期待】10.爱情一定要走到婚姻吗？', options: ['是', '否'] },
      // 二、金钱观念篇
      { type: 'single', title: '【金钱观念】1.婚后工资是否要合并共同管理？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】2.买房买车需要双方共同出资吗？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】3.可以接受另一半负债结婚吗？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】4.婚后可以给父母大额补贴吗？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】5.消费观不一样需要互相妥协吗？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】6.是否要设立家庭共同储备金？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】7.婚前财产需要主动坦白吗？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】8.可以接受对方花钱大手大脚吗？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】9.婚后 AA 制能够接受吗？', options: ['是', '否'] },
      { type: 'single', title: '【金钱观念】10.投资重大决策需要双方商量吗？', options: ['是', '否'] },
      // 三、原生家庭篇
      { type: 'single', title: '【原生家庭】1.婚后需要经常回对方老家吗？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】2.可以接受父母插手小家庭矛盾吗？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】3.是否拒绝长辈长期同住？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】4.婆家娘家需要同等对待吗？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】5.父母干涉感情可以拒绝吗？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】6.过年必须轮流回双方老家吗？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】7.长辈提出不合理要求要顺从吗？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】8.婚后可以无底线帮扶兄弟姐妹吗？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】9.和长辈吵架需要伴侣无条件站队吗？', options: ['是', '否'] },
      { type: 'single', title: '【原生家庭】10.婆媳矛盾必须由男生出面沟通吗？', options: ['是', '否'] },
      // 四、未来规划篇
      { type: 'single', title: '【未来规划】1.生孩子之后谁主要负责带娃？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】2.婚后女生一定要优先顾家吗？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】3.是否计划几年之内备孕生子？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】4.未来打算在哪个城市长期定居？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】5.职业变动要和伴侣提前商量吗？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】6.是否接受丁克或者晚育？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】7.孩子教育观念需要达成一致吗？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】8.是否计划养小孩之外再养宠物？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】9.愿意为对方放弃自己的事业机会吗？', options: ['是', '否'] },
      { type: 'single', title: '【未来规划】10.人生规划不一样还可以继续走下去吗？', options: ['是', '否'] },
      // 五、亲密相处篇
      { type: 'single', title: '【亲密相处】1.吵架可以轻易提分手提离婚吗？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】2.婚后也需要保留单独社交圈子吗？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】3.精神出轨是否等同于背叛？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】4.接受伴侣有很好的异性好友吗？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】5.婚后需要时时刻刻分享日常吗？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】6.矛盾可以过夜不解决吗？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】7.是否能够接受伴侣隐瞒过往情史？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】8.生气可以冷战很多天吗？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】9.感情变淡要不要主动想办法修复？', options: ['是', '否'] },
      { type: 'single', title: '【亲密相处】10.吵架可以翻旧账吗？', options: ['是', '否'] }
    ]
  },
  {
    id: 'dating_rules',
    name: '约会相处 50 问',
    questions: [
      // 一、约会相处篇
      { type: 'single', title: '【约会相处】1.约会迟到要提前主动告知吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】2.安排行程需要兼顾对方喜好吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】3.约会时不能长时间埋头玩手机吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】4.临时取消约会应当提前沟通吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】5.不可以强行邀约对方反感的项目吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】6.约会分歧不能当场甩脸冷战吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】7.分开后主动报备平安到家吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】8.全程零交流的约会能接受吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】9.行程只由一方安排合理吗？', options: ['是', '否'] },
      { type: 'single', title: '【约会相处】10.逛街游玩顾及对方体力感受吗？', options: ['是', '否'] },
      // 二、消息沟通篇
      { type: 'single', title: '【消息沟通】1.忙到无法回复要简单说明情况吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】2.长时间失联前需要提前打招呼吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】3.尽量不要刻意拖延回复消息吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】4.不能用已读不回惩罚对方吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】5.打语音前先询问对方方便与否吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】6.日常碎片琐事愿意主动分享吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】7.生气不能只发表情冷暴力对方吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】8.负面情绪优先和恋人倾诉吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】9.话题中断不必勉强硬找话题吗？', options: ['是', '否'] },
      { type: 'single', title: '【消息沟通】10.消失不回消息容易让人不安吗？', options: ['是', '否'] },
      // 三、矛盾吵架篇
      { type: 'single', title: '【矛盾吵架】1.吵架不能拿分手当作气话吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】2.争执时不随意翻陈年旧账吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】3.冷静期失联不宜超过一天吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】4.激烈争吵杜绝人身攻击挖苦吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】5.不找亲友抱团评判恋人对错吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】6.先安抚情绪再讲道理更好吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】7.有错不能一味等待对方低头吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】8.意见不同不必强迫对方妥协吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】9.重要矛盾尽量当面沟通吗？', options: ['是', '否'] },
      { type: 'single', title: '【矛盾吵架】10.问题和解后不再反复旧事重提吗？', options: ['是', '否'] },
      // 四、异性边界篇
      { type: 'single', title: '【异性边界】1.和异性单独见面需要提前报备吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】2.深夜频繁私聊异性属于越界吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】3.面对暧昧示好应当直接拒绝吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】4.对象介意的异性朋友主动避嫌吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】5.不长期固定和异性连麦打游戏吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】6.不和异性单独短途出行探店吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】7.不与异性进行暧昧私密互动吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】8.异性倾诉感情保持分寸距离吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】9.不隐瞒恋人私下和异性碰面吗？', options: ['是', '否'] },
      { type: 'single', title: '【异性边界】10.可以保留前任联系方式偶尔闲聊吗？', options: ['是', '否'] },
      // 五、消费未来篇
      { type: 'single', title: '【消费未来】1.大额支出决定前和恋人商量吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】2.恋爱开销追求双向付出吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】3.纪念日互相准备小仪式感吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】4.外出聚会提前报备行程归期吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】5.在外吐槽恋人把握分寸不抹黑吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】6.人生规划里互相考虑对方吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】7.难以接受对方超前透支消费吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】8.亲友非议恋人主动维护对方吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】9.向外借钱需要和另一半商议吗？', options: ['是', '否'] },
      { type: 'single', title: '【消费未来】10.感情磨合需要双向改变而非单方面要求吗？', options: ['是', '否'] }
    ]
  }
];

// ---------------- 同步 fetch (宿主封装为阻塞) ----------------
function sbReq(method, path, body) {
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

// ---------------- 工具函数 ----------------
function genId(prefix) {
  var t = new Date().getTime().toString(36);
  var r = Math.floor(Math.random() * 1e9).toString(36);
  return prefix + '_' + t + r;
}
function inArray(arr, v) { for (var i = 0; i < arr.length; i++) { if (arr[i] === v) return true; } return false; }
function getSurveyById(id) {
  var res = sbReq('GET', '/rest/v1/' + SURVEY_T + '?id=eq.' + encodeURIComponent(id) + '&select=*');
  if (res.status === 200 && res.data && res.data.length > 0) return res.data[0];
  return null;
}
// 当前用户能否看这份问卷
function canView(s, me) {
  if (!s) return false;
  if (s.visibility === 'public') return true;
  if (s.visibility === 'draft') return s.owner_id === me.id;
  if (s.visibility === 'private') return (s.owner_id === me.id) || (s.target_user === me.id) || (s.target_user === me.name);
  return false;
}

// ---------------- 工具: 制定问卷 (草稿) ----------------
exports.create_survey = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var title = (params.title || '').toString().trim();
  var questions = params.questions;
  if (!title) return { success: false, error: '标题不能为空' };
  if (!questions || typeof questions !== 'object' || questions.length === 0) return { success: false, error: '问题列表不能为空' };
  // 校验每题结构
  var clean = [];
  for (var i = 0; i < questions.length; i++) {
    var q = questions[i];
    var qt = (q.type || 'text');
    if (qt !== 'single' && qt !== 'multiple' && qt !== 'text') qt = 'text';
    var qtitle = (q.title || ('问题' + (i + 1))).toString().trim();
    if (!qtitle) qtitle = '问题' + (i + 1);
    var opts = [];
    if (qt !== 'text') {
      var rawOpts = q.options || [];
      for (var j = 0; j < rawOpts.length; j++) { if (rawOpts[j] && rawOpts[j].toString().trim()) opts.push(rawOpts[j].toString().trim()); }
    }
    clean.push({ id: 'q' + (i + 1), type: qt, title: qtitle, options: opts });
  }
  var id = genId('sv');
  var rec = {
    id: id, owner_id: me.id, owner_name: me.name, title: title,
    description: (params.description || '').toString(), questions: clean,
    visibility: 'draft', target_user: '', created_at: new Date().toISOString()
  };
  var res = sbReq('POST', '/rest/v1/' + SURVEY_T, rec);
  if (res.status < 200 || res.status >= 300) return { success: false, error: '创建失败: ' + (res.error || res.status) };
  return { success: true, survey_id: id, visibility: 'draft', message: '问卷《' + title + '》已创建为草稿(仅你自己可见)。用 publish_survey 设为 public(公开) 或 private(私密,需 target_user)。', survey: rec };
};

// ---------------- 工具: 列出内置模板 ----------------
exports.list_templates = function (params) {
  var list = [];
  for (var i = 0; i < TEMPLATES.length; i++) {
    list.push({ id: TEMPLATES[i].id, name: TEMPLATES[i].name, description: TEMPLATES[i].description, questionCount: TEMPLATES[i].questions.length });
  }
  return { success: true, templates: list, message: '共 ' + TEMPLATES.length + ' 套内置模板。用 create_template_survey(template_id) 生成问卷。' };
};

// ---------------- 工具: 用内置模板生成问卷 ----------------
exports.create_template_survey = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var tid = (params.template_id || 'couple_71').toString();
  var tpl = null;
  for (var i = 0; i < TEMPLATES.length; i++) { if (TEMPLATES[i].id === tid) { tpl = TEMPLATES[i]; break; } }
  if (!tpl) return { success: false, error: '模板不存在: ' + tid + '。可用 list_templates 查看。' };
  var title = (params.title && params.title.toString().trim()) ? params.title.toString().trim() : tpl.name;
  // 构造问题(复用 create_survey 的校验结构)
  var clean = [];
  for (var k = 0; k < tpl.questions.length; k++) {
    var q = tpl.questions[k];
    var qt = (q.type || 'text');
    if (qt !== 'single' && qt !== 'multiple' && qt !== 'text') qt = 'text';
    var qtitle = (q.title || ('问题' + (k + 1))).toString().trim() || ('问题' + (k + 1));
    var opts = [];
    if (qt !== 'text') {
      var rawOpts = q.options || [];
      for (var j = 0; j < rawOpts.length; j++) { if (rawOpts[j] && rawOpts[j].toString().trim()) opts.push(rawOpts[j].toString().trim()); }
    }
    clean.push({ id: 'q' + (k + 1), type: qt, title: qtitle, options: opts });
  }
  var id = genId('sv');
  var rec = {
    id: id, owner_id: me.id, owner_name: me.name, title: title,
    description: (params.description || tpl.description || '').toString(), questions: clean,
    visibility: 'draft', target_user: '', created_at: new Date().toISOString()
  };
  var res = sbReq('POST', '/rest/v1/' + SURVEY_T, rec);
  if (res.status < 200 || res.status >= 300) return { success: false, error: '创建失败: ' + (res.error || res.status) };
  var msg = '问卷《' + title + '》已用模板「' + tpl.name + '」生成(草稿,仅你自己可见)。';
  // 可选: 直接发布
  var vis = (params.visibility || '').toString();
  if (vis === 'public' || vis === 'private') {
    var target = (params.target_user || '').toString().trim();
    if (vis === 'private' && !target) return { success: true, survey_id: id, visibility: 'draft', message: msg + ' 私密发布需补 target_user, 请再调 publish_survey。' };
    var patch = { visibility: vis, target_user: vis === 'private' ? target : '' };
    var pres = sbReq('PATCH', '/rest/v1/' + SURVEY_T + '?id=eq.' + encodeURIComponent(id), patch);
    if (pres.status >= 200 && pres.status < 300) {
      msg += (vis === 'public') ? ' 已公开发布, 所有人可见。' : (' 已设私密, 仅你和 ' + target + ' 可见。');
      return { success: true, survey_id: id, visibility: vis, message: msg };
    }
    msg += ' 但发布失败(' + (pres.error || pres.status) + '), 仍是草稿, 可用 publish_survey 再发。';
  }
  return { success: true, survey_id: id, visibility: 'draft', message: msg + ' 用 publish_survey 设为 public/private。' };
};

// ---------------- 工具: 发布问卷 (设可见范围) ----------------
exports.publish_survey = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var id = params.survey_id;
  if (!id) return { success: false, error: 'survey_id 不能为空' };
  var s = getSurveyById(id);
  if (!s) return { success: false, error: '问卷不存在' };
  if (s.owner_id !== me.id) return { success: false, error: '无权发布他人问卷' };
  var vis = (params.visibility || '').toString();
  if (vis !== 'public' && vis !== 'private') return { success: false, error: 'visibility 必须是 public 或 private' };
  var target = (params.target_user || '').toString().trim();
  if (vis === 'private' && !target) return { success: false, error: '私密问卷必须指定 target_user(授权可见的对方ID/用户名)' };
  var patch = { visibility: vis, target_user: vis === 'private' ? target : '' };
  var res = sbReq('PATCH', '/rest/v1/' + SURVEY_T + '?id=eq.' + encodeURIComponent(id), patch);
  if (res.status < 200 || res.status >= 300) return { success: false, error: '发布失败: ' + (res.error || res.status) };
  if (vis === 'public') return { success: true, message: '问卷《' + s.title + '》已公开发布, 所有人可见。' };
  return { success: true, message: '问卷《' + s.title + '》已设为私密, 仅你和 ' + target + ' 可见。转发邀请请用 forward_survey。' };
};

// ---------------- 答案归一化: AI 可能传 JSON 字符串, 统一成对象 ----------------
function normalizeAnswers(a) {
  if (a === null || a === undefined) return {};
  if (typeof a === 'object') return a;
  if (typeof a === 'string') {
    var t = a.trim();
    if (!t) return {};
    // 兼容 Python/JS 风格单引号 / 双引号混合
    try { return JSON.parse(t); }
    catch (e) {
      try { return JSON.parse(t.replace(/'/g, '"')); } catch (e2) { return {}; }
    }
  }
  return {};
}

// ---------------- 工具: 填写问卷 (暂存草稿回答) ----------------
exports.fill_survey = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var id = params.survey_id;
  if (!id) return { success: false, error: 'survey_id 不能为空' };
  var s = getSurveyById(id);
  if (!s) return { success: false, error: '问卷不存在' };
  if (!canView(s, me)) return { success: false, error: '你无权查看/填写此问卷(私密且仅授权者可见)' };
  if (s.visibility === 'draft') return { success: false, error: '该问卷还是草稿, 尚未发布' };
  var answers = normalizeAnswers(params.answers);
  // upsert: 同一人对同一问卷只保留一份草稿
  var q = '/rest/v1/' + RESP_T + '?survey_id=eq.' + encodeURIComponent(id) + '&respondent_id=eq.' + encodeURIComponent(me.id);
  var exist = sbReq('GET', q + '&select=*');
  var rec;
  if (exist.status === 200 && exist.data && exist.data.length > 0) {
    var rid = exist.data[0].id;
    rec = { answers: answers, is_submitted: false };
    var up = sbReq('PATCH', '/rest/v1/' + RESP_T + '?id=eq.' + encodeURIComponent(rid), rec);
    if (up.status < 200 || up.status >= 300) return { success: false, error: '保存草稿失败: ' + (up.error || up.status) };
    return { success: true, response_id: rid, is_submitted: false, message: '已暂存你的填写(草稿), 用 submit_survey 正式提交。' };
  }
  rec = {
    id: genId('rs'), survey_id: id, respondent_id: me.id, respondent_name: me.name,
    answers: answers, is_submitted: false, created_at: new Date().toISOString()
  };
  var ins = sbReq('POST', '/rest/v1/' + RESP_T, rec);
  if (ins.status < 200 || ins.status >= 300) return { success: false, error: '保存草稿失败: ' + (ins.error || ins.status) };
  return { success: true, response_id: rec.id, is_submitted: false, message: '已暂存你的填写(草稿), 用 submit_survey 正式提交。' };
};

// ---------------- 工具: 提交问卷 (正式交卷) ----------------
exports.submit_survey = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var id = params.survey_id;
  if (!id) return { success: false, error: 'survey_id 不能为空' };
  var s = getSurveyById(id);
  if (!s) return { success: false, error: '问卷不存在' };
  if (!canView(s, me)) return { success: false, error: '你无权填写此问卷' };
  if (s.visibility === 'draft') return { success: false, error: '该问卷还是草稿, 尚未发布' };
  // 有 response_id -> 把草稿置为已提交
  if (params.response_id) {
    var up = sbReq('PATCH', '/rest/v1/' + RESP_T + '?id=eq.' + encodeURIComponent(params.response_id), { is_submitted: true });
    if (up.status < 200 || up.status >= 300) return { success: false, error: '提交失败: ' + (up.error || up.status) };
    return { success: true, response_id: params.response_id, is_submitted: true, message: '已正式提交! 感谢填写《' + s.title + '》。' };
  }
  // 否则直接用 answers 新建已提交回答
  var answers = normalizeAnswers(params.answers);
  var rec = {
    id: genId('rs'), survey_id: id, respondent_id: me.id, respondent_name: me.name,
    answers: answers, is_submitted: true, created_at: new Date().toISOString()
  };
  var ins = sbReq('POST', '/rest/v1/' + RESP_T, rec);
  if (ins.status < 200 || ins.status >= 300) return { success: false, error: '提交失败: ' + (ins.error || ins.status) };
  return { success: true, response_id: rec.id, is_submitted: true, message: '已正式提交! 感谢填写《' + s.title + '》。' };
};

// ---------------- 工具: 转发问卷 ----------------
exports.forward_survey = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var id = params.survey_id;
  if (!id) return { success: false, error: 'survey_id 不能为空' };
  var s = getSurveyById(id);
  if (!s) return { success: false, error: '问卷不存在' };
  if (!canView(s, me)) return { success: false, error: '你无权转发此问卷' };
  if (s.visibility === 'draft') return { success: false, error: '草稿不能转发, 请先 publish_survey' };
  var to = (params.to || '').toString().trim();
  var rec = {
    id: genId('fw'), survey_id: id, from_id: me.id, from_name: me.name,
    to_target: to, created_at: new Date().toISOString()
  };
  var ins = sbReq('POST', '/rest/v1/' + FWD_T, rec);
  if (ins.status < 200 || ins.status >= 300) return { success: false, error: '转发失败: ' + (ins.error || ins.status) };
  var scopeTxt = (s.visibility === 'public') ? '公开问卷(所有人可填)' : ('私密问卷(仅你和 ' + (s.target_user || '指定人') + ' 可见)');
  var forwardText = '【问卷邀请】' + me.name + ' 邀请' + (to ? ('「' + to + '」') : '你') + '填写《' + s.title + '》——' + scopeTxt +
    (s.description ? ('\n说明: ' + s.description) : '') + '\n(在情侣问卷插件前端打开即可填写)';
  return { success: true, forward_id: rec.id, forward_text: forwardText, message: '已记录转发。可把下面这段发给对方:\n' + forwardText };
};

// ---------------- 工具: 列出问卷 ----------------
exports.list_surveys = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var scope = (params.scope || 'visible').toString();
  var res = sbReq('GET', '/rest/v1/' + SURVEY_T + '?select=*&order=created_at.desc');
  var list = (res.status === 200 && res.data) ? res.data : [];
  // 拉所有 responses 以便统计
  var rres = sbReq('GET', '/rest/v1/' + RESP_T + '?select=survey_id,is_submitted');
  var respAll = (rres.status === 200 && rres.data) ? rres.data : [];
  var out = [];
  for (var i = 0; i < list.length; i++) {
    var s = list[i];
    var visible = canView(s, me);
    if (scope === 'mine') { if (s.owner_id !== me.id) continue; }
    else if (scope === 'all') { /* 全量, 但私密且非授权仍标记不可见 */ }
    else { if (!visible) continue; } // visible 默认
    var cnt = 0;
    for (var k = 0; k < respAll.length; k++) { if (respAll[k].survey_id === s.id && respAll[k].is_submitted) cnt++; }
    out.push({
      survey_id: s.id, title: s.title, visibility: s.visibility, target_user: s.target_user,
      owner_name: s.owner_name, question_count: (s.questions ? s.questions.length : 0),
      submitted_count: cnt, can_view: visible, created_at: s.created_at
    });
  }
  return { success: true, scope: scope, count: out.length, surveys: out };
};

// ---------------- 工具: 获取单份问卷详情 ----------------
exports.get_survey = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var id = params.survey_id;
  if (!id) return { success: false, error: 'survey_id 不能为空' };
  var s = getSurveyById(id);
  if (!s) return { success: false, error: '问卷不存在' };
  if (!canView(s, me)) return { success: false, error: '你无权查看此问卷' };
  return { success: true, survey: s };
};

// ---------------- 工具: 获取问卷结果 ----------------
exports.get_results = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var id = params.survey_id;
  if (!id) return { success: false, error: 'survey_id 不能为空' };
  var s = getSurveyById(id);
  if (!s) return { success: false, error: '问卷不存在' };
  if (!canView(s, me)) return { success: false, error: '你无权查看此问卷' };
  var own = (s.owner_id === me.id);
  var res = sbReq('GET', '/rest/v1/' + RESP_T + '?survey_id=eq.' + encodeURIComponent(id) + '&select=*');
  var rs = (res.status === 200 && res.data) ? res.data : [];
  var submitted = [];
  for (var i = 0; i < rs.length; i++) { if (rs[i].is_submitted) submitted.push(rs[i]); }
  for (var i = 0; i < submitted.length; i++) { submitted[i].answers = normalizeAnswers(submitted[i].answers); }
  // 统计每题
  var stats = [];
  var qs = s.questions || [];
  for (var q = 0; q < qs.length; q++) {
    var item = { question_id: qs[q].id, title: qs[q].title, type: qs[q].type, total: submitted.length, answers: [] };
    if (qs[q].type === 'text') {
      for (var a = 0; a < submitted.length; a++) {
        var v = submitted[a].answers ? submitted[a].answers[qs[q].id] : undefined;
        if (v !== undefined && v !== null && v !== '') item.answers.push({ respondent: submitted[a].respondent_name, value: v });
      }
    } else {
      // single/multiple 计数
      var counter = {};
      for (var b = 0; b < submitted.length; b++) {
        var av = submitted[b].answers ? submitted[b].answers[qs[q].id] : undefined;
        if (av === undefined || av === null || av === '') continue;
        if (typeof av === 'string') { counter[av] = (counter[av] || 0) + 1; }
        else if (typeof av === 'object') { for (var c = 0; c < av.length; c++) { counter[av[c]] = (counter[av[c]] || 0) + 1; } }
      }
      var keys = []; for (var kk in counter) keys.push(kk);
      for (var m = 0; m < keys.length; m++) { item.answers.push({ option: keys[m], count: counter[keys[m]] }); }
    }
    stats.push(item);
  }
  return { success: true, title: s.title, visibility: s.visibility, submitted_count: submitted.length,
    is_owner: own, stats: stats,
    note: own ? '你是发布者, 可见全部明细' : (s.visibility === 'public' ? '公开问卷统计' : '你被授权查看') };
};

// ---------------- 工具: 删除问卷 (仅发布者) ----------------
exports.delete_survey = function (params) {
  params = params || {};
  var me = meOrErr(); if (me.error) return { success: false, error: me.error };
  var id = params.survey_id;
  if (!id) return { success: false, error: 'survey_id 不能为空' };
  var s = getSurveyById(id);
  if (!s) return { success: false, error: '问卷不存在' };
  if (s.owner_id !== me.id) return { success: false, error: '只能删除自己发布的问卷' };
  sbReq('DELETE', '/rest/v1/' + RESP_T + '?survey_id=eq.' + encodeURIComponent(id));
  sbReq('DELETE', '/rest/v1/' + FWD_T + '?survey_id=eq.' + encodeURIComponent(id));
  var res = sbReq('DELETE', '/rest/v1/' + SURVEY_T + '?id=eq.' + encodeURIComponent(id));
  if (res.status < 200 || res.status >= 300) return { success: false, error: '删除失败: ' + (res.error || res.status) };
  return { success: true, message: '问卷《' + s.title + '》及关联回答/转发已删除。' };
};
