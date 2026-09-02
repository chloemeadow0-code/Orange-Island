// 橘子岛官网 / 审核答题系统 配置
// 部署前请将下面两项替换为你的 Supabase 项目凭证

window.ORANGE_ISLAND_CONFIG = {
  // Supabase 项目 URL
  SUPABASE_URL: 'https://nvkcztwjlbszvwkvbetf.supabase.co',

  // Supabase anon/public key
  // anon key 是公开凭证，用于前端直接访问 Supabase。
  // 注意：静态站点无法隐藏该 key（浏览器可见），安全依赖 Supabase 端的
  // RLS 行级安全策略，请确保 quiz 相关表只开放必要的 insert/select。
  SUPABASE_ANON_KEY: 'sb_publishable_UEd2Pn0kR2sau1Xsfk2TQw_oHC8pRhS',

  // 每次答题随机抽取的题目数量（不能超过题库总数）
  QUESTION_COUNT: 25,

  // 答题限时（分钟）
  TIME_LIMIT_MINUTES: 20,

  // 通过所需正确率：0.96 = 25 题最多错 1 题
  PASS_THRESHOLD: 0.96,

  // 通过后显示的入群/下载提示（HTML）
  PASS_MESSAGE: `
    <p>恭喜你通过审核！</p>
    <p>请加入用户群：QQ 群号 <strong>YOUR_GROUP_NUMBER</strong></p>
    <p>入群后请查看群公告下载应用。</p>
  `,

  // 未通过时显示的提示
  FAIL_MESSAGE: `
    <p>未通过审核，请重新完整阅读使用说明后再试。</p>
    <p>每个 QQ 号只能提交一次。</p>
  `,

  // 官网底部 / 关于信息
  APP_NAME: '橘子岛',
  APP_EN_NAME: 'Orange Island',
  GITHUB_URL: 'https://github.com/chloemeadow0-code/Orange-Island',
};
